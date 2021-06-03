package it.auties.protobuf.ast;

import com.google.common.base.CaseFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FieldStatement implements ProtobufStatement {
    private String name;
    private String type;
    private int index;
    private FieldModifier modifier;

    public String getName() {
        return name;
    }

    public String getNameAsConstant(){
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, getName());
    }

    public String getType() {
        if(type.equals("string")){
            return isRequired() ? "List<String>" : "String";
        }

        if(type.equals("bool")){
            return isRequired() ? "List<Boolean>" : "boolean";
        }

        if(type.equals("double")){
            return isRequired() ? "List<Double>" : "double";
        }

        if(type.equals("float")){
            return isRequired() ? "List<Float>" : "float";
        }

        if(type.equals("bytes")){
            return isRequired() ? "List<ByteBuffer>" : "ByteBuffer";
        }

        if(type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")){
            return isRequired() ? "List<Integer>" : "int";
        }

        if(type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")){
            return isRequired() ? "List<Long>" : "long";
        }

        return type;
    }

    public boolean isRequired(){
        return modifier == FieldModifier.REQUIRED;
    }
}
