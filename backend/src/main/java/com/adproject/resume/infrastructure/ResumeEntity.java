package com.adproject.resume.infrastructure;
import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="resumes")
public class ResumeEntity {
 @Id @Column(length=36,columnDefinition="char(36)") private String id;
 @Column(name="candidate_id",nullable=false,unique=true,length=36,columnDefinition="char(36)") private String candidateId;
 @Column(name="full_name",nullable=false,length=100) private String fullName;
 @Column(nullable=false) private int age; @Column(nullable=false,length=100) private String location;
 @Column(nullable=false,length=200) private String headline; @Column(nullable=false,columnDefinition="TEXT") private String summary;
 @Column(name="experiences_json",nullable=false,columnDefinition="TEXT") private String experiencesJson;
 @Column(name="skills_json",nullable=false,columnDefinition="TEXT") private String skillsJson;
 @Column(nullable=false) private int version; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected ResumeEntity(){}
 public ResumeEntity(String id,String candidateId,String fullName,int age,String location,String headline,String summary,String experiencesJson,int version,Instant createdAt,Instant updatedAt){this(id,candidateId,fullName,age,location,headline,summary,experiencesJson,"[]",version,createdAt,updatedAt);}
 public ResumeEntity(String id,String candidateId,String fullName,int age,String location,String headline,String summary,String experiencesJson,String skillsJson,int version,Instant createdAt,Instant updatedAt){this.id=id;this.candidateId=candidateId;this.fullName=fullName;this.age=age;this.location=location;this.headline=headline;this.summary=summary;this.experiencesJson=experiencesJson;this.skillsJson=skillsJson;this.version=version;this.createdAt=createdAt;this.updatedAt=updatedAt;}
 public String getId(){return id;} public String getCandidateId(){return candidateId;} public String getFullName(){return fullName;} public int getAge(){return age;} public String getLocation(){return location;} public String getHeadline(){return headline;} public String getSummary(){return summary;} public String getExperiencesJson(){return experiencesJson;} public String getSkillsJson(){return skillsJson;} public int getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public void replace(String fullName,int age,String location,String headline,String summary,String experiencesJson,String skillsJson,Instant now){this.fullName=fullName;this.age=age;this.location=location;this.headline=headline;this.summary=summary;this.experiencesJson=experiencesJson;this.skillsJson=skillsJson;version++;updatedAt=now;}
}
