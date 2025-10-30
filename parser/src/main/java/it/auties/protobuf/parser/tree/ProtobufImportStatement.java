package it.auties.protobuf.parser.tree;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents an import statement in the Protocol Buffer AST.
 * <p>
 * Import statements allow a .proto file to use definitions from other .proto files. The imported
 * file's types become available in the importing file's namespace.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * import "google/protobuf/timestamp.proto";
 * import public "other.proto";          // Re-export to dependents
 * import weak "optional_dep.proto";     // Weak dependency
 * }</pre>
 * <p>
 * Import modifiers:
 * </p>
 * <ul>
 *   <li><strong>public:</strong> Re-exports the imported file, making its types available to files that import this file</li>
 *   <li><strong>weak:</strong> Marks the import as optional at runtime; the program can still run if the file is missing</li>
 *   <li><strong>none:</strong> Standard import (default)</li>
 * </ul>
 * <p>
 * During semantic analysis, the location is resolved to the actual {@link ProtobufDocumentTree}
 * representing the imported file.
 * </p>
 *
 * @see ProtobufDocumentChild
 * @see ProtobufDocumentTree
 */
public final class ProtobufImportStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private Modifier modifier;
    private String location;
    private ProtobufDocumentTree document;

    /**
     * Constructs a new import statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufImportStatement(int line) {
        super(line);
    }

    /**
     * Returns the import modifier (public, weak, or none).
     *
     * @return the modifier, or null if not yet set
     */
    public Modifier modifier() {
        return modifier;
    }

    /**
     * Checks whether this statement has a modifier assigned.
     *
     * @return true if a modifier is present, false otherwise
     */
    public boolean hasModifier() {
        return modifier != null;
    }

    /**
     * Sets the import modifier for this statement.
     *
     * @param modifier the modifier to set
     */
    public void setModifier(Modifier modifier) {
        this.modifier = modifier;
    }

    /**
     * Returns the file path/location being imported.
     *
     * @return the import location, or null if not yet set
     */
    public String location() {
        return location;
    }

    /**
     * Checks whether this statement has a location assigned.
     *
     * @return true if a location is present, false otherwise
     */
    public boolean hasLocation() {
        return location != null;
    }

    /**
     * Sets the file path/location for this import.
     *
     * @param location the import location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Returns the imported document tree.
     * <p>
     * This is populated during semantic analysis when the import is resolved.
     * </p>
     *
     * @return the imported document, or null if not yet attributed
     */
    public ProtobufDocumentTree document() {
        return document;
    }

    /**
     * Checks whether this import has been attributed with a document.
     *
     * @return true if a document is present, false otherwise
     */
    public boolean hasDocument() {
        return document != null;
    }

    /**
     * Sets the imported document for this statement.
     *
     * @param document the document tree to set
     */
    public void setDocument(ProtobufDocumentTree document) {
        this.document = document;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("import");
        if(modifier != Modifier.NONE) {
            builder.append(" ");
            builder.append(modifier.token());
        }
        builder.append(" \"");
        builder.append(Objects.requireNonNullElse(location, "[missing]"));
        builder.append("\"");
        builder.append(';');
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufImportStatement that
                && Objects.equals(this.location(), that.location());
    }

    @Override
    public boolean isAttributed() {
        return hasDocument();
    }

    /**
     * Enumeration of import statement modifiers.
     */
    public enum Modifier {
        /**
         * No modifier - standard import.
         */
        NONE(""),

        /**
         * Public import - re-exports the imported file's types.
         */
        PUBLIC("public"),

        /**
         * Weak import - marks dependency as optional at runtime.
         */
        WEAK("weak");

        private final String token;

        Modifier(String token) {
            this.token = token;
        }

        /**
         * Returns the keyword token for this modifier.
         *
         * @return the token string
         */
        public String token() {
            return token;
        }

        private static final Map<String, Modifier> VALUES = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Modifier::token, Function.identity()));

        /**
         * Looks up a modifier by its token string.
         *
         * @param name the token string
         * @return optional containing the modifier, or empty if not found
         */
        public static Optional<Modifier> of(String name) {
            return Optional.ofNullable(VALUES.get(name));
        }
    }
}
