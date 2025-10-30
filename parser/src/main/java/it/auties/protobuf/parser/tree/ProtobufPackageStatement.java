package it.auties.protobuf.parser.tree;

import java.util.Objects;

/**
 * Represents a package declaration statement in the Protocol Buffer AST.
 * <p>
 * The package statement specifies the package name for all types defined in the file,
 * helping to prevent name collisions and organize Protocol Buffer definitions. The package
 * name affects the fully qualified names of all types in the file.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * package com.example.proto;
 * package google.protobuf;
 * }</pre>
 * <p>
 * Package names use dot notation and typically follow reverse domain name conventions.
 * A .proto file can have at most one package statement.
 * </p>
 *
 * @see ProtobufDocumentChild
 */
public final class ProtobufPackageStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private String name;

    /**
     * Constructs a new package statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufPackageStatement(int line) {
        super(line);
    }

    /**
     * Returns the package name.
     *
     * @return the package name, or null if not yet set
     */
    public String name() {
        return name;
    }

    /**
     * Checks whether this statement has a package name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the package name for this statement.
     *
     * @param name the package name to set
     */
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
        return this == obj || obj instanceof ProtobufPackageStatement that
                && Objects.equals(this.name(), that.name());
    }

    @Override
    public boolean isAttributed() {
        return hasName();
    }
}
