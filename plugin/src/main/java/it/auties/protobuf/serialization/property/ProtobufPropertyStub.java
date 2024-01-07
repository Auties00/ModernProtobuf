package it.auties.protobuf.serialization.property;

import it.auties.protobuf.annotation.ProtobufProperty;

import javax.lang.model.element.Element;
import java.util.concurrent.ConcurrentHashMap;

public record ProtobufPropertyStub(int index, String name, Element accessor, ProtobufPropertyType type, boolean required, boolean packed) {
    private static final String KEY_SET_VIEW = ConcurrentHashMap.KeySetView.class.getCanonicalName();

    public ProtobufPropertyStub(String name, Element accessor, ProtobufPropertyType type, ProtobufProperty annotation) {
        this(
                annotation.index(),
                name,
                accessor,
                type,
                annotation.required(),
                annotation.packed()
        );
    }
    
    public String defaultValue() {
        return switch (type().fieldType().getKind()) {
            case DECLARED, ARRAY -> switch (type()) {
                case ProtobufPropertyType.MapType mapType -> "new %s()".formatted(mapType.mapType());
                case ProtobufPropertyType.NormalType ignored -> "null";
                case ProtobufPropertyType.CollectionType collectionType -> {
                    var type = type().fieldType().toString();
                    var typeParametersStart = type.indexOf("<");
                    var rawType = typeParametersStart == -1 ? type : type.substring(0, typeParametersStart);
                    if(rawType.equals(KEY_SET_VIEW)) {
                        yield "java.util.concurrent.ConcurrentHashMap.newKeySet()";
                    }

                    yield "new %s()".formatted(collectionType.collectionType());
                }
                case ProtobufPropertyType.AtomicType ignored -> "new %s()".formatted(type().fieldType());
                case ProtobufPropertyType.OptionalType ignored -> {
                    var type = type().fieldType().toString();
                    var typeParametersStart = type.indexOf("<");
                    var rawType = typeParametersStart == -1 ? type : type.substring(0, typeParametersStart);
                    yield "%s.empty()".formatted(rawType);
                }
            };
            case INT, CHAR, SHORT, BYTE -> "0";
            case BOOLEAN -> "false";
            case FLOAT -> "0f";
            case DOUBLE -> "0d";
            case LONG -> "0l";
            default -> throw new IllegalStateException("Unexpected value: " + type().fieldType().getKind());
        };
    }
}
