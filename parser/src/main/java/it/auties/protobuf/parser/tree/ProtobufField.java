package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.SequencedCollection;

public sealed class ProtobufField
        extends ProtobufStatement
        implements ProtobufIndexedTree, ProtobufOptionableStatement,
                    ProtobufOneofChild, ProtobufMessageChild, ProtobufGroupChild
        permits ProtobufEnumConstant, ProtobufGroupField {
    protected Modifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected ProtobufExpression<Integer> index;
    protected final SequencedCollection<ProtobufOption> options;

    protected ProtobufField(int line, ProtobufTreeBody<?> parent) {
        super(line, parent);
        this.index = ProtobufExpression.none();
        this.options = new LinkedList<>();
    }

    public ProtobufField(int line, ProtobufMessage parent) {
        this(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufField(int line, ProtobufOneof parent) {
        this(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufField(int line, ProtobufGroupField parent) {
        this(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public String name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProtobufExpression<Integer> index() {
        return index;
    }

    public boolean hasIndex() {
        return index != null;
    }

    public void setIndex(ProtobufExpression<Integer>  index) {
        Objects.requireNonNull(index, "index cannot be null");
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
    public boolean isAttributed() {
        return hasIndex() && hasName() && hasModifier() && hasType();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        if(modifier != null && modifier.type() != Modifier.Type.NOTHING) {
            builder.append(modifier);
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

    @Override
    public SequencedCollection<ProtobufOption> options() {
        return Collections.unmodifiableSequencedCollection(options);
    }

    @Override
    public void addOption(ProtobufOption option) {
        options.add(option);
    }

    @Override
    public boolean removeOption(ProtobufOption option) {
        return options.remove(option);
    }

    public static final class Modifier {
        private static final Modifier NOTHING = new Modifier("nothing", Type.NOTHING);
        private static final Modifier REQUIRED = new Modifier("required", Type.REQUIRED);
        private static final Modifier OPTIONAL = new Modifier("optional", Type.OPTIONAL);
        private static final Modifier REPEATED = new Modifier("repeated", Type.REPEATED);
    
        private final String token;
        private final Type type;
    
        private Modifier(String token, Type type) {
            this.token = token;
            this.type = type;
        }
    
        public static Modifier of(String name) {
            return switch (name) {
                case "nothing" -> NOTHING;
                case "required" -> REQUIRED;
                case "optional" -> OPTIONAL;
                case "repeated" -> REPEATED;
                default -> new Modifier(name, Type.NOTHING);
            };
        }
    
        public static Modifier required() {
            return REQUIRED;
        }
    
        public static Modifier optional() {
            return OPTIONAL;
        }
    
        public static Modifier repeated() {
            return REPEATED;
        }
    
        public static Modifier nothing() {
            return NOTHING;
        }
    
        public String token() {
            return token;
        }
    
        public Type type() {
            return type;
        }
    
        @Override
        public int hashCode() {
            return Objects.hashCode(token());
        }
    
        @Override
        public String toString() {
            if(type == Type.NOTHING) {
                return "";
            }else {
                return token;
            }
        }
    
        public enum Type {
            NOTHING,
            REQUIRED,
            OPTIONAL,
            REPEATED
        }
    }
}
