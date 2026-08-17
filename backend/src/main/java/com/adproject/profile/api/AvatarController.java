package com.adproject.profile.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.profile.api.AvatarDtos.AvatarResponse;
import com.adproject.profile.application.AvatarService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile/avatar")
public class AvatarController {
    private final AvatarService service;

    public AvatarController(AvatarService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AvatarResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                          @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.upload(user, file);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    void delete(@AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user);
    }
}
