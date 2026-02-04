package io.reco.collector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 - @CreatedDate, @LastModifiedDate 자동 적용
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
