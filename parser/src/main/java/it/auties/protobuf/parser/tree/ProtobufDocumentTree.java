package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class ProtobufDocumentTree
        extends ProtobufBlock<ProtobufDocumentChildTree> {
    private final Path location;
    public ProtobufDocumentTree(Path location) {
        super(0, false);
        this.location = location;
    }

    public Path location() {
        return location;
    }

    public Optional<ProtobufVersion> syntax() {
        return children.stream()
                .filter(statement -> statement instanceof ProtobufSyntaxStatement)
                .map(statement -> (ProtobufSyntaxStatement) statement)
                .map(ProtobufSyntaxStatement::version)
                .findFirst();
    }

    public String packageName() {
        return children.stream()
                .filter(statement -> statement instanceof ProtobufPackageStatement)
                .map(statement -> (ProtobufPackageStatement) statement)
                .map(ProtobufPackageStatement::name)
                .findFirst()
                .orElse("");
    }

    @Override
    public Optional<ProtobufNamedTree> getDirectChildByName(String name){
        return super.getDirectChildByName(name)
                .or(() -> getImportedStatement(name));
    }

    private Optional<ProtobufNamedTree> getImportedStatement(String name) {
        return children.stream()
                .filter(entry -> entry instanceof ProtobufImportStatement)
                .map(entry -> (ProtobufImportStatement) entry)
                .map(ProtobufImportStatement::document)
                .filter(Objects::nonNull)
                .map(imported -> imported.getDirectChildByName(name))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public <V extends ProtobufNamedTree> Optional<V> getChild(String name, Class<V> clazz) {
        return super.getDirectChildByNameAndType(name, clazz)
                .or(() -> getImportedStatement(name, clazz));
    }

    private <V extends ProtobufNamedTree> Optional<V> getImportedStatement(String name, Class<V> clazz) {
        return children.stream()
                .filter(entry -> entry instanceof ProtobufImportStatement)
                .map(entry -> (ProtobufImportStatement) entry)
                .map(ProtobufImportStatement::document)
                .filter(Objects::nonNull)
                .map(imported -> imported.getChild(name, clazz))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <V extends ProtobufNamedTree> Optional<V> getAnyChildByNameAndType(String name, Class<V> clazz) {
        return super.getAnyChildByNameAndType(name, clazz)
                .or(() -> getImportedStatementRecursive(name, clazz));
    }

    private <V extends ProtobufNamedTree> Optional<V> getImportedStatementRecursive(String name, Class<V> clazz) {
        return children.stream()
                .filter(entry -> entry instanceof ProtobufImportStatement)
                .map(entry -> (ProtobufImportStatement) entry)
                .map(ProtobufImportStatement::document)
                .filter(Objects::nonNull)
                .map(imported -> imported.getAnyChildByNameAndType(name, clazz))
                .flatMap(Optional::stream)
                .findFirst();
    }


    @Override
    public <V extends ProtobufTree> Optional<V> getAnyChildByType(Class<V> clazz) {
        return super.getAnyChildByType(clazz).or(() -> {
            for(var child : children) {
                if(!(child instanceof ProtobufImportStatement importStatement)) {
                    continue;
                }

                var imported = importStatement.document();
                if(imported == null) {
                    continue;
                }

                var importedChild = imported.getAnyChildByType(clazz);
                if(importedChild.isPresent()) {
                    return importedChild;
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public <V extends ProtobufTree> List<V> getAnyChildrenByType(Class<V> clazz) {
        return children.stream()
                .mapMulti((ProtobufTree entry, Consumer<V> consumer) -> consumeChildren(entry, clazz, consumer))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <V extends ProtobufTree> void consumeChildren(ProtobufTree child, Class<V> expectedType, Consumer<V> consumer) {
        if(expectedType.isAssignableFrom(child.getClass())){
            consumer.accept((V) child);
        }else if(child instanceof ProtobufBlock<?> objectChild){
            objectChild.consumeChildren(objectChild, expectedType, consumer);
        }else if(child instanceof ProtobufImportStatement importStatement){
            var document = importStatement.document();
            if(document != null) {
                document.consumeChildren(document, expectedType, consumer);
            }
        }
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        children.forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });
        return builder.toString();
    }
}
