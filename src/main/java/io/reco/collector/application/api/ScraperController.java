package io.reco.collector.application.api;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.service.CheckpointService;
import io.reco.collector.infrastructure.crawler.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 크롤링 수동 실행 및 상태 관리 API
 *
 * - POST /start : 크롤링 시작 (FULL/INCREMENTAL, 동기/비동기)
 * - POST /stop  : 크롤링 중지
 * - GET /status : 현재 상태 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
public class ScraperController {

    private final ScraperService scraperService;
    private final CheckpointService checkpointService;

    /**
     * 크롤링 시작
     * @param type FULL(전체) / INCREMENTAL(증분, 기본값)
     * @param async true면 비동기 실행 (기본값), false면 완료까지 대기
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestParam(name = "type", defaultValue = "INCREMENTAL") String type,
                                   @RequestParam(name = "async", defaultValue = "true") boolean async) {
        CrawlType crawlType = "FULL".equalsIgnoreCase(type) ? CrawlType.FULL : CrawlType.INCREMENTAL;

        // 이미 실행 중이면 409 응답
        if (checkpointService.hasRunningCrawl()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 실행 중인 크롤링이 있습니다."));
        }

        if (async) {
            CompletableFuture.runAsync(() -> {
                if (crawlType == CrawlType.FULL) scraperService.scrapeAll();
                else scraperService.scrapeIncremental();
            }).exceptionally(ex -> {
                log.error("비동기 크롤링 실패: {}", ex.getMessage(), ex);
                return null;
            });
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "크롤링 시작", "type", crawlType.name()));
        } else {
            if (crawlType == CrawlType.FULL) scraperService.scrapeAll();
            else scraperService.scrapeIncremental();
            Optional<CrawlCheckpoint> latest = checkpointService.getLatest();
            return ResponseEntity.ok(Map.of(
                    "message", "크롤링 완료",
                    "runId", latest.map(CrawlCheckpoint::getCrawlRunId).orElse(null)
            ));
        }
    }

    /**
     * 크롤링 중지 요청
     * - 현재 진행 중인 항목 처리 완료 후 안전하게 종료
     * - 체크포인트에 진행 상황 저장됨 (이어서 수집 가능)
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        Optional<CrawlCheckpoint> latest = checkpointService.getLatest();
        if (latest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "최근 크롤링 내역이 없습니다."));
        }

        scraperService.requestStop();
        checkpointService.stopCrawl(latest.get().getCrawlRunId());
        return ResponseEntity.ok(Map.of("message", "중지 요청됨", "runId", latest.get().getCrawlRunId()));
    }

    /**
     * 크롤링 상태 조회
     * - 가장 최근 크롤링의 진행 상황 반환
     * - runId, 상태, 페이지, 수집/실패 건수 등
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Optional<CrawlCheckpoint> latest = checkpointService.getLatest();
        if (latest.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "기록 없음"));
        }

        CrawlCheckpoint cp = latest.get();
        Map<String, Object> response = new HashMap<>();
        response.put("runId", cp.getCrawlRunId());
        response.put("type", cp.getCrawlType().name());
        response.put("status", cp.getStatus().name());
        response.put("lastPage", cp.getLastPage());
        response.put("lastNoticeNo", cp.getLastNoticeNo());
        response.put("totalCollected", cp.getTotalCollected());
        response.put("totalFailed", cp.getTotalFailed());
        response.put("startedAt", toIso(cp.getStartedAt()));
        response.put("completedAt", toIso(cp.getCompletedAt()));
        return ResponseEntity.ok(response);
    }

    private String toIso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}

