package io.reco.collector.infrastructure.crawler.parser;

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
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 상세 페이지 파서 - 누리장터 입찰공고 상세 정보 파싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DetailPageParser {

    private final CrawlerProperties crawlerProperties;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    /**
     * 상세 페이지 파싱
     */
    public BiddingNoticeDto parseDetailPage(WebDriver driver) {
        try {
            BiddingNoticeDto.BiddingNoticeDtoBuilder builder = BiddingNoticeDto.builder();

            // 필수 필드
            builder.bidNoticeNo(getTextByDataTitle(driver, "입찰공고번호"))
                    .bidNoticeName(getTextByDataTitle(driver, "입찰공고명"));

            // 분류 정보
            String businessTypeText = getTextByDataTitle(driver, "업무분류");
            if (businessTypeText != null) {
                builder.businessType(BusinessType.fromKorean(businessTypeText));
            }

            String contractText = getTextByDataTitle(driver, "계약방법");
            if (contractText != null) {
                builder.contractMethod(ContractMethod.fromKorean(contractText));
            }

            // 일시 정보
            builder.noticeDt(parseDateTime(getTextByDataTitle(driver, "입찰서접수시작일시")))
                    .bidEndDt(parseDateTime(getTextByDataTitle(driver, "입찰서접수마감일시")));

            // 기관 정보
            builder.orgName(getTextByDataTitle(driver, "담당부서"));

            // 첨부파일
            builder.attachments(parseAttachments(driver));

            // 현재 URL 저장
            builder.detailUrl(driver.getCurrentUrl());

            log.info("상세 페이지 파싱 완료: {}", builder.build().getBidNoticeNo());
            return builder.build();

        } catch (Exception e) {
            log.error("상세 페이지 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * data-title 속성으로 값 추출
     */
    private String getTextByDataTitle(WebDriver driver, String dataTitle) {
        try {
            String selector = String.format("td[data-title='%s']", dataTitle);
            WebElement cell = driver.findElement(By.cssSelector(selector));

            // span 안의 텍스트 우선, 없으면 td 전체 텍스트
            try {
                return cell.findElement(By.tagName("span")).getText().trim();
            } catch (Exception e) {
                return cell.getText().trim();
            }
        } catch (Exception e) {
            log.debug("필드 추출 실패: {}", dataTitle);
            return null;
        }
    }

    /**
     * 날짜 문자열 파싱
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            // "2026/02/12 14:00" 형식
            String cleaned = dateStr.replaceAll("[^0-9/: ]", "").trim();
            if (cleaned.length() >= 16) {
                return LocalDateTime.parse(cleaned.substring(0, 16), DATE_FORMAT);
            }
        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}", dateStr);
        }
        return null;
    }

    /**
     * 첨부파일 목록 파싱
     */
    private List<BiddingNoticeDto.AttachmentDto> parseAttachments(WebDriver driver) {
        List<BiddingNoticeDto.AttachmentDto> attachments = new ArrayList<>();

        try {
            // 파일첨부 테이블 찾기
            List<WebElement> rows = driver.findElements(
                    By.cssSelector("table tbody tr"));

            for (WebElement row : rows) {
                try {
                    List<WebElement> cells = row.findElements(By.tagName("td"));

                    // 파일첨부 테이블: 체크박스, 문서명, 파일명, 파일크기
                    if (cells.size() >= 4) {
                        String docName = cells.get(1).getText().trim();
                        String fileName = cells.get(2).getText().trim();
                        String fileSizeStr = cells.get(3).getText().trim();

                        // 파일명이 있는 행만 추가
                        if (!fileName.isEmpty() && fileName.contains(".")) {
                            Long fileSize = parseFileSize(fileSizeStr);

                            // 다운로드 링크 추출
                            String fileUrl = extractFileUrl(cells.get(2));

                            attachments.add(BiddingNoticeDto.AttachmentDto.builder()
                                    .fileName(fileName)
                                    .fileUrl(fileUrl)
                                    .fileSize(fileSize)
                                    .build());
                        }
                    }
                } catch (Exception e) {
                    // 파일이 아닌 행은 무시
                }
            }

            log.debug("첨부파일 {}개 발견", attachments.size());

        } catch (Exception e) {
            log.debug("첨부파일 파싱 실패: {}", e.getMessage());
        }

        return attachments;
    }

    /**
     * 파일 크기 파싱 (예: "68.0 KB" → 68000)
     */
    private Long parseFileSize(String sizeStr) {
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
            String onclick = link.getAttribute("onclick");
            return onclick;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 목록으로 돌아가기
     */
    public void goBackToList(WebDriver driver) {
        try {
            driver.navigate().back();
            Thread.sleep(crawlerProperties.getRequestDelay());
        } catch (Exception e) {
            log.warn("목록 복귀 실패: {}", e.getMessage());
        }
    }
}
