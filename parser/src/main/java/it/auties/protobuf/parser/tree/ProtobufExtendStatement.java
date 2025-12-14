package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.typeReference.ProtobufTypeReference;

import java.util.Objects;

/**
 * Represents an extend block in the Protocol Buffer AST.
 * <p>
 * Extend blocks allow adding fields to existing message types, including messages defined in
 * other .proto files. This is part of Protocol Buffer's extension mechanism, which enables
 * extending messages without modifying their original definition.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // Extending a message from google.protobuf
 * extend google.protobuf.FileOptions {
 *   optional string my_file_option = 50000;
 * }
 *
 * // Extending a message defined in the same file
 * message Foo {
 *   extensions 100 to 199;
 * }
 *
 * extend Foo {
 *   optional int32 bar = 126;
 *   optional string baz = 127;
 * }
 *
 * // Extending with complex types
 * extend SearchResponse {
 *   optional string debug_info = 1000;
 *   repeated CustomData custom = 1001;
 * }
 * }</pre>
 * <p>
 * Extension requirements:
 * </p>
 * <ul>
 *   <li>The extended message must declare an extension range using {@code extensions}</li>
 *   <li>Extension field numbers must fall within the declared extension ranges</li>
 *   <li>Extension fields must use unique field numbers</li>
 *   <li>Extensions are typically defined in separate .proto files from the extended message</li>
 * </ul>
 * <p>
 * Extend blocks can contain:
 * </p>
 * <ul>
 *   <li><strong>Fields:</strong> Extension field declarations</li>
 *   <li><strong>Options:</strong> Configuration options</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * During semantic analysis, the declaration type reference is resolved to the actual message
 * being extended, and field numbers are validated against the message's extension ranges.
 * </p>
 *
 * @see ProtobufExtensionsStatement
 * @see ProtobufFieldStatement
 * @see ProtobufDocumentChild
 */
public final class ProtobufExtendStatement
        extends ProtobufStatementWithBodyImpl<ProtobufExtendChild>
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufExtendChild>,
                   ProtobufDocumentChild, ProtobufGroupChild, ProtobufMessageChild {
    private ProtobufTypeReference declaration;

    /**
     * Constructs a new extend statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufExtendStatement(int line) {
        super(line);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("extend");
        builder.append(" ");

        var name = Objects.requireNonNullElse(declaration.name(), "[missing]");
        builder.append(name);
        builder.append(" ");

        builder.append("{");
        builder.append("\n");

        if(children.isEmpty()) {
            builder.append("\n");
        } else {
            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });
        }

        builder.append("}");

        return builder.toString();
    }

    /**
     * Returns the type reference of the message being extended.
     *
     * @return the declaration type reference, or null if not yet set
     */
    public ProtobufTypeReference declaration() {
        return declaration;
    }

    /**
     * Checks whether this extend statement has a declaration assigned.
     *
     * @return true if a declaration is present, false otherwise
     */
    public boolean hasDeclaration() {
        return declaration != null;
    }

    /**
     * Sets the type reference for the message being extended.
     * <p>
     * During semantic analysis, this type reference is resolved to the actual message definition.
     * </p>
     *
     * @param declaration the declaration type reference to set
     */
    public void setDeclaration(ProtobufTypeReference declaration) {
        this.declaration = declaration;
    }
}
