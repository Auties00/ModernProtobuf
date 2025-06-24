package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufSyntax
        extends ProtobufMutableStatement
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private ProtobufExpression version;

    public ProtobufSyntax(int line) {
        super(line);
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
