package it.auties.protobuf.parser.statement;

import javax.lang.model.SourceVersion;

public abstract sealed class ProtobufStatement permits ProtobufObject, ProtobufFieldStatement {
    public static final String UNKNOWN_NAME = "<unknown>";
    protected final String INDENTATION = "    ";

    protected String name;
    protected String packageName;
    protected final ProtobufObject<?> parent;
    public ProtobufStatement(String name, String packageName, ProtobufObject<?> parent){
        this.name = name;
        this.packageName = packageName;
        this.parent = parent;
    }

    public String name() {
        return name == null ? UNKNOWN_NAME
                : SourceVersion.isKeyword(name) ? "_%s".formatted(name) : name;
    }

    public ProtobufStatement setName(String name) {
        this.name = name;
        return this;
    }

    public String packageName() {
        return packageName;
    }

    protected ProtobufStatement setPackageName(String packageName){
        this.packageName = packageName;
        return this;
    }

    public String qualifiedName(){
        return name == null ? null
                : nested() ? "%s$%s".formatted(parent.qualifiedName(), name())
                : packageName == null ? name()
                : "%s.%s".formatted(packageName(), name());
    }

    public String qualifiedNameWithoutPackage(){
        return name == null ? null
                : nested() ? "%s$%s".formatted(parent.qualifiedNameWithoutPackage(), name())
                : name();
    }

    public String qualifiedCanonicalName(){
        return name == null ? null
                : nested() ? "%s.%s".formatted(parent.qualifiedCanonicalName(), name())
                : packageName == null ? name()
                : "%s.%s".formatted(packageName(), name());
    }

    public String qualifiedCanonicalNameWithoutPackage(){
        return name == null ? null
                : nested() ? "%s.%s".formatted(parent.qualifiedCanonicalNameWithoutPackage(), name())
                : name();
    }

    public String qualifiedCanonicalPath(){
        return name == null ? null
                : nested() ? "%s/%s".formatted(parent.qualifiedCanonicalPath(), name())
                : packageName == null ? name()
                : "%s/%s".formatted(packageName(), name());
    }

    public ProtobufObject<?> parent() {
        return parent;
    }

    public boolean nested(){
        return parent() != null
                && parent().statementType().canBeNested()
                && statementType().canBeNested();
    }

    public abstract ProtobufStatementType statementType();
}
