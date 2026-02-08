package io.reco.collector.infrastructure.crawler.parser;

import io.reco.collector.infrastructure.crawler.config.CrawlerProperties;
import io.reco.collector.infrastructure.crawler.config.SearchFilter;
import lombok.Builder;
import lombok.Getter;
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
    private static final String BTN_SEARCH_ID = "mf_wfm_container_btnS0001";

    // 테이블 관련 - WebSquare 그리드
    private static final String GRID_BODY_SELECTOR = "#mf_wfm_container_grdBidPbancList_body_tbody tr";

    // 컬럼 col_id 매핑
    private static final String COL_BID_NO = "bidPbancNum";           // 입찰공고번호
    private static final String COL_BID_NAME = "bidPbancNm";          // 입찰공고명
    private static final String COL_NOTICE_TYPE = "prcmBsneSeCdNm";   // 공고분류 (물품/용역/공사)
    private static final String COL_PROGRESS_STATUS = "pbancSttsGridCdNm"; // 진행상태
    private static final String COL_ORG_NAME = "grpNm";               // 기관명
    private static final String COL_NOTICE_DATE = "pbancPstgDt";      // 공고게시일시

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
     * 검색 실행 (기본 필터)
     */
    public void search(WebDriver driver) {
        search(driver, SearchFilter.defaults());
    }

    /**
     * 검색 실행 - 필터 조건을 폼에 입력한 뒤 검색
     *
     * 누리장터 WebSquare 폼은 JavaScript로 값을 설정해야 정상 반영된다.
     * - input: wfm_container 접두사 + 필드ID 패턴
     * - select/combobox: WebSquare API setValue() 호출
     * - 기간 버튼: 1개월/3개월/6개월 클릭
     */
    public void search(WebDriver driver, SearchFilter filter) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(crawlerProperties.getImplicitWait()));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. 입찰공고명 키워드 입력
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                setInputValue(js, "mf_wfm_container_tbxBidPbancNm", filter.getKeyword());
                log.debug("키워드 입력: {}", filter.getKeyword());
            }

            // 2. 공고분류 (물품/용역/공사/전체) - w2selectbox
            if (!"전체".equals(filter.getBusinessType())) {
                setSelectBoxByText(js, "mf_wfm_container_sbxBsneClsf", filter.getBusinessType());
                log.debug("공고분류 설정: {}", filter.getBusinessType());
            }

            // 3. 진행상태 (입찰개시/개찰완료/전체) - w2selectbox
            if (!"전체".equals(filter.getProgressStatus())) {
                setSelectBoxByText(js, "mf_wfm_container_sbxPrgrsStts", filter.getProgressStatus());
                log.debug("진행상태 설정: {}", filter.getProgressStatus());
            }

            // 4. 공고구분 (등록공고/변경공고/전체) - w2selectbox
            if (!"전체".equals(filter.getNoticeType())) {
                setSelectBoxByText(js, "mf_wfm_container_tbxPbancKndCd", filter.getNoticeType());
                log.debug("공고구분 설정: {}", filter.getNoticeType());
            }

            // 5. 공고게시일자 기간 버튼 (1개월/3개월/6개월)
            if (filter.getPeriodMonths() != 1) {
                clickPeriodButton(driver, filter.getPeriodMonths());
                log.debug("기간 설정: {}개월", filter.getPeriodMonths());
            }

            // 6. 검색 버튼 클릭
            try {
                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id(BTN_SEARCH_ID)));
                searchBtn.click();
                log.debug("검색 버튼 클릭");
            } catch (Exception e) {
                log.warn("검색 버튼 못 찾음 - 이미 데이터가 로드된 상태일 수 있음: {}", e.getMessage());
            }

            // 결과 로드 대기
            Thread.sleep(crawlerProperties.getRequestDelay());
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(GRID_BODY_SELECTOR)));

            log.info("검색 완료 (필터: keyword={}, businessType={}, progressStatus={}, period={}개월)",
                    filter.getKeyword(), filter.getBusinessType(),
                    filter.getProgressStatus(), filter.getPeriodMonths());

        } catch (Exception e) {
            log.error("검색 실패: {}", e.getMessage());
        }
    }

    /**
     * WebSquare input에 값 설정 - WebSquare API 사용
     */
    private void setInputValue(JavascriptExecutor js, String elementId, String value) {
        js.executeScript(
                "var comp = $w.getComponentById(arguments[0]);" +
                "if(comp && comp.setValue) { comp.setValue(arguments[1]); }" +
                "else {" +
                "  var el = document.getElementById(arguments[0]);" +
                "  if(el) { el.value = arguments[1]; " +
                "    el.dispatchEvent(new Event('change', {bubbles:true})); }" +
                "}",
                elementId, value);
    }

    /**
     * w2selectbox에 텍스트 기반으로 값 설정 - WebSquare API 사용
     * - getItemCount/getItemText로 option 순회
     * - setSelectedIndex로 선택 (내부 상태 동기화)
     */
    private void setSelectBoxByText(JavascriptExecutor js, String elementId, String displayText) {
        js.executeScript(
                "var comp = $w.getComponentById(arguments[0]);" +
                "if(comp && comp.getItemCount) {" +
                "  for(var i=0; i<comp.getItemCount(); i++) {" +
                "    if(comp.getItemText(i).trim() === arguments[1]) {" +
                "      comp.setSelectedIndex(i); break;" +
                "    }" +
                "  }" +
                "} else {" +
                "  var sel = document.getElementById(arguments[0]);" +
                "  if(sel) {" +
                "    for(var i=0; i<sel.options.length; i++) {" +
                "      if(sel.options[i].text.trim() === arguments[1]) {" +
                "        sel.selectedIndex = i;" +
                "        sel.dispatchEvent(new Event('change', {bubbles:true}));" +
                "        break;" +
                "      }" +
                "    }" +
                "  }" +
                "}",
                elementId, displayText);
    }

    /**
     * 기간 버튼 클릭 (1개월/3개월/6개월)
     */
    private void clickPeriodButton(WebDriver driver, int months) {
        try {
            // 버튼 텍스트로 찾기: "1개월", "3개월", "6개월"
            String buttonText = months + "개월";
            List<WebElement> buttons = driver.findElements(
                    By.xpath("//button[contains(text(),'" + buttonText + "')] | //a[contains(text(),'" + buttonText + "')]"));
            if (!buttons.isEmpty()) {
                buttons.get(0).click();
                Thread.sleep(500);
            } else {
                log.debug("기간 버튼 미발견: {}", buttonText);
            }
        } catch (Exception e) {
            log.debug("기간 버튼 클릭 실패: {}", e.getMessage());
        }
    }

    /**
     * 목록 페이지에서 공고 항목들 파싱
     */
    public List<ListItem> parseListPage(WebDriver driver) {
        List<ListItem> items = new ArrayList<>();

        try {
            // WebSquare 그리드 body에서 행 찾기
            List<WebElement> rows = driver.findElements(By.cssSelector(GRID_BODY_SELECTOR));

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
     * 테이블 행에서 공고 정보 추출 (col_id 속성 기반)
     */
    private ListItem parseRow(WebElement row) {
        // col_id 속성으로 셀 찾기
        String bidNoticeNo = getCellTextByColId(row, COL_BID_NO);
        String bidNoticeName = getCellTextByColId(row, COL_BID_NAME);
        String noticeType = getCellTextByColId(row, COL_NOTICE_TYPE);
        String progressStatus = getCellTextByColId(row, COL_PROGRESS_STATUS);
        String orgName = getCellTextByColId(row, COL_ORG_NAME);

        // 상세 페이지 링크 추출
        String detailUrl = extractDetailUrlByColId(row, COL_BID_NAME);

        log.debug("파싱: 공고번호={}, 공고명={}, 진행상태={}", bidNoticeNo, bidNoticeName, progressStatus);

        return ListItem.builder()
                .bidNoticeNo(bidNoticeNo)
                .bidNoticeName(bidNoticeName)
                .noticeType(noticeType)
                .progressStatus(progressStatus)
                .orgName(orgName)
                .detailUrl(detailUrl)
                .build();
    }

    /**
     * col_id 속성으로 셀 찾아서 텍스트 추출
     */
    private String getCellTextByColId(WebElement row, String colId) {
        try {
            String selector = String.format("td[col_id='%s']", colId);
            List<WebElement> cells = row.findElements(By.cssSelector(selector));
            if (!cells.isEmpty()) {
                return getCellText(cells.get(0));
            }
        } catch (Exception e) {
            log.debug("col_id={} 셀 찾기 실패", colId);
        }
        return "";
    }

    /**
     * col_id로 셀 찾아서 링크 URL 추출
     */
    private String extractDetailUrlByColId(WebElement row, String colId) {
        try {
            String selector = String.format("td[col_id='%s'] a", colId);
            List<WebElement> links = row.findElements(By.cssSelector(selector));
            if (!links.isEmpty()) {
                WebElement link = links.get(0);
                String href = link.getAttribute("href");
                if (href != null && !href.contains("javascript")) {
                    return href;
                }
                return link.getAttribute("onclick");
            }
        } catch (Exception e) {
            log.debug("상세 링크 추출 실패: col_id={}", colId);
        }
        return null;
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
     * 공고명 클릭하여 상세 페이지 이동
     */
    public void clickDetail(WebDriver driver, int rowIndex) {
        try {
            // 팝업이 클릭을 가로챌 수 있으므로 먼저 닫기
            closePopups(driver);

            List<WebElement> rows = driver.findElements(By.cssSelector(GRID_BODY_SELECTOR));

            if (rowIndex < rows.size()) {
                WebElement row = rows.get(rowIndex);
                // col_id='bidPbancNm' 컬럼의 링크 클릭
                String selector = String.format("td[col_id='%s'] a", COL_BID_NAME);
                WebElement link = row.findElement(By.cssSelector(selector));
                link.click();

                // SPA 페이지 전환 대기 (parseDetailPage에서 요소 출현 대기함)
                Thread.sleep(2000);
                log.debug("상세 페이지 이동: row {}", rowIndex);
            }
        } catch (Exception e) {
            log.error("상세 페이지 이동 실패: {}", e.getMessage());
        }
    }

    /**
     * 누리장터 팝업(공지사항 등) 닫기
     * - pop_contents 클래스 팝업의 닫기 버튼 클릭
     * - 팝업이 없으면 무시
     */
    private void closePopups(WebDriver driver) {
        try {
            List<WebElement> closeButtons = driver.findElements(
                    By.cssSelector("div.pop_contents button.btn_close, div.pop_contents .close, div.pop_contents [title='닫기']"));
            for (WebElement btn : closeButtons) {
                try {
                    if (btn.isDisplayed()) {
                        btn.click();
                        log.debug("팝업 닫기 완료");
                        Thread.sleep(300);
                    }
                } catch (Exception ignored) {
                }
            }

            // 닫기 버튼을 못 찾으면 JavaScript로 팝업 div 숨기기
            List<WebElement> popups = driver.findElements(By.cssSelector("div.pop_contents"));
            for (WebElement popup : popups) {
                try {
                    if (popup.isDisplayed()) {
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].closest('.w2window, .w2popup, div[style*=\"z-index\"]').style.display='none'",
                                popup);
                        log.debug("팝업 JavaScript로 숨김 처리");
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("팝업 닫기 시도 중 예외 (무시): {}", e.getMessage());
        }
    }

    /**
     * 다음 페이지로 이동 (WebSquare pagelist 컴포넌트)
     * @return 다음 페이지 존재 여부
     */
    public boolean goToNextPage(WebDriver driver, int currentPage) {
        try {
            // WebSquare pagelist의 "다음" 버튼
            WebElement nextBtn = driver.findElement(By.id("mf_wfm_container_pagelist_next_btn"));

            // 버튼이 비활성화(disabled) 상태이면 마지막 페이지
            String classList = nextBtn.getAttribute("class");
            if (classList != null && classList.contains("disabled")) {
                log.info("마지막 페이지: {}", currentPage);
                return false;
            }

            // a 태그를 찾아서 클릭 (LI 안에 a가 있음)
            List<WebElement> links = nextBtn.findElements(By.tagName("a"));
            if (!links.isEmpty()) {
                links.get(0).click();
            } else {
                nextBtn.click();
            }

            Thread.sleep(crawlerProperties.getRequestDelay());

            // 그리드 로드 대기
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(crawlerProperties.getImplicitWait()));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(GRID_BODY_SELECTOR)));

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
     * 목록 아이템 DTO (col_id 기반 매핑)
     */
    @Getter
    @Builder
    public static class ListItem {
        private String bidNoticeNo;      // 입찰공고번호 (bidPbancNum)
        private String bidNoticeName;    // 입찰공고명 (bidPbancNm)
        private String noticeType;       // 공고분류 - 물품/용역/공사 (prcmBsneSeCdNm)
        private String progressStatus;   // 진행상태 (pbancSttsGridCdNm)
        private String orgName;          // 기관명 (grpNm)
        private String detailUrl;        // 상세 페이지 URL 또는 onclick
    }
}
