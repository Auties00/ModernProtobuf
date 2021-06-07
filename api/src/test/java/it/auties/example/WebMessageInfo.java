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
public class WebMessageInfo implements ProtobufMessage {

  @JsonProperty(value = "33", required = false)
  private int ephemeralDuration;

  @JsonProperty(value = "32", required = false)
  private long ephemeralStartTimestamp;

  @JsonProperty(value = "31", required = false)
  private PaymentInfo quotedPaymentInfo;

  @JsonProperty(value = "30", required = false)
  private LiveLocationMessage finalLiveLocation;

  @JsonProperty(value = "29", required = false)
  private PaymentInfo paymentInfo;

  @JsonProperty(value = "28", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<String> labels;

  @JsonProperty(value = "27", required = false)
  private int duration;

  @JsonProperty(value = "26", required = false)
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<String> messageStubParameters;

  @JsonProperty(value = "25", required = false)
  private boolean clearMedia;

  @JsonProperty(value = "24", required = false)
  private WebMessageInfoStubType messageStubType;

  @JsonProperty(value = "23", required = false)
  private boolean urlNumber;

  @JsonProperty(value = "22", required = false)
  private boolean urlText;

  @JsonProperty(value = "21", required = false)
  private boolean multicast;

  @JsonProperty(value = "20", required = false)
  private byte[] mediaCiphertextSha256;

  @JsonProperty(value = "19", required = false)
  private String pushName;

  @JsonProperty(value = "18", required = false)
  private boolean broadcast;

  @JsonProperty(value = "17", required = false)
  private boolean starred;

  @JsonProperty(value = "16", required = false)
  private boolean ignore;

  @JsonProperty(value = "5", required = false)
  private String participant;

  @JsonProperty(value = "4", required = false)
  private WebMessageInfoStatus status;

  @JsonProperty(value = "3", required = false)
  private long messageTimestamp;

  @JsonProperty(value = "2", required = false)
  private Message message;

  @JsonProperty(value = "1", required = true)
  private MessageKey key;

  @Accessors(fluent = true)
  public enum WebMessageInfoStatus implements ProtobufEnum {
    ERROR(0),
    PENDING(1),
    SERVER_ACK(2),
    DELIVERY_ACK(3),
    READ(4),
    PLAYED(5);

    private final @Getter int index;

    WebMessageInfoStatus(int index) {
      this.index = index;
    }

    @JsonCreator
    public static WebMessageInfoStatus forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Accessors(fluent = true)
  public enum WebMessageInfoStubType implements ProtobufEnum {
    UNKNOWN(0),
    REVOKE(1),
    CIPHERTEXT(2),
    FUTUREPROOF(3),
    NON_VERIFIED_TRANSITION(4),
    UNVERIFIED_TRANSITION(5),
    VERIFIED_TRANSITION(6),
    VERIFIED_LOW_UNKNOWN(7),
    VERIFIED_HIGH(8),
    VERIFIED_INITIAL_UNKNOWN(9),
    VERIFIED_INITIAL_LOW(10),
    VERIFIED_INITIAL_HIGH(11),
    VERIFIED_TRANSITION_ANY_TO_NONE(12),
    VERIFIED_TRANSITION_ANY_TO_HIGH(13),
    VERIFIED_TRANSITION_HIGH_TO_LOW(14),
    VERIFIED_TRANSITION_HIGH_TO_UNKNOWN(15),
    VERIFIED_TRANSITION_UNKNOWN_TO_LOW(16),
    VERIFIED_TRANSITION_LOW_TO_UNKNOWN(17),
    VERIFIED_TRANSITION_NONE_TO_LOW(18),
    VERIFIED_TRANSITION_NONE_TO_UNKNOWN(19),
    GROUP_CREATE(20),
    GROUP_CHANGE_SUBJECT(21),
    GROUP_CHANGE_ICON(22),
    GROUP_CHANGE_INVITE_LINK(23),
    GROUP_CHANGE_DESCRIPTION(24),
    GROUP_CHANGE_RESTRICT(25),
    GROUP_CHANGE_ANNOUNCE(26),
    GROUP_PARTICIPANT_ADD(27),
    GROUP_PARTICIPANT_REMOVE(28),
    GROUP_PARTICIPANT_PROMOTE(29),
    GROUP_PARTICIPANT_DEMOTE(30),
    GROUP_PARTICIPANT_INVITE(31),
    GROUP_PARTICIPANT_LEAVE(32),
    GROUP_PARTICIPANT_CHANGE_NUMBER(33),
    BROADCAST_CREATE(34),
    BROADCAST_ADD(35),
    BROADCAST_REMOVE(36),
    GENERIC_NOTIFICATION(37),
    E2E_IDENTITY_CHANGED(38),
    E2E_ENCRYPTED(39),
    CALL_MISSED_VOICE(40),
    CALL_MISSED_VIDEO(41),
    INDIVIDUAL_CHANGE_NUMBER(42),
    GROUP_DELETE(43),
    GROUP_ANNOUNCE_MODE_MESSAGE_BOUNCE(44),
    CALL_MISSED_GROUP_VOICE(45),
    CALL_MISSED_GROUP_VIDEO(46),
    PAYMENT_CIPHERTEXT(47),
    PAYMENT_FUTUREPROOF(48),
    PAYMENT_TRANSACTION_STATUS_UPDATE_FAILED(49),
    PAYMENT_TRANSACTION_STATUS_UPDATE_REFUNDED(50),
    PAYMENT_TRANSACTION_STATUS_UPDATE_REFUND_FAILED(51),
    PAYMENT_TRANSACTION_STATUS_RECEIVER_PENDING_SETUP(52),
    PAYMENT_TRANSACTION_STATUS_RECEIVER_SUCCESS_AFTER_HICCUP(53),
    PAYMENT_ACTION_ACCOUNT_SETUP_REMINDER(54),
    PAYMENT_ACTION_SEND_PAYMENT_REMINDER(55),
    PAYMENT_ACTION_SEND_PAYMENT_INVITATION(56),
    PAYMENT_ACTION_REQUEST_DECLINED(57),
    PAYMENT_ACTION_REQUEST_EXPIRED(58),
    PAYMENT_ACTION_REQUEST_CANCELLED(59),
    BIZ_VERIFIED_TRANSITION_TOP_TO_BOTTOM(60),
    BIZ_VERIFIED_TRANSITION_BOTTOM_TO_TOP(61),
    BIZ_INTRO_TOP(62),
    BIZ_INTRO_BOTTOM(63),
    BIZ_NAME_CHANGE(64),
    BIZ_MOVE_TO_CONSUMER_APP(65),
    BIZ_TWO_TIER_MIGRATION_TOP(66),
    BIZ_TWO_TIER_MIGRATION_BOTTOM(67),
    OVERSIZED(68),
    GROUP_CHANGE_NO_FREQUENTLY_FORWARDED(69),
    GROUP_V4_ADD_INVITE_SENT(70),
    GROUP_PARTICIPANT_ADD_REQUEST_JOIN(71),
    CHANGE_EPHEMERAL_SETTING(72);

    private final @Getter int index;

    WebMessageInfoStubType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static WebMessageInfoStubType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, MessageKey.class),
        Map.entry(2, Message.class),
        Map.entry(3, long.class),
        Map.entry(4, WebMessageInfoStatus.class),
        Map.entry(5, String.class),
        Map.entry(16, boolean.class),
        Map.entry(17, boolean.class),
        Map.entry(18, boolean.class),
        Map.entry(19, String.class),
        Map.entry(20, byte[].class),
        Map.entry(21, boolean.class),
        Map.entry(22, boolean.class),
        Map.entry(23, boolean.class),
        Map.entry(24, WebMessageInfoStubType.class),
        Map.entry(25, boolean.class),
        Map.entry(26, String.class),
        Map.entry(27, int.class),
        Map.entry(28, String.class),
        Map.entry(29, PaymentInfo.class),
        Map.entry(30, LiveLocationMessage.class),
        Map.entry(31, PaymentInfo.class),
        Map.entry(32, long.class),
        Map.entry(33, int.class));
  }
}
