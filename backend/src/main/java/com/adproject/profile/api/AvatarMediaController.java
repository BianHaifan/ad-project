package com.adproject.profile.api;

import com.adproject.profile.application.AvatarService;
import com.adproject.profile.infrastructure.UserAvatarEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/avatars")
public class AvatarMediaController {
    private final AvatarService service;

    public AvatarMediaController(AvatarService service) { this.service = service; }

    @GetMapping("/{userId}")
    ResponseEntity<byte[]> read(@PathVariable String userId) {
        UserAvatarEntity avatar = service.read(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                .contentLength(avatar.getSizeBytes())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.getContent());
    }
}
