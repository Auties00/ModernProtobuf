package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.*;
import it.auties.protobuf.tool.util.ProtobufUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record MessageSchemaCreator(MessageStatement message, String pack) implements SchemaCreator {
    @Override
    public String createSchema(int level) {
        var builder = new StringBuilder();
        if(level == 0){
            builder.append("package %s;".formatted(pack));
            builder.append("\n");
            builder.append(createImports());
            builder.append("\n");
        }

        builder.append(ProtobufUtils.indentLine("@Jacksonized", level))
                .append("\n")
                .append(ProtobufUtils.indentLine("@Builder", level))
                .append("\n")
                .append(ProtobufUtils.indentLine("@Data", level))
                .append("\n")
                .append(ProtobufUtils.indentLine("@Accessors(fluent = true)", level))
                .append("\n")
                .append(ProtobufUtils.indentLine("public class %s {".formatted(message.name()), level))
                .append("\n");
        var oneOfMethods = new ArrayList<String>();
        var builderMethods = new ArrayList<String>();
        var nestedObjects = new ArrayList<String>();
        for (var i = 0; i < message.getStatements().size(); i++) {
            var statement = message.getStatements().get(i);
            if (statement instanceof MessageStatement messageStatement) {
                var schema = new MessageSchemaCreator(messageStatement, pack);
                nestedObjects.add(schema.createSchema(level + 1));
                continue;
            }

            if (statement instanceof EnumStatement enumStatement) {
                var schema = new EnumSchemaCreator(enumStatement, pack);
                nestedObjects.add(schema.createSchema(level + 1));
                continue;
            }

            if (statement instanceof OneOfStatement oneOfStatement) {
                var className = oneOfStatement.className();
                var fieldName = oneOfStatement.name();
                var enumStatement = new EnumStatement(className);
                var unknownField = new FieldStatement("unknown")
                        .index(0);
                enumStatement.getStatements().add(unknownField);
                enumStatement.getStatements()
                        .addAll(oneOfStatement.getStatements());
                var condition = ProtobufUtils.generateCondition(className, oneOfStatement.getStatements().iterator());
                var schema = new EnumSchemaCreator(enumStatement, pack);
                nestedObjects.add(schema.createSchema(level + 1));
                oneOfMethods.add("""
                        public %s %sType() {
                        %s
                        }
                        """.formatted(className, fieldName, ProtobufUtils.indentLines(condition, level + 1)));
                continue;
            }

            if (statement instanceof FieldStatement fieldStatement) {
                var index = fieldStatement.index();
                var protoType = fieldStatement.fieldType();
                var implementation = createImplementationParameter(fieldStatement);
                var repeated = createRepeatedParameter(fieldStatement);
                var packed = createPackedParameter(fieldStatement);
                var annotations = fieldAnnotations(fieldStatement);
                var javaType = fieldStatement.javaType(true, true);
                var name = ProtobufUtils.toValidIdentifier(fieldStatement.name());
                var defaultValue = createDefaultValue(fieldStatement);
                builder.append(ProtobufUtils.indentLines("""
                        @ProtobufProperty(index = %s, type = %s%s%s%s)
                        %sprivate %s %s%s;
                         """.formatted(index, protoType, implementation, repeated, packed, annotations, javaType, name, defaultValue), level + 1));
                builder.append("\n");
                if(i != message.getStatements().size() - 1 || !nestedObjects.isEmpty() || !oneOfMethods.isEmpty() || !builderMethods.isEmpty()) {
                    builder.append("\n");
                }

                if (!fieldStatement.isRepeated()) {
                    continue;
                }

                builderMethods.add("""
                        public %sBuilder %s(%s %s){
                            if(this.%s == null) {
                                this.%s = new ArrayList<>();
                            }
                               
                            this.%s.addAll(%s);
                            return this;
                        }
                        """.formatted(message.name(), name, javaType, name, name, name, name, name));
                continue;
            }

            throw new UnsupportedOperationException("Cannot create schema for statement: " + statement);
        }

        var toWrite = new ArrayList<String>();
        toWrite.addAll(oneOfMethods);
        toWrite.addAll(nestedObjects);
        writeList(builder, toWrite, level + 1, !builderMethods.isEmpty());

        if(!builderMethods.isEmpty()){
            builder.append(ProtobufUtils.indentLine("public static class %sBuilder {".formatted(message.name()), level + 1));
            builder.append("\n");
            writeList(builder, builderMethods, level + 2, false);
            builder.append(ProtobufUtils.indentLine("}", level + 1));
            builder.append("\n");
            if(!message.reservedIndexes().isEmpty()) {
                builder.append("\n");
            }
        }

        if(!message.reservedIndexes().isEmpty()){
            addReservedFields(builder, level, "reservedFieldIndexes", reservedIndexesList(), !message.reservedNames().isEmpty());
        }

        if(!message.reservedNames().isEmpty()){
            addReservedFields(builder, level, "reservedFieldNames", reservedNamesList(), false);
        }

        builder.append(ProtobufUtils.indentLine("}", level));
        builder.append("\n");
        return ProtobufUtils.indentLines(builder.toString(), level);
    }

    private void addReservedFields(StringBuilder builder, int level, String method, String reservedNamesList, boolean addNewLine) {
        builder.append(ProtobufUtils.indentLine("@Override", level + 1));
        builder.append("\n");
        builder.append(ProtobufUtils.indentLine("public List<Integer> %s() {".formatted(method), level + 1));
        builder.append("\n");
        builder.append(ProtobufUtils.indentLine("return List.of(%s);".formatted(reservedNamesList), level + 2));
        builder.append("\n");
        builder.append(ProtobufUtils.indentLine("}", level + 1));
        builder.append("\n");
        if (!addNewLine) {
            return;
        }

        builder.append("\n");
    }

    private String reservedIndexesList() {
        return message.reservedIndexes()
                .stream()
                .map(Objects::toString)
                .collect(Collectors.joining(", "));
    }

    private String reservedNamesList() {
        return String.join(", ", message.reservedNames());
    }

    private static void writeList(StringBuilder builder, List<String> entries, int level, boolean forceNewLine) {
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            builder.append(ProtobufUtils.indentLines(entry, level));
            builder.append("\n");
            if (i == entries.size() - 1 && !forceNewLine) {
                continue;
            }

            builder.append("\n");
        }
    }

    private String createDefaultValue(FieldStatement fieldStatement) {
        return fieldStatement.defaultValue() == null ? ""
                : " = %s".formatted(fieldStatement.defaultValue());
    }

    private String fieldAnnotations(FieldStatement fieldStatement) {
        var annotations = new StringBuilder();
        if(fieldStatement.isRequired()){
            annotations.append("@NonNull");
            annotations.append("\n");
        }

        if(fieldStatement.deprecated()){
            annotations.append("@Deprecated");
            annotations.append("\n");
        }

        if(fieldStatement.defaultValue() != null){
            annotations.append("@Builder.Default");
            annotations.append("\n");
        }

        return annotations.toString();
    }

    private String createPackedParameter(FieldStatement fieldStatement) {
        return fieldStatement.packed() ? ", packed = true" : "";
    }

    private String createRepeatedParameter(FieldStatement fieldStatement) {
        return fieldStatement.isRepeated() ? ", repeated = true" : "";
    }

    private String createImplementationParameter(FieldStatement fieldStatement) {
        if (!fieldStatement.isRepeated()) {
            return "";
        }

        var javaType = fieldStatement.javaType(false, false);
        return ", implementation = %s.class".formatted(javaType);
    }

    private String createImports(){
        return """
                import lombok.extern.jackson.Jacksonized;
                import lombok.Builder;
                import lombok.Data;
                import lombok.experimental.Accessors;
                """;
    }
}
