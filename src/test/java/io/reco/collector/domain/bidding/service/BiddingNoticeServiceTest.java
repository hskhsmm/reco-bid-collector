package io.reco.collector.domain.bidding.service;

import io.reco.collector.domain.bidding.dto.BiddingNoticeDto;
import io.reco.collector.domain.bidding.entity.BiddingNotice;
import io.reco.collector.domain.bidding.enums.BusinessType;
import io.reco.collector.domain.bidding.enums.ParseStatus;
import io.reco.collector.domain.bidding.repository.BiddingNoticeRepository;
import io.reco.collector.domain.checkpoint.entity.CrawlCheckpoint;
import io.reco.collector.domain.checkpoint.enums.CrawlType;
import io.reco.collector.domain.checkpoint.repository.CrawlCheckpointRepository;
import io.reco.collector.domain.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class BiddingNoticeServiceTest {

    @Autowired
    private BiddingNoticeService biddingNoticeService;

    @Autowired
    private BiddingNoticeRepository biddingNoticeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CrawlCheckpointRepository checkpointRepository;

    private CrawlCheckpoint checkpoint;

    @BeforeEach
    void setUp() {
        biddingNoticeRepository.deleteAll();
        organizationRepository.deleteAll();
        checkpointRepository.deleteAll();

        checkpoint = checkpointRepository.save(CrawlCheckpoint.start("test_run_001", CrawlType.FULL));
    }

    @Test
    @DisplayName("새 공고 저장 성공")
    void 새_공고_저장_성공() {
        // given
        BiddingNoticeDto dto = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-001")
                .bidNoticeName("테스트 공고")
                .orgName("서울시청")
                .businessType(BusinessType.SERVICE)
                .build();

        // when
        Optional<BiddingNotice> result = biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getBidNoticeNo()).isEqualTo("BID-2024-001");
        assertThat(result.get().getOrganization().getOrgName()).isEqualTo("서울시청");
    }

    @Test
    @DisplayName("중복 공고는 저장 안 함")
    void 중복_공고_저장_안함() {
        // given
        BiddingNoticeDto dto = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-001")
                .bidNoticeName("테스트 공고")
                .build();
        biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // when
        Optional<BiddingNotice> result = biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // then
        assertThat(result).isEmpty();
        assertThat(biddingNoticeRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("첨부파일 함께 저장")
    void 첨부파일_함께_저장() {
        // given
        BiddingNoticeDto dto = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-002")
                .bidNoticeName("첨부파일 테스트")
                .attachments(List.of(
                        BiddingNoticeDto.AttachmentDto.builder()
                                .fileName("공고문.pdf")
                                .fileUrl("http://example.com/file1.pdf")
                                .fileSize(1024L)
                                .build(),
                        BiddingNoticeDto.AttachmentDto.builder()
                                .fileName("규격서.hwp")
                                .fileUrl("http://example.com/file2.hwp")
                                .fileSize(2048L)
                                .build()
                ))
                .build();

        // when
        Optional<BiddingNotice> result = biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getAttachments()).hasSize(2);
    }

    @Test
    @DisplayName("공고 존재 여부 확인")
    void 공고_존재_여부_확인() {
        // given
        BiddingNoticeDto dto = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-003")
                .bidNoticeName("존재 확인 테스트")
                .build();
        biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // when & then
        assertThat(biddingNoticeService.exists("BID-2024-003")).isTrue();
        assertThat(biddingNoticeService.exists("BID-9999-999")).isFalse();
    }

    @Test
    @DisplayName("파싱 실패 마킹")
    void 파싱_실패_마킹() {
        // given
        BiddingNoticeDto dto = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-004")
                .bidNoticeName("실패 마킹 테스트")
                .build();
        biddingNoticeService.saveIfNotExists(dto, checkpoint);

        // when
        biddingNoticeService.markAsFailed("BID-2024-004", "파싱 오류 발생");

        // then
        BiddingNotice notice = biddingNoticeRepository.findByBidNoticeNo("BID-2024-004").get();
        assertThat(notice.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(notice.getParseError()).isEqualTo("파싱 오류 발생");
    }

    @Test
    @DisplayName("파싱 실패 목록 조회")
    void 파싱_실패_목록_조회() {
        // given
        BiddingNoticeDto dto1 = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-005")
                .bidNoticeName("성공 공고")
                .build();
        BiddingNoticeDto dto2 = BiddingNoticeDto.builder()
                .bidNoticeNo("BID-2024-006")
                .bidNoticeName("실패 공고")
                .build();
        biddingNoticeService.saveIfNotExists(dto1, checkpoint);
        biddingNoticeService.saveIfNotExists(dto2, checkpoint);
        biddingNoticeService.markAsFailed("BID-2024-006", "에러");

        // when
        List<BiddingNotice> failedList = biddingNoticeService.findFailedNotices();

        // then
        assertThat(failedList).hasSize(1);
        assertThat(failedList.get(0).getBidNoticeNo()).isEqualTo("BID-2024-006");
    }
}
