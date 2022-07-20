package it.auties.protobuf.parser.object;

import it.auties.protobuf.parser.model.ProtobufVersion;
import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(fluent = true)
@EqualsAndHashCode(callSuper = true)
public final class ProtobufDocument extends ProtobufObject<ProtobufObject<?>> {
    private final Map<String, String> options;
    private String packageName;
    private ProtobufVersion version;

    public ProtobufDocument() {
        super();
        this.version = ProtobufVersion.defaultVersion();
        this.options = new HashMap<>();
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int level) {
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
        getStatements().forEach(statement -> {
            if (statement instanceof MessageStatement messageStatement) {
                builder.append(messageStatement.toString(0));
                builder.append("\n");
                return;
            }

            if (statement instanceof EnumStatement enumStatement) {
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
