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
public class ImageMessage implements ProtobufMessage {

  @JsonProperty(value = "24", required = false)
  private byte[] midQualityFileEncSha256;

  @JsonProperty(value = "23", required = false)
  private byte[] midQualityFileSha256;

  @JsonProperty(value = "22", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<Integer> scanLengths;

  @JsonProperty(value = "21", required = false)
  private byte[] scansSidecar;

  @JsonProperty(value = "20", required = false)
  private int experimentGroupId;

  @JsonProperty(value = "19", required = false)
  private int firstScanLength;

  @JsonProperty(value = "18", required = false)
  private byte[] firstScanSidecar;

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "12", required = false)
  private long mediaKeyTimestamp;

  @JsonProperty(value = "11", required = false)
  private String directPath;

  @JsonProperty(value = "10", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<InteractiveAnnotation> interactiveAnnotations;

  @JsonProperty(value = "9", required = false)
  private byte[] fileEncSha256;

  @JsonProperty(value = "8", required = false)
  private byte[] mediaKey;

  @JsonProperty(value = "7", required = false)
  private int width;

  @JsonProperty(value = "6", required = false)
  private int height;

  @JsonProperty(value = "5", required = false)
  private long fileLength;

  @JsonProperty(value = "4", required = false)
  private byte[] fileSha256;

  @JsonProperty(value = "3", required = false)
  private String caption;

  @JsonProperty(value = "2", required = false)
  private String mimetype;

  @JsonProperty(value = "1", required = false)
  private String url;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, String.class),
        Map.entry(4, byte[].class),
        Map.entry(5, long.class),
        Map.entry(6, int.class),
        Map.entry(7, int.class),
        Map.entry(8, byte[].class),
        Map.entry(9, byte[].class),
        Map.entry(10, InteractiveAnnotation.class),
        Map.entry(11, String.class),
        Map.entry(12, long.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class),
        Map.entry(18, byte[].class),
        Map.entry(19, int.class),
        Map.entry(20, int.class),
        Map.entry(21, byte[].class),
        Map.entry(22, int.class),
        Map.entry(23, byte[].class),
        Map.entry(24, byte[].class));
  }
}
