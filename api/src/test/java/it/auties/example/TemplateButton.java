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
public class TemplateButton implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private CallButton callButton;

  @JsonProperty(value = "2", required = false)
  private URLButton urlButton;

  @JsonProperty(value = "1", required = false)
  private QuickReplyButton quickReplyButton;

  @JsonProperty(value = "4", required = false)
  private int index;

  public Button buttonCase() {
    if (quickReplyButton != null) return Button.QUICK_REPLY_BUTTON;
    if (urlButton != null) return Button.URL_BUTTON;
    if (callButton != null) return Button.CALL_BUTTON;
    return Button.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum Button implements ProtobufEnum {
    UNKNOWN(0),
    QUICK_REPLY_BUTTON(1),
    URL_BUTTON(2),
    CALL_BUTTON(3);

    private final @Getter int index;

    Button(int index) {
      this.index = index;
    }

    @JsonCreator
    public static Button forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(Button.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(4, int.class),
        Map.entry(1, QuickReplyButton.class),
        Map.entry(2, URLButton.class),
        Map.entry(3, CallButton.class));
  }
}
