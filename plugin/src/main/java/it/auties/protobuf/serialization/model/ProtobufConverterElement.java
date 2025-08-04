package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public sealed interface ProtobufConverterElement {
    sealed interface Attributed
            extends ProtobufConverterElement {
        record Serializer(
                ProtobufConverterMethod delegate,
                TypeMirror parameterType,
                TypeMirror returnType
        ) implements Attributed {

        }

        record Deserializer(
                ProtobufConverterMethod delegate,
                TypeMirror parameterType,
                TypeMirror returnType
        ) implements Attributed {

        }
    }

    record Unattributed(
            Element invoker,
            TypeMirror from,
            TypeMirror to,
            ProtobufType protobufType,
            List<TypeElement> mixins,
            Type type
    ) implements ProtobufConverterElement {
        public enum Type {
            SERIALIZER,
            DESERIALIZER
        }
    }
}
