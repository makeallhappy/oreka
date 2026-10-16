package net.sf.oreka.spring.service;

import net.sf.oreka.persistent.OrkTape;
import net.sf.oreka.spring.repository.TapeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

/**
 * 音频文件服务 —— 从 OrkTape.filename 定位音频文件
 *
 * 文件定位规则(沿用老 orkweb TapeStream):
 *   absolutePath = audioOutputPath + / + filename
 *   (orkaudio 写文件时是 AudioOutputPath/日期子目录/filename,
 *    完整版需要按日期目录查找,M3 先用 filename 直接拼)
 */
@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private final TapeRepository tapeRepository;
    private final String audioOutputPath;

    public AudioService(TapeRepository tapeRepository,
                        @Value("${orkweb.audio-output-path}") String audioOutputPath) {
        this.tapeRepository = tapeRepository;
        this.audioOutputPath = audioOutputPath;
    }

    /**
     * 按 tapeId 定位音频文件
     * @return Resource + 文件名,empty 表示 tape 不存在或文件不存在
     */
    public Optional<AudioFile> getAudioByTapeId(int tapeId) {
        Optional<OrkTape> tape = tapeRepository.findById(tapeId);
        if (tape.isEmpty()) {
            log.warn("Tape not found: {}", tapeId);
            return Optional.empty();
        }
        String filename = tape.get().getFilename();
        if (filename == null || filename.isBlank()) {
            log.warn("Tape {} has no filename", tapeId);
            return Optional.empty();
        }
        // 先直接拼
        File f = new File(audioOutputPath, filename);
        if (f.exists()) {
            return Optional.of(new AudioFile(new FileSystemResource(f), filename, f.length()));
        }
        // 再按日期子目录找(orkaudio 的 AudioOutputPath/yyyyMMdd/filename)
        File parent = new File(audioOutputPath);
        if (parent.isDirectory()) {
            File[] subs = parent.listFiles(File::isDirectory);
            if (subs != null) {
                for (File sub : subs) {
                    File candidate = new File(sub, filename);
                    if (candidate.exists()) {
                        return Optional.of(new AudioFile(new FileSystemResource(candidate), filename, candidate.length()));
                    }
                }
            }
        }
        log.warn("Audio file not found for tape {}: {}", tapeId, filename);
        return Optional.empty();
    }

    public record AudioFile(Resource resource, String filename, long contentLength) {
    }
}
