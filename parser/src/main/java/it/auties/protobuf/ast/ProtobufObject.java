package it.auties.protobuf.ast;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedList;
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
