package io.reco.collector.domain.bidding.dto;

import io.reco.collector.domain.bidding.enums.BusinessType;
import io.reco.collector.domain.bidding.enums.ContractMethod;
import io.reco.collector.domain.bidding.enums.ProgressStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 크롤링 결과를 담는 DTO
 * Scraper → DataPipeline으로 데이터 전달용
 */
@Getter
@Builder
public class BiddingNoticeDto {

    // 필수
    private String bidNoticeNo;
    private String bidNoticeName;

    // 기관
    private String orgName;

    // 분류
    private BusinessType businessType;
    private ProgressStatus progressStatus;
    private ContractMethod contractMethod;

    // 일시
    private LocalDateTime noticeDt;
    private LocalDateTime bidEndDt;

    // JSON으로 저장할 부가정보
    private String noticeInfo;
    private String scheduleInfo;
    private String amountInfo;
    private String detailInfo;
    private String rawPayload;

    // 메타
    private String detailUrl;

    // 담당자
    private String managerName;

    // 첨부파일
    private List<AttachmentDto> attachments;

    @Getter
    @Builder
    public static class AttachmentDto {
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String docType;     // 문서명 (설계도서, 과업지시서 등)
    }
}
