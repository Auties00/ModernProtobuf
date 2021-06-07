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
public class LiveLocationMessage implements ProtobufMessage {

  @JsonProperty(value = "17", required = false)
  private ContextInfo contextInfo;

  @JsonProperty(value = "16", required = false)
  private byte[] jpegThumbnail;

  @JsonProperty(value = "8", required = false)
  private int timeOffset;

  @JsonProperty(value = "7", required = false)
  private long sequenceNumber;

  @JsonProperty(value = "6", required = false)
  private String caption;

  @JsonProperty(value = "5", required = false)
  private int degreesClockwiseFromMagneticNorth;

  @JsonProperty(value = "4", required = false)
  private float speedInMps;

  @JsonProperty(value = "3", required = false)
  private int accuracyInMeters;

  @JsonProperty(value = "2", required = false)
  private double degreesLongitude;

  @JsonProperty(value = "1", required = false)
  private double degreesLatitude;

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, double.class),
        Map.entry(2, double.class),
        Map.entry(3, int.class),
        Map.entry(4, float.class),
        Map.entry(5, int.class),
        Map.entry(6, String.class),
        Map.entry(7, long.class),
        Map.entry(8, int.class),
        Map.entry(16, byte[].class),
        Map.entry(17, ContextInfo.class));
  }
}
