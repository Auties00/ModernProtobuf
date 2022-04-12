package it.auties.protobuf;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import it.auties.protobuf.jackson.ProtobufParser;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufProperty;
import it.auties.protobuf.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class RepeatedTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var repeatedMessage = new ModernRepeatedMessage(List.of(1));
        var encoded = JACKSON.writeValueAsBytes(repeatedMessage);
        var oldDecoded = RepeatedMessage.parseFrom(encoded);
        var modernDecoded = JACKSON
                .reader()
                .withFeatures(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .with(ProtobufSchema.of(ModernRepeatedMessage.class))
                .readValue(encoded, ModernRepeatedMessage.class);
        Assertions.assertEquals(repeatedMessage.content(), modernDecoded.content());
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class ModernRepeatedMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufProperty.Type.INT32,
                repeated = true
        )
        private List<Integer> content;

        static class ModernRepeatedMessageBuilder {
            public void content(List<Integer> entries){
                if(content == null){
                    this.content = entries;
                }else {
                    content.addAll(entries);
                }
            }
        }
    }

    public interface RepeatedMessageOrBuilder extends com.google.protobuf.MessageLiteOrBuilder {
        List<Integer> getContentList();

        int getContentCount();

        int getContent(int index);
    }

    public static final class RepeatedMessage extends
            com.google.protobuf.GeneratedMessageLite<
                    RepeatedMessage, RepeatedMessage.Builder> implements

            RepeatedMessageOrBuilder {
        private RepeatedMessage() {
            content_ = emptyIntList();
        }

        public static final int CONTENT_FIELD_NUMBER = 1;
        private com.google.protobuf.Internal.IntList content_;

        @Override
        public List<Integer>
        getContentList() {
            return content_;
        }

        @Override
        public int getContentCount() {
            return content_.size();
        }

        @Override
        public int getContent(int index) {
            return content_.getInt(index);
        }

        private void ensureContentIsMutable() {
            com.google.protobuf.Internal.IntList tmp = content_;
            if (!tmp.isModifiable()) {
                content_ =
                        com.google.protobuf.GeneratedMessageLite.mutableCopy(tmp);
            }
        }

        private void setContent(
                int index, int value) {
            ensureContentIsMutable();
            content_.setInt(index, value);
        }

        private void addContent(int value) {
            ensureContentIsMutable();
            content_.addInt(value);
        }

        private void addAllContent(
                Iterable<? extends Integer> values) {
            ensureContentIsMutable();
            com.google.protobuf.AbstractMessageLite.addAll(
                    values, content_);
        }

        private void clearContent() {
            content_ = emptyIntList();
        }

        public static RepeatedMessage parseFrom(
                java.nio.ByteBuffer data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static RepeatedMessage parseFrom(
                java.nio.ByteBuffer data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static RepeatedMessage parseFrom(
                com.google.protobuf.ByteString data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static RepeatedMessage parseFrom(
                com.google.protobuf.ByteString data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static RepeatedMessage parseFrom(byte[] data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static RepeatedMessage parseFrom(
                byte[] data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static RepeatedMessage parseFrom(java.io.InputStream input)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input);
        }

        public static RepeatedMessage parseFrom(
                java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static RepeatedMessage parseDelimitedFrom(java.io.InputStream input)
                throws java.io.IOException {
            return parseDelimitedFrom(DEFAULT_INSTANCE, input);
        }

        public static RepeatedMessage parseDelimitedFrom(
                java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static RepeatedMessage parseFrom(
                com.google.protobuf.CodedInputStream input)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input);
        }

        public static RepeatedMessage parseFrom(
                com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static Builder newBuilder() {
            return (Builder) DEFAULT_INSTANCE.createBuilder();
        }

        public static Builder newBuilder(RepeatedMessage prototype) {
            return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
        }


        public static final class Builder extends
                com.google.protobuf.GeneratedMessageLite.Builder<
                        RepeatedMessage, Builder> implements

                RepeatedMessageOrBuilder {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }


            @Override
            public List<Integer>
            getContentList() {
                return java.util.Collections.unmodifiableList(
                        instance.getContentList());
            }

            @Override
            public int getContentCount() {
                return instance.getContentCount();
            }

            @Override
            public int getContent(int index) {
                return instance.getContent(index);
            }

            public Builder setContent(
                    int index, int value) {
                copyOnWrite();
                instance.setContent(index, value);
                return this;
            }

            public Builder addContent(int value) {
                copyOnWrite();
                instance.addContent(value);
                return this;
            }

            public Builder addAllContent(
                    Iterable<? extends Integer> values) {
                copyOnWrite();
                instance.addAllContent(values);
                return this;
            }

            public Builder clearContent() {
                copyOnWrite();
                instance.clearContent();
                return this;
            }


        }

        @Override
        @SuppressWarnings({"unchecked", "fallthrough"})
        protected final Object dynamicMethod(
                MethodToInvoke method,
                Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE: {
                    return new RepeatedMessage();
                }
                case NEW_BUILDER: {
                    return new Builder();
                }
                case BUILD_MESSAGE_INFO: {
                    Object[] objects = new Object[]{
                            "content_",
                    };
                    String info =
                            "\u0001\u0001\u0000\u0000\u0001\u0001\u0001\u0000\u0001\u0000\u0001\u0016";
                    return newMessageInfo(DEFAULT_INSTANCE, info, objects);
                }

                case GET_DEFAULT_INSTANCE: {
                    return DEFAULT_INSTANCE;
                }
                case GET_PARSER: {
                    com.google.protobuf.Parser<RepeatedMessage> parser = PARSER;
                    if (parser == null) {
                        synchronized (RepeatedMessage.class) {
                            parser = PARSER;
                            if (parser == null) {
                                parser =
                                        new DefaultInstanceBasedParser<RepeatedMessage>(
                                                DEFAULT_INSTANCE);
                                PARSER = parser;
                            }
                        }
                    }
                    return parser;
                }
                case GET_MEMOIZED_IS_INITIALIZED: {
                    return (byte) 1;
                }
                case SET_MEMOIZED_IS_INITIALIZED: {
                    return null;
                }
            }
            throw new UnsupportedOperationException();
        }


        private static final RepeatedMessage DEFAULT_INSTANCE;

        static {
            RepeatedMessage defaultInstance = new RepeatedMessage();


            DEFAULT_INSTANCE = defaultInstance;
            com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
                    RepeatedMessage.class, defaultInstance);
        }

        public static RepeatedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        private static volatile com.google.protobuf.Parser<RepeatedMessage> PARSER;

        public static com.google.protobuf.Parser<RepeatedMessage> parser() {
            return DEFAULT_INSTANCE.getParserForType();
        }
    }
}
