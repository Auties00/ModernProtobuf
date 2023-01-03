package it.auties.protobuf.performance;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.lang.invoke.MethodHandle;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

@AllArgsConstructor
@Builder
public class ExampleObject implements ProtobufMessage {
    public static final MethodHandle CONSTRUCTOR;

    static {
        try {
            CONSTRUCTOR = publicLookup().findConstructor(ExampleObject.class, methodType(void.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @ProtobufProperty(
            index = 1,
            type = ProtobufType.FIXED32
    )
    private Object fixed32;

    @ProtobufProperty(
            index = 2,
            type = ProtobufType.SFIXED32
    )
    private Object sfixed32;

    @ProtobufProperty(
            index = 3,
            type = ProtobufType.INT32
    )
    private Object int32;

    @ProtobufProperty(
            index = 4,
            type = ProtobufType.UINT32
    )
    private Object uint32;

    @ProtobufProperty(
            index = 5,
            type = ProtobufType.FIXED64
    )
    private Object fixed64;

    @ProtobufProperty(
            index = 6,
            type = ProtobufType.SFIXED64
    )
    private Object sfixed64;

    @ProtobufProperty(
            index = 7,
            type = ProtobufType.INT64
    )
    private Object int64;

    @ProtobufProperty(
            index = 8,
            type = ProtobufType.UINT64
    )
    private Object uint64;

    @ProtobufProperty(
            index = 9,
            type = ProtobufType.FLOAT
    )
    private Object _float;

    @ProtobufProperty(
            index = 10,
            type = ProtobufType.DOUBLE
    )
    private Object _double;

    @ProtobufProperty(
            index = 11,
            type = ProtobufType.BOOL
    )
    private Object bool;
}
