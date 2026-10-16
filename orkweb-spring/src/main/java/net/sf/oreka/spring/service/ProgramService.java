package net.sf.oreka.spring.service;

import net.sf.oreka.persistent.OrkProgram;
import net.sf.oreka.spring.dto.ProgramDto;
import net.sf.oreka.spring.dto.ProgramRequest;
import net.sf.oreka.spring.repository.PortRepository;
import net.sf.oreka.spring.repository.ProgramRepository;
import net.sf.oreka.spring.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 录音计划(Program)管理
 * 删除是软删(discarded=true),跟老代码一致
 */
@Service
@Transactional
public class ProgramService {

    private static final Logger log = LoggerFactory.getLogger(ProgramService.class);

    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final PortRepository portRepository;

    public ProgramService(ProgramRepository programRepository, UserRepository userRepository,
                          PortRepository portRepository) {
        this.programRepository = programRepository;
        this.userRepository = userRepository;
        this.portRepository = portRepository;
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> findAll() {
        return programRepository.findByDiscardedFalse().stream()
                .map(ProgramDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProgramDto> findById(int id) {
        return programRepository.findById(id)
                .filter(p -> !p.isDiscarded())
                .map(ProgramDto::from);
    }

    public ProgramDto create(ProgramRequest req, Integer creatorUserId) {
        OrkProgram p = new OrkProgram();
        applyRequest(p, req);
        p.setTimestamp(new Date());
        p.setRecordedSoFar(0);
        if (creatorUserId != null) {
            userRepository.findById(creatorUserId).ifPresent(p::setCreator);
        }
        OrkProgram saved = programRepository.save(p);
        log.info("Created program id={} name={}", saved.getId(), saved.getName());
        return ProgramDto.from(saved);
    }

    public Optional<ProgramDto> update(int id, ProgramRequest req) {
        return programRepository.findById(id)
                .filter(p -> !p.isDiscarded())
                .map(p -> {
                    applyRequest(p, req);
                    return ProgramDto.from(programRepository.save(p));
                });
    }

    public boolean delete(int id) {
        return programRepository.findById(id).map(p -> {
            p.setDiscarded(true);
            programRepository.save(p);
            log.info("Soft-deleted(discarded)program id={}", id);
            return true;
        }).orElse(false);
    }

    private void applyRequest(OrkProgram p, ProgramRequest req) {
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.active() != null) p.setActive(req.active());
        if (req.direction() != null) p.setDirection(req.direction());
        if (req.minDuration() != null) p.setMinDuration(req.minDuration());
        if (req.maxDuration() != null) p.setMaxDuration(req.maxDuration());
        if (req.randomPercent() != null) p.setRandomPercent(req.randomPercent());
        if (req.cycle() != null) p.setCycle(req.cycle());
        if (req.startDay() != null) p.setStartDay(req.startDay());
        if (req.stopDay() != null) p.setStopDay(req.stopDay());
        if (req.startTime() != null) p.setStartTime(req.startTime());
        if (req.stopTime() != null) p.setStopTime(req.stopTime());
        if (req.recPerCycle() != null) p.setRecPerCycle(req.recPerCycle());
        if (req.localParty() != null) p.setLocalParty(req.localParty());
        if (req.remoteParty() != null) p.setRemoteParty(req.remoteParty());
        if (req.keepForHours() != null) p.setKeepForHours(req.keepForHours());
        if (req.targetUserId() != null) {
            userRepository.findById(req.targetUserId()).ifPresent(p::setTargetUser);
        }
        if (req.targetPortId() != null) {
            portRepository.findById(req.targetPortId()).ifPresent(p::setTargetPort);
        }
    }
}
