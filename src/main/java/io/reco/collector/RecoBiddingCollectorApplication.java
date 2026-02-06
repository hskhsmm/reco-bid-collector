package io.reco.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // 스케줄링 활성화 (@Scheduled)
@EnableRetry       // 재시도 활성화 (@Retryable)
public class RecoBiddingCollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecoBiddingCollectorApplication.class, args);
	}

}
