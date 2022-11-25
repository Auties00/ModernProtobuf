package it.auties.protobuf.tool.util;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;

@UtilityClass
@Accessors(fluent = true)
public class AccessorsSettings {
    @Setter
    @Getter
    private boolean accessors = false;
}
