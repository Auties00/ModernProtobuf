package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.util.Objects;

public final class ProtobufSyntax
        extends ProtobufStatement
        implements ProtobufDocumentChild {
    private ProtobufVersion version;

    public ProtobufSyntax(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
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
        return "syntax = " + (version == null ? "<missing>" : version.versionCode()) + ";";
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
