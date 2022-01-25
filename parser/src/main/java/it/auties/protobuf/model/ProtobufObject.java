package it.auties.protobuf.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Data
public class ProtobufObject<T extends ProtobufStatement> implements ProtobufStatement {
    private final String name;
    private final List<T> statements;
    public ProtobufObject(String name){
        this(name, new ArrayList<>());
    }
}
