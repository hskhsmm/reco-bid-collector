package io.reco.collector.domain.organization;

import io.reco.collector.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 기관/단지 정보 - 중복 저장 방지용 정규화 테이블
 */
@Entity
@Table(name = "organization")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Organization extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UK: 기관명으로 중복 체크하여 같은 기관 재사용
    @Column(name = "org_name", length = 200, nullable = false, unique = true)
    private String orgName;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "contact", length = 50)
    private String contact;

    @Column(name = "homepage", length = 200)
    private String homepage;

    @Column(name = "dept_name", length = 100)
    private String deptName;

    @Column(name = "manager_name", length = 50)
    private String managerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_info", columnDefinition = "json")
    private String extraInfo;

    // === 팩토리 메서드 ===

    public static Organization create(String orgName) {
        return Organization.builder()
                .orgName(orgName)
                .build();
    }

    // === 상태 변경 메서드 ===

    public void updateDetails(String address, String contact, String homepage,
                              String deptName, String managerName, String extraInfo) {
        this.address = address;
        this.contact = contact;
        this.homepage = homepage;
        this.deptName = deptName;
        this.managerName = managerName;
        this.extraInfo = extraInfo;
    }
}
