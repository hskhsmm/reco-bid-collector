package io.reco.collector.infrastructure.crawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "crawler")
@Getter
@Setter
public class CrawlerProperties {

    private boolean headless = true;
    private int pageLoadTimeout = 30;
    private int implicitWait = 10;
    private long requestDelay = 1000;
    private int maxRetry = 3;
    private String baseUrl = "https://nuri.g2b.go.kr";
}
