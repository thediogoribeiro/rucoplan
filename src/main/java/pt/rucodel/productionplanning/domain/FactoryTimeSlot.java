package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public enum FactoryTimeSlot {
    MORNING_09_14(1, "Das 09:00 às 14:00", LocalTime.of(9, 0), LocalTime.of(14, 0), false),
    AFTERNOON_14_19(2, "Das 14:00 às 19:00", LocalTime.of(14, 0), LocalTime.of(19, 0), false),
    EVENING_19_OVERNIGHT(3, "Das 19:00 à madrugada", LocalTime.of(19, 0), null, true);

    private final int option;
    private final String label;
    private final LocalTime startTime;
    private final LocalTime fixedEndTime;
    private final boolean overnight;

    FactoryTimeSlot(int option, String label, LocalTime startTime, LocalTime fixedEndTime, boolean overnight) {
        this.option = option;
        this.label = label;
        this.startTime = startTime;
        this.fixedEndTime = fixedEndTime;
        this.overnight = overnight;
    }

    public static FactoryTimeSlot fromOption(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int option = Integer.parseInt(value.trim());
            for (FactoryTimeSlot slot : values()) {
                if (slot.option == option) {
                    return slot;
                }
            }
            return null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public int option() {
        return option;
    }

    public String label() {
        return label;
    }

    public OffsetDateTime startAt(LocalDate date, ZoneId zone) {
        return date.atTime(startTime).atZone(zone).toOffsetDateTime();
    }

    public OffsetDateTime endAt(LocalDate date, ZoneId zone, LocalTime overnightEndTime) {
        LocalDate endDate = overnight ? date.plusDays(1) : date;
        LocalTime endTime = overnight ? overnightEndTime : fixedEndTime;
        return endDate.atTime(endTime).atZone(zone).toOffsetDateTime();
    }
}
