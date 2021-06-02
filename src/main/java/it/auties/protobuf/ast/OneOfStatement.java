package it.auties.protobuf.ast;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class OneOfStatement extends ProtobufObject<FieldStatement> {
    public OneOfStatement(String name) {
        super(name);
    }
}
