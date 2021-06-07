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
public class CatalogSnapshot implements ProtobufMessage {

  @JsonProperty(value = "3", required = false)
  private String description;

  @JsonProperty(value = "2", required = false)
  private String title;

  @JsonProperty(value = "1", required = false)
  private ImageMessage catalogImage;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, ImageMessage.class), Map.entry(2, String.class), Map.entry(3, String.class));
  }
}
