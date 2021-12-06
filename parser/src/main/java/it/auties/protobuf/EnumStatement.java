package it.auties.protobuf;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class EnumStatement extends ProtobufObject<EnumConstantStatement> {
    public EnumStatement(String name) {
        super(name);
    }
}
