package io.reco.collector.infrastructure.crawler;

import io.reco.collector.domain.bidding.dto.BiddingNoticeDto;
import io.reco.collector.domain.bidding.service.BiddingNoticeService;
import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.service.CheckpointService;
import io.reco.collector.infrastructure.crawler.config.CrawlerProperties;
import io.reco.collector.infrastructure.crawler.config.WebDriverConfig;
import io.reco.collector.infrastructure.crawler.parser.DetailPageParser;
import io.reco.collector.infrastructure.crawler.parser.ListPageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 크롤링 서비스 - 전체 크롤링 흐름 조율
 *
 * 주요 기능:
 * - 목록 → 상세 순차 크롤링
 * - 체크포인트 기반 중단점 복구
 * - 중복 방지
 * - 재시도 로직
 * - 스케줄링 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final WebDriverConfig webDriverConfig;
    private final CrawlerProperties crawlerProperties;
    private final ListPageParser listPageParser;
    private final DetailPageParser detailPageParser;
    private final CheckpointService checkpointService;
    private final BiddingNoticeService biddingNoticeService;

    /**
     * 스케줄링 크롤링 (cron 표현식으로 주기적 실행)
     * 기본: 매일 새벽 2시
     */
    @Scheduled(cron = "${crawler.schedule.cron:0 0 2 * * *}")
    public void scheduledScrape() {
        log.info("=== 스케줄 크롤링 시작 ===");
        scrape(CrawlType.INCREMENTAL);
    }

    /**
     * 전체 크롤링 실행
     */
    public void scrapeAll() {
        scrape(CrawlType.FULL);
    }

    /**
     * 증분 크롤링 실행
     */
    public void scrapeIncremental() {
        scrape(CrawlType.INCREMENTAL);
    }

    /**
     * 메인 크롤링 로직
     */
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public void scrape(CrawlType crawlType) {
        WebDriver driver = null;
        CrawlCheckpoint checkpoint = null;

        try {
            // 1. 체크포인트 확인/생성
            checkpoint = getOrCreateCheckpoint(crawlType);
            String crawlRunId = checkpoint.getCrawlRunId();
            log.info("크롤링 시작 - ID: {}, 타입: {}, 시작 페이지: {}",
                    crawlRunId, crawlType, checkpoint.getLastPage());

            // 2. WebDriver 생성
            driver = webDriverConfig.createWebDriver();

            // 3. 목록 페이지 이동
            listPageParser.navigateToListPage(driver);
            listPageParser.search(driver);

            // 4. 페이지 순회하며 크롤링
            int currentPage = checkpoint.getLastPage() > 0 ? checkpoint.getLastPage() : 1;
            boolean hasMorePages = true;

            while (hasMorePages) {
                log.info("--- {}페이지 처리 중 ---", currentPage);

                // 목록 파싱
                List<ListPageParser.ListItem> items = listPageParser.parseListPage(driver);
                log.info("{}페이지에서 {}건 발견", currentPage, items.size());

                if (items.isEmpty()) {
                    log.warn("파싱된 항목이 없습니다. 셀렉터를 확인하세요.");
                }

                // 각 공고 처리
                for (int i = 0; i < items.size(); i++) {
                    ListPageParser.ListItem item = items.get(i);
                    String bidNoticeNo = item.getBidNoticeNo();

                    try {
                        // 중복 체크
                        if (biddingNoticeService.exists(bidNoticeNo)) {
                            log.debug("중복 스킵: {}", bidNoticeNo);
                            continue;
                        }

                        // 상세 페이지 이동 및 파싱
                        listPageParser.clickDetail(driver, i);
                        BiddingNoticeDto dto = detailPageParser.parseDetailPage(driver);

                        if (dto != null && dto.getBidNoticeNo() != null) {
                            // DB 저장
                            biddingNoticeService.saveIfNotExists(dto, checkpoint);
                            checkpointService.incrementCollected(crawlRunId);
                            log.info("저장 완료: {} - {}", dto.getBidNoticeNo(), dto.getBidNoticeName());
                        }

                        // 목록으로 복귀
                        detailPageParser.goBackToList(driver);
                        Thread.sleep(crawlerProperties.getRequestDelay());

                    } catch (Exception e) {
                        log.error("공고 처리 실패: {} - {}", bidNoticeNo, e.getMessage());
                        checkpointService.incrementFailed(crawlRunId);
                        // 실패해도 계속 진행
                        try {
                            driver.navigate().back();
                            Thread.sleep(crawlerProperties.getRequestDelay());
                        } catch (Exception ignored) {}
                    }

                    // 진행 상황 업데이트 (매 항목마다)
                    checkpointService.updateProgress(crawlRunId, currentPage, bidNoticeNo);
                }

                // 다음 페이지 이동
                hasMorePages = listPageParser.goToNextPage(driver, currentPage);
                if (hasMorePages) {
                    currentPage++;
                    checkpointService.updateProgress(crawlRunId, currentPage, null);
                }
            }

            // 5. 완료 처리
            checkpointService.completeCrawl(crawlRunId);
            log.info("=== 크롤링 완료 - 수집: {}건, 실패: {}건 ===",
                    checkpoint.getTotalCollected(), checkpoint.getTotalFailed());

        } catch (Exception e) {
            log.error("크롤링 실패: {}", e.getMessage(), e);
            if (checkpoint != null) {
                checkpointService.failCrawl(checkpoint.getCrawlRunId(), e.getMessage());
            }
            throw new RuntimeException("크롤링 실패", e);

        } finally {
            // WebDriver 정리
            if (driver != null) {
                try {
                    driver.quit();
                    log.debug("WebDriver 종료");
                } catch (Exception e) {
                    log.warn("WebDriver 종료 실패: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 체크포인트 확인/생성
     * - 재개 가능한 체크포인트가 있으면 이어서 진행
     * - 없으면 새로 생성
     */
    private CrawlCheckpoint getOrCreateCheckpoint(CrawlType crawlType) {
        return checkpointService.findResumable()
                .map(cp -> {
                    log.info("이전 체크포인트에서 이어서 진행: runId={}, page={}, collected={}",
                            cp.getCrawlRunId(), cp.getLastPage(), cp.getTotalCollected());
                    return checkpointService.resumeCrawl(cp.getCrawlRunId());
                })
                .orElseGet(() -> {
                    log.info("새 크롤링 시작");
                    return checkpointService.startNewCrawl(crawlType);
                });
    }

    /**
     * 수동 중지
     */
    public void stop() {
        checkpointService.findResumable()
                .filter(CrawlCheckpoint::isRunning)
                .ifPresent(cp -> {
                    checkpointService.stopCrawl(cp.getCrawlRunId());
                    log.info("크롤링 중지됨: {}", cp.getCrawlRunId());
                });
    }
}
