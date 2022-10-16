package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Collectors;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class ProtobufFieldStatement extends ProtobufStatement {
    private ProtobufTypeReference reference;
    private Integer index;
    private Modifier modifier;
    private boolean packed;
    private boolean deprecated;
    private String defaultValue;
    public ProtobufFieldStatement(String packageName, ProtobufObject<?> parent){
        this(null, packageName, parent);
    }

    public ProtobufFieldStatement(String name, String packageName, ProtobufObject<?> parent){
        super(name, packageName, parent);
    }

    public ProtobufTypeReference reference() {
        return reference;
    }

    public ProtobufFieldStatement reference(ProtobufTypeReference type) {
        this.reference = type;
        return this;
    }

    public Integer index() {
        return index;
    }

    public ProtobufFieldStatement index(Integer index) {
        this.index = index;
        return this;
    }

    public Modifier modifier() {
        return modifier;
    }

    public ProtobufFieldStatement modifier(Modifier modifier) {
        this.modifier = modifier;
        return this;
    }

    public boolean packed() {
        return packed;
    }

    public ProtobufFieldStatement packed(boolean packed) {
        this.packed = packed;
        return this;
    }

    public boolean deprecated() {
        return deprecated;
    }

    public ProtobufFieldStatement deprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public ProtobufFieldStatement defaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public String nameAsConstant() {
        if(name().chars().allMatch(entry -> Character.isUpperCase(entry) || Character.isDigit(entry))){
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

    public String qualifiedType(){
        return "%s.%s".formatted(packageName(), reference().name());
    }

    public boolean nothing(){
        return modifier == Modifier.NOTHING;
    }

    public boolean optional() {
        return modifier == Modifier.OPTIONAL;
    }

    public boolean repeated() {
        return modifier == Modifier.REPEATED;
    }

    public boolean required() {
        return modifier == Modifier.REQUIRED;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public ProtobufStatementType type() {
        return ProtobufStatementType.FIELD;
    }

    String toString(int level) {
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
        return reference() == null ? "" : "%s ".formatted(reference().name());
    }

    private String toPrettyModifier() {
        return modifier() == null || modifier() == Modifier.NOTHING
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

    public enum Modifier {
        NOTHING,
        REQUIRED,
        OPTIONAL,
        REPEATED;

        public static Modifier forName(String name) {
            return Arrays.stream(values())
                    .filter(entry -> entry.name().toLowerCase().equals(name))
                    .findAny()
                    .orElse(NOTHING);
        }
    }
}
