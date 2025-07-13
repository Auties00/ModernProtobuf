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
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String LIST_SEPARATOR = ",";
    private static final String RANGE_OPERATOR = "to";
    private static final String MAX_KEYWORD = "max";
    private static final String TYPE_PARAMETERS_START = "<";
    private static final String TYPE_PARAMETERS_END = ">";
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String NULL = "null";
    private static final String KEY_VALUE_SEPARATOR = ":";

    private ProtobufParser() {
        throw new UnsupportedOperationException();
    }

    public static Set<ProtobufDocumentTree> parse(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Set.of(parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashSet<ProtobufDocumentTree>();
            for(var file : files) {
                var parsed = doParse(file, Files.newBufferedReader(file));
                if(!results.add(parsed)) {
                    throw new ProtobufParserException("Duplicate file: " + file);
                }
            }
            ProtobufAttribute.attribute(results);
            return results;
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
                        document.body()
                                .addChild(statement);
                    }
                    case "package" -> {
                        var statement = parsePackage(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "syntax" -> {
                        var statement = parseSyntax(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "option" -> {
                        var statement = parseOption(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "message" -> {
                        var statement = parseMessage(false, document, tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "enum" -> {
                        var statement = parseEnum(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "service" -> {
                        var statement = parseService(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "import" -> {
                        var statement = parseImport(tokenizer);
                        document.body()
                                .addChild(statement);
                    }
                    case "extend" -> {
                        var statement = parseMessage(true, document, tokenizer);
                        document.body()
                                .addChild(statement);
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
        ProtobufParserException.check(isLegalIdentifier(name),
                "Unexpected token " + name, tokenizer.line());
        statement.setName(name);
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
        var index = tokenizer.nextRequiredInt(false);
        statement.setVersion(index);
        var end = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static ProtobufOptionStatement parseOption(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufOptionStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalIdentifier(name),
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
        ProtobufParserException.check(isLegalIdentifier(name),
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
                    statement.body()
                            .addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(false, document, tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "extend" -> {
                    var child = parseMessage(true, document, tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "oneof" -> {
                    var child = parseOneof(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                default -> {
                    parseField(document, token, tokenizer);
                }
            }
        }
        return statement;
    }

    private static ProtobufFieldStatement parseField(ProtobufDocumentTree document, String token, ProtobufTokenizer tokenizer) throws IOException {
        var modifier = ProtobufFieldStatement.Modifier.of(token);
        ProtobufTypeReference reference;
        if(modifier.type() == ProtobufFieldStatement.Modifier.Type.NOTHING) {
            var version = document.syntax()
                    .orElse(ProtobufVersion.defaultVersion());
            ProtobufParserException.check(version == ProtobufVersion.PROTOBUF_3,
                    "Unexpected token " + token, tokenizer.line());
            reference = ProtobufTypeReference.of(token);
        }else {
            var type = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalIdentifier(type),
                    "Unexpected token " + type, tokenizer.line());
            reference = ProtobufTypeReference.of(type);
        }

        var child = reference.protobufType() == ProtobufType.GROUP
                ? new ProtobufGroupFieldStatement(tokenizer.line())
                : new ProtobufFieldStatement(tokenizer.line());
        child.setModifier(modifier);
        child.setType(reference);

        var nameOrTypeArgs = tokenizer.nextRequiredToken();
        if(isTypeParametersStart(nameOrTypeArgs)) {
            if(!(child.type() instanceof ProtobufMapTypeReference mapType) || mapType.isAttributed()) {
                throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
            }

            var keyTypeToken = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalIdentifier(keyTypeToken),
                    "Unexpected token " + keyTypeToken, tokenizer.line());
            var keyType = ProtobufTypeReference.of(keyTypeToken);
            mapType.setKeyType(keyType);

            var separator = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isListSeparator(separator),
                    "Unexpected token " + separator, tokenizer.line());

            var valueTypeToken = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalIdentifier(valueTypeToken),
                    "Unexpected token " + valueTypeToken, tokenizer.line());
            var valueType = ProtobufTypeReference.of(valueTypeToken);
            mapType.setValueType(valueType);

            var end = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isTypeParametersEnd(end),
                    "Unexpected token " + end, tokenizer.line());

            var childName = tokenizer.nextRequiredToken();
            ProtobufParserException.check(isLegalIdentifier(childName),
                    "Unexpected token " + childName, tokenizer.line());
            child.setName(childName);
        }else if(isLegalIdentifier(nameOrTypeArgs)) {
            child.setName(nameOrTypeArgs);
        }else {
            throw new ProtobufParserException("Unexpected token " + nameOrTypeArgs, tokenizer.line());
        }

        var operator = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());
        var index = tokenizer.nextRequiredInt(false);
        child.setIndex(index);

        var childToken = tokenizer.nextRequiredToken();
        if(isArrayStart(operator)) {
            while (true) {
                var optionName = tokenizer.nextRequiredToken();
                if(isArrayEnd(optionName)) {
                    break;
                }else if(isLegalIdentifier(optionName)) {
                    var optionValue = readExpression(tokenizer);
                    child.addOption(optionName, optionValue);
                }else {
                    throw new ProtobufParserException("Unexpected token " + optionName, tokenizer.line());
                }
            }
            childToken = tokenizer.nextRequiredToken();
        }

        if(child instanceof ProtobufGroupFieldStatement groupStatement && isBodyStart(childToken)) {
            String groupToken;
            while(!isBodyEnd(groupToken = tokenizer.nextRequiredToken())) {
                switch (groupToken) {
                    case STATEMENT_END -> {
                        var groupChild = parseEmpty(tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "message" -> {
                        var groupChild = parseMessage(false, document, tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "enum" -> {
                        var groupChild = parseEnum(tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "extend" -> {
                        var groupChild = parseMessage(true, document, tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "extensions" -> {
                        var groupChild = parseExtensions(tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "reserved" -> {
                        var groupChild = parseReserved(tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    case "oneof" -> {
                        var groupChild = parseOneof(tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }

                    default -> {
                        var groupChild = parseField(document, token, tokenizer);
                        groupStatement.body()
                                .addChild(groupChild);
                    }
                }
            }
            return child;
        } else if(isStatementEnd(childToken)) {
            return child;
        }else {
            throw new ProtobufParserException("Unexpected token " + childToken, tokenizer.line());
        }
    }

    private static ProtobufEnumStatement parseEnum(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufEnumStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalIdentifier(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        return statement;
    }

    private static ProtobufExtensionsStatement parseExtensions(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufExtensionsStatement(tokenizer.line());

        while(true) {
            var value = tokenizer.nextRequiredInt(false);
            var operator = tokenizer.nextRequiredToken();
            if(isListSeparator(operator)) {
                var expression = new ProtobufIntegerExpression(tokenizer.line());
                expression.setValue(value);
                statement.addExpression(expression);
            }else if(isRangeOperator(operator)) {
                var end = tokenizer.nextRequiredInt(true);
                var expression = new ProtobufRangeExpression(tokenizer.line());
                expression.setMin(value);
                expression.setMax(end);
                statement.addExpression(expression);
                operator = tokenizer.nextRequiredToken();
                if(isStatementEnd(operator)) {
                    break;
                }else if(!isListSeparator(operator)) {
                    throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                }
            }else if(isStatementEnd(operator)) {
                break;
            }else {
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
                var value = tokenizer.nextRequiredParsedToken(false);
                switch (value) {
                    case ProtobufTokenizer.ParsedToken.Literal literal -> {
                        var expression = new ProtobufLiteralExpression(tokenizer.line());
                        expression.setValue(literal.value());
                        statement.addExpression(expression);
                        var operator = tokenizer.nextRequiredToken();
                        if(isStatementEnd(operator)) {
                            break bodyLoop;
                        }else if(!isListSeparator(operator)) {
                            throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    case ProtobufTokenizer.ParsedToken.Int integer -> {
                        var operator = tokenizer.nextRequiredToken();
                        if(isListSeparator(operator)) {
                            var expression = new ProtobufIntegerExpression(tokenizer.line());
                            expression.setValue(integer.value());
                            statement.addExpression(expression);
                        }else if(isRangeOperator(operator)) {
                            var end = tokenizer.nextRequiredInt(true);
                            var expression = new ProtobufRangeExpression(tokenizer.line());
                            expression.setMin(integer.value());
                            expression.setMax(end);
                            statement.addExpression(expression);
                            operator = tokenizer.nextRequiredToken();
                            if(isStatementEnd(operator)) {
                                break bodyLoop;
                            }else if(!isListSeparator(operator)) {
                                throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                            }
                        }else if(isStatementEnd(operator)) {
                            break bodyLoop;
                        }else {
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
        ProtobufParserException.check(isLegalIdentifier(name),
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
                    statement.body()
                            .addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                case "rpc" -> {
                    var child = parseMethod(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
            }
        }
        return statement;
    }

    private static ProtobufMethodStatement parseMethod(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufMethodStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalIdentifier(name),
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
                    statement.body()
                            .addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
            }
        }
        return statement;
    }

    private static ProtobufOneofStatement parseOneof(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufOneofStatement(tokenizer.line());
        var name = tokenizer.nextRequiredToken();
        ProtobufParserException.check(isLegalIdentifier(name),
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
                    statement.body()
                            .addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer);
                    statement.body()
                            .addChild(child);
                }
                default -> {
                    var child = new ProtobufFieldStatement(tokenizer.line());
                    var reference = ProtobufTypeReference.of(token);
                    child.setType(reference);
                    child.setModifier(ProtobufFieldStatement.Modifier.nothing());
                    statement.body()
                            .addChild(child);
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
        var token = tokenizer.nextNullableToken();
        if(token == null) {
            return null;
        }

        var line = tokenizer.line();
        if(isNullExpression(token)) {
            return new ProtobufNullExpression(line);
        }

        if(isBodyStart(token)) {
            var expression = new ProtobufMessageValueExpression(line);

            while (true) {
                var keyToken = tokenizer.nextNullableToken();
                if(keyToken == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }

                if(isBodyEnd(keyToken)) {
                    break;
                }

                var key = parseStringLiteral(keyToken)
                        .orElseThrow(() -> new ProtobufParserException("Unexpected token " + keyToken, line));

                var keyValueSeparatorToken = tokenizer.nextNullableToken();
                if(keyValueSeparatorToken == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }

                ProtobufParserException.check(!isKeyValueSeparatorOperator(keyValueSeparatorToken),
                        "Unexpected token " + keyValueSeparatorToken, line);
                var value = readExpression(tokenizer);

                expression.addData(key, value);
            }

            return expression;
        }

        var literal = parseStringLiteral(token);
        if(literal.isPresent()) {
            var expression = new ProtobufLiteralExpression(line);
            expression.setValue(literal.get());
            return expression;
        }

        var bool = parseBool(token);
        if(bool.isPresent()) {
            var expression = new ProtobufBoolExpression(line);
            expression.setValue(bool.get());
            return expression;
        }

        var integer = parseIndex(token, true, false);
        if(integer.isPresent()) {
            var expression = new ProtobufIntegerExpression(line);
            expression.setValue(integer.get());
            return expression;
        }

        if(isLegalIdentifier(token)) {
            var expression = new ProtobufEnumConstantExpression(line);
            expression.setName(token);
            return expression;
        }

        throw new ProtobufParserException("Unexpected token " + token, line);
    }

    private static boolean isKeyValueSeparatorOperator(String keyValueSeparatorToken) {
        return Objects.equals(keyValueSeparatorToken, KEY_VALUE_SEPARATOR);
    }

    private static boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private static boolean isBodyStart(String operator) {
        return Objects.equals(operator, OBJECT_START);
    }

    private static boolean isBodyEnd(String operator) {
        return Objects.equals(operator, OBJECT_END);
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

    private static boolean isListSeparator(String operator) {
        return Objects.equals(operator, LIST_SEPARATOR);
    }

    private static boolean isRangeOperator(String operator) {
        return Objects.equals(operator, RANGE_OPERATOR);
    }

    private static boolean isLegalIdentifier(String instruction) {
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
