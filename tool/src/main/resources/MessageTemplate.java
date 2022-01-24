import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
<% } %>

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Accessors(fluent = true)
public class ${message.name} {
    <%
        def data = []
        for(statement in message.statements) {
            if(statement instanceof it.auties.protobuf.FieldStatement) {
                data.add("""
                    @JsonProperty(${statement.required ? "value = \"${statement.index}\", required = true" : statement.index})
                    @JsonPropertyDescription("${statement.type}")
                    ${statement.repeated ? "@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)" : ""}
                    ${statement.required ? "@NonNull" : ""}
                    private ${statement.javaType} ${it.auties.protobuf.utils.ProtobufUtils.toValidIdentifier(statement.name)};
                """)
            } else if(statement instanceof it.auties.protobuf.OneOfStatement) {
                for(oneOf in statement.statements) {
                    data.add("""
                        @JsonProperty("${oneOf.index}")
                        @JsonPropertyDescription("${oneOf.type}")
                        private ${oneOf.javaType} ${oneOf.name};
                    """)
                }
                data.push("""
                    public ${statement.name} ${statement.nameAsField}Type() {
                        ${it.auties.protobuf.utils.ProtobufUtils.generateCondition(statement.name, statement.statements.iterator())}
                    }
                """)

                data.push(new it.auties.protobuf.schema.OneOfSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.MessageStatement) {
                data.push(new it.auties.protobuf.schema.MessageSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.EnumStatement) {
                data.push(new it.auties.protobuf.schema.EnumSchemaCreator(statement, pack, false).createSchema())
            }
        }
    %>

    <% for(statement in data){ %>
        ${statement}
    <% } %>
}
