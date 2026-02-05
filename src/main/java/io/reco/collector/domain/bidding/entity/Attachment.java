package io.reco.collector.domain.bidding.entity;

import io.reco.collector.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 첨부파일 - 입찰공고에 첨부된 파일 정보
 */
@Entity
@Table(name = "attachment", indexes = {
        @Index(name = "idx_bid_notice_id", columnList = "bid_notice_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Setter: BiddingNotice.addAttachment()에서 양방향 관계 설정용
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bid_notice_id", nullable = false)
    private BiddingNotice biddingNotice;

    @Column(name = "file_name", length = 300, nullable = false)
    private String fileName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Long fileSize;

    // === 팩토리 메서드 ===

    public static Attachment create(String fileName, String fileUrl, Long fileSize) {
        return Attachment.builder()
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .build();
    }
}
