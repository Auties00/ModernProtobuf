package it.auties.example;

import com.fasterxml.jackson.annotation.*;
import it.auties.protobuf.model.*;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Accessors(fluent = true)
public class ExtendedTextMessage implements ProtobufMessage {

  @JsonProperty(value = "18", required = false)
  private boolean doNotPlayInline;

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "10", required = false)
  private ExtendedTextMessagePreviewType previewType;

  @JsonProperty(value = "9", required = false)
  private ExtendedTextMessageFontType font;

  @JsonProperty(value = "8", required = false)
  private int backgroundArgb;

  @JsonProperty(value = "7", required = false)
  private int textArgb;

  @JsonProperty(value = "6", required = false)
  private String title;

  @JsonProperty(value = "5", required = false)
  private String description;

  @JsonProperty(value = "4", required = false)
  private String canonicalUrl;

  @JsonProperty(value = "2", required = false)
  private String matchedText;

  @JsonProperty(value = "1", required = false)
  private String text;

  @Accessors(fluent = true)
  public enum ExtendedTextMessageFontType implements ProtobufEnum {
    SANS_SERIF(0),
    SERIF(1),
    NORICAN_REGULAR(2),
    BRYNDAN_WRITE(3),
    BEBASNEUE_REGULAR(4),
    OSWALD_HEAVY(5);

    private final @Getter int index;

    ExtendedTextMessageFontType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static ExtendedTextMessageFontType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Accessors(fluent = true)
  public enum ExtendedTextMessagePreviewType implements ProtobufEnum {
    NONE(0),
    VIDEO(1);

    private final @Getter int index;

    ExtendedTextMessagePreviewType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static ExtendedTextMessagePreviewType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(4, String.class),
        Map.entry(5, String.class),
        Map.entry(6, String.class),
        Map.entry(7, int.class),
        Map.entry(8, int.class),
        Map.entry(9, ExtendedTextMessageFontType.class),
        Map.entry(10, ExtendedTextMessagePreviewType.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class),
        Map.entry(18, boolean.class));
  }
}
