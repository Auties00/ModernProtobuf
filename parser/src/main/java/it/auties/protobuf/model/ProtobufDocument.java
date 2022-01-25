package it.auties.protobuf.model;

import java.util.List;

public class ProtobufDocument extends ProtobufObject<ProtobufObject<?>>{
    public ProtobufDocument(List<ProtobufObject<?>> statements) {
        super(null, statements);
    }
}
