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
public class AudioMessage implements ProtobufMessage {

  @JsonProperty(value = "18", required = false)
  private byte[] streamingSidecar;

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "10", required = false)
  private long mediaKeyTimestamp;

  @JsonProperty(value = "9", required = false)
  private String directPath;

  @JsonProperty(value = "8", required = false)
  private byte[] fileEncSha256;

  @JsonProperty(value = "7", required = false)
  private byte[] mediaKey;

  @JsonProperty(value = "6", required = false)
  private boolean ptt;

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

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, byte[].class),
        Map.entry(4, long.class),
        Map.entry(5, int.class),
        Map.entry(6, boolean.class),
        Map.entry(7, byte[].class),
        Map.entry(8, byte[].class),
        Map.entry(9, String.class),
        Map.entry(10, long.class),
        Map.entry(17, ContextInfo.class),
        Map.entry(18, byte[].class));
  }
}
