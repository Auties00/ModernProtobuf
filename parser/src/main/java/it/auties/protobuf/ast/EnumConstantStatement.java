package it.auties.protobuf.ast;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
public final class EnumConstantStatement implements ProtobufStatement {
    private String name;
    private int index;
}
