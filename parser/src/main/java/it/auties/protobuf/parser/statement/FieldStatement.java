package it.auties.protobuf.parser.statement;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.parser.model.FieldModifier;
import it.auties.protobuf.parser.model.FieldType;

public final class FieldStatement extends ProtobufStatement {
    private final String type;
    private final int index;
    private final FieldModifier modifier;
    private final boolean packed;

    public FieldStatement(String name, String type, int index, FieldModifier modifier, boolean packed) {
        super(name);
        this.type = type;
        this.index = index;
        this.modifier = modifier;
        this.packed = packed;
    }

    public int getIndex() {
        return index;
    }

    public boolean isPacked() {
        return packed;
    }

    public FieldModifier getModifier() {
        return modifier;
    }

    public String getType() {
        return type;
    }

    public String getNameAsConstant() {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, getName());
    }

    public FieldType getFieldType() {
        return FieldType.forName(type);
    }

    public String getJavaType() {
        if (type.equals("string")) {
            return isRepeated() ? "List<String>" : "String";
        }

        if (type.equals("bool")) {
            return isRepeated() ? "List<Boolean>"
                    : isRequired() ? "boolean" : "Boolean";
        }

        if (type.equals("double")) {
            return isRepeated() ? "List<Double>"
                    : isRequired() ? "double" : "Double";
        }

        if (type.equals("float")) {
            return isRepeated() ? "List<Float>"
                    : isRequired() ? "float" : "Float";
        }

        if (type.equals("bytes")) {
            return isRepeated() ? "List<byte[]>" : "byte[]";
        }

        if (type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")) {
            return isRepeated() ? "List<Integer>"
                    : isRequired() ? "int" : "Integer";
        }

        if (type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")) {
            return isRepeated() ? "List<Long>"
                    : isRequired() ? "long" : "Long";
        }

        return isRepeated() ? "List<%s>".formatted(type) : type;
    }

    public boolean isOptional() {
        return modifier == FieldModifier.OPTIONAL;
    }

    public boolean isRepeated() {
        return modifier == FieldModifier.REPEATED;
    }

    public boolean isRequired() {
        return modifier == FieldModifier.REQUIRED;
    }
}
