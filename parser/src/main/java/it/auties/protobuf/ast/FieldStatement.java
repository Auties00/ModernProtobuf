package it.auties.protobuf.ast;

import it.auties.protobuf.utils.ProtobufUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FieldStatement implements ProtobufStatement {
    private String name;
    private String type;
    private int index;
    private FieldModifier modifier;

    public String getType() {
        return ProtobufUtils.getJavaClass(type, isRequired());
    }

    public boolean isRequired(){
        return modifier == FieldModifier.REQUIRED;
    }
}
