package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProtobufParser {
    private static final String STATEMENT_END = ";";
    private static final String BODY_START = "{";
    private static final String BODY_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String ARRAY_SEPARATOR = ",";
    private static final String RANGE_OPERATOR = "to";
    private static final String TYPE_PARAMETERS_START = "<";
    private static final String TYPE_PARAMETERS_END = ">";
    private static final String NULL = "null";
    private static final String KEY_VALUE_SEPARATOR = ":";

    private ProtobufParser() {
        throw new UnsupportedOperationException();
    }

    public static Map<String, ProtobufDocumentTree> parse(Path path) throws IOException {
        Objects.requireNonNull(path, "Path must not be null");
        if (!Files.isDirectory(path)) {
            return Map.of(path.getFileName().toString(), parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashMap<String, ProtobufDocumentTree>();
            for(var file : files) {
                var parsed = doParse(file, Files.newBufferedReader(file));
                if(results.put(file.getFileName().toString(), parsed) != null) {
                    throw new ProtobufParserException("Duplicate file: " + file);
                }
            }
            ProtobufAttribute.attribute(results.values());
            return Collections.unmodifiableMap(results);
        }
    }

    public static ProtobufDocumentTree parseOnly(String input) {
        try {
            var result = doParse(null, new StringReader(input));
            ProtobufAttribute.attribute(result);
            return result;
        }catch (IOException exception) {
            throw new InternalError("Unexpected exception", exception);
        }
    }

    public static ProtobufDocumentTree parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.newBufferedReader(path));
        ProtobufAttribute.attribute(result);
        return result;
    }

    private static ProtobufDocumentTree doParse(Path location, Reader input) throws IOException {
        try {
            var document = new ProtobufDocumentTree(location);
            var tokenizer = new ProtobufTokenizer(input);
            String token;
            while ((token = tokenizer.nextNullableToken()) != null) {
                switch (token) {
                    case STATEMENT_END -> {
                        var statement = parseEmpty(tokenizer);
                        document.addChild(statement);
                    }
                    case "package" -> {
                        var statement = parsePackage(tokenizer);
                        document.addChild(statement);
                    }
                    case "syntax" -> {
                        var statement = parseSyntax(tokenizer);
                        document.addChild(statement);
                    }
                    case "option" -> {
                        var statement = parseOption(tokenizer);
                        document.addChild(statement);
                    }
                    case "message" -> {
                        var statement = parseMessage(false, document, tokenizer);
                        document.addChild(statement);
                    }
                    case "enum" -> {
                        var statement = parseEnum(tokenizer);
                        document.addChild(statement);
                    }
                    case "service" -> {
                        var statement = parseService(tokenizer);
                        document.addChild(statement);
                    }
                    case "import" -> {
                        var statement = parseImport(tokenizer);
                        document.addChild(statement);
                    }
                    case "extend" -> {
                        var statement = parseMessage(true, document, tokenizer);
                        document.addChild(statement);
                    }
                    default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
                }
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            var withPath = new ProtobufParserException(syntaxException.getMessage() + " while parsing " + location);
            withPath.setStackTrace(syntaxException.getStackTrace());
            throw withPath;
        }
    }

    private static ProtobufEmptyStatement parseEmpty(ProtobufTokenizer tokenizer) {
        return new ProtobufEmptyStatement(tokenizer.line());
    }

    private static ProtobufPackageStatement parsePackage(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufPackageStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalTypeReference(name),
                "Unexpected token " + name, tokenizer.line());
        statement.setName(name);
        var end = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static ProtobufSyntaxStatement parseSyntax(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufSyntaxStatement(tokenizer.line());
        var assignment = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isAssignmentOperator(assignment),
                "Unexpected token " + assignment, tokenizer.line());
        var version = tokenizer.nextRequiredLiteral();
        statement.setVersion(version);
        var end = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static ProtobufOptionStatement parseOption(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufOptionStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var assignment = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isAssignmentOperator(assignment),
                "Unexpected token " + assignment, tokenizer.line());
        var expression = readExpression(tokenizer);
        statement.setValue(expression);
        var end = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static ProtobufMessageStatement parseMessage(boolean extension, ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufMessageStatement(tokenizer.line(), extension);
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRequiredToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(false, document, tokenizer);
                    statement.addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.addChild(child);
                }
                case "extend" -> {
                    var child = parseMessage(true, document, tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.addChild(child);
                }
                case "oneof" -> {
                    var child = parseOneof(document, tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseField(document, true, tokenizer, token);
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufFieldStatement parseField(ProtobufDocumentTree document, boolean allowModifier, ProtobufTokenizer tokenizer, String token) throws IOException {
        ProtobufFieldStatement.Modifier modifier;
        ProtobufTypeReference reference;
        if(allowModifier) {
            modifier = ProtobufFieldStatement.Modifier.of(token);
           if(modifier.type() == ProtobufFieldStatement.Modifier.Type.NOTHING) {
               var version = document.syntax()
                       .orElse(ProtobufVersion.defaultVersion());
               ProtobufParserException.check(version == ProtobufVersion.PROTOBUF_3,
                       "Unexpected token " + token, tokenizer.line());
               reference = ProtobufTypeReference.of(token);
           }else {
               var type = tokenizer.nextRequiredToken();
               ProtobufParserException.check(isLegalTypeReference(type),
                       "Unexpected token " + type, tokenizer.line());
               reference = ProtobufTypeReference.of(type);
           }
       }else {
           modifier = ProtobufFieldStatement.Modifier.nothing();
           reference = ProtobufTypeReference.of(token);
       }

        ProtobufFieldStatement child;
        if(reference.protobufType() == ProtobufType.GROUP) {
            child = new ProtobufGroupFieldStatement(tokenizer.line());
        }else {
            child = new ProtobufFieldStatement(tokenizer.line());
            child.setType(reference);
        }
        child.setModifier(modifier);

        var nameOrTypeArgs = tokenizer.nextRequiredToken();
        if(isTypeParametersStart(nameOrTypeArgs)) {
            if(!(child.type() instanceof ProtobufMapTypeReference mapType) || mapType.isAttributed()) {
                throw new ProtobufParserException("Unexpected token " + nameOrTypeArgs, tokenizer.line());
            }

            var keyTypeToken = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalName(keyTypeToken),
                    "Unexpected token " + keyTypeToken, tokenizer.line());
            var keyType = ProtobufTypeReference.of(keyTypeToken);
            mapType.setKeyType(keyType);

            var separator = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isArraySeparator(separator),
                    "Unexpected token " + separator, tokenizer.line());

            var valueTypeToken = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalName(valueTypeToken),
                    "Unexpected token " + valueTypeToken, tokenizer.line());
            var valueType = ProtobufTypeReference.of(valueTypeToken);
            mapType.setValueType(valueType);

            var end = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isTypeParametersEnd(end),
                    "Unexpected token " + end, tokenizer.line());

            var childName = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalName(childName),
                    "Unexpected token " + childName, tokenizer.line());
            child.setName(childName);
        }else if(isLegalName(nameOrTypeArgs)) {
            child.setName(nameOrTypeArgs);
        }else {
            throw new ProtobufParserException("Unexpected token " + nameOrTypeArgs, tokenizer.line());
        }

        var operator = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());
        var index = tokenizer.nextRequiredIndex(false);
        child.setIndex(index);

        var optionsOrBodyOrEndToken = tokenizer.nextRequiredToken();
        if(isArrayStart(optionsOrBodyOrEndToken)) {
            parseFieldOptions(tokenizer, child);
            optionsOrBodyOrEndToken = tokenizer.nextRequiredToken();
        }

        if(child instanceof ProtobufGroupFieldStatement groupStatement && isBodyStart(optionsOrBodyOrEndToken)) {
            String groupToken;
            while(!isBodyEnd(groupToken = tokenizer.nextRequiredToken())) {
                switch (groupToken) {
                    case STATEMENT_END -> {
                        var groupChild = parseEmpty(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "message" -> {
                        var groupChild = parseMessage(false, document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "enum" -> {
                        var groupChild = parseEnum(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extend" -> {
                        var groupChild = parseMessage(true, document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extensions" -> {
                        var groupChild = parseExtensions(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "reserved" -> {
                        var groupChild = parseReserved(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "oneof" -> {
                        var groupChild = parseOneof(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    default -> {
                        var groupChild = parseField(document, true, tokenizer, groupToken);
                        groupStatement.addChild(groupChild);
                    }
                }
            }
            return child;
        } else if(isStatementEnd(optionsOrBodyOrEndToken)) {
            return child;
        }else {
            throw new ProtobufParserException("Unexpected token " + optionsOrBodyOrEndToken, tokenizer.line());
        }
    }

    private static void parseFieldOptions(ProtobufTokenizer tokenizer, ProtobufFieldStatement child) throws IOException {
        var optionNameOrEnd = tokenizer.nextRequiredToken();
        if (isArrayEnd(optionNameOrEnd)) {
            return;
        }

        while (true) {
            if (!isLegalName(optionNameOrEnd)) {
                throw new ProtobufParserException("Unexpected token " + optionNameOrEnd, tokenizer.line());
            }

            var optionOperator = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isAssignmentOperator(optionOperator),
                    "Unexpected token " + optionOperator, tokenizer.line());

            var optionValue = readExpression(tokenizer);
            child.addOption(optionNameOrEnd, optionValue);

            var optionSeparator = tokenizer.nextRequiredToken();
            if(isArrayEnd(optionSeparator)) {
                break;
            }else if(isArraySeparator(optionSeparator)) {
                optionNameOrEnd = tokenizer.nextRequiredToken();
            } else {
                throw new ProtobufParserException("Unexpected token " + optionSeparator, tokenizer.line());
            }
        }
    }

    //  ProtobufEmptyStatement, ProtobufEnumConstant, ProtobufExtensionsStatement, ProtobufOptionStatement, ProtobufReservedStatement
    private static ProtobufEnumStatement parseEnum(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufEnumStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRequiredToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseEnumConstant(token, tokenizer);
                    child.setType(new ProtobufMessageOrEnumTypeReference(statement));
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufEnumConstant parseEnumConstant(String token, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufEnumConstant(tokenizer.line());
        statement.setModifier(ProtobufFieldStatement.Modifier.nothing());
        statement.setName(token);

        var operator = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());

        var index = tokenizer.nextRequiredIndex(false);
        statement.setIndex(index);

        var optionsOrEndToken = tokenizer.nextRequiredToken();
        if(isArrayStart(optionsOrEndToken)) {
            parseFieldOptions(tokenizer, statement);
            optionsOrEndToken = tokenizer.nextRequiredToken();
        }

        ProtobufParserException.check(isStatementEnd(optionsOrEndToken),
                "Unexpected token " + optionsOrEndToken, tokenizer.line());
        return statement;
    }

    private static ProtobufExtensionsStatement parseExtensions(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufExtensionsStatement(tokenizer.line());

        while(true) {
            var value = tokenizer.nextRequiredIndex(false);
            var operator = tokenizer.nextRequiredToken();
            if(isRangeOperator(operator)) {
                var end = tokenizer.nextRequiredIndex(true);
                var expression = new ProtobufRangeExpression(tokenizer.line());
                expression.setMin(value);
                expression.setMax(end);
                statement.addExpression(expression);
                operator = tokenizer.nextRequiredToken();
            }else {
                var expression = new ProtobufIntegerExpression(tokenizer.line());
                expression.setValue(value);
                statement.addExpression(expression);
            }
            if (isStatementEnd(operator)) {
                break;
            } else if (!isArraySeparator(operator)) {
                throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
            }
        }

        if(statement.expressions().isEmpty()) {
            throw new ProtobufParserException("Unexpected token ;", tokenizer.line());
        }

        return statement;
    }

    private static ProtobufReservedStatement parseReserved(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufReservedStatement(tokenizer.line());

        bodyLoop: {
            while(true) {
                var value = tokenizer.nextRequiredParsedToken();
                switch (value) {
                    case ProtobufTokenizer.ParsedToken.Literal literal -> {
                        var expression = new ProtobufLiteralExpression(tokenizer.line());
                        expression.setValue(literal.value());
                        statement.addExpression(expression);
                        var operator = tokenizer.nextRequiredToken();
                        if(isStatementEnd(operator)) {
                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    case ProtobufTokenizer.ParsedToken.Number.Integer integer -> {
                        var operator = tokenizer.nextRequiredToken();
                        if(isRangeOperator(operator)) {
                            var end = tokenizer.nextRequiredIndex(true);
                            var expression = new ProtobufRangeExpression(tokenizer.line());
                            expression.setMin(integer.value());
                            expression.setMax(end);
                            statement.addExpression(expression);
                            operator = tokenizer.nextRequiredToken();
                        }else {
                            var expression = new ProtobufIntegerExpression(tokenizer.line());
                            expression.setValue(integer.value());
                            statement.addExpression(expression);
                        }
                        if(isStatementEnd(operator)) {
                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    default -> throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                }
            }
        }

        if(statement.expressions().isEmpty()) {
            throw new ProtobufParserException("Unexpected token ;", tokenizer.line());
        }

        return statement;
    }

    private static ProtobufServiceStatement parseService(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufServiceStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRequiredToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.addChild(child);
                }
                case "rpc" -> {
                    var child = parseMethod(tokenizer);
                    statement.addChild(child);
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
            }
        }
        return statement;
    }

    private static ProtobufMethodStatement parseMethod(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufMethodStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRequiredToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.addChild(child);
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
            }
        }
        return statement;
    }

    private static ProtobufOneofFieldStatement parseOneof(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufOneofFieldStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalName(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRequiredToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseField(document, false, tokenizer, token);
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufImportStatement parseImport(ProtobufTokenizer tokenizer) throws IOException {
        var importStatement = new ProtobufImportStatement(tokenizer.line());
        var literal = tokenizer.nextRequiredLiteral();
        ProtobufParserException.check(literal != null,
                "Unexpected token " + literal, tokenizer.line());
        importStatement.setLocation(literal);
        var end = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return importStatement;
    }

    private static ProtobufExpression readExpression(ProtobufTokenizer tokenizer) throws IOException {
        var line = tokenizer.line();
        return switch (tokenizer.nextRequiredParsedToken()) {
            case ProtobufTokenizer.ParsedToken.Boolean bool -> {
                var expression = new ProtobufBoolExpression(line);
                expression.setValue(bool.value());
                yield expression;
            }

            case ProtobufTokenizer.ParsedToken.Number.Integer integer -> {
                var expression = new ProtobufIntegerExpression(line);
                expression.setValue(integer.value());
                yield expression;
            }

            case ProtobufTokenizer.ParsedToken.Number.FloatingPoint floatingPoint -> {
                var expression = new ProtobufFloatingPointExpression(line);
                expression.setValue(floatingPoint.value());
                yield expression;
            }

            case ProtobufTokenizer.ParsedToken.Literal literal -> {
                var expression = new ProtobufLiteralExpression(line);
                expression.setValue(literal.value());
                yield expression;
            }

            case ProtobufTokenizer.ParsedToken.Raw raw
                    when isNullExpression(raw.value()) -> new ProtobufNullExpression(line);
            case ProtobufTokenizer.ParsedToken.Raw raw
                    when isBodyStart(raw.value()) -> {
                var expression = new ProtobufMessageValueExpression(line);
                loop: {
                    while (true) {
                        var keyToken = tokenizer.nextRequiredParsedToken();
                        switch (keyToken) {
                            case ProtobufTokenizer.ParsedToken.Raw(var value)
                                    when isBodyEnd(value) -> {
                                break loop;
                            }
                            case ProtobufTokenizer.ParsedToken.Literal(var key) -> {
                                var keyValueSeparatorToken = tokenizer.nextNullableToken();
                                if(keyValueSeparatorToken == null) {
                                    throw new ProtobufParserException("Unexpected end of input");
                                }

                                ProtobufParserException.check(!isKeyValueSeparatorOperator(keyValueSeparatorToken),
                                        "Unexpected token " + keyValueSeparatorToken, line);
                                var value = readExpression(tokenizer);

                                expression.addData(key, value);
                            }
                            default -> throw new ProtobufParserException("Unexpected token " + raw.value(), line);
                        }
                    }
                }
                yield expression;
            }
            case ProtobufTokenizer.ParsedToken.Raw raw
                    when isLegalName(raw.value()) -> {
                var expression = new ProtobufEnumConstantExpression(line);
                expression.setName(raw.value());
                yield expression;
            }
            case ProtobufTokenizer.ParsedToken.Raw raw -> throw new ProtobufParserException("Unexpected token " + raw.value(), line);
        };
    }

    private static boolean isKeyValueSeparatorOperator(String keyValueSeparatorToken) {
        return Objects.equals(keyValueSeparatorToken, KEY_VALUE_SEPARATOR);
    }

    private static boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private static boolean isBodyStart(String operator) {
        return Objects.equals(operator, BODY_START);
    }

    private static boolean isBodyEnd(String operator) {
        return Objects.equals(operator, BODY_END);
    }

    private static boolean isArrayStart(String operator) {
        return Objects.equals(operator, ARRAY_START);
    }

    private static boolean isArrayEnd(String operator) {
        return Objects.equals(operator, ARRAY_END);
    }

    private static boolean isTypeParametersStart(String operator) {
        return Objects.equals(operator, TYPE_PARAMETERS_START);
    }

    private static boolean isTypeParametersEnd(String operator) {
        return Objects.equals(operator, TYPE_PARAMETERS_END);
    }

    private static boolean isStatementEnd(String operator) {
        return Objects.equals(operator, STATEMENT_END);
    }

    private static boolean isArraySeparator(String operator) {
        return Objects.equals(operator, ARRAY_SEPARATOR);
    }

    private static boolean isRangeOperator(String operator) {
        return Objects.equals(operator, RANGE_OPERATOR);
    }

    private static boolean isLegalTypeReference(String name) {
        var length = name.length();
        if(length == 0) {
            return false;
        }

        if(name.charAt(0) == '.' || name.charAt(length - 1) == '.') {
            return false;
        }

        var start = 0;
        for(var end = 1; end < length; end++) {
            if (name.charAt(end) != '.') {
                continue;
            }

            if(!isLegalName(name.substring(start, end))) {
                return false;
            }

            start = end + 1;
        }

        return true;
    }

    private static boolean isLegalName(String instruction) {
        var length = instruction.length();
        if(length == 0) {
            return false;
        }

        if(!Character.isLetter(instruction.charAt(0))) {
            return false;
        }

        for(var i = 1; i < length; i++) {
            var entry = instruction.charAt(i);
            if(!Character.isLetterOrDigit(entry) && entry != '_') {
                return false;
            }
        }

        return true;
    }

    private static boolean isNullExpression(String token) {
        return Objects.equals(token, NULL);
    }
}
