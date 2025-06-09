package it.auties.protobuf.parser.tree;

public final class ProtobufReservedListStatement
        extends ProtobufBlock<ProtobufReservedStatement<?>>
        implements ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree {
    public ProtobufReservedListStatement(int line) {
        super(line, false);
    }
}
