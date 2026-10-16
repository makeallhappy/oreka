package net.sf.oreka.spring.dto;

import net.sf.oreka.persistent.OrkService;

public record ServiceDto(
        int id,
        String name,
        String hostname,
        int tcpPort,
        String fileServeProtocol,
        int fileServeTcpPort,
        String fileServePath,
        boolean recordMaster,
        String contextPath
) {
    public static ServiceDto from(OrkService s) {
        return new ServiceDto(
                s.getId(), s.getName(), s.getHostname(), s.getTcpPort(),
                s.getFileServeProtocol(), s.getFileServeTcpPort(), s.getFileServePath(),
                s.isRecordMaster(), s.getContextPath()
        );
    }
}
