package net.sf.oreka.spring.service;

import net.sf.oreka.Cycle;
import net.sf.oreka.Day;
import net.sf.oreka.Direction;
import net.sf.oreka.persistent.OrkProgram;
import net.sf.oreka.persistent.OrkSegment;
import net.sf.oreka.spring.repository.ProgramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 录音计划过滤服务 —— 迁移自 orktrack ProgramManager
 *
 * 职责:判断一个 segment 是否被任一 active program 接受(应录音)。
 * 规则(与老代码一致):
 *   - 无 active program → 全部保留(默认行为)
 *   - 有 program → segment 需被至少一个 program 接受
 *
 * 过滤条件(逐项与老 ProgramManager.filterSegmentAgainstProgram 对齐):
 *   方向 / 时长范围 / 随机抽样 / 主叫匹配 / 被叫匹配 / 目标用户 / 目标端口 /
 *   周期配额(recPerCycle) / 调度(PERMANENT/DAILY/WEEKLY)
 *
 * 单位说明(有意现代化统一):
 *   老系统 segment.duration 存毫秒,过滤时 prog.maxDuration(秒) *1000 比较。
 *   新系统 segment/tape/前端全程用秒(与 tape 表、前端显示、已有数据一致),
 *   故此处按秒直接比较,不再 *1000。
 */
@Service
@Transactional
public class ProgramFilterService {

    private static final Logger log = LoggerFactory.getLogger(ProgramFilterService.class);

    private final ProgramRepository programRepository;

    public ProgramFilterService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    /**
     * segment 是否应被保留(被任一 active program 接受)
     */
    public boolean shouldKeep(OrkSegment seg) {
        List<OrkProgram> programs = programRepository.findByActiveTrueAndDiscardedFalse();
        if (programs.isEmpty()) {
            return true; // 无计划 → 全录(老代码默认行为)
        }
        for (OrkProgram prog : programs) {
            if (matches(seg, prog)) {
                // 命中:累计配额并关联(对齐老代码 recordedSoFar++)
                prog.setRecordedSoFar(prog.getRecordedSoFar() + 1);
                programRepository.save(prog);
                seg.getRecPrograms().add(prog);
                log.debug("Segment accepted by program '{}' (id={})", prog.getName(), prog.getId());
                return true;
            }
        }
        return false;
    }

    /**
     * segment 是否匹配某个 program(任一条件不满足即丢弃)
     */
    public boolean matches(OrkSegment seg, OrkProgram prog) {
        // 1) 方向(ALL 不限)
        if (prog.getDirection() != null && prog.getDirection() != Direction.ALL
                && prog.getDirection() != Direction.UNKN
                && seg.getDirection() != prog.getDirection()) {
            return reject(prog, "Dir.");
        }

        // 2) 时长上限(秒;0 = 不限)
        if (prog.getMaxDuration() > 0 && seg.getDuration() > prog.getMaxDuration()) {
            return reject(prog, "Max Dur.");
        }

        // 3) 时长下限(秒;0 = 不限)
        if (prog.getMinDuration() > 0 && seg.getDuration() < prog.getMinDuration()) {
            return reject(prog, "Min Dur.");
        }

        // 4) 随机抽样(randomPercent ∈ [0,1];0 = 关闭抽样全录)
        if (prog.getRandomPercent() != 0.0 && Math.random() > prog.getRandomPercent()) {
            return reject(prog, "Random percent");
        }

        // 5) 主叫精确匹配(空 = 不限)
        if (notBlank(prog.getLocalParty()) && !safe(seg.getLocalParty()).equals(prog.getLocalParty())) {
            return reject(prog, "LocalParty");
        }

        // 6) 被叫精确匹配(空 = 不限)
        if (notBlank(prog.getRemoteParty()) && !safe(seg.getRemoteParty()).equals(prog.getRemoteParty())) {
            return reject(prog, "RemoteParty");
        }

        // 7) 目标用户(segment 需已解析出 user)
        if (prog.getTargetUser() != null) {
            if (seg.getUser() == null || seg.getUser().getId() != prog.getTargetUser().getId()) {
                return reject(prog, "Target User");
            }
        }

        // 8) 目标端口(segment 需已解析出 port)
        if (prog.getTargetPort() != null) {
            if (seg.getPort() == null || seg.getPort().getId() != prog.getTargetPort().getId()) {
                return reject(prog, "Target Port");
            }
        }

        // 9) 周期配额(0 = 不限)
        if (prog.getRecPerCycle() != 0 && prog.getRecordedSoFar() >= prog.getRecPerCycle()) {
            return reject(prog, "Enough recordings for this Cycle");
        }

        // 10) 调度(PERMANENT/DAILY/WEEKLY)
        if (!matchesSchedule(seg, prog)) {
            return reject(prog, "Schedule");
        }

        return true;
    }

    /**
     * 调度匹配 —— 迁移自 filterSegmentAgainstProgramSchedule
     * PERMANENT 恒真;DAILY 按日内时段;WEEKLY 按星期+日内时段。
     */
    private boolean matchesSchedule(OrkSegment seg, OrkProgram prog) {
        Cycle cycle = prog.getCycle();
        if (cycle == null || cycle == Cycle.PERMANENT) {
            return true;
        }
        if (prog.getStartTime() == null || prog.getStopTime() == null) {
            return true; // 未设时段则不限
        }

        LocalDateTime segTime = LocalDateTime.ofInstant(seg.getTimestamp().toInstant(), ZoneId.systemDefault());
        LocalTime segTod = segTime.toLocalTime();
        LocalTime progStart = toLocalTime(prog.getStartTime());
        LocalTime progStop = toLocalTime(prog.getStopTime());

        if (cycle == Cycle.DAILY) {
            return withinDay(segTod, progStart, progStop);
        }

        if (cycle == Cycle.WEEKLY) {
            // Day 枚举 {monday..sunday} = {0..6};java DayOfWeek {MONDAY..SUNDAY} = {1..7}
            int segDay = segTime.getDayOfWeek().getValue() - 1; // -> 0..6
            int startDay = prog.getStartDay() != null ? prog.getStartDay().ordinal() : 0;
            int stopDay = prog.getStopDay() != null ? prog.getStopDay().ordinal() : 6;
            if (prog.getStartDay() == Day.UNKN || prog.getStopDay() == Day.UNKN) {
                // 未设星期则只看时段
                return withinDay(segTod, progStart, progStop);
            }
            if (segDay >= startDay && segDay <= stopDay) {
                return withinDay(segTod, progStart, progStop);
            }
            return false;
        }

        // HOURLY/MONTHLY 老代码也未实现
        log.warn("Cycle not implemented (same as legacy): {}", cycle);
        return false;
    }

    /** 日内时段匹配(含跨午夜:如 22:00-06:00) */
    private boolean withinDay(LocalTime t, LocalTime start, LocalTime stop) {
        if (start == null || stop == null) return true;
        if (!stop.isBefore(start)) {
            // 常规:start <= t <= stop
            return !t.isBefore(start) && !t.isAfter(stop);
        }
        // 跨午夜:t >= start 或 t <= stop
        return !t.isBefore(start) || !t.isAfter(stop);
    }

    private LocalTime toLocalTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).toLocalTime();
    }

    private boolean reject(OrkProgram prog, String reason) {
        log.trace("Segment dropped by program '{}' (id={}), reason: {}", prog.getName(), prog.getId(), reason);
        return false;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
