<% if(!pack.empty && imports) { %>
package ${pack};
<% } %>

<% if(imports) { %>
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.*;
import lombok.experimental.Accessors;
import java.util.*;
<% } %>

@AllArgsConstructor
@Accessors(fluent = true)
public enum ${enm.name} {
    UNKNOWN(0),${enm.statements.collect{ it.nameAsConstant + '(' + it.index + ')'}.join(', ')};

    @Getter
    private final int index;

    @JsonCreator
    public static ${enm.name} forIndex(int index){
        return Arrays.stream(values()).filter(entry -> entry.index() == index).findFirst().orElse(${enm.name}.UNKNOWN);
    }
}
