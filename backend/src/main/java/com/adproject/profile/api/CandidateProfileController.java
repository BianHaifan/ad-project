package com.adproject.profile.api;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.profile.application.CandidateProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/candidate/profile")
public class CandidateProfileController {
 private final CandidateProfileService service; public CandidateProfileController(CandidateProfileService service){this.service=service;}
 @GetMapping ProfileDtos.ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser user){return service.get(user);}
 @PatchMapping ProfileDtos.ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody ProfileDtos.UpdateProfileRequest request){return service.update(user,request);}
}
