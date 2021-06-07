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
public class InteractiveAnnotation implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private Location location;

  @JsonProperty(value = "1", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<Point> polygonVertices;

  public Action actionCase() {
    if (location != null) return Action.LOCATION;
    return Action.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum Action implements ProtobufEnum {
    UNKNOWN(0),
    LOCATION(2);

    private final @Getter int index;

    Action(int index) {
      this.index = index;
    }

    @JsonCreator
    public static Action forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(Action.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(1, Point.class), Map.entry(2, Location.class));
  }
}
