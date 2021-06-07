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
public class SendPaymentMessage implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private MessageKey requestMessageKey;

  @JsonProperty(value = "2", required = false)
  private Message noteMessage;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(2, Message.class), Map.entry(3, MessageKey.class));
  }
}
