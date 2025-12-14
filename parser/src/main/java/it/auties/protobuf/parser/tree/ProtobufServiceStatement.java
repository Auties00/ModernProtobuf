package it.auties.protobuf.parser.tree;

import java.util.Objects;

/**
 * Represents a service declaration in the Protocol Buffer AST.
 * <p>
 * Services define RPC (Remote Procedure Call) interfaces in Protocol Buffers. A service contains
 * a collection of method declarations that specify the request and response message types. Services
 * are used by RPC frameworks like gRPC to generate client and server code.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * // Simple service with unary RPC
 * service UserService {
 *   rpc GetUser(GetUserRequest) returns (GetUserResponse);
 *   rpc CreateUser(CreateUserRequest) returns (CreateUserResponse);
 * }
 *
 * // Service with streaming RPCs
 * service ChatService {
 *   // Server streaming
 *   rpc StreamMessages(StreamRequest) returns (stream Message);
 *
 *   // Client streaming
 *   rpc UploadFile(stream Chunk) returns (UploadResponse);
 *
 *   // Bidirectional streaming
 *   rpc Chat(stream ChatMessage) returns (stream ChatMessage);
 * }
 *
 * // Service with options
 * service SearchService {
 *   option deprecated = true;
 *   rpc Search(SearchRequest) returns (SearchResponse) {
 *     option (google.api.http) = {
 *       post: "/v1/search"
 *       body: "*"
 *     };
 *   }
 * }
 * }</pre>
 * <p>
 * Services can contain various child elements:
 * </p>
 * <ul>
 *   <li><strong>Methods (RPCs):</strong> RPC method declarations with request/response types</li>
 *   <li><strong>Options:</strong> Configuration options for the service</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * This class only implements {@link ProtobufDocumentChild}, meaning services can only appear at
 * the file level, not nested within other structures.
 * </p>
 * <p>
 * Services are attributed when they have a name and all their child statements (methods and options)
 * are attributed. During semantic analysis, method request and response types are resolved to their
 * message definitions.
 * </p>
 *
 * @see ProtobufServiceChild
 * @see ProtobufMethodStatement
 * @see ProtobufOptionStatement
 */
public final class ProtobufServiceStatement
        extends ProtobufStatementWithBodyImpl<ProtobufServiceChild>
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufServiceChild>,
                   ProtobufDocumentChild {
    private String name;

    /**
     * Constructs a new service statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufServiceStatement(int line) {
        super(line);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("service");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
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
     * Returns the service name.
     *
     * @return the service name, or null if not yet set
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Checks whether this service has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    @Override
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name for this service.
     * <p>
     * The name must be a valid Protocol Buffer identifier and unique within the file.
     * </p>
     *
     * @param name the service name to set
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }
}
