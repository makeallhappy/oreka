package net.sf.oreka.spring.dto;

import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkTape;

import java.util.Date;

/**
 * Tape DTO(扁平化)
 */
public record TapeDto(
        int id,
        Date timestamp,
        long duration,
        String filename,
        String localParty,
        String localEntryPoint,
        String remoteParty,
        Direction direction,
        String portName,
        String nativeCallId,
        String state,
        Date expiryTimestamp,
        Integer serviceId
) {
    public static TapeDto from(OrkTape tape) {
        return new TapeDto(
                tape.getId(),
                tape.getTimestamp(),
                tape.getDuration(),
                tape.getFilename(),
                tape.getLocalParty(),
                tape.getLocalEntryPoint(),
                tape.getRemoteParty(),
                tape.getDirection(),
                tape.getPortName(),
                tape.getNativeCallId(),
                tape.getState(),
                tape.getExpiryTimestamp(),
                tape.getService() != null ? tape.getService().getId() : null
        );
    }
}
