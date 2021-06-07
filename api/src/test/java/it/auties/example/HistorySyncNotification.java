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
public class HistorySyncNotification implements ProtobufMessage {

  @JsonProperty(value = "7", required = false)
  private int chunkOrder;

  @JsonProperty(value = "6", required = false)
  private HistorySyncType syncType;

  @JsonProperty(value = "5", required = false)
  private String directPath;

  @JsonProperty(value = "4", required = false)
  private byte[] fileEncSha256;

  @JsonProperty(value = "3", required = false)
  private byte[] mediaKey;

  @JsonProperty(value = "2", required = false)
  private long fileLength;

  @JsonProperty(value = "1", required = false)
  private byte[] fileSha256;

  @Accessors(fluent = true)
  public enum HistorySyncType implements ProtobufEnum {
    INITIAL_BOOTSTRAP(0),
    INITIAL_STATUS_V3(1),
    FULL(2),
    RECENT(3);

    private final @Getter int index;

    HistorySyncType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static HistorySyncType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, byte[].class),
        Map.entry(2, long.class),
        Map.entry(3, byte[].class),
        Map.entry(4, byte[].class),
        Map.entry(5, String.class),
        Map.entry(6, HistorySyncType.class),
        Map.entry(7, int.class));
  }
}
