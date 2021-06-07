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
public class ContactsArrayMessage implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "2", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<ContactMessage> contacts;

  @JsonProperty(value = "1", required = false)
  private String displayName;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, ContactMessage.class),
        Map.entry(17, ContextInfo.class));
  }
}
