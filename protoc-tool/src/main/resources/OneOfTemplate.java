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

@Accessors(fluent = true)
public enum ${enm.name} {
    ${enm.statements.collect{ it.nameAsConstant + '(' + it.index + ')'}.join(', ')};

    private final @Getter int index;

    ${enm.name}(int index){
        this.index = index;
    }

    @JsonCreator
    public static ${enm.name} forIndex(int index){
        return Arrays.stream(values()).filter(entry -> entry.index() == index).findFirst().orElseThrow(() -> new NoSuchElementException("Cannot deserialize ${enm.name} from index %s".formatted(index)));
    }
}
