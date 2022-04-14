<% if(!pack.empty && imports) { %>
package ${pack};
<% } %>

<% if(imports) { %>
import lombok.*;
import lombok.experimental.*;
import lombok.extern.jackson.*;
import java.util.*;
import it.auties.protobuf.api.model.ProtobufProperty;
import static it.auties.protobuf.api.model.ProtobufProperty.Type.*;
<% } %>

@AllArgsConstructor
@Data
@Builder
@Jacksonized
@Accessors(fluent = true)
public class ${message.name} {
    <%
        def builderInstructions = []
        def data = []
        for(statement in message.statements) {
            if(statement instanceof it.auties.protobuf.parser.statement.FieldStatement) {
                def validName = it.auties.protobuf.tool.util.ProtobufUtils.toValidIdentifier(statement.name);
                data.add("""
                     @ProtobufProperty(
                        index = ${statement.index},
                        type = ${statement.fieldType}
                        ${statement.fieldType == it.auties.protobuf.parser.model.FieldType.MESSAGE ? ",concreteType = ${statement.type}.class" : ""}
                        ${statement.repeated ? ",repeated = ${statement.repeated}" : ""}
                        ${statement.packed ? ",packed = true" : "" }
                    )
                    ${statement.required ? "@NonNull" : ""}
                    private ${statement.javaType} ${validName};
                """)
                if(statement.repeated){
                    builderInstructions.add("""
                       public ${message.name}Builder ${validName}(${statement.javaType} ${validName}){
                           if(this.${validName} == null) this.${validName} = new ArrayList<>();
                           this.${validName}.addAll(${validName});
                           return this;
                       }
                    """)
                }
            } else if(statement instanceof it.auties.protobuf.parser.statement.OneOfStatement) {
                for(oneOf in statement.statements) {
                    data.add("""
                        @ProtobufProperty(
                            index = ${oneOf.index},
                            type = ${oneOf.fieldType}
                            ${oneOf.fieldType == it.auties.protobuf.parser.model.FieldType.MESSAGE ? ",concreteType = ${oneOf.type}.class" : ""}
                        )
                        private ${oneOf.javaType} ${it.auties.protobuf.tool.util.ProtobufUtils.toValidIdentifier(oneOf.name)};
                    """)
                }
                data.push("""
                    public ${statement.name} ${statement.nameAsField}Type() {
                        ${it.auties.protobuf.tool.util.ProtobufUtils.generateCondition(statement.name, statement.statements.iterator())}
                    }
                """)

                data.push(new it.auties.protobuf.tool.schema.OneOfSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.parser.statement.MessageStatement) {
                data.push(new it.auties.protobuf.tool.schema.MessageSchemaCreator(statement, pack, false).createSchema())
            } else if(statement instanceof it.auties.protobuf.parser.statement.EnumStatement) {
                data.push(new it.auties.protobuf.tool.schema.EnumSchemaCreator(statement, pack, false).createSchema())
            }
        }
    %>

    <% for(statement in data){ %>
        ${statement}
    <% } %>

    <% if(!builderInstructions.empty){ %>
        public static class ${message.name}Builder {
            <% for(statement in builderInstructions){ %>
                ${statement}
            <% } %>
        }
    <% } %>   
}
