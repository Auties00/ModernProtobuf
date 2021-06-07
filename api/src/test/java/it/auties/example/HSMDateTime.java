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
public class HSMDateTime implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private HSMDateTimeUnixEpoch unixEpoch;

  @JsonProperty(value = "1", required = false)
  private HSMDateTimeComponent component;

  public DatetimeOneof datetimeOneofCase() {
    if (component != null) return DatetimeOneof.COMPONENT;
    if (unixEpoch != null) return DatetimeOneof.UNIX_EPOCH;
    return DatetimeOneof.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum DatetimeOneof implements ProtobufEnum {
    UNKNOWN(0),
    COMPONENT(1),
    UNIX_EPOCH(2);

    private final @Getter int index;

    DatetimeOneof(int index) {
      this.index = index;
    }

    @JsonCreator
    public static DatetimeOneof forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(DatetimeOneof.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, HSMDateTimeComponent.class), Map.entry(2, HSMDateTimeUnixEpoch.class));
  }
}
