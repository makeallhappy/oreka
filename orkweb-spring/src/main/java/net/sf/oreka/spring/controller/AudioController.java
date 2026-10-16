package net.sf.oreka.spring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.sf.oreka.spring.service.AudioService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 音频流服务 —— 支持 HTTP Range(拖动播放必须)
 *
 * GET /api/v1/tapes/{id}/audio        流式播放(inline)
 * GET /api/v1/tapes/{id}/download     下载(attachment)
 */
@RestController
@RequestMapping("/api/v1/tapes")
@Tag(name = "Audio", description = "音频流(支持 Range)")
public class AudioController {

    private final AudioService audioService;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @GetMapping("/{id}/audio")
    @Operation(summary = "音频流", description = "支持 HTTP Range 拖动播放")
    public ResponseEntity<StreamingResponseBody> streamAudio(
            @PathVariable int id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) throws IOException {
        return serveAudio(id, rangeHeader, false);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "音频下载")
    public ResponseEntity<StreamingResponseBody> downloadAudio(
            @PathVariable int id,
            @RequestHeader(value = "Range", required = false) String rangeHeader) throws IOException {
        return serveAudio(id, rangeHeader, true);
    }

    private ResponseEntity<StreamingResponseBody> serveAudio(int id, String rangeHeader, boolean download) throws IOException {
        var audioOpt = audioService.getAudioByTapeId(id);
        if (audioOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var audio = audioOpt.get();
        Resource resource = audio.resource();
        long fileLength = audio.contentLength();
        String filename = audio.filename();

        // 解析 Range 头(格式:bytes=start-end)
        long rangeStart = 0;
        long rangeEnd = fileLength - 1;
        boolean isRangeRequest = false;
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isRangeRequest = true;
            String[] parts = rangeHeader.substring(6).split("-");
            try {
                rangeStart = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    rangeEnd = Long.parseLong(parts[1]);
                }
            } catch (NumberFormatException e) {
                // 非法 Range,按全量处理
                isRangeRequest = false;
                rangeStart = 0;
                rangeEnd = fileLength - 1;
            }
            if (rangeEnd >= fileLength) rangeEnd = fileLength - 1;
        }

        final long start = rangeStart;
        final long end = rangeEnd;
        final long contentLength = end - start + 1;

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = resource.getInputStream()) {
                in.skip(start);
                streamRange(in, outputStream, contentLength);
            }
        };

        String contentType = guessContentType(filename);
        String disposition = (download ? "attachment" : "inline") + ";filename=\"" + filename + "\"";

        var builder = isRangeRequest ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT) : ResponseEntity.ok();
        var response = builder
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength);
        if (isRangeRequest) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
        }
        return response.body(body);
    }

    private void streamRange(InputStream in, OutputStream out, long bytesToWrite) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = bytesToWrite;
        int read;
        while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
            out.write(buffer, 0, read);
            remaining -= read;
        }
        out.flush();
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".gsm")) return "audio/x-gsm";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".flac")) return "audio/flac";
        return "application/octet-stream";
    }
}
