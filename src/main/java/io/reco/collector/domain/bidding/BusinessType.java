package io.reco.collector.domain.bidding;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업무분류 (공고분류)
 */
@Getter
@RequiredArgsConstructor
public enum BusinessType {
    CONSTRUCTION("공사"),
    SERVICE("용역"),
    GOODS("물품");

    private final String koreanName;

    // 팩토리 메서드: 크롤링한 한글 텍스트를 Enum으로 변환
    public static BusinessType fromKorean(String korean) {
        for (BusinessType type : values()) {
            if (type.koreanName.equals(korean)) {
                return type;
            }
        }
        return null;
    }
}
