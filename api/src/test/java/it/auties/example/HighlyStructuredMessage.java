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
public class HighlyStructuredMessage implements ProtobufMessage {

  @JsonProperty(value = "9", required = false)
  private TemplateMessage hydratedHsm;

  @JsonProperty(value = "8", required = false)
  private String deterministicLc;

  @JsonProperty(value = "7", required = false)
  private String deterministicLg;

  @JsonProperty(value = "6", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<HSMLocalizableParameter> localizableParams;

  @JsonProperty(value = "5", required = false)
  private String fallbackLc;

  @JsonProperty(value = "4", required = false)
  private String fallbackLg;

  @JsonProperty(value = "3", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<String> params;

  @JsonProperty(value = "2", required = false)
  private String elementName;

  @JsonProperty(value = "1", required = false)
  private String namespace;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, String.class),
        Map.entry(4, String.class),
        Map.entry(5, String.class),
        Map.entry(6, HSMLocalizableParameter.class),
        Map.entry(7, String.class),
        Map.entry(8, String.class),
        Map.entry(9, TemplateMessage.class));
  }
}
