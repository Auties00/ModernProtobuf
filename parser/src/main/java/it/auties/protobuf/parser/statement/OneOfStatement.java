package it.auties.protobuf.parser.statement;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
public final class OneOfStatement extends ProtobufObject<FieldStatement> {
    public OneOfStatement(String name) {
        super(name, new ArrayList<>());
    }

    public String getClassName() {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, super.getName());
    }
}
