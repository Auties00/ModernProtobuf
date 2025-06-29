package it.auties.protobuf.parser.tree;

import java.util.SequencedCollection;

public sealed interface ProtobufTree
        permits ProtobufDocumentTree, ProtobufStatement, ProtobufExpression,
                ProtobufTree.WithBody, ProtobufTree.WithIndex, ProtobufTree.WithName, ProtobufTree.WithOptions {
    int line();
    boolean isAttributed();
    ProtobufTree parent();
    boolean hasParent();

    sealed interface WithIndex
            extends ProtobufTree
            permits ProtobufFieldStatement {
        ProtobufIntegerExpression index();
        boolean hasIndex();
        void setIndex(ProtobufIntegerExpression index);
    }

    sealed interface WithName
            extends ProtobufTree
            permits ProtobufEnumStatement, ProtobufFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofStatement, ProtobufServiceStatement {
        String name();
        boolean hasName();
        void setName(String name);
    }

    sealed interface WithOptions
            extends ProtobufTree
            permits ProtobufFieldStatement {
        SequencedCollection<ProtobufExpression> options();
        void addOption(String name, ProtobufExpression value);
        boolean removeOption(String name);
    }

    sealed interface WithBody<T extends ProtobufStatement>
            extends ProtobufTree
            permits ProtobufDocumentTree, ProtobufEnumStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofStatement, ProtobufServiceStatement {
        ProtobufBody<T> body();
        boolean hasBody();
    }
}
