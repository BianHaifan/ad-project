package com.adproject.profile.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.profile.api.ProfileDtos.*;
import com.adproject.profile.infrastructure.*;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.*;
import java.time.Clock;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {
    private final UserRepository users; private final CandidateProfileRepository profiles; private final Clock clock;
    public CandidateProfileService(UserRepository users, CandidateProfileRepository profiles, Clock clock) { this.users=users; this.profiles=profiles; this.clock=clock; }
    @Transactional(readOnly=true) public ProfileResponse get(AuthenticatedUser principal) { requireCandidate(principal); return response(users.findById(principal.userId()).orElseThrow(), profiles.findById(principal.userId()).orElse(null)); }
    @Transactional public ProfileResponse update(AuthenticatedUser principal, UpdateProfileRequest request) {
        requireCandidate(principal); rejectNull(request);
        UserEntity user=users.findById(principal.userId()).orElseThrow(); var existing=profiles.findByUserIdForUpdate(user.getId()).orElse(null);
        int version=existing==null?1:existing.getVersion();
        if(version!=request.getExpectedVersion()) throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The profile has changed");
        var now=clock.instant();
        if(request.isFullNamePresent()) user.updateFullName(request.getFullName(),now);
        String headline=request.isHeadlinePresent()?request.getHeadline():(existing==null?"":existing.getHeadline());
        String location=request.isLocationPresent()?request.getLocation():(existing==null?"":existing.getLocation());
        if(existing==null) existing=profiles.save(new CandidateProfileEntity(user.getId(),headline,location,2,now,now)); else existing.update(headline,location,now);
        profiles.flush(); return response(user,existing);
    }
    private ProfileResponse response(UserEntity user, CandidateProfileEntity profile) {
        return new ProfileResponse(new CandidateProfile(user.getId(),user.getFullName(),user.getEmail(),profile==null?"":profile.getHeadline(),user.getAvatarUrl(),profile==null?"":profile.getLocation(),new CandidateStats(0,0,0,0),profile==null?1:profile.getVersion(),profile==null?user.getCreatedAt():profile.getCreatedAt(),profile==null?user.getUpdatedAt():profile.getUpdatedAt()));
    }
    private static void requireCandidate(AuthenticatedUser p){if(p==null||p.role()!=UserRole.CANDIDATE)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Insufficient permission");}
    private static void rejectNull(UpdateProfileRequest r){Map<String,String> e=new java.util.LinkedHashMap<>(); if(r.isFullNamePresent()&&r.getFullName()==null)e.put("fullName","must not be null"); if(r.isHeadlinePresent()&&r.getHeadline()==null)e.put("headline","must not be null"); if(r.isLocationPresent()&&r.getLocation()==null)e.put("location","must not be null"); if(!e.isEmpty())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR","Request validation failed",e);}
}
