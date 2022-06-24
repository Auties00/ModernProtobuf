package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
public final class MessageStatement extends ProtobufObject<ProtobufStatement> {
    public MessageStatement(String name) {
        super(name, new ArrayList<>());
    }
}
