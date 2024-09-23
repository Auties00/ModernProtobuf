package it.auties.protobuf.parser.tree.body;

import it.auties.protobuf.parser.tree.ProtobufNamedTree;
import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufGroupTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufObjectTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofTree;
import it.auties.protobuf.parser.tree.nested.ProtobufNestedTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionedTree;

import java.util.*;
import java.util.function.Consumer;

public sealed class ProtobufBodyTree<T extends ProtobufBodyTree<T, C>, C extends ProtobufTree> extends ProtobufNestedTree implements ProtobufNamedTree, ProtobufOptionedTree<T>
        permits ProtobufDocumentTree, ProtobufObjectTree, ProtobufOneofTree {
    protected String name;
    protected final LinkedList<C> statements;
    protected final LinkedHashMap<String, ProtobufOptionTree> options;
    private ProtobufOptionTree lastOption;
    protected ProtobufBodyTree(int line, String name) {
        super(line);
        this.name = name;
        this.statements = new LinkedList<>();
        this.options = new LinkedHashMap<>();
    }

    @Override
    public Optional<ProtobufOptionTree> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public Collection<ProtobufOptionTree> options() {
        return Collections.unmodifiableCollection(options.values());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T addOption(int line, String value, ProtobufGroupableFieldTree definition) {
        var option = new ProtobufOptionTree(line, value, definition);
        option.setParent(this, 1);
        options.put(value, option);
        this.lastOption = option;
        return (T) this;
    }

    @Override
    public Optional<ProtobufOptionTree> lastOption() {
        return Optional.ofNullable(lastOption);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public ProtobufBodyTree setName(String name) {
        this.name = name;
        return this;
    }

    public Optional<String> qualifiedName() {
        return parent()
                .flatMap(parent -> parent instanceof ProtobufDocumentTree document ? document.packageName() : parent.qualifiedName())
                .map(parentName -> name == null ? parentName : parentName + (parent instanceof ProtobufObjectTree<?, ?> ? "$" : ".") + name)
                .or(this::name);
    }

    public Optional<String> qualifiedCanonicalName() {
        return parent()
                .flatMap(parent -> parent instanceof ProtobufDocumentTree document ? document.packageName() : parent.qualifiedCanonicalName())
                .map(parentName -> name == null ? parentName : parentName + "." + name)
                .or(this::name);
    }

    public Optional<String> qualifiedPath() {
        return parent()
                .flatMap(parent -> parent instanceof ProtobufDocumentTree document ? document.packageNamePath() : parent.qualifiedPath())
                .map(parentName -> name == null ? parentName : parentName + "/" + name)
                .or(this::name);
    }

    public Collection<C> statements() {
        return Collections.unmodifiableCollection(statements);
    }

    public Optional<C> firstStatement() {
        return Optional.ofNullable(statements.peekFirst());
    }

    public Optional<C> lastStatement() {
        return Optional.ofNullable(statements.peekLast());
    }

    public ProtobufBodyTree addStatement(C statement){
        if(statement instanceof ProtobufNestedTree nestedTree) {
            nestedTree.setParent(this, this instanceof ProtobufGroupTree ? 0 : 1);
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
                .map(entry -> (ProtobufBodyTree<?, ?>) entry)
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

    public List<Integer> indexes() {
        return statements().stream()
                .filter(entry -> entry instanceof ProtobufFieldTree)
                .map(entry -> (ProtobufFieldTree) entry)
                .map(ProtobufFieldTree::index)
                .flatMapToInt(OptionalInt::stream)
                .boxed()
                .toList();
    }

    public boolean hasIndex(int index) {
        return statements()
                .stream()
                .filter(entry -> entry instanceof ProtobufFieldTree)
                .map(entry -> (ProtobufFieldTree) entry)
                .anyMatch(entry -> entry.index().isPresent() && entry.index().getAsInt() == index);
    }

    public boolean hasName(String name) {
        return statements()
                .stream()
                .filter(entry -> entry instanceof ProtobufNamedTree)
                .map(entry -> (ProtobufNamedTree) entry)
                .anyMatch(entry -> entry.name().isPresent() && entry.name().get().equals(name));
    }

    @Override
    public boolean isAttributed() {
        return name != null;
    }
}
