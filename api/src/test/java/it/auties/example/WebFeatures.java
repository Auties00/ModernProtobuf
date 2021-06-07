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
public class WebFeatures implements ProtobufMessage {

  @JsonProperty(value = "34", required = false)
  private WebFeaturesFlag recentStickersV2;

  @JsonProperty(value = "33", required = false)
  private WebFeaturesFlag e2ENotificationSync;

  @JsonProperty(value = "32", required = false)
  private WebFeaturesFlag ephemeralMessages;

  @JsonProperty(value = "31", required = false)
  private WebFeaturesFlag templateMessageInteractivity;

  @JsonProperty(value = "30", required = false)
  private WebFeaturesFlag templateMessage;

  @JsonProperty(value = "29", required = false)
  private WebFeaturesFlag voipGroupCall;

  @JsonProperty(value = "28", required = false)
  private WebFeaturesFlag starredStickers;

  @JsonProperty(value = "27", required = false)
  private WebFeaturesFlag catalog;

  @JsonProperty(value = "26", required = false)
  private WebFeaturesFlag recentStickers;

  @JsonProperty(value = "25", required = false)
  private WebFeaturesFlag groupsV4JoinPermission;

  @JsonProperty(value = "24", required = false)
  private WebFeaturesFlag frequentlyForwardedSetting;

  @JsonProperty(value = "23", required = false)
  private WebFeaturesFlag thirdPartyStickers;

  @JsonProperty(value = "22", required = false)
  private WebFeaturesFlag voipIndividualVideo;

  @JsonProperty(value = "21", required = false)
  private WebFeaturesFlag statusRanking;

  @JsonProperty(value = "20", required = false)
  private WebFeaturesFlag videoPlaybackUrl;

  @JsonProperty(value = "19", required = false)
  private WebFeaturesFlag vnameV2;

  @JsonProperty(value = "18", required = false)
  private WebFeaturesFlag mediaUploadRichQuickReplies;

  @JsonProperty(value = "15", required = false)
  private WebFeaturesFlag mediaUpload;

  @JsonProperty(value = "14", required = false)
  private WebFeaturesFlag labelsEdit;

  @JsonProperty(value = "13", required = false)
  private WebFeaturesFlag liveLocationsFinal;

  @JsonProperty(value = "12", required = false)
  private WebFeaturesFlag stickerPackQuery;

  @JsonProperty(value = "11", required = false)
  private WebFeaturesFlag payments;

  @JsonProperty(value = "10", required = false)
  private WebFeaturesFlag quickRepliesQuery;

  @JsonProperty(value = "9", required = false)
  private WebFeaturesFlag voipIndividualIncoming;

  @JsonProperty(value = "8", required = false)
  private WebFeaturesFlag queryVname;

  @JsonProperty(value = "7", required = false)
  private WebFeaturesFlag liveLocations;

  @JsonProperty(value = "6", required = false)
  private WebFeaturesFlag queryStatusV3Thumbnail;

  @JsonProperty(value = "5", required = false)
  private WebFeaturesFlag changeNumberV2;

  @JsonProperty(value = "4", required = false)
  private WebFeaturesFlag groupsV3Create;

  @JsonProperty(value = "3", required = false)
  private WebFeaturesFlag groupsV3;

  @JsonProperty(value = "2", required = false)
  private WebFeaturesFlag voipIndividualOutgoing;

  @JsonProperty(value = "1", required = false)
  private WebFeaturesFlag labelsDisplay;

  @Accessors(fluent = true)
  public enum WebFeaturesFlag implements ProtobufEnum {
    NOT_STARTED(0),
    FORCE_UPGRADE(1),
    DEVELOPMENT(2),
    PRODUCTION(3);

    private final @Getter int index;

    WebFeaturesFlag(int index) {
      this.index = index;
    }

    @JsonCreator
    public static WebFeaturesFlag forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, WebFeaturesFlag.class),
        Map.entry(2, WebFeaturesFlag.class),
        Map.entry(3, WebFeaturesFlag.class),
        Map.entry(4, WebFeaturesFlag.class),
        Map.entry(5, WebFeaturesFlag.class),
        Map.entry(6, WebFeaturesFlag.class),
        Map.entry(7, WebFeaturesFlag.class),
        Map.entry(8, WebFeaturesFlag.class),
        Map.entry(9, WebFeaturesFlag.class),
        Map.entry(10, WebFeaturesFlag.class),
        Map.entry(11, WebFeaturesFlag.class),
        Map.entry(12, WebFeaturesFlag.class),
        Map.entry(13, WebFeaturesFlag.class),
        Map.entry(14, WebFeaturesFlag.class),
        Map.entry(15, WebFeaturesFlag.class),
        Map.entry(18, WebFeaturesFlag.class),
        Map.entry(19, WebFeaturesFlag.class),
        Map.entry(20, WebFeaturesFlag.class),
        Map.entry(21, WebFeaturesFlag.class),
        Map.entry(22, WebFeaturesFlag.class),
        Map.entry(23, WebFeaturesFlag.class),
        Map.entry(24, WebFeaturesFlag.class),
        Map.entry(25, WebFeaturesFlag.class),
        Map.entry(26, WebFeaturesFlag.class),
        Map.entry(27, WebFeaturesFlag.class),
        Map.entry(28, WebFeaturesFlag.class),
        Map.entry(29, WebFeaturesFlag.class),
        Map.entry(30, WebFeaturesFlag.class),
        Map.entry(31, WebFeaturesFlag.class),
        Map.entry(32, WebFeaturesFlag.class),
        Map.entry(33, WebFeaturesFlag.class),
        Map.entry(34, WebFeaturesFlag.class));
  }
}
