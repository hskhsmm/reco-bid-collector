package io.reco.collector.domain.checkpoint.service;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlStatus;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.repository.CrawlCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class CheckpointServiceTest {

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private CrawlCheckpointRepository checkpointRepository;

    @Test
    @DisplayName("새 크롤링 시작 - 정상 케이스")
    void 새_크롤링_시작_성공() {
        // when
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);

        // then
        assertThat(checkpoint.getId()).isNotNull();
        assertThat(checkpoint.getCrawlRunId()).startsWith("crawl_");
        assertThat(checkpoint.getCrawlType()).isEqualTo(CrawlType.FULL);
        assertThat(checkpoint.getStatus()).isEqualTo(CrawlStatus.RUNNING);
        assertThat(checkpoint.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("새 크롤링 시작 - 이미 실행 중이면 예외")
    void 이미_실행중이면_새_크롤링_시작_실패() {
        // given
        checkpointService.startNewCrawl(CrawlType.FULL);

        // when & then
        assertThatThrownBy(() -> checkpointService.startNewCrawl(CrawlType.INCREMENTAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 실행 중인 크롤링");
    }

    @Test
    @DisplayName("진행 상황 업데이트")
    void 진행_상황_업데이트() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);

        // when
        checkpointService.updateProgress(checkpoint.getCrawlRunId(), 5, "BID-2024-001");

        // then
        CrawlCheckpoint updated = checkpointRepository.findByCrawlRunId(checkpoint.getCrawlRunId()).get();
        assertThat(updated.getLastPage()).isEqualTo(5);
        assertThat(updated.getLastNoticeNo()).isEqualTo("BID-2024-001");
    }

    @Test
    @DisplayName("수집 카운트 증가")
    void 수집_성공_실패_카운트_증가() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);
        String runId = checkpoint.getCrawlRunId();

        // when
        checkpointService.incrementCollected(runId);
        checkpointService.incrementCollected(runId);
        checkpointService.incrementFailed(runId);

        // then
        CrawlCheckpoint updated = checkpointRepository.findByCrawlRunId(runId).get();
        assertThat(updated.getTotalCollected()).isEqualTo(2);
        assertThat(updated.getTotalFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("크롤링 완료 처리")
    void 크롤링_완료_처리() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);

        // when
        checkpointService.completeCrawl(checkpoint.getCrawlRunId());

        // then
        CrawlCheckpoint completed = checkpointRepository.findByCrawlRunId(checkpoint.getCrawlRunId()).get();
        assertThat(completed.getStatus()).isEqualTo(CrawlStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("크롤링 실패 처리")
    void 크롤링_실패_처리() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);

        // when
        checkpointService.failCrawl(checkpoint.getCrawlRunId(), "Connection timeout");

        // then
        CrawlCheckpoint failed = checkpointRepository.findByCrawlRunId(checkpoint.getCrawlRunId()).get();
        assertThat(failed.getStatus()).isEqualTo(CrawlStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Connection timeout");
    }

    @Test
    @DisplayName("크롤링 수동 중지")
    void 크롤링_수동_중지() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);

        // when
        checkpointService.stopCrawl(checkpoint.getCrawlRunId());

        // then
        CrawlCheckpoint stopped = checkpointRepository.findByCrawlRunId(checkpoint.getCrawlRunId()).get();
        assertThat(stopped.getStatus()).isEqualTo(CrawlStatus.STOPPED);
    }

    @Test
    @DisplayName("재개 가능한 크롤링 찾기 - STOPPED 우선")
    void 재개_가능한_크롤링_찾기_STOPPED_우선() {
        // given
        CrawlCheckpoint first = checkpointService.startNewCrawl(CrawlType.FULL);
        checkpointService.stopCrawl(first.getCrawlRunId());

        // when
        Optional<CrawlCheckpoint> resumable = checkpointService.findResumable();

        // then
        assertThat(resumable).isPresent();
        assertThat(resumable.get().getStatus()).isEqualTo(CrawlStatus.STOPPED);
    }

    @Test
    @DisplayName("크롤링 재개")
    void 크롤링_재개_성공() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);
        checkpointService.updateProgress(checkpoint.getCrawlRunId(), 3, "BID-001");
        checkpointService.stopCrawl(checkpoint.getCrawlRunId());

        // when
        CrawlCheckpoint resumed = checkpointService.resumeCrawl(checkpoint.getCrawlRunId());

        // then
        assertThat(resumed.getStatus()).isEqualTo(CrawlStatus.RUNNING);
        assertThat(resumed.getLastPage()).isEqualTo(3); // 진행 상황 유지
        assertThat(resumed.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("완료된 크롤링은 재개 불가")
    void 완료된_크롤링_재개_불가() {
        // given
        CrawlCheckpoint checkpoint = checkpointService.startNewCrawl(CrawlType.FULL);
        checkpointService.completeCrawl(checkpoint.getCrawlRunId());

        // when & then
        assertThatThrownBy(() -> checkpointService.resumeCrawl(checkpoint.getCrawlRunId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 크롤링");
    }
}
