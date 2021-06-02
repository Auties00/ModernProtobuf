<% if(!pack.empty && imports) { %>
package ${pack};
<% } %>

<% if(imports) { %>
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
<% } %>

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Accessors(fluent = true)
public class ${message.name} {
    <% for(statement in message.statements) {
        if(statement instanceof it.auties.protobuf.ast.FieldStatement) { %>
            @JsonProperty(value = "${statement.index}", required = ${statement.required})
            private ${statement.type} ${it.auties.protobuf.utils.ProtobufUtils.toValidIdentifier(statement.name)};
        <% } %>
    <% } %>

    <% for(statement in message.statements) {
        if(statement instanceof it.auties.protobuf.ast.MessageStatement) { %>
            ${new it.auties.protobuf.schema.MessageSchemaCreator(statement, pack, false).createSchema()}
        <% } else if(statement instanceof it.auties.protobuf.ast.EnumStatement) { %>
            ${new it.auties.protobuf.schema.EnumSchemaCreator(statement, pack, false).createSchema()}
        <% } %>
    <% } %>
}
