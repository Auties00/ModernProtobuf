package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.type.ProtobufMessageOrEnumTypeReference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

public final class ProtobufMessageValueExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private final SequencedMap<String, ProtobufExpression> data;
    private ProtobufMessageOrEnumTypeReference type;

    public ProtobufMessageValueExpression(int line) {
        super(line);
        this.data = new LinkedHashMap<>();
    }

    public SequencedMap<String, ProtobufExpression> data() {
        return Collections.unmodifiableSequencedMap(data);
    }

    public void addData(String key, ProtobufExpression value) {
        data.put(key, value);
    }

    public ProtobufExpression removeData(String key) {
        return data.remove(key);
    }

    public ProtobufMessageOrEnumTypeReference type() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(ProtobufMessageOrEnumTypeReference type) {
        if(type != null && type.hasDeclaration() && type.protobufType() != ProtobufType.MESSAGE) {
            throw new IllegalStateException("Type isn't an enum");
        }

        this.type = type;
    }

    @Override
    public boolean isAttributed() {
        return type != null;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("{");
        for(var entry : data.entrySet()){
            builder.append("    \"");
            builder.append(entry.getKey());
            builder.append("\"");
            builder.append(": ");
            builder.append(entry.getValue());
        }
        builder.append("}");
        return builder.toString();
    }
}
