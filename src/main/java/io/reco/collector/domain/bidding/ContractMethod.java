package io.reco.collector.domain.bidding;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계약방법
 */
@Getter
@RequiredArgsConstructor
public enum ContractMethod {
    OPEN_COMPETITION("일반경쟁"),
    NOMINATED_COMPETITION("지명경쟁"),
    RESTRICTED_COMPETITION("제한경쟁"),
    PRIVATE_CONTRACT("수의계약");

    private final String koreanName;

    // 팩토리 메서드: 크롤링한 한글 텍스트를 Enum으로 변환
    public static ContractMethod fromKorean(String korean) {
        for (ContractMethod method : values()) {
            if (method.koreanName.equals(korean)) {
                return method;
            }
        }
        return null;
    }
}
