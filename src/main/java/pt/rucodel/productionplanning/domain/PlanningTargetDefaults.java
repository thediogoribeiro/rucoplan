package pt.rucodel.productionplanning.domain;

import java.time.LocalDate;

public final class PlanningTargetDefaults {
    public static final int MINIMUM_DAILY_TARGET = 150;
    public static final int REGULAR_DAILY_CAPACITY = 180;
    public static final LocalDate EFFECTIVE_FROM = LocalDate.of(1970, 1, 1);
    public static final String CREATED_BY = "SYSTEM";
    public static final String SOURCE_SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
    public static final String SOURCE_DATABASE = "DATABASE";

    private PlanningTargetDefaults() {
    }
}
