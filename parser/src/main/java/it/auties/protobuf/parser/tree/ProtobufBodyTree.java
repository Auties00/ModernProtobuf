package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.function.Consumer;

public abstract sealed class ProtobufBodyTree<T extends ProtobufTree> extends ProtobufNestedTree implements ProtobufNamedTree permits ProtobufDocument, ProtobufIndexedBodyTree {
    String name;
    final LinkedList<T> statements;
    ProtobufBodyTree(){
        this.statements = new LinkedList<>();
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public ProtobufBodyTree setName(String name) {
        this.name = name;
        return this;
    }

    public abstract Optional<String> qualifiedName();

    public abstract Optional<String> qualifiedCanonicalName();

    public abstract Optional<String> qualifiedPath();

    public Collection<T> statements() {
        return Collections.unmodifiableCollection(statements);
    }

    public Optional<T> firstStatement() {
        return Optional.ofNullable(statements.peekFirst());
    }

    public Optional<T> lastStatement() {
        return Optional.ofNullable(statements.peekLast());
    }

    public ProtobufBodyTree addStatement(T statement){
        if(statement instanceof ProtobufNestedTree nestedTree) {
            nestedTree.setParent(this);
        }

        statements.add(statement);
        return this;
    }

    public Optional<ProtobufNamedTree> getStatement(String name){
        if (name == null) {
            return Optional.empty();
        }

        return statements.stream()
                .filter(entry -> entry instanceof ProtobufNamedTree)
                .map(entry -> (ProtobufNamedTree) entry)
                .filter(entry -> entry.name().filter(name::equals).isPresent())
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public <V extends ProtobufNamedTree> Optional<V> getStatement(String name, Class<V> clazz){
        return getStatement(name)
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()))
                .map(entry -> (V) entry);
    }

    public <V extends ProtobufNamedTree> Optional<V> getStatementRecursive(String name, Class<V> clazz){
        var child = getStatement(name, clazz);
        return child.isPresent() ? child : statements().stream()
                .filter(entry -> ProtobufBodyTree.class.isAssignableFrom(entry.getClass()))
                .map(entry -> (ProtobufBodyTree<?>) entry)
                .flatMap(entry -> entry.getStatement(name, clazz).stream())
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufNamedTree> Optional<V> getStatementRecursive(Class<V> clazz){
        return statements().stream()
                .map((entry) -> {
                    if(clazz.isAssignableFrom(entry.getClass())){
                        return Optional.of((V) entry);
                    }

                    if(entry instanceof ProtobufBodyTree object){
                        return object.getStatementRecursive(clazz);
                    }

                    return Optional.<V>empty();
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufNamedTree> List<V> getStatementsRecursive(Class<V> clazz){
        return statements().stream()
            .mapMulti((ProtobufTree entry, Consumer<V> consumer) -> {
                if(clazz.isAssignableFrom(entry.getClass())){
                    consumer.accept((V) entry);
                }

                if(entry instanceof ProtobufBodyTree object){
                    object.getStatementsRecursive(clazz)
                        .forEach(consumer);
                }
            })
            .toList();
    }
}
