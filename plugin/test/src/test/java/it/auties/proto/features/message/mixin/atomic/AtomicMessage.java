package it.auties.proto.features.message.mixin.atomic;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static it.auties.protobuf.model.ProtobufType.*;

@ProtobufMessage
public record AtomicMessage(
        @ProtobufProperty(index = 1, type = STRING)
        AtomicReference<ProtobufString> atomicString,
        @ProtobufProperty(index = 2, type = UINT32)
        AtomicInteger atomicInteger,
        @ProtobufProperty(index = 3, type = UINT64)
        AtomicLong atomicLong,
        @ProtobufProperty(index = 4, type = BOOL)
        AtomicBoolean atomicBoolean
) {
        @Override
        public boolean equals(Object obj) {
                return obj instanceof AtomicMessage other
                        && Objects.equals(atomicString.get(), other.atomicString().get())
                        && Objects.equals(atomicInteger.get(), other.atomicInteger().get())
                        && Objects.equals(atomicLong.get(), other.atomicLong().get())
                        && Objects.equals(atomicBoolean.get(), other.atomicBoolean().get());
        }
}
