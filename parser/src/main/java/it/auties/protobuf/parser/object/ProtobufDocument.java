package it.auties.protobuf.parser.object;

import java.util.List;

public final class ProtobufDocument extends ProtobufObject<ProtobufObject<?>>{
    public ProtobufDocument(List<ProtobufObject<?>> statements) {
        super(null, statements);
    }
}
