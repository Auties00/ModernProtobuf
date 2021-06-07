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
public class Location implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private String name;

  @JsonProperty(value = "2", required = false)
  private double degreesLongitude;

  @JsonProperty(value = "1", required = false)
  private double degreesLatitude;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, double.class), Map.entry(2, double.class), Map.entry(3, String.class));
  }
}
