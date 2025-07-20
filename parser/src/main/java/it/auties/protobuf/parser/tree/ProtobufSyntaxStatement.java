package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.util.Objects;

public final class ProtobufSyntaxStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private ProtobufVersion version;

    public ProtobufSyntaxStatement(int line) {
        super(line);
    }

    public ProtobufVersion version() {
        return version;
    }

    public boolean hasVersion() {
        return version != null;
    }

    public void setVersion(ProtobufVersion version) {
        this.version = version;
    }

    @Override
    public String toString() {
        var version = Objects.requireNonNullElse(this.version, "[missing]");
        return "syntax = \"" + version + "\";";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(version);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufSyntaxStatement that
                              && Objects.equals(this.version(), that.version());
    }

    @Override
    public boolean isAttributed() {
        return hasVersion();
    }
}
