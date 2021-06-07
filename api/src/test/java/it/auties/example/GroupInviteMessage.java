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
public class GroupInviteMessage implements ProtobufMessage {

  @JsonProperty(value = "7", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "6", required = false)
  private String caption;

  @JsonProperty(value = "5", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "4", required = false)
  private String groupName;

  @JsonProperty(value = "3", required = false)
  private long inviteExpiration;

  @JsonProperty(value = "2", required = false)
  private String inviteCode;

  @JsonProperty(value = "1", required = false)
  private String groupJid;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, long.class),
        Map.entry(4, String.class),
        Map.entry(5, byte[].class),
        Map.entry(6, String.class),
        Map.entry(7, ContextInfo.class));
  }
}
