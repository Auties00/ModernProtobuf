package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.stream.Collectors;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class ProtobufFieldStatement extends ProtobufStatement {
    private ProtobufTypeReference type;
    private int index;
    private Modifier modifier;
    private boolean packed;
    private boolean deprecated;
    private String defaultValue;
    public ProtobufFieldStatement(String packageName, ProtobufObject<?> parent){
        this(null, packageName, parent);
    }

    public ProtobufFieldStatement(String name, String packageName, ProtobufObject<?> parent){
        this(0, name, packageName, parent);
    }

    public ProtobufFieldStatement(int index, String name, String packageName, ProtobufObject<?> parent){
        super(name, packageName, parent);
        this.index = index;
    }

    public ProtobufTypeReference type() {
        return type;
    }

    public ProtobufFieldStatement setType(ProtobufTypeReference type) {
        this.type = type;
        return this;
    }

    public int index() {
        return index;
    }

    public ProtobufFieldStatement setIndex(int index) {
        this.index = index;
        return this;
    }

    public Modifier modifier() {
        return modifier;
    }

    public ProtobufFieldStatement setModifier(Modifier modifier) {
        this.modifier = modifier;
        return this;
    }

    public boolean packed() {
        return packed;
    }

    public ProtobufFieldStatement setPacked(boolean packed) {
        this.packed = packed;
        return this;
    }

    public boolean deprecated() {
        return deprecated;
    }

    public ProtobufFieldStatement setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
        return this;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public ProtobufFieldStatement setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    @Override
    public String name() {
        var name = super.name();
        return switch (parent().statementType()){
            case ENUM -> toEnumName(name);
            case ONE_OF -> toOneOfName(name);
            default -> name;
        };
    }

    private String toOneOfName(String name) {
        var parentName = parent().name().replaceFirst("(?i)oneof", "");
        return name.toLowerCase().contains(parentName.toLowerCase()) ? name.replaceFirst("(?i)oneof", "")
                : parentName + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static String toEnumName(String name) {
        if(name.chars().allMatch(entry -> Character.isUpperCase(entry) || Character.isDigit(entry))){
            return name;
        }

        if(name.contains("_")){
            return name.toUpperCase(Locale.ROOT);
        }

        var builder = new StringBuilder();
        for(var i = 0; i < name.length(); i++){
            var entry = name.charAt(i);
            if (i == 0 || Character.isLowerCase(entry)) {
                builder.append(Character.toUpperCase(entry));
                continue;
            }

            builder.append("_");
            builder.append(entry);
        }

        return builder.toString();
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
    public ProtobufStatementType statementType() {
        return ProtobufStatementType.FIELD;
    }

    String toString(int level) {
        return INDENTATION.repeat(level) +
                toPrettyModifier() +
                toPrettyType() +
                name +
                " = " +
                index() +
                toPrettyOptions() +
                ";";
    }

    private String toPrettyType() {
        if (type() == null) {
            return "";
        }

        if (type() instanceof ProtobufObjectType messageType) {
            return "%s ".formatted(messageType.name());
        }

        return "%s ".formatted(type().protobufType().name());
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

        var map = new LinkedHashMap<>();
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

        public static Modifier of(String name) {
            return Arrays.stream(values())
                    .filter(entry -> entry.name().toLowerCase().equals(name))
                    .findAny()
                    .orElse(NOTHING);
        }
    }
}
