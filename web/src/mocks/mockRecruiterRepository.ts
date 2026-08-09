import type {RecruiterRepository,ListApplicationsParams} from '../api/recruiterRepository';
import type {ApplicationStatus,JobDraft,JobStatus} from '../models/recruiter';
import {applications,conversations,jobs,recruiter} from './data';
const delay=(ms=260)=>new Promise(r=>setTimeout(r,ms));
const failIfDemoError=async()=>{await delay(); if(sessionStorage.getItem('ad_mock_error')==='true') throw new Error('Mock service is unavailable.');};
export const mockRecruiterRepository:RecruiterRepository={
 async signIn(email,password){await failIfDemoError();if(!email.includes('@')||password.length<8)throw new Error('Invalid email or password.');localStorage.setItem('ad_session','true');return recruiter;},
 async register(companyName,email,password){await failIfDemoError();if(!companyName||!email.includes('@')||password.length<8)throw new Error('Please check the account details.');localStorage.setItem('ad_session','true');return {...recruiter,email,companyName};},
 async getMe(){await failIfDemoError();return recruiter;},
 async getDashboard(){await failIfDemoError();return {openRoles:12,newMatches:248,pendingResumes:73,interviews:18,verification:'Approved',recommendations:applications.slice(0,3),jobs:jobs.slice(0,3)};},
 async listJobs(){await failIfDemoError();return [...jobs];},async getJob(id){await failIfDemoError();return jobs.find(j=>j.id===id);},
 async saveJob(input:JobDraft,id,publish=false){await delay(500);const existing=id?jobs.find(j=>j.id===id):undefined;const saved={id:existing?.id??`job_${Date.now()}`,team:existing?.team??'New team',applicantCount:existing?.applicantCount??0,owner:recruiter.name,publishedAt:publish?new Date().toISOString():existing?.publishedAt??null,status:(publish?'ACTIVE':existing?.status??'DRAFT') as JobStatus,salary:{min:input.salaryMin,max:input.salaryMax,currency:'CNY' as const,period:'MONTH' as const},...input};if(existing)Object.assign(existing,saved);else jobs.unshift(saved);return saved;},
 async setJobStatus(id,status){await delay();const job=jobs.find(j=>j.id===id);if(!job)throw new Error('Job not found.');job.status=status;return job;},
 async getApplicationCounts(){await failIfDemoError();return {APPLIED:18,IN_REVIEW:9,INTERVIEW:6,REJECTED:12};},
 async listApplications(params:ListApplicationsParams){await failIfDemoError();return applications.filter(a=>(!params.status||a.status===params.status)&&(!params.jobId||a.jobId===params.jobId)&&(!params.search||`${a.candidate.name} ${a.candidate.email}`.toLowerCase().includes(params.search.toLowerCase())));},
 async getApplication(id){await failIfDemoError();return applications.find(a=>a.id===id);},
 async updateApplicationStatus(id,status:ApplicationStatus){await delay(500);const app=applications.find(a=>a.id===id);if(!app)throw new Error('Application not found.');app.status=status;const step=app.timeline.find(t=>t.status===status);if(step)step.at=new Date().toISOString();else app.timeline.push({status,at:new Date().toISOString(),note:`Moved to ${status}.`});return app;},
 async saveApplicationNote(id,note){await delay(400);const app=applications.find(a=>a.id===id);if(!app)throw new Error('Application not found.');app.note=note;return app;},
 async scheduleInterview(id,scheduledAt){await delay(550);const app=applications.find(a=>a.id===id);if(!app)throw new Error('Application not found.');app.status='INTERVIEW';app.timeline.push({status:'INTERVIEW',at:scheduledAt,note:'Interview invitation sent.'});return app;},
 async listConversations(){await failIfDemoError();return conversations;},async getConversation(id){await failIfDemoError();return conversations.find(c=>c.id===id);},
 async sendMessage(id,body){await delay(350);const conversation=conversations.find(c=>c.id===id);if(!conversation)throw new Error('Conversation not found.');conversation.messages.push({id:`msg_${Date.now()}`,body,senderType:'RECRUITER',sentAt:new Date().toISOString()});return {conversation};}
};
