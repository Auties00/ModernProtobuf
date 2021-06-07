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
public class StickerMessage implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] pngThumbnail;

  @JsonProperty(value = "13", required = false)
  private boolean isAnimated;

  @JsonProperty(value = "12", required = false)
  private byte[] firstFrameSidecar;

  @JsonProperty(value = "11", required = false)
  private int firstFrameLength;

  @JsonProperty(value = "10", required = false)
  private long mediaKeyTimestamp;

  @JsonProperty(value = "9", required = false)
  private long fileLength;

  @JsonProperty(value = "8", required = false)
  private String directPath;

  @JsonProperty(value = "7", required = false)
  private int width;

  @JsonProperty(value = "6", required = false)
  private int height;

  @JsonProperty(value = "5", required = false)
  private String mimetype;

  @JsonProperty(value = "4", required = false)
  private byte[] mediaKey;

  @JsonProperty(value = "3", required = false)
  private byte[] fileEncSha256;

  @JsonProperty(value = "2", required = false)
  private byte[] fileSha256;

  @JsonProperty(value = "1", required = false)
  private String url;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, byte[].class),
        Map.entry(3, byte[].class),
        Map.entry(4, byte[].class),
        Map.entry(5, String.class),
        Map.entry(6, int.class),
        Map.entry(7, int.class),
        Map.entry(8, String.class),
        Map.entry(9, long.class),
        Map.entry(10, long.class),
        Map.entry(11, int.class),
        Map.entry(12, byte[].class),
        Map.entry(13, boolean.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class));
  }
}
