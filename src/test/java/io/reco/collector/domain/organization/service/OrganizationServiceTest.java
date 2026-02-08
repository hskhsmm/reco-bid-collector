package io.reco.collector.domain.organization.service;

import io.reco.collector.domain.organization.entity.Organization;
import io.reco.collector.domain.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class OrganizationServiceTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    @DisplayName("기관 생성 - 새 기관이면 생성")
    void 새_기관_생성() {
        // when
        Organization org = organizationService.getOrCreate("서울시청");

        // then
        assertThat(org).isNotNull();
        assertThat(org.getId()).isNotNull();
        assertThat(org.getOrgName()).isEqualTo("서울시청");
    }

    @Test
    @DisplayName("기관 조회 - 이미 있으면 재사용")
    void 기존_기관_재사용() {
        // given
        Organization first = organizationService.getOrCreate("서울시청");
        long countAfterFirst = organizationRepository.count();

        // when
        Organization second = organizationService.getOrCreate("서울시청");

        // then
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(organizationRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("기관명 null이면 null 반환")
    void 기관명_null이면_null_반환() {
        // when
        Organization org = organizationService.getOrCreate(null);

        // then
        assertThat(org).isNull();
    }

    @Test
    @DisplayName("기관명 빈 문자열이면 null 반환")
    void 기관명_빈문자열이면_null_반환() {
        // when
        Organization org = organizationService.getOrCreate("  ");

        // then
        assertThat(org).isNull();
    }
}
