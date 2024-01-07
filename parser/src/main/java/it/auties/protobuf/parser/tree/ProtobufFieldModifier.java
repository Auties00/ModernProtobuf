package it.auties.protobuf.parser.tree;

import java.util.Arrays;

public enum ProtobufFieldModifier {
    NOTHING(null),
    REQUIRED("required"),
    OPTIONAL("optional"),
    REPEATED("repeated");

    private final String keyword;

    ProtobufFieldModifier(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }

    public static ProtobufFieldModifier of(String name) {
        return name == null ? NOTHING : Arrays.stream(values())
                .filter(entry -> name.equals(entry.keyword))
                .findAny()
                .orElse(NOTHING);
    }

    @Override
    public String toString() {
        return keyword;
    }
}
