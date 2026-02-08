package io.reco.collector.infrastructure.crawler.config;

import lombok.Builder;
import lombok.Getter;

/**
 * 누리장터 검색 필터 - API 파라미터로 전달받아 검색 폼에 입력
 */
@Getter
@Builder
public class SearchFilter {

    /** 입찰공고명 키워드 */
    @Builder.Default
    private String keyword = "";

    /** 공고분류: 전체 / 물품 / 용역 / 공사 */
    @Builder.Default
    private String businessType = "전체";

    /** 진행상태: 전체 / 입찰개시 / 개찰완료 등 */
    @Builder.Default
    private String progressStatus = "전체";

    /** 공고게시일자 기간 (개월): 1 / 3 / 6 */
    @Builder.Default
    private int periodMonths = 1;

    /** 공고구분: 전체 / 등록공고 / 변경공고 */
    @Builder.Default
    private String noticeType = "전체";

    public static SearchFilter defaults() {
        return SearchFilter.builder().build();
    }
}
