package net.sf.oreka.spring.dto;

import net.sf.oreka.persistent.OrkPort;

public record PortDto(
        int id
) {
    public static PortDto from(OrkPort p) {
        return new PortDto(p.getId());
    }
}
