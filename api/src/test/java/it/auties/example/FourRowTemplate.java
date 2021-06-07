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
public class FourRowTemplate implements ProtobufMessage {

  @JsonProperty(value = "5", required = false)
  private LocationMessage locationMessage;

  @JsonProperty(value = "4", required = false)
  private VideoMessage videoMessage;

  @JsonProperty(value = "3", required = false)
  private ImageMessage imageMessage;

  @JsonProperty(value = "2", required = false)
  private HighlyStructuredMessage highlyStructuredMessage;

  @JsonProperty(value = "1", required = false)
  private DocumentMessage documentMessage;

  @JsonProperty(value = "8", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<TemplateButton> buttons;

  @JsonProperty(value = "7", required = false)
  private HighlyStructuredMessage footer;

  @JsonProperty(value = "6", required = false)
  private HighlyStructuredMessage content;

  public Title titleCase() {
    if (documentMessage != null) return Title.DOCUMENT_MESSAGE;
    if (highlyStructuredMessage != null) return Title.HIGHLY_STRUCTURED_MESSAGE;
    if (imageMessage != null) return Title.IMAGE_MESSAGE;
    if (videoMessage != null) return Title.VIDEO_MESSAGE;
    if (locationMessage != null) return Title.LOCATION_MESSAGE;
    return Title.UNKNOWN;
  }

  @Accessors(fluent = true)
  public enum Title implements ProtobufEnum {
    UNKNOWN(0),
    DOCUMENT_MESSAGE(1),
    HIGHLY_STRUCTURED_MESSAGE(2),
    IMAGE_MESSAGE(3),
    VIDEO_MESSAGE(4),
    LOCATION_MESSAGE(5);

    private final @Getter int index;

    Title(int index) {
      this.index = index;
    }

    @JsonCreator
    public static Title forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(Title.UNKNOWN);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(6, HighlyStructuredMessage.class),
        Map.entry(7, HighlyStructuredMessage.class),
        Map.entry(8, TemplateButton.class),
        Map.entry(1, DocumentMessage.class),
        Map.entry(2, HighlyStructuredMessage.class),
        Map.entry(3, ImageMessage.class),
        Map.entry(4, VideoMessage.class),
        Map.entry(5, LocationMessage.class));
  }
}
