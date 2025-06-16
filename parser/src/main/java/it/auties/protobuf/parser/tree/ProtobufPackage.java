package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufPackage
        extends ProtobufStatement
        implements ProtobufDocumentChild {
    private String name;

    public ProtobufPackage(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
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
