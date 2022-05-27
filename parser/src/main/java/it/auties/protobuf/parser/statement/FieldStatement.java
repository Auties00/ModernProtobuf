package it.auties.protobuf.parser.statement;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.parser.model.FieldModifier;
import it.auties.protobuf.parser.model.FieldType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public final class FieldStatement implements ProtobufStatement {
    private String name;
    private String type;
    private int index;
    private FieldModifier modifier;
    private boolean packed;

    public String getNameAsConstant(){
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, getName());
    }

    public FieldType getFieldType() {
        return FieldType.forName(type);
    }

    public String getJavaType() {
        if(type.equals("string")){
            return isRepeated() ? "List<String>" : "String";
        }

        if(type.equals("bool")){
            return isRepeated() ? "List<Boolean>"
                    : isRequired() ? "boolean" : "Boolean";
        }

        if(type.equals("double")){
            return isRepeated() ? "List<Double>"
                    : isRequired() ? "double" : "Double";
        }

        if(type.equals("float")){
            return isRepeated() ? "List<Float>"
                    : isRequired() ? "float" : "Float";
        }

        if(type.equals("bytes")){
            return isRepeated() ? "List<byte[]>" : "byte[]";
        }

        if(type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")){
            return isRepeated() ? "List<Integer>"
                    : isRequired() ? "int" : "Integer";
        }

        if(type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")){
            return isRepeated() ? "List<Long>"
                    : isRequired() ? "long" : "Long";
        }

        return isRepeated() ? "List<%s>".formatted(type) : type;
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
