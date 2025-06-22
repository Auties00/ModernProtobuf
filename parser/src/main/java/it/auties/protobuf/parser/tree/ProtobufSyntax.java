package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufSyntax
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private final int line;
    private ProtobufExpression version;
    private ProtobufTree parent;

    public ProtobufSyntax(int line) {
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

    public ProtobufExpression version() {
        return version;
    }

    public boolean hasVersion() {
        return version != null;
    }

    public void setVersion(ProtobufExpression version) {
        if(version != null) {
            if(version.hasParent()) {
                throw new IllegalStateException("Index is already owned by another tree");
            }
            version.setParent(this);
        }
        this.version = version;
    }

    @Override
    public String toString() {
        var version = Objects.requireNonNullElse(this.version, "[missing]");
        return "syntax = " + version + ";";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufSyntax that
                && Objects.equals(this.version(), that.version());
    }

    @Override
    public boolean isAttributed() {
        return hasVersion();
    }
}
