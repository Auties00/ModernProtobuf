package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExpression {
    static ProtobufExpression none() {
        return None.INSTANCE;
    }

    static ProtobufExpression operator() {
        return Operator.INSTANCE;
    }

    static <T> ProtobufExpression value(T value) {
        return new Value<>(value);
    }

    final class None implements ProtobufExpression {
        private static final None INSTANCE = new None();

        private None() {

        }
    }

    final class Operator implements ProtobufExpression {
        private static final Operator INSTANCE = new Operator();

        private Operator() {

        }
    }

    final class Value<T> implements ProtobufExpression {
        private final T value;

        private Value(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }
    }
}
