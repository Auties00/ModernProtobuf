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
public class ProductSnapshot implements ProtobufMessage {

  @JsonProperty(value = "11", required = false)
  private String firstImageId;

  @JsonProperty(value = "9", required = false)
  private int productImageCount;

  @JsonProperty(value = "8", required = false)
  private String url;

  @JsonProperty(value = "7", required = false)
  private String retailerId;

  @JsonProperty(value = "6", required = false)
  private long priceAmount1000;

  @JsonProperty(value = "5", required = false)
  private String currencyCode;

  @JsonProperty(value = "4", required = false)
  private String description;

  @JsonProperty(value = "3", required = false)
  private String title;

  @JsonProperty(value = "2", required = false)
  private String productId;

  @JsonProperty(value = "1", required = false)
  private ImageMessage productImage;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, ImageMessage.class),
        Map.entry(2, String.class),
        Map.entry(3, String.class),
        Map.entry(4, String.class),
        Map.entry(5, String.class),
        Map.entry(6, long.class),
        Map.entry(7, String.class),
        Map.entry(8, String.class),
        Map.entry(9, int.class),
        Map.entry(11, String.class));
  }
}
