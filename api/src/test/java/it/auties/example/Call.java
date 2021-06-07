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
public class Call implements ProtobufMessage {

  @JsonProperty(value = "1", required = false)
  private byte[] callKey;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(Map.entry(1, byte[].class));
  }
}
