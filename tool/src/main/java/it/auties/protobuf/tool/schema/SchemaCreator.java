package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.util.LogProvider;
import lombok.NonNull;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

public abstract class SchemaCreator<T extends CtType<?>, V extends ProtobufObject<?>> implements LogProvider {
    protected T ctType;

    @NonNull
    protected V protoStatement;

    @NonNull
    protected Factory factory;

    protected SchemaCreator(T ctType, V protoStatement, Factory factory) {
        this.ctType = ctType;
        this.protoStatement = protoStatement;
        this.factory = factory;
    }

    protected SchemaCreator(V protoStatement, Factory factory) {
        this(null, protoStatement, factory);
    }

    public  abstract T createSchema();
    public  abstract T update();
}
