package it.auties.proto.ci;

import com.google.protobuf.ByteString;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class ProtobufInteroperabilityTest {
    @Test
        public void encodeScalarTypes() {
        var random = new Random();
        var googleMessage = GoogleScalarMessage.newBuilder()
                .setString("Hello, this is an automated test")
                .setBytes(ByteString.copyFrom("Hello, this is an automated test".getBytes(StandardCharsets.UTF_8)))
                .setFixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setSfixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setInt32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setUint32(random.nextInt(0, Integer.MAX_VALUE))
                .setFixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                .setSfixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                .setBool(random.nextBoolean())
                .setDouble(random.nextDouble(Double.MIN_VALUE, Double.MAX_VALUE))
                .setFloat(random.nextFloat(Float.MIN_VALUE, Float.MAX_VALUE))
                .build();
        var modernDecoded = ProtobufInteroperabilityTestModernScalarMessageSpec.decode(googleMessage.toByteArray());
        assertEquals(modernDecoded, googleMessage);
        var modernEncoded = ProtobufInteroperabilityTestModernScalarMessageSpec.encode(modernDecoded);
        var modernDecoded1 = ProtobufInteroperabilityTestModernScalarMessageSpec.decode(modernEncoded);
        assertEquals(modernDecoded, modernDecoded1);
    }

    private void assertEquals(ModernScalarMessage modernScalarMessage, ModernScalarMessage modernDecoded) {
        Assertions.assertEquals(modernScalarMessage.fixed32(), modernDecoded.fixed32());
        Assertions.assertEquals(modernScalarMessage.sfixed32(), modernDecoded.sfixed32());
        Assertions.assertEquals(modernScalarMessage.int32(), modernDecoded.int32());
        Assertions.assertEquals(modernScalarMessage.uint32(), modernDecoded.uint32());
        Assertions.assertEquals(modernScalarMessage.fixed64(), modernDecoded.fixed64());
        Assertions.assertEquals(modernScalarMessage.sfixed64(), modernDecoded.sfixed64());
        Assertions.assertEquals(modernScalarMessage.int64(), modernDecoded.int64());
        Assertions.assertEquals(modernScalarMessage.uint64(), modernDecoded.uint64());
        Assertions.assertEquals(modernScalarMessage._float(), modernDecoded._float());
        Assertions.assertEquals(modernScalarMessage._double(), modernDecoded._double());
        Assertions.assertEquals(modernScalarMessage.bool(), modernDecoded.bool());
        Assertions.assertEquals(modernScalarMessage.string(), modernDecoded.string());
        assertEquals(modernScalarMessage.bytes(), modernDecoded.bytes());
    }

    private void assertEquals(ModernScalarMessage modernDecoded, GoogleScalarMessage oldDecoded) {
        Assertions.assertEquals(modernDecoded.fixed32(), oldDecoded.getFixed32());
        Assertions.assertEquals(modernDecoded.sfixed32(), oldDecoded.getSfixed32());
        Assertions.assertEquals(modernDecoded.int32(), oldDecoded.getInt32());
        Assertions.assertEquals(modernDecoded.uint32(), oldDecoded.getUint32());
        Assertions.assertEquals(modernDecoded.fixed64(), oldDecoded.getFixed64());
        Assertions.assertEquals(modernDecoded.sfixed64(), oldDecoded.getSfixed64());
        Assertions.assertEquals(modernDecoded.int64(), oldDecoded.getInt64());
        Assertions.assertEquals(modernDecoded.uint64(), oldDecoded.getUint64());
        Assertions.assertEquals(modernDecoded._float(), oldDecoded.getFloat());
        Assertions.assertEquals(modernDecoded._double(), oldDecoded.getDouble());
        Assertions.assertEquals(modernDecoded.bool(), oldDecoded.getBool());
        Assertions.assertEquals(modernDecoded.string().toString(), oldDecoded.getString());
        assertEquals(modernDecoded.bytes(), oldDecoded.getBytes().toByteArray());
    }

    private void assertEquals(ByteBuffer buffer, byte[] array) {
        assertEquals(buffer, ByteBuffer.wrap(array));
    }

    private void assertEquals(ByteBuffer buffer, ByteBuffer other) {
        Assertions.assertEquals(buffer.remaining(), other.remaining());
        var bufferPosition = buffer.position();
        var otherPosition = other.position();
        for(var i = 0; i < other.remaining(); i++) {
            Assertions.assertEquals(buffer.get(bufferPosition + i), other.get(otherPosition + i));
        }
    }

    public static final class GoogleScalarMessage extends
            com.google.protobuf.GeneratedMessageLite<
                    GoogleScalarMessage, GoogleScalarMessage.Builder> implements

            GoogleScalarMessageOrBuilder {
        public static final int SFIXED32_FIELD_NUMBER = 2;
        public static final int INT32_FIELD_NUMBER = 3;
        public static final int UINT32_FIELD_NUMBER = 4;
        public static final int FIXED64_FIELD_NUMBER = 5;
        public static final int SFIXED64_FIELD_NUMBER = 6;
        public static final int INT64_FIELD_NUMBER = 7;
        public static final int UINT64_FIELD_NUMBER = 8;
        public static final int FLOAT_FIELD_NUMBER = 9;
        public static final int DOUBLE_FIELD_NUMBER = 10;
        public static final int BOOL_FIELD_NUMBER = 11;
        public static final int STRING_FIELD_NUMBER = 12;
        public static final int BYTES_FIELD_NUMBER = 13;
        private static final GoogleScalarMessage DEFAULT_INSTANCE;
        private static volatile com.google.protobuf.Parser<GoogleScalarMessage> PARSER;

        static {
            GoogleScalarMessage defaultInstance = new GoogleScalarMessage();


            DEFAULT_INSTANCE = defaultInstance;
            com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
                    GoogleScalarMessage.class, defaultInstance);
        }

        private int bitField0_;
        private int fixed32_;
        private int sfixed32_;
        private int int32_;
        private int uint32_;
        private long fixed64_;
        private long sfixed64_;
        private long int64_;
        private long uint64_;
        private float float_;
        private double double_;
        private boolean bool_;
        private String string_;
        private ByteString bytes_;

        private GoogleScalarMessage() {
            string_ = "";
            bytes_ = ByteString.EMPTY;
        }

        public static GoogleScalarMessage parseFrom(
                ByteBuffer data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static GoogleScalarMessage parseFrom(
                ByteBuffer data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static GoogleScalarMessage parseFrom(
                ByteString data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static GoogleScalarMessage parseFrom(
                ByteString data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static GoogleScalarMessage parseFrom(byte[] data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data);
        }

        public static GoogleScalarMessage parseFrom(
                byte[] data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, data, extensionRegistry);
        }

        public static GoogleScalarMessage parseFrom(java.io.InputStream input)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input);
        }

        public static GoogleScalarMessage parseFrom(
                java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static GoogleScalarMessage parseDelimitedFrom(java.io.InputStream input)
                throws java.io.IOException {
            return parseDelimitedFrom(DEFAULT_INSTANCE, input);
        }

        public static GoogleScalarMessage parseDelimitedFrom(
                java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static GoogleScalarMessage parseFrom(
                com.google.protobuf.CodedInputStream input)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input);
        }

        public static GoogleScalarMessage parseFrom(
                com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageLite.parseFrom(
                    DEFAULT_INSTANCE, input, extensionRegistry);
        }

        public static Builder newBuilder() {
            return (Builder) DEFAULT_INSTANCE.createBuilder();
        }

        public static Builder newBuilder(GoogleScalarMessage prototype) {
            return (Builder) DEFAULT_INSTANCE.createBuilder(prototype);
        }

        public static GoogleScalarMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static com.google.protobuf.Parser<GoogleScalarMessage> parser() {
            return DEFAULT_INSTANCE.getParserForType();
        }

        @Override
        public boolean hasFixed32() {
            return ((bitField0_ & 0x00000001) != 0);
        }

        @Override
        public int getFixed32() {
            return fixed32_;
        }

        private void setFixed32(int value) {
            bitField0_ |= 0x00000001;
            fixed32_ = value;
        }

        private void clearFixed32() {
            bitField0_ = (bitField0_ & ~0x00000001);
            fixed32_ = 0;
        }

        @Override
        public boolean hasSfixed32() {
            return ((bitField0_ & 0x00000002) != 0);
        }

        @Override
        public int getSfixed32() {
            return sfixed32_;
        }

        private void setSfixed32(int value) {
            bitField0_ |= 0x00000002;
            sfixed32_ = value;
        }

        private void clearSfixed32() {
            bitField0_ = (bitField0_ & ~0x00000002);
            sfixed32_ = 0;
        }

        @Override
        public boolean hasInt32() {
            return ((bitField0_ & 0x00000004) != 0);
        }

        @Override
        public int getInt32() {
            return int32_;
        }

        private void setInt32(int value) {
            bitField0_ |= 0x00000004;
            int32_ = value;
        }

        private void clearInt32() {
            bitField0_ = (bitField0_ & ~0x00000004);
            int32_ = 0;
        }

        @Override
        public boolean hasUint32() {
            return ((bitField0_ & 0x00000008) != 0);
        }

        @Override
        public int getUint32() {
            return uint32_;
        }

        private void setUint32(int value) {
            bitField0_ |= 0x00000008;
            uint32_ = value;
        }

        private void clearUint32() {
            bitField0_ = (bitField0_ & ~0x00000008);
            uint32_ = 0;
        }

        @Override
        public boolean hasFixed64() {
            return ((bitField0_ & 0x00000010) != 0);
        }

        @Override
        public long getFixed64() {
            return fixed64_;
        }

        private void setFixed64(long value) {
            bitField0_ |= 0x00000010;
            fixed64_ = value;
        }

        private void clearFixed64() {
            bitField0_ = (bitField0_ & ~0x00000010);
            fixed64_ = 0L;
        }

        @Override
        public boolean hasSfixed64() {
            return ((bitField0_ & 0x00000020) != 0);
        }

        @Override
        public long getSfixed64() {
            return sfixed64_;
        }

        private void setSfixed64(long value) {
            bitField0_ |= 0x00000020;
            sfixed64_ = value;
        }

        private void clearSfixed64() {
            bitField0_ = (bitField0_ & ~0x00000020);
            sfixed64_ = 0L;
        }

        @Override
        public boolean hasInt64() {
            return ((bitField0_ & 0x00000040) != 0);
        }

        @Override
        public long getInt64() {
            return int64_;
        }

        private void setInt64(long value) {
            bitField0_ |= 0x00000040;
            int64_ = value;
        }

        private void clearInt64() {
            bitField0_ = (bitField0_ & ~0x00000040);
            int64_ = 0L;
        }

        @Override
        public boolean hasUint64() {
            return ((bitField0_ & 0x00000080) != 0);
        }

        @Override
        public long getUint64() {
            return uint64_;
        }

        private void setUint64(long value) {
            bitField0_ |= 0x00000080;
            uint64_ = value;
        }

        private void clearUint64() {
            bitField0_ = (bitField0_ & ~0x00000080);
            uint64_ = 0L;
        }

        @Override
        public boolean hasFloat() {
            return ((bitField0_ & 0x00000100) != 0);
        }

        @Override
        public float getFloat() {
            return float_;
        }

        private void setFloat(float value) {
            bitField0_ |= 0x00000100;
            float_ = value;
        }

        private void clearFloat() {
            bitField0_ = (bitField0_ & ~0x00000100);
            float_ = 0F;
        }

        @Override
        public boolean hasDouble() {
            return ((bitField0_ & 0x00000200) != 0);
        }

        @Override
        public double getDouble() {
            return double_;
        }

        private void setDouble(double value) {
            bitField0_ |= 0x00000200;
            double_ = value;
        }

        private void clearDouble() {
            bitField0_ = (bitField0_ & ~0x00000200);
            double_ = 0D;
        }

        @Override
        public boolean hasBool() {
            return ((bitField0_ & 0x00000400) != 0);
        }

        @Override
        public boolean getBool() {
            return bool_;
        }

        private void setBool(boolean value) {
            bitField0_ |= 0x00000400;
            bool_ = value;
        }

        private void clearBool() {
            bitField0_ = (bitField0_ & ~0x00000400);
            bool_ = false;
        }

        @Override
        public boolean hasString() {
            return ((bitField0_ & 0x00000800) != 0);
        }

        @Override
        public String getString() {
            return string_;
        }

        private void setString(
                String value) {
            Class<?> valueClass = value.getClass();
            bitField0_ |= 0x00000800;
            string_ = value;
        }

        @Override
        public ByteString
        getStringBytes() {
            return ByteString.copyFromUtf8(string_);
        }

        private void setStringBytes(
                ByteString value) {
            string_ = value.toStringUtf8();
            bitField0_ |= 0x00000800;
        }

        private void clearString() {
            bitField0_ = (bitField0_ & ~0x00000800);
            string_ = getDefaultInstance().getString();
        }

        @Override
        public boolean hasBytes() {
            return ((bitField0_ & 0x00001000) != 0);
        }

        @Override
        public ByteString getBytes() {
            return bytes_;
        }

        private void setBytes(ByteString value) {
            Class<?> valueClass = value.getClass();
            bitField0_ |= 0x00001000;
            bytes_ = value;
        }

        private void clearBytes() {
            bitField0_ = (bitField0_ & ~0x00001000);
            bytes_ = getDefaultInstance().getBytes();
        }

        @Override
        @SuppressWarnings({"unchecked", "fallthrough"})
        protected final Object dynamicMethod(
                MethodToInvoke method,
                Object arg0, Object arg1) {
            switch (method) {
                case NEW_MUTABLE_INSTANCE: {
                    return new GoogleScalarMessage();
                }
                case NEW_BUILDER: {
                    return new Builder();
                }
                case BUILD_MESSAGE_INFO: {
                    Object[] objects = new Object[]{
                            "bitField0_",
                            "fixed32_",
                            "sfixed32_",
                            "int32_",
                            "uint32_",
                            "fixed64_",
                            "sfixed64_",
                            "int64_",
                            "uint64_",
                            "float_",
                            "double_",
                            "bool_",
                            "string_",
                            "bytes_",
                    };
                    String info =
                            "\u0001\r\u0000\u0001\u0001\r\r\u0000\u0000\u0000\u0001\u1006\u0000\u0002\u100d\u0001" +
                                    "\u0003\u1004\u0002\u0004\u100b\u0003\u0005\u1005\u0004\u0006\u100e\u0005\u0007\u1002" +
                                    "\u0006\b\u1003\u0007\t\u1001\b\n\u1000\t\u000b\u1007\n\f\u1008\u000b\r\u100a\f";
                    return newMessageInfo(DEFAULT_INSTANCE, info, objects);
                }

                case GET_DEFAULT_INSTANCE: {
                    return DEFAULT_INSTANCE;
                }
                case GET_PARSER: {
                    com.google.protobuf.Parser<GoogleScalarMessage> parser = PARSER;
                    if (parser == null) {
                        synchronized (GoogleScalarMessage.class) {
                            parser = PARSER;
                            if (parser == null) {
                                parser =
                                        new DefaultInstanceBasedParser<GoogleScalarMessage>(
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

        public static final class Builder extends
                com.google.protobuf.GeneratedMessageLite.Builder<
                        GoogleScalarMessage, Builder> implements

                GoogleScalarMessageOrBuilder {

            private Builder() {
                super(DEFAULT_INSTANCE);
            }


            @Override
            public boolean hasFixed32() {
                return instance.hasFixed32();
            }

            @Override
            public int getFixed32() {
                return instance.getFixed32();
            }

            public Builder setFixed32(int value) {
                copyOnWrite();
                instance.setFixed32(value);
                return this;
            }

            public Builder clearFixed32() {
                copyOnWrite();
                instance.clearFixed32();
                return this;
            }


            @Override
            public boolean hasSfixed32() {
                return instance.hasSfixed32();
            }

            @Override
            public int getSfixed32() {
                return instance.getSfixed32();
            }

            public Builder setSfixed32(int value) {
                copyOnWrite();
                instance.setSfixed32(value);
                return this;
            }

            public Builder clearSfixed32() {
                copyOnWrite();
                instance.clearSfixed32();
                return this;
            }


            @Override
            public boolean hasInt32() {
                return instance.hasInt32();
            }

            @Override
            public int getInt32() {
                return instance.getInt32();
            }

            public Builder setInt32(int value) {
                copyOnWrite();
                instance.setInt32(value);
                return this;
            }

            public Builder clearInt32() {
                copyOnWrite();
                instance.clearInt32();
                return this;
            }


            @Override
            public boolean hasUint32() {
                return instance.hasUint32();
            }

            @Override
            public int getUint32() {
                return instance.getUint32();
            }

            public Builder setUint32(int value) {
                copyOnWrite();
                instance.setUint32(value);
                return this;
            }

            public Builder clearUint32() {
                copyOnWrite();
                instance.clearUint32();
                return this;
            }


            @Override
            public boolean hasFixed64() {
                return instance.hasFixed64();
            }

            @Override
            public long getFixed64() {
                return instance.getFixed64();
            }

            public Builder setFixed64(long value) {
                copyOnWrite();
                instance.setFixed64(value);
                return this;
            }

            public Builder clearFixed64() {
                copyOnWrite();
                instance.clearFixed64();
                return this;
            }


            @Override
            public boolean hasSfixed64() {
                return instance.hasSfixed64();
            }

            @Override
            public long getSfixed64() {
                return instance.getSfixed64();
            }

            public Builder setSfixed64(long value) {
                copyOnWrite();
                instance.setSfixed64(value);
                return this;
            }

            public Builder clearSfixed64() {
                copyOnWrite();
                instance.clearSfixed64();
                return this;
            }


            @Override
            public boolean hasInt64() {
                return instance.hasInt64();
            }

            @Override
            public long getInt64() {
                return instance.getInt64();
            }

            public Builder setInt64(long value) {
                copyOnWrite();
                instance.setInt64(value);
                return this;
            }

            public Builder clearInt64() {
                copyOnWrite();
                instance.clearInt64();
                return this;
            }


            @Override
            public boolean hasUint64() {
                return instance.hasUint64();
            }

            @Override
            public long getUint64() {
                return instance.getUint64();
            }

            public Builder setUint64(long value) {
                copyOnWrite();
                instance.setUint64(value);
                return this;
            }

            public Builder clearUint64() {
                copyOnWrite();
                instance.clearUint64();
                return this;
            }


            @Override
            public boolean hasFloat() {
                return instance.hasFloat();
            }

            @Override
            public float getFloat() {
                return instance.getFloat();
            }

            public Builder setFloat(float value) {
                copyOnWrite();
                instance.setFloat(value);
                return this;
            }

            public Builder clearFloat() {
                copyOnWrite();
                instance.clearFloat();
                return this;
            }


            @Override
            public boolean hasDouble() {
                return instance.hasDouble();
            }

            @Override
            public double getDouble() {
                return instance.getDouble();
            }

            public Builder setDouble(double value) {
                copyOnWrite();
                instance.setDouble(value);
                return this;
            }

            public Builder clearDouble() {
                copyOnWrite();
                instance.clearDouble();
                return this;
            }


            @Override
            public boolean hasBool() {
                return instance.hasBool();
            }

            @Override
            public boolean getBool() {
                return instance.getBool();
            }

            public Builder setBool(boolean value) {
                copyOnWrite();
                instance.setBool(value);
                return this;
            }

            public Builder clearBool() {
                copyOnWrite();
                instance.clearBool();
                return this;
            }


            @Override
            public boolean hasString() {
                return instance.hasString();
            }

            @Override
            public String getString() {
                return instance.getString();
            }

            public Builder setString(
                    String value) {
                copyOnWrite();
                instance.setString(value);
                return this;
            }

            @Override
            public ByteString
            getStringBytes() {
                return instance.getStringBytes();
            }

            public Builder setStringBytes(
                    ByteString value) {
                copyOnWrite();
                instance.setStringBytes(value);
                return this;
            }

            public Builder clearString() {
                copyOnWrite();
                instance.clearString();
                return this;
            }

            @Override
            public boolean hasBytes() {
                return instance.hasBytes();
            }

            @Override
            public ByteString getBytes() {
                return instance.getBytes();
            }

            public Builder setBytes(ByteString value) {
                copyOnWrite();
                instance.setBytes(value);
                return this;
            }

            public Builder clearBytes() {
                copyOnWrite();
                instance.clearBytes();
                return this;
            }
        }
    }

    public static interface GoogleScalarMessageOrBuilder extends com.google.protobuf.MessageLiteOrBuilder {
        boolean hasFixed32();

        int getFixed32();

        boolean hasSfixed32();

        int getSfixed32();

        boolean hasInt32();

        int getInt32();

        boolean hasUint32();

        int getUint32();

        boolean hasFixed64();

        long getFixed64();

        boolean hasSfixed64();

        long getSfixed64();

        boolean hasInt64();

        long getInt64();

        boolean hasUint64();

        long getUint64();

        boolean hasFloat();

        float getFloat();

        boolean hasDouble();

        double getDouble();

        boolean hasBool();

        boolean getBool();

        boolean hasString();

        String getString();

        ByteString getStringBytes();

        boolean hasBytes();

        ByteString getBytes();
    }

    @ProtobufMessage
    public static record ModernScalarMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.FIXED32)
            int fixed32,
            @ProtobufProperty(index = 2, type = ProtobufType.SFIXED32)
            int sfixed32,
            @ProtobufProperty(index = 3, type = ProtobufType.INT32)
            int int32,
            @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
            int uint32,
            @ProtobufProperty(index = 5, type = ProtobufType.FIXED64)
            long fixed64,
            @ProtobufProperty(index = 6, type = ProtobufType.SFIXED64)
            long sfixed64,
            @ProtobufProperty(index = 7, type = ProtobufType.INT64)
            long int64,
            @ProtobufProperty(index = 8, type = ProtobufType.UINT64)
            long uint64,
            @ProtobufProperty(index = 9, type = ProtobufType.FLOAT)
            float _float,
            @ProtobufProperty(index = 10, type = ProtobufType.DOUBLE)
            double _double,
            @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
            boolean bool,
            @ProtobufProperty(index = 12, type = ProtobufType.STRING)
            ProtobufString string,
            @ProtobufProperty(index = 13, type = ProtobufType.BYTES)
            ByteBuffer bytes
    ) {
            @Override
            public ByteBuffer bytes() {
                    return bytes;
            }
    }
}