package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;

public final class ProtobufExtendStatement
        extends ProtobufStatementWithBodyImpl<ProtobufMessageChild>
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufMessageChild>,
                   ProtobufDocumentChild, ProtobufGroupChild, ProtobufMessageChild, ProtobufExtendChild {
    private ProtobufTypeReference declaration;

    public ProtobufExtendStatement(int line) {
        super(line);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("extend");
        builder.append(" ");

        var name = Objects.requireNonNullElse(declaration.name(), "[missing]");
        builder.append(name);
        builder.append(" ");

        builder.append("{");
        builder.append("\n");

        if(children.isEmpty()) {
            builder.append("\n");
        } else {
            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });
        }

        builder.append("}");

        return builder.toString();
    }

    public ProtobufTypeReference declaration() {
        return declaration;
    }

    public boolean hasDeclaration() {
        return declaration != null;
    }

    public void setDeclaration(ProtobufTypeReference declaration) {
        this.declaration = declaration;
    }
}
