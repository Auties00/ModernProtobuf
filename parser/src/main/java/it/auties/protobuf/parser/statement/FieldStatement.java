package it.auties.protobuf.parser.statement;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.parser.model.FieldModifier;
import it.auties.protobuf.parser.model.FieldType;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Data
@EqualsAndHashCode(callSuper = true)
public final class FieldStatement extends ProtobufStatement {
    private String type;
    private Integer index;
    private FieldModifier modifier;
    private boolean packed;
    private boolean deprecated;
    private String defaultValue;
    private Scope scope;

    public String getNameAsConstant() {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, getName());
    }

    public FieldType getFieldType() {
        return FieldType.forName(type)
                .orElse(FieldType.MESSAGE);
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

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int level) {
        return INDENTATION.repeat(level) +
                toPrettyModifier() +
                toPrettyType() +
                getName() +
                " = " +
                getIndex() +
                toPrettyOptions() +
                ";";
    }

    private String toPrettyType() {
        return getType() == null ? "" : "%s ".formatted(getType());
    }

    private String toPrettyModifier() {
        return getModifier() == null || getModifier() == FieldModifier.NOTHING
                ? "" : "%s ".formatted(getModifier().name().toLowerCase(Locale.ROOT));
    }

    private String toPrettyOptions() {
        if (!isPacked()
                && !isDeprecated()
                && getDefaultValue() == null) {
            return "";
        }

        var map = new HashMap<>();
        map.put("packed", isPacked()  ? "true" : null);
        map.put("deprecated", isDeprecated() ? "true" : null);
        map.put("default", getDefaultValue());
        var entries = map.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
        return entries.isEmpty() ? entries : " [%s]".formatted(entries);
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

    public enum Scope {
        MESSAGE,
        ONE_OF,
        ENUM
    }
}
