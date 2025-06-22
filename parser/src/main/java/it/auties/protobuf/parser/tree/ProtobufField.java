package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.*;

public sealed class ProtobufField
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithIndex, ProtobufTree.WithOptions,
                   ProtobufOneofChild, ProtobufMessageChild, ProtobufGroupChild
        permits ProtobufEnumConstant, ProtobufGroupField {
    protected final int line;
    protected Modifier modifier;
    protected ProtobufTypeReference type;
    protected String name;
    protected ProtobufExpression index;
    protected final SequencedCollection<ProtobufOption> options;
    protected ProtobufTree.WithBody<?> parent;

    public ProtobufField(int line) {
        this.line = line;
        this.options = new LinkedList<>();
    }

    @Override
    public int line() {
        return line;
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
    public ProtobufExpression index() {
        return index;
    }

    @Override
    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public void setIndex(ProtobufExpression index) {
        if(index != null) {
            if(index.hasParent()) {
                throw new IllegalStateException("Index is already owned by another tree");
            }
            index.setParent(this);
        }
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

    @Override
    public ProtobufTree.WithBody<?> parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public void setParent(ProtobufTree.WithBody<?> parent) {
        this.parent = parent;
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
        // writeOptions(builder);
        builder.append(";");
        return builder.toString();
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
