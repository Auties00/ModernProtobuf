package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.tool.util.ProtobufUtils;

import java.util.stream.Collectors;

public record EnumSchemaCreator(EnumStatement enumStatement, String pack) implements SchemaCreator {
    @Override
    public String createSchema(int level) {
        var header = createPackageAndImports(level != 0);
        var name = enumStatement.name();
        var constants = createConstants(level);
        return """
                %s@AllArgsConstructor
                @Accessors(fluent = true)
                public enum %s {
                %s;
                                
                    @Getter
                    private final int index;
                                
                    @JsonCreator
                    public static %s of(int index){
                       return Arrays.stream(values())
                           .filter(entry -> entry.index() == index)
                           .findFirst()
                           .orElse(null);
                    }
                }
                """.formatted(header, name, constants, name);
    }

    private String createConstants(int level) {
        return enumStatement.getStatements()
                .stream()
                .map(entry -> "%s(%s)".formatted(entry.nameAsConstant(), entry.name()))
                .map(entry -> ProtobufUtils.indentLine(entry, level))
                .collect(Collectors.joining(", \n"));
    }

    private String createPackageAndImports(boolean nested) {
        return nested ? "" : """
                package %s;
                           
                import com.fasterxml.jackson.annotation.JsonCreator;
                import lombok.experimental.Accessors;
                import lombok.AllArgsConstructor;
                import lombok.Getter;
                """.formatted(pack);
    }
}
