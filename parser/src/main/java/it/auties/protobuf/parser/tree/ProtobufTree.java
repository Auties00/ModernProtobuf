package it.auties.protobuf.parser.tree;

import java.util.Optional;
import java.util.SequencedCollection;
import java.util.stream.Stream;

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
        Integer index();
        boolean hasIndex();
        void setIndex(Integer index);
    }

    sealed interface WithName
            extends ProtobufTree
            permits ProtobufEnumStatement, ProtobufFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofFieldStatement, ProtobufServiceStatement {
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
            permits ProtobufDocumentTree, ProtobufEnumStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofFieldStatement, ProtobufServiceStatement, ProtobufStatementWithBodyImpl {
        SequencedCollection<T> children();
        void addChild(T statement);
        boolean removeChild(T statement);

        <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz);
        Optional<? extends WithName> getDirectChildByName(String name);
        Optional<? extends WithIndex> getDirectChildByIndex(int index);
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz);
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(int index, Class<V> clazz);

        <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz);
        // No getAnyChildrenByIndexAndType
        <V extends ProtobufTree.WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz);
    }
}
