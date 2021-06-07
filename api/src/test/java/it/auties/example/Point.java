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
public class Point implements ProtobufMessage {

  @JsonProperty(value = "4", required = false)
  private double y;

  @JsonProperty(value = "3", required = false)
  private double x;

  @JsonProperty(value = "2", required = false)
  private int yDeprecated;

  @JsonProperty(value = "1", required = false)
  private int xDeprecated;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, int.class),
        Map.entry(2, int.class),
        Map.entry(3, double.class),
        Map.entry(4, double.class));
  }
}
