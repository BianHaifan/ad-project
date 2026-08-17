package com.adproject.community;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.community.application.CommunityService;
import com.adproject.community.api.CommunityDtos.CreateCommunityCommentRequest;
import com.adproject.community.api.CommunityDtos.CreateCommunityPostRequest;
import com.adproject.community.infrastructure.CommunityCommentRepository;
import com.adproject.community.infrastructure.CommunityPostLikeRepository;
import com.adproject.community.infrastructure.CommunityPostMetricsRepository;
import com.adproject.community.infrastructure.CommunityPostRepository;
import com.adproject.company.infrastructure.CompanyMemberRepository;
import com.adproject.company.infrastructure.CompanyRepository;
import com.adproject.user.infrastructure.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CommunityServicePermissionTest {
    @Test
    void serviceExplicitlyRejectsAnAuthenticatedPrincipalWithoutAnAllowedRole() {
        AuthenticatedUser unsupported = mock(AuthenticatedUser.class);
        when(unsupported.role()).thenReturn(null);
        CommunityService service = new CommunityService(mock(CommunityPostRepository.class),
                mock(CommunityPostLikeRepository.class), mock(CommunityCommentRepository.class),
                mock(CommunityPostMetricsRepository.class), mock(UserRepository.class),
                mock(CompanyMemberRepository.class), mock(CompanyRepository.class), Clock.systemUTC());

        assertForbidden(() -> service.list(unsupported, 1, 20));
        assertForbidden(() -> service.create(unsupported, new CreateCommunityPostRequest("body")));
        assertForbidden(() -> service.detail(unsupported, "post"));
        assertForbidden(() -> service.like(unsupported, "post"));
        assertForbidden(() -> service.unlike(unsupported, "post"));
        assertForbidden(() -> service.comments(unsupported, "post", 1, 20));
        assertForbidden(() -> service.comment(unsupported, "post", new CreateCommunityCommentRequest("body")));
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation) {
        assertThatThrownBy(invocation).isInstanceOfSatisfying(ApiException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
