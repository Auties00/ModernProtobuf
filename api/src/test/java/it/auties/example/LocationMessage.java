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
public class LocationMessage implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "11", required = false)
  private String comment;

  @JsonProperty(value = "9", required = false)
  private int degreesClockwiseFromMagneticNorth;

  @JsonProperty(value = "8", required = false)
  private float speedInMps;

  @JsonProperty(value = "7", required = false)
  private int accuracyInMeters;

  @JsonProperty(value = "6", required = false)
  private boolean isLive;

  @JsonProperty(value = "5", required = false)
  private String url;

  @JsonProperty(value = "4", required = false)
  private String address;

  @JsonProperty(value = "3", required = false)
  private String name;

  @JsonProperty(value = "2", required = false)
  private double degreesLongitude;

  @JsonProperty(value = "1", required = false)
  private double degreesLatitude;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, double.class),
        Map.entry(2, double.class),
        Map.entry(3, String.class),
        Map.entry(4, String.class),
        Map.entry(5, String.class),
        Map.entry(6, boolean.class),
        Map.entry(7, int.class),
        Map.entry(8, float.class),
        Map.entry(9, int.class),
        Map.entry(11, String.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class));
  }
}
