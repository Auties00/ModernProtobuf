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
public class TemplateMessage implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private HydratedFourRowTemplate hydratedFourRowTemplate;

  @JsonProperty(value = "1", required = false)
  private FourRowTemplate fourRowTemplate;

  @JsonProperty(value = "4", required = false)
  private HydratedFourRowTemplate hydratedTemplate;

  @JsonProperty(value = "3", required = false)
  private ContextInfo contextInfo;

  public Format formatCase() {
    if (fourRowTemplate != null) return Format.FOUR_ROW_TEMPLATE;
    if (hydratedFourRowTemplate != null) return Format.HYDRATED_FOUR_ROW_TEMPLATE;
    return Format.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum Format implements ProtobufEnum {
    UNKNOWN(0),
    FOUR_ROW_TEMPLATE(1),
    HYDRATED_FOUR_ROW_TEMPLATE(2);

    private final @Getter int index;

    Format(int index) {
      this.index = index;
    }

    @JsonCreator
    public static Format forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(Format.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(3, ContextInfo.class),
        Map.entry(4, HydratedFourRowTemplate.class),
        Map.entry(1, FourRowTemplate.class),
        Map.entry(2, HydratedFourRowTemplate.class));
  }
}
