export type UserRole='CANDIDATE'|'RECRUITER'|'ADMIN';
export type JobStatus='DRAFT'|'ACTIVE'|'PAUSED'|'CLOSED';
export type ApplicationStatus='APPLIED'|'IN_REVIEW'|'INTERVIEW'|'REJECTED'|'WITHDRAWN';
export type EmploymentType='FULL_TIME'|'INTERNSHIP'|'PART_TIME';
export type WorkplaceType='ONSITE'|'HYBRID'|'REMOTE';
export interface Recruiter {id:string;name:string;email:string;companyId:string;companyName:string;initials:string}
export interface Salary {min:number;max:number;currency:'CNY';period:'MONTH'|'YEAR'}
export interface Job {id:string;title:string;team:string;employmentType:EmploymentType;workplaceType:WorkplaceType;location:string;salary:Salary;status:JobStatus;publishedAt:string|null;applicantCount:number;owner:string;description:string;requirements:string;skills:string[];deadline:string;visibility:'PUBLIC'|'PRIVATE'}
export interface Candidate {id:string;name:string;email:string;initials:string;headline:string;location:string;availability:string;skills:string[]}
export interface Application {id:string;candidate:Candidate;jobId:string;jobTitle:string;employmentType:EmploymentType;status:ApplicationStatus;matchScore:number;appliedAt:string;owner:string|null;source:string;note:string;resume:ResumeSnapshot;matchEvidence:{label:string;strength:'STRONG'|'MODERATE'|'MISSING'}[];timeline:{status:ApplicationStatus;at:string|null;note:string}[]}
export interface ResumeSnapshot {education:string[];projects:string[];experience:string[];skills:string[];summary:string}
export interface Message {id:string;body:string;senderType:'CANDIDATE'|'RECRUITER'|'SYSTEM';sentAt:string}
export interface Conversation {id:string;applicationId:string;candidateId:string;candidateName:string;candidateInitials:string;jobTitle:string;matchScore:number;unreadCount:number;messages:Message[]}
export interface Dashboard {openRoles:number;newMatches:number;pendingResumes:number;interviews:number;verification:'Approved'|'Pending';recommendations:Application[];jobs:Job[]}
export interface ApplicationCounts {APPLIED:number;IN_REVIEW:number;INTERVIEW:number;REJECTED:number}
export interface JobDraft {title:string;employmentType:EmploymentType;workplaceType:WorkplaceType;location:string;salaryMin:number;salaryMax:number;description:string;requirements:string;skills:string[];deadline:string;visibility:'PUBLIC'|'PRIVATE'}
