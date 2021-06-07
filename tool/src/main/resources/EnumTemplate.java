<% if(!pack.empty && imports) { %>
    package ${pack};
<% } %>

<% if(imports) { %>
import com.fasterxml.jackson.annotation.*;
import it.auties.protobuf.model.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;
<% } %>

@Accessors(fluent = true)
public enum ${enm.name} implements ProtobufEnum {
    ${enm.statements.collect{ it.name + '(' + it.index + ')'}.join(', ')};

    private final @Getter int index;

    ${enm.name}(int index){
        this.index = index;
    }

    @JsonCreator
    public static ${enm.name} forIndex(int index){
        return Arrays.stream(values()).filter(entry -> entry.index() == index).findFirst().orElse(null);
    }
}
