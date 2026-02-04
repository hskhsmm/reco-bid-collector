package io.reco.collector.domain.bidding.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 진행상태
 */
@Getter
@RequiredArgsConstructor
public enum ProgressStatus {
    BID_START("입찰개시"),
    OPENING("개찰중"),
    OPENING_COMPLETED("개찰완료"),
    FAILED_BID("유찰"),
    REBID("재입찰"),
    WINNER_SELECTED("낙찰자선정"),
    CANCELLED("입찰취소");

    private final String koreanName;

    // 팩토리 메서드: 크롤링한 한글 텍스트를 Enum으로 변환
    public static ProgressStatus fromKorean(String korean) {
        for (ProgressStatus status : values()) {
            if (status.koreanName.equals(korean)) {
                return status;
            }
        }
        return null;
    }
}
