package it.auties.protobuf.parser.tree;

import java.util.SequencedCollection;

public sealed interface ProtobufOptionableStatement permits ProtobufExtension.Range, ProtobufField {
    SequencedCollection<ProtobufOption> options();
    void addOption(ProtobufOption option);
    boolean removeOption(ProtobufOption option);
}
