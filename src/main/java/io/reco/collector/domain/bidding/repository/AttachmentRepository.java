package io.reco.collector.domain.bidding.repository;

import io.reco.collector.domain.bidding.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // 특정 공고의 첨부파일 조회
    List<Attachment> findByBiddingNotice_Id(Long biddingNoticeId);
}
