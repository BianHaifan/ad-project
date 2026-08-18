package com.adproject.community.api;
import com.adproject.common.security.AuthenticatedUser; import com.adproject.community.application.CommunityDirectMessageService; import jakarta.validation.constraints.*;
import org.springframework.http.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/v1/community") public class CommunityDirectMessageController {
 private final CommunityDirectMessageService service; public CommunityDirectMessageController(CommunityDirectMessageService service){this.service=service;}
 @PostMapping("/posts/{postId}/direct-conversation") @ResponseStatus(HttpStatus.CREATED) CommunityDirectDtos.ConversationResponse start(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String postId){return service.start(user,postId);}
 @GetMapping("/direct-conversations") CommunityDirectDtos.ConversationListResponse list(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="50") @Min(1) @Max(100) int pageSize){return service.list(user,page,pageSize);}
 @GetMapping("/direct-conversations/{id}") CommunityDirectDtos.ConversationResponse detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id){return service.detail(user,id);}
 @GetMapping("/direct-conversations/{id}/messages") CommunityDirectDtos.MessageListResponse messages(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="50") @Min(1) @Max(100) int pageSize){return service.messages(user,id,page,pageSize);}
 @PostMapping("/direct-conversations/{id}/messages") @ResponseStatus(HttpStatus.CREATED) CommunityDirectDtos.MessageResponse send(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable String id,@RequestBody CommunityDirectDtos.SendMessageRequest request){return service.send(user,id,request);}
}
