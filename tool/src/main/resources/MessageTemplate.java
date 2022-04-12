<% if(!pack.empty && imports) { %>
package ${pack};
<% } %>

<% if(imports) { %>
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.nio.ByteBuffer;
import java.util.*;
<% } %>

@AllArgsConstructor
@Data
@Builder
@Jacksonized
@Accessors(fluent = true)
public class ${message.name} {
    <%
        def data = []
        for(statement in message.statements) {
            if(statement instanceof it.auties.protobuf.parser.model.FieldStatement) {
                data.add("""
                     @ProtobufProperty(
                        index = ${statement.index},
                        type = ProtobufProperty.Type.${statement.fieldType}
                        ${statement.fieldType == it.auties.protobuf.parser.model.FieldType.MESSAGE ? ",concreteType = ${statement.type}.class" : ""}
                        ${statement.repeated ? ",repeated = ${statement.repeated}" : ""}
                    )
                    ${statement.required ? "@NonNull" : ""}
                    private ${statement.javaType} ${it.auties.protobuf.utils.ProtobufUtils.toValidIdentifier(statement.name)};
                """)
            } else if(statement instanceof it.auties.protobuf.parser.model.OneOfStatement) {
                for(oneOf in statement.statements) {
                    data.add("""
                        @ProtobufProperty(
                            index = ${oneOf.index},
                            type = ProtobufProperty.Type.${oneOf.type}
                            ${oneOf.fieldType == it.auties.protobuf.parser.model.FieldType.MESSAGE ? ",concreteType = oneOf.type" : ""}
                        )
                        private ${oneOf.javaType} ${oneOf.name};
                    """)
                }
                data.push("""
                    public ${statement.name} ${statement.nameAsField}Type() {
                        ${it.auties.protobuf.utils.ProtobufUtils.generateCondition(statement.name, statement.statements.iterator())}
                    }
                """)

                data.push(new it.auties.protobuf.schema.OneOfSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.parser.model.MessageStatement) {
                data.push(new it.auties.protobuf.schema.MessageSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.parser.model.EnumStatement) {
                data.push(new it.auties.protobuf.schema.EnumSchemaCreator(statement, pack, false).createSchema())
            }
        }
    %>

    <% for(statement in data){ %>
        ${statement}
    <% } %>
}
