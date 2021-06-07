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
public class HSMLocalizableParameter implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private HSMDateTime dateTime;

  @JsonProperty(value = "2", required = false)
  private HSMCurrency currency;

  @JsonProperty(value = "1", required = false)
  private String _default;

  public ParamOneof paramOneofCase() {
    if (currency != null) return ParamOneof.CURRENCY;
    if (dateTime != null) return ParamOneof.DATE_TIME;
    return ParamOneof.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum ParamOneof implements ProtobufEnum {
    UNKNOWN(0),
    CURRENCY(2),
    DATE_TIME(3);

    private final @Getter int index;

    ParamOneof(int index) {
      this.index = index;
    }

    @JsonCreator
    public static ParamOneof forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(ParamOneof.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, HSMCurrency.class),
        Map.entry(3, HSMDateTime.class));
  }
}
