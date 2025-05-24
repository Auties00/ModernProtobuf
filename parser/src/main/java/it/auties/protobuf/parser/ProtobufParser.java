package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_2;
import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_3;

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
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String MAP_TYPE = "map";
    private static final String DEFAULT_KEYWORD_OPTION = "default";

    static {
        var parser = new ProtobufParser();
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufParserException("Missing built-in .proto");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            BUILT_INS = parser.parse(builtInTypesPath);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufParserException("Missing built-in .proto");
        }
    }

    private final Deque<ProtobufBlock<?, ?>> objects;
    private final Deque<Instruction> instructions;
    private final Deque<NestedInstruction> nestedInstructions;
    private final ReentrantLock parserLock;
    private ProtobufDocumentTree document;
    private StreamTokenizer tokenizer;

    public ProtobufParser() {
        this.objects = new LinkedList<>();
        this.instructions = new LinkedList<>();
        this.nestedInstructions = new LinkedList<>();
        this.parserLock = new ReentrantLock(true);
    }

    public Set<ProtobufDocumentTree> parse(Path path) throws IOException {
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
            for (var statement : results) {
                attributeStatement(statement, statement);
            }
            return results;
        }
    }

    public ProtobufDocumentTree parseOnly(String input) {
        var result = doParse(null, new StringReader(input));
        attributeStatement(result, result);
        return result;
    }

    public ProtobufDocumentTree parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.newBufferedReader(path));
        attributeImports(List.of(result));
        attributeStatement(result, result);
        return result;
    }

    private void attributeImports(Collection<ProtobufDocumentTree> documents) {
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(BUILT_INS.size() + documents.size());
        for(var document : BUILT_INS) {
            canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        }
        for(var document : documents) {
            canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        }
        for (var protobufDocumentTree : documents) {
            for (var entry : protobufDocumentTree.children()) {
                if (!(entry instanceof ProtobufImportStatement importStatement) || entry.isAttributed()) {
                    continue;
                }

                var imported = canonicalPathToDocumentMap.get(importStatement.location());
                ProtobufParserException.check(imported != null,
                        "Cannot resolve import %s", importStatement.line(), importStatement.location());
                importStatement.setDocument(imported);
            }
        }
    }

    private ProtobufDocumentTree doParse(Path location, Reader input) {
        try {
            parserLock.lock();
            this.document = new ProtobufDocumentTree(location);
            this.tokenizer = new StreamTokenizer(input);
            tokenizer.resetSyntax();
            tokenizer.wordChars('a', 'z');
            tokenizer.wordChars('A', 'Z');
            tokenizer.wordChars(128 + 32, 255);
            tokenizer.wordChars('_', '_');
            tokenizer.wordChars('"', '"');
            tokenizer.wordChars('\'', '\'');
            tokenizer.wordChars('.', '.');
            tokenizer.wordChars('-', '-');
            for (int i = '0'; i <= '9'; i++) {
                tokenizer.wordChars(i, i);
            }
            tokenizer.whitespaceChars(0, ' ');
            tokenizer.commentChar('/');
            tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
            String token;
            while ((token = nextToken()) != null) {
                handleToken(token);
            }

            return document;
        } catch (ProtobufParserException syntaxException) {
            throw ProtobufParserException.wrap(syntaxException, location);
        } finally {
            document = null;
            objects.clear();
            instructions.clear();
            nestedInstructions.clear();
            tokenizer = null;
            parserLock.unlock();
        }
    }

    private void attributeStatement(ProtobufDocumentTree document, ProtobufTree tree) {
        switch (tree) {
            case ProtobufBlock<?, ?> body -> {
                for (var child : body.children()) {
                    attributeStatement(document, child);
                }
            }

            case ProtobufFieldStatement field -> {
                var type = field.type()
                        .orElse(null);
                attributeTypedStatement(document, field, type);
                for(var option : field.options()) {
                    attributeOption(option);
                }
            }

            case ProtobufStatement statement -> {
                for(var option : statement.options()) {
                    attributeOption(option);
                }
            }
        }
    }

    private void attributeTypedStatement(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree, ProtobufTypeReference typeReference) {
        switch (typeReference) {
            case ProtobufMapType mapType -> {
                attributeTypedStatement(document, typedFieldTree, mapType.keyType().orElseThrow());
                attributeTypedStatement(document, typedFieldTree, mapType.valueType().orElseThrow());
            }
            case ProtobufObjectType messageType -> attributeType(document, typedFieldTree, messageType);
            case null, default -> {}
        }
    }

    private void attributeType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree, ProtobufObjectType fieldType) {
        if (fieldType.isAttributed()) {
            return;
        }

        var accessed = fieldType.name();
        var types = accessed.split(TYPE_SELECTOR_SPLITTER);
        var parent = typedFieldTree.parent()
                .orElseThrow(() -> throwUnattributableType(typedFieldTree));

        // Look for the type definition starting from the field's parent
        // Only the first result should be considered because of shadowing (i.e. if a name is reused in an inner scope, the inner scope should override the outer scope)
        ProtobufBlock<?, ?> innerType = null;
        while (parent != null && innerType == null){
            innerType = parent.getDirectChildByNameAndType(types[0], ProtobufBlock.class)
                    .orElse(null);
            parent = parent.parent()
                    .orElse(null);
        }

        if(innerType != null) { // Found a match in the parent scope
            // Try to resolve the type reference in the matched scope
            for(var index = 1; index < types.length; index++){
                innerType = innerType.getDirectChildByNameAndType(types[index], ProtobufBlock.class)
                        .orElseThrow(() -> throwUnattributableType(typedFieldTree));
            }
        } else { // No match found in the parent scope, try to resolve the type reference through imports
            for(var statement : document.children()) {
                if(!(statement instanceof ProtobufImportStatement importStatement)) {
                    continue;
                }

                var imported = importStatement.document()
                        .orElse(null);
                if(imported == null) {
                    continue;
                }

                var importedPackage = imported.packageName();
                var importedName = accessed.startsWith(importedPackage + TYPE_SELECTOR)
                        ? accessed.substring(importedPackage.length() + 1) : accessed;
                var simpleImportName = importedName.split(TYPE_SELECTOR_SPLITTER);
                ProtobufBlock<?, ?> type = document;
                for (var i = 0; i < simpleImportName.length && type != null; i++) {
                    type = type.getDirectChildByNameAndType(simpleImportName[i], ProtobufBlock.class)
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

        fieldType.attribute(innerType);
    }

    private ProtobufParserException throwUnattributableType(ProtobufFieldStatement typedFieldTree) {
        return new ProtobufParserException(
                "Cannot resolve type %s in field %s inside %s",
                tokenizer.lineno(),
                typedFieldTree.type().map(ProtobufTypeReference::name).orElse(null),
                typedFieldTree.name(),
                typedFieldTree.parent().flatMap(entry -> entry instanceof ProtobufNameableTree nameableTree ? nameableTree.name() : Optional.of("<block>")).orElse(null)
        );
    }

    private void attributeOption(ProtobufOptionStatement optionStatement) {
        var type = optionStatement.definition()
                .flatMap(ProtobufFieldStatement::type)
                .orElse(null);
        switch (type) {
            case ProtobufPrimitiveType primitiveType -> {
                switch (primitiveType.protobufType()) {
                    case STRING, BYTES -> {
                        if (!(optionStatement.value() instanceof String value)) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value);
                    }
                    case FLOAT -> {
                        if (!(optionStatement.value() instanceof Number value)) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value.floatValue());
                    }
                    case DOUBLE -> {
                        if (!(optionStatement.value() instanceof Number value)) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value.doubleValue());
                    }
                    case BOOL -> {
                        if (!(optionStatement.value() instanceof Boolean value)) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value);
                    }
                    case INT32, SINT32, FIXED32, SFIXED32 -> {
                        if (!(optionStatement.value() instanceof Integer value)) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value);
                    }
                    case UINT32 -> {
                        if (!(optionStatement.value() instanceof Integer value) || value < 0) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }

                        optionStatement.setAttributedValue(value.doubleValue());
                    }
                    case INT64, SINT64, FIXED64, SFIXED64 -> {
                        var value = parseInt64Value(optionStatement, primitiveType);
                        optionStatement.setAttributedValue(value);
                    }
                    case UINT64 -> {
                        var value = parseInt64Value(optionStatement, primitiveType);
                        if (value < 0) {
                            throw ProtobufParserException.invalidOption(optionStatement, primitiveType);
                        }
                        optionStatement.setAttributedValue(value);
                    }
                }
            }
            case ProtobufObjectType objectType
                    when objectType.declaration().orElseThrow(() -> new ProtobufParserException("Object type is not attributed")) instanceof ProtobufEnumTree enumTree
                    -> {
                var enumDefault = enumTree.children()
                        .stream()
                        .flatMap(entry -> entry instanceof ProtobufEnumConstantStatement statement ? Stream.of(statement) : Stream.empty())
                        .filter(constantName -> Objects.equals(constantName, optionStatement.value()))
                        .findFirst()
                        .orElseThrow(() -> new ProtobufParserException("Invalid default value for type enum: " + optionStatement.value()));
                optionStatement.setAttributedValue(enumDefault);
            }
            case null, default -> {
                if(optionStatement.name().equals(DEFAULT_KEYWORD_OPTION)) {
                    throw new ProtobufParserException("Default values are only supported for fields whose type is a primitive or an enum", tokenizer.lineno());
                }
            }
        }
    }

    private long parseInt64Value(ProtobufOptionStatement option, ProtobufPrimitiveType primitiveType) {
        return switch (option.value()) {
            case Integer intValue -> intValue;
            case Long longValue -> longValue;
            default -> throw ProtobufParserException.invalidOption(option, primitiveType);
        };
    }

    private void handleOptionsStart() {
        ProtobufParserException.check(hasFieldScope(),
                "Unexpected token " + ARRAY_START, tokenizer.lineno());
        jumpIntoInstruction(Instruction.FIELD_OPTIONS);
    }

    private void jumpIntoInstruction(Instruction instruction) {
        instructions.add(instruction);
        nestedInstructions.add(instruction.nestedInstructions().getFirst());
    }

    private void jumpOutInstruction() {
        instructions.removeLast();
        nestedInstructions.removeLast();
    }

    private void handleOptionsEnd() {
        ProtobufParserException.check(instructions.peekLast() == Instruction.FIELD_OPTIONS,
                "Unexpected token " + ARRAY_END, tokenizer.lineno());
        var fieldTree = (ProtobufStatement) objects.getLast()
                .children()
                .getLast();
        var lastOption = fieldTree.options()
                .getLast();
        ProtobufParserException.check(lastOption.hasValue(),
                "Unexpected token " + ARRAY_END, tokenizer.lineno());
        jumpOutInstruction();
    }

    private boolean hasFieldScope() {
        var object = objects.peekLast();
        if(object == null) {
            return false;
        }

        var statements = object.children();
        if(statements.isEmpty()) {
            return false;
        }

        return statements.getLast() instanceof ProtobufFieldStatement;
    }

    private void handleToken(String token) {
        var instructionsSize = instructions.size();
        switch (instructions.peekLast()) {
            case PACKAGE -> handleDocumentPackage(token);
            case SYNTAX -> handleDocumentSyntax(token);
            case OPTION -> handleOption(token);
            case MESSAGE, ENUM, ONE_OF, EXTEND -> handleInstructionWithBody(token);
            case RESERVED -> handleReserved(token);
            case EXTENSIONS -> handleExtensions(token);
            case SERVICE -> handleService();
            case IMPORT -> handleImport(token);
            case MAP_TYPE -> handleMapType(token);
            case FIELD_OPTIONS -> handleFieldOption(token);
            case FIELD -> handleField(token);
            case GROUP_BODY -> handleNestedInstructionForBody(token, true);
            case UNKNOWN -> throw new ProtobufParserException("Unexpected state");
            case null -> handleInstruction(token);
        }

        if(instructionsSize == instructions.size() && instructions.getLast().shouldMoveInstructionAutomatically()) {
            jumpIntoNextNestedInstruction();
        }

        if(isStatementEnd(token)) {
            jumpOutInstruction();
        }
    }

    private void handleService() {

    }

    private void handleMapType(String token) {
        switch (token) {
            case TYPE_PARAMETERS_START -> handleTypeParametersStart();
            case TYPE_PARAMETERS_END -> handleTypeParametersEnd();
            default -> handleMapTypeInstruction(token);
        }
    }

    private void handleMapTypeInstruction(String token) {
        var fieldTree = (ProtobufFieldStatement) objects.getLast()
                .children()
                .getLast();
        var fieldType = fieldTree.type()
                .orElseThrow();
        var objectType = (ProtobufMapType) fieldType;
        switch (nestedInstructions.peekLast()) {
            case BODY_OR_VALUE -> {
                if(isStatementEnd(token)) {
                    ProtobufParserException.check(objectType.isAttributed(),
                            "Unexpected token " + token, tokenizer.lineno());
                    return;
                }

                var type = ProtobufTypeReference.of(token);
                if(objectType.keyType().isEmpty()) {
                    objectType.setKeyType(type);
                    return;
                }

                if(objectType.valueType().isEmpty()) {
                    objectType.setValueType(type);
                    return;
                }

                throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufParserException.check(!objectType.isAttributed() && Objects.equals(token, LIST_SEPARATOR),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleImport(String token) {
        ProtobufParserException.check(document.location().isPresent(),
                "Imports are not supported when parsing a raw protobuf", tokenizer.lineno());
        ProtobufParserException.check(objects.isEmpty(),
                "Illegal import statement", tokenizer.lineno());
        switch (nestedInstructions.peekLast()) {
            case BODY_OR_VALUE -> {
                var importValue = parseStringLiteral(token)
                        .orElseThrow(() -> new ProtobufParserException("Expected string literal as import value", tokenizer.lineno()));
                ProtobufParserException.check(hasDuplicateImport(importValue),
                        "Duplicate import statement", tokenizer.lineno());
                var importStatement = new ProtobufImportStatement(tokenizer.lineno(), importValue);
                document.addChild(importStatement);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleTypeParametersEnd() {
        ProtobufParserException.check(instructions.peekLast() == Instruction.MAP_TYPE && nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END,
                "Unexpected token " + TYPE_PARAMETERS_END, tokenizer.lineno());
        jumpOutInstruction();
    }

    private void handleTypeParametersStart() {
        ProtobufParserException.check(isValidTypeParameterScope(),
                "Unexpected generic parameter", tokenizer.lineno());
        jumpIntoInstruction(Instruction.MAP_TYPE);
    }

    private boolean isValidTypeParameterScope() {
        var object = objects.peekLast();
        if(object == null) {
            return false;
        }

        var statements = object.children();
        if(statements.isEmpty()) {
            return false;
        }

        return statements.getLast() instanceof ProtobufFieldStatement field
                && field.type().filter(protobufTypeReference -> protobufTypeReference.protobufType() == ProtobufType.MAP).isPresent();
    }

    private void handleObjectEnd() {
        var object = objects.pollLast();
        if(object == null) {
            throw new ProtobufParserException("Unexpected end of object", tokenizer.lineno());
        }

        ProtobufParserException.check(hasMinimumStatementsRequired(object),
                "Illegal enum or oneof without any constants", tokenizer.lineno());
        ProtobufParserException.check(isValidIndex(object),
                "Illegal field index used", tokenizer.lineno());
        ProtobufParserException.check(isValidEnumConstant(object),
                "Proto3 enums require the first constant to have index 0", tokenizer.lineno());
        if(object instanceof ProtobufGroupTree groupTree) {
            attributeSyntheticGroup(groupTree);
            jumpOutInstruction();
        }

        jumpOutInstruction();
    }

    private void attributeSyntheticGroup(ProtobufGroupTree groupTree) {
        var groupOwner = objects.peekLast();
        if(groupOwner == null) {
            throw new ProtobufParserException("Missing group owner at line " + tokenizer.lineno());
        }

        var children = groupOwner.children();
        if(children.isEmpty() || !(children.getLast() instanceof ProtobufFieldStatement field)) {
            throw new ProtobufParserException("Missing field at line " + tokenizer.lineno());
        }

        var groupType = field.type()
                .filter(type -> type instanceof ProtobufGroupType)
                .map(type -> (ProtobufGroupType) type)
                .orElseThrow(() -> new ProtobufParserException("Missing group type at line " + tokenizer.lineno()));
        groupType.attribute(groupTree);
    }

    private boolean isValidEnumConstant(ProtobufBlock<?, ?> indexedBodyTree) {
        if (!(indexedBodyTree instanceof ProtobufEnumTree enumTree)) {
            return true;
        }

        var version = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        if(version != PROTOBUF_3) {
            return true;
        }

        var statements = enumTree.children();
        return statements.stream()
                .filter(statement -> statement instanceof ProtobufEnumConstantStatement)
                .findFirst()
                .filter(field -> ((ProtobufEnumConstantStatement) field).index().orElse(-1) == 0)
                .isPresent();
    }

    private boolean hasDuplicateImport(String token) {
        return document.children()
                .stream()
                .noneMatch(entry -> entry instanceof ProtobufImportStatement importStatement && Objects.equals(importStatement.location(), token));
    }

    private boolean isValidIndex(ProtobufBlock<?, ?> reservable) {
        var remainingBlocks = new LinkedList<ProtobufBlock<?, ?>>();
        remainingBlocks.add(reservable);
        var reservedStatements = new ArrayList<ProtobufReservedStatement>();
        var usedIndexes = new HashSet<Integer>();
        while (!remainingBlocks.isEmpty()) {
            reservable = remainingBlocks.removeFirst();
            for(var child : reservable.children()) {
                switch (child) {
                    case ProtobufBlock<?, ?> block when block.isScopeInherited() -> remainingBlocks.add(block);
                    case ProtobufReservedStatement reservedStatement -> reservedStatements.add(reservedStatement);
                    case ProtobufIndexableTree indexable -> indexable.index()
                            .ifPresent(usedIndexes::add);
                    default -> {}
                }
            }
        }
        for(var index : usedIndexes) {
            var usesReservedIndex = reservedStatements.stream()
                    .anyMatch(reservedStatement -> reservedStatement.entries()
                            .stream()
                            .anyMatch(entry -> entry instanceof ProtobufReservedStatement.Entry.FieldIndex number && number.hasValue(index)));
            if(usesReservedIndex) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMinimumStatementsRequired(ProtobufBlock<?, ?> object) {
        return switch (object) {
            case ProtobufEnumTree enumStatement -> !enumStatement.children().isEmpty();
            case ProtobufOneofTree oneOfStatement -> !oneOfStatement.children().isEmpty();
            case null, default -> true;
        };
    }

    private void handleInstructionWithBody(String token) {
        if (token.equals(OBJECT_END)) {
            handleObjectEnd();
            return;
        }

        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> handleNestedBodyDeclaration(instructions.getLast(), token);
            case INITIALIZER -> ProtobufParserException.check(isObjectStart(token),
                    "Expected message initializer after message declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> handleNestedInstructionForBody(token, false);
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleNestedInstructionForBody(String token, boolean allowObjectEnd) {
        if (allowObjectEnd && token.equals(OBJECT_END)) {
            handleObjectEnd();
            return;
        }

        var nestedInstruction = Instruction.of(token, true);
        var actualNestedInstruction = nestedInstruction == Instruction.UNKNOWN ? Instruction.FIELD : nestedInstruction;
        if (actualNestedInstruction == Instruction.FIELD) {
            handleFieldDeclaration(token);
        }

        jumpIntoInstruction(actualNestedInstruction);
    }

    // TODO: Check for intersections
    private void handleReserved(String token) {
        var scope = objects.peekLast();
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> {
                var implementation = new ProtobufReservedStatement(tokenizer.lineno());
                if (!(scope instanceof ProtobufNameableBlock<?, ?> block)) {
                    throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Should never happen
                }

                switch (block) {
                    case ProtobufEnumTree enumTree -> enumTree.addChild(implementation);
                    case ProtobufGroupTree groupTree -> groupTree.addChild(implementation);
                    case ProtobufMessageTree messageTree -> messageTree.addChild(implementation);
                    case ProtobufOneofTree ignored -> throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Can happen
                }
            }

            case BODY_OR_VALUE -> {
                if(scope == null) {
                    throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Should never happen
                }

                if(isStatementEnd(token) || isListSeparator(token) || isRangeOperator(token)) {
                    throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                }

                var reserved = (ProtobufReservedStatement) scope.children().getLast();
                var type = reserved.type();
                if(type == ProtobufReservedStatement.EntryType.NONE) {
                    var entry = parseReservedEntry(token);
                    reserved.add(entry);
                    return;
                }

                var entry = reserved.entries().getLast();
                if (!entry.isValid()) {
                    entry = parseReservedEntry(token);
                    ProtobufParserException.check(entry.type() == type,
                            "Field names and indexes cannot be mixed in the same reserved statement",
                            tokenizer.lineno());
                    reserved.add(entry);
                    return;
                }

                try {
                    switch (entry) {
                        case ProtobufReservedStatement.Entry.FieldIndex fieldIndex -> {
                            var index = parseIndex(token, false, false);
                            ProtobufParserException.check(index.isPresent(),
                                    "Unexpected token " + token,
                                    tokenizer.lineno());
                            fieldIndex.addValue(index.get());
                        }

                        case ProtobufReservedStatement.Entry.FieldName fieldName -> {
                            var literal = parseStringLiteral(token);
                            ProtobufParserException.check(literal.isPresent(),
                                    "Unexpected token " + token,
                                    tokenizer.lineno());
                            fieldName.addValue(literal.get());
                        }
                    }
                }catch (Throwable throwable) {
                    throw new ProtobufParserException(throwable.getMessage(), tokenizer.lineno());
                }
            }

            case NESTED_INSTRUCTION_OR_END -> {
                if(scope == null) {
                    throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Should never happen
                }

                var reserved = (ProtobufReservedStatement) scope.children().getLast();
                if(isStatementEnd(token)) {
                    ProtobufParserException.check(reserved.isAttributed(),
                            "Unexpected token " + token, tokenizer.lineno());
                    return;
                }

                if(isRangeOperator(token)) {
                    var entry = reserved.remove();
                    if(!(entry instanceof ProtobufReservedStatement.Entry.FieldIndex.Values values)) {
                        throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                    }

                    var range = new ProtobufReservedStatement.Entry.FieldIndex.Range(values.values().getFirst());
                    reserved.add(range);
                    return;
                }

                if(!isListSeparator(token)) {
                    throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                }
            }
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private ProtobufReservedStatement.Entry<?> parseReservedEntry(String token) {
        var value = parseStringLiteral(token);
        if(value.isPresent()) {
            var literal = new ProtobufReservedStatement.Entry.FieldName();
            literal.addValue(value.get());
            return literal;
        }

        var number = parseIndex(token, false, true);
        if(number.isPresent()) {
            var values = new ProtobufReservedStatement.Entry.FieldIndex.Values();
            values.addValue(number.get());
            return values;
        }

        throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
    }

    private void handleExtensions(String token) {
        var scope = objects.peekLast();
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> {
                var implementation = new ProtobufExtensionsStatement(tokenizer.lineno());
                if (!(scope instanceof ProtobufNameableBlock<?, ?> block)) {
                    throw new ProtobufParserException("Invalid scope for extensions", tokenizer.lineno()); // Should never happen
                }

                switch (block) {
                    case ProtobufEnumTree enumTree -> enumTree.addChild(implementation);
                    case ProtobufGroupTree groupTree -> groupTree.addChild(implementation);
                    case ProtobufMessageTree messageTree -> messageTree.addChild(implementation);
                    case ProtobufOneofTree ignored -> throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Can happen
                }
            }

            case BODY_OR_VALUE -> {
                if(scope == null) {
                    throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Should never happen
                }

                if(isStatementEnd(token) || isListSeparator(token) || isRangeOperator(token)) {
                    throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                }

                var reserved = (ProtobufExtensionsStatement) scope.children().getLast();
                if(reserved.isEmpty()) {
                    var entry = parseExtensionsEntry(token);
                    reserved.add(entry);
                    return;
                }

                var entry = reserved.entries().getLast();
                if (!entry.isValid()) {
                    entry = parseExtensionsEntry(token);
                    reserved.add(entry);
                    return;
                }

                try {
                    var index = parseIndex(token, false, false);
                    ProtobufParserException.check(index.isPresent(),
                            "Unexpected token " + token,
                            tokenizer.lineno());
                    entry.addValue(index.get());
                }catch (Throwable throwable) {
                    throw new ProtobufParserException(throwable.getMessage(), tokenizer.lineno());
                }
            }

            case NESTED_INSTRUCTION_OR_END -> {
                if(scope == null) {
                    throw new ProtobufParserException("Invalid scope for reserved", tokenizer.lineno()); // Should never happen
                }

                var reserved = (ProtobufExtensionsStatement) scope.children().getLast();
                if(isStatementEnd(token)) {
                    ProtobufParserException.check(reserved.isAttributed(),
                            "Unexpected token " + token, tokenizer.lineno());
                    return;
                }

                if(isRangeOperator(token)) {
                    var entry = reserved.remove();
                    if(!(entry instanceof ProtobufExtensionsStatement.Entry.FieldIndexValues values)) {
                        throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                    }

                    var range = new ProtobufExtensionsStatement.Entry.FieldIndexRange(values.values().getFirst());
                    reserved.add(range);
                    return;
                }

                if(!isListSeparator(token)) {
                    throw new ProtobufParserException("Unexpected token " + token, tokenizer.lineno());
                }
            }
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private ProtobufExtensionsStatement.Entry parseExtensionsEntry(String token) {
        var number = parseIndex(token, false, true)
                .orElseThrow(() -> new ProtobufParserException("Unexpected token " + token, tokenizer.lineno()));
        var values = new ProtobufExtensionsStatement.Entry.FieldIndexValues();
        values.addValue(number);
        return values;
    }

    private Optional<String> parseStringLiteral(String token) {
        return (token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))
                ? Optional.of(token.substring(1, token.length() - 1)) : Optional.empty();
    }

    private ProtobufBlock<?, ?> checkFieldParent(String token, ProtobufFieldModifier modifier) {
        var scope = objects.peekLast();
        if (modifier.type() != ProtobufFieldModifier.Type.NOTHING) {
            ProtobufParserException.check(document.syntax().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2 || modifier.type() != ProtobufFieldModifier.Type.REQUIRED,
                    "Support for the required label was dropped in proto3", tokenizer.lineno());
            ProtobufParserException.check(scope instanceof ProtobufMessageTree || scope instanceof ProtobufGroupTree,
                    "Expected message or group scope for field declaration", tokenizer.lineno());
            return scope;
        }

        if (document.syntax().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_3 && scope instanceof ProtobufMessageTree) {
            return scope;
        }

        if (scope instanceof ProtobufEnumTree || scope instanceof ProtobufOneofTree || token.equals(MAP_TYPE)) {
            return scope;
        }

        throw new ProtobufParserException("Expected a valid field modifier in proto2 message scope for token " + modifier.token(), tokenizer.lineno());
    }

    private void handleField(String token) {
        switch (token) {
            case TYPE_PARAMETERS_START -> handleTypeParametersStart();
            case TYPE_PARAMETERS_END -> handleTypeParametersEnd();
            case ARRAY_START -> handleOptionsStart();
            case ARRAY_END -> handleOptionsEnd();
            default -> handleFieldInstruction(token);
        }
    }

    private void handleFieldInstruction(String token) {
        var parent = objects.getLast();
        var lastField = parent.children().getLast();
        switch (lastField) {
            case ProtobufEnumConstantStatement constant -> handleEnumConstant(token, constant, parent);
            case ProtobufFieldStatement fieldTree -> handleModifiableField(token, fieldTree, parent);
            default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleModifiableField(String token, ProtobufFieldStatement fieldTree, ProtobufBlock<?, ?> parent) {
        switch (parent) {
            case ProtobufMessageTree ignored -> handleMessageField(token, fieldTree, parent);
            case ProtobufGroupTree ignored ->  handleMessageField(token, fieldTree, parent);
            case ProtobufOneofTree ignored -> handleOneOfField(token, fieldTree, parent);
            default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleOneOfField(String token, ProtobufFieldStatement fieldTree, ProtobufBlock<?, ?> parent) {
        if(fieldTree.type().isEmpty()) {
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufParserException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufParserException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufParserException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid index " + token, tokenizer.lineno()));
            ProtobufParserException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        var type = fieldTree.type();
        if(type.isPresent() && type.get().protobufType() == ProtobufType.GROUP) {
            handleGroupStart(token, fieldTree);
            return;
        }

        ProtobufParserException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleGroupStart(String token, ProtobufFieldStatement fieldTree) {
        ProtobufParserException.check(document.syntax().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2,
                "Groups are not supported in proto3", tokenizer.lineno());
        ProtobufParserException.check(isObjectStart(token),
                "Unexpected token " + token, tokenizer.lineno());
        var syntheticName = fieldTree.name()
                        .orElseThrow(() -> new ProtobufParserException("Missing field name for group"));
        ProtobufParserException.check(!syntheticName.isEmpty() && Character.isUpperCase(syntheticName.charAt(0)),
                "Group name " + syntheticName + " should start with a capital letter", tokenizer.lineno());
        var groupTree = new ProtobufGroupTree(tokenizer.lineno(), syntheticName);
        var fieldParent = fieldTree.parent()
                .orElseThrow(() -> new ProtobufParserException("Missing field parent at line " + tokenizer.lineno()));
        groupTree.setParent(fieldParent, 2);
        objects.add(groupTree);
        jumpIntoInstruction(Instruction.GROUP_BODY);
    }

    private void handleMessageField(String token, ProtobufFieldStatement fieldTree, ProtobufBlock<?, ?> parent) {
        if(fieldTree.modifier().isEmpty()) {
            var modifier = ProtobufFieldModifier.of(token);
            fieldTree.setModifier(modifier);
            return;
        }

        if(fieldTree.type().isEmpty()) {
            ProtobufParserException.check(!token.equals(MAP_TYPE) || fieldTree.modifier().get().type() == ProtobufFieldModifier.Type.NOTHING,
                    "Unexpected token " + token, tokenizer.lineno());
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufParserException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufParserException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufParserException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token " + token, tokenizer.lineno()));
            ProtobufParserException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        var type = fieldTree.type();
        if(type.isPresent() && type.get().protobufType() == ProtobufType.GROUP) {
            handleGroupStart(token, fieldTree);
            return;
        }

        ProtobufParserException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleEnumConstant(String token, ProtobufEnumConstantStatement constant, ProtobufBlock<?, ?> parent) {
        if(constant.name().isEmpty()) {
            ProtobufParserException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufParserException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            constant.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(constant.index().isEmpty()) {
            ProtobufParserException.check(nestedInstructions.peekLast() != NestedInstruction.DECLARATION || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.DECLARATION) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, true, false)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token " + token, tokenizer.lineno()));
            constant.setIndex(index);
            return;
        }

        ProtobufParserException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleFieldDeclaration(String token) {
        var modifier = ProtobufFieldModifier.of(token);
        var parent = checkFieldParent(token, modifier);
        switch (parent) {
            case ProtobufMessageTree messageTree -> handleMessageFieldDeclaration(token, messageTree, modifier);
            case ProtobufGroupTree groupTree -> handleGroupFieldDeclaration(token, groupTree, modifier);
            case ProtobufEnumTree enumTree -> handleEnumConstantDeclaration(token, enumTree);
            case ProtobufOneofTree oneOfTree -> handleOneOfFieldDeclaration(token, oneOfTree, modifier);
            default -> throw new ProtobufParserException("Unexpected context");
        }
    }

    private void handleOneOfFieldDeclaration(String token, ProtobufOneofTree oneOfTree, ProtobufFieldModifier modifier) {
        var field = new ProtobufFieldStatement(tokenizer.lineno());
        ProtobufParserException.check(modifier.type() == ProtobufFieldModifier.Type.NOTHING || modifier.type() == ProtobufFieldModifier.Type.OPTIONAL,
                "Unexpected token " + token, tokenizer.lineno());
        field.setModifier(modifier);
        if(modifier.type() == ProtobufFieldModifier.Type.NOTHING) {
            field.setType(ProtobufTypeReference.of(token));
        }

        oneOfTree.addChild(field);
    }

    private void handleEnumConstantDeclaration(String name, ProtobufEnumTree enumTree) {
        var field = new ProtobufEnumConstantStatement(tokenizer.lineno(), name);
        enumTree.addChild(field);
    }

    private void handleMessageFieldDeclaration(String token, ProtobufMessageTree messageTree, ProtobufFieldModifier modifier) {
        var field = new ProtobufFieldStatement(tokenizer.lineno());
        switch (document.syntax().orElse(ProtobufVersion.defaultVersion())){
            case PROTOBUF_2 -> {
                field.setModifier(modifier);
                if(token.equals(MAP_TYPE)) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
            case PROTOBUF_3 -> {
                field.setModifier(modifier);
                if(modifier.type() == ProtobufFieldModifier.Type.NOTHING) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
        }
        messageTree.addChild(field);
    }

    private void handleGroupFieldDeclaration(String token, ProtobufGroupTree groupTree, ProtobufFieldModifier modifier) {
        var field = new ProtobufFieldStatement(tokenizer.lineno());
        field.setModifier(modifier);
        if(token.equals(MAP_TYPE)) {
            field.setType(ProtobufTypeReference.of(token));
        }
        groupTree.addChild(field);
    }

    private void handleOption(String token) {
        var object = getOptionedObject(token);
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> object.addOption(token);
            case INITIALIZER -> ProtobufParserException.check(isAssignmentOperator(token),
                    "Expected assignment operator after option declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> {
                ProtobufParserException.check(!object.options().isEmpty(),
                        "Unexpected token " + token, tokenizer.lineno());
                setOptionValue(token, object.options().getLast());
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private ProtobufStatement getOptionedObject(String name) {
        if(objects.isEmpty()) {
            return document;
        }

        if(objects.peekLast() instanceof ProtobufStatement statement) {
            return statement;
        }

        throw new ProtobufParserException("Invalid scope for option " + name, tokenizer.lineno());
    }

    private void handleFieldOption(String token) {
        switch (token) {
            case ARRAY_START -> handleOptionsStart();
            case ARRAY_END -> handleOptionsEnd();
            default -> handleFieldOptionInstruction(token);
        }
    }

    private void handleFieldOptionInstruction(String token) {
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> {
                var fieldTree = (ProtobufFieldStatement) objects.getLast()
                        .children()
                        .getLast();
                ProtobufParserException.check(!Objects.equals(token, DEFAULT_KEYWORD_OPTION) || document.syntax().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2,
                        "Support for the default values was dropped in proto3", tokenizer.lineno());
                fieldTree.addOption(token);
            }
            case INITIALIZER -> ProtobufParserException.check(isAssignmentOperator(token), "Unexpected token " + token, tokenizer.lineno());
            case BODY_OR_VALUE -> {
                var fieldTree = (ProtobufFieldStatement) objects.getLast()
                        .children()
                        .getLast();
                var lastOption = fieldTree.options()
                        .getLast();
                setOptionValue(token, lastOption);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufParserException.check(token.equals(LIST_SEPARATOR) || isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void setOptionValue(String token, ProtobufOptionStatement lastOption) {
        var literal = parseStringLiteral(token);
        if(literal.isPresent()) {
            lastOption.setRawValue(literal.get());
            return;
        }

        var index = parseIndex(token, true, false);
        if(index.isPresent()) {
            lastOption.setRawValue(index.get());
            return;
        }

        var bool = parseBool(token);
        if(bool.isPresent()) {
            lastOption.setRawValue(bool.get());
            return;
        }

        lastOption.setRawValue(token);
    }

    private void handleDocumentSyntax(String token) {
        switch (nestedInstructions.peekLast()) {
            case INITIALIZER -> ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case BODY_OR_VALUE -> {
                ProtobufParserException.check(document.children().isEmpty(),
                        "Unexpected token " + token, tokenizer.lineno());
                var version = ProtobufVersion.of(token)
                        .orElseThrow(() -> new ProtobufParserException("Illegal syntax declaration: %s is not a valid version".formatted(token), tokenizer.lineno()));
                var statement = new ProtobufSyntaxStatement(tokenizer.lineno(), version);
                document.addChild(statement);
            }
            case NESTED_INSTRUCTION_OR_END -> {
                ProtobufParserException.check(document.syntax().isPresent(),
                        "Unexpected token " + token, tokenizer.lineno());
                ProtobufParserException.check(isStatementEnd(token),
                        "Unexpected token " + token, tokenizer.lineno());
            }
            case null, default -> throw new ProtobufParserException("Unexpected state");
        }
    }

    private void handleDocumentPackage(String token) {
        if (nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE) {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            return;
        }

        ProtobufParserException.check(document.packageName().isEmpty(),
                "Duplicate package statement", tokenizer.lineno());
        var statement = new ProtobufPackageStatement(tokenizer.lineno(), token);
        document.addChild(statement);
    }

    private void handleInstruction(String token) {
        var instruction = Instruction.of(token, false);
        ProtobufParserException.check(instruction != Instruction.UNKNOWN,
                "Unexpected token " + token, tokenizer.lineno(), token);
        jumpIntoInstruction(instruction);
    }

    private void handleNestedBodyDeclaration(Instruction instruction, String name) {
        var object = switch (instruction) {
            case MESSAGE -> new ProtobufMessageTree(tokenizer.lineno(), name, false);
            case ENUM -> new ProtobufEnumTree(tokenizer.lineno(), name);
            case ONE_OF -> new ProtobufOneofTree(tokenizer.lineno(), name);
            case EXTEND -> new ProtobufMessageTree(tokenizer.lineno(), name, true);
            default -> throw new ProtobufParserException("Illegal state", tokenizer.lineno());
        };
        var scope = objects.peekLast();
        switch (scope) {
            case ProtobufMessageTree message -> message.addChild(object);
            case ProtobufGroupTree groupTree -> groupTree.addChild(object);
            case null -> {
                if (!(object instanceof ProtobufDocumentChildTree childTree)) {
                    throw new ProtobufParserException("Invalid scope", tokenizer.lineno());
                }

                document.addChild(childTree);
            }
            default -> throw new ProtobufParserException("Invalid scope", tokenizer.lineno());
        }

        objects.add(object);
    }

    private void jumpIntoNextNestedInstruction() {
        var instruction = instructions.getLast();
        var nestedInstruction = nestedInstructions.getLast();
        jumpIntoNestedInstruction(nestedInstruction.next(instruction));
    }

    private void jumpIntoNestedInstruction(NestedInstruction nestedInstruction) {
        nestedInstructions.removeLast();
        nestedInstructions.addLast(nestedInstruction);
    }

    private boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private boolean isObjectStart(String operator) {
        return Objects.equals(operator, OBJECT_START);
    }

    private boolean isStatementEnd(String operator) {
        return Objects.equals(operator, STATEMENT_END);
    }

    private boolean isListSeparator(String operator) {
        return Objects.equals(operator, LIST_SEPARATOR);
    }

    private boolean isRangeOperator(String operator) {
        return Objects.equals(operator, RANGE_OPERATOR);
    }

    private boolean isLegalIdentifier(String instruction) {
        return !instruction.isBlank()
                && !Character.isDigit(instruction.charAt(0))
                && instruction.chars().mapToObj(entry -> (char) entry).noneMatch(SYMBOLS::contains);
    }

    private String nextToken() {
        try {
            var token = tokenizer.nextToken();
            if (token == StreamTokenizer.TT_EOF) {
                return null;
            }

            return switch (token) {
                case StreamTokenizer.TT_WORD -> tokenizer.sval;
                case STRING_LITERAL_DELIMITER -> STRING_LITERAL + tokenizer.sval + STRING_LITERAL;
                case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
                default -> String.valueOf((char) token);
            };
        } catch (IOException exception) {
            return null;
        }
    }

    private Optional<Integer> parseIndex(String input, boolean acceptZero, boolean acceptMax) {
        try {
            if(acceptMax && Objects.equals(input, MAX_KEYWORD)) {
                return Optional.of(Integer.MAX_VALUE);
            }

            return Optional.of(Integer.parseUnsignedInt(input))
                    .filter(value -> acceptZero || value != 0);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<Boolean> parseBool(String token) {
        return switch (token) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private enum Instruction {
        UNKNOWN(null, false, NestedInstruction.VALUES, false, true),
        PACKAGE("package", false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        SYNTAX("syntax", false, List.of(NestedInstruction.INITIALIZER, NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        RESERVED("reserved", true, List.of(NestedInstruction.INITIALIZER, NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        OPTION("option", true, NestedInstruction.VALUES, false, true),
        MESSAGE("message", true, NestedInstruction.VALUES, false, true),
        ENUM("enum", true, NestedInstruction.VALUES, false, true),
        ONE_OF("oneof", true, NestedInstruction.VALUES, false, true),
        SERVICE("service", true, NestedInstruction.VALUES, false, true),
        IMPORT("import", false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        EXTENSIONS("extensions", true, List.of(NestedInstruction.INITIALIZER, NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        EXTEND("extend", true, NestedInstruction.VALUES, false, true),
        MAP_TYPE(null, false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        FIELD(null, false, NestedInstruction.VALUES, false, false),
        FIELD_OPTIONS(null, false, NestedInstruction.VALUES, true, true),
        GROUP_BODY(null, true, List.of(NestedInstruction.BODY_OR_VALUE), true, false);

        private final String name;
        private final boolean nestable;
        private final List<NestedInstruction> nestedInstructions;
        private final boolean circularInstructions;
        private final boolean moveInstructionAutomatically;
        Instruction(String name, boolean nestable, List<NestedInstruction> nestedInstructions, boolean circularInstructions, boolean moveInstructionAutomatically) {
            this.name = name;
            this.nestable = nestable;
            this.nestedInstructions = nestedInstructions;
            this.circularInstructions = circularInstructions;
            this.moveInstructionAutomatically = moveInstructionAutomatically;
        }

        public static Instruction of(String name, boolean nested) {
            return name == null ? UNKNOWN : Arrays.stream(values())
                    .filter(entry -> (!nested || entry.nestable) && Objects.equals(entry.name, name))
                    .findFirst()
                    .orElse(UNKNOWN);
        }

        public List<NestedInstruction> nestedInstructions() {
            return nestedInstructions;
        }

        public boolean hasCircularInstructions() {
            return circularInstructions;
        }

        public boolean shouldMoveInstructionAutomatically() {
            return moveInstructionAutomatically;
        }
    }

    private enum NestedInstruction {
        DECLARATION,
        INITIALIZER,
        BODY_OR_VALUE,
        NESTED_INSTRUCTION_OR_END;

        private static final List<NestedInstruction> VALUES = List.of(values());

        public NestedInstruction next(Instruction instruction) {
            if (this == instruction.nestedInstructions().getLast()) {
                return instruction.hasCircularInstructions() ? instruction.nestedInstructions().getFirst() : instruction.nestedInstructions().getLast();
            }

            return instruction.nestedInstructions().get(instruction.nestedInstructions().indexOf(this) + 1);
        }
    }
}
