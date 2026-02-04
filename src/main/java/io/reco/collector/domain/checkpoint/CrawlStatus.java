package io.reco.collector.domain.checkpoint;

/**
 * 크롤링 상태
 */
public enum CrawlStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPED
}
