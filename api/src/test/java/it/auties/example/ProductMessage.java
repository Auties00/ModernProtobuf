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
public class ProductMessage implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "4", required = false)
  private CatalogSnapshot catalog;

  @JsonProperty(value = "2", required = false)
  private String businessOwnerJid;

  @JsonProperty(value = "1", required = false)
  private ProductSnapshot product;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, ProductSnapshot.class),
        Map.entry(2, String.class),
        Map.entry(4, CatalogSnapshot.class),
        Map.entry(17, ContextInfo.class));
  }
}
