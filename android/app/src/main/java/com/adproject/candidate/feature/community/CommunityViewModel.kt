package com.adproject.candidate.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adproject.candidate.data.api.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityUiState(
    val posts: List<CommunityPost> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val submitting: Boolean = false,
    val draft: String = "",
    val loadError: String? = null,
    val publishError: String? = null,
)

class CommunityViewModel(private val repository: CommunityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = mutableState.asStateFlow()

    init { loadFirst() }

    fun updateDraft(value: String) = mutableState.update { it.copy(draft = value, publishError = null) }
    fun retry() = loadFirst()
    fun refresh() = loadFirst(refreshing = true)
    fun loadMore() {
        val current = mutableState.value
        if (!current.hasNext || current.loadingMore || current.loading || current.refreshing) return
        load(current.page + 1, append = true)
    }

    fun publish() {
        val current = mutableState.value
        val length = current.draft.codePointCount(0, current.draft.length)
        if (current.submitting || current.draft.isBlank() || length > 2000) return
        mutableState.update { it.copy(submitting = true, publishError = null) }
        viewModelScope.launch {
            when (val result = repository.create(current.draft)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(submitting = false, draft = "", publishError = null) }
                    loadFirst(refreshing = true)
                }
                is ApiResult.Failure -> mutableState.update { it.copy(submitting = false, publishError = result.message) }
            }
        }
    }

    private fun loadFirst(refreshing: Boolean = false) = load(1, append = false, refreshing = refreshing)

    private fun load(page: Int, append: Boolean, refreshing: Boolean = false) {
        val current = mutableState.value
        if ((!append && (current.loading && current.page > 0 || current.refreshing)) || (append && current.loadingMore)) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = !append && !refreshing && it.posts.isEmpty(), refreshing = refreshing,
                    loadingMore = append, loadError = null,
                )
            }
            when (val result = repository.posts(page)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(
                        posts = if (append) (it.posts + result.value.posts).distinctBy(CommunityPost::id) else result.value.posts,
                        page = result.value.meta.page, hasNext = result.value.meta.hasNext,
                        loading = false, refreshing = false, loadingMore = false, loadError = null,
                    )
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(loading = false, refreshing = false, loadingMore = false, loadError = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: CommunityRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CommunityViewModel(repository) as T
        }
    }
}
