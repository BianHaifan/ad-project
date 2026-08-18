package com.adproject.company.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.company.api.CompanyDtos.Company;
import com.adproject.company.api.CompanyDtos.CompanyResponse;
import com.adproject.company.api.CompanyDtos.UpdateCompanyRequest;
import com.adproject.company.infrastructure.CompanyEntity;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.domain.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterCompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository memberRepository;

    public RecruiterCompanyService(CompanyRepository companyRepository, CompanyMemberRepository memberRepository) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(AuthenticatedUser currentUser) {
        requireRecruiter(currentUser);
        var member = memberRepository.findByUserId(currentUser.userId()).orElseThrow(RecruiterCompanyService::forbidden);
        return new CompanyResponse(toCompany(companyRepository.findById(member.getCompanyId())
                .orElseThrow(RecruiterCompanyService::notFound)));
    }

    @Transactional
    public CompanyResponse update(AuthenticatedUser currentUser, UpdateCompanyRequest request) {
        requireRecruiter(currentUser);
        throw forbidden();
    }

    private static Company toCompany(CompanyEntity company) {
        return new Company(company.getId(), company.getName(), company.getLogoUrl(), company.getStage(),
                company.getEmployeeRange(), company.getVerificationStatus(), company.getWebsite(),
                company.getDescription(), company.getLocation(), company.getVersion(), company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static void requireRecruiter(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.role() != UserRole.RECRUITER) throw forbidden();
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permission");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Company not found");
    }
}
