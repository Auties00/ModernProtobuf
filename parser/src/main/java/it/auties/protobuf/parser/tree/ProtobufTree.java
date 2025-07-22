package it.auties.protobuf.parser.tree;

import java.util.Optional;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.stream.Stream;

public sealed interface ProtobufTree
        permits ProtobufDocumentTree, ProtobufExpression, ProtobufStatement, ProtobufTree.WithBody, ProtobufTree.WithBodyAndName, ProtobufTree.WithIndex, ProtobufTree.WithName, ProtobufTree.WithOptions {
    int line();
    boolean isAttributed();
    ProtobufTree parent();
    boolean hasParent();

    sealed interface WithIndex
            extends ProtobufTree
            permits ProtobufFieldStatement {
        Long index();
        boolean hasIndex();
        void setIndex(Long index);
    }

    sealed interface WithName
            extends ProtobufTree
            permits ProtobufEnumStatement, ProtobufFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofFieldStatement, ProtobufServiceStatement, WithBodyAndName {
        String name();
        boolean hasName();
        void setName(String name);

        default String qualifiedName() {
            var name = name();
            if(name == null) {
                return null;
            }

            if (!(parent() instanceof WithName parentWithName)) {
                return name;
            }

            var qualifiedParentName = parentWithName.qualifiedName();
            if (qualifiedParentName == null) {
                return name;
            }

            return qualifiedParentName + "." + name;
        }
    }

    sealed interface WithOptions
            extends ProtobufTree
            permits ProtobufFieldStatement {
        SequencedCollection<ProtobufOptionExpression> options();
        Optional<ProtobufOptionExpression> getOption(String name);
        void addOption(ProtobufOptionExpression value);
        boolean removeOption(String name);
    }

    sealed interface WithBody<T extends ProtobufStatement>
            extends ProtobufTree
            permits ProtobufDocumentTree, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofFieldStatement, ProtobufServiceStatement, ProtobufStatementWithBodyImpl, WithBodyAndName {
        SequencedCollection<T> children();
        void addChild(T statement);
        boolean removeChild(T statement);

        <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz);
        <V extends ProtobufTree> Stream<? extends V> getDirectChildrenByType(Class<V> clazz);
        Optional<? extends WithName> getDirectChildByName(String name);
        Optional<? extends WithIndex> getDirectChildByIndex(long index);
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz);
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(long index, Class<V> clazz);

        <V extends ProtobufTree> Optional<? extends V> getAnyChildByType(Class<V> clazz);
        <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz);
        <V extends ProtobufTree.WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz);
    }

    sealed interface WithBodyAndName<T extends ProtobufStatement>
            extends ProtobufTree, WithName, WithBody<T>
            permits ProtobufEnumStatement, ProtobufMessageStatement, ProtobufOneofFieldStatement, ProtobufServiceStatement {

    }
}
