package pt.rucodel.productionplanning.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record PlanningTargetRequest(
        @NotNull(message = "O target mínimo é obrigatório.")
        @Min(value = 0, message = "O target mínimo não pode ser negativo.")
        Integer minimumDailyTarget,

        @JsonAlias("maximumDailyTarget")
        @NotNull(message = "O target máximo é obrigatório.")
        @Positive(message = "O target máximo deve ser superior a zero.")
        Integer regularDailyCapacity,

        @NotNull(message = "A data de entrada em vigor é obrigatória.")
        LocalDate effectiveFrom
) {
}
