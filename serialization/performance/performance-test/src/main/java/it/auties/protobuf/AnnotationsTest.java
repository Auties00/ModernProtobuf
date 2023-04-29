package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

public class AnnotationsTest {
    @SneakyThrows
    public static void main(String[] args) {
        var wrapper = new Wrapper("Abc");

    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class Wrapper implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        private String entry;
    }
}
