package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.util.LogProvider;
import lombok.NonNull;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;

public abstract class SchemaCreator<T extends CtType<?>, V extends ProtobufObject<?>> implements LogProvider {
    protected T ctType;

    protected CtType<?> parent;

    @NonNull
    protected V protoStatement;

    @NonNull
    protected Factory factory;

    protected boolean accessors;

    protected boolean updating;

    protected SchemaCreator(T ctType, V protoStatement, boolean accessors, Factory factory) {
        this(ctType, null, protoStatement, accessors, factory);
    }

    protected SchemaCreator(V protoStatement, boolean accessors, Factory factory) {
        this(null, protoStatement, accessors, factory);
    }

    protected SchemaCreator(T ctType, CtType<?> parent, V protoStatement, boolean accessors, Factory factory) {
        this.ctType = ctType;
        this.protoStatement = protoStatement;
        this.parent = parent;
        this.factory = factory;
        this.accessors = accessors;
        this.updating = ctType != null;
    }

    public abstract T createSchema();
    public abstract T update();
}
