<% if(!pack.empty && imports) { %>
package ${pack};
<% } %>

<% if(imports) { %>
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.nio.ByteBuffer;
import java.util.*;
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
                    @JsonProperty(value = "${statement.index}", required = ${statement.required})
                    @JsonPropertyDescription("${statement.type}")
                    ${statement.repeated ? "@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)" : ""}
                    private ${statement.javaType} ${it.auties.protobuf.utils.ProtobufUtils.toValidIdentifier(statement.name)};
                """)
            } else if(statement instanceof it.auties.protobuf.OneOfStatement) {
                for(oneOf in statement.statements) {
                    data.add("""
                        @JsonProperty(value = "${oneOf.index}", required = false)
                        @JsonPropertyDescription("${oneOf.type}")
                        private ${oneOf.javaType} ${oneOf.name};
                    """)
                }
                data.push("""
                    public ${statement.name} ${statement.nameAsField}Case() {
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

    <% for(statement in data.reverse()){ %>
        ${statement}
    <% } %>
}
