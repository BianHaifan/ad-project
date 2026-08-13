package com.adproject.conversation.api;

import com.adproject.common.security.AuthenticatedUser;
import com.adproject.conversation.application.ConversationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/candidate/conversations")
public class CandidateConversationController {
    private final ConversationService service;

    public CandidateConversationController(ConversationService service) { this.service = service; }

    @GetMapping
    ConversationDtos.ListResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.listCandidate(user, page, pageSize);
    }

    @GetMapping("/{conversationId}")
    ConversationDtos.DetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable String conversationId) {
        return service.detailCandidate(user, conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    ConversationDtos.MessageListResponse messages(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return service.listMessagesCandidate(user, conversationId, before, limit);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{conversationId}/messages")
    ConversationDtos.MessageResponse send(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String conversationId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ConversationDtos.SendMessageRequest request) {
        return service.sendCandidate(user, conversationId, idempotencyKey, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{conversationId}/read-state")
    void readState(@AuthenticationPrincipal AuthenticatedUser user,
                   @PathVariable String conversationId,
                   @RequestBody ConversationDtos.ReadStateRequest request) {
        service.updateReadStateCandidate(user, conversationId, request);
    }
}
