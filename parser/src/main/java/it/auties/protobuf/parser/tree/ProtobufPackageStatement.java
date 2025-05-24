package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufPackageStatement
        extends ProtobufStatement
        implements ProtobufDocumentChildTree {
    private final String name;

    public ProtobufPackageStatement(int line, String name) {
        super(line);
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "package " + name + ";";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufPackageStatement that
                && Objects.equals(this.name(), that.name());
    }

    @Override
    public boolean isAttributed() {
        return true;
    }
}
