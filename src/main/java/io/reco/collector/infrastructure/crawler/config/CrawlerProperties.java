package io.reco.collector.infrastructure.crawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 크롤링 설정 - application.yml의 crawler.* 바인딩
 */
@Component
@ConfigurationProperties(prefix = "crawler")
@Getter
@Setter
public class CrawlerProperties {

    private boolean headless = true;          // 브라우저 화면 표시 여부
    private int pageLoadTimeout = 30;         // 페이지 로드 타임아웃 (초)
    private int implicitWait = 10;            // 요소 탐색 대기 시간 (초)
    private long requestDelay = 1000;         // 요청 간격 (ms) - Rate limiting
    private int maxRetry = 3;                 // 실패 시 재시도 횟수
    private String baseUrl = "https://nuri.g2b.go.kr";
}
