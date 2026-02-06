package io.reco.collector.application.api;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.service.CheckpointService;
import io.reco.collector.infrastructure.crawler.ScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
public class ScraperController {

    private final ScraperService scraperService;
    private final CheckpointService checkpointService;

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

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Optional<CrawlCheckpoint> latest = checkpointService.getLatest();
        if (latest.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "기록 없음"));
        }

        CrawlCheckpoint cp = latest.get();
        return ResponseEntity.ok(Map.of(
                "runId", cp.getCrawlRunId(),
                "type", cp.getCrawlType().name(),
                "status", cp.getStatus().name(),
                "lastPage", cp.getLastPage(),
                "lastNoticeNo", cp.getLastNoticeNo(),
                "totalCollected", cp.getTotalCollected(),
                "totalFailed", cp.getTotalFailed(),
                "startedAt", toIso(cp.getStartedAt()),
                "completedAt", toIso(cp.getCompletedAt())
        ));
    }

    private String toIso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}

