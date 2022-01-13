# ModernProtoc
A modern implementation of protoc to generate java sources from protobuf schemas

### What is ModernProtoc

Protoc, the default compiler for protobuf schemas, can generate classes for Java starting from a schema. 
The generated code, though, is really verbose and not up to date with modern versions of Java. 
Moreover, it is not really intended to be edited, which may be necessary if you want your code to be available to other developers. 
Keeping in mind the previous points, I decided to start this project.
The latest LTS, Java 17, and upwards is supported. 
The generated code relies only on Lombok and Jackson.
An important premise is that this library cannot currently compete with Google's implementation is terms of serialization efficiency(~5x times faster).
On the other hand, code size is largely reduced (~10x on average) and creating abstractions is much more simple.
In the future, I will try my best to improve on the efficiency side of things by, for example, reducing the use of reflection.
For now, Protobuf 3 isn't supported, but I count on adding it asap.

### Schema generation

The schema generator CLI tool is developed inside the tool module. 
It can be easily downloaded from the release tab or by compiling it manually using maven.

To get started, run the executable from any terminal passing generate as an argument:
```
Missing required parameter: '<protobuf>'
Usage: <main class> generate [-hV] [-o=<output>] [-p=<pack>] <protobuf>
Generates the java classes for a protobuf file
      <protobuf>          The protobuf file used to generate the java classes
  -h, --help              Show this help message and exit.
  -o, --output=<output>   The directory where the generated classes should be
                            outputted, by default a directory named schemas
                            will be created in the home directory
  -p, --package=<pack>    The package of the generated classes, by default none
                            is specified
  -V, --version           Print version information and exit.
```

Follow the instructions to generate the files you need. For example:
```
protoc generate ./protobufs/auth.proto --package it.auties.example --output ./src/main/java/it/auties/example
```

You can freely edit the generated schemas. The type of each protobuf field is inferred by using reflection when decoding or the json description
when encoding. If you want to override the type infer system, use the `@ProtobufType` annotation and pass the desired type as a parameter.
Obviously, the type contained in an encoded protobuf should be applicable to the new field type. 
If you want to map a particular class to another type when used as a property in a protobuf schema, apply the same procedure but to said class declaration.
This might be useful if for example you want to create a wrapper around a common property type around your schemas, but cannot modify the behaviour of the server
sending the encoded protobuf.

### Serialization

Any Protobuf object can be serialized to an array of bytes using this piece of this code:
```java
var result = ProtobufEncoder.encode(protobuf);
```

Similarly, an array of bytes can be converted to any protobuf object using this piece of this code:
```java
var result = ProtobufDecoder.forType(ProtobufObject.class)
    .decode(bytes);
```

### Example Schema

ModernProtoc(12 LOC):
```protobuf
message AdReplyInfo {
    optional string advertiserName = 1;
    enum AdReplyInfoMediaType {
        NONE = 0;
        IMAGE = 1;
        VIDEO = 2;
    }
    optional AdReplyInfoMediaType mediaType = 2;
    optional bytes jpegThumbnail = 16;
    optional string caption = 17;
}
```

Modern Protoc(42 LOC):
```java
import com.fasterxml.jackson.annotation.*;
import java.util.Arrays;
import lombok.*;
import lombok.experimental.Accessors;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Accessors(fluent = true)
public class AdReplyInfo {
  @JsonProperty(value = "1", required = false)
  private String advertiserName;

  @JsonProperty(value = "2", required = false)
  private AdReplyInfoMediaType mediaType;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "17", required = false)
  private String caption;

  @AllArgsConstructor
  @Accessors(fluent = true)
  public enum AdReplyInfoMediaType {
    NONE(0),
    IMAGE(1),
    VIDEO(2);

    @Getter
    private final int index;

    @JsonCreator
    public static AdReplyInfoMediaType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }
}
```

Google's Protoc(268 LOC):
```java
public interface AdReplyInfoOrBuilder extends
      // @@protoc_insertion_point(interface_extends:it.auties.whatsapp4j.model.AdReplyInfo)
      com.google.protobuf.MessageLiteOrBuilder {

    /**
     * <code>optional string advertiserName = 1;</code>
     * @return Whether the advertiserName field is set.
     */
    boolean hasAdvertiserName();
    /**
     * <code>optional string advertiserName = 1;</code>
     * @return The advertiserName.
     */
    java.lang.String getAdvertiserName();
    /**
     * <code>optional string advertiserName = 1;</code>
     * @return The bytes for advertiserName.
     */
    com.google.protobuf.ByteString
        getAdvertiserNameBytes();

    /**
     * <code>optional .it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType mediaType = 2;</code>
     * @return Whether the mediaType field is set.
     */
    boolean hasMediaType();
    /**
     * <code>optional .it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType mediaType = 2;</code>
     * @return The mediaType.
     */
    it.auties.whatsapp4j.model.WhatsappProtobuf.AdReplyInfo.AdReplyInfoMediaType getMediaType();

    /**
     * <code>optional bytes jpegThumbnail = 16;</code>
     * @return Whether the jpegThumbnail field is set.
     */
    boolean hasJpegThumbnail();
    /**
     * <code>optional bytes jpegThumbnail = 16;</code>
     * @return The jpegThumbnail.
     */
    com.google.protobuf.ByteString getJpegThumbnail();

    /**
     * <code>optional string caption = 17;</code>
     * @return Whether the caption field is set.
     */
    boolean hasCaption();
    /**
     * <code>optional string caption = 17;</code>
     * @return The caption.
     */
    java.lang.String getCaption();
    /**
     * <code>optional string caption = 17;</code>
     * @return The bytes for caption.
     */
    com.google.protobuf.ByteString
        getCaptionBytes();
  }
  /**
   * Protobuf type {@code it.auties.whatsapp4j.model.AdReplyInfo}
   */
  public  static final class AdReplyInfo extends
      com.google.protobuf.GeneratedMessageLite<
          AdReplyInfo, AdReplyInfo.Builder> implements
      // @@protoc_insertion_point(message_implements:it.auties.whatsapp4j.model.AdReplyInfo)
      AdReplyInfoOrBuilder {
    private AdReplyInfo() {
      advertiserName_ = "";
      jpegThumbnail_ = com.google.protobuf.ByteString.EMPTY;
      caption_ = "";
    }
    /**
     * Protobuf enum {@code it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType}
     */
    public enum AdReplyInfoMediaType
        implements com.google.protobuf.Internal.EnumLite {
      /**
       * <code>NONE = 0;</code>
       */
      NONE(0),
      /**
       * <code>IMAGE = 1;</code>
       */
      IMAGE(1),
      /**
       * <code>VIDEO = 2;</code>
       */
      VIDEO(2),
      ;

      /**
       * <code>NONE = 0;</code>
       */
      public static final int NONE_VALUE = 0;
      /**
       * <code>IMAGE = 1;</code>
       */
      public static final int IMAGE_VALUE = 1;
      /**
       * <code>VIDEO = 2;</code>
       */
      public static final int VIDEO_VALUE = 2;


      @java.lang.Override
      public final int getNumber() {
        return value;
      }

      /**
       * @param value The number of the enum to look for.
       * @return The enum associated with the given number.
       * @deprecated Use {@link #forNumber(int)} instead.
       */
      @java.lang.Deprecated
      public static AdReplyInfoMediaType valueOf(int value) {
        return forNumber(value);
      }

      public static AdReplyInfoMediaType forNumber(int value) {
        switch (value) {
          case 0: return NONE;
          case 1: return IMAGE;
          case 2: return VIDEO;
          default: return null;
        }
      }

      public static com.google.protobuf.Internal.EnumLiteMap<AdReplyInfoMediaType>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static final com.google.protobuf.Internal.EnumLiteMap<
          AdReplyInfoMediaType> internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<AdReplyInfoMediaType>() {
              @java.lang.Override
              public AdReplyInfoMediaType findValueByNumber(int number) {
                return AdReplyInfoMediaType.forNumber(number);
              }
            };

      public static com.google.protobuf.Internal.EnumVerifier 
          internalGetVerifier() {
        return AdReplyInfoMediaTypeVerifier.INSTANCE;
      }

      private static final class AdReplyInfoMediaTypeVerifier implements 
           com.google.protobuf.Internal.EnumVerifier { 
              static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new AdReplyInfoMediaTypeVerifier();
              @java.lang.Override
              public boolean isInRange(int number) {
                return AdReplyInfoMediaType.forNumber(number) != null;
              }
            };

      private final int value;

      private AdReplyInfoMediaType(int value) {
        this.value = value;
      }

      // @@protoc_insertion_point(enum_scope:it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType)
    }

 public  static final class AdReplyInfo extends
      com.google.protobuf.GeneratedMessageLite<
          AdReplyInfo, AdReplyInfo.Builder> implements
      // @@protoc_insertion_point(message_implements:it.auties.whatsapp4j.model.AdReplyInfo)
      AdReplyInfoOrBuilder {
    private AdReplyInfo() {
      advertiserName_ = "";
      jpegThumbnail_ = com.google.protobuf.ByteString.EMPTY;
      caption_ = "";
    }
    /**
     * Protobuf enum {@code it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType}
     */
    public enum AdReplyInfoMediaType
        implements com.google.protobuf.Internal.EnumLite {
      /**
       * <code>NONE = 0;</code>
       */
      NONE(0),
      /**
       * <code>IMAGE = 1;</code>
       */
      IMAGE(1),
      /**
       * <code>VIDEO = 2;</code>
       */
      VIDEO(2),
      ;

      /**
       * <code>NONE = 0;</code>
       */
      public static final int NONE_VALUE = 0;
      /**
       * <code>IMAGE = 1;</code>
       */
      public static final int IMAGE_VALUE = 1;
      /**
       * <code>VIDEO = 2;</code>
       */
      public static final int VIDEO_VALUE = 2;


      @java.lang.Override
      public final int getNumber() {
        return value;
      }

      /**
       * @param value The number of the enum to look for.
       * @return The enum associated with the given number.
       * @deprecated Use {@link #forNumber(int)} instead.
       */
      @java.lang.Deprecated
      public static AdReplyInfoMediaType valueOf(int value) {
        return forNumber(value);
      }

      public static AdReplyInfoMediaType forNumber(int value) {
        switch (value) {
          case 0: return NONE;
          case 1: return IMAGE;
          case 2: return VIDEO;
          default: return null;
        }
      }

      public static com.google.protobuf.Internal.EnumLiteMap<AdReplyInfoMediaType>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static final com.google.protobuf.Internal.EnumLiteMap<
          AdReplyInfoMediaType> internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<AdReplyInfoMediaType>() {
              @java.lang.Override
              public AdReplyInfoMediaType findValueByNumber(int number) {
                return AdReplyInfoMediaType.forNumber(number);
              }
            };

      public static com.google.protobuf.Internal.EnumVerifier 
          internalGetVerifier() {
        return AdReplyInfoMediaTypeVerifier.INSTANCE;
      }

      private static final class AdReplyInfoMediaTypeVerifier implements 
           com.google.protobuf.Internal.EnumVerifier { 
              static final com.google.protobuf.Internal.EnumVerifier           INSTANCE = new AdReplyInfoMediaTypeVerifier();
              @java.lang.Override
              public boolean isInRange(int number) {
                return AdReplyInfoMediaType.forNumber(number) != null;
              }
            };

      private final int value;

      private AdReplyInfoMediaType(int value) {
        this.value = value;
      }

      // @@protoc_insertion_point(enum_scope:it.auties.whatsapp4j.model.AdReplyInfo.AdReplyInfoMediaType)
    }
```
