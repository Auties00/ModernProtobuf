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
public class AdReplyInfo implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private String caption;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "2", required = false)
  private AdReplyInfoMediaType mediaType;

  @JsonProperty(value = "1", required = false)
  private String advertiserName;

  @Accessors(fluent = true)
  public enum AdReplyInfoMediaType implements ProtobufEnum {
    NONE(0),
    IMAGE(1),
    VIDEO(2);

    private final @Getter int index;

    AdReplyInfoMediaType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static AdReplyInfoMediaType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, AdReplyInfoMediaType.class),
        Map.entry(16, byte[].class),
        Map.entry(17, String.class));
  }
}
