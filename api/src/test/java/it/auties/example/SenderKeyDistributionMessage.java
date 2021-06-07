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
public class SenderKeyDistributionMessage implements ProtobufMessage {

  @JsonProperty(value = "2", required = false)
  private byte[] axolotlSenderKeyDistributionMessage;

  @JsonProperty(value = "1", required = false)
  private String groupId;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(1, String.class), Map.entry(2, byte[].class));
  }
}
