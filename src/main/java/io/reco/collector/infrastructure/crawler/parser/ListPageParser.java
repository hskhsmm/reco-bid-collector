package io.reco.collector.infrastructure.crawler.parser;

import io.reco.collector.infrastructure.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 목록 페이지 파서 - 누리장터 입찰공고 목록 파싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListPageParser {

    private final CrawlerProperties crawlerProperties;

    // 메뉴 ID
    private static final String MENU_BID_ID = "mf_wfm_gnb_wfm_gnbMenu_genDepth1_1_btn_menuLvl1";
    private static final String MENU_BID_LIST_ID = "mf_wfm_gnb_wfm_gnbMenu_genDepth1_1_genDepth2_0_genDepth3_0_btn_menuLvl3";

    // 검색 버튼 ID
    private static final String BTN_SEARCH_ID = "mf_wfm_container_btn00001";

    // 테이블 관련
    private static final String GRID_ID = "mf_wfm_container_grdBidPbancList";

    /**
     * 입찰공고목록 페이지로 이동
     */
    public void navigateToListPage(WebDriver driver) {
        try {
            driver.get(crawlerProperties.getBaseUrl());

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(crawlerProperties.getImplicitWait()));

            // 1. 입찰공고 메뉴 hover
            WebElement menu = wait.until(ExpectedConditions.elementToBeClickable(By.id(MENU_BID_ID)));
            Actions actions = new Actions(driver);
            actions.moveToElement(menu).perform();

            Thread.sleep(500); // 드롭다운 열리기 대기

            // 2. 입찰공고목록 클릭
            WebElement subMenu = wait.until(ExpectedConditions.elementToBeClickable(By.id(MENU_BID_LIST_ID)));
            subMenu.click();

            Thread.sleep(crawlerProperties.getRequestDelay());
            log.info("입찰공고목록 페이지 이동 완료");

        } catch (Exception e) {
            log.error("페이지 이동 실패: {}", e.getMessage());
            throw new RuntimeException("입찰공고목록 페이지 이동 실패", e);
        }
    }

    /**
     * 검색 실행
     */
    public void search(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(crawlerProperties.getImplicitWait()));

            // 검색 버튼 클릭 시도 (없으면 이미 데이터 로드된 상태일 수 있음)
            try {
                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id(BTN_SEARCH_ID)));
                searchBtn.click();
                log.debug("검색 버튼 클릭");
            } catch (Exception e) {
                log.warn("검색 버튼 못 찾음 - 이미 데이터가 로드된 상태일 수 있음: {}", e.getMessage());
            }

            // 결과 로드 대기 - 그리드 테이블 기준
            Thread.sleep(crawlerProperties.getRequestDelay());
            String gridSelector = String.format("#%s table tbody tr", GRID_ID);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(gridSelector)));

            log.info("검색 완료");

        } catch (Exception e) {
            log.error("검색 실패: {}", e.getMessage());
        }
    }

    /**
     * 목록 페이지에서 공고 항목들 파싱
     */
    public List<ListItem> parseListPage(WebDriver driver) {
        List<ListItem> items = new ArrayList<>();

        try {
            // 그리드 컨테이너 안의 테이블 찾기
            String gridSelector = String.format("#%s table tbody tr", GRID_ID);
            List<WebElement> rows = driver.findElements(By.cssSelector(gridSelector));

            log.debug("검색 결과 행 수: {}", rows.size());

            for (int i = 0; i < rows.size(); i++) {
                try {
                    WebElement row = rows.get(i);
                    ListItem item = parseRow(row);

                    // null 체크 + 빈 문자열 체크
                    if (item != null && item.getBidNoticeNo() != null && !item.getBidNoticeNo().isBlank()) {
                        items.add(item);
                        log.debug("행 {} 파싱 성공: {}", i, item.getBidNoticeNo());
                    } else {
                        log.debug("행 {} 스킵 (빈 데이터)", i);
                    }
                } catch (Exception e) {
                    log.warn("행 {} 파싱 실패: {}", i, e.getMessage());
                }
            }

            log.info("목록 파싱 완료: {}건", items.size());

        } catch (Exception e) {
            log.error("목록 파싱 실패: {}", e.getMessage());
        }

        return items;
    }

    /**
     * 테이블 행에서 공고 정보 추출
     */
    private ListItem parseRow(WebElement row) {
        List<WebElement> cells = row.findElements(By.tagName("td"));

        // 디버그: 셀 개수와 내용 확인
        log.debug("셀 개수: {}", cells.size());
        if (cells.size() > 1) {
            String cell0 = getCellText(cells.get(0));
            String cell1 = getCellText(cells.get(1));
            log.debug("첫 번째 셀: [{}], 두 번째 셀: [{}]", cell0, cell1);
        }

        if (cells.size() < 10) {
            log.debug("셀 개수 부족으로 스킵 (필요: 10, 실제: {})", cells.size());
            return null;
        }

        // 컬럼 순서: No, 입찰공고번호, 입찰공고명, 공고분류, 입찰방식, 진행상태, 공고종류, 계약방법, 낙찰방법, 개찰, 기관명
        String detailUrl = extractDetailUrl(cells.get(2)); // 입찰공고명 셀에서 링크

        return ListItem.builder()
                .bidNoticeNo(getCellText(cells.get(1)))
                .bidNoticeName(getCellText(cells.get(2)))
                .noticeType(getCellText(cells.get(3)))      // 공고분류
                .bidMethod(getCellText(cells.get(4)))       // 입찰방식
                .progressStatus(getCellText(cells.get(5)))  // 진행상태
                .noticeKind(getCellText(cells.get(6)))      // 공고종류
                .contractMethod(getCellText(cells.get(7)))  // 계약방법
                .awardMethod(getCellText(cells.get(8)))     // 낙찰방법
                .orgName(cells.size() > 10 ? getCellText(cells.get(10)) : null)
                .detailUrl(detailUrl)
                .build();
    }

    /**
     * 셀에서 텍스트 추출 (span, input 등 하위 요소 고려)
     * findElements 사용으로 implicit wait 없이 즉시 반환
     */
    private String getCellText(WebElement cell) {
        // 1. 먼저 직접 getText 시도
        String text = cell.getText().trim();
        if (!text.isEmpty()) {
            return text;
        }

        // 2. span 요소 찾기 (findElements는 없으면 빈 리스트 반환 - implicit wait 없음)
        List<WebElement> spans = cell.findElements(By.tagName("span"));
        if (!spans.isEmpty()) {
            text = spans.get(0).getText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        // 3. a 링크 텍스트
        List<WebElement> links = cell.findElements(By.tagName("a"));
        if (!links.isEmpty()) {
            text = links.get(0).getText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }

        // 4. input value 속성
        List<WebElement> inputs = cell.findElements(By.tagName("input"));
        if (!inputs.isEmpty()) {
            text = inputs.get(0).getAttribute("value");
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }

        return "";
    }

    /**
     * 상세 페이지 URL 또는 onclick 함수 추출
     */
    private String extractDetailUrl(WebElement cell) {
        try {
            WebElement link = cell.findElement(By.tagName("a"));
            String href = link.getAttribute("href");
            String onclick = link.getAttribute("onclick");

            // javascript:void 면 onclick에서 정보 추출
            if (href != null && !href.contains("javascript")) {
                return href;
            }
            return onclick; // onclick 함수 반환 (나중에 처리)

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 공고명 클릭하여 상세 페이지 이동
     */
    public void clickDetail(WebDriver driver, int rowIndex) {
        try {
            String gridSelector = String.format("#%s table tbody tr", GRID_ID);
            List<WebElement> rows = driver.findElements(By.cssSelector(gridSelector));

            if (rowIndex < rows.size()) {
                WebElement row = rows.get(rowIndex);
                // 입찰공고명 컬럼(3번째)의 링크 클릭
                WebElement link = row.findElement(By.cssSelector("td:nth-child(3) a"));
                link.click();

                Thread.sleep(crawlerProperties.getRequestDelay());
                log.debug("상세 페이지 이동: row {}", rowIndex);
            }
        } catch (Exception e) {
            log.error("상세 페이지 이동 실패: {}", e.getMessage());
        }
    }

    /**
     * 다음 페이지로 이동
     * @return 다음 페이지 존재 여부
     */
    public boolean goToNextPage(WebDriver driver, int currentPage) {
        try {
            // 페이지 번호 링크 찾기
            String nextPageSelector = String.format("a[onclick*='%d']", currentPage + 1);
            WebElement nextPageLink = driver.findElement(By.cssSelector(nextPageSelector));
            nextPageLink.click();

            Thread.sleep(crawlerProperties.getRequestDelay());
            log.debug("{}페이지로 이동", currentPage + 1);
            return true;

        } catch (NoSuchElementException e) {
            log.info("마지막 페이지: {}", currentPage);
            return false;
        } catch (Exception e) {
            log.warn("페이지 이동 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 목록 아이템 DTO
     */
    @lombok.Getter
    @lombok.Builder
    public static class ListItem {
        private String bidNoticeNo;      // 입찰공고번호
        private String bidNoticeName;    // 입찰공고명
        private String noticeType;       // 공고분류 (변경공고, 등록공고)
        private String bidMethod;        // 입찰방식 (전자입찰, 직찰)
        private String progressStatus;   // 진행상태
        private String noticeKind;       // 공고종류 (실공고, 모의공고)
        private String contractMethod;   // 계약방법 (일반경쟁, 지명경쟁)
        private String awardMethod;      // 낙찰방법 (적격심사제, 최저가낙찰)
        private String orgName;          // 기관명
        private String detailUrl;        // 상세 페이지 URL 또는 onclick
    }
}
