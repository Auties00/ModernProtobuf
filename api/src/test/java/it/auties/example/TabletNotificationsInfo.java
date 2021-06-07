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
public class TabletNotificationsInfo implements ProtobufMessage {

  @JsonProperty(value = "5", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<NotificationMessageInfo> notifyMessage;

  @JsonProperty(value = "4", required = false)
  private int notifyMessageCount;

  @JsonProperty(value = "3", required = false)
  private int unreadChats;

  @JsonProperty(value = "2", required = false)
  private long timestamp;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(2, long.class),
        Map.entry(3, int.class),
        Map.entry(4, int.class),
        Map.entry(5, NotificationMessageInfo.class));
  }
}
