package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProtobufParser {
    private static final Set<ProtobufDocumentTree> BUILT_INS;
    private static final Set<Character> SYMBOLS = Set.of('@', '!', '(', ')');
    private static final String STATEMENT_END = ";";
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String LIST_SEPARATOR = ",";
    private static final String RANGE_OPERATOR = "to";
    private static final String MAX_KEYWORD = "max";
    private static final String TYPE_SELECTOR = ".";
    private static final String TYPE_SELECTOR_SPLITTER = "\\.";
    private static final String TYPE_PARAMETERS_START = "<";
    private static final String TYPE_PARAMETERS_END = ">";
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String NULL = "null";
    private static final String KEY_VALUE_SEPARATOR = ":";

    private ProtobufParser() {

    }

    static {
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufParserException("Missing built-in .proto");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            BUILT_INS = parse(builtInTypesPath);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufParserException("Missing built-in .proto");
        }
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

            attributeImports(results);
            for (var result : results) {
                attributeDocument(result);
            }
            return results;
        }
    }

    public static ProtobufDocumentTree parseOnly(String input) {
        try {
            var result = doParse(null, new StringReader(input));
            attributeDocument(result);
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
        attributeImports(List.of(result));
        attributeDocument(result);
        return result;
    }

    private static void attributeImports(Collection<ProtobufDocumentTree> documents) {
        var mapSize = documents.size();
        if(BUILT_INS != null) {
            mapSize += BUILT_INS.size();
        }
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        for(var document : documents) {
            canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        }
        if(BUILT_INS != null) {
            for(var document : BUILT_INS) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
            }
        }
        for(var document : documents) {
            for(var child : document.body().children()) {
                if(child instanceof ProtobufImportStatement importStatement && !importStatement.isAttributed()) {
                    var imported = canonicalPathToDocumentMap.get(importStatement.location());
                    ProtobufParserException.check(imported != null,
                            "Cannot resolve import %s", importStatement.line(), importStatement.location());
                    importStatement.setDocument(imported);
                }
            }
        }
    }

    private static void attributeDocument(ProtobufDocumentTree document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);
        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            switch (tree) {
                case ProtobufTree.WithBody<?> body -> queue.addAll(body.body().children());

                case ProtobufExpression protobufExpression -> {
                    switch (protobufExpression) {
                        case ProtobufBoolExpression protobufBoolExpression -> {

                        }

                        case ProtobufEnumConstantExpression protobufEnumConstantExpression -> {

                        }

                        case ProtobufIntegerExpression protobufIntegerExpression -> {

                        }

                        case ProtobufLiteralExpression protobufLiteralExpression -> {

                        }

                        case ProtobufNullExpression protobufNullExpression -> {

                        }

                        case ProtobufRangeExpression protobufRangeExpression -> {

                        }

                        case ProtobufReservedChild protobufReservedChild -> {

                        }

                        case ProtobufMessageValueExpression protobufMessageValueExpression -> {

                        }
                    }
                }

                case ProtobufStatement protobufStatement -> {
                    switch (protobufStatement) {
                        case ProtobufEmptyStatement ignored -> {
                            // Nothing to check
                        }

                        case ProtobufExtensionsStatement protobufExtension -> {

                        }

                        case ProtobufFieldStatement protobufField -> {
                            attributeType(document, protobufField);
                        }

                        case ProtobufImportStatement protobufImport -> {
                            if(!protobufImport.hasDocument()) {
                                throw new InternalError("Import statement should already be attributed");
                            }
                        }

                        case ProtobufOptionStatement protobufOption -> queue.add(protobufOption.value());

                        case ProtobufPackageStatement ignored -> {
                            // Nothing to check
                        }

                        case ProtobufReservedStatement protobufReserved -> {

                        }

                        case ProtobufSyntaxStatement protobufSyntax -> {
                            if(!protobufSyntax.hasVersion()) {
                                throw new InternalError("Syntax statement should already be attributed");
                            }
                        }

                        default -> throw new IllegalStateException("Unexpected value: " + protobufStatement);
                    }
                }
            }
        }
    }

    private static void attributeType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree) {
        var typeReferences = new LinkedList<ProtobufTypeReference>();
        typeReferences.add(typedFieldTree.type());
        while (!typeReferences.isEmpty()) {
            switch (typeReferences.removeFirst()) {
                case ProtobufGroupTypeReference protobufGroupType -> {
                    if(!protobufGroupType.isAttributed()) {
                        throw throwUnattributableType(typedFieldTree);
                    }
                }

                case ProtobufMapTypeReference protobufMapType -> {
                    var keyType = protobufMapType.keyType();
                    typeReferences.add(keyType);
                    var valueType = protobufMapType.valueType();
                    typeReferences.add(valueType);
                }

                case ProtobufMessageOrEnumTypeReference fieldType -> {
                    var accessed = fieldType.name();
                    var types = accessed.split(TYPE_SELECTOR_SPLITTER);
                    var parent = typedFieldTree.parent();

                    // Look for the type definition starting from the field's parent
                    // Only the first result should be considered because of shadowing (i.e. if a name is reused in an inner scope, the inner scope should override the outer scope)
                    ProtobufTree.WithBody<?> resolvedType = null;
                    while (parent != null && resolvedType == null) {
                        resolvedType = parent.body()
                                .getDirectChildByNameAndType(types[0], ProtobufTree.WithBody.class)
                                .orElse(null);
                        parent = parent.parent() instanceof ProtobufTree.WithBody<?> validParent ? validParent : null;
                    }

                    if (resolvedType != null) { // Found a match in the parent scope
                        // Try to resolve the type reference in the matched scope
                        for (var index = 1; index < types.length; index++) {
                            resolvedType = resolvedType.body()
                                    .getDirectChildByNameAndType(types[index], ProtobufTree.WithBody.class)
                                    .orElseThrow(() -> throwUnattributableType(typedFieldTree));
                        }
                    } else { // No match found in the parent scope, try to resolve the type reference through imports
                        for (var statement : document.body().children()) {
                            if (!(statement instanceof ProtobufImportStatement importStatement)) {
                                continue;
                            }

                            var imported = importStatement.document();
                            if (imported == null) {
                                continue;
                            }

                            var simpleName = imported.packageName()
                                    .map(packageName -> accessed.startsWith(packageName + TYPE_SELECTOR) ? accessed.substring(packageName.length() + 1) : null)
                                    .orElse(accessed);
                            var simpleImportName = simpleName.split(TYPE_SELECTOR_SPLITTER);
                            resolvedType = imported;
                            for (var i = 0; i < simpleImportName.length && resolvedType != null; i++) {
                                resolvedType = resolvedType.body()
                                        .getDirectChildByNameAndType(simpleImportName[i], ProtobufTree.WithBody.class)
                                        .orElse(null);
                            }
                            if (resolvedType != null) {
                                break;
                            }
                        }

                        if (resolvedType == null) {
                            throw throwUnattributableType(typedFieldTree);
                        }
                    }

                    fieldType.setDeclaration(resolvedType);
                }

                case ProtobufPrimitiveTypeReference ignored -> {
                    // Nothing to do
                }
            }
        }
    }

    private static ProtobufParserException throwUnattributableType(ProtobufFieldStatement typedFieldTree) {
        return new ProtobufParserException(
                "Cannot resolve type \"%s\" in field \"%s\"%s",
                typedFieldTree.line(),
                typedFieldTree.type().name(),
                typedFieldTree.name(),
                typedFieldTree.parent() instanceof ProtobufTree.WithName withName ? " inside \"%s\"".formatted(withName.name())  : ""
        );
    }

    private static ProtobufDocumentTree doParse(Path location, Reader input) throws IOException {
        try {
            var document = new ProtobufDocumentTree(location);
            var tokenizer = new ProtobufTokenizer(input);
            String token;
            ProtobufTree tree = document;
            while ((token = tokenizer.nextToken()) != null) {
                var line = tokenizer.line();
                tree = switch (tree) {
                    case ProtobufPackageStatement packageStatement -> handlePackageStatement(packageStatement, token, line);
                    case ProtobufSyntaxStatement syntaxStatement -> handleSyntaxStatement(syntaxStatement, tokenizer, token, line);
                    case ProtobufOptionStatement optionStatement -> handleOptionStatement(optionStatement, tokenizer, token, line);
                    case ProtobufFieldStatement fieldStatement -> handleFieldStatement(fieldStatement, tokenizer, token, line);
                    case ProtobufImportStatement importStatement -> handleImportStatement(importStatement, token, line);
                    case ProtobufEnumStatement enumTree -> handleEnumTree(enumTree, token, line);
                    case ProtobufMessageStatement messageTree -> handleMessageTree(document, messageTree, token, line);
                    case ProtobufOneofStatement oneofTree -> handleOneofTree(oneofTree, token, line);
                    case ProtobufServiceStatement serviceTree -> handleServiceTree(serviceTree, token, line);
                    case ProtobufMethodStatement methodStatement -> handleMethodStatement(methodStatement, token, line);
                    case ProtobufReservedStatement reservedListStatement -> handleReservedListStatement(reservedListStatement, token, line);
                    case ProtobufExtensionsStatement extensionsListStatement -> handleExtensionsListStatement(extensionsListStatement, token, line);
                    case ProtobufDocumentTree documentTree -> handleDocumentTree(documentTree, token, line);
                    case ProtobufExpression ignored -> throw new ProtobufParserException("Expressions cannot be parsed");
                    case ProtobufEmptyStatement ignored -> throw new ProtobufParserException("Empty statements cannot be parsed");
                };
            }
            if(tree != document) {
                throw new ProtobufParserException("Unexpected end of input");
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            throw ProtobufParserException.wrap(syntaxException, location);
        }
    }

    private static ProtobufExpression readExpression(ProtobufTokenizer tokenizer) throws IOException {
        var token = tokenizer.nextToken();
        if(token == null) {
            return null;
        }

        var line = tokenizer.line();
        if(isNullExpression(token)) {
            return new ProtobufNullExpression(line);
        }

        if(isObjectStart(token)) {
            var expression = new ProtobufMessageValueExpression(line);

            while (true) {
                var keyToken = tokenizer.nextToken();
                if(keyToken == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }

                if(isObjectEnd(keyToken)) {
                    break;
                }

                var key = parseStringLiteral(keyToken)
                        .orElseThrow(() -> new ProtobufParserException("Unexpected token " + keyToken, line));

                var keyValueSeparatorToken = tokenizer.nextToken();
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

    private static ProtobufTree handleMethodStatement(ProtobufMethodStatement methodStatement, String token, int line) {
        if(!methodStatement.hasName()) {
            methodStatement.setName(token);
            return methodStatement;
        }else if(!methodStatement.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token " + token, line);
            var block = new ProtobufBody<ProtobufMethodChild>(line);
            methodStatement.setBody(block);
            return methodStatement;
        } else {
            return switch (token) {
                case OBJECT_END -> methodStatement.parent();
                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    methodStatement.body()
                            .addChild(statement);
                    yield methodStatement;
                }
                case "option" -> {
                    var statement = new ProtobufOptionStatement(line);
                    methodStatement.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, line);
            };
        }
    }

    private static ProtobufTree handleExtensionsListStatement(ProtobufExtensionsStatement extensionsTree, String token, int line) {
        if(isStatementEnd(token)) {
            return extensionsTree.parent();
        }
        return extensionsTree;
    }

    private static ProtobufTree handleReservedListStatement(ProtobufReservedStatement reservedTree, String token, int line) {
        if(isStatementEnd(token)) {
            return reservedTree.parent();
        }
        return reservedTree;
    }

    private static ProtobufTree handleServiceTree(ProtobufServiceStatement serviceTree, String token, int line) {
        if(!serviceTree.hasName()) {
            serviceTree.setName(token);
            return serviceTree;
        }else if(!serviceTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token " + token, line);
            var body = new ProtobufBody<ProtobufServiceChild>(line);
            serviceTree.setBody(body);
            return serviceTree;
        } else {
            return switch (token) {
                case OBJECT_END -> serviceTree.parent();
                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    serviceTree.body()
                            .addChild(statement);
                    yield serviceTree;
                }
                case "option" -> {
                    var statement = new ProtobufOptionStatement(line);
                    serviceTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "rpc" -> {
                    var statement = new ProtobufMethodStatement(line);
                    serviceTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, line);
            };
        }
    }

    private static ProtobufTree handleEnumTree(ProtobufEnumStatement enumTree, String token, int line) {
        if(!enumTree.hasName()) {
            enumTree.setName(token);
            return enumTree;
        }else if(!enumTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token " + token, line);
            var block = new ProtobufBody<ProtobufEnumChild>(line);
            enumTree.setBody(block);
            return enumTree;
        } else {
            return switch (token) {
                case OBJECT_END -> enumTree.parent();
                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    enumTree.body()
                            .addChild(statement);
                    yield enumTree;
                }
                case "extensions" -> {
                    var statement = new ProtobufExtensionsStatement(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "option" -> {
                    var statement = new ProtobufOptionStatement(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "reserved" -> {
                    var statement = new ProtobufReservedStatement(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> {
                    var statement = new ProtobufEnumConstant(line);
                    statement.setModifier(ProtobufFieldStatement.Modifier.nothing());
                    statement.setType(new ProtobufMessageOrEnumTypeReference(enumTree.name(), enumTree));
                    statement.setName(token);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        }
    }

    private static ProtobufTree handleMessageTree(ProtobufDocumentTree document, ProtobufMessageStatement messageTree, String token, int line) {
        if(!messageTree.hasName()) {
            messageTree.setName(token);
            return messageTree;
        }else if(!messageTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token " + token, line);
            var block = new ProtobufBody<ProtobufMessageChild>(line);
            messageTree.setBody(block);
            return messageTree;
        } else {
            return switch (token) {
                case OBJECT_END -> messageTree.parent();
                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield messageTree;
                }
                case "option" -> {
                    var statement = new ProtobufOptionStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "message" -> {
                    var statement = new ProtobufMessageStatement(line, false);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "enum" -> {
                    var statement = new ProtobufEnumStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "extend" -> {
                    var statement = new ProtobufMessageStatement(line, true);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "extensions" -> {
                    var statement = new ProtobufExtensionsStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "reserved" -> {
                    var statement = new ProtobufReservedStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "oneof" -> {
                    var statement = new ProtobufOneofStatement(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> {
                    var statement = new ProtobufFieldStatement(line);
                    var modifier = ProtobufFieldStatement.Modifier.of(token);
                    if(modifier.type() == ProtobufFieldStatement.Modifier.Type.NOTHING) {
                        var version = document.syntax()
                                .orElse(ProtobufVersion.defaultVersion());
                        ProtobufParserException.check(version == ProtobufVersion.PROTOBUF_3,
                                "Unexpected token " + token, line);
                        var reference = ProtobufTypeReference.of(token);
                        statement.setType(reference);
                    }
                    statement.setModifier(modifier);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        }
    }

    private static ProtobufTree handleOneofTree(ProtobufOneofStatement oneofTree, String token, int line) {
        if(!oneofTree.hasName()) {
            oneofTree.setName(token);
            return oneofTree;
        }else if(!oneofTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token " + token, line);
            var block = new ProtobufBody<ProtobufOneofChild>(line);
            oneofTree.setBody(block);
            return oneofTree;
        } else {
            return switch (token) {
                case OBJECT_END -> oneofTree.parent();
                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    oneofTree.body()
                            .addChild(statement);
                    yield oneofTree;
                }
                case "option" -> {
                    var statement = new ProtobufOptionStatement(line);
                    oneofTree.body()
                            .addChild(statement);
                    yield oneofTree;
                }
                default -> {
                    var statement = new ProtobufFieldStatement(line);
                    var reference = ProtobufTypeReference.of(token);
                    statement.setType(reference);
                    statement.setModifier(ProtobufFieldStatement.Modifier.nothing());
                    oneofTree.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        }
    }

    private static ProtobufTree handleDocumentTree(ProtobufDocumentTree documentTree, String token, int line) {
        return switch (token) {
            case STATEMENT_END -> {
                var statement = new ProtobufEmptyStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield documentTree;
            }
            case "package" -> {
                var statement = new ProtobufPackageStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "syntax" -> {
                var statement = new ProtobufSyntaxStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "option" -> {
                var statement = new ProtobufOptionStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "message" -> {
                var statement = new ProtobufMessageStatement(line, false);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "enum" -> {
                var statement = new ProtobufEnumStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "service" -> {
                var statement = new ProtobufServiceStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "import" -> {
                var statement = new ProtobufImportStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "extend" -> {
                var statement = new ProtobufMessageStatement(line, true);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            default -> throw new ProtobufParserException("Unexpected token " + token, line);
        };
    }

    private static ProtobufTree handleImportStatement(ProtobufImportStatement importStatement, String token, int line) {
        if (!importStatement.hasLocation()) {
            var literal = parseStringLiteral(token)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token " + token, line));
            importStatement.setLocation(literal);
            return importStatement;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return importStatement.parent();
        }
    }

    private static ProtobufTree handleFieldStatement(ProtobufFieldStatement fieldStatement, ProtobufTokenizer tokenizer, String token, int line) throws IOException {
        if(!fieldStatement.hasModifier()) {
            var modifier = ProtobufFieldStatement.Modifier.of(token);
            fieldStatement.setModifier(modifier);
            return fieldStatement;
        }else if(!fieldStatement.hasType()) {
            fieldStatement.setType(ProtobufTypeReference.of(token));
            return fieldStatement;
        } else if (!fieldStatement.hasName()) {
            if(isTypeParametersStart(token)) {
                if(!(fieldStatement.type() instanceof ProtobufMapTypeReference mapType) || mapType.isAttributed()) {
                    throw new ProtobufParserException("Unexpected token " + token, line);
                }

                var keyTypeToken = tokenizer.nextToken();
                if(keyTypeToken == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }
                var keyType = ProtobufTypeReference.of(keyTypeToken);
                mapType.setKeyType(keyType);

                var separator = tokenizer.nextToken();
                if(separator == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }
                ProtobufParserException.check(isListSeparator(separator),
                        "Unexpected token " + separator, line);

                var valueTypeToken = tokenizer.nextToken();
                if(valueTypeToken == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }
                var valueType = ProtobufTypeReference.of(valueTypeToken);
                mapType.setValueType(valueType);

                var end = tokenizer.nextToken();
                if(end == null) {
                    throw new ProtobufParserException("Unexpected end of input");
                }
                ProtobufParserException.check(isTypeParametersEnd(end),
                        "Unexpected token " + end, line);
                return fieldStatement;
            }else if(isLegalIdentifier(token)) {
                fieldStatement.setName(token);
                return fieldStatement;
            }else {
                throw new ProtobufParserException("Unexpected token " + token, line);
            }
        }else if(!fieldStatement.hasIndex()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = new ProtobufIntegerExpression(line);
            var indexToken = tokenizer.nextToken();
            ProtobufParserException.check(indexToken != null,
                    "Unexpected end of input", line);
            var index = tokenizer.nextInt(false);
            expression.setValue(index);
            fieldStatement.setIndex(expression);
            return fieldStatement;
        } else if(isArrayStart(token)) {
            while (true) {
                var optionName = tokenizer.nextToken();
                ProtobufParserException.check(optionName != null,
                        "Unexpected end of input", line);
                var operator = tokenizer.nextToken();
                ProtobufParserException.check(operator != null,
                        "Unexpected end of input", line);
                ProtobufParserException.check(isAssignmentOperator(operator),
                        "Unexpected token " + operator, line);
                var optionValue = readExpression(tokenizer);
                var endOrContinue = tokenizer.nextToken();
                ProtobufParserException.check(endOrContinue != null,
                        "Unexpected end of input", line);
                fieldStatement.addOption(optionName, optionValue);
                if (isArrayEnd(endOrContinue)) {
                    break;
                } else if(!isListSeparator(endOrContinue)){
                    throw new ProtobufParserException("Unexpected token " + endOrContinue, line);
                }
            }
            return fieldStatement;
        } else if(fieldStatement instanceof ProtobufGroupFieldStatement groupField) {
            return switch (token) {
                case OBJECT_START -> {
                    var body = new ProtobufBody<ProtobufGroupChild>(line);
                    groupField.setBody(body);
                    yield groupField;
                }

                case OBJECT_END -> groupField.parent();

                case STATEMENT_END -> {
                    var statement = new ProtobufEmptyStatement(line);
                    groupField.body()
                            .addChild(statement);
                    yield groupField;
                }

                case "message" -> {
                    var statement = new ProtobufMessageStatement(line, false);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                case "enum" -> {
                    var statement = new ProtobufEnumStatement(line);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                case "extend" -> {
                    var statement = new ProtobufMessageStatement(line, true);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                case "extensions" -> {
                    var statement = new ProtobufExtensionsStatement(line);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                case "reserved" -> {
                    var statement = new ProtobufReservedStatement(line);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                case "oneof" -> {
                    var statement = new ProtobufOneofStatement(line);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }

                default -> {
                    var statement = new ProtobufFieldStatement(line);
                    var modifier = ProtobufFieldStatement.Modifier.of(token);
                    ProtobufParserException.check(modifier.type() != ProtobufFieldStatement.Modifier.Type.NOTHING,
                            "Unexpected token " + token, line);
                    statement.setModifier(modifier);
                    groupField.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        } else if(isStatementEnd(token)) {
            return fieldStatement.parent();
        } else {
            throw new ProtobufParserException("Unexpected token " + token, line);
        }
    }

    private static ProtobufTree handleOptionStatement(ProtobufOptionStatement optionStatement, ProtobufTokenizer tokenizer, String token, int line) throws IOException {
        if(!optionStatement.hasName()) {
            optionStatement.setName(token);
            return optionStatement;
        }else if(!optionStatement.hasValue()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = readExpression(tokenizer);
            optionStatement.setValue(expression);
            return optionStatement;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return optionStatement.parent();
        }
    }

    private static ProtobufTree handleSyntaxStatement(ProtobufSyntaxStatement syntaxStatement, ProtobufTokenizer tokenizer, String token, int line) throws IOException {
        if(!syntaxStatement.hasVersion()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = readExpression(tokenizer);
            if(!(expression instanceof ProtobufLiteralExpression literal)) {
                throw new ProtobufParserException("Unexpected token " + token, line);
            }
            syntaxStatement.setVersion(literal);
            return syntaxStatement;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return syntaxStatement.parent();
        }
    }

    private static ProtobufTree handlePackageStatement(ProtobufPackageStatement packageStatement, String token, int line) {
        if (!packageStatement.hasName()) {
            packageStatement.setName(token);
            return packageStatement;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return packageStatement.parent();
        }
    }

    private static boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private static boolean isObjectStart(String operator) {
        return Objects.equals(operator, OBJECT_START);
    }

    private static boolean isObjectEnd(String operator) {
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
        return !instruction.isBlank()
               && !Character.isDigit(instruction.charAt(0))
               && instruction.chars().mapToObj(entry -> (char) entry).noneMatch(SYMBOLS::contains);
    }

    private static boolean isNullExpression(String token) {
        return Objects.equals(token, NULL);
    }
}
