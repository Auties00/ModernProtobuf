package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public abstract sealed class ProtobufStatement permits ProtobufObject, FieldStatement {
    protected static final String INDENTATION = "    ";
    private String name;
    public ProtobufStatement(){
        this(null);
    }

    public abstract String toString(int level);
}
