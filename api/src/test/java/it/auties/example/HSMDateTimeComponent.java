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
public class HSMDateTimeComponent implements ProtobufMessage {

  @JsonProperty(value = "7", required = false)
  private CalendarType calendar;

  @JsonProperty(value = "6", required = false)
  private int minute;

  @JsonProperty(value = "5", required = false)
  private int hour;

  @JsonProperty(value = "4", required = false)
  private int dayOfMonth;

  @JsonProperty(value = "3", required = false)
  private int month;

  @JsonProperty(value = "2", required = false)
  private int year;

  @JsonProperty(value = "1", required = false)
  private DayOfWeekType dayOfWeek;

  @Accessors(fluent = true)
  public enum DayOfWeekType implements ProtobufEnum {
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6),
    SUNDAY(7);

    private final @Getter int index;

    DayOfWeekType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static DayOfWeekType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Accessors(fluent = true)
  public enum CalendarType implements ProtobufEnum {
    GREGORIAN(1),
    SOLAR_HIJRI(2);

    private final @Getter int index;

    CalendarType(int index) {
      this.index = index;
    }

    @JsonCreator
    public static CalendarType forIndex(int index) {
      return Arrays.stream(values())
          .filter(entry -> entry.index() == index)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public Map<Integer, Class<?>> types() {
    return Map.ofEntries(
        Map.entry(1, DayOfWeekType.class),
        Map.entry(2, int.class),
        Map.entry(3, int.class),
        Map.entry(4, int.class),
        Map.entry(5, int.class),
        Map.entry(6, int.class),
        Map.entry(7, CalendarType.class));
  }
}
