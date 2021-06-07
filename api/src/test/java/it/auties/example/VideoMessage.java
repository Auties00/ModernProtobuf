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
public class VideoMessage implements ProtobufMessage {

  @JsonProperty(value = "19", required = false)
  private VideoMessageAttribution gifAttribution;

  @JsonProperty(value = "18", required = false)
  private byte[] streamingSidecar;

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "14", required = false)
  private long mediaKeyTimestamp;

  @JsonProperty(value = "13", required = false)
  private String directPath;

  @JsonProperty(value = "12", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<InteractiveAnnotation> interactiveAnnotations;

  @JsonProperty(value = "11", required = false)
  private byte[] fileEncSha256;

  @JsonProperty(value = "10", required = false)
  private int width;

  @JsonProperty(value = "9", required = false)
  private int height;

  @JsonProperty(value = "8", required = false)
  private boolean gifPlayback;

  @JsonProperty(value = "7", required = false)
  private String caption;

  @JsonProperty(value = "6", required = false)
  private byte[] mediaKey;

  @JsonProperty(value = "5", required = false)
  private int seconds;

  @JsonProperty(value = "4", required = false)
  private long fileLength;

  @JsonProperty(value = "3", required = false)
  private byte[] fileSha256;

  @JsonProperty(value = "2", required = false)
  private String mimetype;

  @JsonProperty(value = "1", required = false)
  private String url;

  @Accessors(fluent = true)
  public enum VideoMessageAttribution implements ProtobufEnum {
    NONE(0),
    GIPHY(1),
    TENOR(2);

    private final @Getter int index;

    VideoMessageAttribution(int index) {
      this.index = index;
    }

    @JsonCreator
    public static VideoMessageAttribution forIndex(int index) {
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
        Map.entry(3, byte[].class),
        Map.entry(4, long.class),
        Map.entry(5, int.class),
        Map.entry(6, byte[].class),
        Map.entry(7, String.class),
        Map.entry(8, boolean.class),
        Map.entry(9, int.class),
        Map.entry(10, int.class),
        Map.entry(11, byte[].class),
        Map.entry(12, InteractiveAnnotation.class),
        Map.entry(13, String.class),
        Map.entry(14, long.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class),
        Map.entry(18, byte[].class),
        Map.entry(19, VideoMessageAttribution.class));
  }
}
