# ModernProtoc
A modern implementation of protoc to generate java sources from protobuf schemas

### What is ModernProtoc

Protoc, the default compiler for protobuf schemas, can generate classes for Java starting from a schema. The generated code, though, is really verbose and not up to date
with modern versions of Java. Because of this, I wrote this CLI tool that does the same exact thing, but keeping in mind code size and readability. As a result, Jackson is used to deserialize the protobuf and Lombok is used to reduce code size.

### Example
Modern Protoc(50 lines of code):
```java
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
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
  private ByteBuffer jpegThumbnail;

  @JsonProperty(value = "17", required = false)
  private String caption;

  @Accessors(fluent = true)
  public enum AdReplyInfoMediaType {
    NONE(0),
    IMAGE(1),
    VIDEO(2);

    private final @Getter int index;

    AdReplyInfoMediaType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static AdReplyInfoMediaType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElseThrow(
              () ->
                  new NoSuchElementException(
                      "Cannot deserialize AdReplyInfoMediaType from index %s".formatted(index)));
    }
  }
}
```

Google's Protobuc(279 lines of code):
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
