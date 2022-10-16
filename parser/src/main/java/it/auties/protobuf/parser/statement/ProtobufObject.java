package it.auties.protobuf.parser.statement;

import java.util.*;

public abstract sealed class ProtobufObject<T extends ProtobufStatement> extends ProtobufStatement
        permits ProtobufDocument, ProtobufReservable, ProtobufOneOfStatement {
    private final Map<String, T> statements;
    public ProtobufObject(String name, String packageName, ProtobufObject<?> parent){
        super(name, packageName, parent);
        this.statements = new HashMap<>();
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

    public Optional<ProtobufStatement> getStatementRecursive(String name){
        if(name == null){
            return Optional.empty();
        }

        for(var entry : statements()){
            if(entry instanceof ProtobufObject<?> object){
                if(object.name().equals(name)){
                    return Optional.of(object);
                }

                var result = object.getStatementRecursive(name);
                if(result.isPresent()){
                    return result;
                }
            }

            if (entry.name().equals(name)) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }
}
