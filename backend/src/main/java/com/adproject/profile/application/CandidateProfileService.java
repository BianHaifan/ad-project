package com.adproject.profile.application;

import com.adproject.common.api.ApiException;
import com.adproject.common.security.AuthenticatedUser;
import com.adproject.common.time.DatabaseTimePrecision;
import com.adproject.profile.api.ProfileDtos.CandidateProfile;
import com.adproject.profile.api.ProfileDtos.CandidateStats;
import com.adproject.profile.api.ProfileDtos.ProfileResponse;
import com.adproject.profile.api.ProfileDtos.UpdateProfileRequest;
import com.adproject.profile.domain.Gender;
import com.adproject.profile.infrastructure.*;
import com.adproject.user.domain.UserRole;
import com.adproject.user.infrastructure.*;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9][0-9\\s\\-()]{4,19}$");
    private final UserRepository users; private final CandidateProfileRepository profiles; private final Clock clock;
    public CandidateProfileService(UserRepository users, CandidateProfileRepository profiles, Clock clock) { this.users=users; this.profiles=profiles; this.clock=clock; }
    @Transactional(readOnly=true) public ProfileResponse get(AuthenticatedUser principal) { requireCandidate(principal); return response(users.findById(principal.userId()).orElseThrow(), profiles.findById(principal.userId()).orElse(null)); }
    @Transactional public ProfileResponse update(AuthenticatedUser principal, UpdateProfileRequest request) {
        requireCandidate(principal); rejectNull(request);
        Map<String,String> errors=validateOptionalFields(request);
        if(!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR","Request validation failed",errors);
        UserEntity user=users.findById(principal.userId()).orElseThrow(); var existing=profiles.findByUserIdForUpdate(user.getId()).orElse(null);
        int version=existing==null?1:existing.getVersion();
        if(version!=request.getExpectedVersion()) throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","The profile has changed");
        var now=DatabaseTimePrecision.micros(clock.instant());
        if(request.isFullNamePresent()) user.updateFullName(request.getFullName(),now);
        String headline=request.isHeadlinePresent()?request.getHeadline():existing==null?"":existing.getHeadline();
        String location=request.isLocationPresent()?request.getLocation():existing==null?"":existing.getLocation();
        Integer age=request.isAgePresent()?request.getAge():existing==null?null:existing.getAge();
        Gender gender=request.isGenderPresent()?request.getGender():existing==null?null:existing.getGender();
        String phone=request.isPhonePresent()?normalizeOptional(request.getPhone()):existing==null?null:existing.getPhone();
        String birthplace=request.isBirthplacePresent()?normalizeOptional(request.getBirthplace()):existing==null?null:existing.getBirthplace();
        if(existing==null) existing=profiles.save(new CandidateProfileEntity(user.getId(),headline,location,age,gender,phone,birthplace,2,now,now)); else existing.update(headline,location,age,gender,phone,birthplace,now);
        profiles.flush(); return response(user,existing);
    }
    private ProfileResponse response(UserEntity user, CandidateProfileEntity profile) {
        return new ProfileResponse(new CandidateProfile(user.getId(),user.getFullName(),user.getEmail(),profile==null?"":profile.getHeadline(),user.getAvatarUrl(),profile==null?"":profile.getLocation(),profile==null?null:profile.getAge(),profile==null||profile.getGender()==null?null:profile.getGender().name(),profile==null?null:profile.getPhone(),profile==null?null:profile.getBirthplace(),new CandidateStats(0,0,0,0),profile==null?1:profile.getVersion(),profile==null?user.getCreatedAt():profile.getCreatedAt(),profile==null?user.getUpdatedAt():profile.getUpdatedAt()));
    }
    private static Map<String,String> validateOptionalFields(UpdateProfileRequest r) {
        Map<String,String> e=new LinkedHashMap<>();
        if(r.isPhonePresent()&&r.getPhone()!=null){String phone=r.getPhone().trim(); if(!phone.isEmpty()&&!PHONE_PATTERN.matcher(phone).matches()) e.put("phone","Enter a valid phone number");}
        if(r.isBirthplacePresent()&&r.getBirthplace()!=null&&r.getBirthplace().trim().length()>100) e.put("birthplace","Maximum 100 characters");
        if(r.isAgePresent()&&r.getAge()!=null&&(r.getAge()<16||r.getAge()>100)) e.put("age","Age must be between 16 and 100");
        return e;
    }
    private static String normalizeOptional(String value){ if(value==null) return null; String trimmed=value.trim(); return trimmed.isEmpty()?null:trimmed; }
    private static void requireCandidate(AuthenticatedUser p){if(p==null||p.role()!=UserRole.CANDIDATE)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Insufficient permission");}
    private static void rejectNull(UpdateProfileRequest r){Map<String,String> e=new LinkedHashMap<>(); if(r.isFullNamePresent()&&r.getFullName()==null)e.put("fullName","must not be null"); if(r.isHeadlinePresent()&&r.getHeadline()==null)e.put("headline","must not be null"); if(r.isLocationPresent()&&r.getLocation()==null)e.put("location","must not be null"); if(!e.isEmpty())throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR","Request validation failed",e);}
}
