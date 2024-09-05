package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufInternalException;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.body.document.ProtobufDocument;
import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;
import it.auties.protobuf.parser.tree.nested.impors.ProtobufImportTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufObjectTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufObjectTree.*;
import it.auties.protobuf.parser.tree.nested.field.ProtobufEnumConstantTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufGroupTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufMessageTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionedTree;
import it.auties.protobuf.parser.type.*;

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
    private static final String DEFAULT_KEYWORD_OPTION = "default";

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

    private final Deque<ProtobufBodyTree<?>> objects;
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
            for (var statement : results) {
                attributeStatement(statement, statement);
            }
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
        } catch (ProtobufSyntaxException syntaxException) {
            throw ProtobufSyntaxException.withPath(syntaxException.getMessage(), location);
        } finally {
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
            case ProtobufDocument documentStatement -> attributeDocument(document, documentStatement);
            case ProtobufMessageTree messageStatement -> messageStatement.statements()
                    .forEach(child -> attributeStatement(document, child));
            case ProtobufOneofTree oneOfStatement -> oneOfStatement.statements()
                    .forEach(child -> attributeStatement(document, child));
            case ProtobufGroupableFieldTree fieldStatement -> attributeTypedStatement(document, fieldStatement, fieldStatement.type().orElse(null));
            default -> {}
        }
    }

    private void attributeDocument(ProtobufDocument document, ProtobufDocument documentStatement) {
        for (var child : documentStatement.statements()) {
            attributeStatement(document, child);
        }

        attributeOptions(document);
    }

    private void attributeTypedStatement(ProtobufDocument document, ProtobufGroupableFieldTree typedFieldTree, ProtobufTypeReference typeReference) {
        switch (typeReference) {
            case ProtobufMapType mapType -> {
                attributeTypedStatement(document, typedFieldTree, mapType.keyType().orElseThrow());
                attributeTypedStatement(document, typedFieldTree, mapType.valueType().orElseThrow());
            }
            case ProtobufObjectType messageType -> attributeType(document, typedFieldTree, messageType);
            case null, default -> {}
        }
        attributeOptions(typedFieldTree);
    }

    private void attributeType(ProtobufDocument document, ProtobufGroupableFieldTree typedFieldTree, ProtobufObjectType fieldType) {
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

    private Optional<ProtobufObjectTree<?>> getLocalType(ProtobufGroupableFieldTree typedFieldTree, String accessed) {
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

    private void attributeOptions(ProtobufOptionedTree<?> fieldTree) {
        for(var option : fieldTree.options()) {
            if(Objects.equals(option.name(), DEFAULT_KEYWORD_OPTION)) {
                attributeDefaultValue(fieldTree, option);
            }
        }
    }

    private void attributeDefaultValue(ProtobufOptionedTree<?> fieldTree, ProtobufOptionTree option) {
        if(!(fieldTree instanceof ProtobufGroupableFieldTree typedFieldTree)) {
            throw new ProtobufTypeException("Default values are only supported by typed fields");
        }

        switch (typedFieldTree.type().orElseThrow(() -> new ProtobufInternalException("Typed field with no type"))) {
            case ProtobufPrimitiveType primitiveType -> attributePrimitiveDefaultValue(option, primitiveType);
            case ProtobufObjectType objectType
                    when objectType.declaration().orElseThrow(() -> new ProtobufInternalException("Object type is not attributed")) instanceof ProtobufEnumTree enumTree
                    -> attributeEnumDefaultValue(option, enumTree);
            default -> throw new ProtobufSyntaxException("Default values are only supported for fields whose type is a primitive or an enum", tokenizer.lineno());
        }
    }

    private void attributeEnumDefaultValue(ProtobufOptionTree lastOption, ProtobufEnumTree enumTree) {
        var enumDefault = enumTree.statements()
                .stream()
                .map(ProtobufEnumConstantTree::name)
                .flatMap(Optional::stream)
                .filter(constantName -> Objects.equals(constantName, lastOption.value()))
                .findFirst()
                .orElseThrow(() -> new ProtobufTypeException("Invalid default value for type enum: " + lastOption.value()));
        lastOption.setAttributedValue(enumDefault);
    }

    private void attributePrimitiveDefaultValue(ProtobufOptionTree option, ProtobufPrimitiveType primitiveType) {
        switch (primitiveType.protobufType()) {
            case FLOAT -> {
                try {
                    var value = Float.parseFloat(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type float");
                }
            }
            case DOUBLE -> {
                try {
                    var value = Double.parseDouble(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type double");
                }
            }
            case BOOL -> {
                try {
                    var value = Boolean.parseBoolean(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type double");
                }
            }
            case STRING, BYTES -> parseStringLiteral(String.valueOf(option.value())).ifPresentOrElse(option::setAttributedValue, () -> {
                throw new ProtobufTypeException("Invalid default value for type string");
            });
            case INT32, SINT32, FIXED32, SFIXED32 -> {
                try {
                    var value = Integer.parseInt(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type int");
                }
            }
            case UINT32 -> {
                try {
                    var value = Integer.parseUnsignedInt(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type unsigned int");
                }
            }

            case INT64, SINT64, FIXED64, SFIXED64 -> {
                try {
                    var value = Long.parseLong(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type long");
                }
            }
            case UINT64 -> {
                try {
                    var value = Long.parseUnsignedLong(String.valueOf(option.value()));
                    option.setAttributedValue(value);
                }catch (NumberFormatException exception) {
                    throw new ProtobufTypeException("Invalid default value for type unsigned long");
                }
            }
        }
    }

    private void handleOptionsStart() {
        ProtobufSyntaxException.check(hasFieldScope(),
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
        ProtobufSyntaxException.check(instructions.peekLast() == Instruction.FIELD_OPTIONS,
                "Unexpected token " + ARRAY_END, tokenizer.lineno());
        var fieldTree = (ProtobufFieldTree) objects.getLast()
                .lastStatement()
                .orElseThrow();
        var lastOption = fieldTree.lastOption()
                .orElseThrow();
        ProtobufSyntaxException.check(lastOption.hasValue(),
                "Unexpected token " + ARRAY_END, tokenizer.lineno());
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

    private void handleToken(String token) {
        var instructionsSize = instructions.size();
        switch (instructions.peekLast()) {
            case PACKAGE -> handleDocumentPackage(token);
            case SYNTAX -> handleDocumentSyntax(token);
            case OPTION -> handleDocumentOption(token);
            case MESSAGE, ENUM, ONE_OF -> handleInstructionWithBody(token);
            case RESERVED -> handleReserved(token);
            case EXTENSIONS -> handleExtensions(token);
            case SERVICE -> handleService();
            case IMPORT -> handleImport(token);
            case MAP_TYPE -> handleMapType(token);
            case FIELD_OPTIONS -> handleFieldOption(token);
            case FIELD -> handleField(token);
            case GROUP_BODY -> handleNestedInstructionForBody(token, true);
            case UNKNOWN -> throw new ProtobufInternalException("Unexpected state");
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
        var fieldTree = (ProtobufGroupableFieldTree) objects.getLast()
                .lastStatement()
                .orElseThrow();
        var fieldType = fieldTree.type()
                .orElseThrow();
        var objectType = (ProtobufMapType) fieldType;
        switch (nestedInstructions.peekLast()) {
            case BODY_OR_VALUE -> {
                if(isStatementEnd(token)) {
                    ProtobufSyntaxException.check(objectType.isAttributed(),
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

                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(!objectType.isAttributed() && Objects.equals(token, LIST_SEPARATOR),
                    "Unexpected token " + token, tokenizer.lineno());
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
                var importValue = parseStringLiteral(token)
                        .orElseThrow(() -> new ProtobufSyntaxException("Expected string literal as import value", tokenizer.lineno()));
                ProtobufSyntaxException.check(hasDuplicateImport(importValue),
                        "Duplicate import statement", tokenizer.lineno());
                var importStatement = new ProtobufImportTree(importValue);
                document.addStatement(importStatement);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleTypeParametersEnd() {
        ProtobufSyntaxException.check(instructions.peekLast() == Instruction.MAP_TYPE && nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END,
                "Unexpected token " + TYPE_PARAMETERS_END, tokenizer.lineno());
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
                .filter(entry -> entry instanceof ProtobufGroupableFieldTree)
                .map(entry -> (ProtobufGroupableFieldTree) entry)
                .flatMap(ProtobufGroupableFieldTree::type)
                .filter(entry -> entry instanceof ProtobufMapType)
                .isPresent();
    }

    private void handleObjectEnd() {
        var object = objects.pollLast();
        ProtobufSyntaxException.check(hasAnyStatements(object),
                "Illegal enum or oneof without any constants", tokenizer.lineno());
        ProtobufSyntaxException.check(isValidIndex(object),
                "Illegal field index used", tokenizer.lineno());
        ProtobufSyntaxException.check(isValidEnumConstant(object),
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
            throw new ProtobufInternalException("Missing group owner at line " + tokenizer.lineno());
        }

        var groupType = groupOwner.lastStatement()
                .filter(entry -> entry instanceof ProtobufGroupableFieldTree)
                .map(entry -> (ProtobufGroupableFieldTree) entry)
                .flatMap(ProtobufGroupableFieldTree::type)
                .filter(type -> type instanceof ProtobufGroupType)
                .map(type -> (ProtobufGroupType) type)
                .orElseThrow(() -> new ProtobufInternalException("Missing group type at line " + tokenizer.lineno()));
        groupType.attribute(groupTree);
    }

    private boolean isValidEnumConstant(ProtobufBodyTree<?> indexedBodyTree) {
        return !(indexedBodyTree instanceof ProtobufEnumTree enumTree) || document.version().orElse(ProtobufVersion.defaultVersion()) != PROTOBUF_3 || enumTree.firstStatement()
                .filter(constant -> constant.index().isPresent() && constant.index().getAsInt() == 0)
                .isPresent();
    }

    private boolean hasDuplicateImport(String token) {
        return document.statements()
                .stream()
                .noneMatch(entry -> entry instanceof ProtobufImportTree importStatement && Objects.equals(importStatement.location(), token));
    }

    private boolean isValidIndex(ProtobufBodyTree<?> indexedBodyTree) {
        if (!(indexedBodyTree instanceof ProtobufObjectTree<?> reservable)) {
            return true;
        }

        if (reservable.reserved().isEmpty() && reservable.extensions().isEmpty()){
            return true;
        }

        return indexedBodyTree.statements()
                .stream()
                .map(entry -> entry instanceof ProtobufOneofTree oneOfTree ? oneOfTree.statements() : List.of(entry))
                .flatMap(Collection::stream)
                .filter(entry -> entry instanceof ProtobufFieldTree)
                .map(entry -> (ProtobufFieldTree) entry)
                .noneMatch(entry -> hasForbiddenField(reservable, entry));
    }

    private boolean hasForbiddenField(ProtobufObjectTree<?> reservable, ProtobufFieldTree entry) {
        var index = entry.index().orElseThrow();
        var name = entry.name().orElseThrow();
        return reservable.hasReservedIndex(index) || reservable.hasReservedName(name) || reservable.hasExtensionsIndex(index);
    }

    private boolean hasAnyStatements(ProtobufBodyTree<?> object) {
        if(object instanceof ProtobufEnumTree enumStatement) {
            return !enumStatement.statements().isEmpty();
        }

        if(object instanceof ProtobufOneofTree oneOfStatement) {
            return !oneOfStatement.statements().isEmpty();
        }

        return true;
    }

    private void handleInstructionWithBody(String token) {
        if (token.equals(OBJECT_END)) {
            handleObjectEnd();
            return;
        }

        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> handleNestedBodyDeclaration(instructions.getLast(), token);
            case INITIALIZER -> ProtobufSyntaxException.check(isObjectStart(token),
                    "Expected message initializer after message declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> handleNestedInstructionForBody(token, false);
            case null, default -> throw new ProtobufInternalException("Unexpected state");
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

    private void handleReserved(String token) {
        var scope = objects.peekLast();
        if(!(scope instanceof ProtobufObjectTree<?> reservable)) {
            throw new ProtobufSyntaxException("Invalid scope for reserved", tokenizer.lineno());
        }

        var reserved = reservable.reserved()
                .stream()
                .filter(entry -> !entry.isAttributed())
                .findFirst()
                .orElse(null);
        if(isStatementEnd(token)) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END && !reservable.reserved().isEmpty(),
                    "Unexpected token " + token, tokenizer.lineno());
            if(reserved != null) {
                reserved.setAttributed(true);
            }

            return;
        }

        ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.BODY_OR_VALUE || token.equals(LIST_SEPARATOR) || token.equals(RANGE_OPERATOR),
                "Unexpected token " + token, tokenizer.lineno());
        if(nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE && token.equals(LIST_SEPARATOR)) {
            return;
        }

        if(Objects.equals(token, RANGE_OPERATOR)) {
            if(!(reserved instanceof ReservedIndexes indexes)) {
                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }

            var lastValue = indexes.pollLastValue();
            if(lastValue == null || lastValue == Integer.MAX_VALUE) {
                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
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
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token " + token, tokenizer.lineno());
                ProtobufSyntaxException.check(reservedIndexes.addValue(index.get()),
                        "Duplicate reserved index", tokenizer.lineno());
            }
            case ReservedNames reservedNames -> {
                var literal = parseStringLiteral(token);
                ProtobufSyntaxException.check(literal.isPresent(), "Unexpected token " + token, tokenizer.lineno());
                ProtobufSyntaxException.check(reservedNames.addValue(literal.get()),
                        "Duplicate reserved name", tokenizer.lineno());
            }
            case ReservedRange reservedRange -> {
                var index = parseIndex(token, false, true);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token " + token, tokenizer.lineno());
                ProtobufSyntaxException.check(reservedRange.setMax(index.get()),
                        "Duplicate reserved index", tokenizer.lineno());
                reserved.setAttributed(true);
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

                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }
        }
    }

    private void handleExtensions(String token) {
        var scope = objects.peekLast();
        if(!(scope instanceof ProtobufObjectTree<?> extensible)) {
            throw new ProtobufSyntaxException("Invalid scope for extensions", tokenizer.lineno());
        }

        if(isStatementEnd(token)) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.NESTED_INSTRUCTION_OR_END && !extensible.extensions().isEmpty(),
                    "Unexpected token " + token, tokenizer.lineno());
            return;
        }

        ProtobufSyntaxException.check(nestedInstructions.peekLast() == NestedInstruction.BODY_OR_VALUE || token.equals(LIST_SEPARATOR) || token.equals(RANGE_OPERATOR),
                "Unexpected token " + token, tokenizer.lineno());
        if(nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE && token.equals(LIST_SEPARATOR)) {
            return;
        }

        var extensions = extensible.extensions()
                .stream()
                .filter(entry -> !entry.isAttributed())
                .findFirst()
                .orElse(null);
        var didDeclareRange = Objects.equals(token, RANGE_OPERATOR);
        if(didDeclareRange) {
            if(!(extensions instanceof ExtensionsIndexes indexes)) {
                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }

            var lastValue = indexes.pollLastValue();
            if(lastValue == null || lastValue == Integer.MAX_VALUE) {
                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }

            if(indexes.isEmpty()) {
                extensible.pollExtensions();
            }

            extensible.addExtensionsRange(lastValue);
            return;
        }

        switch (extensions) {
            case ExtensionsIndexes extensionsIndexes -> {
                var index = parseIndex(token, false, false);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token " + token, tokenizer.lineno());
                ProtobufSyntaxException.check(extensionsIndexes.addValue(index.get()),
                        "Duplicate extensions index", tokenizer.lineno());
            }
            case ExtensionsRange extensionsRange -> {
                var index = parseIndex(token, false, true);
                ProtobufSyntaxException.check(index.isPresent(), "Unexpected token " + token, tokenizer.lineno());
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

                throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno());
            }
        }
    }

    private Optional<String> parseStringLiteral(String token) {
        return (token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))
                ? Optional.of(token.substring(1, token.length() - 1)) : Optional.empty();
    }

    private ProtobufBodyTree<?> checkFieldParent(String token, ProtobufFieldTree.Modifier modifier) {
        var scope = objects.peekLast();
        if (!(modifier instanceof ProtobufFieldTree.Modifier.MaybeNothing maybeNothing)) {
            ProtobufSyntaxException.check(document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2 || modifier.type() != ProtobufFieldTree.Modifier.Type.REQUIRED,
                    "Support for the required label was dropped in proto3", tokenizer.lineno());
            ProtobufSyntaxException.check(scope instanceof ProtobufMessageTree || scope instanceof ProtobufGroupTree,
                    "Expected message or group scope for field declaration", tokenizer.lineno());
            return scope;
        }

        if (document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_3 && scope instanceof ProtobufMessageTree) {
            return scope;
        }

        if (scope instanceof ProtobufEnumTree || scope instanceof ProtobufOneofTree || token.equals(MAP_TYPE)) {
            return scope;
        }

        throw new ProtobufSyntaxException("Expected a valid field modifier in proto2 message scope for token " + maybeNothing.token(),
                tokenizer.lineno());
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
        var lastField = parent.lastStatement().orElseThrow();
        switch (lastField) {
            case ProtobufGroupableFieldTree fieldTree -> handleModifiableField(token, fieldTree, parent);
            case ProtobufEnumConstantTree constant -> handleEnumConstant(token, constant, parent);
            default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleModifiableField(String token, ProtobufGroupableFieldTree fieldTree, ProtobufBodyTree<?> parent) {
        switch (parent) {
            case ProtobufMessageTree ignored -> handleMessageField(token, fieldTree, parent);
            case ProtobufGroupTree ignored ->  handleMessageField(token, fieldTree, parent);
            case ProtobufOneofTree ignored -> handleOneOfField(token, fieldTree, parent);
            default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    @SuppressWarnings("deprecation")
    private void handleOneOfField(String token, ProtobufGroupableFieldTree fieldTree, ProtobufBodyTree<?> parent) {
        if(fieldTree.type().isEmpty()) {
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufSyntaxException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Invalid index " + token, tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        var type = fieldTree.type();
        if(type.isPresent() && type.get().protobufType() == ProtobufType.GROUP) {
            handleGroupStart(token, fieldTree);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleGroupStart(String token, ProtobufGroupableFieldTree fieldTree) {
        ProtobufSyntaxException.check(document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2,
                "Groups are not supported in proto3", tokenizer.lineno());
        ProtobufSyntaxException.check(isObjectStart(token),
                "Unexpected token " + token, tokenizer.lineno());
        var syntheticName = fieldTree.name()
                        .orElseThrow(() -> new ProtobufInternalException("Missing field name for group"));
        ProtobufSyntaxException.check(!syntheticName.isEmpty() && Character.isUpperCase(syntheticName.charAt(0)),
                "Group name " + syntheticName + " should start with a capital letter", tokenizer.lineno());
        var groupTree = new ProtobufGroupTree(syntheticName);
        var fieldParent = fieldTree.parent()
                .orElseThrow(() -> new ProtobufInternalException("Missing field parent at line " + tokenizer.lineno()));
        groupTree.setParent(fieldParent, 2);
        objects.add(groupTree);
        jumpIntoInstruction(Instruction.GROUP_BODY);
    }

    @SuppressWarnings("deprecation")
    private void handleMessageField(String token, ProtobufGroupableFieldTree fieldTree, ProtobufBodyTree<?> parent) {
        if(fieldTree.modifier().isEmpty()) {
            var modifier = ProtobufFieldTree.Modifier.of(token);
            fieldTree.setModifier(modifier);
            return;
        }

        if(fieldTree.type().isEmpty()) {
            ProtobufSyntaxException.check(!token.equals(MAP_TYPE) || fieldTree.modifier().get().type() == ProtobufFieldTree.Modifier.Type.NOTHING,
                    "Unexpected token " + token, tokenizer.lineno());
            fieldTree.setType(ProtobufTypeReference.of(token));
            return;
        }

        if(fieldTree.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufSyntaxException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            fieldTree.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(fieldTree.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.INITIALIZER || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.INITIALIZER) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            fieldTree.setIndex(index);
            return;
        }

        var type = fieldTree.type();
        if(type.isPresent() && type.get().protobufType() == ProtobufType.GROUP) {
            handleGroupStart(token, fieldTree);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleEnumConstant(String token, ProtobufEnumConstantTree constant, ProtobufBodyTree<?> parent) {
        if(constant.name().isEmpty()) {
            ProtobufSyntaxException.check(isLegalIdentifier(token), "Illegal field name: %s",
                    tokenizer.lineno(), token);
            ProtobufSyntaxException.check(!parent.hasName(token),
                    "Duplicated name %s", tokenizer.lineno(), token);
            constant.setName(token);
            jumpIntoNestedInstruction(NestedInstruction.INITIALIZER);
            return;
        }

        if(constant.index().isEmpty()) {
            ProtobufSyntaxException.check(nestedInstructions.peekLast() != NestedInstruction.DECLARATION || isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            if(nestedInstructions.peekLast() == NestedInstruction.DECLARATION) {
                jumpIntoNestedInstruction(NestedInstruction.BODY_OR_VALUE);
                return;
            }

            var index = parseIndex(token, true, false)
                    .orElseThrow(() -> new ProtobufSyntaxException("Unexpected token " + token, tokenizer.lineno()));
            ProtobufSyntaxException.check(!parent.hasIndex(index),
                    "Duplicated index %s", tokenizer.lineno(), index);
            constant.setIndex(index);
            return;
        }

        ProtobufSyntaxException.check(isStatementEnd(token),
                "Unexpected token " + token, tokenizer.lineno());
    }

    private void handleFieldDeclaration(String token) {
        var modifier = ProtobufFieldTree.Modifier.of(token);
        var parent = checkFieldParent(token, modifier);
        switch (parent) {
            case ProtobufMessageTree messageTree -> handleMessageFieldDeclaration(token, messageTree, modifier);
            case ProtobufGroupTree groupTree -> handleGroupFieldDeclaration(token, groupTree, modifier);
            case ProtobufEnumTree enumTree -> handleEnumConstantDeclaration(token, enumTree);
            case ProtobufOneofTree oneOfTree -> handleOneOfFieldDeclaration(token, oneOfTree, modifier);
            default -> throw new ProtobufInternalException("Unexpected context");
        }
    }

    private void handleOneOfFieldDeclaration(String token, ProtobufOneofTree oneOfTree, ProtobufFieldTree.Modifier modifier) {
        var field = new ProtobufGroupableFieldTree();
        ProtobufSyntaxException.check(modifier.type() == ProtobufFieldTree.Modifier.Type.NOTHING || modifier.type() == ProtobufFieldTree.Modifier.Type.OPTIONAL,
                "Unexpected token " + token, tokenizer.lineno());
        field.setModifier(modifier);
        if(modifier.type() == ProtobufFieldTree.Modifier.Type.NOTHING) {
            field.setType(ProtobufTypeReference.of(token));
        }

        oneOfTree.addStatement(field);
    }

    private void handleEnumConstantDeclaration(String token, ProtobufEnumTree enumTree) {
        var field = new ProtobufEnumConstantTree();
        field.setName(token);
        enumTree.addStatement(field);
    }

    private void handleMessageFieldDeclaration(String token, ProtobufMessageTree messageTree, ProtobufFieldTree.Modifier modifier) {
        var field = new ProtobufGroupableFieldTree();
        switch (document.version().orElse(ProtobufVersion.defaultVersion())){
            case PROTOBUF_2 -> {
                field.setModifier(modifier);
                if(token.equals(MAP_TYPE)) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
            case PROTOBUF_3 -> {
                field.setModifier(modifier);
                if(modifier.type() == ProtobufFieldTree.Modifier.Type.NOTHING) {
                    field.setType(ProtobufTypeReference.of(token));
                }
            }
        }
        messageTree.addStatement(field);
    }

    private void handleGroupFieldDeclaration(String token, ProtobufGroupTree groupTree, ProtobufFieldTree.Modifier modifier) {
        var field = new ProtobufGroupableFieldTree();
        field.setModifier(modifier);
        if(token.equals(MAP_TYPE)) {
            field.setType(ProtobufTypeReference.of(token));
        }
        groupTree.addStatement(field);
    }

    private void handleDocumentOption(String token) {
        switch (nestedInstructions.peekLast()) {
            case DECLARATION -> document.addOption(token);
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token),
                    "Expected assignment operator after option declaration", tokenizer.lineno());
            case BODY_OR_VALUE -> {
                var lastOption = document.lastOption();
                ProtobufSyntaxException.check(lastOption.isPresent(),
                                "Unexpected token " + token, tokenizer.lineno());
                lastOption.get().setRawValue(token);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
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
                var fieldTree = (ProtobufFieldTree) objects.getLast()
                        .lastStatement()
                        .orElseThrow();
                ProtobufSyntaxException.check(!Objects.equals(token, DEFAULT_KEYWORD_OPTION) || document.version().orElse(ProtobufVersion.defaultVersion()) == PROTOBUF_2,
                        "Support for the default values was dropped in proto3", tokenizer.lineno());
                fieldTree.addOption(token);
            }
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token), "Unexpected token " + token, tokenizer.lineno());
            case BODY_OR_VALUE -> {
                var fieldTree = (ProtobufFieldTree) objects.getLast()
                        .lastStatement()
                        .orElseThrow();
                var lastOption = fieldTree.lastOption()
                        .orElseThrow();
                lastOption.setRawValue(token);
            }
            case NESTED_INSTRUCTION_OR_END -> ProtobufSyntaxException.check(token.equals(LIST_SEPARATOR) || isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleDocumentSyntax(String token) {
        ProtobufSyntaxException.check(document.statements().isEmpty(),
                "Unexpected token " + token, tokenizer.lineno());
        switch (nestedInstructions.peekLast()) {
            case INITIALIZER -> ProtobufSyntaxException.check(isAssignmentOperator(token),
                    "Unexpected token " + token, tokenizer.lineno());
            case BODY_OR_VALUE -> document.setVersion(ProtobufVersion.of(token)
                    .orElseThrow(() -> new ProtobufSyntaxException("Illegal syntax declaration: %s is not a valid version".formatted(token), tokenizer.lineno())));
            case NESTED_INSTRUCTION_OR_END -> {
                ProtobufSyntaxException.check(document.version().isPresent(),
                        "Unexpected token " + token, tokenizer.lineno());
                ProtobufSyntaxException.check(isStatementEnd(token),
                        "Unexpected token " + token, tokenizer.lineno());
            }
            case null, default -> throw new ProtobufInternalException("Unexpected state");
        }
    }

    private void handleDocumentPackage(String token) {
        if (nestedInstructions.peekLast() != NestedInstruction.BODY_OR_VALUE) {
            ProtobufSyntaxException.check(isStatementEnd(token),
                    "Unexpected token " + token, tokenizer.lineno());
            return;
        }

        ProtobufSyntaxException.check(document.packageName().isEmpty(),
                "Duplicate package statement", tokenizer.lineno());
        document.setPackageName(token);
    }

    private void handleInstruction(String token) {
        var instruction = Instruction.of(token, false);
        ProtobufSyntaxException.check(instruction != Instruction.UNKNOWN,
                "Unexpected token " + token, tokenizer.lineno(), token);
        jumpIntoInstruction(instruction);
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

    private void handleNestedBodyDeclaration(Instruction instruction, String name) {
        var object = switch (instruction) {
            case MESSAGE -> new ProtobufMessageTree(name);
            case ENUM -> new ProtobufEnumTree(name);
            case ONE_OF -> new ProtobufOneofTree(name);
            default -> throw new ProtobufSyntaxException("Illegal state", tokenizer.lineno());
        };
        var scope = objects.peekLast();
        switch (scope) {
            case ProtobufMessageTree message -> message.addStatement(object);
            case ProtobufGroupTree groupTree -> groupTree.addStatement(object);
            case null -> {
                if (!(object instanceof ProtobufDocumentChildTree childTree)) {
                    throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
                }

                document.addStatement(childTree);
            }
            default -> throw new ProtobufSyntaxException("Invalid scope", tokenizer.lineno());
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
