package io.reco.collector.domain.bidding;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
public class Attachment {

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // === 팩토리 메서드 ===

    public static Attachment create(String fileName, String fileUrl, Long fileSize) {
        return Attachment.builder()
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
