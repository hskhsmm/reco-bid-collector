package io.reco.collector.domain.checkpoint.repository;

import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawlCheckpointRepository extends JpaRepository<CrawlCheckpoint, Long> {

    // 배치 ID로 조회
    Optional<CrawlCheckpoint> findByCrawlRunId(String crawlRunId);

    // 실행 중인 크롤링 찾기 (중단 후 재개용)
    Optional<CrawlCheckpoint> findFirstByStatusOrderByStartedAtDesc(CrawlStatus status);

    // 가장 최근 크롤링 조회
    Optional<CrawlCheckpoint> findFirstByOrderByStartedAtDesc();
}
