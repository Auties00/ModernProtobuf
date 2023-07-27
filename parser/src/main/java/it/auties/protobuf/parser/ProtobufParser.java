package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
import it.auties.protobuf.parser.statement.*;
import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_2;
import static it.auties.protobuf.model.ProtobufVersion.PROTOBUF_3;
import static java.util.Objects.requireNonNullElse;

public final class ProtobufParser {
    private static final String STATEMENT_END = ";";
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String COMMA = ",";
    private static final System.Logger LOGGER = System.getLogger("ModernProtobuf");
    private static final String TO_CONTEXTUAL_KEYWORD = "to";
    private static final String TYPE_SELECTOR_KEYWORD = ".";
    private static final String TYPE_SELECTOR_KEYWORD_SPLITTER = "\\.";

    private final ProtobufDocument document;
    private final StreamTokenizer tokenizer;
    private final Deque<ProtobufObject<?>> objectsQueue;
    private final Deque<Instruction> instructions;
    private final Deque<Set<Integer>> knowIndexes;
    private InstructionState instructionState;
    private ProtobufFieldStatement field;
    private FieldState fieldState;
    private String optionName;
    private String fieldOptionName;
    private String reservedName;
    private Integer reservedIndex;
    private boolean reservedIndexRange;

    public ProtobufParser(@NonNull File file) throws IOException {
        this(file.toPath());
    }

    public ProtobufParser(@NonNull Path input) throws IOException {
        this(Files.readString(input));
    }

    public ProtobufParser(@NonNull String input) {
        this.document = new ProtobufDocument();
        this.tokenizer = new StreamTokenizer(new StringReader(input));
        this.objectsQueue = new LinkedList<>();
        this.instructions = new LinkedList<>();
        this.instructionState = InstructionState.DECLARATION;
        this.knowIndexes = new LinkedList<>();
        tokenizer.wordChars('_', '_');
        tokenizer.wordChars('"', '"');
        tokenizer.wordChars('\'', '\'');
    }

    public ProtobufDocument tokenizeAndParse() {
        String token;
        while ((token = nextToken()) != null) {
            handleToken(token);
        }

        attribute(document);

        return document;
    }

    private void attribute(ProtobufStatement statement) {
        if(statement instanceof ProtobufDocument documentStatement) {
            documentStatement.statements()
                    .forEach(this::attribute);
            return;
        }

        if(statement instanceof ProtobufEnumStatement){
            return;
        }

        if(statement instanceof ProtobufMessageStatement messageStatement){
            messageStatement.statements()
                    .forEach(this::attribute);
            return;
        }

        if(statement instanceof ProtobufOneOfStatement oneOfStatement){
            oneOfStatement.statements()
                    .forEach(this::attribute);
            return;
        }

        if(statement instanceof ProtobufFieldStatement fieldStatement){
            if(!(fieldStatement.type() instanceof ProtobufObjectType messageType)){
                return;
            }

            if(messageType.attributed()){
                return;
            }

            var type = getReferencedType(fieldStatement, messageType);
            if(type == null){
                throw new ProtobufTypeException("Cannot resolve protobufType in field %s inside %s",
                        fieldStatement, fieldStatement.parent().name());
            }

            messageType.attribute(type);
            return;
        }

        throw new ProtobufTypeException("Cannot check protobufType of %s", statement.name());
    }

    private ProtobufObject<?> getReferencedType(ProtobufFieldStatement fieldStatement, ProtobufObjectType messageType) {
        if (!messageType.name().contains(TYPE_SELECTOR_KEYWORD)) {
            return getReferencedType(fieldStatement, messageType.name());
        }

        var types = messageType.name().split(TYPE_SELECTOR_KEYWORD_SPLITTER);
        if(types.length == 0){
            throw new ProtobufTypeException("Cannot resolve protobufType in field %s inside %s",
                    fieldStatement, fieldStatement.parent().name());
        }

        var type = getReferencedType(fieldStatement, types[0]);
        for(var index = 1; index < types.length; index++){
            type = (ProtobufObject<?>) type.getStatement(types[index])
                    .orElseThrow(() -> new ProtobufTypeException("Cannot resolve protobufType in field %s inside %s", fieldStatement, fieldStatement.parent().name()));
        }

        return type;
    }

    // This can be optimized with a map
    // TODO: Look into it
    private ProtobufObject<?> getReferencedType(ProtobufFieldStatement fieldStatement, String accessed) {
        ProtobufObject<?> parent = fieldStatement.parent();
        ProtobufObject<?> innerType = null;
        while (parent != null && innerType == null){
            innerType = parent.getStatement(accessed, ProtobufObject.class)
                    .orElse(null);
            parent = parent.parent();
        }

        return innerType;
    }

    private void handleToken(String token) {
        switch (token) {
            case STATEMENT_END -> {
                if(reservedName != null || reservedIndex != null){
                    var reservable = (ProtobufReservable<?>) objectsQueue.getLast();
                    if(reservedIndex != null) {
                        ProtobufSyntaxException.check(reservable.reservedIndexes().add(reservedIndex),
                                "Duplicated reserved field index(%s)", tokenizer.lineno(), reservedIndex);
                    }

                    if(reservedName != null){
                        ProtobufSyntaxException.check(reservable.reservedNames().add(reservedName),
                                "Duplicated reserved field name(%s)", tokenizer.lineno(), reservedName);
                    }

                    this.reservedIndex = null;
                    this.reservedName = null;
                    this.reservedIndexRange = false;
                    this.instructionState = InstructionState.OPTIONS;
                    return;
                }

                if (field != null) {
                    addFieldToScope();
                    return;
                }

                var instruction = instructions.getLast();
                if (instruction.hasBody()) {
                    return;
                }

                instructions.removeLast();
                knowIndexes.removeLast();
                this.instructionState = InstructionState.DECLARATION;
            }

            case OBJECT_END -> {
                var object = objectsQueue.pollLast();
                ProtobufSyntaxException.check(hasAnyConstants(object),
                        "Illegal enum or oneof without any constants", tokenizer.lineno());
                ProtobufSyntaxException.check(hasDefaultEnumConstant(object),
                        "Proto3 enums require a constant with index 0", tokenizer.lineno());
                ProtobufSyntaxException.check(isValidReservable(object),
                        "Illegal use of reserved field", tokenizer.lineno());
                var scope = objectsQueue.peekLast();
                if(scope == null){
                    document.addStatement(object);
                }else if(scope instanceof ProtobufMessageStatement message){
                    message.addStatement(object);
                }else {
                    throw new ProtobufSyntaxException("%s cannot be declared in %s as the latter should be a message", tokenizer.lineno(), object.name(), scope.name());
                }

                instructions.removeLast();
                knowIndexes.removeLast();
                this.instructionState = InstructionState.OPTIONS;
            }

            default -> {
                var instruction = requireNonNullElse(instructions.peekLast(), Instruction.UNKNOWN);
                switch (instruction) {
                    case UNKNOWN -> openInstruction(token);

                    case PACKAGE -> {
                        createPackageOption(token);
                        nextInstructionState();
                    }

                    case SYNTAX -> {
                        handleSyntaxState(token);
                        nextInstructionState();
                    }

                    case OPTION -> {
                        handleOptionState(token);
                        nextInstructionState();
                    }

                    case MESSAGE, ENUM, ONE_OF -> {
                        handleBodyState(token, instruction);
                        nextInstructionState();
                    }

                    case SERVICE -> nextInstructionState();
                }
            }
        }
    }

    private boolean isValidReservable(ProtobufObject<?> object) {
        if(!(object instanceof ProtobufReservable<?> reservable)){
            return true;
        }

        return object.statements()
                .stream()
                .filter(entry -> entry instanceof ProtobufFieldStatement)
                .map(entry -> (ProtobufFieldStatement) entry)
                .noneMatch(entry -> hasReservedFieldIndex(reservable, entry) || hasReservedFieldName(reservable, entry));
    }

    private boolean hasReservedFieldName(ProtobufReservable<?> reservable, ProtobufFieldStatement entry) {
        return reservable.reservedNames()
                .stream()
                .anyMatch(reserved -> Objects.equals(reserved, entry.name()));
    }

    private boolean hasReservedFieldIndex(ProtobufReservable<?> reservable, ProtobufFieldStatement entry) {
        return reservable.reservedIndexes()
                .stream()
                .anyMatch(reserved -> Objects.equals(reserved, entry.index()));
    }

    private boolean hasAnyConstants(ProtobufObject<?> object) {
        return (object instanceof ProtobufEnumStatement enumStatement && !enumStatement.statements().isEmpty())
                || (object instanceof ProtobufOneOfStatement oneOfStatement && !oneOfStatement.statements().isEmpty())
                || object instanceof ProtobufMessageStatement;
    }

    private boolean hasDefaultEnumConstant(ProtobufObject<?> object) {
        return document.version() != PROTOBUF_3
                || !(object instanceof ProtobufEnumStatement enumStatement)
                || enumStatement.statements().stream().anyMatch(entry -> entry.index() == 0);
    }

    private void handleBodyState(String token, Instruction instruction) {
        switch (instructionState) {
            case DECLARATION -> createBody(instruction, token);
            case VALUE -> ProtobufSyntaxException.check(isObjectStart(token),
                    "Expected message initializer after message declaration", tokenizer.lineno());
            case OPTIONS -> {
                var nestedInstruction = Instruction.of(token.toUpperCase(Locale.ROOT));
                switch (nestedInstruction){
                    case UNKNOWN -> createField(token);

                    case RESERVED -> createReserved(token);

                    default -> {
                        createBody(nestedInstruction, nextToken());
                        instructions.add(nestedInstruction);
                        knowIndexes.add(new HashSet<>());
                        this.instructionState = InstructionState.DECLARATION;
                    }
                }
            }
            case BODY -> {
                if (fieldState == null) {
                    createReserved(token);
                    return;
                }

                attributeField(token);
                nextFieldState();
            }
        }
    }

    private void createReserved(String token) {
        if(Instruction.RESERVED.name().toLowerCase(Locale.ROOT).equals(token)){
            return;
        }

        var scope = objectsQueue.peekLast();
        ProtobufSyntaxException.check(scope instanceof ProtobufReservable,
                "Invalid scope for reserved statement", tokenizer.lineno());
        var reservable =  (ProtobufReservable<?>) scope;
        if(token.equals(COMMA)){
            if(reservedIndexRange){
                this.reservedIndexRange = false;
                return;
            }

            if(reservedName != null) {
                ProtobufSyntaxException.check(reservable.reservedNames().add(reservedName),
                        "Duplicated reserved field name(%s)", tokenizer.lineno(), reservedName);
                return;
            }

            if(reservedIndex != null){
                ProtobufSyntaxException.check(reservable.reservedIndexes().add(reservedIndex),
                        "Duplicated reserved field index(%s)", tokenizer.lineno(), reservedIndex);
                return;
            }

            throw new ProtobufSyntaxException("Expected value before comma in reserved statement", tokenizer.lineno());
        }

        if(token.equals(TO_CONTEXTUAL_KEYWORD)) {
            ProtobufSyntaxException.check(reservedName == null,
                    "Illegal to keyword usage with field name in reserved statement", tokenizer.lineno());
            ProtobufSyntaxException.check(reservedIndex != null,
                    "Expected value before to keyword in reserved statement", tokenizer.lineno());
            this.reservedIndexRange = true;
            return;
        }

        if(isStringLiteral(token)){
            ProtobufSyntaxException.check(reservedIndex == null,
                "Illegal mixture of field names and indexes in reserved statement", tokenizer.lineno());
            this.reservedName = token.substring(1, token.length() - 1);
            return;
        }

        if(reservedIndexRange){
            var end = parseIndex(token, true)
                    .orElseThrow(() -> new ProtobufSyntaxException("Expected int or string in reserved statement", tokenizer.lineno()));
            IntStream.rangeClosed(reservedIndex, end)
                    .forEach(reservable.reservedIndexes()::add);
            return;
        }

        ProtobufSyntaxException.check(reservedName == null,
                "Illegal mixture of field names and indexes in reserved statement", tokenizer.lineno());
        this.reservedIndex = parseIndex(token, true)
                .orElseThrow(() -> new ProtobufSyntaxException("Expected int or string in reserved statement instead of %s",
                        tokenizer.lineno(), token));
    }

    private boolean isStringLiteral(String token) {
        return (token.startsWith("\"") && token.endsWith("\""))
               || (token.startsWith("'") && token.endsWith("'"));
    }

    private ProtobufObject<?> checkFieldParent(ProtobufObject<?> scope, ProtobufFieldStatement.Modifier modifier) {
        if (modifier != ProtobufFieldStatement.Modifier.NOTHING) {
            ProtobufSyntaxException.check(document.version() == PROTOBUF_2 || modifier != ProtobufFieldStatement.Modifier.REQUIRED,
                    "Support for the required label was dropped in proto3", tokenizer.lineno());
            ProtobufSyntaxException.check(scope instanceof ProtobufMessageStatement,
                    "Expected message scope for field declaration", tokenizer.lineno());
            return scope;
        }

        if (document.version() == PROTOBUF_3 && scope instanceof ProtobufMessageStatement) {
            return scope;
        }

        if (scope instanceof ProtobufEnumStatement) {
            return scope;
        }

        if (scope instanceof ProtobufOneOfStatement) {
            return scope;
        }

        throw new ProtobufSyntaxException("Expected enum or one of scope for field declaration without label",
                tokenizer.lineno());
    }

    private void attributeField(String token) {
        switch (fieldState) {
            case MODIFIER -> field.type(ProtobufTypeReference.of(token));
            case TYPE -> {
                ProtobufSyntaxException.check(isLegalName(token), "Illegal field name: %s",
                        tokenizer.lineno(), token);
                field.name(token);
            }
            case NAME ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Expected assignment operator after field protobufType", tokenizer.lineno());
            case INDEX -> {
                var index = parseIndex(token, field.parent().statementType() == ProtobufStatementType.ENUM)
                        .orElseThrow(() -> new ProtobufSyntaxException("Missing or illegal index: %s".formatted(token), tokenizer.lineno()));
                ProtobufSyntaxException.check(!knowIndexes.getLast().contains(index),
                        "Duplicated index %s", tokenizer.lineno(), index);
                knowIndexes.getLast().add(index);
                field.index(index);
            }
            case OPTIONS_START ->
                    ProtobufSyntaxException.check(isArrayStart(token),
                            "Expected array start operator to initialize field options", tokenizer.lineno());
            case OPTIONS_NAME -> this.fieldOptionName = token;
            case OPTION_ASSIGNMENT ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Expected assignment operator after field option", tokenizer.lineno());
            case OPTIONS_VALUE -> {
                switch (fieldOptionName) {
                    case "packed" -> field.packed(Boolean.parseBoolean(token));
                    case "deprecated" -> field.deprecated(Boolean.parseBoolean(token));
                    case "default" -> {
                        ProtobufSyntaxException.check(document.version() != PROTOBUF_3,
                                "Support for default values was dropped in proto3", tokenizer.lineno());
                        field.defaultValue(token);
                    }
                    default ->
                            LOGGER.log(System.Logger.Level.WARNING, "Unrecognized field option: %s=%s%n".formatted(fieldOptionName, token));
                }
            }
            case OPTIONS_END -> {
                switch (token) {
                    case ARRAY_END -> {}
                    case COMMA -> this.fieldState = FieldState.OPTIONS_START;
                    default ->
                            throw new ProtobufSyntaxException("Expected comma or array end after field options declaration",
                                    tokenizer.lineno());
                }
            }
        }
    }

    private void createField(String token) {
        var scope = objectsQueue.peekLast();
        var modifier = ProtobufFieldStatement.Modifier.of(token);
        var parent = checkFieldParent(scope, modifier);
        this.field = new ProtobufFieldStatement(document.packageName(), parent);
        field.modifier(modifier);
        this.fieldState = FieldState.MODIFIER;
        if (modifier != ProtobufFieldStatement.Modifier.NOTHING) {
            return;
        }

        switch (parent.statementType()) {
            case MESSAGE -> {
                if (document.version() == PROTOBUF_3) {
                    field.type(ProtobufTypeReference.of(token));
                    this.fieldState = FieldState.TYPE;
                }
            }
            case ONE_OF -> {
                field.type(ProtobufTypeReference.of(token));
                this.fieldState = FieldState.TYPE;
            }
            case ENUM -> {

                field.name(token);
                this.fieldState = FieldState.NAME;
            }
        }
    }

    private void handleOptionState(String token) {
        switch (instructionState) {
            case DECLARATION -> this.optionName = token;
            case VALUE -> ProtobufSyntaxException.check(isAssignmentOperator(token),
                    "Expected assignment operator after option declaration", tokenizer.lineno());
            case OPTIONS -> {
                document.options().put(optionName, token);
                this.optionName = null;
            }
            case BODY ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Unsupported options for syntax declaration", tokenizer.lineno());
        }
    }

    private void handleSyntaxState(String token) {
        switch (instructionState) {
            case DECLARATION ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Expected assignment operator after syntax declaration", tokenizer.lineno());
            case VALUE -> document.version(ProtobufVersion.of(token)
                    .orElseThrow(() -> new ProtobufSyntaxException("Illegal syntax declaration: %s is not a valid version".formatted(token),
                            tokenizer.lineno())));
            case OPTIONS, BODY ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Unsupported options for syntax declaration", tokenizer.lineno());
        }
    }

    private void createPackageOption(String token) {
        ProtobufSyntaxException.check(instructionState == InstructionState.DECLARATION,
                "Illegal options specified for package declaration", tokenizer.lineno());
        document.packageName(token);
    }

    private void openInstruction(String token) {
        var instruction = Instruction.of(token.toUpperCase(Locale.ROOT));
        if(instruction == Instruction.SERVICE){
            LOGGER.log(System.Logger.Level.INFO, "Service will not be parsed at line %s".formatted(tokenizer.lineno()));
        }

        ProtobufSyntaxException.check(instruction != Instruction.UNKNOWN,
                "Unknown instruction: %s", tokenizer.lineno(), token);
        instructions.add(instruction);
        knowIndexes.add(new HashSet<>());
        this.instructionState = InstructionState.DECLARATION;
    }

    @SuppressWarnings("unchecked") // safe
    private void addFieldToScope() {
        var scope = objectsQueue.getLast();
        if(field.parent().statementType() == ProtobufStatementType.DOCUMENT){
            throw new ProtobufSyntaxException("Field %s cannot be declared outside of a scope", tokenizer.lineno(), field.name());
        }

        var safeScope = (ProtobufObject<ProtobufFieldStatement>) scope;
        safeScope.addStatement(field);
        this.instructionState = InstructionState.OPTIONS;
        this.field = null;
        this.fieldState = null;
    }

    private String nextToken() {
        try {
            var token = tokenizer.nextToken();
            if (token == StreamTokenizer.TT_EOF) {
                return null;
            }

            return switch (token) {
                case StreamTokenizer.TT_WORD -> tokenizer.sval;
                case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
                default -> String.valueOf((char) token);
            };
        } catch (IOException exception) {
            return null;
        }
    }

    private void createBody(Instruction instruction, String name) {
        var packageName = document.packageName();
        var parent = Objects.requireNonNullElse(objectsQueue.peekLast(), document);
        var body = switch (instruction) {
            case MESSAGE -> new ProtobufMessageStatement(name, packageName, parent);
            case ENUM -> new ProtobufEnumStatement(name, packageName, parent);
            case ONE_OF -> new ProtobufOneOfStatement(name, packageName, parent);
            default -> throw new ProtobufSyntaxException("Illegal state", tokenizer.lineno());
        };

        objectsQueue.add(body);
    }

    private void nextInstructionState() {
        this.instructionState = instructionState.next();
    }

    private void nextFieldState() {
        this.fieldState = fieldState.next();
    }

    private boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private boolean isObjectStart(String operator) {
        return Objects.equals(operator, OBJECT_START);
    }

    private boolean isArrayStart(String operator) {
        return Objects.equals(operator, ARRAY_START);
    }

    private boolean isLegalName(String instruction) {
        return !instruction.isBlank()
                && !instruction.isEmpty()
                && !Character.isDigit(instruction.charAt(0));
    }

    private Optional<Integer> parseIndex(String parse, boolean acceptZero) {
        try {
            return Optional.of(Integer.parseUnsignedInt(parse))
                    .filter(value -> acceptZero || value != 0);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @Getter
    @AllArgsConstructor
    @Accessors(fluent = true)
    private enum Instruction {
        UNKNOWN(false),
        PACKAGE(false),
        SYNTAX(false),
        RESERVED(false),
        OPTION(false),
        MESSAGE(true),
        ENUM(true),
        ONE_OF(true),
        SERVICE(true);

        private final boolean hasBody;

        public static Instruction of(@NonNull String name) {
            return Arrays.stream(values())
                    .filter(entry -> entry.name().replaceAll("_", "").equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }

    private enum InstructionState {
        DECLARATION,
        VALUE,
        OPTIONS,
        BODY;

        public InstructionState next() {
            return ordinal() + 1 >= values().length ? BODY : values()[ordinal() + 1];
        }
    }

    private enum FieldState {
        MODIFIER,
        TYPE,
        NAME,
        INDEX,
        OPTIONS_START,
        OPTIONS_NAME,
        OPTION_ASSIGNMENT,
        OPTIONS_VALUE,
        OPTIONS_END;

        public FieldState next() {
            return ordinal() + 1 >= values().length ? OPTIONS_END : values()[ordinal() + 1];
        }
    }
}
