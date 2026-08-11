import {useNavigate, useParams} from 'react-router-dom';
import {useApplication} from '../api/queries';
import {ErrorState, LoadingState} from '../components/AsyncState';

export function ResumeReviewPage() {
  const {applicationId = ''} = useParams(); const nav = useNavigate();
  const query = useApplication(applicationId);
  if (query.isLoading) return <LoadingState/>;
  if (query.isError || !query.data) return <ErrorState onRetry={() => query.refetch()}/>;
  const application = query.data; const resume = application.resumeSnapshot; const analysis = application.matchAnalysis;
  return <div className="review-layout"><section className="panel review-resume"><button className="text-button" onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}>‹ Back to application</button><h1>{resume.fullName}</h1><h3>{resume.headline}</h3><p>{resume.summary}</p><div><h2>Experience</h2>{resume.experiences.map(experience => <p key={experience.experienceId ?? experience.title}><b>{experience.title} · {experience.company}</b><br/>{experience.description}<br/>{experience.startDate}–{experience.endDate ?? 'Present'}</p>)}</div></section><section className="panel review-analysis"><div className="section-title"><div><h1>ML Fit Analysis</h1><p>{application.jobTitle}</p></div>{application.matchScore !== null && <span className="match-badge">{application.matchScore}% ML match</span>}</div><h2>Requirement Match</h2>{analysis?.evidence.map(evidence => <div className="evidence" key={evidence}><b>{evidence}</b><span className="evidence-strong">Evidence</span></div>)}<h2>ML Model Summary</h2><p>{analysis ? `Strong matches: ${analysis.strongMatches.join(', ')}. Gaps: ${analysis.gaps.join(', ')}.` : 'Match score and analysis are not available because the recommendation model is not connected.'}</p><small>{analysis?.modelVersion ?? 'No model connected'}</small><div className="review-actions"><button className="button primary" onClick={() => nav(`/recruiter/applications/${application.applicationId}`)}>Return to application decision</button></div></section></div>;
}
