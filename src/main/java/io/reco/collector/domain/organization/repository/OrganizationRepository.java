package io.reco.collector.domain.organization.repository;

import io.reco.collector.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    // 기관명으로 조회 (중복 체크용)
    Optional<Organization> findByOrgName(String orgName);

    // 기관명 존재 여부
    boolean existsByOrgName(String orgName);
}
