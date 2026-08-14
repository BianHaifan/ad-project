package com.adproject.resume.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.Instant; import java.util.List;
public final class ResumeDtos { private ResumeDtos(){}
 public record Experience(String experienceId,@NotNull String title,@NotNull String company,@NotNull String description,@NotNull @Pattern(regexp="^\\d{4}-(0[1-9]|1[0-2])$") String startDate,@Pattern(regexp="^\\d{4}-(0[1-9]|1[0-2])$") String endDate){}
 public record Resume(String resumeId,String fullName,int age,String location,String headline,String summary,List<Experience> experiences,int version,Instant createdAt,Instant updatedAt){}
 public record ResumeResponse(Resume data){}
 public record SaveResumeRequest(@NotNull String fullName,@Min(16) @Max(100) int age,@NotNull String location,@NotNull String headline,@NotNull String summary,@NotNull List<@Valid Experience> experiences,@Min(0) int expectedVersion){}
}
