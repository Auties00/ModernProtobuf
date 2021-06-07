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
public class ProtocolMessage implements ProtobufMessage {

  @JsonProperty(value = "6", required = false)
  private HistorySyncNotification historySyncNotification;

  @JsonProperty(value = "5", required = false)
  private long ephemeralSettingTimestamp;

  @JsonProperty(value = "4", required = false)
  private int ephemeralExpiration;

  @JsonProperty(value = "2", required = false)
  private ProtocolMessageType type;

  @JsonProperty(value = "1", required = false)
  private MessageKey key;

  @Accessors(fluent = true)
  public enum ProtocolMessageType implements ProtobufEnum {
    REVOKE(0),
    EPHEMERAL_SETTING(3),
    EPHEMERAL_SYNC_RESPONSE(4),
    HISTORY_SYNC_NOTIFICATION(5);

    private final @Getter int index;

    ProtocolMessageType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static ProtocolMessageType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, MessageKey.class),
        Map.entry(2, ProtocolMessageType.class),
        Map.entry(4, int.class),
        Map.entry(5, long.class),
        Map.entry(6, HistorySyncNotification.class));
  }
}
