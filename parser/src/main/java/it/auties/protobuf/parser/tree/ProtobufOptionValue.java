package it.auties.protobuf.parser.tree;

import java.util.Collections;
import java.util.Map;

public sealed interface ProtobufOptionValue {
    record Literal(String value) implements ProtobufOptionValue {
        @Override
        public String toString() {
            return '"' + value + '"';
        }
    }

    record Int(int value) implements ProtobufOptionValue {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Bool(boolean value) implements ProtobufOptionValue {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Enum(String value) implements ProtobufOptionValue {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record Object(Map<String, ProtobufOptionValue> values) implements ProtobufOptionValue {
        @Override
        public Map<String, ProtobufOptionValue> values() {
            return Collections.unmodifiableMap(values);
        }

        // TODO: Stringify
        @Override
        public String toString() {
            return "todo";
        }
    }
}
