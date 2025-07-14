package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class ProtobufDocumentTree
        implements ProtobufTree, ProtobufTree.WithBody<ProtobufDocumentChild> {
    private final Path location;
    private final List<ProtobufDocumentChild> children;
    public ProtobufDocumentTree(Path location) {
        this.location = location;
        this.children = new ArrayList<>();
    }

    @Override
    public int line() {
        return 0;
    }

    @Override
    public boolean isAttributed() {
        return children.stream()
                .allMatch(ProtobufTree::isAttributed);
    }

    public Path location() {
        return location;
    }

    @Override
    public ProtobufTree parent() {
        return null;
    }

    @Override
    public boolean hasParent() {
        return false;
    }

    public String qualifiedPath() {
        return packageName()
                .map(packageName -> packageName.replaceAll("\\.", "/") + "/" + location.getFileName().toString())
                .orElse(location.getFileName().toString());
    }

    public Optional<ProtobufVersion> syntax() {
        for(var child : children()){
            if (!(child instanceof ProtobufSyntaxStatement syntax) || !syntax.hasVersion()) {
                continue;
            }

            return ProtobufVersion.of(syntax.version());
        }
        return Optional.empty();
    }

    public Optional<String> packageName() {
        return children()
                .stream()
                .filter(statement -> statement instanceof ProtobufPackageStatement)
                .map(statement -> (ProtobufPackageStatement) statement)
                .map(ProtobufPackageStatement::name)
                .findFirst();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        children().forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });
        return builder.toString();
    }

    @Override
    public SequencedCollection<ProtobufDocumentChild> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(ProtobufDocumentChild statement) {
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

    @Override
    public boolean removeChild(ProtobufDocumentChild statement) {
        var result = children.remove(statement);
        if(result && statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(null);
        }
        return result;
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildByType(children, clazz);
    }

    @Override
    public Optional<? extends WithName> getDirectChildByName(String name) {
        return ProtobufStatementWithBodyImpl.getDirectChildByName(children, name);
    }

    @Override
    public Optional<? extends WithIndex> getDirectChildByIndex(int index) {
        return Optional.empty(); // No direct child with an index exists in a document
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildByNameAndType(children, name, clazz);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(int index, Class<V> clazz) {
        return Optional.empty(); // No direct child with an index exists in a document
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByType(children, clazz);
    }

    @Override
    public <V extends WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz);
    }
}
