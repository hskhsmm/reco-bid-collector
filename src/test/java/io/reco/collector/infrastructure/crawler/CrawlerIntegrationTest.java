package io.reco.collector.infrastructure.crawler;

import io.reco.collector.domain.bidding.dto.BiddingNoticeDto;
import io.reco.collector.infrastructure.crawler.config.CrawlerProperties;
import io.reco.collector.infrastructure.crawler.config.SearchFilter;
import io.reco.collector.infrastructure.crawler.config.WebDriverConfig;
import io.reco.collector.infrastructure.crawler.parser.DetailPageParser;
import io.reco.collector.infrastructure.crawler.parser.ListPageParser;
import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 크롤러 통합 테스트 - 실제 누리장터 사이트 연동
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrawlerIntegrationTest {

    @Autowired
    private WebDriverConfig webDriverConfig;

    @Autowired
    private ListPageParser listPageParser;

    @Autowired
    private DetailPageParser detailPageParser;

    @Autowired
    private CrawlerProperties crawlerProperties;

    private static WebDriver driver;

    @BeforeAll
    static void setUpDriver(@Autowired WebDriverConfig config) {
        driver = config.createWebDriver();
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 입찰공고목록 페이지 이동")
    void 입찰공고목록_페이지_이동() {
        // when
        listPageParser.navigateToListPage(driver);

        // then
        String pageSource = driver.getPageSource();
        assertThat(pageSource).contains("입찰공고");
        System.out.println("✅ 입찰공고목록 페이지 이동 성공");
    }

    @Test
    @Order(2)
    @DisplayName("2. 검색 실행")
    void 검색_실행() throws InterruptedException {
        // when
        listPageParser.search(driver);
        Thread.sleep(2000); // 결과 로드 대기

        // then
        String pageSource = driver.getPageSource();
        assertThat(pageSource).containsAnyOf("입찰공고번호", "조회결과");
        System.out.println("✅ 검색 실행 성공");
    }

    @Test
    @Order(3)
    @DisplayName("3. 목록 파싱")
    void 목록_파싱() throws Exception {
        // 디버깅: 현재 URL과 페이지 일부 출력
        System.out.println("\n🔍 현재 URL: " + driver.getCurrentUrl());
        System.out.println("🔍 페이지 타이틀: " + driver.getTitle());

        // 테이블 HTML 일부 확인
        try {
            String gridHtml = driver.findElement(
                    org.openqa.selenium.By.id("mf_wfm_container_grdBidPbancList")).getAttribute("outerHTML");
            System.out.println("🔍 그리드 HTML (처음 2000자):");
            System.out.println(gridHtml.substring(0, Math.min(2000, gridHtml.length())));
        } catch (Exception e) {
            System.out.println("🔍 그리드를 찾을 수 없음: " + e.getMessage());
        }

        // when
        List<ListPageParser.ListItem> items = listPageParser.parseListPage(driver);

        // then
        System.out.println("\n📋 목록 파싱 결과: " + items.size() + "건");
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            ListPageParser.ListItem item = items.get(i);
            System.out.printf("  %d. %s: %s (%s)%n",
                    i + 1,
                    item.getBidNoticeNo(),
                    item.getBidNoticeName(),
                    item.getProgressStatus());
        }

        assertThat(items).isNotEmpty();
        assertThat(items.get(0).getBidNoticeNo()).isNotBlank();
        System.out.println("✅ 목록 파싱 성공");
    }

    @Test
    @Order(4)
    @DisplayName("4. 검색 폼 element ID 탐색")
    void 검색_폼_엘리먼트_탐색() {
        // 목록 페이지로 돌아가기
        listPageParser.navigateToListPage(driver);

        // 검색 폼 영역의 모든 input, select, button 요소 ID 출력
        System.out.println("\n🔎 === 검색 폼 Element ID 탐색 ===");

        // input 요소
        var inputs = driver.findElements(org.openqa.selenium.By.cssSelector("input[id*='mf_wfm_container']"));
        System.out.println("\n📝 Input 요소:");
        for (var el : inputs) {
            String id = el.getAttribute("id");
            String type = el.getAttribute("type");
            String value = el.getAttribute("value");
            String placeholder = el.getAttribute("placeholder");
            if (id != null && !id.isBlank() && !"hidden".equals(type)) {
                System.out.printf("  - id=%s, type=%s, value=%s, placeholder=%s%n", id, type, value, placeholder);
            }
        }

        // select 요소 (WebSquare는 select 대신 div 기반 콤보 사용)
        var selects = driver.findElements(org.openqa.selenium.By.cssSelector("select[id*='mf_wfm_container']"));
        System.out.println("\n📋 Select 요소:");
        for (var el : selects) {
            String id = el.getAttribute("id");
            System.out.printf("  - id=%s%n", id);
        }

        // WebSquare 콤보박스 (일반적으로 div.w2selectbox)
        var combos = driver.findElements(org.openqa.selenium.By.cssSelector("[id*='mf_wfm_container'][class*='select'], [id*='mf_wfm_container'][class*='combo']"));
        System.out.println("\n🔽 Combo/Select 요소:");
        for (var el : combos) {
            String id = el.getAttribute("id");
            String cls = el.getAttribute("class");
            System.out.printf("  - id=%s, class=%s%n", id, cls);
        }

        // 버튼 요소 (기간 버튼 포함)
        var buttons = driver.findElements(org.openqa.selenium.By.cssSelector("button[id*='mf_wfm_container'], a[id*='mf_wfm_container']"));
        System.out.println("\n🔘 Button 요소:");
        for (var el : buttons) {
            String id = el.getAttribute("id");
            String text = el.getText().trim();
            if (id != null && !id.isBlank()) {
                System.out.printf("  - id=%s, text=%s%n", id, text);
            }
        }

        // JavaScript로 WebSquare 컴포넌트 목록 조회
        try {
            org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
            Object result = js.executeScript(
                    "var ids = [];" +
                    "var all = document.querySelectorAll('[id*=\"mf_wfm_container\"]');" +
                    "all.forEach(function(el) {" +
                    "  var id = el.id; var tag = el.tagName;" +
                    "  if(id && (id.includes('cbo') || id.includes('txt') || id.includes('btn') || id.includes('rdo') || id.includes('chk') || id.includes('cal'))) {" +
                    "    ids.push(id + ' [' + tag + ']');" +
                    "  }" +
                    "});" +
                    "return ids.join('\\n');");
            System.out.println("\n🧩 WebSquare 컴포넌트 (cbo/txt/btn/rdo/chk/cal):");
            System.out.println(result);
        } catch (Exception e) {
            System.out.println("JS 실행 실패: " + e.getMessage());
        }

        System.out.println("\n✅ Element ID 탐색 완료");
    }

    @Test
    @Order(5)
    @DisplayName("5. 키워드 필터 검색 테스트")
    void 키워드_필터_검색() throws InterruptedException {
        // given
        SearchFilter filter = SearchFilter.builder()
                .keyword("환경")
                .businessType("용역")
                .build();

        // when - 필터 적용 검색
        listPageParser.navigateToListPage(driver);
        listPageParser.search(driver, filter);
        Thread.sleep(2000);

        List<ListPageParser.ListItem> items = listPageParser.parseListPage(driver);

        // then
        System.out.println("\n🔍 필터 검색 결과 (keyword=환경, businessType=용역): " + items.size() + "건");
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            ListPageParser.ListItem item = items.get(i);
            System.out.printf("  %d. %s: %s (%s)%n",
                    i + 1, item.getBidNoticeNo(), item.getBidNoticeName(), item.getProgressStatus());
        }

        // 결과가 있으면 공고명에 "환경" 포함 여부 확인
        if (!items.isEmpty()) {
            boolean hasKeyword = items.stream()
                    .anyMatch(item -> item.getBidNoticeName().contains("환경"));
            System.out.println("  ➡ '환경' 키워드 포함 여부: " + hasKeyword);
        }

        System.out.println("✅ 키워드 필터 검색 테스트 완료");
    }

    @Test
    @Order(6)
    @DisplayName("6. 상세 페이지 이동 및 파싱")
    void 상세_페이지_파싱() throws InterruptedException {
        // given - 첫 번째 공고 클릭
        listPageParser.clickDetail(driver, 0);
        Thread.sleep(2000); // 페이지 로드 대기

        // when
        BiddingNoticeDto dto = detailPageParser.parseDetailPage(driver, null);

        // then
        System.out.println("\n📄 상세 파싱 결과:");
        if (dto != null) {
            System.out.println("  - 공고번호: " + dto.getBidNoticeNo());
            System.out.println("  - 공고명: " + dto.getBidNoticeName());
            System.out.println("  - 업무분류: " + dto.getBusinessType());
            System.out.println("  - 계약방법: " + dto.getContractMethod());
            System.out.println("  - 마감일시: " + dto.getBidEndDt());
            System.out.println("  - 담당부서: " + dto.getOrgName());
            System.out.println("  - 담당자: " + dto.getManagerName());
            System.out.println("  - 첨부파일: " + (dto.getAttachments() != null ? dto.getAttachments().size() : 0) + "개");

            if (dto.getAttachments() != null) {
                for (BiddingNoticeDto.AttachmentDto att : dto.getAttachments()) {
                    System.out.println("    - [" + att.getDocType() + "] " + att.getFileName() + " (" + att.getFileSize() + " bytes)");
                }
            }

            // JSON 섹션별 출력
            System.out.println("\n📦 JSON 섹션:");
            System.out.println("  - noticeInfo: " + dto.getNoticeInfo());
            System.out.println("  - scheduleInfo: " + dto.getScheduleInfo());
            System.out.println("  - amountInfo: " + dto.getAmountInfo());
            System.out.println("  - detailInfo: " + dto.getDetailInfo());
            System.out.println("  - rawPayload 길이: " + (dto.getRawPayload() != null ? dto.getRawPayload().length() : 0) + "자");
        }

        assertThat(dto).isNotNull();
        assertThat(dto.getBidNoticeNo()).isNotBlank();
        // JSON 컬럼이 실제로 채워졌는지 검증
        assertThat(dto.getRawPayload()).isNotNull();
        System.out.println("✅ 상세 파싱 성공");
    }
}
