package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;

/**
 * Represents an RPC method declaration in the Protocol Buffer AST.
 * <p>
 * RPC methods define the interface for remote procedure calls within a service. Each method
 * specifies a request message type, a response message type, and optionally whether either
 * type uses streaming. Methods can also contain options for framework-specific configuration.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * service UserService {
 *   // Unary RPC (single request, single response)
 *   rpc GetUser(GetUserRequest) returns (GetUserResponse);
 *
 *   // Server streaming RPC
 *   rpc ListUsers(ListUsersRequest) returns (stream User);
 *
 *   // Client streaming RPC
 *   rpc CreateUsers(stream CreateUserRequest) returns (CreateUsersResponse);
 *
 *   // Bidirectional streaming RPC
 *   rpc Chat(stream ChatMessage) returns (stream ChatMessage);
 *
 *   // Method with options
 *   rpc Search(SearchRequest) returns (SearchResponse) {
 *     option (google.api.http) = {
 *       post: "/v1/search"
 *       body: "*"
 *     };
 *     option deprecated = true;
 *   }
 * }
 * }</pre>
 * <p>
 * RPC method types:
 * </p>
 * <ul>
 *   <li><strong>Unary:</strong> Single request, single response (no streaming)</li>
 *   <li><strong>Server streaming:</strong> Single request, stream of responses</li>
 *   <li><strong>Client streaming:</strong> Stream of requests, single response</li>
 *   <li><strong>Bidirectional streaming:</strong> Stream of requests, stream of responses</li>
 * </ul>
 * <p>
 * Methods can contain child elements:
 * </p>
 * <ul>
 *   <li><strong>Options:</strong> Configuration options for the method</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * This class implements {@link ProtobufServiceChild}, meaning methods can only appear within
 * service declarations. During semantic analysis, the input and output type references are
 * resolved to their message definitions.
 * </p>
 *
 * @see ProtobufServiceChild
 * @see ProtobufServiceStatement
 * @see ProtobufMethodChild
 */
public final class ProtobufMethodStatement
        extends ProtobufStatementWithBodyImpl<ProtobufMethodChild>
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufMethodChild>,
                   ProtobufServiceChild, ProtobufTree.WithName {
    private String name;
    private Type inputType;
    private Type outputType;

    /**
     * Constructs a new method statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufMethodStatement(int line) {
        super(line);
    }

    /**
     * Returns the method name.
     *
     * @return the method name, or null if not yet set
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Checks whether this method has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    @Override
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name for this method.
     * <p>
     * The name must be a valid Protocol Buffer identifier and unique within the service.
     * </p>
     *
     * @param name the method name to set
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the input (request) type for this method.
     *
     * @return the input type, or null if not yet set
     */
    public Type inputType() {
        return inputType;
    }

    /**
     * Checks whether this method has an input type assigned.
     *
     * @return true if an input type is present, false otherwise
     */
    public boolean hasInputType() {
        return inputType != null;
    }

    /**
     * Sets the input (request) type for this method.
     *
     * @param inputType the input type to set
     */
    public void setInputType(Type inputType) {
        this.inputType = inputType;
    }

    /**
     * Returns the output (response) type for this method.
     *
     * @return the output type, or null if not yet set
     */
    public Type outputType() {
        return outputType;
    }

    /**
     * Checks whether this method has an output type assigned.
     *
     * @return true if an output type is present, false otherwise
     */
    public boolean hasOutputType() {
        return outputType != null;
    }

    /**
     * Sets the output (response) type for this method.
     *
     * @param outputType the output type to set
     */
    public void setOutputType(Type outputType) {
        this.outputType = outputType;
    }

    @Override
    public boolean isAttributed() {
        return name != null && inputType != null && outputType != null;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("rpc ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        var inputType = Objects.requireNonNullElse(this.inputType, "[missing]");
        builder.append("(");
        builder.append(inputType);
        builder.append(") ");

        builder.append("returns ");

        var outputType = Objects.requireNonNullElse(this.outputType, "[missing]");
        builder.append("(");
        builder.append(outputType);
        builder.append(")");

        if (children.isEmpty()) {
            builder.append(";");
        } else {
            builder.append(" {");
            builder.append("\n");

            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });

            builder.append("};");
        }

        return builder.toString();
    }

    /**
     * Represents a method parameter or return type with optional streaming.
     * <p>
     * The type reference specifies the message type, and the stream flag indicates
     * whether this is a stream of messages or a single message.
     * </p>
     *
     * @param value  the message type reference
     * @param stream true if this is a stream type, false for a single message
     */
    public record Type(ProtobufTypeReference value, boolean stream) {

    }
}