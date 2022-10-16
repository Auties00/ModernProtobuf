package it.auties.protobuf.parser.statement;

import it.auties.protobuf.base.ProtobufVersion;

import java.util.HashMap;
import java.util.Map;

public final class ProtobufDocument extends ProtobufObject<ProtobufObject<?>> {
    private final Map<String, String> options;
    private ProtobufVersion version;

    public ProtobufDocument() {
        super(null, null,null);
        this.version = ProtobufVersion.defaultVersion();
        this.options = new HashMap<>();
    }

    public Map<String, String> options() {
        return options;
    }

    public ProtobufVersion version() {
        return version;
    }

    public ProtobufDocument version(ProtobufVersion version) {
        this.version = version;
        return this;
    }

    @Override
    public String packageName() {
        return super.packageName();
    }

    @Override
    public ProtobufDocument packageName(String packageName) {
        return (ProtobufDocument) super.packageName(packageName);
    }

    @Override
    public ProtobufStatementType type() {
        return ProtobufStatementType.DOCUMENT;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        if(version() != ProtobufVersion.PROTOBUF_2){
            builder.append("syntax = %s;".formatted(version().versionCode()));
            builder.append("\n");
        }

        if(packageName() != null){
            builder.append("package %s;".formatted(packageName()));
            builder.append("\n");
        }

        toPrettyOptions(builder);
        statements().forEach(statement -> {
            if (statement instanceof ProtobufMessageStatement messageStatement) {
                builder.append(messageStatement.toString(0));
                builder.append("\n");
                return;
            }

            if (statement instanceof ProtobufEnumStatement enumStatement) {
                builder.append(enumStatement.toString(0));
                builder.append("\n");
                return;
            }

            throw new UnsupportedOperationException("Cannot transform to string child %s: unknown object"
                    .formatted(statement == null ? "unknown" : statement.getClass().getName()));
        });

        return builder.toString();
    }

    private void toPrettyOptions(StringBuilder builder) {
        options.entrySet()
                .stream()
                .map(entry -> "option %s = %s;\n"
                        .formatted(entry.getKey(), entry.getValue()))
                .forEach(builder::append);
    }
}
