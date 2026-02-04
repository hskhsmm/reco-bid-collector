package io.reco.collector.domain.checkpoint;

/**
 * 크롤링 유형
 */
public enum CrawlType {
    FULL,        // 전체 수집
    INCREMENTAL  // 증분 수집
}
