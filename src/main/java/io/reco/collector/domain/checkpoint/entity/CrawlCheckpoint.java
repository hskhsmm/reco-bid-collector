package io.reco.collector.domain.checkpoint.entity;

import io.reco.collector.domain.checkpoint.enums.CrawlStatus;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 크롤링 체크포인트 - 중단 후 재개를 위한 상태 저장
 *
 * BaseTimeEntity 미사용 이유:
 * - 다른 Entity는 created_at/updated_at (생성/수정 시각)
 * - 이 Entity는 started_at/completed_at (시작/완료 시각)
 * - 비즈니스 의미가 다르므로 별도 관리
 */
@Entity
@Table(name = "crawl_checkpoint", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_started_at", columnList = "started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CrawlCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UK: PK 대신 배치 ID로 다른 테이블에서 참조 (비즈니스 키)
    @Column(name = "crawl_run_id", length = 50, nullable = false, unique = true)
    private String crawlRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "crawl_type", length = 20)
    private CrawlType crawlType;

    @Column(name = "last_page")
    @Builder.Default
    private Integer lastPage = 0;

    @Column(name = "last_notice_no", length = 30)
    private String lastNoticeNo;

    @Column(name = "total_collected")
    @Builder.Default
    private Integer totalCollected = 0;

    @Column(name = "total_failed")
    @Builder.Default
    private Integer totalFailed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CrawlStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // === 팩토리 메서드: 생성 의도를 명확히 표현 ===

    public static CrawlCheckpoint start(String crawlRunId, CrawlType crawlType) {
        return CrawlCheckpoint.builder()
                .crawlRunId(crawlRunId)
                .crawlType(crawlType)
                .status(CrawlStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
    }

    // === 상태 변경 메서드: 자기 상태는 자기가 변경 ===

    public void updateProgress(int page, String noticeNo) {
        this.lastPage = page;
        this.lastNoticeNo = noticeNo;
    }

    public void incrementCollected() {
        this.totalCollected++;
    }

    public void incrementFailed() {
        this.totalFailed++;
    }

    public void complete() {
        this.status = CrawlStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = CrawlStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public void stop() {
        this.status = CrawlStatus.STOPPED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isRunning() {
        return CrawlStatus.RUNNING.equals(this.status);
    }

    public void resume() {
        this.status = CrawlStatus.RUNNING;
        this.completedAt = null;
        this.errorMessage = null;
    }
}
