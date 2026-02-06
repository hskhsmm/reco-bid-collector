package io.reco.collector.application.api;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlStatus;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.service.CheckpointService;
import io.reco.collector.infrastructure.crawler.ScraperService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScraperController 단위 테스트
 *
 * Spring Boot 4.x 테스트 구조:
 * - @SpringBootTest + @AutoConfigureMockMvc 조합 사용
 * - @MockitoBean으로 의존성 모킹
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScraperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScraperService scraperService;

    @MockitoBean
    private CheckpointService checkpointService;

    @Test
    @DisplayName("POST /start - 크롤링 시작 성공 (비동기)")
    void 크롤링_시작_성공() throws Exception {
        // given
        when(checkpointService.hasRunningCrawl()).thenReturn(false);

        // when & then
        mockMvc.perform(post("/api/scrape/start")
                        .param("type", "INCREMENTAL")
                        .param("async", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("크롤링 시작"))
                .andExpect(jsonPath("$.type").value("INCREMENTAL"));
    }

    @Test
    @DisplayName("POST /start - 이미 실행 중이면 409 응답")
    void 이미_실행중이면_409_응답() throws Exception {
        // given
        when(checkpointService.hasRunningCrawl()).thenReturn(true);

        // when & then
        mockMvc.perform(post("/api/scrape/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 실행 중인 크롤링이 있습니다."));
    }

    @Test
    @DisplayName("POST /start - FULL 타입 크롤링")
    void FULL_타입_크롤링() throws Exception {
        // given
        when(checkpointService.hasRunningCrawl()).thenReturn(false);

        // when & then
        mockMvc.perform(post("/api/scrape/start")
                        .param("type", "FULL"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("FULL"));
    }

    @Test
    @DisplayName("POST /stop - 크롤링 중지 성공")
    void 크롤링_중지_성공() throws Exception {
        // given
        CrawlCheckpoint checkpoint = createCheckpoint(CrawlStatus.RUNNING);
        when(checkpointService.getLatest()).thenReturn(Optional.of(checkpoint));

        // when & then
        mockMvc.perform(post("/api/scrape/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("중지 요청됨"))
                .andExpect(jsonPath("$.runId").value("crawl_test_001"));

        verify(scraperService).requestStop();
        verify(checkpointService).stopCrawl("crawl_test_001");
    }

    @Test
    @DisplayName("POST /stop - 크롤링 내역 없으면 404")
    void 크롤링_내역_없으면_404() throws Exception {
        // given
        when(checkpointService.getLatest()).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(post("/api/scrape/stop"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("최근 크롤링 내역이 없습니다."));
    }

    @Test
    @DisplayName("GET /status - 상태 조회 성공")
    void 상태_조회_성공() throws Exception {
        // given
        CrawlCheckpoint checkpoint = createCheckpoint(CrawlStatus.RUNNING);
        when(checkpointService.getLatest()).thenReturn(Optional.of(checkpoint));

        // when & then
        mockMvc.perform(get("/api/scrape/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("crawl_test_001"))
                .andExpect(jsonPath("$.type").value("INCREMENTAL"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.lastPage").value(5))
                .andExpect(jsonPath("$.totalCollected").value(42))
                .andExpect(jsonPath("$.totalFailed").value(3));
    }

    @Test
    @DisplayName("GET /status - 기록 없으면 메시지 반환")
    void 기록_없으면_메시지_반환() throws Exception {
        // given
        when(checkpointService.getLatest()).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/scrape/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("기록 없음"));
    }

    // === Helper ===

    private CrawlCheckpoint createCheckpoint(CrawlStatus status) {
        return CrawlCheckpoint.builder()
                .id(1L)
                .crawlRunId("crawl_test_001")
                .crawlType(CrawlType.INCREMENTAL)
                .status(status)
                .lastPage(5)
                .lastNoticeNo("BID-2024-001")
                .totalCollected(42)
                .totalFailed(3)
                .startedAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}
