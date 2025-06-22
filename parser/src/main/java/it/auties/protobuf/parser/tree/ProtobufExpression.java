package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufMessageOrEnumType;

public final class ProtobufExpression implements ProtobufTree {
    private final int line;
    private Value value;
    private ProtobufTree parent;

    public ProtobufExpression(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    public Value value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    @Override
    public ProtobufTree parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    void setParent(ProtobufTree parent) {
        this.parent = parent;
    }

    @Override
    public boolean isAttributed() {
        return hasValue();
    }

    @Override
    public String toString() {
        return value == null ? "[missing]" : value.toString();
    }

    public sealed interface Value {
        record Literal(String value) implements Value {
            @Override
            public String toString() {
                return "\"" + value + "\"";
            }
        }

        record Number(int value) implements Value {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record Bool(boolean value) implements Value {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record EnumConstant(ProtobufMessageOrEnumType type, String value) implements Value {
            @Override
            public String toString() {
                return value;
            }
        }
    }
}
