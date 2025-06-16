package it.auties.protobuf.parser.tree;

import java.util.stream.Collectors;

public sealed abstract class ProtobufStatement
        implements ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtension, ProtobufExtensionsList, ProtobufField, ProtobufImport, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufOption, ProtobufPackage, ProtobufReserved, ProtobufReservedList, ProtobufService, ProtobufSyntax {
    private final int line;
    private ProtobufTreeBody<?> parent;

    protected ProtobufStatement(int line, ProtobufTreeBody<?> parent) {
        this.line = line;
        this.parent = parent;
    }

    public ProtobufTreeBody<?> parent() {
        return parent;
    }

    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public int line() {
        return line;
    }

    void writeOptions(StringBuilder builder) {
        if(!(this instanceof ProtobufOptionableStatement optionableStatement)) {
            return;
        }

        var options = optionableStatement.options();
        if(options.isEmpty()) {
            return;
        }

        builder.append(" [");
        var values = options.stream()
                .map(ProtobufOption::toString)
                .collect(Collectors.joining(", "));
        builder.append(values);
        builder.append("]");
    }
}
