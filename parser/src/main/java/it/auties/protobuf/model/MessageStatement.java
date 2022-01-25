package it.auties.protobuf.model;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class MessageStatement extends ProtobufObject<ProtobufStatement> {
    public MessageStatement(String name) {
        super(name);
    }
}
