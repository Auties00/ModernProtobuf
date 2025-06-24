package it.auties.protobuf.parser.tree;

import java.util.SequencedCollection;

public sealed interface ProtobufTree
        permits ProtobufDocument, ProtobufExpression, ProtobufStatement, ProtobufTree.WithBody, ProtobufTree.WithIndex, ProtobufTree.WithName, ProtobufTree.WithOptions {
    int line();
    boolean isAttributed();
    ProtobufTree parent();
    boolean hasParent();

    sealed interface WithIndex
            extends ProtobufTree
            permits ProtobufField {
        ProtobufExpression index();
        boolean hasIndex();
        void setIndex(ProtobufExpression index);
    }

    sealed interface WithName
            extends ProtobufTree
            permits ProtobufEnum, ProtobufField, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufService {
        String name();
        boolean hasName();
        void setName(String name);
    }

    sealed interface WithOptions
            extends ProtobufTree
            permits ProtobufField {
        SequencedCollection<ProtobufOption> options();
        void addOption(ProtobufOption option);
    }

    sealed interface WithBody<T extends ProtobufStatement>
            extends ProtobufTree
            permits ProtobufDocument, ProtobufEnum, ProtobufExtensionsList, ProtobufGroupField, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufReservedList, ProtobufService {
        ProtobufBody<T> body();
        boolean hasBody();
    }
}
