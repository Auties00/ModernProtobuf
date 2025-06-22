package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufMapType;
import it.auties.protobuf.parser.type.ProtobufMessageOrEnumType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProtobufParser {
    private static final Set<ProtobufDocument> BUILT_INS;
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

    public static Set<ProtobufDocument> parse(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Set.of(parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashSet<ProtobufDocument>();
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

    public static ProtobufDocument parseOnly(String input) {
        try {
            var result = doParse(null, new StringReader(input));
            attributeDocument(result);
            return result;
        }catch (IOException exception) {
            throw new InternalError("Unexpected exception", exception);
        }
    }

    public static ProtobufDocument parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.newBufferedReader(path));
        attributeImports(List.of(result));
        attributeDocument(result);
        return result;
    }

    private static void attributeImports(Collection<ProtobufDocument> documents) {
        var mapSize = documents.size();
        if(BUILT_INS != null) {
            mapSize += BUILT_INS.size();
        }
        Map<String, ProtobufDocument> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
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
                if(child instanceof ProtobufImport importStatement && !importStatement.isAttributed()) {
                    var imported = canonicalPathToDocumentMap.get(importStatement.location());
                    ProtobufParserException.check(imported != null,
                            "Cannot resolve import %s", importStatement.line(), importStatement.location());
                    importStatement.setDocument(imported);
                }
            }
        }
    }

    private static void attributeDocument(ProtobufDocument document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);
        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            switch (tree) {
                case ProtobufDocument protobufDocument -> {
                    for (var child : protobufDocument.body().children()) {
                        if(!child.isAttributed()) {
                            queue.add(child);
                        }
                    }
                }
                case ProtobufExpression protobufExpression -> {
                    if (protobufExpression.value() instanceof ProtobufExpression.Value.EnumConstant enumConstant) {
                        // FIXME: Check if enum exists
                        throw new UnsupportedOperationException();
                    }else if(!protobufExpression.isAttributed()){
                        throw new ProtobufParserException("Expected value in expression", protobufExpression.line());
                    }
                }
                case ProtobufStatement protobufStatement -> {
                    switch (protobufStatement) {
                        case ProtobufEmptyStatement emptyStatement -> {

                        }

                        case ProtobufEnum protobufEnum -> {

                        }

                        case ProtobufExtension protobufExtension -> {

                        }

                        case ProtobufExtensionsList protobufExtensionsList -> {

                        }

                        case ProtobufField protobufField -> {

                        }

                        case ProtobufImport protobufImport -> {

                        }

                        case ProtobufMessage protobufMessage -> {

                        }

                        case ProtobufMethod protobufMethod -> {

                        }

                        case ProtobufOneof protobufOneof -> {

                        }

                        case ProtobufOption protobufOption -> {

                        }

                        case ProtobufPackage protobufPackage -> {

                        }

                        case ProtobufReserved protobufReserved -> {

                        }

                        case ProtobufReservedList protobufReservedList -> {

                        }

                        case ProtobufService protobufService -> {

                        }

                        case ProtobufSyntax protobufSyntax -> {

                        }
                    }
                }
            }
        }
    }

    private void attributeType(ProtobufDocument document, ProtobufField typedFieldTree, ProtobufMessageOrEnumType fieldType) {
        if (fieldType.isAttributed()) {
            return;
        }

        var accessed = fieldType.name();
        var types = accessed.split(TYPE_SELECTOR_SPLITTER);
        var parent = typedFieldTree.parent();

        // Look for the type definition starting from the field's parent
        // Only the first result should be considered because of shadowing (i.e. if a name is reused in an inner scope, the inner scope should override the outer scope)
        ProtobufTree.WithBody<?> innerType = null;
        while (parent != null && innerType == null){
            innerType = parent.body()
                    .getDirectChildByNameAndType(types[0], ProtobufTree.WithBody.class)
                    .orElse(null);
            parent = parent.parent();
        }

        if(innerType != null) { // Found a match in the parent scope
            // Try to resolve the type reference in the matched scope
            for(var index = 1; index < types.length; index++){
                innerType = innerType.body()
                        .getDirectChildByNameAndType(types[index], ProtobufTree.WithBody.class)
                        .orElseThrow(() -> throwUnattributableType(typedFieldTree));
            }
        } else { // No match found in the parent scope, try to resolve the type reference through imports
            for(var statement : document.body().children()) {
                if(!(statement instanceof ProtobufImport importStatement)) {
                    continue;
                }

                var imported = importStatement.document();
                if(imported == null) {
                    continue;
                }

                var importedPackage = imported.packageName()
                        .orElse("");
                var importedName = accessed.startsWith(importedPackage + TYPE_SELECTOR)
                        ? accessed.substring(importedPackage.length() + 1) : accessed;
                var simpleImportName = importedName.split(TYPE_SELECTOR_SPLITTER);
                ProtobufTree.WithBody<?> type = document;
                for (var i = 0; i < simpleImportName.length && type != null; i++) {
                    type = type.body()
                            .getDirectChildByNameAndType(simpleImportName[i], ProtobufTree.WithBody.class)
                            .orElse(null);
                }
                if(type != null) {
                    innerType = type;
                    break;
                }
            }

            if(innerType == null) {
                throw throwUnattributableType(typedFieldTree);
            }
        }

        fieldType.setDeclaration(innerType);
    }

    private ProtobufParserException throwUnattributableType(ProtobufField typedFieldTree) {
        return new ProtobufParserException(
                "Cannot resolve type %s in field %s inside %s",
                typedFieldTree.line(),
                typedFieldTree.type().name(),
                typedFieldTree.name(),
                typedFieldTree.parent()
        );
    }

    private static ProtobufDocument doParse(Path location, Reader input) throws IOException {
        try {
            var document = new ProtobufDocument(location);
            var tokenizer = new ProtobufTokenizer(input);
            String token;
            ProtobufTree tree = document;
            while ((token = tokenizer.nextToken()) != null) {
                var line = tokenizer.line();
                tree = switch (tree) {
                    case ProtobufPackage packageStatement -> handlePackageStatement(packageStatement, token, line);
                    case ProtobufSyntax syntaxStatement -> handleSyntaxStatement(syntaxStatement, token, line);
                    case ProtobufOption optionStatement -> handleOptionStatement(optionStatement, token, line);
                    case ProtobufField fieldStatement -> handleFieldStatement(fieldStatement, tokenizer, token, line);
                    case ProtobufImport importStatement -> handleImportStatement(importStatement, token, line);
                    case ProtobufReserved reservedStatement -> handleReservedStatement(reservedStatement, token, line);
                    case ProtobufExtension extensionStatement -> handleExtensionStatement(extensionStatement, token, line);
                    case ProtobufEnum enumTree -> handleEnumTree(enumTree, token, line);
                    case ProtobufMessage messageTree -> handleMessageTree(document, messageTree, token, line);
                    case ProtobufOneof oneofTree -> handleOneofTree(oneofTree, token, line);
                    case ProtobufService serviceTree -> handleServiceTree(serviceTree, token, line);
                    case ProtobufMethod methodStatement -> handleMethodStatement(methodStatement, token, line);
                    case ProtobufReservedList reservedListStatement -> handleReservedListStatement(reservedListStatement, token, line);
                    case ProtobufExtensionsList extensionsListStatement -> handleExtensionsListStatement(extensionsListStatement, token, line);
                    case ProtobufDocument documentTree -> handleDocumentTree(documentTree, token, line);
                    case ProtobufExpression expression -> handleExpression(expression, token, line);
                    case ProtobufEmptyStatement ignored -> throw new ProtobufParserException("Empty statements cannot be parsed");
                };
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            throw ProtobufParserException.wrap(syntaxException, location);
        }
    }

    private static ProtobufTree handleExpression(ProtobufExpression expression, String token, @SuppressWarnings("unused") int line) {
        var literal = parseStringLiteral(token);
        if(literal.isPresent()) {
            var value = new ProtobufExpression.Value.Literal(literal.get());
            expression.setValue(value);
            return expression.parent();
        }

        var bool = parseBool(token);
        if(bool.isPresent()) {
            var value = new ProtobufExpression.Value.Bool(bool.get());
            expression.setValue(value);
            return expression.parent();
        }

        var number = parseIndex(token, true, false);
        if(number.isPresent()) {
            var value = new ProtobufExpression.Value.Number(number.get());
            expression.setValue(value);
            return expression.parent();
        }

        if(isObjectStart(token)) {
            // FIXME: Parse the message
            throw new UnsupportedOperationException();
        }

        // FIXME: Get the type for the enum
        var value = new ProtobufExpression.Value.EnumConstant(null, token);
        expression.setValue(value);
        return expression.parent();
    }

    private static ProtobufTree handleMethodStatement(ProtobufMethod methodStatement, String token, int line) {
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
                    var statement = new ProtobufOption(line);
                    methodStatement.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, line);
            };
        }
    }

    private static ProtobufTree handleExtensionsListStatement(ProtobufExtensionsList extensionsTree, String token, int line) {
        if(isStatementEnd(token)) {
            // Can only happen inside a ProtobufExtensionsList
            throw new ProtobufParserException("Unexpected token " + token, line);
        }

        var statement = new ProtobufExtension(line);
        statement.setParent(extensionsTree);
        var index = parseIndex(token, false, false);
        if(index.isPresent()) {
            var value = new ProtobufExtension.Value.FieldIndex(index.get());
            statement.setValue(value);
            return statement;
        }

        throw new ProtobufParserException("Unexpected token " + token, line);
    }

    private static ProtobufTree handleReservedListStatement(ProtobufReservedList reservedTree, String token, int line) {
        if(isStatementEnd(token)) {
            // Can only happen inside a ProtobufReservedStatement
            throw new ProtobufParserException("Unexpected token " + token, line);
        }

        var statement = new ProtobufReserved(line);
        statement.setParent(reservedTree);
        var index = parseIndex(token, false, false);
        if(index.isPresent()) {
            var value = new ProtobufReserved.Value.FieldIndex(index.get());
            statement.setValue(value);
            return statement;
        }

        var literal = parseStringLiteral(token);
        if(literal.isPresent()) {
            var value = new ProtobufReserved.Value.FieldName(literal.get());
            statement.setValue(value);
            return statement;
        }

        throw new ProtobufParserException("Unexpected token " + token, line);
    }

    private static ProtobufTree handleServiceTree(ProtobufService serviceTree, String token, int line) {
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
                    var statement = new ProtobufOption(line);
                    serviceTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "rpc" -> {
                    var statement = new ProtobufMethod(line);
                    serviceTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> throw new ProtobufParserException("Unexpected token " + token, line);
            };
        }
    }

    private static ProtobufTree handleEnumTree(ProtobufEnum enumTree, String token, int line) {
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
                    var statement = new ProtobufExtensionsList(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "option" -> {
                    var statement = new ProtobufOption(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "reserved" -> {
                    var statement = new ProtobufReservedList(line);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> {
                    var statement = new ProtobufEnumConstant(line);
                    statement.setModifier(ProtobufField.Modifier.nothing());
                    statement.setType(ProtobufMessageOrEnumType.of(enumTree.name(), enumTree));
                    statement.setName(token);
                    enumTree.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        }
    }

    private static ProtobufTree handleMessageTree(ProtobufDocument document, ProtobufMessage messageTree, String token, int line) {
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
                    var statement = new ProtobufOption(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "message" -> {
                    var statement = new ProtobufMessage(line, false);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "enum" -> {
                    var statement = new ProtobufEnum(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "extend" -> {
                    var statement = new ProtobufMessage(line, true);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "extensions" -> {
                    var statement = new ProtobufExtensionsList(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "reserved" -> {
                    var statement = new ProtobufReservedList(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                case "oneof" -> {
                    var statement = new ProtobufOneof(line);
                    messageTree.body()
                            .addChild(statement);
                    yield statement;
                }
                default -> {
                    var statement = new ProtobufField(line);
                    var modifier = ProtobufField.Modifier.of(token);
                    if(modifier.type() == ProtobufField.Modifier.Type.NOTHING) {
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

    private static ProtobufTree handleOneofTree(ProtobufOneof oneofTree, String token, int line) {
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
                    var statement = new ProtobufOption(line);
                    oneofTree.body()
                            .addChild(statement);
                    yield oneofTree;
                }
                default -> {
                    var statement = new ProtobufField(line);
                    var reference = ProtobufTypeReference.of(token);
                    statement.setType(reference);
                    statement.setModifier(ProtobufField.Modifier.nothing());
                    oneofTree.body()
                            .addChild(statement);
                    yield statement;
                }
            };
        }
    }

    private static ProtobufTree handleDocumentTree(ProtobufDocument documentTree, String token, int line) {
        return switch (token) {
            case STATEMENT_END -> {
                var statement = new ProtobufEmptyStatement(line);
                documentTree.body()
                        .addChild(statement);
                yield documentTree;
            }
            case "package" -> {
                var statement = new ProtobufPackage(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "syntax" -> {
                var statement = new ProtobufSyntax(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "option" -> {
                var statement = new ProtobufOption(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "message" -> {
                var statement = new ProtobufMessage(line, false);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "enum" -> {
                var statement = new ProtobufEnum(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "service" -> {
                var statement = new ProtobufService(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "import" -> {
                var statement = new ProtobufImport(line);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            case "extend" -> {
                var statement = new ProtobufMessage(line, true);
                documentTree.body()
                        .addChild(statement);
                yield statement;
            }
            default -> throw new ProtobufParserException("Unexpected token " + token, line);
        };
    }

    private static ProtobufTree handleReservedStatement(ProtobufReserved reservedStatement, String token, int line) {
        return reservedStatement;
    }

    private static ProtobufTree handleExtensionStatement(ProtobufExtension extensionStatement, String token, int line) {
        return extensionStatement;
    }

    private static ProtobufTree handleImportStatement(ProtobufImport importStatement, String token, int line) {
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

    private static ProtobufTree handleFieldStatement(ProtobufField fieldStatement, ProtobufTokenizer tokenizer, String token, int line) throws IOException {
        if(!fieldStatement.hasModifier()) {
            var modifier = ProtobufField.Modifier.of(token);
            fieldStatement.setModifier(modifier);
            return fieldStatement;
        }else if(!fieldStatement.hasType()) {
            fieldStatement.setType(ProtobufTypeReference.of(token));
            return fieldStatement;
        } else if (!fieldStatement.hasName()) {
            if(isTypeParametersStart(token)) {
                if(!(fieldStatement.type() instanceof ProtobufMapType mapType) || mapType.isAttributed()) {
                    throw new ProtobufParserException("Unexpected token " + token, line);
                }

                var keyTypeToken = tokenizer.nextToken();
                if(keyTypeToken == null) {
                    return fieldStatement;
                }
                var keyType = ProtobufTypeReference.of(keyTypeToken);
                mapType.setKeyType(keyType);

                var separator = tokenizer.nextToken();
                if(separator == null) {
                    return fieldStatement;
                }
                ProtobufParserException.check(isListSeparator(separator),
                        "Unexpected token " + separator, line);

                var valueTypeToken = tokenizer.nextToken();
                if(valueTypeToken == null) {
                    return fieldStatement;
                }
                var valueType = ProtobufTypeReference.of(valueTypeToken);
                mapType.setValueType(valueType);

                var end = tokenizer.nextToken();
                if(end == null) {
                    return fieldStatement;
                }
                ProtobufParserException.check(isTypeParametersEnd(end),
                        "Unexpected token " + end, line);
            }else {
                fieldStatement.setName(token);
            }

            return fieldStatement;
        }else if(!fieldStatement.hasIndex()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = new ProtobufExpression(line);
            fieldStatement.setIndex(expression);
            return expression;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return fieldStatement.parent();
        }
    }

    private static ProtobufTree handleOptionStatement(ProtobufOption optionStatement, String token, int line) {
        if(!optionStatement.hasName()) {
            optionStatement.setName(token);
            return optionStatement;
        }else if(!optionStatement.hasValue()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = new ProtobufExpression(line);
            optionStatement.setValue(expression);
            return expression;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return optionStatement.parent();
        }
    }

    private static ProtobufTree handleSyntaxStatement(ProtobufSyntax syntaxStatement, String token, int line) {
        if(!syntaxStatement.hasVersion()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, line);
            var expression = new ProtobufExpression(line);
            syntaxStatement.setVersion(expression);
            return expression;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return syntaxStatement.parent();
        }
    }

    private static ProtobufTree handlePackageStatement(ProtobufPackage packageStatement, String token, int line) {
        if (!packageStatement.hasName()) {
            packageStatement.setName(token);
            return packageStatement;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, line);
            return packageStatement.parent();
        }
    }

    private static Optional<String> parseStringLiteral(String token) {
        if ((token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))) {
            return Optional.of(token.substring(1, token.length() - 1));
        } else {
            return Optional.empty();
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

    private static Optional<Integer> parseIndex(String input, boolean acceptNegative, boolean acceptMax) {
        try {
            if(acceptMax && Objects.equals(input, MAX_KEYWORD)) {
                return Optional.of(Integer.MAX_VALUE);
            }

            return Optional.of(Integer.parseInt(input))
                    .filter(value -> acceptNegative || value > 0);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> parseBool(String token) {
        return switch (token) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
