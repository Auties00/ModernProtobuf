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
public class MessageKey implements ProtobufMessage {

  @JsonProperty(value = "4", required = false)
  private String participant;

  @JsonProperty(value = "3", required = false)
  private String id;

  @JsonProperty(value = "2", required = false)
  private boolean fromMe;

  @JsonProperty(value = "1", required = false)
  private String remoteJid;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, boolean.class),
        Map.entry(3, String.class),
        Map.entry(4, String.class));
  }
}
