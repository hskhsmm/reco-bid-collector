package io.reco.collector.infrastructure.crawler.parser;

import tools.jackson.databind.json.JsonMapper;
import io.reco.collector.domain.bidding.dto.BiddingNoticeDto;
import io.reco.collector.domain.bidding.enums.BusinessType;
import io.reco.collector.domain.bidding.enums.ContractMethod;
import io.reco.collector.domain.bidding.enums.ProgressStatus;
import io.reco.collector.infrastructure.crawler.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 상세 페이지 파서 - 누리장터 입찰공고 상세 정보 파싱
 *
 * 페이지 내 모든 td[data-title] 필드를 동적으로 추출하여
 * 4개 JSON 컬럼(notice_info, schedule_info, amount_info, detail_info)에 섹션별로 저장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DetailPageParser {

    private final CrawlerProperties crawlerProperties;
    private final JsonMapper jsonMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    // === 이미 전용 컬럼에 저장하는 필드 → JSON 분류에서 제외 ===
    private static final Set<String> PRIMARY_FIELDS = Set.of(
            "입찰공고번호", "입찰공고명", "업무분류", "계약방법",
            "입찰서접수시작일시", "입찰서접수마감일시", "담당부서"
    );

    // === 섹션별 분류 키 ===

    /** 공고 기본정보 → notice_info */
    private static final Set<String> NOTICE_KEYS = Set.of(
            "문서번호", "긴급입찰여부", "공고종류", "공고처리구분",
            "입찰방식", "낙찰방법", "재입찰여부", "개찰및낙찰-비고"
    );

    /** 일정정보 → schedule_info */
    private static final Set<String> SCHEDULE_KEYS = Set.of(
            "개찰일시", "개찰장소", "입찰보증서접수마감일시", "입찰참가자격등록마감일시"
    );
    /** SCHEDULE_KEYS에 포함시킬 접두사 패턴 */
    private static final String SCHEDULE_PREFIX = "현장설명회";

    /** 금액정보 → amount_info */
    private static final Set<String> AMOUNT_KEYS = Set.of(
            "부가가치세포함여부", "배정예산"
    );
    /** AMOUNT_KEYS에 포함시킬 접두사 패턴 */
    private static final String AMOUNT_PREFIX = "기준금액";

    // ========== 공개 메서드 ==========

    /**
     * 상세 페이지 파싱 - 모든 td[data-title] 필드를 동적으로 추출
     * @param driver WebDriver
     * @param progressStatusFromList 목록에서 가져온 진행상태 (한글)
     */
    public BiddingNoticeDto parseDetailPage(WebDriver driver, String progressStatusFromList) {
        // implicit wait를 0으로 설정하여 findElements 지연 방지
        // (셀마다 select/span 탐색 시 10초씩 대기하는 문제 해결)
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        try {
            // 상세 페이지 핵심 요소(입찰공고번호) 출현 대기
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(crawlerProperties.getPageLoadTimeout()));
            wait.until(d -> !d.findElements(By.cssSelector("td[data-title='입찰공고번호']")).isEmpty());

            BiddingNoticeDto.BiddingNoticeDtoBuilder builder = BiddingNoticeDto.builder();

            // 1. 페이지 전체 td[data-title] 필드 일괄 추출
            Map<String, String> allFields = extractAllDataTitleFields(driver);
            log.debug("추출된 필드 수: {}", allFields.size());

            // 2. 필수 필드 (전용 컬럼)
            builder.bidNoticeNo(allFields.get("입찰공고번호"))
                    .bidNoticeName(allFields.get("입찰공고명"));

            // 3. 진행상태 (목록에서 전달받음)
            if (progressStatusFromList != null && !progressStatusFromList.isBlank()) {
                builder.progressStatus(ProgressStatus.fromKorean(progressStatusFromList));
            }

            // 4. 분류 정보 (enum 변환)
            String businessTypeText = allFields.get("업무분류");
            if (businessTypeText != null) {
                builder.businessType(BusinessType.fromKorean(businessTypeText));
            }

            String contractText = allFields.get("계약방법");
            if (contractText != null) {
                builder.contractMethod(ContractMethod.fromKorean(contractText));
            }

            // 5. 일시 정보 (전용 컬럼)
            builder.noticeDt(parseDateTime(allFields.get("입찰서접수시작일시")))
                    .bidEndDt(parseDateTime(allFields.get("입찰서접수마감일시")));

            // 6. 기관 정보
            builder.orgName(allFields.get("담당부서"));

            // 7. 담당자 정보
            builder.managerName(allFields.get("담당자"));

            // 8. 섹션별 JSON 분류
            Map<String, Map<String, String>> grouped = groupFieldsBySection(allFields);
            builder.noticeInfo(toJson(grouped.get("noticeInfo")))
                    .scheduleInfo(toJson(grouped.get("scheduleInfo")))
                    .amountInfo(toJson(grouped.get("amountInfo")));

            // 9. detailInfo: 기본 분류 + 그리드 데이터(용역상세내역, 재입찰내역) 병합
            Map<String, Object> detailInfoMap = new LinkedHashMap<>(grouped.get("detailInfo"));

            List<Map<String, String>> serviceGrid = extractGridData(driver,
                    "td[col_id='srvceNm']", "srvceNm", "srvceQty", "srvceUnit", "srvceBgnDe", "srvceEndDe");
            if (!serviceGrid.isEmpty()) {
                detailInfoMap.put("용역상세내역", serviceGrid);
            }

            List<Map<String, String>> rebidGrid = extractGridData(driver,
                    "td[col_id='rebidSn']", "rebidSn", "rebidRsn", "rebidDt");
            if (!rebidGrid.isEmpty()) {
                detailInfoMap.put("재입찰내역", rebidGrid);
            }

            builder.detailInfo(toJson(detailInfoMap));

            // 10. rawPayload: 전체 추출 데이터 원본
            builder.rawPayload(toJson(allFields));

            // 11. 첨부파일
            builder.attachments(parseAttachments(driver));

            // 12. 현재 URL 저장
            builder.detailUrl(driver.getCurrentUrl());

            log.info("상세 페이지 파싱 완료: {}", builder.build().getBidNoticeNo());
            return builder.build();

        } catch (Exception e) {
            log.error("상세 페이지 파싱 실패: {}", e.getMessage());
            return null;
        } finally {
            // implicit wait 복원
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(crawlerProperties.getImplicitWait()));
        }
    }

    /**
     * 목록으로 돌아가기
     */
    public void goBackToList(WebDriver driver) {
        try {
            driver.navigate().back();
            Thread.sleep(2000);
        } catch (Exception e) {
            log.warn("목록 복귀 실패: {}", e.getMessage());
        }
    }

    // ========== 동적 필드 추출 ==========

    /**
     * 페이지 전체 td[data-title] → Map&lt;String, String&gt; 일괄 추출
     *
     * - 여러 span 결합: 날짜+시간 패턴(2026/02/13 + 10:00)을 하나로 합침
     * - 중복 key 처리: 같은 data-title이 여러 번 나오면 _2, _3 접미사 부여
     */
    private Map<String, String> extractAllDataTitleFields(WebDriver driver) {
        Map<String, String> result = new LinkedHashMap<>();

        List<WebElement> cells = driver.findElements(By.cssSelector("td[data-title]"));

        for (WebElement cell : cells) {
            try {
                String dataTitle = cell.getAttribute("data-title");
                if (dataTitle == null || dataTitle.isBlank()) continue;

                String value = extractCellValue(cell);
                if (value == null || value.isBlank()) continue;

                // 중복 key 처리: 같은 data-title이 여러 번 나오면 접미사 부여
                String uniqueKey = resolveUniqueKey(result, dataTitle);
                result.put(uniqueKey, value);

            } catch (Exception e) {
                // 개별 셀 실패는 무시하고 계속 진행
            }
        }

        return result;
    }

    /**
     * td 셀에서 텍스트 추출 - 여러 span을 공백으로 결합
     *
     * 날짜+시간 패턴: "2026/02/13" + "10:00" → "2026/02/13 10:00"
     * select 태그가 있으면 선택된 옵션 텍스트 추출
     */
    private String extractCellValue(WebElement cell) {
        // select 태그 우선 처리 - findElements로 implicit wait 회피
        List<WebElement> selects = cell.findElements(By.tagName("select"));
        if (!selects.isEmpty()) {
            return new Select(selects.get(0)).getFirstSelectedOption().getText().trim();
        }

        // span 여러 개를 결합 (날짜+시간 패턴 대응)
        List<WebElement> spans = cell.findElements(By.tagName("span"));
        if (!spans.isEmpty()) {
            String combined = spans.stream()
                    .map(span -> span.getText().trim())
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.joining(" "));
            if (!combined.isBlank()) {
                return combined;
            }
        }

        // span이 없으면 td 전체 텍스트
        return cell.getText().trim();
    }

    /**
     * 중복 key 해결 - 이미 존재하면 _2, _3 ... 접미사 부여
     */
    private String resolveUniqueKey(Map<String, String> map, String key) {
        if (!map.containsKey(key)) {
            return key;
        }
        int suffix = 2;
        while (map.containsKey(key + "_" + suffix)) {
            suffix++;
        }
        return key + "_" + suffix;
    }

    // ========== 섹션 분류 ==========

    /**
     * 추출된 전체 필드를 4개 JSON 섹션으로 분류
     *
     * - noticeInfo: 공고 기본정보 (문서번호, 공고종류, 입찰방식 등)
     * - scheduleInfo: 일정정보 (개찰일시, 개찰장소 등)
     * - amountInfo: 금액정보 (배정예산, 기준금액 등)
     * - detailInfo: 나머지 (적격심사, 투찰제한, 담당자 등)
     *
     * PRIMARY_FIELDS(이미 전용 컬럼에 저장)는 제외
     */
    private Map<String, Map<String, String>> groupFieldsBySection(Map<String, String> allFields) {
        Map<String, String> noticeInfo = new LinkedHashMap<>();
        Map<String, String> scheduleInfo = new LinkedHashMap<>();
        Map<String, String> amountInfo = new LinkedHashMap<>();
        Map<String, String> detailInfo = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : allFields.entrySet()) {
            String key = entry.getKey();
            // 접미사(_2, _3)를 제거한 원본 키로 분류 판단
            String baseKey = key.replaceAll("_\\d+$", "");

            // 이미 전용 컬럼에 저장하는 필드는 제외
            if (PRIMARY_FIELDS.contains(baseKey)) continue;

            if (NOTICE_KEYS.contains(baseKey)) {
                noticeInfo.put(key, entry.getValue());
            } else if (SCHEDULE_KEYS.contains(baseKey) || baseKey.startsWith(SCHEDULE_PREFIX)) {
                scheduleInfo.put(key, entry.getValue());
            } else if (AMOUNT_KEYS.contains(baseKey) || baseKey.startsWith(AMOUNT_PREFIX)) {
                amountInfo.put(key, entry.getValue());
            } else {
                detailInfo.put(key, entry.getValue());
            }
        }

        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        grouped.put("noticeInfo", noticeInfo);
        grouped.put("scheduleInfo", scheduleInfo);
        grouped.put("amountInfo", amountInfo);
        grouped.put("detailInfo", detailInfo);
        return grouped;
    }

    // ========== 그리드(w2grid) 데이터 추출 ==========

    /**
     * w2grid 테이블에서 행 데이터 추출
     * @param driver WebDriver
     * @param gridSelector 그리드 존재 여부 확인용 CSS 셀렉터
     * @param colIds 추출할 col_id 목록
     * @return 행 목록. 그리드가 없으면 빈 리스트
     */
    private List<Map<String, String>> extractGridData(WebDriver driver, String gridSelector, String... colIds) {
        List<Map<String, String>> rows = new ArrayList<>();
        try {
            // 그리드 존재 여부 확인
            List<WebElement> testCells = driver.findElements(By.cssSelector(gridSelector));
            if (testCells.isEmpty()) return rows;

            // 첫 번째 매칭 셀의 부모 테이블에서 모든 행 탐색
            WebElement table = testCells.get(0).findElement(By.xpath("ancestor::table"));
            List<WebElement> tableRows = table.findElements(By.tagName("tr"));

            for (WebElement row : tableRows) {
                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (String colId : colIds) {
                    String cellText = getGridCellText(row, colId);
                    if (cellText != null && !cellText.isBlank()) {
                        rowData.put(colId, cellText);
                        hasData = true;
                    }
                }

                if (hasData) {
                    rows.add(rowData);
                }
            }
        } catch (Exception e) {
            log.debug("그리드 데이터 추출 실패 [{}]: {}", gridSelector, e.getMessage());
        }
        return rows;
    }

    /**
     * 그리드 행에서 특정 col_id의 셀 텍스트 추출
     * - nobr 태그 내 텍스트 우선
     * - select 태그의 선택된 옵션 텍스트 지원
     */
    private String getGridCellText(WebElement row, String colId) {
        try {
            // col_id 셀 찾기 - findElements로 implicit wait 회피
            List<WebElement> cells = row.findElements(By.cssSelector("td[col_id='" + colId + "']"));
            if (cells.isEmpty()) return null;
            WebElement cell = cells.get(0);

            // select 태그 처리
            List<WebElement> selects = cell.findElements(By.tagName("select"));
            if (!selects.isEmpty()) {
                return new Select(selects.get(0)).getFirstSelectedOption().getText().trim();
            }

            // nobr 태그 우선
            List<WebElement> nobrs = cell.findElements(By.tagName("nobr"));
            if (!nobrs.isEmpty()) {
                return nobrs.get(0).getText().trim();
            }

            return cell.getText().trim();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 첨부파일 ==========

    /**
     * 첨부파일 목록 파싱 - col_id 기반으로 파일첨부 그리드 행 탐색
     *
     * - orgnlAtchFileNm: 원본 파일명
     * - fileSz: 파일 크기
     * - atchFileKndCd: 문서명 (select 옵션)
     */
    private List<BiddingNoticeDto.AttachmentDto> parseAttachments(WebDriver driver) {
        List<BiddingNoticeDto.AttachmentDto> attachments = new ArrayList<>();

        try {
            // col_id='orgnlAtchFileNm' 셀이 있는 행들 = 파일첨부 그리드
            List<WebElement> fileNameCells = driver.findElements(
                    By.cssSelector("td[col_id='orgnlAtchFileNm']"));

            for (WebElement fileNameCell : fileNameCells) {
                try {
                    // 파일명 추출 (nobr > 텍스트) - findElements로 implicit wait 회피
                    List<WebElement> nobrs = fileNameCell.findElements(By.tagName("nobr"));
                    String fileName = nobrs.isEmpty()
                            ? fileNameCell.getText().trim()
                            : nobrs.get(0).getText().trim();

                    if (fileName.isEmpty() || !fileName.contains(".")) continue;

                    // 같은 행(tr)에서 다른 셀 추출
                    WebElement row = fileNameCell.findElement(By.xpath("ancestor::tr"));

                    // 파일 크기
                    String fileSizeStr = getGridCellText(row, "fileSz");
                    Long fileSize = parseFileSize(fileSizeStr);

                    // 문서명 (select에서 선택된 옵션)
                    String docType = getGridCellText(row, "atchFileKndCd");

                    // 다운로드 URL
                    String fileUrl = extractFileUrl(fileNameCell);

                    attachments.add(BiddingNoticeDto.AttachmentDto.builder()
                            .fileName(fileName)
                            .fileUrl(fileUrl)
                            .fileSize(fileSize)
                            .docType(docType)
                            .build());

                } catch (Exception e) {
                    // 개별 파일 파싱 실패는 무시
                }
            }

            log.debug("첨부파일 {}개 발견", attachments.size());

        } catch (Exception e) {
            log.debug("첨부파일 파싱 실패: {}", e.getMessage());
        }

        return attachments;
    }

    // ========== 유틸리티 ==========

    /**
     * 날짜 문자열 파싱 (여러 형식 지원)
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            // 숫자, /, :, 공백만 추출
            String cleaned = dateStr.replaceAll("[^0-9/: ]", "").trim();

            // "2026/02/24 15:00" 형식 (공백 있는 경우)
            if (cleaned.matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}.*")) {
                return LocalDateTime.parse(cleaned.substring(0, 16), DATE_FORMAT);
            }

            // "2026/02/2415:00" 형식 (공백 없이 붙어있는 경우)
            String noSpace = cleaned.replaceAll("\\s+", "");
            if (noSpace.matches("\\d{4}/\\d{2}/\\d{2}\\d{2}:\\d{2}")) {
                String formatted = noSpace.substring(0, 10) + " " + noSpace.substring(10);
                return LocalDateTime.parse(formatted, DATE_FORMAT);
            }

            // "2026/02/24" 형식 (날짜만 있는 경우) -> 00:00으로 처리
            if (noSpace.matches("\\d{4}/\\d{2}/\\d{2}")) {
                return LocalDateTime.parse(noSpace + " 00:00", DATE_FORMAT);
            }

        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}", dateStr);
        }
        return null;
    }

    /**
     * 파일 크기 파싱 (예: "68.0 KB" → 69632)
     */
    private Long parseFileSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) return null;
        try {
            sizeStr = sizeStr.toUpperCase().trim();
            double size = Double.parseDouble(sizeStr.replaceAll("[^0-9.]", ""));

            if (sizeStr.contains("KB")) {
                return (long) (size * 1024);
            } else if (sizeStr.contains("MB")) {
                return (long) (size * 1024 * 1024);
            } else if (sizeStr.contains("GB")) {
                return (long) (size * 1024 * 1024 * 1024);
            }
            return (long) size;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 파일 다운로드 URL 추출
     */
    private String extractFileUrl(WebElement cell) {
        try {
            WebElement link = cell.findElement(By.tagName("a"));
            String href = link.getAttribute("href");
            if (href != null && !href.contains("javascript")) {
                return href;
            }
            // onclick에서 URL 추출 시도
            return link.getAttribute("onclick");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map/List → JSON 문자열 변환. null이거나 빈 컬렉션이면 null 반환
     */
    private String toJson(Object data) {
        if (data == null) return null;

        // 빈 Map 또는 빈 List는 null 처리
        if (data instanceof Map && ((Map<?, ?>) data).isEmpty()) return null;
        if (data instanceof List && ((List<?>) data).isEmpty()) return null;

        try {
            return jsonMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("JSON 변환 실패: {}", e.getMessage());
            return null;
        }
    }
}
