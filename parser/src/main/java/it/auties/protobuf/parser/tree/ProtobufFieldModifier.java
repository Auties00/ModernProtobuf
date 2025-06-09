package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufFieldModifier {
    private static final ProtobufFieldModifier NOTHING = new ProtobufFieldModifier("nothing", Type.NOTHING);
    private static final ProtobufFieldModifier REQUIRED = new ProtobufFieldModifier("required", Type.REQUIRED);
    private static final ProtobufFieldModifier OPTIONAL = new ProtobufFieldModifier("optional", Type.OPTIONAL);
    private static final ProtobufFieldModifier REPEATED = new ProtobufFieldModifier("repeated", Type.REPEATED);

    private final String token;
    private final Type type;

    private ProtobufFieldModifier(String token, Type type) {
        this.token = token;
        this.type = type;
    }

    public static ProtobufFieldModifier of(String name) {
        return switch (name) {
            case "nothing" -> NOTHING;
            case "required" -> REQUIRED;
            case "optional" -> OPTIONAL;
            case "repeated" -> REPEATED;
            default -> new ProtobufFieldModifier(name, Type.NOTHING);
        };
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
