package it.auties.protobuf.parser.tree.body.document;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.ProtobufNamedTree;
import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.nested.imports.ProtobufImportTree;
import it.auties.protobuf.parser.tree.nested.option.ProtobufOptionTree;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class ProtobufDocumentTree extends ProtobufBodyTree<ProtobufDocumentTree, ProtobufDocumentChildTree> {
    private final Path location;
    private final LinkedHashMap<String, ProtobufImportTree> imports;
    private String packageName;
    private ProtobufVersion version;
    public ProtobufDocumentTree(Path location) {
        super(0,null);
        this.location = location;
        this.version = null;
        this.imports = new LinkedHashMap<>();
    }

    public Optional<Path> location() {
        return Optional.ofNullable(location);
    }

    public Optional<ProtobufVersion> version() {
        return Optional.ofNullable(version);
    }

    public ProtobufDocumentTree setVersion(ProtobufVersion version) {
        this.version = Objects.requireNonNull(version);
        return this;
    }

    public Optional<String> packageName() {
        return Optional.ofNullable(packageName);
    }

    public Optional<String> packageNamePath() {
        return packageName()
                .map(packageName -> packageName.replaceAll("\\.", "/"));
    }

    public ProtobufDocumentTree setPackageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        if(version != null){
            builder.append("syntax = \"%s\";".formatted(version.versionCode()));
            builder.append("\n");
        }

        if(packageName != null){
            builder.append("package %s;".formatted(packageName));
            builder.append("\n");
        }

        options.values()
                .stream()
                .map(ProtobufOptionTree::toString)
                .forEach(option -> {
                    builder.append(option);
                    builder.append("\n");
                });

        statements().forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });

        return builder.toString();
    }

    @Override
    public ProtobufBodyTree addStatement(ProtobufDocumentChildTree statement) {
        if(statement instanceof ProtobufImportTree importStatement) {
            imports.put(importStatement.location(), importStatement);
            return this;
        }else {
            return super.addStatement(statement);
        }
    }

    @Override
    public Optional<ProtobufNamedTree> getStatement(String name){
        return super.getStatement(name)
                .or(() -> getImportedStatement(name));
    }

    private Optional<ProtobufNamedTree> getImportedStatement(String name) {
        return imports.values()
                .stream()
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .map(imported -> imported.getStatement(name))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <V extends ProtobufNamedTree> Optional<V> getStatement(String name, Class<V> clazz) {
        return super.getStatement(name, clazz)
                .or(() -> getImportedStatement(name, clazz));
    }

    private <V extends ProtobufNamedTree> Optional<V> getImportedStatement(String name, Class<V> clazz) {
        return imports.values()
                .stream()
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .map(imported -> imported.getStatement(name, clazz))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <V extends ProtobufNamedTree> Optional<V> getStatementRecursive(String name, Class<V> clazz) {
        return super.getStatementRecursive(name, clazz)
                .or(() -> getImportedStatementRecursive(name, clazz));
    }

    private <V extends ProtobufNamedTree> Optional<V> getImportedStatementRecursive(String name, Class<V> clazz) {
        return imports.values()
                .stream()
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .map(imported -> imported.getStatementRecursive(name, clazz))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <V extends ProtobufNamedTree> Optional<V> getStatementRecursive(Class<V> clazz) {
        return super.getStatementRecursive(clazz)
                .or(() -> getImportedStatementRecursive(clazz));
    }

    public <V extends ProtobufNamedTree> Optional<V> getImportedStatementRecursive(Class<V> clazz) {
        return imports.values()
                .stream()
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .map(imported -> imported.getStatementRecursive(clazz))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Override
    public <V extends ProtobufNamedTree> List<V> getStatementsRecursive(Class<V> clazz) {
        return Stream.of(super.getStatementsRecursive(clazz), getImportedStatementsRecursive(clazz))
                .flatMap(Collection::stream)
                .toList();
    }

    public <V extends ProtobufNamedTree> List<V> getImportedStatementsRecursive(Class<V> clazz) {
        return imports.values()
                .stream()
                .map(ProtobufImportTree::document)
                .flatMap(Optional::stream)
                .map(imported -> imported.getStatementsRecursive(clazz))
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public Optional<String> qualifiedCanonicalName() {
        if(name == null && packageName == null) {
            return Optional.empty();
        }else if(name != null && packageName == null) {
            return Optional.of(name);
        }else if(name == null) {
            return Optional.of(packageName);
        }else {
            return Optional.of(packageName + "." + name);
        }
    }

    @Override
    public Optional<String> qualifiedPath() {
        if(name == null && packageName == null) {
            return Optional.empty();
        }else if(name != null && packageName == null) {
            return Optional.of(name);
        }else if(name == null) {
            return Optional.of(packageName);
        }else {
            return Optional.of(packageNamePath().orElseThrow() + "/" + name);
        }
    }

    @Override
    public Optional<String> qualifiedName() {
        if(name == null && packageName == null) {
            return Optional.empty();
        }else if(name != null && packageName == null) {
            return Optional.of(name);
        }else if(name == null) {
            return Optional.of(packageName);
        }else {
            return Optional.of(packageName + "." + name);
        }
    }

    public Optional<ProtobufImportTree> getImport(String fullyQualifiedName) {
        return Optional.ofNullable(imports.get(fullyQualifiedName));
    }

    public Collection<ProtobufImportTree> imports() {
        return Collections.unmodifiableCollection(imports.values());
    }

    @Override
    public Collection<ProtobufDocumentChildTree> statements() {
        var statements = new ArrayList<ProtobufDocumentChildTree>();
        statements.addAll(imports.values());
        statements.addAll(super.statements());
        return Collections.unmodifiableList(statements);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufDocumentChildTree::isAttributed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProtobufDocumentTree that
                && Objects.equals(that.qualifiedName(), this.qualifiedName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.qualifiedName());
    }
}
