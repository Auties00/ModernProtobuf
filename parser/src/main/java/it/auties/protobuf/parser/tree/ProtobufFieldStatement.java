package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufInteger;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public sealed class ProtobufFieldStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithIndex, ProtobufTree.WithOptions,
                   ProtobufOneofChild, ProtobufMessageChild, ProtobufGroupChild, ProtobufExtendChild
        permits ProtobufEnumConstantStatement, ProtobufGroupFieldStatement, ProtobufOneofFieldStatement {
    protected Modifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected ProtobufInteger index;
    protected final SequencedMap<String, ProtobufOptionExpression> options;

    public ProtobufFieldStatement(int line) {
        super(line);
        this.options = new LinkedHashMap<>();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ProtobufInteger index() {
        return index;
    }

    @Override
    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public void setIndex(ProtobufInteger index) {
        this.index = index;
    }

    public ProtobufTypeReference type() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    public Modifier modifier() {
        return modifier;
    }

    public boolean hasModifier() {
        return modifier != null;
    }

    public void setModifier(Modifier modifier) {
        this.modifier = modifier;
    }

    @Override
    public SequencedCollection<ProtobufOptionExpression> options() {
        return options.sequencedValues();
    }

    @Override
    public void addOption(ProtobufOptionExpression value) {
        Objects.requireNonNull(value, "Cannot add null option");
        options.put(value.name().toString(), value);
    }

    @Override
    public boolean removeOption(String name) {
        return options.remove(name) != null;
    }

    @Override
    public Optional<ProtobufOptionExpression> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public boolean isAttributed() {
        return hasIndex() && hasName() && hasModifier() && hasType();
    }

    void writeOptions(StringBuilder builder) {
        var options = this.options.sequencedEntrySet();
        if (options.isEmpty()) {
            return;
        }

        builder.append(" ");
        builder.append("[");
        var optionsToString = options.stream()
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", "));
        builder.append(optionsToString);
        builder.append("]");
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        if(modifier != null && modifier != Modifier.NONE) {
            builder.append(modifier.token());
            builder.append(" ");
        }
        var type = Objects.requireNonNullElse(this.type.toString(), "[missing]");
        builder.append(type);
        builder.append(" ");
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");
        var index = Objects.requireNonNullElse(this.index, "[missing]");
        builder.append("=");
        builder.append(" ");
        builder.append(index);
        writeOptions(builder);
        builder.append(";");
        return builder.toString();
    }

    public enum Modifier {
        NONE(""),
        REQUIRED("required"),
        OPTIONAL("optional"),
        REPEATED("repeated");

        private final String token;

        Modifier(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        private static final Map<String, Modifier> VALUES = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Modifier::token, Function.identity()));

        public static Optional<Modifier> of(String name) {
            return Optional.ofNullable(VALUES.get(name));
        }
    }
}
