package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.util.Objects;

/**
 * Represents a syntax version declaration statement in the Protocol Buffer AST.
 * <p>
 * The syntax statement specifies which version of the Protocol Buffer language is used in the file.
 * It must be the first non-comment, non-empty line in a .proto file.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * syntax = "proto2";
 * syntax = "proto3";
 * }</pre>
 * <p>
 * If no syntax statement is present, proto2 is assumed for backward compatibility. The syntax
 * version affects language semantics including field labels, default values, and feature availability.
 * </p>
 *
 * @see ProtobufVersion
 * @see ProtobufDocumentChild
 */
public final class ProtobufSyntaxStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private ProtobufVersion version;

    /**
     * Constructs a new syntax statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufSyntaxStatement(int line) {
        super(line);
    }

    /**
     * Returns the Protocol Buffer version specified by this statement.
     *
     * @return the version, or null if not yet set
     */
    public ProtobufVersion version() {
        return version;
    }

    /**
     * Checks whether this statement has a version assigned.
     *
     * @return true if a version is present, false otherwise
     */
    public boolean hasVersion() {
        return version != null;
    }

    /**
     * Sets the Protocol Buffer version for this statement.
     *
     * @param version the version to set
     */
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
