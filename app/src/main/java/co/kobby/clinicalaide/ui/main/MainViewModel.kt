package co.kobby.clinicalaide.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kobby.clinicalaide.data.rag.RagRepository
import co.kobby.clinicalaide.data.rag.entities.ContentChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val ragRepository: RagRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load RAG database stats
                val ragStats = ragRepository.getDatabaseStats()
                
                // Load chapters from RAG database
                val ragChapters = ragRepository.getAllChapters()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    ragStats = ragStats,
                    ragChapters = ragChapters,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
            }
        }
    }
    
    fun refreshData() {
        loadData()
    }
    
    fun selectChapter(chapterId: Long) {
        viewModelScope.launch {
            try {
                // For now, we'll just store the selected chapter ID
                _uiState.value = _uiState.value.copy(
                    selectedChapterId = chapterId
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load chapter data: ${e.message}"
                )
            }
        }
    }
    
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedChapterId = null
        )
    }
    
    fun searchMedicalContent(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                isSearching = true,
                error = null
            )
            
            try {
                val results = ragRepository.searchMedicalContent(query, limit = 20)
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Search failed: ${e.message}"
                )
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val ragChapters: List<co.kobby.clinicalaide.data.rag.entities.Chapter> = emptyList(),
    val ragStats: co.kobby.clinicalaide.data.rag.dao.RagDao.DatabaseStats? = null,
    val selectedChapterId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<ContentChunk> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)