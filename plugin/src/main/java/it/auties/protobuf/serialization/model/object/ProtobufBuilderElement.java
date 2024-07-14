package it.auties.protobuf.serialization.model.object;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

public record ProtobufBuilderElement(String name, List<? extends VariableElement> parameters, ExecutableElement delegate) {
}
