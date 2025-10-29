package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.attribute.ProtobufAttribute;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.token.ProtobufToken;
import it.auties.protobuf.parser.token.ProtobufTokenizer;
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
    private static final String PARENS_START = "(";
    private static final String PARENS_END = ")";
    private static final String STREAM = "stream";
    private static final String MAX = "max";

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
        return parseOnly(input, (Collection<ProtobufDocumentTree>) null);
    }

    public static ProtobufDocumentTree parseOnly(String input, ProtobufDocumentTree... documentTrees) {
        return parseOnly(input, Arrays.asList(documentTrees));
    }

    public static ProtobufDocumentTree parseOnly(String input, Collection<? extends ProtobufDocumentTree> documents) {
        try {
            var results = new ArrayList<ProtobufDocumentTree>();
            if(documents != null) {
                results.addAll(documents);
            }
            var result = doParse(null, new StringReader(input));
            results.add(result);
            ProtobufAttribute.attribute(results);
            return result;
        }catch (IOException exception) {
            throw new InternalError(exception);
        }
    }

    public static ProtobufDocumentTree parseOnly(Path input) {
        return parseOnly(input, (Collection<ProtobufDocumentTree>) null);
    }

    public static ProtobufDocumentTree parseOnly(Path input, ProtobufDocumentTree... documentTrees) {
        return parseOnly(input, Arrays.asList(documentTrees));
    }

    public static ProtobufDocumentTree parseOnly(Path input, Collection<? extends ProtobufDocumentTree> documents) {
        try {
            var results = new ArrayList<ProtobufDocumentTree>();
            if(documents != null) {
                results.addAll(documents);
            }
            var result = doParse(null, Files.newBufferedReader(input));
            results.add(result);
            ProtobufAttribute.attribute(results);
            return result;
        }catch (IOException exception) {
            throw new InternalError("Unexpected exception", exception);
        }
    }

    private static ProtobufDocumentTree doParse(Path location, Reader input) throws IOException {
        try {
            var document = new ProtobufDocumentTree(location);
            var tokenizer = new ProtobufTokenizer(input);
            String token;
            while ((token = tokenizer.nextRawToken(false)) != null) {
                switch (token) {
                    case STATEMENT_END -> {
                        var statement = parseEmpty(tokenizer);
                        document.addChild(statement);
                    }
                    case "package" -> {
                        var statement = parsePackage(document, tokenizer);
                        document.addChild(statement);
                    }
                    case "syntax" -> {
                        var statement = parseSyntax(document, tokenizer);
                        document.addChild(statement);
                    }
                    case "option" -> {
                        var statement = parseOption(tokenizer, OptionParser.STATEMENT);
                        document.addChild(statement);
                    }
                    case "message" -> {
                        var statement = parseMessage(document, tokenizer);
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
                        var statement = parseExtend(document, tokenizer);
                        document.addChild(statement);
                    }
                    default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
                }
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            var withPath = new ProtobufParserException(syntaxException.getMessage() + " while parsing " + (location == null ? "input" : location.getFileName()));
            withPath.setStackTrace(syntaxException.getStackTrace());
            throw withPath;
        }
    }

    private static ProtobufEmptyStatement parseEmpty(ProtobufTokenizer tokenizer) {
        return new ProtobufEmptyStatement(tokenizer.line());
    }

    private static ProtobufPackageStatement parsePackage(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        ProtobufParserException.check(document.packageName().isEmpty(),
                "Package can only be set once",
                tokenizer.line());
        var statement = new ProtobufPackageStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidType(name),
                "Unexpected token " + name, tokenizer.line());
        statement.setName(name);
        var end = tokenizer.nextRawToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static ProtobufSyntaxStatement parseSyntax(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        ProtobufParserException.check(document.children().isEmpty(),
                "Syntax should be the first statement",
                tokenizer.line());
        var statement = new ProtobufSyntaxStatement(tokenizer.line());
        var assignment = tokenizer.nextRawToken();
        ProtobufParserException.check(isAssignmentOperator(assignment),
                "Unexpected token " + assignment, tokenizer.line());
        var versionCodeToken = tokenizer.nextToken();
        if(!(versionCodeToken instanceof ProtobufToken.Literal(var versionCode, _))) {
            throw new ProtobufParserException("Unexpected token " + versionCodeToken, tokenizer.line());
        }
        var version = ProtobufVersion.of(versionCode)
                .orElseThrow(() -> new ProtobufParserException("Unknown protobuf version: \"" + versionCode + "\""));
        statement.setVersion(version);
        var end = tokenizer.nextRawToken();
        ProtobufParserException.check(isStatementEnd(end),
                "Unexpected token " + end, tokenizer.line());
        return statement;
    }

    private static <T> T parseOption(ProtobufTokenizer tokenizer, OptionParser<T> parser) throws IOException {
        var nameOrParensStart = tokenizer.nextRawToken();
        
        String name;
        boolean extension;
        if(isParensStart(nameOrParensStart)) {
            name = tokenizer.nextRawToken();
            var parensEnd = tokenizer.nextRawToken();
            ProtobufParserException.check(isParensEnd(parensEnd),
                    "Unexpected token " + parensEnd, tokenizer.line());
            extension = true;
        }else {
           name = nameOrParensStart;
           extension = false;
        }

        List<String> membersAccessed;
        var membersAccessedOrAssignment = tokenizer.nextRawToken();
        if(isAssignmentOperator(membersAccessedOrAssignment)) {
            membersAccessed = List.of();
        }else if(membersAccessedOrAssignment.charAt(0) == '.') {
            var accessed = membersAccessedOrAssignment.substring(1);
            ProtobufParserException.check(isValidType(accessed),
                    "Unexpected token " + accessed, tokenizer.line());
            membersAccessed = Arrays.asList(accessed.split("\\."));
            var assignment = tokenizer.nextRawToken();
            ProtobufParserException.check(isAssignmentOperator(assignment),
                    "Unexpected token " + assignment, tokenizer.line());
        }else{
            throw new ProtobufParserException("Unexpected token " + membersAccessedOrAssignment, tokenizer.line());
        }
        var optionName = new ProtobufOptionName(name, extension, membersAccessed);
        var optionValue = readExpression(tokenizer);
        return parser.parse(tokenizer, optionName, optionValue);
    }
    
    private interface OptionParser<T> {
         OptionParser<ProtobufOptionStatement> STATEMENT = (tokenizer, name, value) -> {
             var end = tokenizer.nextRawToken();
             ProtobufParserException.check(isStatementEnd(end),
                     "Unexpected token " + end, tokenizer.line());
             var statement = new ProtobufOptionStatement(tokenizer.line());
             statement.setName(name);
             statement.setValue(value);
             return statement;
         };

        OptionParser<ProtobufOptionExpression> EXPRESSION = (tokenizer, name, value) -> {
            var expression = new ProtobufOptionExpression(tokenizer.line());
            expression.setName(name);
            expression.setValue(value);
            return expression;
        };

        T parse(ProtobufTokenizer tokenizer, ProtobufOptionName name, ProtobufExpression value) throws IOException;
    }

    private static ProtobufMessageStatement parseMessage(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufMessageStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(document, tokenizer);
                    statement.addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.addChild(child);
                }
                case "extend" -> {
                    var child = parseExtend(document, tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer, false);
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

    private static ProtobufExtendStatement parseExtend(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        var version = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        ProtobufParserException.check(version != ProtobufVersion.PROTOBUF_3, // TODO: In proto3 extensions are supported for options
                "Extensions are only supported in proto3 for options", tokenizer.line());
        var statement = new ProtobufExtendStatement(tokenizer.line());
        var qualifiedName = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidType(qualifiedName),
                "Unexpected token: " + qualifiedName, tokenizer.line());
        var reference = new ProtobufUnresolvedObjectTypeReference(qualifiedName);
        statement.setDeclaration(reference);
        var objectStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(document, tokenizer);
                    statement.addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.addChild(child);
                }
                case "extend" -> {
                    var child = parseExtend(document, tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer, false);
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

    private static ProtobufFieldStatement parseField(ProtobufDocumentTree document, boolean parseModifier, ProtobufTokenizer tokenizer, String token) throws IOException {
        ProtobufFieldStatement.Modifier modifier;
        ProtobufTypeReference reference;
        if(parseModifier) {
            modifier = ProtobufFieldStatement.Modifier.of(token)
                    .orElse(ProtobufFieldStatement.Modifier.NONE);
           if(modifier == ProtobufFieldStatement.Modifier.NONE) {
               var version = document.syntax()
                       .orElse(ProtobufVersion.defaultVersion());
               if(token.equals("map")) {
                   reference = version == ProtobufVersion.PROTOBUF_3
                           ? new ProtobufMapTypeReference()
                           : new ProtobufUnresolvedObjectTypeReference(token);
               }else {
                   var primitiveType = ProtobufType.ofPrimitive(token);
                   if (primitiveType == ProtobufType.UNKNOWN) {
                       ProtobufParserException.check(isValidType(token),
                               "Unexpected token " + token, tokenizer.line());
                       reference = new ProtobufUnresolvedObjectTypeReference(token);
                   }else {
                       reference = new ProtobufPrimitiveTypeReference(primitiveType);
                   }
               }
           }else {
               var type = tokenizer.nextRawToken();
               ProtobufParserException.check(isValidType(type),
                       "Unexpected token " + type, tokenizer.line());
               reference = ProtobufTypeReference.of(type);
               ProtobufParserException.check(reference.protobufType() != ProtobufType.MAP,
                       "Map fields cannot have a modifier",
                       tokenizer.line());
           }
       }else {
           modifier = ProtobufFieldStatement.Modifier.NONE;
           reference = ProtobufTypeReference.of(token);
       }

        ProtobufFieldStatement child;
        if(reference.protobufType() == ProtobufType.GROUP) {
            if(document.syntax().isPresent() && document.syntax().get() == ProtobufVersion.PROTOBUF_3) {
                throw new ProtobufParserException("Group fields are not supported in proto3", tokenizer.line());
            }
            child = new ProtobufGroupFieldStatement(tokenizer.line());
        }else {
            child = new ProtobufFieldStatement(tokenizer.line());
            child.setType(reference);
        }
        child.setModifier(modifier);

        var nameOrTypeArgs = tokenizer.nextRawToken();
        if(isTypeParametersStart(nameOrTypeArgs)) {
            if(!(child.type() instanceof ProtobufMapTypeReference mapType) || mapType.isAttributed()) {
                throw new ProtobufParserException("Unexpected token " + nameOrTypeArgs, tokenizer.line());
            }

            var keyTypeToken = tokenizer.nextRawToken();
            ProtobufParserException.check(isValidIdent(keyTypeToken),
                    "Unexpected token " + keyTypeToken, tokenizer.line());
            var keyType = ProtobufTypeReference.of(keyTypeToken);
            if(keyType.protobufType() == ProtobufType.MAP || keyType.protobufType() == ProtobufType.GROUP) {
                throw new ProtobufParserException("Unexpected token " + keyTypeToken, tokenizer.line());
            }
            mapType.setKeyType(keyType);

            var separator = tokenizer.nextRawToken();
            ProtobufParserException.check(isArraySeparator(separator),
                    "Unexpected token " + separator, tokenizer.line());

            var valueTypeToken = tokenizer.nextRawToken();
            ProtobufParserException.check(isValidIdent(valueTypeToken),
                    "Unexpected token " + valueTypeToken, tokenizer.line());
            var valueType = ProtobufTypeReference.of(valueTypeToken);
            mapType.setValueType(valueType);

            var end = tokenizer.nextRawToken();
            ProtobufParserException.check(isTypeParametersEnd(end),
                    "Unexpected token " + end, tokenizer.line());

            var childName = tokenizer.nextRawToken();
            ProtobufParserException.check(isValidIdent(childName),
                    "Unexpected token " + childName, tokenizer.line());
            child.setName(childName);
        }else if(isValidIdent(nameOrTypeArgs)) {
            child.setName(nameOrTypeArgs);
        }else {
            throw new ProtobufParserException("Unexpected token " + nameOrTypeArgs, tokenizer.line());
        }

        var operator = tokenizer.nextRawToken();
        ProtobufParserException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());
        var index = tokenizer.nextToken();
        if(index instanceof ProtobufToken.Number(var number) && number instanceof ProtobufInteger integer) {
            child.setIndex(integer);
        }else {
            throw new ProtobufParserException("Unexpected token " + index, tokenizer.line());
        }

        var bodyStartOrStatementEndToken = parseFieldOptions(tokenizer, child);
        if(child instanceof ProtobufGroupFieldStatement groupStatement && isBodyStart(bodyStartOrStatementEndToken)) {
            String groupToken;
            while(!isBodyEnd(groupToken = tokenizer.nextRawToken())) {
                switch (groupToken) {
                    case STATEMENT_END -> {
                        var groupChild = parseEmpty(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "message" -> {
                        var groupChild = parseMessage(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "enum" -> {
                        var groupChild = parseEnum(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extend" -> {
                        var groupChild = parseExtend(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extensions" -> {
                        var groupChild = parseExtensions(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "reserved" -> {
                        var groupChild = parseReserved(tokenizer, false);
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
        } else if(isStatementEnd(bodyStartOrStatementEndToken)) {
            return child;
        }else {
            throw new ProtobufParserException("Unexpected token " + bodyStartOrStatementEndToken, tokenizer.line());
        }
    }

    private static String parseFieldOptions(ProtobufTokenizer tokenizer, ProtobufFieldStatement child) throws IOException {
        var maybeOptionStart = tokenizer.nextRawToken();
        if(!isArrayStart(maybeOptionStart)) {
            return maybeOptionStart;
        }

        String optionSeparatorOrOptionEnd;
        do {
            var expression = parseOption(tokenizer, OptionParser.EXPRESSION);
            child.addOption(expression);
        }while (isArraySeparator(optionSeparatorOrOptionEnd = tokenizer.nextRawToken()));

        ProtobufParserException.check(isArrayEnd(optionSeparatorOrOptionEnd),
                "Unexpected token " + optionSeparatorOrOptionEnd, tokenizer.line());

        return tokenizer.nextRawToken();
    }

    private static ProtobufEnumStatement parseEnum(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufEnumStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer, true);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseEnumConstant(token, tokenizer);
                    child.setType(new ProtobufEnumTypeReference(statement));
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufEnumConstantStatement parseEnumConstant(String token, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufEnumConstantStatement(tokenizer.line());
        statement.setModifier(ProtobufFieldStatement.Modifier.NONE);
        statement.setName(token);

        var operator = tokenizer.nextRawToken();
        ProtobufParserException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());

        var index = tokenizer.nextToken();
        if(index instanceof ProtobufToken.Number(var number) && number instanceof ProtobufInteger integer) {
            statement.setIndex(integer);
        }else {
            throw new ProtobufParserException("Unexpected token " + index, tokenizer.line());
        }

        var statementEndToken = parseFieldOptions(tokenizer, statement);

        ProtobufParserException.check(isStatementEnd(statementEndToken),
                "Unexpected token " + statementEndToken, tokenizer.line());
        return statement;
    }

    private static ProtobufExtensionsStatement parseExtensions(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufExtensionsStatement(tokenizer.line());

        while(true) {
            var valueOrMinToken = tokenizer.nextToken();
            if (!(valueOrMinToken instanceof ProtobufToken.Number(var valueOrMinNumber)) || !(valueOrMinNumber instanceof ProtobufInteger valueOrMinInt)) {
                throw new ProtobufParserException("Unexpected token " + valueOrMinToken, tokenizer.line());
            }

            var operator = tokenizer.nextRawToken();
            if(isRangeOperator(operator)) {
                var maxToken = tokenizer.nextToken();
                var range = switch (maxToken) {
                    case ProtobufToken.Number(var maxNumber) when maxNumber instanceof ProtobufInteger maxInt -> new ProtobufRange.Bounded(valueOrMinInt, maxInt);
                    case ProtobufToken.Raw(var token) when isMax(token) -> new ProtobufRange.LowerBounded(valueOrMinInt);
                    default -> throw new ProtobufParserException("Unexpected token " + maxToken, tokenizer.line());
                };

                var expression = new ProtobufIntegerRangeExpression(tokenizer.line());
                expression.setValue(range);
                statement.addExpression(expression);

                operator = tokenizer.nextRawToken();
            }else {
                var expression = new ProtobufNumberExpression(tokenizer.line());
                expression.setValue(valueOrMinInt);
                statement.addExpression(expression);
            }

            if (isStatementEnd(operator)) {
                if(statement.expressions().isEmpty()) {
                    throw new ProtobufParserException("Unexpected token " + operator, tokenizer.line());
                }

                break;
            } else if (!isArraySeparator(operator)) {
                throw new ProtobufParserException("Unexpected token " + operator, tokenizer.line());
            }
        }

        return statement;
    }

    private static ProtobufReservedStatement parseReserved(ProtobufTokenizer tokenizer, boolean enumeration) throws IOException {
        var statement = new ProtobufReservedStatement(tokenizer.line());

        bodyLoop: {
            while (true) {
                var valueOrMinToken = tokenizer.nextToken();
                switch (valueOrMinToken) {
                    case ProtobufToken.Literal(var value, _) -> {
                        var expression = new ProtobufLiteralExpression(tokenizer.line());
                        expression.setValue(value);
                        statement.addExpression(expression);
                        var operator = tokenizer.nextRawToken();
                        if(isStatementEnd(operator)) {
                            if(statement.expressions().isEmpty()) {
                                throw new ProtobufParserException("Unexpected token " + operator, tokenizer.line());
                            }

                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    case ProtobufToken.Number(var number) when number instanceof ProtobufInteger valueOrMinInt -> {
                        var operator = tokenizer.nextRawToken();
                        if(isRangeOperator(operator)) {
                            var maxToken = tokenizer.nextToken();
                            var range = switch (maxToken) {
                                case ProtobufToken.Number(var maxNumber) when maxNumber instanceof ProtobufInteger maxInt -> new ProtobufRange.Bounded(valueOrMinInt, maxInt);
                                case ProtobufToken.Raw(var token) when isMax(token) -> new ProtobufRange.LowerBounded(valueOrMinInt);
                                default -> throw new ProtobufParserException("Unexpected token " + maxToken, tokenizer.line());
                            };

                            var expression = new ProtobufIntegerRangeExpression(tokenizer.line());
                            expression.setValue(range);
                            statement.addExpression(expression);

                            operator = tokenizer.nextRawToken();
                        }else {
                            var expression = new ProtobufNumberExpression(tokenizer.line());
                            expression.setValue(valueOrMinInt);
                            statement.addExpression(expression);
                        }

                        if(isStatementEnd(operator)) {
                            if(statement.expressions().isEmpty()) {
                                throw new ProtobufParserException("Unexpected token " + operator, tokenizer.line());
                            }

                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufParserException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    default -> throw new ProtobufParserException("Unexpected token " + valueOrMinToken, tokenizer.line());
                }
            }
        }

        return statement;
    }

    private static ProtobufServiceStatement parseService(ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufServiceStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
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
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var inputType = parseMethodType(tokenizer);
        statement.setInputType(inputType);
        var returnsToken = tokenizer.nextRawToken();
        ProtobufParserException.check(Objects.equals(returnsToken, "returns"),
                "Unexpected token: " + returnsToken, tokenizer.line());
        var outputType = parseMethodType(tokenizer);
        statement.setOutputType(outputType);
        var objectStartOrStatementEnd = tokenizer.nextRawToken();
        if(isBodyStart(objectStartOrStatementEnd)){
            String token;
            while (!isBodyEnd(token = tokenizer.nextRawToken())) {
                switch (token) {
                    case STATEMENT_END -> {
                        var child = parseEmpty(tokenizer);
                        statement.addChild(child);
                    }
                    case "option" -> {
                        var child = parseOption(tokenizer, OptionParser.STATEMENT);
                        statement.addChild(child);
                    }
                    default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
                }
            }
        }else if(!isStatementEnd(objectStartOrStatementEnd)) {
            throw new ProtobufParserException("Unexpected token: " + objectStartOrStatementEnd, tokenizer.line());
        }
        return statement;
    }

    private static ProtobufMethodStatement.Type parseMethodType(ProtobufTokenizer tokenizer) throws IOException {
        var typeStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isParensStart(typeStart),
                "Unexpected token: " + typeStart, tokenizer.line());
        var typeOrModifier = tokenizer.nextRawToken();
        ProtobufTypeReference typeReference;
        boolean stream;
        if(isStreamModifier(typeOrModifier)) {
            typeReference = ProtobufTypeReference.of(tokenizer.nextRawToken());
            stream = true;
        }else {
            typeReference = ProtobufTypeReference.of(typeOrModifier);
            stream = false;
        }
        ProtobufParserException.check(typeReference instanceof ProtobufUnresolvedObjectTypeReference,
                "Unexpected type, only messages can be used: " + typeReference.name(), tokenizer.line());
        var typeEnd = tokenizer.nextRawToken();
        ProtobufParserException.check(isParensEnd(typeEnd),
                "Unexpected token: " + typeEnd, tokenizer.line());
        return new ProtobufMethodStatement.Type(typeReference, stream);
    }

    private static ProtobufOneofFieldStatement parseOneof(ProtobufDocumentTree document, ProtobufTokenizer tokenizer) throws IOException {
        var statement = new ProtobufOneofFieldStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufParserException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufParserException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
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
        var token = tokenizer.nextToken();
        switch (token) {
            case ProtobufToken.Literal literal -> {
                importStatement.setModifier(ProtobufImportStatement.Modifier.NONE);
                importStatement.setLocation(literal.value());
                var end = tokenizer.nextRawToken();
                ProtobufParserException.check(isStatementEnd(end),
                        "Unexpected token " + end, tokenizer.line());
            }
            case ProtobufToken.Raw raw -> {
                var modifier = ProtobufImportStatement.Modifier.of(raw.value())
                        .orElseThrow(() -> new ProtobufParserException("Unexpected token " + raw.value(), tokenizer.line()));
                importStatement.setModifier(modifier);
                var locationToken = tokenizer.nextToken();
                if(!(locationToken instanceof ProtobufToken.Literal(var location, _))) {
                    throw new ProtobufParserException("Unexpected token " + locationToken, tokenizer.line());
                }
                importStatement.setLocation(location);
                var end = tokenizer.nextRawToken();
                ProtobufParserException.check(isStatementEnd(end),
                        "Unexpected token " + end, tokenizer.line());
            }
            default -> throw new ProtobufParserException("Unexpected token " + token, tokenizer.line());
        }
        return importStatement;
    }

    private static ProtobufExpression readExpression(ProtobufTokenizer tokenizer) throws IOException {
        var line = tokenizer.line();
        return switch (tokenizer.nextToken()) {
            case ProtobufToken.Boolean bool -> {
                var expression = new ProtobufBoolExpression(line);
                expression.setValue(bool.value());
                yield expression;
            }

            case ProtobufToken.Number number -> {
                var expression = new ProtobufNumberExpression(line);
                expression.setValue(number.value());
                yield expression;
            }

            case ProtobufToken.Literal literal -> {
                var expression = new ProtobufLiteralExpression(line);
                expression.setValue(literal.value());
                yield expression;
            }

            case ProtobufToken.Raw raw
                    when isNullExpression(raw.value()) -> new ProtobufNullExpression(line);
            case ProtobufToken.Raw raw
                    when isBodyStart(raw.value()) -> {
                var expression = new ProtobufMessageValueExpression(line);
                loop: {
                    while (true) {
                        var keyToken = tokenizer.nextToken();
                        switch (keyToken) {
                            case ProtobufToken.Raw(var value)
                                    when isBodyEnd(value) -> {
                                break loop;
                            }
                            case ProtobufToken.Literal(var key, _) -> {
                                var keyValueSeparatorToken = tokenizer.nextRawToken();
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
            case ProtobufToken.Raw raw
                    when isValidIdent(raw.value()) -> {
                var expression = new ProtobufEnumConstantExpression(line);
                expression.setName(raw.value());
                yield expression;
            }
            case ProtobufToken.Raw raw -> throw new ProtobufParserException("Unexpected token " + raw.value(), line);
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

    private static boolean isValidType(String token) {
        var length = token.length();
        if(length == 0) {
            return false;
        }

        if(token.charAt(length - 1) == '.') {
            return false;
        }

        int start;
        if(token.charAt(0) == '.') {
            if(length == 1) {
                return false;
            }
            start = 1;
        }else {
            start = 0;
        }

        for(var end = start + 1; end < length; end++) {
            if (token.charAt(end) != '.') {
                continue;
            }

            if(!isValidIdent(token.substring(start, end))) {
                return false;
            }

            start = end + 1;
        }

        return isValidIdent(token.substring(start, length));
    }

    private static boolean isValidIdent(String token) {
        var length = token.length();
        if(length == 0) {
            return false;
        }

        var head = token.charAt(0);
        if(!Character.isLetter(head) && token.charAt(0) != '_') {
            return false;
        }

        for(var i = 1; i < length; i++) {
            var entry = token.charAt(i);
            if(!Character.isLetterOrDigit(entry) && entry != '_') {
                return false;
            }
        }

        return true;
    }

    private static boolean isNullExpression(String token) {
        return Objects.equals(token, NULL);
    }

    private static boolean isParensStart(String typeStart) {
        return Objects.equals(typeStart, PARENS_START);
    }

    private static boolean isParensEnd(String typeEnd) {
        return Objects.equals(typeEnd, PARENS_END);
    }

    private static boolean isStreamModifier(String typeOrModifier) {
        return Objects.equals(typeOrModifier, STREAM);
    }

    private static boolean isMax(String token) {
        return Objects.equals(token, MAX);
    }
}
