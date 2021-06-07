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
public class HydratedTemplateButton implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private HydratedCallButton callButton;

  @JsonProperty(value = "2", required = false)
  private HydratedURLButton urlButton;

  @JsonProperty(value = "1", required = false)
  private HydratedQuickReplyButton quickReplyButton;

  @JsonProperty(value = "4", required = false)
  private int index;

  public HydratedButton hydratedButtonCase() {
    if (quickReplyButton != null) return HydratedButton.QUICK_REPLY_BUTTON;
    if (urlButton != null) return HydratedButton.URL_BUTTON;
    if (callButton != null) return HydratedButton.CALL_BUTTON;
    return HydratedButton.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum HydratedButton implements ProtobufEnum {
    UNKNOWN(0),
    QUICK_REPLY_BUTTON(1),
    URL_BUTTON(2),
    CALL_BUTTON(3);

    private final @Getter int index;

    HydratedButton(int index) {
      this.index = index;
    }

    @JsonCreator
    public static HydratedButton forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(HydratedButton.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(4, int.class),
        Map.entry(1, HydratedQuickReplyButton.class),
        Map.entry(2, HydratedURLButton.class),
        Map.entry(3, HydratedCallButton.class));
  }
}
