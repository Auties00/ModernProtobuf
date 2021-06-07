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
public class NotificationMessageInfo implements ProtobufMessage {

  @JsonProperty(value = "4", required = false)
  private String participant;

  @JsonProperty(value = "3", required = false)
  private long messageTimestamp;

  @JsonProperty(value = "2", required = false)
  private Message message;

  @JsonProperty(value = "1", required = false)
  private MessageKey key;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, MessageKey.class),
        Map.entry(2, Message.class),
        Map.entry(3, long.class),
        Map.entry(4, String.class));
  }
}
