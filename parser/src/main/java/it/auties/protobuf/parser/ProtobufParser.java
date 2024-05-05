package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufInternalException;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.tree.ProtobufObjectTree.*;
import it.auties.protobuf.parser.type.ProtobufMapType;
import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufPrimitiveType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_2;
import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_3;

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
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String MAP_TYPE = "map";

    static {
        var parser = new ProtobufParser();
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufInternalException("Missing built-in .proto");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            BUILT_INS = parser.parse(builtInTypesPath);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufInternalException("Missing built-in .proto");
        }
    }

    private final Deque<ProtobufIndexedBodyTree<?>> objects;
    private final Deque<Instruction> instructions;
    private final Deque<NestedInstruction> nestedInstructions;
    private final ReentrantLock parserLock;
    private ProtobufDocument document;
    private StreamTokenizer tokenizer;

    public ProtobufParser() {
        this.objects = new LinkedList<>();
        this.instructions = new LinkedList<>();
        this.nestedInstructions = new LinkedList<>();
        this.parserLock = new ReentrantLock(true);
    }

    public Set<ProtobufDocument> parse(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Set.of(parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashSet<ProtobufDocument>();
            for(var file : files) {
                results.add(doParse(file, Files.readString(file)));
            }

            attributeImports(results);
            results.forEach(statement -> attributeStatement(statement, statement));
            results.addAll(getImportedDocuments(results));
            return results;
        }
    }

    private List<ProtobufDocument> getImportedDocuments(Set<ProtobufDocument> documents) {
        return documents.stream()
                .map(ProtobufDocument::imports)
                .flatMap(Collection::stream)
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .toList();
    }

    public ProtobufDocument parseOnly(String input) {
        var result = doParse(null, input);
        attributeStatement(result, result);
        return result;
    }

    public ProtobufDocument parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.readString(path));
        attributeImports(List.of(result));
        attributeStatement(result, result);
        return result;
    }

    private ProtobufDocument doParse(Path location, String input) {
        try {
            parserLock.lock();
            this.document = new ProtobufDocument(location);
            document.setName(location == null ? "" : location.getFileName().toString());
            this.tokenizer = new StreamTokenizer(new StringReader(input));
            tokenizer.wordChars('_', '_');
            tokenizer.wordChars('"', '"');
            tokenizer.wordChars('\'', '\'');
            tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
            String token;
            while ((token = nextToken()) != null) {
                handleToken(token);
            }

            return document;
        }finally {
            document = null;
            objects.clear();
            instructions.clear();
            nestedInstructions.clear();
            tokenizer = null;
            parserLock.unlock();
        }
    }

    private void attributeImports(Collection<ProtobufDocument> documents) {
        var canonicalPathToDocumentMap = getImportsMap(documents);
        documents.stream()
                .flatMap(document -> document.imports().stream())
                .filter(importStatement -> !importStatement.isAttributed())
                .forEach(importStatement -> {
                    var imported = canonicalPathToDocumentMap.get(importStatement.location());
                    ProtobufTypeException.check(imported != null,
                            "Cannot resolve import %s", importStatement.location());
                    importStatement.setDocument(imported);
                });
    }

    private Map<String, ProtobufDocument> getImportsMap(Collection<ProtobufDocument> documents) {
        return Stream.of(documents, BUILT_INS)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(entry -> Map.entry(entry.qualifiedPath().orElse(entry.name().orElseThrow()), entry))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void attributeStatement(ProtobufDocument document, ProtobufTree statement) {
        switch (statement) {
            case ProtobufDocument documentStatement -> documentStatement.statements()
                    .forEach(child -> attributeStatement(document, child));
            case ProtobufMessageTree messageStatement -> messageStatement.statements()
                    .forEach(child -> attributeStatement(document, child));
            case ProtobufOneOfTree oneOfStatement -> oneOfStatement.statements()
                    .forEach(child -> attributeStatement(document, child));
            case ProtobufTypedFieldTree fieldStatement -> attributeTypedStatement(document, fieldStatement, fieldStatement.type().orElse(null));
            default -> {}
        }
    }

    private void attributeTypedStatement(ProtobufDocument document, ProtobufTypedFieldTree typedFieldTree, ProtobufTypeReference typeReference) {
        switch (typeReference) {
            case ProtobufMapType mapType -> {
                attributeTypedStatement(document, typedFieldTree, mapType.keyType().orElseThrow());
                attributeTypedStatement(document, typedFieldTree, mapType.valueType().orElseThrow());
            }
            case ProtobufObjectType messageType -> attributeType(document, typedFieldTree, messageType);
            case ProtobufPrimitiveType ignored -> {}
            case null -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void attributeType(ProtobufDocument document, ProtobufTypedFieldTree typedFieldTree, ProtobufObjectType fieldType) {
        if (fieldType.isAttributed()) {
            return;
        }

        var accessed = fieldType.name();
        var types = accessed.split(TYPE_SELECTOR_SPLITTER);
        var type = getLocalType(typedFieldTree, types[0])
                .orElse(null);
        for(var index = 1; index < types.length; index++){
            if(type == null) {
                break;
            }

            type = type.getStatement(types[index], ProtobufObjectTree.class).orElseThrow(() -> new ProtobufTypeException(
                    "Cannot resolve type %s in field %s inside %s",
                    tokenizer.lineno(),
                    typedFieldTree.type().map(ProtobufTypeReference::name).orElse(null),
                    typedFieldTree.name(),
                    typedFieldTree.parent().flatMap(ProtobufBodyTree::name).orElse(null))
            );
        }

        if(type == null) {
            type = getImportedType(document, accessed).orElse(null);
        }

        if(type == null) {
            throw new ProtobufTypeException("Cannot resolve type %s for field %s inside %s".formatted(
                    typedFieldTree.type().map(ProtobufTypeReference::name).orElse("<missing>"),
                    typedFieldTree.name().orElse("<missing>"),
                    typedFieldTree.parent().flatMap(ProtobufBodyTree::name).orElse(null))
            );
        }

        fieldType.attribute(type);
    }

    private Optional<ProtobufObjectTree<?>> getLocalType(ProtobufTypedFieldTree typedFieldTree, String accessed) {
        var parent = typedFieldTree.parent().orElse(null);
        ProtobufObjectTree<?> innerType = null;
        while (parent != null && innerType == null){
            innerType = parent.getStatement(accessed, ProtobufObjectTree.class)
                    .orElse(null);
            parent = parent.parent().orElse(null);
        }

        if(innerType != null) {
            return Optional.of(innerType);
        }

        return Optional.empty();
    }

    private Optional<ProtobufObjectTree<?>> getImportedType(ProtobufDocument document, String accessed) {
        for(var imported : document.imports()) {
            var importedDocument = imported.document()
                    .orElse(null);
            if(importedDocument == null) {
                continue;
            }

            var importedPackage = importedDocument.packageName()
                    .orElse(null);
            var importedName = importedPackage != null && accessed.startsWith(importedPackage + TYPE_SELECTOR)
                    ? accessed.substring(importedPackage.length() + 1) : accessed;
            var simpleImportName = importedName.split(TYPE_SELECTOR_SPLITTER);
            ProtobufBodyTree<?> type = document;
            for (var importPart : simpleImportName) {
                type = type.getStatement(importPart, ProtobufObjectTree.class).orElse(null);
                if (type == null) {
                    break;
                }
            }

            if(type instanceof ProtobufObjectTree<?> result) {
                return Optional.of(result);
            }
        }

        return Optional.empty();
    }

    private void handleToken(String token) {
        switch (token) {
            case OBJECT_END -> handleObjectEnd();
            case TYPE_PARAMETERS_START -> handleTypeParametersStart();
            case TYPE_PARAMETERS_END -> handleTypeParametersEnd();
            case ARRAY_START -> handleOptionsStart();
            case ARRAY_END -> handleOptionsEnd();
            default -> handleTokenOnLastInstruction(token);
        }
    }

    private void handleOptionsStart() {
        ProtobufSyntaxException.check(hasFieldScope(),
                "Unexpected token", tokenizer.lineno());
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
        ProtobufSyntaxException.check(instructions.peekLast() == Instruction.FIELD_OPTIONS,
                "Unexpected token", tokenizer.lineno());
        var fieldTree = (ProtobufFieldTree) objects.getLast()
                .lastStatement()
                .orElseThrow();
        var lastOption = fieldTree.lastOption()
                .orElseThrow();
        ProtobufSyntaxException.check(lastOption.isAttributed(),
                "Unexpected token", tokenizer.lineno());
        jumpOutInstruction();
    }

    private boolean hasFieldScope() {
        return Optional.ofNullable(objects.peekLast())
                .flatMap(ProtobufBodyTree::lastStatement)
                .filter(entry -> entry instanceof ProtobufFieldTree)
                .map(entry -> (ProtobufFieldTree) entry)
                .filter(entry -> entry.options().isEmpty())
                .isPresent();
    }

    private void handleTokenOnLastInstruction(String token) {
        var instructionsSize = instructions.size();
        switch (instructions.peekLast()) {
            case UNKNOWN -> throw new ProtobufInternalException("Unexpected state");
            case null -> handleInstruction(token);
            case PACKAGE -> handlePackage(token);
            case SYNTAX -> handleSyntaxState(token);
            case OPTION -> handleOptionState(token);
            case MESSAGE, ENUM, ONE_OF -> handleInstructionWithBody(token);
            case RESERVED -> handleReserved(token);
            case EXTENSIONS -> handleExtensions(token);
            case SERVICE -> handleService();
            case IMPORT -> handleImport(token);
            case MAP_TYPE -> handleMapType(token);
            case FIELD_OPTIONS -> handleFieldOption(token);
            case FIELD -> handleField(token);
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
        var fieldTree = (ProtobufTypedFieldTree) objects.getLast()
                .lastStatement()
                .orElseThrow();
        var fieldType = (ProtobufTypeReference) fieldTree.type()
                .orElseThrow();
        var objectType = (ProtobufMapType) fieldType;
        switch (nestedInstructions.peekLast()) {
            case BODY_OR_VALUE -> {
                if(isStatementEnd(token)) {
                    ProtobufSyntaxException.check(objectType.isAttributed(),
                            "Unexpected token", tokenizer.lineno());
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

                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(!objectType.isAttributed() && Objects.equals(token, LIST_SEPARATOR),
                    "Unexpected token", tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleImport(String token) {
        ProtobufSyntaxException.check(document.location().isPresent(),
                "Imports are not supported when parsing a raw protobuf", tokenizer.lineno());
        ProtobufSyntaxException.check(objects.isEmpty(),
                "Illegal import statement", tokenizer.lineno());
        switch (nestedInstructions.peekLast()) {
            case BODY_OR_VALUE -> {
                ProtobufSyntaxException.check(hasDuplicateImport(token),
                        "Duplicate import statement", tokenizer.lineno());
                var importStatement = new ProtobufImportTree(token);
                document.addStatement(importStatement);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token", tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleTypeParametersEnd() {
        ProtobufSyntaxException.check(instructions.peekLast() == Instruction.MAP_TYPE && nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END,
                "Unexpected token", tokenizer.lineno());
        jumpOutInstruction();
    }

    private void handleTypeParametersStart() {
        ProtobufSyntaxException.check(isValidTypeParameterScope(),
                "Unexpected generic parameter", tokenizer.lineno());
        jumpIntoInstruction(Instruction.MAP_TYPE);
    }

    private boolean isValidTypeParameterScope() {
        return Optional.ofNullable(objects.peekLast())
                .flatMap(ProtobufBodyTree::lastStatement)
                .filter(entry -> entry instanceof ProtobufTypedFieldTree)
                .map(entry -> (ProtobufTypedFieldTree) entry)
                .flatMap(ProtobufTypedFieldTree::type)
                .filter(entry -> entry instanceof ProtobufMapType)
                .isPresent();
    }

    private void handleObjectEnd() {
        var object = objects.pollLast();
        ProtobufSyntaxException.check(hasAnyStatements(object),
                "Illegal enum or oneof without any constants", tokenizer.lineno());
        ProtobufSyntaxException.check(isValidReservable(object),
                "Illegal use of reserved field", tokenizer.lineno());
        ProtobufSyntaxException.check(isValidEnumConstant(object),
                "Proto3 enums require the first constant to have index 0", tokenizer.lineno());
        jumpOutInstruction();
    }

    private boolean isValidEnumConstant(ProtobufIndexedBodyTree<?> indexedBodyTree) {
        return !(indexedBodyTree instanceof ProtobufEnumTree enumTree) || document.version().orElse(ProtobufVersion.defaultVersion()) != PROTOBUF_3 || enumTree.firstStatement()
                .filter(constant -> constant.index().isPresent() && constant.index().getAsInt() == 0)
                .isPresent();
    }

    private boolean hasDuplicateImport(String token) {
        return document.statements()
                .stream()
                .noneMatch(entry -> entry instanceof ProtobufImportTree importStatement && Objects.equals(importStatement.location(), token));
    }

    private boolean isValidReservable(ProtobufIndexedBodyTree<?> indexedBodyTree) {
        return !(indexedBodyTree instanceof ProtobufObjectTree<?> reservable) || reservable.reserved().isEmpty() || indexedBodyTree.statements()
                .stream()
                .map(entry -> entry instanceof ProtobufOneOfTree oneOfTree ? oneOfTree.statements() : List.of(entry))
                .flatMap(Collection::stream)
                .filter(entry -> entry instanceof ProtobufIndexedTree)
                .map(entry -> (ProtobufIndexedTree) entry)
                .noneMatch(entry -> hasForbiddenField(reservable, entry));
    }

    private boolean hasForbiddenField(ProtobufObjectTree<?> reservable, ProtobufIndexedTree entry) {
        var index = entry.index().orElseThrow();
        var name = entry instanceof ProtobufFieldTree fieldTree ? fieldTree.name().orElseThrow() : null;
        return reservable.hasReservedIndex(index) || reservable.hasReservedName(name);
    }

    private boolean hasAnyStatements(ProtobufIndexedBodyTree<?> object) {
        return (object instanceof ProtobufEnumTree enumStatement && !enumStatement.statements().isEmpty())
                || (object instanceof ProtobufOneOfTree oneOfStatement && !oneOfStatement.statements().isEmpty())
                || object instanceof ProtobufMessageTree;
    }

    private void handleInstructionWithBody(String token) {
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> handleNestedBodyDeclaration(instructions.getLast(), token);
            case INITIALIZER -> ProtobufSyntaxException.check(isObjectStart(token),
                    "Expected message initializer after message declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> handleNestedInstructionForBody(token);
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleNestedInstructionForBody(String token) {
        var nestedInstruction = Instruction.of(token, true);
        var actualNestedInstruction = nestedInstruction == Instruction.UNKNOWN ? Instruction.FIELD : nestedInstruction;
        if (actualNestedInstruction == Instruction.FIELD) {
            handleFieldDeclaration(token);
        }
        jumpIntoInstruction(actualNestedInstruction);
    }

    private void handleReserved(String token) {
        var scope = objects.peekLast();
        if(!(scope instanceof ProtobufObjectTree<?> reservable)) {
            throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
        }

        if(isStatementEnd(token)) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END && !reservable.reserved().isEmpty(),
                    "Unexpected token", tokenizer.lineno());
            return;
        }

        ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.BODY_OR_VALUE || token.equals(LIST_SEPARATOR) || token.equals(RANGE_OPERATOR),
                "Unexpected token", tokenizer.lineno());
        if(nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE && token.equals(LIST_SEPARATOR)) {
            return;
        }

        var reserved = reservable.lastReserved().orElse(null);
        var didDeclareRange = Objects.equals(token, RANGE_OPERATOR);
        if(didDeclareRange) {
            if(!(reserved instanceof ReservedIndexes indexes)) {
                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }

            var lastValue = indexes.pollLastValue();
            if(lastValue == null || lastValue == Integer.MAX_VALUE) {
                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }

            if(indexes.isEmpty()) {
                reservable.pollReserved();
            }

            reservable.addReservedRange(lastValue);
            return;
        }

        switch (reserved) {
            case ReservedIndexes reservedIndexes -> {
                var index = parseIndex(token, false, false);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(reservedIndexes.addValue(index.get()),
                        "Duplicate reserved index", tokenizer.lineno());
            }
            case ReservedNames reservedNames -> {
                var literal = parseStringLiteral(token);
                ProtobufSyntaxException.check(literal.isPresent(), "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(reservedNames.addValue(literal.get()),
                        "Duplicate reserved name", tokenizer.lineno());
            }
            case ReservedRange reservedRange -> {
                var index = parseIndex(token, false, true);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(reservedRange.setMax(index.get()),
                        "Duplicate reserved index", tokenizer.lineno());
            }
            case null -> {
                var literal = parseStringLiteral(token);
                if(literal.isPresent()) {
                    ProtobufSyntaxException.check(reservable.addReservedName(literal.get()),
                            "Duplicate reserved name", tokenizer.lineno());
                    return;
                }

                var index = parseIndex(token, false, true);
                if(index.isPresent()) {
                    ProtobufSyntaxException.check(reservable.addReservedIndex(index.get()),
                            "Duplicate reserved index", tokenizer.lineno());
                    return;
                }

                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }
        }
    }

    private void handleExtensions(String token) {
        var scope = objects.peekLast();
        if(!(scope instanceof ProtobufObjectTree<?> extensible)) {
            throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
        }

        if(isStatementEnd(token)) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END && !extensible.extensions().isEmpty(),
                    "Unexpected token", tokenizer.lineno());
            return;
        }

        ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.BODY_OR_VALUE || token.equals(LIST_SEPARATOR) || token.equals(RANGE_OPERATOR),
                "Unexpected token", tokenizer.lineno());
        if(nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE && token.equals(LIST_SEPARATOR)) {
            return;
        }

        var extensions = extensible.lastExtension().orElse(null);
        var didDeclareRange = Objects.equals(token, RANGE_OPERATOR);
        if(didDeclareRange) {
            if(!(extensions instanceof ExtensionsIndexes indexes)) {
                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }

            var lastValue = indexes.pollLastValue();
            if(lastValue == null || lastValue == Integer.MAX_VALUE) {
                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }

            if(indexes.isEmpty()) {
                extensible.pollExtensions();
            }

            extensible.addReservedRange(lastValue);
            return;
        }

        switch (extensions) {
            case ExtensionsIndexes extensionsIndexes -> {
                var index = parseIndex(token, false, false);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(extensionsIndexes.addValue(index.get()),
                        "Duplicate extensions index", tokenizer.lineno());
            }
            case ExtensionsRange extensionsRange -> {
                var index = parseIndex(token, false, true);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(extensionsRange.setMax(index.get()),
                        "Duplicate extensions index", tokenizer.lineno());
            }
            case null -> {
                var index = parseIndex(token, false, true);
                if(index.isPresent()) {
                    ProtobufSyntaxException.check(extensible.addExtensionsIndex(index.get()),
                            "Duplicate extensions index", tokenizer.lineno());
                    return;
                }

                throw new ProtobufSyntaxException("Unexpected token", tokenizer.lineno());
            }
        }
    }

    private Optional<String> parseStringLiteral(String token) {
        return (token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))
                ? Optional.of(token) : Optional.empty();
    }

    private ProtobufIndexedBodyTree<?> checkFieldParent(String token, ProtobufFieldModifier modifier) {
        var scope = objects.peekLast();
        if (modifier != ProtobufFieldModifier.NOTHING) {
            ProtobufSyntaxException.check(document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2 || modifier != ProtobufFieldModifier.REQUIRED,
                    "Support for the required label was dropped in proto3", tokenizer.lineno());
            ProtobufSyntaxException.check(scope instanceof ProtobufMessageTree,
                    "Expected message scope for field declaration", tokenizer.lineno());
            return scope;
        }

        if (document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_3 && scope instanceof ProtobufMessageTree) {
            return scope;
        }

        if (scope instanceof ProtobufEnumTree || scope instanceof ProtobufOneOfTree || token.equals(MAP_TYPE)) {
            return scope;
        }

        throw new ProtobufSyntaxException("Expected enum or one of scope for field declaration without label",
                tokenizer.lineno());
    }

    private void handleField(String token) {
        var parent = objects.getLast();
        var lastField = parent.lastStatement().orElseThrow();
        switch (lastField) {
            case ProtobufModifiableFieldTree fieldTree -> handleModifiableField(token, fieldTree, parent);
            case ProtobufEnumConstantTree constant -> handleEnumConstant(token, constant, parent);
            default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleModifiableField(String token, ProtobufModifiableFieldTree fieldTree, ProtobufIndexedBodyTree<?> parent) {
        switch (parent) {
            case ProtobufMessageTree ignored -> handleMessageField(token, fieldTree, parent);
            case ProtobufOneOfTree ignored -> handleOneOfField(token, fieldTree, parent);
            default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleOneOfField(String token, ProtobufModifiableFieldTree fieldTree, ProtobufIndexedBodyTree<?> parent) {
        if(fieldTree.type().isEmpty()) {
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token", tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Unexpected token", tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token", tokenizer.lineno());
    }

    private void handleMessageField(String token, ProtobufModifiableFieldTree fieldTree, ProtobufIndexedBodyTree<?> parent) {
        if(fieldTree.modifier().isEmpty()) {
            fieldTree.setModifier(ProtobufFieldModifier.of(token));
            return;
        }

        if(fieldTree.type().isEmpty()) {
            ProtobufSyntaxException.check(!token.equals(MAP_TYPE) || fieldTree.modifier().get() == ProtobufFieldModifier.NOTHING,
                    "Unexpected token", tokenizer.lineno());
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token", tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Unexpected token", tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token", tokenizer.lineno());
    }

    private void handleEnumConstant(String token, ProtobufEnumConstantTree constant, ProtobufIndexedBodyTree<?> parent) {
        if(constant.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            constant.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(constant.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.DECLARATION || isAssignmentOperator(token),
                    "Unexpected token", tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.DECLARATION) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, true, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Unexpected token", tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            constant.setIndex(index);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token", tokenizer.lineno());
    }

    private void handleFieldDeclaration(String token) {
        var modifier = ProtobufFieldModifier.of(token);
        var parent = checkFieldParent(token, modifier);
        switch (parent) {
            case ProtobufMessageTree messageTree -> handleMessageFieldDeclaration(token, messageTree, modifier);
            case ProtobufEnumTree enumTree -> handleEnumConstantDeclaration(token, enumTree);
            case ProtobufOneOfTree oneOfTree -> handleOneOfFieldDeclaration(token, oneOfTree, modifier);
            default -> throw new ProtobufInternalException("Unexpected context");
        }
    }

    private void handleOneOfFieldDeclaration(String token, ProtobufOneOfTree oneOfTree, ProtobufFieldModifier modifier) {
        var field = new ProtobufModifiableFieldTree();
        ProtobufSyntaxException.check(modifier == ProtobufFieldModifier.NOTHING || modifier == ProtobufFieldModifier.OPTIONAL,
                "Unexpected token", tokenizer.lineno());
        field.setModifier(modifier);
        if(modifier == ProtobufFieldModifier.NOTHING) {
            field.setType(ProtobufTypeReference.of(token));
        }

        oneOfTree.addStatement(field);
    }

    private void handleEnumConstantDeclaration(String token, ProtobufEnumTree enumTree) {
        var field = new ProtobufEnumConstantTree();
        field.setName(token);
        enumTree.addStatement(field);
    }

    private void handleMessageFieldDeclaration(String token, ProtobufMessageTree messageTree, ProtobufFieldModifier modifier) {
        var field = new ProtobufModifiableFieldTree();
        switch (document.version().orElse(ProtobufVersion.defaultVersion())){
            case PROTOBUF_2 -> {
                field.setModifier(modifier);
                if(token.equals(MAP_TYPE)) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
            case PROTOBUF_3 -> {
                field.setModifier(modifier);
                if(modifier == ProtobufFieldModifier.NOTHING) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
        }
        messageTree.addStatement(field);
    }

    private void handleOptionState(String token) {
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> document.addOption(token);
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token),
                    "Expected assignment operator after option declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> {
                var lastOption = document.lastOption();
                ProtobufSyntaxException.check(lastOption.isPresent(),
                                "Unexpected token", tokenizer.lineno());
                lastOption.get().setValue(token);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token", tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleFieldOption(String token) {
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> {
                var fieldTree = (ProtobufFieldTree) objects.getLast()
                        .lastStatement()
                        .orElseThrow();
                fieldTree.addOption(token);
            }
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token), "Unexpected token", tokenizer.lineno());
            case BODY_OR_VALUE -> {
                var fieldTree = (ProtobufFieldTree) objects.getLast()
                        .lastStatement()
                        .orElseThrow();
                var lastOption = fieldTree.lastOption()
                        .orElseThrow();
                lastOption.setValue(token);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(token.equals(LIST_SEPARATOR) || isStatementEnd(token),
                    "Unexpected token", tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleSyntaxState(String token) {
        ProtobufSyntaxException.check(document.statements().isEmpty(),
                "Unexpected token", tokenizer.lineno());
        switch (nestedInstructions.peekLast()) {
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token),
                    "Unexpected token", tokenizer.lineno());
            case BODY_OR_VALUE -> document.setVersion(ProtobufVersion.of(token)
                    .orElseThrow(() -> new ProtobufSyntaxException("Illegal syntax declaration: %s is not a valid version".formatted(token), tokenizer.lineno())));
            case NESTED_INSTRUCTION_OR_END -> {
                ProtobufSyntaxException.check(document.version().isPresent(),
                        "Unexpected token", tokenizer.lineno());
                ProtobufSyntaxException.check(isStatementEnd(token),
                        "Unexpected token", tokenizer.lineno());
            }
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handlePackage(String token) {
        if (nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE) {
            ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token", tokenizer.lineno());
            return;
        }

        ProtobufSyntaxException.check(document.packageName().isEmpty(),
                "Duplicate package statement", tokenizer.lineno());
        document.setPackageName(token);
    }

    private void handleInstruction(String token) {
        var instruction = Instruction.of(token, false);
        ProtobufSyntaxException.check(instruction != Instruction.UNKNOWN,
                "Unexpected token", tokenizer.lineno(), token);
        jumpIntoInstruction(instruction);
    }

    private String nextToken() {
        try {
            var token = tokenizer.nextToken();
            if (token == StreamTokenizer.TT_EOF) {
                return null;
            }

            return switch (token) {
                case StreamTokenizer.TT_WORD, STRING_LITERAL_DELIMITER -> tokenizer.sval;
                case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
                default -> String.valueOf((char) token);
            };
        } catch (IOException exception) {
            return null;
        }
    }

    private void handleNestedBodyDeclaration(Instruction instruction, String name) {
        var object = switch (instruction) {
            case MESSAGE -> new ProtobufMessageTree(name);
            case ENUM -> new ProtobufEnumTree(name);
            case ONE_OF -> new ProtobufOneOfTree(name);
            default -> throw new ProtobufSyntaxException("Illegal state", tokenizer.lineno());
        };
        var scope = objects.peekLast();
        if(scope == null){
            if(!(object instanceof ProtobufDocumentChildTree childTree)) {
                throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
            }

            document.addStatement(childTree);
        }else if(scope instanceof ProtobufMessageTree message){
            message.addStatement(object);
        }else {
            throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
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

    private boolean isLegalIdentifier(String instruction) {
        return !instruction.isBlank()
                && !instruction.isEmpty()
                && !Character.isDigit(instruction.charAt(0))
                && instruction.chars().mapToObj(entry -> (char) entry).noneMatch(SYMBOLS::contains);
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

    private enum Instruction {
        UNKNOWN(null, false, NestedInstruction.VALUES, false, true),
        PACKAGE("package", false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        SYNTAX("syntax", false, List.of(NestedInstruction.INITIALIZER, NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        RESERVED("reserved", true, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        OPTION("option", false, NestedInstruction.VALUES, false, true),
        MESSAGE("message", true, NestedInstruction.VALUES, false, true),
        ENUM("enum", true, NestedInstruction.VALUES, false, true),
        ONE_OF("oneof", true, NestedInstruction.VALUES, false, true),
        SERVICE("service", true, NestedInstruction.VALUES, false, true),
        IMPORT("import", false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), false, true),
        EXTENSIONS("extensions", true, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        MAP_TYPE(null, false, List.of(NestedInstruction.BODY_OR_VALUE, NestedInstruction.NESTED_INSTRUCTION_OR_END), true, true),
        FIELD(null, false, NestedInstruction.VALUES, false, false),
        FIELD_OPTIONS(null, false, NestedInstruction.VALUES, true, true);

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
