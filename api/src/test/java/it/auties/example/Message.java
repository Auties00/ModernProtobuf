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
public class Message implements ProtobufMessage {

  @JsonProperty(value = "32", required = false)
  private DeviceSyncMessage deviceSyncMessage;

  @JsonProperty(value = "31", required = false)
  private DeviceSentMessage deviceSentMessage;

  @JsonProperty(value = "30", required = false)
  private ProductMessage productMessage;

  @JsonProperty(value = "29", required = false)
  private TemplateButtonReplyMessage templateButtonReplyMessage;

  @JsonProperty(value = "28", required = false)
  private GroupInviteMessage groupInviteMessage;

  @JsonProperty(value = "26", required = false)
  private StickerMessage stickerMessage;

  @JsonProperty(value = "25", required = false)
  private TemplateMessage templateMessage;

  @JsonProperty(value = "24", required = false)
  private CancelPaymentRequestMessage cancelPaymentRequestMessage;

  @JsonProperty(value = "23", required = false)
  private DeclinePaymentRequestMessage declinePaymentRequestMessage;

  @JsonProperty(value = "22", required = false)
  private RequestPaymentMessage requestPaymentMessage;

  @JsonProperty(value = "18", required = false)
  private LiveLocationMessage liveLocationMessage;

  @JsonProperty(value = "16", required = false)
  private SendPaymentMessage sendPaymentMessage;

  @JsonProperty(value = "15", required = false)
  private SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage;

  @JsonProperty(value = "14", required = false)
  private HighlyStructuredMessage highlyStructuredMessage;

  @JsonProperty(value = "13", required = false)
  private ContactsArrayMessage contactsArrayMessage;

  @JsonProperty(value = "12", required = false)
  private ProtocolMessage protocolMessage;

  @JsonProperty(value = "11", required = false)
  private Chat chat;

  @JsonProperty(value = "10", required = false)
  private Call call;

  @JsonProperty(value = "9", required = false)
  private VideoMessage videoMessage;

  @JsonProperty(value = "8", required = false)
  private AudioMessage audioMessage;

  @JsonProperty(value = "7", required = false)
  private DocumentMessage documentMessage;

  @JsonProperty(value = "6", required = false)
  private ExtendedTextMessage extendedTextMessage;

  @JsonProperty(value = "5", required = false)
  private LocationMessage locationMessage;

  @JsonProperty(value = "4", required = false)
  private ContactMessage contactMessage;

  @JsonProperty(value = "3", required = false)
  private ImageMessage imageMessage;

  @JsonProperty(value = "2", required = false)
  private SenderKeyDistributionMessage senderKeyDistributionMessage;

  @JsonProperty(value = "1", required = false)
  private String conversation;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, String.class),
        Map.entry(2, SenderKeyDistributionMessage.class),
        Map.entry(3, ImageMessage.class),
        Map.entry(4, ContactMessage.class),
        Map.entry(5, LocationMessage.class),
        Map.entry(6, ExtendedTextMessage.class),
        Map.entry(7, DocumentMessage.class),
        Map.entry(8, AudioMessage.class),
        Map.entry(9, VideoMessage.class),
        Map.entry(10, Call.class),
        Map.entry(11, Chat.class),
        Map.entry(12, ProtocolMessage.class),
        Map.entry(13, ContactsArrayMessage.class),
        Map.entry(14, HighlyStructuredMessage.class),
        Map.entry(15, SenderKeyDistributionMessage.class),
        Map.entry(16, SendPaymentMessage.class),
        Map.entry(18, LiveLocationMessage.class),
        Map.entry(22, RequestPaymentMessage.class),
        Map.entry(23, DeclinePaymentRequestMessage.class),
        Map.entry(24, CancelPaymentRequestMessage.class),
        Map.entry(25, TemplateMessage.class),
        Map.entry(26, StickerMessage.class),
        Map.entry(28, GroupInviteMessage.class),
        Map.entry(29, TemplateButtonReplyMessage.class),
        Map.entry(30, ProductMessage.class),
        Map.entry(31, DeviceSentMessage.class),
        Map.entry(32, DeviceSyncMessage.class));
  }
}
