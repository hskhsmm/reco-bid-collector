package io.reco.collector.application.web;

import io.reco.collector.domain.bidding.entity.BiddingNotice;
import io.reco.collector.domain.bidding.enums.BusinessType;
import io.reco.collector.domain.bidding.service.BiddingNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final BiddingNoticeService biddingNoticeService;

    /**
     * 대시보드: 크롤링 상태 + 시작/중지 + 최근 수집 공고
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        Pageable recent = PageRequest.of(0, 10, Sort.by("collectedAt").descending());
        Page<BiddingNotice> recentNotices = biddingNoticeService.findAll(recent);
        model.addAttribute("recentNotices", recentNotices.getContent());
        model.addAttribute("totalCount", recentNotices.getTotalElements());
        return "dashboard";
    }

    /**
     * 최근 수집 공고 JSON API (대시보드 실시간 갱신용)
     */
    @GetMapping("/api/notices/recent")
    @ResponseBody
    public Map<String, Object> recentNoticesApi() {
        Pageable recent = PageRequest.of(0, 10, Sort.by("collectedAt").descending());
        Page<BiddingNotice> page = biddingNoticeService.findAll(recent);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dtfTime = DateTimeFormatter.ofPattern("MM-dd HH:mm");

        List<Map<String, Object>> list = page.getContent().stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("bidNoticeName", n.getBidNoticeName());
            m.put("businessType", n.getBusinessType() != null ? n.getBusinessType().getKoreanName() : null);
            m.put("businessTypeCode", n.getBusinessType() != null ? n.getBusinessType().name() : null);
            m.put("progressStatus", n.getProgressStatus() != null ? n.getProgressStatus().getKoreanName() : null);
            m.put("orgName", n.getOrganization() != null ? n.getOrganization().getOrgName() : null);
            m.put("noticeDt", n.getNoticeDt() != null ? n.getNoticeDt().format(dtf) : null);
            m.put("bidEndDt", n.getBidEndDt() != null ? n.getBidEndDt().format(dtfTime) : null);
            return m;
        }).toList();

        return Map.of("totalCount", page.getTotalElements(), "notices", list);
    }

    /**
     * 공고 목록: 페이징 + 필터
     */
    @GetMapping("/notices")
    public String notices(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "businessType", required = false) String businessType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("noticeDt").descending());

        BusinessType type = null;
        if (businessType != null && !businessType.isBlank()) {
            try {
                type = BusinessType.valueOf(businessType);
            } catch (IllegalArgumentException ignored) {
            }
        }

        Page<BiddingNotice> notices = biddingNoticeService.search(keyword, type, pageable);

        model.addAttribute("notices", notices);
        model.addAttribute("keyword", keyword);
        model.addAttribute("businessType", businessType);
        model.addAttribute("businessTypes", BusinessType.values());

        return "notices/list";
    }

    /**
     * 공고 상세: JSON 섹션별 표시 + 첨부파일
     */
    @GetMapping("/notices/{id}")
    public String noticeDetail(@PathVariable("id") Long id, Model model) {
        BiddingNotice notice = biddingNoticeService.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("공고를 찾을 수 없습니다: " + id));

        model.addAttribute("notice", notice);

        return "notices/detail";
    }
}
