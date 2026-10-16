package net.sf.oreka.spring.dto;

import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkSegment;

import java.util.Date;

/**
 * 录音段 DTO(扁平化,避免暴露实体关联和懒加载问题)
 */
public record SegmentDto(
        int id,
        Date timestamp,
        long duration,
        String localParty,
        String localEntryPoint,
        String remoteParty,
        Direction direction,
        String loginString,
        String portName,
        Integer tapeId,
        Integer sessionId,
        Integer userId
) {
    public static SegmentDto from(OrkSegment seg) {
        return new SegmentDto(
                seg.getId(),
                seg.getTimestamp(),
                seg.getDuration(),
                seg.getLocalParty(),
                seg.getLocalEntryPoint(),
                seg.getRemoteParty(),
                seg.getDirection(),
                seg.getLoginString(),
                seg.getPortName(),
                seg.getTape() != null ? seg.getTape().getId() : null,
                seg.getSession() != null ? seg.getSession().getId() : null,
                seg.getUser() != null ? seg.getUser().getId() : null
        );
    }
}
