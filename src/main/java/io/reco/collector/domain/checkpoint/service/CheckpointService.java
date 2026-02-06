package io.reco.collector.domain.checkpoint.service;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlStatus;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.repository.CrawlCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckpointService {

    private final CrawlCheckpointRepository checkpointRepository;

    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 새 크롤링 시작
     */
    @Transactional
    public CrawlCheckpoint startNewCrawl(CrawlType crawlType) {
        // 이미 실행 중인 크롤링이 있으면 예외
        checkpointRepository.findFirstByStatusOrderByStartedAtDesc(CrawlStatus.RUNNING)
                .ifPresent(running -> {
                    throw new IllegalStateException(
                            "이미 실행 중인 크롤링이 있습니다: " + running.getCrawlRunId());
                });

        String crawlRunId = generateCrawlRunId();
        CrawlCheckpoint checkpoint = CrawlCheckpoint.start(crawlRunId, crawlType);

        return checkpointRepository.save(checkpoint);
    }

    /**
     * 진행 상황 업데이트
     */
    @Transactional
    public void updateProgress(String crawlRunId, int page, String lastNoticeNo) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.updateProgress(page, lastNoticeNo);
    }

    /**
     * 수집 성공 카운트 증가
     */
    @Transactional
    public void incrementCollected(String crawlRunId) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.incrementCollected();
    }

    /**
     * 수집 실패 카운트 증가
     */
    @Transactional
    public void incrementFailed(String crawlRunId) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.incrementFailed();
    }

    /**
     * 크롤링 완료 처리
     */
    @Transactional
    public void completeCrawl(String crawlRunId) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.complete();
    }

    /**
     * 크롤링 실패 처리
     */
    @Transactional
    public void failCrawl(String crawlRunId, String errorMessage) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.fail(errorMessage);
    }

    /**
     * 크롤링 수동 중지
     */
    @Transactional
    public void stopCrawl(String crawlRunId) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);
        checkpoint.stop();
    }

    /**
     * 재개 가능한 크롤링 찾기 (STOPPED 또는 RUNNING 상태)
     */
    public Optional<CrawlCheckpoint> findResumable() {
        // STOPPED 상태 먼저 (수동 중지된 것)
        Optional<CrawlCheckpoint> stopped = checkpointRepository
                .findFirstByStatusOrderByStartedAtDesc(CrawlStatus.STOPPED);
        if (stopped.isPresent()) {
            return stopped;
        }

        // RUNNING 상태 (비정상 종료된 것)
        return checkpointRepository
                .findFirstByStatusOrderByStartedAtDesc(CrawlStatus.RUNNING);
    }

    /**
     * 중단된 크롤링 재개
     */
    @Transactional
    public CrawlCheckpoint resumeCrawl(String crawlRunId) {
        CrawlCheckpoint checkpoint = getCheckpoint(crawlRunId);

        if (checkpoint.getStatus() == CrawlStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 크롤링입니다: " + crawlRunId);
        }
        if (checkpoint.getStatus() == CrawlStatus.FAILED) {
            throw new IllegalStateException("실패한 크롤링은 재개할 수 없습니다: " + crawlRunId);
        }

        // STOPPED나 RUNNING(비정상 종료) 상태를 RUNNING으로 변경
        checkpoint.resume();
        return checkpoint;
    }

    /**
     * 가장 최근 크롤링 조회
     */
    public Optional<CrawlCheckpoint> getLatest() {
        return checkpointRepository.findFirstByOrderByStartedAtDesc();
    }

    /**
     * runId로 체크포인트 조회
     */
    public Optional<CrawlCheckpoint> findByRunId(String crawlRunId) {
        return checkpointRepository.findByCrawlRunId(crawlRunId);
    }

    /**
     * 실행 중인 크롤링 존재 여부
     */
    public boolean hasRunningCrawl() {
        return checkpointRepository
                .findFirstByStatusOrderByStartedAtDesc(CrawlStatus.RUNNING)
                .isPresent();
    }

    // === Private 메서드 ===

    private CrawlCheckpoint getCheckpoint(String crawlRunId) {
        return checkpointRepository.findByCrawlRunId(crawlRunId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "체크포인트를 찾을 수 없습니다: " + crawlRunId));
    }

    private String generateCrawlRunId() {
        return "crawl_" + LocalDateTime.now().format(RUN_ID_FORMAT);
    }
}
