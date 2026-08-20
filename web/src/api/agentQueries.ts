import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {agentHttpClient as api} from './agentHttpClient';
import type {ConfirmAgentRunInput,CreateAgentRunInput} from './agentHttpClient';
import {DETAIL_INTERVAL_MS, LIST_INTERVAL_MS, usePollingQuery} from './polling';

export const agentKeys={
  conversations:['agent','conversations'] as const,
  conversation:(id:string)=>['agent','conversation',id] as const,
  run:(id:string)=>['agent','run',id] as const,
};

export const useAgentConversations=()=>usePollingQuery({
  queryKey:agentKeys.conversations,queryFn:()=>api.listConversations(),enabled:true,intervalMs:LIST_INTERVAL_MS,
});

export const useAgentConversation=(id:string)=>usePollingQuery({
  queryKey:agentKeys.conversation(id),queryFn:()=>api.getConversation(id),enabled:!!id,intervalMs:DETAIL_INTERVAL_MS,
});

export const useAgentRun=(id:string)=>useQuery({queryKey:agentKeys.run(id),queryFn:()=>api.getRun(id),enabled:!!id});

export function useCreateAgentRun(){
  const qc=useQueryClient();
  return useMutation({
    mutationFn:(input:CreateAgentRunInput)=>api.createRun(input),
    onSuccess:run=>{
      qc.invalidateQueries({queryKey:agentKeys.conversations});
      if(run.conversationId) qc.invalidateQueries({queryKey:agentKeys.conversation(run.conversationId)});
      qc.setQueryData(agentKeys.run(run.runId),run);
    },
  });
}

export function useCancelAgentRun(){
  const qc=useQueryClient();
  return useMutation({
    mutationFn:(runId:string)=>api.cancelRun(runId),
    onSuccess:run=>{
      qc.invalidateQueries({queryKey:agentKeys.conversations});
      if(run.conversationId) qc.invalidateQueries({queryKey:agentKeys.conversation(run.conversationId)});
      qc.setQueryData(agentKeys.run(run.runId),run);
    },
  });
}

export function useDeleteAgentConversation(){
  const qc=useQueryClient();
  return useMutation({
    mutationFn:(conversationId:string)=>api.deleteConversation(conversationId),
    onSuccess:(_data,conversationId)=>{
      qc.invalidateQueries({queryKey:agentKeys.conversations});
      qc.removeQueries({queryKey:agentKeys.conversation(conversationId)});
    },
  });
}

export function useConfirmAgentRun(){
  const qc=useQueryClient();
  return useMutation({
    mutationFn:(input:ConfirmAgentRunInput)=>api.confirmRun(input),
    onSuccess:run=>{
      qc.invalidateQueries({queryKey:agentKeys.conversations});
      if(run.conversationId) qc.invalidateQueries({queryKey:agentKeys.conversation(run.conversationId)});
      qc.setQueryData(agentKeys.run(run.runId),run);
    },
    // A rejected confirm (expired preview, version conflict, …) also changes the run's
    // persisted state, so refetch it to show the safe explanation from the server.
    onError:()=>{
      qc.invalidateQueries({queryKey:['agent']});
    },
  });
}

export function useStartAgentOutreach(){
  return useMutation({mutationFn:({runId,candidateId}:{runId:string;candidateId:string})=>
    api.startOutreachConversation(runId,candidateId)});
}
