package net.sf.oreka.spring.dto;

import net.sf.oreka.Cycle;
import net.sf.oreka.Day;
import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkProgram;

import java.util.Date;

public record ProgramDto(
        int id,
        String name,
        String description,
        boolean active,
        Direction direction,
        int minDuration,
        int maxDuration,
        double randomPercent,
        Cycle cycle,
        Day startDay,
        Day stopDay,
        Date startTime,
        Date stopTime,
        int recPerCycle,
        int recordedSoFar,
        String localParty,
        String remoteParty,
        int keepForHours,
        Integer creatorId,
        Integer targetUserId,
        Integer targetPortId
) {
    public static ProgramDto from(OrkProgram p) {
        return new ProgramDto(
                p.getId(), p.getName(), p.getDescription(), p.isActive(),
                p.getDirection(), p.getMinDuration(), p.getMaxDuration(),
                p.getRandomPercent(), p.getCycle(), p.getStartDay(), p.getStopDay(),
                p.getStartTime(), p.getStopTime(), p.getRecPerCycle(), p.getRecordedSoFar(),
                p.getLocalParty(), p.getRemoteParty(), p.getKeepForHours(),
                p.getCreator() != null ? p.getCreator().getId() : null,
                p.getTargetUser() != null ? p.getTargetUser().getId() : null,
                p.getTargetPort() != null ? p.getTargetPort().getId() : null
        );
    }
}
