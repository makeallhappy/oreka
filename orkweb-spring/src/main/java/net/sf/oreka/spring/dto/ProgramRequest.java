package net.sf.oreka.spring.dto;

import jakarta.validation.constraints.NotBlank;
import net.sf.oreka.Cycle;
import net.sf.oreka.Day;
import net.sf.oreka.Direction;

import java.util.Date;

public record ProgramRequest(
        @NotBlank String name,
        String description,
        Boolean active,
        Direction direction,
        Integer minDuration,
        Integer maxDuration,
        Double randomPercent,
        Cycle cycle,
        Day startDay,
        Day stopDay,
        Date startTime,
        Date stopTime,
        Integer recPerCycle,
        String localParty,
        String remoteParty,
        Integer keepForHours,
        Integer targetUserId,
        Integer targetPortId
) {
}
