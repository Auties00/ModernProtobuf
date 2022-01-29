package it.auties.protobuf.model;

import com.google.common.base.CaseFormat;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class OneOfStatement extends ProtobufObject<FieldStatement> {
    public OneOfStatement(String name) {
        super(name);
    }

    @Override
    public String getName() {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, super.getName());
    }

    public String getNameAsField(){
        return super.getName();
    }
}
