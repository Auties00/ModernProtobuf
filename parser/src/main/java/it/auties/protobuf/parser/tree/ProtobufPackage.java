package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufPackage
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private final int line;
    private String name;
    private ProtobufTree parent;

    public ProtobufPackage(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public ProtobufTree parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public void setParent(ProtobufTree parent) {
        this.parent = parent;
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
        return this == obj || obj instanceof ProtobufPackage that
                && Objects.equals(this.name(), that.name());
    }

    @Override
    public boolean isAttributed() {
        return hasName();
    }
}
