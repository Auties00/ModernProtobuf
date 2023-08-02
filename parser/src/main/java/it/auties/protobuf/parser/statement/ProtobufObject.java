package it.auties.protobuf.parser.statement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public abstract sealed class ProtobufObject<T extends ProtobufStatement> extends ProtobufStatement
        permits ProtobufDocument, ProtobufReservable, ProtobufOneOfStatement {
    private final Map<String, T> statements;
    public ProtobufObject(String name, String packageName, ProtobufObject<?> parent){
        super(name, packageName, parent);
        this.statements = new LinkedHashMap<>();
    }

    public Collection<T> statements() {
        return statements.values();
    }

    public ProtobufObject<T> addStatement(T statement){
        statements.put(statement.name(), statement);
        return this;
    }

    public Optional<T> getStatement(String name){
        return name == null ? Optional.empty()
                : Optional.ofNullable(statements.get(name));
    }

    @SuppressWarnings("unchecked")
    public <V extends ProtobufStatement> Optional<V> getStatement(String name, Class<V> clazz){
        return getStatement(name)
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()))
                .map(entry -> (V) entry);
    }

    public <V extends ProtobufStatement> Optional<V> getStatementRecursive(String name, Class<V> clazz){
        var child = getStatement(name, clazz);
        return child.isPresent() ? child : statements().stream()
                .filter(entry -> ProtobufObject.class.isAssignableFrom(entry.getClass()))
                .map(entry -> (ProtobufObject<?>) entry)
                .flatMap(entry -> entry.getStatement(name, clazz).stream())
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufStatement> Optional<V> getStatementRecursive(Class<V> clazz){
        return statements().stream()
                .map((T entry) -> {
                    if(clazz.isAssignableFrom(entry.getClass())){
                        return Optional.of((V) entry);
                    }

                    if(entry instanceof ProtobufObject<?> object){
                        return object.getStatementRecursive(clazz);
                    }

                    return Optional.<V>empty();
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufStatement> List<V> getStatementsRecursive(Class<V> clazz){
        return statements().stream()
            .mapMulti((T entry, Consumer<V> consumer) -> {
                if(clazz.isAssignableFrom(entry.getClass())){
                    consumer.accept((V) entry);
                }

                if(entry instanceof ProtobufObject<?> object){
                    object.getStatementsRecursive(clazz)
                        .forEach(consumer);
                }
            })
            .toList();
    }
}
