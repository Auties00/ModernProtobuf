package it.auties.protobuf.parser;

import it.auties.protobuf.parser.model.FieldModifier;
import it.auties.protobuf.parser.model.ProtobufSyntaxException;
import it.auties.protobuf.parser.model.ProtobufVersion;
import it.auties.protobuf.parser.object.ProtobufDocument;
import it.auties.protobuf.parser.object.ProtobufObject;
import it.auties.protobuf.parser.statement.*;
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

import static it.auties.protobuf.parser.model.ProtobufVersion.PROTOBUF_2;
import static it.auties.protobuf.parser.model.ProtobufVersion.PROTOBUF_3;
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

    private final ProtobufDocument document;
    private final StreamTokenizer tokenizer;
    private final Deque<ProtobufObject<?>> objectsQueue;
    private final Deque<Instruction> instructions;
    private final Deque<Set<Integer>> knowIndexes;
    private InstructionState instructionState;
    private FieldStatement field;
    private FieldState fieldState;
    private String optionName;
    private String fieldOptionName;

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

        return document;
    }

    private void handleToken(String token) {
        switch (token) {
            case STATEMENT_END -> {
                if (field != null) {
                    addFieldToScope();
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
                var scope = objectsQueue.peekLast();
                if(scope == null){
                    document.getStatements().add(object);
                }else if(scope instanceof MessageStatement messageStatement){
                    messageStatement.getStatements().add(object);
                }else {
                    throw new ProtobufSyntaxException("Incorrect scope", tokenizer.lineno());
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
                }
            }
        }
    }

    private boolean hasAnyConstants(ProtobufObject<?> object) {
        return (object instanceof EnumStatement enumStatement && !enumStatement.getStatements().isEmpty())
                || (object instanceof OneOfStatement oneOfStatement && !oneOfStatement.getStatements().isEmpty())
                || object instanceof MessageStatement;
    }

    private boolean hasDefaultEnumConstant(ProtobufObject<?> object) {
        return document.getVersion() != PROTOBUF_3
                || !(object instanceof EnumStatement enumStatement)
                || enumStatement.getStatements().stream().anyMatch(entry -> entry.index() == 0);
    }

    private void handleBodyState(String token, Instruction instruction) {
        switch (instructionState) {
            case DECLARATION -> createBody(instruction, token);
            case VALUE -> ProtobufSyntaxException.check(isObjectStart(token),
                    "Expected message initializer after message declaration", tokenizer.lineno());
            case OPTIONS -> {
                var nestedInstruction = Instruction.forName(token.toUpperCase(Locale.ROOT));
                if (nestedInstruction == Instruction.UNKNOWN) {
                    createField(token);
                    return;
                }

                createBody(nestedInstruction, nextToken());
                instructions.add(nestedInstruction);
                knowIndexes.add(new HashSet<>());
                this.instructionState = InstructionState.DECLARATION;
            }
            case BODY -> {
                attributeField(token);
                nextFieldState();
            }
        }
    }

    private FieldStatement.Scope createFieldScope(ProtobufStatement scope, FieldModifier modifier) {
        if (modifier != FieldModifier.NOTHING) {
            ProtobufSyntaxException.check(document.getVersion() == PROTOBUF_2 || modifier != FieldModifier.REQUIRED,
                    "Support for the required label was dropped in proto3", tokenizer.lineno());
            ProtobufSyntaxException.check(scope instanceof MessageStatement,
                    "Expected message scope for field declaration", tokenizer.lineno());
            return FieldStatement.Scope.MESSAGE;
        }

        if (document.getVersion() == PROTOBUF_3 && scope instanceof MessageStatement) {
            return FieldStatement.Scope.MESSAGE;
        }

        if (scope instanceof EnumStatement) {
            return FieldStatement.Scope.ENUM;
        }

        if (scope instanceof OneOfStatement) {
            return FieldStatement.Scope.ONE_OF;
        }

        throw new ProtobufSyntaxException("Expected enum or one of scope for field declaration without label",
                tokenizer.lineno());
    }

    private void attributeField(String token) {
        switch (fieldState) {
            case MODIFIER -> field.type(token);
            case TYPE -> {
                ProtobufSyntaxException.check(isLegalName(token), "Illegal field name: %s",
                        tokenizer.lineno(), token);
                field.name(token);
            }
            case NAME ->
                    ProtobufSyntaxException.check(isAssignmentOperator(token),
                            "Expected assignment operator after field type", tokenizer.lineno());
            case INDEX -> {
                var index = parseIndex(token, field.scope() == FieldStatement.Scope.ENUM)
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
                    case "default" -> field.defaultValue(token);
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
        var modifier = FieldModifier.forName(token);
        var scopeType = createFieldScope(scope, modifier);
        this.field = new FieldStatement()
                .modifier(modifier)
                .scope(scopeType);
        this.fieldState = FieldState.MODIFIER;
        if (modifier != FieldModifier.NOTHING) {
            return;
        }

        switch (scopeType) {
            case MESSAGE -> {
                if (document.getVersion() == PROTOBUF_3) {
                    field.type(token);
                    this.fieldState = FieldState.TYPE;
                }
            }
            case ONE_OF -> {
                field.type(token);
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
                document.getOptions().put(optionName, token);
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
            case VALUE -> document.setVersion(ProtobufVersion.forName(token)
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
        document.setPackageName(token);
    }

    private void openInstruction(String token) {
        var instruction = Instruction.forName(token.toUpperCase(Locale.ROOT));
        ProtobufSyntaxException.check(instruction != Instruction.UNKNOWN,
                "Unknown instruction: %s", tokenizer.lineno(), token);
        instructions.add(instruction);
        knowIndexes.add(new HashSet<>());
        this.instructionState = InstructionState.DECLARATION;
    }

    private void addFieldToScope() {
        var scope = objectsQueue.getLast();
        switch (field.scope()) {
            case MESSAGE -> ((MessageStatement) scope).getStatements().add(field);
            case ONE_OF -> ((OneOfStatement) scope).getStatements().add(field);
            case ENUM -> ((EnumStatement) scope).getStatements().add(field);
            default -> throw new ProtobufSyntaxException("Invalid scope for field", tokenizer.lineno());
        }

        this.instructionState = InstructionState.OPTIONS;
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
        var body = switch (instruction) {
            case MESSAGE -> new MessageStatement(name);
            case ENUM -> new EnumStatement(name);
            case ONE_OF -> new OneOfStatement(name);
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

    @AllArgsConstructor
    @Accessors(fluent = true)
    private enum Instruction {
        UNKNOWN(false),
        PACKAGE(false),
        SYNTAX(false),
        OPTION(false),
        MESSAGE(true),
        ENUM(true),
        ONE_OF(true);

        @Getter
        private final boolean hasBody;

        public static Instruction forName(@NonNull String name) {
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
