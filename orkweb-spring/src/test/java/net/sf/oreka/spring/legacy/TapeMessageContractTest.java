package net.sf.oreka.spring.legacy;

import net.sf.oreka.spring.repository.TapeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * orkaudio /command 协议契约测试(TDD:先写测试,M2 实现让它过)
 *
 * 协议格式(从 orktrack CommandServlet + TapeMessage 反推):
 *   请求:GET /command?message=tape&stage=START&recid=xxx&...
 *   响应:key=value 单行文本(success=true deletetape=false comment=)
 *
 * 关键兼容点:
 *   - 参数名全小写(localparty 不是 localParty)
 *   - stage 枚举大写(START/STOP/READY/COMPLETE)
 *   - direction 枚举大写(IN/OUT/UNKN)
 *   - 响应不是 JSON,是 key=value
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TapeMessageContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TapeRepository tapeRepository;

    @BeforeEach
    void cleanDb() {
        tapeRepository.deleteAll();
    }

    // ==================== 基本生命周期 ====================

    @Nested
    @DisplayName("录音生命周期")
    class Lifecycle {

        @Test
        @DisplayName("START 消息:新增 tape,返回 success=true")
        void startMessage_createsTape() throws Exception {
            MvcResult result = mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-001")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("filename", "")
                    .param("captureport", "SIP/1001")
                    .param("localparty", "1001")
                    .param("remoteparty", "1002")
                    .param("direction", "IN")
                    .param("service", "orkaudio-test")
                    .param("hostname", "recorder01")
                    .param("nativecallid", "call-uuid-001"))
                .andExpect(status().isOk())
                .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).contains("success=true");
            assertThat(tapeRepository.findFirstByNativeCallIdOrderByIdDesc("call-uuid-001")).isPresent();
        }

        @Test
        @DisplayName("START→STOP 同 recId:合并为一条 tape,不新增")
        void startThenStop_mergesIntoSingleTape() throws Exception {
            // START
            mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-002")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("captureport", "SIP/1001")
                    .param("localparty", "1001")
                    .param("remoteparty", "1002")
                    .param("direction", "IN")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-002"))
                .andExpect(status().isOk());

            // STOP(同 nativeCallId + 时间窗内)
            MvcResult result = mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "STOP")
                    .param("recid", "rec-002")
                    .param("timestamp", "1697525100")
                    .param("duration", "300")
                    .param("filename", "20261016_100000.wav")
                    .param("captureport", "SIP/1001")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-002"))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("success=true");
            // 关键断言:只有一条 tape,不是两条
            assertThat(tapeRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("STOP 消息写入 duration 和 filename")
        void stopMessage_updatesDurationAndFilename() throws Exception {
            // START
            mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-003")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("captureport", "SIP/1001")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-003"));

            // STOP
            mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "STOP")
                    .param("recid", "rec-003")
                    .param("timestamp", "1697525100")
                    .param("duration", "300")
                    .param("filename", "call-003.wav")
                    .param("captureport", "SIP/1001")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-003"));

            var tape = tapeRepository.findFirstByNativeCallIdOrderByIdDesc("call-uuid-003");
            assertThat(tape).isPresent();
            assertThat(tape.get().getDuration()).isEqualTo(300);
            assertThat(tape.get().getFilename()).isEqualTo("call-003.wav");
        }
    }

    // ==================== 协议兼容性 ====================

    @Nested
    @DisplayName("协议兼容性")
    class ProtocolCompatibility {

        @Test
        @DisplayName("响应是 key=value 格式,不是 JSON")
        void responseIsKeyValueNotJson() throws Exception {
            MvcResult result = mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-010")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("captureport", "SIP/1001")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-010"))
                .andExpect(status().isOk())
                .andReturn();

            String body = result.getResponse().getContentAsString();
            // 必须是 key=value,不能以 { 开头
            assertThat(body.trim()).doesNotStartWith("{");
            assertThat(body).contains("success=");
        }

        @Test
        @DisplayName("direction 枚举值是大写(IN/OUT/UNKN)")
        void directionEnumIsUpperCase() throws Exception {
            MvcResult result = mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-011")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("captureport", "SIP/1001")
                    .param("direction", "OUT")
                    .param("service", "orkaudio-test")
                    .param("nativecallid", "call-uuid-011"))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("success=true");
        }

        @Test
        @DisplayName("缺少必填参数 stage:容错处理,不返回 500")
        void missingRequiredParam_doesNotReturn500() throws Exception {
            mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("recid", "rec-012")
                    // 故意不传 stage
                    .param("captureport", "SIP/1001")
                    .param("service", "orkaudio-test"))
                .andExpect(status().isOk()); // 协议要求容错,不是 4xx/5xx
        }
    }

    // ==================== Service 自动创建 ====================

    @Nested
    @DisplayName("Service/Port 自动创建")
    class AutoCreate {

        @Test
        @DisplayName("未知 service 名:自动创建 OrkService 记录")
        void unknownService_autoCreates() throws Exception {
            mockMvc.perform(get("/command")
                    .param("message", "tape")
                    .param("stage", "START")
                    .param("recid", "rec-020")
                    .param("timestamp", "1697524800")
                    .param("duration", "0")
                    .param("captureport", "SIP/1001")
                    .param("service", "new-service-xyz")
                    .param("nativecallid", "call-uuid-020"))
                .andExpect(status().isOk());

            // TODO: M2 实现后验证 OrkService 被创建
            // 当前先只验证请求成功
        }
    }
}
