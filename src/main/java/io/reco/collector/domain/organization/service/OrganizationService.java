package io.reco.collector.domain.organization.service;

import io.reco.collector.domain.organization.entity.Organization;
import io.reco.collector.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    /**
     * 기관 조회 또는 생성 (getOrCreate 패턴)
     * - 같은 기관명이면 기존 것 재사용
     * - 없으면 새로 생성
     */
    @Transactional
    public Organization getOrCreate(String orgName) {
        if (orgName == null || orgName.isBlank()) {
            return null;
        }

        return organizationRepository.findByOrgName(orgName)
                .orElseGet(() -> organizationRepository.save(Organization.create(orgName)));
    }

    /**
     * 기관 상세정보 업데이트
     */
    @Transactional
    public void updateDetails(String orgName, String address, String contact,
                              String homepage, String deptName, String managerName, String extraInfo) {
        organizationRepository.findByOrgName(orgName)
                .ifPresent(org -> org.updateDetails(address, contact, homepage, deptName, managerName, extraInfo));
    }
}
