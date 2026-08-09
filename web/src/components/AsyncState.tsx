import type {ReactNode} from 'react';
export function LoadingState({label='Loading workspace…'}:{label?:string}){return <div className="state-card"><span className="spinner"/>{label}</div>}
export function ErrorState({onRetry}:{onRetry:()=>void}){return <div className="state-card error"><strong>Something went wrong</strong><span>We could not load this content.</span><button className="button secondary" onClick={onRetry}>Try again</button></div>}
export function EmptyState({title='Nothing here yet',description='There is no data to display.',action}:{title?:string;description?:string;action?:ReactNode}){return <div className="state-card"><strong>{title}</strong><span>{description}</span>{action}</div>}
