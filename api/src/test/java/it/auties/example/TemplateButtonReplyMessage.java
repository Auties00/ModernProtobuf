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
public class TemplateButtonReplyMessage implements ProtobufMessage {

  @JsonProperty(value = "4", required = false)
  private int selectedIndex;

  @JsonProperty(value = "3", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "2", required = false)
  private String selectedDisplayText;

  @JsonProperty(value = "1", required = false)
  private String selectedId;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, ContextInfo.class),
        Map.entry(4, int.class));
  }
}
