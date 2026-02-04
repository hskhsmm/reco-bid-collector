package io.reco.collector.domain.bidding.repository;

import io.reco.collector.domain.bidding.entity.BiddingNotice;
import io.reco.collector.domain.bidding.enums.BusinessType;
import io.reco.collector.domain.bidding.enums.ParseStatus;
import io.reco.collector.domain.bidding.enums.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BiddingNoticeRepository extends JpaRepository<BiddingNotice, Long> {

    // 공고번호로 조회 (중복 체크용)
    Optional<BiddingNotice> findByBidNoticeNo(String bidNoticeNo);

    // 공고번호 존재 여부
    boolean existsByBidNoticeNo(String bidNoticeNo);

    // 특정 배치에서 수집한 공고 조회
    List<BiddingNotice> findByCrawlCheckpoint_CrawlRunId(String crawlRunId);

    // 파싱 실패한 공고 조회 (재처리용)
    List<BiddingNotice> findByParseStatus(ParseStatus parseStatus);

    // 업무분류별 조회
    List<BiddingNotice> findByBusinessType(BusinessType businessType);

    // 진행상태별 조회
    List<BiddingNotice> findByProgressStatus(ProgressStatus progressStatus);
}
