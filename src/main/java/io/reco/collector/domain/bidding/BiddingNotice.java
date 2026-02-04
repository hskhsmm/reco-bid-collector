package io.reco.collector.domain.bidding;

import io.reco.collector.common.entity.BaseTimeEntity;
import io.reco.collector.domain.checkpoint.CrawlCheckpoint;
import io.reco.collector.domain.organization.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 입찰공고 - 누리장터에서 수집한 공고 정보
 */
@Entity
@Table(name = "bidding_notice", indexes = {
        @Index(name = "idx_notice_dt", columnList = "notice_dt"),
        @Index(name = "idx_bid_end_dt", columnList = "bid_end_dt"),
        @Index(name = "idx_business_type", columnList = "business_type"),
        @Index(name = "idx_progress_status", columnList = "progress_status"),
        @Index(name = "idx_crawl_run_id", columnList = "crawl_run_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BiddingNotice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bid_notice_no", length = 30, nullable = false, unique = true)
    private String bidNoticeNo;

    @Column(name = "bid_notice_name", length = 500, nullable = false)
    private String bidNoticeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 20)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", length = 20)
    private ProgressStatus progressStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_method", length = 20)
    private ContractMethod contractMethod;

    @Column(name = "notice_dt")
    private LocalDateTime noticeDt;

    @Column(name = "bid_end_dt")
    private LocalDateTime bidEndDt;

    // === FK 관계 ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    // PK(id) 대신 UK(crawl_run_id)로 참조 - 배치 ID로 추적하는 게 비즈니스적으로 의미있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crawl_run_id", referencedColumnName = "crawl_run_id")
    private CrawlCheckpoint crawlCheckpoint;

    // === JSON 필드: 구조가 가변적인 부가정보는 JSON으로 유연하게 저장 ===

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notice_info", columnDefinition = "json")
    private String noticeInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule_info", columnDefinition = "json")
    private String scheduleInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "amount_info", columnDefinition = "json")
    private String amountInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_info", columnDefinition = "json")
    private String detailInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "json")
    private String rawPayload;

    // === 크롤링 메타 ===

    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", length = 20)
    private ParseStatus parseStatus;

    @Column(name = "parse_error", columnDefinition = "TEXT")
    private String parseError;

    // === 첨부파일 (1:N): cascade로 공고 저장 시 첨부파일도 함께 저장 ===

    @OneToMany(mappedBy = "biddingNotice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    // === 팩토리 메서드 ===

    public static BiddingNotice create(String bidNoticeNo, String bidNoticeName,
                                       Organization organization, CrawlCheckpoint checkpoint) {
        return BiddingNotice.builder()
                .bidNoticeNo(bidNoticeNo)
                .bidNoticeName(bidNoticeName)
                .organization(organization)
                .crawlCheckpoint(checkpoint)
                .parseStatus(ParseStatus.SUCCESS)
                .collectedAt(LocalDateTime.now())
                .build();
    }

    // === 상태 변경 메서드 ===

    public void updateParseStatus(ParseStatus status, String error) {
        this.parseStatus = status;
        this.parseError = error;
    }

    public void markAsFailed(String error) {
        this.parseStatus = ParseStatus.FAILED;
        this.parseError = error;
    }

    public void addAttachment(Attachment attachment) {
        this.attachments.add(attachment);
        attachment.setBiddingNotice(this);
    }
}
