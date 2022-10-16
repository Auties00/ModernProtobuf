package it.auties.protobuf.parser.statement;

import javax.lang.model.SourceVersion;

public abstract sealed class ProtobufStatement permits ProtobufObject, ProtobufFieldStatement {
    protected final String INDENTATION = "    ";

    private String name;
    private String packageName;
    private final ProtobufObject<?> parent;
    public ProtobufStatement(String name, String packageName, ProtobufObject<?> parent){
        this.name = name;
        this.packageName = packageName;
        this.parent = parent;
    }

    public String name() {
        return SourceVersion.isKeyword(name) ? "_%s".formatted(name) : name;
    }

    public ProtobufStatement name(String name) {
        this.name = name;
        return this;
    }

    public String packageName() {
        return packageName;
    }

    protected ProtobufStatement packageName(String packageName){
        this.packageName = packageName;
        return this;
    }

    public String qualifiedName(){
        return nested() ? "%s$%s".formatted(parent.name(), name())
                : "%s.%s".formatted(packageName(), name());
    }

    public ProtobufObject<?> parent() {
        return parent;
    }

    public boolean nested(){
        return parent() != null
                && type() != ProtobufStatementType.DOCUMENT
                && type() != ProtobufStatementType.FIELD;
    }

    public abstract ProtobufStatementType type();
}
