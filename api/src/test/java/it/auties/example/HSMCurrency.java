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
public class HSMCurrency implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private long amount1000;

  @JsonProperty(value = "1", required = false)
  private String currencyCode;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(1, String.class), Map.entry(2, long.class));
  }
}
