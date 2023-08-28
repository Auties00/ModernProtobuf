// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: scalar.proto

package it.auties.proto.benchmark;

public final class LiteScalar {
  private LiteScalar() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }
  public interface ScalarMessageOrBuilder extends
      // @@protoc_insertion_point(interface_extends:it.auties.proto.benchmark.LiteScalarMessageMessage)
      com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     * @return Whether the fixed32 field is set.
     */
    boolean hasFixed32();
    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     * @return The fixed32.
     */
    int getFixed32();

    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     * @return Whether the sfixed32 field is set.
     */
    boolean hasSfixed32();
    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     * @return The sfixed32.
     */
    int getSfixed32();

    /**
     * <code>optional int32 int32 = 3;</code>
     * @return Whether the int32 field is set.
     */
    boolean hasInt32();
    /**
     * <code>optional int32 int32 = 3;</code>
     * @return The int32.
     */
    int getInt32();

    /**
     * <code>optional uint32 uint32 = 4;</code>
     * @return Whether the uint32 field is set.
     */
    boolean hasUint32();
    /**
     * <code>optional uint32 uint32 = 4;</code>
     * @return The uint32.
     */
    int getUint32();

    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     * @return Whether the fixed64 field is set.
     */
    boolean hasFixed64();
    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     * @return The fixed64.
     */
    long getFixed64();

    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     * @return Whether the sfixed64 field is set.
     */
    boolean hasSfixed64();
    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     * @return The sfixed64.
     */
    long getSfixed64();

    /**
     * <code>optional int64 int64 = 7;</code>
     * @return Whether the int64 field is set.
     */
    boolean hasInt64();
    /**
     * <code>optional int64 int64 = 7;</code>
     * @return The int64.
     */
    long getInt64();

    /**
     * <code>optional uint64 uint64 = 8;</code>
     * @return Whether the uint64 field is set.
     */
    boolean hasUint64();
    /**
     * <code>optional uint64 uint64 = 8;</code>
     * @return The uint64.
     */
    long getUint64();

    /**
     * <code>optional float float = 9;</code>
     * @return Whether the float field is set.
     */
    boolean hasFloat();
    /**
     * <code>optional float float = 9;</code>
     * @return The float.
     */
    float getFloat();

    /**
     * <code>optional double double = 10;</code>
     * @return Whether the double field is set.
     */
    boolean hasDouble();
    /**
     * <code>optional double double = 10;</code>
     * @return The double.
     */
    double getDouble();

    /**
     * <code>optional bool bool = 11;</code>
     * @return Whether the bool field is set.
     */
    boolean hasBool();
    /**
     * <code>optional bool bool = 11;</code>
     * @return The bool.
     */
    boolean getBool();

    /**
     * <code>optional string string = 12;</code>
     * @return Whether the string field is set.
     */
    boolean hasString();
    /**
     * <code>optional string string = 12;</code>
     * @return The string.
     */
    String getString();
    /**
     * <code>optional string string = 12;</code>
     * @return The bytes for string.
     */
    com.google.protobuf.ByteString
        getStringBytes();

    /**
     * <code>optional bytes bytes = 13;</code>
     * @return Whether the bytes field is set.
     */
    boolean hasBytes();
    /**
     * <code>optional bytes bytes = 13;</code>
     * @return The bytes.
     */
    com.google.protobuf.ByteString getBytes();
  }
  /**
   * Protobuf type {@code it.auties.proto.benchmark.LiteScalarMessageMessage}
   */
  public  static final class ScalarMessage extends
      com.google.protobuf.GeneratedMessageLite<
          ScalarMessage, ScalarMessage.Builder> implements
      // @@protoc_insertion_point(message_implements:it.auties.proto.benchmark.LiteScalarMessageMessage)
      ScalarMessageOrBuilder {
    private ScalarMessage() {
      string_ = "";
      bytes_ = com.google.protobuf.ByteString.EMPTY;
    }
    private int bitField0_;
    public static final int FIXED32_FIELD_NUMBER = 1;
    private int fixed32_;
    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     * @return Whether the fixed32 field is set.
     */
    @Override
    public boolean hasFixed32() {
      return ((bitField0_ & 0x00000001) != 0);
    }
    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     * @return The fixed32.
     */
    @Override
    public int getFixed32() {
      return fixed32_;
    }
    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     * @param value The fixed32 to set.
     */
    private void setFixed32(int value) {
      bitField0_ |= 0x00000001;
      fixed32_ = value;
    }
    /**
     * <code>optional fixed32 fixed32 = 1;</code>
     */
    private void clearFixed32() {
      bitField0_ = (bitField0_ & ~0x00000001);
      fixed32_ = 0;
    }

    public static final int SFIXED32_FIELD_NUMBER = 2;
    private int sfixed32_;
    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     * @return Whether the sfixed32 field is set.
     */
    @Override
    public boolean hasSfixed32() {
      return ((bitField0_ & 0x00000002) != 0);
    }
    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     * @return The sfixed32.
     */
    @Override
    public int getSfixed32() {
      return sfixed32_;
    }
    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     * @param value The sfixed32 to set.
     */
    private void setSfixed32(int value) {
      bitField0_ |= 0x00000002;
      sfixed32_ = value;
    }
    /**
     * <code>optional sfixed32 sfixed32 = 2;</code>
     */
    private void clearSfixed32() {
      bitField0_ = (bitField0_ & ~0x00000002);
      sfixed32_ = 0;
    }

    public static final int INT32_FIELD_NUMBER = 3;
    private int int32_;
    /**
     * <code>optional int32 int32 = 3;</code>
     * @return Whether the int32 field is set.
     */
    @Override
    public boolean hasInt32() {
      return ((bitField0_ & 0x00000004) != 0);
    }
    /**
     * <code>optional int32 int32 = 3;</code>
     * @return The int32.
     */
    @Override
    public int getInt32() {
      return int32_;
    }
    /**
     * <code>optional int32 int32 = 3;</code>
     * @param value The int32 to set.
     */
    private void setInt32(int value) {
      bitField0_ |= 0x00000004;
      int32_ = value;
    }
    /**
     * <code>optional int32 int32 = 3;</code>
     */
    private void clearInt32() {
      bitField0_ = (bitField0_ & ~0x00000004);
      int32_ = 0;
    }

    public static final int UINT32_FIELD_NUMBER = 4;
    private int uint32_;
    /**
     * <code>optional uint32 uint32 = 4;</code>
     * @return Whether the uint32 field is set.
     */
    @Override
    public boolean hasUint32() {
      return ((bitField0_ & 0x00000008) != 0);
    }
    /**
     * <code>optional uint32 uint32 = 4;</code>
     * @return The uint32.
     */
    @Override
    public int getUint32() {
      return uint32_;
    }
    /**
     * <code>optional uint32 uint32 = 4;</code>
     * @param value The uint32 to set.
     */
    private void setUint32(int value) {
      bitField0_ |= 0x00000008;
      uint32_ = value;
    }
    /**
     * <code>optional uint32 uint32 = 4;</code>
     */
    private void clearUint32() {
      bitField0_ = (bitField0_ & ~0x00000008);
      uint32_ = 0;
    }

    public static final int FIXED64_FIELD_NUMBER = 5;
    private long fixed64_;
    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     * @return Whether the fixed64 field is set.
     */
    @Override
    public boolean hasFixed64() {
      return ((bitField0_ & 0x00000010) != 0);
    }
    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     * @return The fixed64.
     */
    @Override
    public long getFixed64() {
      return fixed64_;
    }
    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     * @param value The fixed64 to set.
     */
    private void setFixed64(long value) {
      bitField0_ |= 0x00000010;
      fixed64_ = value;
    }
    /**
     * <code>optional fixed64 fixed64 = 5;</code>
     */
    private void clearFixed64() {
      bitField0_ = (bitField0_ & ~0x00000010);
      fixed64_ = 0L;
    }

    public static final int SFIXED64_FIELD_NUMBER = 6;
    private long sfixed64_;
    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     * @return Whether the sfixed64 field is set.
     */
    @Override
    public boolean hasSfixed64() {
      return ((bitField0_ & 0x00000020) != 0);
    }
    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     * @return The sfixed64.
     */
    @Override
    public long getSfixed64() {
      return sfixed64_;
    }
    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     * @param value The sfixed64 to set.
     */
    private void setSfixed64(long value) {
      bitField0_ |= 0x00000020;
      sfixed64_ = value;
    }
    /**
     * <code>optional sfixed64 sfixed64 = 6;</code>
     */
    private void clearSfixed64() {
      bitField0_ = (bitField0_ & ~0x00000020);
      sfixed64_ = 0L;
    }

    public static final int INT64_FIELD_NUMBER = 7;
    private long int64_;
    /**
     * <code>optional int64 int64 = 7;</code>
     * @return Whether the int64 field is set.
     */
    @Override
    public boolean hasInt64() {
      return ((bitField0_ & 0x00000040) != 0);
    }
    /**
     * <code>optional int64 int64 = 7;</code>
     * @return The int64.
     */
    @Override
    public long getInt64() {
      return int64_;
    }
    /**
     * <code>optional int64 int64 = 7;</code>
     * @param value The int64 to set.
     */
    private void setInt64(long value) {
      bitField0_ |= 0x00000040;
      int64_ = value;
    }
    /**
     * <code>optional int64 int64 = 7;</code>
     */
    private void clearInt64() {
      bitField0_ = (bitField0_ & ~0x00000040);
      int64_ = 0L;
    }

    public static final int UINT64_FIELD_NUMBER = 8;
    private long uint64_;
    /**
     * <code>optional uint64 uint64 = 8;</code>
     * @return Whether the uint64 field is set.
     */
    @Override
    public boolean hasUint64() {
      return ((bitField0_ & 0x00000080) != 0);
    }
    /**
     * <code>optional uint64 uint64 = 8;</code>
     * @return The uint64.
     */
    @Override
    public long getUint64() {
      return uint64_;
    }
    /**
     * <code>optional uint64 uint64 = 8;</code>
     * @param value The uint64 to set.
     */
    private void setUint64(long value) {
      bitField0_ |= 0x00000080;
      uint64_ = value;
    }
    /**
     * <code>optional uint64 uint64 = 8;</code>
     */
    private void clearUint64() {
      bitField0_ = (bitField0_ & ~0x00000080);
      uint64_ = 0L;
    }

    public static final int FLOAT_FIELD_NUMBER = 9;
    private float float_;
    /**
     * <code>optional float float = 9;</code>
     * @return Whether the float field is set.
     */
    @Override
    public boolean hasFloat() {
      return ((bitField0_ & 0x00000100) != 0);
    }
    /**
     * <code>optional float float = 9;</code>
     * @return The float.
     */
    @Override
    public float getFloat() {
      return float_;
    }
    /**
     * <code>optional float float = 9;</code>
     * @param value The float to set.
     */
    private void setFloat(float value) {
      bitField0_ |= 0x00000100;
      float_ = value;
    }
    /**
     * <code>optional float float = 9;</code>
     */
    private void clearFloat() {
      bitField0_ = (bitField0_ & ~0x00000100);
      float_ = 0F;
    }

    public static final int DOUBLE_FIELD_NUMBER = 10;
    private double double_;
    /**
     * <code>optional double double = 10;</code>
     * @return Whether the double field is set.
     */
    @Override
    public boolean hasDouble() {
      return ((bitField0_ & 0x00000200) != 0);
    }
    /**
     * <code>optional double double = 10;</code>
     * @return The double.
     */
    @Override
    public double getDouble() {
      return double_;
    }
    /**
     * <code>optional double double = 10;</code>
     * @param value The double to set.
     */
    private void setDouble(double value) {
      bitField0_ |= 0x00000200;
      double_ = value;
    }
    /**
     * <code>optional double double = 10;</code>
     */
    private void clearDouble() {
      bitField0_ = (bitField0_ & ~0x00000200);
      double_ = 0D;
    }

    public static final int BOOL_FIELD_NUMBER = 11;
    private boolean bool_;
    /**
     * <code>optional bool bool = 11;</code>
     * @return Whether the bool field is set.
     */
    @Override
    public boolean hasBool() {
      return ((bitField0_ & 0x00000400) != 0);
    }
    /**
     * <code>optional bool bool = 11;</code>
     * @return The bool.
     */
    @Override
    public boolean getBool() {
      return bool_;
    }
    /**
     * <code>optional bool bool = 11;</code>
     * @param value The bool to set.
     */
    private void setBool(boolean value) {
      bitField0_ |= 0x00000400;
      bool_ = value;
    }
    /**
     * <code>optional bool bool = 11;</code>
     */
    private void clearBool() {
      bitField0_ = (bitField0_ & ~0x00000400);
      bool_ = false;
    }

    public static final int STRING_FIELD_NUMBER = 12;
    private String string_;
    /**
     * <code>optional string string = 12;</code>
     * @return Whether the string field is set.
     */
    @Override
    public boolean hasString() {
      return ((bitField0_ & 0x00000800) != 0);
    }
    /**
     * <code>optional string string = 12;</code>
     * @return The string.
     */
    @Override
    public String getString() {
      return string_;
    }
    /**
     * <code>optional string string = 12;</code>
     * @return The bytes for string.
     */
    @Override
    public com.google.protobuf.ByteString
        getStringBytes() {
      return com.google.protobuf.ByteString.copyFromUtf8(string_);
    }
    /**
     * <code>optional string string = 12;</code>
     * @param value The string to set.
     */
    private void setString(
        String value) {
      Class<?> valueClass = value.getClass();
  bitField0_ |= 0x00000800;
      string_ = value;
    }
    /**
     * <code>optional string string = 12;</code>
     */
    private void clearString() {
      bitField0_ = (bitField0_ & ~0x00000800);
      string_ = getDefaultInstance().getString();
    }
    /**
     * <code>optional string string = 12;</code>
     * @param value The bytes for string to set.
     */
    private void setStringBytes(
        com.google.protobuf.ByteString value) {
      string_ = value.toStringUtf8();
      bitField0_ |= 0x00000800;
    }

    public static final int BYTES_FIELD_NUMBER = 13;
    private com.google.protobuf.ByteString bytes_;
    /**
     * <code>optional bytes bytes = 13;</code>
     * @return Whether the bytes field is set.
     */
    @Override
    public boolean hasBytes() {
      return ((bitField0_ & 0x00001000) != 0);
    }
    /**
     * <code>optional bytes bytes = 13;</code>
     * @return The bytes.
     */
    @Override
    public com.google.protobuf.ByteString getBytes() {
      return bytes_;
    }
    /**
     * <code>optional bytes bytes = 13;</code>
     * @param value The bytes to set.
     */
    private void setBytes(com.google.protobuf.ByteString value) {
      Class<?> valueClass = value.getClass();
  bitField0_ |= 0x00001000;
      bytes_ = value;
    }
    /**
     * <code>optional bytes bytes = 13;</code>
     */
    private void clearBytes() {
      bitField0_ = (bitField0_ & ~0x00001000);
      bytes_ = getDefaultInstance().getBytes();
    }

    public static LiteScalar.ScalarMessage parseFrom(
        java.nio.ByteBuffer data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        java.nio.ByteBuffer data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static LiteScalar.ScalarMessage parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, data, extensionRegistry);
    }
    public static LiteScalar.ScalarMessage parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static LiteScalar.ScalarMessage parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input);
    }

    public static LiteScalar.ScalarMessage parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return parseDelimitedFrom(DEFAULT_INSTANCE, input, extensionRegistry);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input);
    }
    public static LiteScalar.ScalarMessage parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageLite.parseFrom(
          DEFAULT_INSTANCE, input, extensionRegistry);
    }

    public static Builder newBuilder() {
      return (Builder) DEFAULT_INSTANCE.createBuilder();
    }
    public static Builder newBuilder(LiteScalar.ScalarMessage prototype) {
      return DEFAULT_INSTANCE.createBuilder(prototype);
    }

    /**
     * Protobuf type {@code it.auties.proto.benchmark.LiteScalarMessageMessage}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageLite.Builder<
          LiteScalar.ScalarMessage, Builder> implements
        // @@protoc_insertion_point(builder_implements:it.auties.proto.benchmark.LiteScalarMessageMessage)
        LiteScalar.ScalarMessageOrBuilder {
      // Construct using it.auties.proto.benchmark.LiteScalarMessage.ScalarMessage.newBuilder()
      private Builder() {
        super(DEFAULT_INSTANCE);
      }


      /**
       * <code>optional fixed32 fixed32 = 1;</code>
       * @return Whether the fixed32 field is set.
       */
      @Override
      public boolean hasFixed32() {
        return instance.hasFixed32();
      }
      /**
       * <code>optional fixed32 fixed32 = 1;</code>
       * @return The fixed32.
       */
      @Override
      public int getFixed32() {
        return instance.getFixed32();
      }
      /**
       * <code>optional fixed32 fixed32 = 1;</code>
       * @param value The fixed32 to set.
       * @return This builder for chaining.
       */
      public Builder setFixed32(int value) {
        copyOnWrite();
        instance.setFixed32(value);
        return this;
      }
      /**
       * <code>optional fixed32 fixed32 = 1;</code>
       * @return This builder for chaining.
       */
      public Builder clearFixed32() {
        copyOnWrite();
        instance.clearFixed32();
        return this;
      }

      /**
       * <code>optional sfixed32 sfixed32 = 2;</code>
       * @return Whether the sfixed32 field is set.
       */
      @Override
      public boolean hasSfixed32() {
        return instance.hasSfixed32();
      }
      /**
       * <code>optional sfixed32 sfixed32 = 2;</code>
       * @return The sfixed32.
       */
      @Override
      public int getSfixed32() {
        return instance.getSfixed32();
      }
      /**
       * <code>optional sfixed32 sfixed32 = 2;</code>
       * @param value The sfixed32 to set.
       * @return This builder for chaining.
       */
      public Builder setSfixed32(int value) {
        copyOnWrite();
        instance.setSfixed32(value);
        return this;
      }
      /**
       * <code>optional sfixed32 sfixed32 = 2;</code>
       * @return This builder for chaining.
       */
      public Builder clearSfixed32() {
        copyOnWrite();
        instance.clearSfixed32();
        return this;
      }

      /**
       * <code>optional int32 int32 = 3;</code>
       * @return Whether the int32 field is set.
       */
      @Override
      public boolean hasInt32() {
        return instance.hasInt32();
      }
      /**
       * <code>optional int32 int32 = 3;</code>
       * @return The int32.
       */
      @Override
      public int getInt32() {
        return instance.getInt32();
      }
      /**
       * <code>optional int32 int32 = 3;</code>
       * @param value The int32 to set.
       * @return This builder for chaining.
       */
      public Builder setInt32(int value) {
        copyOnWrite();
        instance.setInt32(value);
        return this;
      }
      /**
       * <code>optional int32 int32 = 3;</code>
       * @return This builder for chaining.
       */
      public Builder clearInt32() {
        copyOnWrite();
        instance.clearInt32();
        return this;
      }

      /**
       * <code>optional uint32 uint32 = 4;</code>
       * @return Whether the uint32 field is set.
       */
      @Override
      public boolean hasUint32() {
        return instance.hasUint32();
      }
      /**
       * <code>optional uint32 uint32 = 4;</code>
       * @return The uint32.
       */
      @Override
      public int getUint32() {
        return instance.getUint32();
      }
      /**
       * <code>optional uint32 uint32 = 4;</code>
       * @param value The uint32 to set.
       * @return This builder for chaining.
       */
      public Builder setUint32(int value) {
        copyOnWrite();
        instance.setUint32(value);
        return this;
      }
      /**
       * <code>optional uint32 uint32 = 4;</code>
       * @return This builder for chaining.
       */
      public Builder clearUint32() {
        copyOnWrite();
        instance.clearUint32();
        return this;
      }

      /**
       * <code>optional fixed64 fixed64 = 5;</code>
       * @return Whether the fixed64 field is set.
       */
      @Override
      public boolean hasFixed64() {
        return instance.hasFixed64();
      }
      /**
       * <code>optional fixed64 fixed64 = 5;</code>
       * @return The fixed64.
       */
      @Override
      public long getFixed64() {
        return instance.getFixed64();
      }
      /**
       * <code>optional fixed64 fixed64 = 5;</code>
       * @param value The fixed64 to set.
       * @return This builder for chaining.
       */
      public Builder setFixed64(long value) {
        copyOnWrite();
        instance.setFixed64(value);
        return this;
      }
      /**
       * <code>optional fixed64 fixed64 = 5;</code>
       * @return This builder for chaining.
       */
      public Builder clearFixed64() {
        copyOnWrite();
        instance.clearFixed64();
        return this;
      }

      /**
       * <code>optional sfixed64 sfixed64 = 6;</code>
       * @return Whether the sfixed64 field is set.
       */
      @Override
      public boolean hasSfixed64() {
        return instance.hasSfixed64();
      }
      /**
       * <code>optional sfixed64 sfixed64 = 6;</code>
       * @return The sfixed64.
       */
      @Override
      public long getSfixed64() {
        return instance.getSfixed64();
      }
      /**
       * <code>optional sfixed64 sfixed64 = 6;</code>
       * @param value The sfixed64 to set.
       * @return This builder for chaining.
       */
      public Builder setSfixed64(long value) {
        copyOnWrite();
        instance.setSfixed64(value);
        return this;
      }
      /**
       * <code>optional sfixed64 sfixed64 = 6;</code>
       * @return This builder for chaining.
       */
      public Builder clearSfixed64() {
        copyOnWrite();
        instance.clearSfixed64();
        return this;
      }

      /**
       * <code>optional int64 int64 = 7;</code>
       * @return Whether the int64 field is set.
       */
      @Override
      public boolean hasInt64() {
        return instance.hasInt64();
      }
      /**
       * <code>optional int64 int64 = 7;</code>
       * @return The int64.
       */
      @Override
      public long getInt64() {
        return instance.getInt64();
      }
      /**
       * <code>optional int64 int64 = 7;</code>
       * @param value The int64 to set.
       * @return This builder for chaining.
       */
      public Builder setInt64(long value) {
        copyOnWrite();
        instance.setInt64(value);
        return this;
      }
      /**
       * <code>optional int64 int64 = 7;</code>
       * @return This builder for chaining.
       */
      public Builder clearInt64() {
        copyOnWrite();
        instance.clearInt64();
        return this;
      }

      /**
       * <code>optional uint64 uint64 = 8;</code>
       * @return Whether the uint64 field is set.
       */
      @Override
      public boolean hasUint64() {
        return instance.hasUint64();
      }
      /**
       * <code>optional uint64 uint64 = 8;</code>
       * @return The uint64.
       */
      @Override
      public long getUint64() {
        return instance.getUint64();
      }
      /**
       * <code>optional uint64 uint64 = 8;</code>
       * @param value The uint64 to set.
       * @return This builder for chaining.
       */
      public Builder setUint64(long value) {
        copyOnWrite();
        instance.setUint64(value);
        return this;
      }
      /**
       * <code>optional uint64 uint64 = 8;</code>
       * @return This builder for chaining.
       */
      public Builder clearUint64() {
        copyOnWrite();
        instance.clearUint64();
        return this;
      }

      /**
       * <code>optional float float = 9;</code>
       * @return Whether the float field is set.
       */
      @Override
      public boolean hasFloat() {
        return instance.hasFloat();
      }
      /**
       * <code>optional float float = 9;</code>
       * @return The float.
       */
      @Override
      public float getFloat() {
        return instance.getFloat();
      }
      /**
       * <code>optional float float = 9;</code>
       * @param value The float to set.
       * @return This builder for chaining.
       */
      public Builder setFloat(float value) {
        copyOnWrite();
        instance.setFloat(value);
        return this;
      }
      /**
       * <code>optional float float = 9;</code>
       * @return This builder for chaining.
       */
      public Builder clearFloat() {
        copyOnWrite();
        instance.clearFloat();
        return this;
      }

      /**
       * <code>optional double double = 10;</code>
       * @return Whether the double field is set.
       */
      @Override
      public boolean hasDouble() {
        return instance.hasDouble();
      }
      /**
       * <code>optional double double = 10;</code>
       * @return The double.
       */
      @Override
      public double getDouble() {
        return instance.getDouble();
      }
      /**
       * <code>optional double double = 10;</code>
       * @param value The double to set.
       * @return This builder for chaining.
       */
      public Builder setDouble(double value) {
        copyOnWrite();
        instance.setDouble(value);
        return this;
      }
      /**
       * <code>optional double double = 10;</code>
       * @return This builder for chaining.
       */
      public Builder clearDouble() {
        copyOnWrite();
        instance.clearDouble();
        return this;
      }

      /**
       * <code>optional bool bool = 11;</code>
       * @return Whether the bool field is set.
       */
      @Override
      public boolean hasBool() {
        return instance.hasBool();
      }
      /**
       * <code>optional bool bool = 11;</code>
       * @return The bool.
       */
      @Override
      public boolean getBool() {
        return instance.getBool();
      }
      /**
       * <code>optional bool bool = 11;</code>
       * @param value The bool to set.
       * @return This builder for chaining.
       */
      public Builder setBool(boolean value) {
        copyOnWrite();
        instance.setBool(value);
        return this;
      }
      /**
       * <code>optional bool bool = 11;</code>
       * @return This builder for chaining.
       */
      public Builder clearBool() {
        copyOnWrite();
        instance.clearBool();
        return this;
      }

      /**
       * <code>optional string string = 12;</code>
       * @return Whether the string field is set.
       */
      @Override
      public boolean hasString() {
        return instance.hasString();
      }
      /**
       * <code>optional string string = 12;</code>
       * @return The string.
       */
      @Override
      public String getString() {
        return instance.getString();
      }
      /**
       * <code>optional string string = 12;</code>
       * @return The bytes for string.
       */
      @Override
      public com.google.protobuf.ByteString
          getStringBytes() {
        return instance.getStringBytes();
      }
      /**
       * <code>optional string string = 12;</code>
       * @param value The string to set.
       * @return This builder for chaining.
       */
      public Builder setString(
          String value) {
        copyOnWrite();
        instance.setString(value);
        return this;
      }
      /**
       * <code>optional string string = 12;</code>
       * @return This builder for chaining.
       */
      public Builder clearString() {
        copyOnWrite();
        instance.clearString();
        return this;
      }
      /**
       * <code>optional string string = 12;</code>
       * @param value The bytes for string to set.
       * @return This builder for chaining.
       */
      public Builder setStringBytes(
          com.google.protobuf.ByteString value) {
        copyOnWrite();
        instance.setStringBytes(value);
        return this;
      }

      /**
       * <code>optional bytes bytes = 13;</code>
       * @return Whether the bytes field is set.
       */
      @Override
      public boolean hasBytes() {
        return instance.hasBytes();
      }
      /**
       * <code>optional bytes bytes = 13;</code>
       * @return The bytes.
       */
      @Override
      public com.google.protobuf.ByteString getBytes() {
        return instance.getBytes();
      }
      /**
       * <code>optional bytes bytes = 13;</code>
       * @param value The bytes to set.
       * @return This builder for chaining.
       */
      public Builder setBytes(com.google.protobuf.ByteString value) {
        copyOnWrite();
        instance.setBytes(value);
        return this;
      }
      /**
       * <code>optional bytes bytes = 13;</code>
       * @return This builder for chaining.
       */
      public Builder clearBytes() {
        copyOnWrite();
        instance.clearBytes();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:it.auties.proto.benchmark.LiteScalarMessageMessage)
    }
    @Override
    @SuppressWarnings({"unchecked", "fallthrough"})
    protected final Object dynamicMethod(
        MethodToInvoke method,
        Object arg0, Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE: {
          return new LiteScalar.ScalarMessage();
        }
        case NEW_BUILDER: {
          return new Builder();
        }
        case BUILD_MESSAGE_INFO: {
            Object[] objects = new Object[] {
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
        // fall through
        case GET_DEFAULT_INSTANCE: {
          return DEFAULT_INSTANCE;
        }
        case GET_PARSER: {
          com.google.protobuf.Parser<LiteScalar.ScalarMessage> parser = PARSER;
          if (parser == null) {
            synchronized (LiteScalar.ScalarMessage.class) {
              parser = PARSER;
              if (parser == null) {
                parser =
                    new DefaultInstanceBasedParser<LiteScalar.ScalarMessage>(
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


    // @@protoc_insertion_point(class_scope:it.auties.proto.benchmark.LiteScalarMessageMessage)
    private static final LiteScalar.ScalarMessage DEFAULT_INSTANCE;
    static {
      ScalarMessage defaultInstance = new ScalarMessage();
      // New instances are implicitly immutable so no need to make
      // immutable.
      DEFAULT_INSTANCE = defaultInstance;
      com.google.protobuf.GeneratedMessageLite.registerDefaultInstance(
        ScalarMessage.class, defaultInstance);
    }

    public static LiteScalar.ScalarMessage getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static volatile com.google.protobuf.Parser<ScalarMessage> PARSER;

    public static com.google.protobuf.Parser<ScalarMessage> parser() {
      return DEFAULT_INSTANCE.getParserForType();
    }
  }


  static {
  }

  // @@protoc_insertion_point(outer_class_scope)
}
