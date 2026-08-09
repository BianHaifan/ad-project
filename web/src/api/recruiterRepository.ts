import type {Application,ApplicationCounts,ApplicationStatus,Conversation,Dashboard,Job,JobDraft,Recruiter} from '../models/recruiter';
export interface ListApplicationsParams {status?:ApplicationStatus;search?:string;jobId?:string}
export interface RecruiterRepository {
 signIn(email:string,password:string):Promise<Recruiter>; register(companyName:string,email:string,password:string):Promise<Recruiter>; getMe():Promise<Recruiter>;
 getDashboard():Promise<Dashboard>; listJobs():Promise<Job[]>; getJob(id:string):Promise<Job|undefined>; saveJob(input:JobDraft,id?:string,publish?:boolean):Promise<Job>; setJobStatus(id:string,status:Job['status']):Promise<Job>;
 getApplicationCounts():Promise<ApplicationCounts>; listApplications(params:ListApplicationsParams):Promise<Application[]>; getApplication(id:string):Promise<Application|undefined>; updateApplicationStatus(id:string,status:ApplicationStatus):Promise<Application>; saveApplicationNote(id:string,note:string):Promise<Application>; scheduleInterview(id:string,scheduledAt:string):Promise<Application>;
 listConversations():Promise<Conversation[]>; getConversation(id:string):Promise<Conversation|undefined>; sendMessage(id:string,body:string):Promise<MessageResult>;
}
export interface MessageResult {conversation:Conversation}
