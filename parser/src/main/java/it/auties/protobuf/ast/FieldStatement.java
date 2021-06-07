package it.auties.protobuf.ast;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.model.ProtobufMessage;
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
            return isRepeated() ? "List<String>" : "String";
        }

        if(type.equals("bool")){
            return isRepeated() ? "List<Boolean>" : "boolean";
        }

        if(type.equals("double")){
            return isRepeated() ? "List<Double>" : "double";
        }

        if(type.equals("float")){
            return isRepeated() ? "List<Float>" : "float";
        }

        if(type.equals("bytes")){
            return isRepeated() ? "List<byte[]>" : "byte[]";
        }

        if(type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")){
            return isRepeated() ? "List<Integer>" : "int";
        }

        if(type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")){
            return isRepeated() ? "List<Long>" : "long";
        }

        return isRepeated() ? "List<%s>".formatted(type) : type;
    }

    public String getRawType() {
        if(type.equals("string")){
            return "String";
        }

        if(type.equals("bool")){
            return "boolean";
        }

        if(type.equals("bytes")){
            return "byte[]";
        }

        if(type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")){
            return "int";
        }

        if(type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")){
            return "long";
        }

        return type;
    }

    public boolean isOptional(){
        return modifier == FieldModifier.OPTIONAL;
    }

    public boolean isRepeated(){
        return modifier == FieldModifier.REPEATED;
    }
    
    public boolean isRequired(){
        return modifier == FieldModifier.REQUIRED;
    }
}
