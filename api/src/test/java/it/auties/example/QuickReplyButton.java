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
public class QuickReplyButton implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private String id;

  @JsonProperty(value = "1", required = false)
  private HighlyStructuredMessage displayText;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(1, HighlyStructuredMessage.class), Map.entry(2, String.class));
  }
}
