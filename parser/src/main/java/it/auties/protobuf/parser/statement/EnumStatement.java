package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class EnumStatement extends ProtobufObject<EnumConstantStatement> {
    public EnumStatement(String name) {
        super(name);
    }
}
