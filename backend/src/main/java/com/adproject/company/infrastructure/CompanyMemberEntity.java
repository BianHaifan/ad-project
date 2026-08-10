package com.adproject.company.infrastructure;

import com.adproject.company.domain.CompanyMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "company_members")
public class CompanyMemberEntity {
    @Id
    @Column(length = 36, columnDefinition = "char(36)")
    private String id;
    @Column(name = "company_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String companyId;
    @Column(name = "user_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false, length = 32)
    private CompanyMemberRole memberRole;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompanyMemberEntity() {}

    public CompanyMemberEntity(String id, String companyId, String userId, CompanyMemberRole memberRole, Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.userId = userId;
        this.memberRole = memberRole;
        this.createdAt = createdAt;
    }

    public String getCompanyId() { return companyId; }
    public String getUserId() { return userId; }
    public CompanyMemberRole getMemberRole() { return memberRole; }
}
