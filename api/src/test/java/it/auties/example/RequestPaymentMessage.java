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
public class RequestPaymentMessage implements ProtobufMessage {

  @JsonProperty(value = "5", required = false)
  private long expiryTimestamp;

  @JsonProperty(value = "3", required = false)
  private String requestFrom;

  @JsonProperty(value = "2", required = false)
  private long amount1000;

  @JsonProperty(value = "1", required = false)
  private String currencyCodeIso4217;

  @JsonProperty(value = "4", required = false)
  private Message noteMessage;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(4, Message.class),
        Map.entry(1, String.class),
        Map.entry(2, long.class),
        Map.entry(3, String.class),
        Map.entry(5, long.class));
  }
}
