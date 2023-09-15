package it.auties.protobuf.serialization.util;

import it.auties.protobuf.serialization.property.ProtobufPropertyStub;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class PropertyUtils {
    private static final Set<String> OPTIONAL_TYPES = Set.of(
            Optional.class.getName(),
            OptionalInt.class.getName(),
            OptionalLong.class.getName(),
            OptionalDouble.class.getName()
    );
    private static final Set<String> ATOMIC_TYPES = Set.of(
            AtomicReference.class.getName(),
            AtomicInteger.class.getName(),
            AtomicLong.class.getName(),
            AtomicBoolean.class.getName()
    );

    public static String getPropertyDefaultValue(ProtobufPropertyStub property) {
        return switch (property.type().implementationType().getKind()) {
            case DECLARED, ARRAY -> {
                if(property.type().fieldType() instanceof DeclaredType declaredType
                        && declaredType.asElement() instanceof TypeElement typeElement
                        && OPTIONAL_TYPES.contains(typeElement.getQualifiedName().toString())) {
                    yield "%s.empty()".formatted(typeElement.getQualifiedName());
                }

                if(property.type().fieldType() instanceof DeclaredType declaredType
                        && declaredType.asElement() instanceof TypeElement typeElement
                        && ATOMIC_TYPES.contains(typeElement.getQualifiedName().toString())) {
                    yield "new %s()".formatted(typeElement.getQualifiedName());
                }

                if (!property.repeated()) {
                    yield "null";
                }

                yield "new %s()".formatted(property.type().concreteCollectionType());
            }
            case INT, CHAR, SHORT, BYTE -> "0";
            case BOOLEAN -> "false";
            case FLOAT -> "0f";
            case DOUBLE -> "0d";
            case LONG -> "0l";
            default -> throw new IllegalArgumentException("Unexpected type: " + property.type().implementationType());
        };
    }
}
