package com.adproject.candidate.feature.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.adproject.candidate.core.designsystem.*
import com.adproject.candidate.data.api.ApiResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CommunityDirectUiState(val conversation:CommunityDirectConversation?=null,val messages:List<CommunityDirectMessage> = emptyList(),val draft:String="",val loading:Boolean=true,val sending:Boolean=false,val error:String?=null)
class CommunityDirectViewModel(private val id:String,private val repository:CommunityRepository):ViewModel(){private val mutable=MutableStateFlow(CommunityDirectUiState());val state=mutable.asStateFlow();init{load()}
 fun load(){viewModelScope.launch{mutable.update{it.copy(loading=true,error=null)};val detail=repository.direct(id);val messages=repository.directMessages(id);if(detail is ApiResult.Success&&messages is ApiResult.Success)mutable.update{it.copy(conversation=detail.value,messages=messages.value,loading=false)}else mutable.update{it.copy(loading=false,error=(detail as? ApiResult.Failure)?.message?:(messages as? ApiResult.Failure)?.message)}}}
 fun updateDraft(value:String)=mutable.update{it.copy(draft=value.take(2000),error=null)}
 fun send(){val body=mutable.value.draft.trim();if(body.isEmpty()||mutable.value.sending)return;mutable.update{it.copy(sending=true,error=null)};viewModelScope.launch{when(val result=repository.sendDirect(id,body)){is ApiResult.Success->mutable.update{it.copy(messages=it.messages+result.value,draft="",sending=false)};is ApiResult.Failure->mutable.update{it.copy(sending=false,error=result.message)}}}}
 companion object{fun factory(id:String,repository:CommunityRepository)=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>)=CommunityDirectViewModel(id,repository)as T}}
}
@Composable fun CommunityDirectMessageScreen(state:CommunityDirectUiState,onBack:()->Unit,onRetry:()->Unit,onDraft:(String)->Unit,onSend:()->Unit){Scaffold(containerColor=AdBackground,topBar={AdTopBar(state.conversation?.participant?.fullName?:"Community message",onBack)}){padding->when{state.loading->Box(Modifier.fillMaxSize().padding(padding),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator(color=AdTeal)};state.conversation==null->StateBox{Text(state.error?:"Conversation unavailable");SecondaryButton("Try again",onRetry)};else->Column(Modifier.fillMaxSize().padding(padding)){LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){if(state.messages.isEmpty())item{Text("No messages yet. Say hello.",color=AdMuted)}else items(state.messages,key={it.messageId}){message->AdCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(message.body,color=AdText);Text(localTime(message.sentAt),color=AdMuted)}}}};state.error?.let{Text(it,Modifier.padding(horizontal=16.dp),color=Color(0xFFB42318))};Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(state.draft,onDraft,Modifier.weight(1f),placeholder={Text("Message")});PrimaryButton(if(state.sending)"Sending…" else "Send",onSend,enabled=!state.sending&&state.draft.isNotBlank())}}}}}
