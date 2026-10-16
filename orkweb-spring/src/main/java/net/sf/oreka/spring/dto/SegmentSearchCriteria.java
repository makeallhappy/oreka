package net.sf.oreka.spring.dto;

import net.sf.oreka.Direction;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 录音段搜索条件(GET /api/v1/segments 的查询参数)
 */
public record SegmentSearchCriteria(
        String localParty,
        String remoteParty,
        String portName,
        Direction direction,
        Long minDuration,
        Long maxDuration,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Date from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Date to
) {
}
