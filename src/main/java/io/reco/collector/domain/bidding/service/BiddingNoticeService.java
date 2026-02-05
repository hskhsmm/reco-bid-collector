package io.reco.collector.domain.bidding.service;

import io.reco.collector.domain.bidding.dto.BiddingNoticeDto;
import io.reco.collector.domain.bidding.entity.Attachment;
import io.reco.collector.domain.bidding.entity.BiddingNotice;
import io.reco.collector.domain.bidding.enums.ParseStatus;
import io.reco.collector.domain.bidding.repository.BiddingNoticeRepository;
import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.organization.entity.Organization;
import io.reco.collector.domain.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiddingNoticeService {

    private final BiddingNoticeRepository biddingNoticeRepository;
    private final OrganizationService organizationService;

    /**
     * 공고 저장 (중복 체크 포함)
     * @return 저장된 공고. 이미 존재하면 empty
     */
    @Transactional
    public Optional<BiddingNotice> saveIfNotExists(BiddingNoticeDto dto, CrawlCheckpoint checkpoint) {
        // 중복 체크
        if (biddingNoticeRepository.existsByBidNoticeNo(dto.getBidNoticeNo())) {
            log.debug("이미 존재하는 공고: {}", dto.getBidNoticeNo());
            return Optional.empty();
        }

        // 기관 조회 또는 생성
        Organization organization = organizationService.getOrCreate(dto.getOrgName());

        // 공고 엔티티 생성
        BiddingNotice notice = BiddingNotice.builder()
                .bidNoticeNo(dto.getBidNoticeNo())
                .bidNoticeName(dto.getBidNoticeName())
                .organization(organization)
                .crawlCheckpoint(checkpoint)
                .businessType(dto.getBusinessType())
                .progressStatus(dto.getProgressStatus())
                .contractMethod(dto.getContractMethod())
                .noticeDt(dto.getNoticeDt())
                .bidEndDt(dto.getBidEndDt())
                .noticeInfo(dto.getNoticeInfo())
                .scheduleInfo(dto.getScheduleInfo())
                .amountInfo(dto.getAmountInfo())
                .detailInfo(dto.getDetailInfo())
                .rawPayload(dto.getRawPayload())
                .detailUrl(dto.getDetailUrl())
                .collectedAt(LocalDateTime.now())
                .parseStatus(ParseStatus.SUCCESS)
                .build();

        // 첨부파일 추가
        if (dto.getAttachments() != null) {
            for (BiddingNoticeDto.AttachmentDto attachmentDto : dto.getAttachments()) {
                Attachment attachment = Attachment.create(
                        attachmentDto.getFileName(),
                        attachmentDto.getFileUrl(),
                        attachmentDto.getFileSize()
                );
                notice.addAttachment(attachment);
            }
        }

        BiddingNotice saved = biddingNoticeRepository.save(notice);
        log.info("공고 저장 완료: {} - {}", saved.getBidNoticeNo(), saved.getBidNoticeName());

        return Optional.of(saved);
    }

    /**
     * 공고 존재 여부 확인
     */
    public boolean exists(String bidNoticeNo) {
        return biddingNoticeRepository.existsByBidNoticeNo(bidNoticeNo);
    }

    /**
     * 공고번호로 조회
     */
    public Optional<BiddingNotice> findByBidNoticeNo(String bidNoticeNo) {
        return biddingNoticeRepository.findByBidNoticeNo(bidNoticeNo);
    }

    /**
     * 파싱 실패한 공고 목록 조회 (재처리용)
     */
    public List<BiddingNotice> findFailedNotices() {
        return biddingNoticeRepository.findByParseStatus(ParseStatus.FAILED);
    }

    /**
     * 파싱 실패 마킹
     */
    @Transactional
    public void markAsFailed(String bidNoticeNo, String errorMessage) {
        biddingNoticeRepository.findByBidNoticeNo(bidNoticeNo)
                .ifPresent(notice -> notice.markAsFailed(errorMessage));
    }

    /**
     * 특정 크롤링 배치에서 수집한 공고 수
     */
    public long countByCheckpoint(String crawlRunId) {
        return biddingNoticeRepository.findByCrawlCheckpoint_CrawlRunId(crawlRunId).size();
    }
}
