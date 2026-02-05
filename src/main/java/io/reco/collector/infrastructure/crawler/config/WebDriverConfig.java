package io.reco.collector.infrastructure.crawler.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Selenium WebDriver 설정 - Chrome headless 브라우저 관리
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebDriverConfig {

    private final CrawlerProperties crawlerProperties;

    @PostConstruct
    public void setupDriver() {
        WebDriverManager.chromedriver().setup();
        log.info("ChromeDriver 설정 완료");
    }

    @Bean
    public ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();

        if (crawlerProperties.isHeadless()) {
            options.addArguments("--headless=new");
        }

        // 안정성 옵션
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        // User-Agent 설정 (봇 차단 우회)
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // 불필요한 리소스 차단 (속도 향상)
        options.addArguments("--disable-images");
        options.addArguments("--blink-settings=imagesEnabled=false");

        return options;
    }

    /**
     * WebDriver는 prototype scope - 매번 새로 생성
     * 사용 후 반드시 quit() 호출 필요
     */
    public WebDriver createWebDriver() {
        ChromeDriver driver = new ChromeDriver(chromeOptions());

        driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(crawlerProperties.getPageLoadTimeout()));
        driver.manage().timeouts()
                .implicitlyWait(Duration.ofSeconds(crawlerProperties.getImplicitWait()));

        log.debug("WebDriver 생성 완료 (headless: {})", crawlerProperties.isHeadless());
        return driver;
    }
}
