package it.auties.protobuf.parser.tree;

/**
 * Represents an empty statement in the Protocol Buffer AST.
 * <p>
 * Empty statements consist of a standalone semicolon (;) with no content. They are syntactically
 * valid but have no semantic meaning. Empty statements can appear in any context that accepts
 * statements.
 * </p>
 * <h2>Example:</h2>
 * <pre>{@code
 * message Example {
 *   string field = 1;
 *   ;  // Empty statement
 * }
 * }</pre>
 * <p>
 * This class implements all child marker interfaces, allowing empty statements to appear anywhere
 * in the AST. Empty statements are always attributed and have no mutable state.
 * </p>
 */
public final class ProtobufEmptyStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufOneofChild, ProtobufMethodChild, ProtobufServiceChild, ProtobufExtendChild {
    /**
     * Constructs a new empty statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufEmptyStatement(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return ";";
    }
}