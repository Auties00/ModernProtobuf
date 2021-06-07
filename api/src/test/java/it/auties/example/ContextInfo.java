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
public class ContextInfo implements ProtobufMessage {

  @JsonProperty(value = "26", required = false)
  private long ephemeralSettingTimestamp;

  @JsonProperty(value = "25", required = false)
  private int expiration;

  @JsonProperty(value = "24", required = false)
  private MessageKey placeholderKey;

  @JsonProperty(value = "23", required = false)
  private AdReplyInfo quotedAd;

  @JsonProperty(value = "22", required = false)
  private boolean isForwarded;

  @JsonProperty(value = "21", required = false)
  private int forwardingScore;

  @JsonProperty(value = "20", required = false)
  private int conversionDelaySeconds;

  @JsonProperty(value = "19", required = false)
  private byte[] conversionData;

  @JsonProperty(value = "18", required = false)
  private String conversionSource;

  @JsonProperty(value = "15", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<String> mentionedJid;

  @JsonProperty(value = "4", required = false)
  private String remoteJid;

  @JsonProperty(value = "3", required = false)
  private Message quotedMessage;

  @JsonProperty(value = "2", required = false)
  private String participant;

  @JsonProperty(value = "1", required = false)
  private String stanzaId;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, String.class),
        Map.entry(3, Message.class),
        Map.entry(4, String.class),
        Map.entry(15, String.class),
        Map.entry(18, String.class),
        Map.entry(19, byte[].class),
        Map.entry(20, int.class),
        Map.entry(21, int.class),
        Map.entry(22, boolean.class),
        Map.entry(23, AdReplyInfo.class),
        Map.entry(24, MessageKey.class),
        Map.entry(25, int.class),
        Map.entry(26, long.class));
  }
}
