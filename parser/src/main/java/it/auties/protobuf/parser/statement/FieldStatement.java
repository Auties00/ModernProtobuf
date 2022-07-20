package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.model.FieldModifier;
import it.auties.protobuf.parser.model.FieldType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Accessors(fluent = true)
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
    public FieldStatement(String name){
        super(name);
    }

    public String nameAsConstant() {
        if(name().chars().allMatch(Character::isUpperCase)){
            return name();
        }

        if(name().contains("_")){
            return name().toUpperCase(Locale.ROOT);
        }

        var builder = new StringBuilder();
        for(var i = 0; i < name().length(); i++){
            var entry = name().charAt(i);
            if (i == 0 || Character.isLowerCase(entry)) {
                builder.append(Character.toUpperCase(entry));
                continue;
            }

            builder.append("_");
            builder.append(entry);
        }

        return builder.toString();
    }

    public FieldType fieldType() {
        return FieldType.forName(type)
                .orElse(FieldType.MESSAGE);
    }

    public String javaType(boolean allowPrimitives, boolean wrapRepeated) {
        if (type.equals("string")) {
            return isRepeated() && wrapRepeated ? "List<String>" : "String";
        }

        if (type.equals("bool")) {
            return isRepeated() && wrapRepeated  ? "List<Boolean>"
                    : isRequired() && allowPrimitives ? "boolean" : "Boolean";
        }

        if (type.equals("double")) {
            return isRepeated() && wrapRepeated  ? "List<Double>"
                    : isRequired() && allowPrimitives ? "double" : "Double";
        }

        if (type.equals("float")) {
            return isRepeated() && wrapRepeated  ? "List<Float>"
                    : isRequired() && allowPrimitives ? "float" : "Float";
        }

        if (type.equals("bytes")) {
            return isRepeated() && wrapRepeated  ? "List<byte[]>" : "byte[]";
        }

        if (type.equals("int32") || type.equals("uint32") || type.equals("sint32") || type.equals("fixed32") || type.equals("sfixed32")) {
            return isRepeated() && wrapRepeated  ? "List<Integer>"
                    : isRequired() && allowPrimitives ? "int" : "Integer";
        }

        if (type.equals("int64") || type.equals("uint64") || type.equals("sint64") || type.equals("fixed64") || type.equals("sfixed64")) {
            return isRepeated() && wrapRepeated  ? "List<Long>"
                    : isRequired() && allowPrimitives ? "long" : "Long";
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
                name() +
                " = " +
                index() +
                toPrettyOptions() +
                ";";
    }

    private String toPrettyType() {
        return type() == null ? "" : "%s ".formatted(type());
    }

    private String toPrettyModifier() {
        return modifier() == null || modifier() == FieldModifier.NOTHING
                ? "" : "%s ".formatted(modifier().name().toLowerCase(Locale.ROOT));
    }

    private String toPrettyOptions() {
        if (!packed()
                && !deprecated()
                && defaultValue() == null) {
            return "";
        }

        var map = new HashMap<>();
        map.put("packed", packed()  ? "true" : null);
        map.put("deprecated", deprecated() ? "true" : null);
        map.put("default", defaultValue());
        var entries = map.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> "%s=%s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
        return entries.isEmpty() ? entries : " [%s]".formatted(entries);
    }

    public boolean isNothing(){
        return modifier == FieldModifier.NOTHING;
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
