package de.mm20.launcher2.network.nextdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NextDnsViewModel(private val repo: NextDnsRepository) : ViewModel() {
    private val _logs = MutableStateFlow<List<NextDnsLogEntry>>(emptyList())
    val logs: StateFlow<List<NextDnsLogEntry>> = _logs

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setToken(token: String) {
        repo.saveToken(token)
        // auto fetch after saving
        fetchLogs()
    }

    fun fetchLogs() {
        viewModelScope.launch {
            _loading.value = true
            val res = repo.fetchLogs(100)
            if (res.isSuccess) {
                _logs.value = res.getOrNull()?.items ?: emptyList()
                _error.value = null
            } else {
                _error.value = res.exceptionOrNull()?.localizedMessage ?: "Unknown error"
            }
            _loading.value = false
        }
    }

    fun allowDomain(domain: String) {
        viewModelScope.launch {
            _loading.value = true
            val r = repo.postRule(domain, true)
            if (r.isSuccess) {
                // optimistic update: mark matching logs as allowed (no mutation here, just refetch)
                fetchLogs()
                _error.value = null
            } else {
                _error.value = r.exceptionOrNull()?.localizedMessage ?: "Failed to allow"
            }
            _loading.value = false
        }
    }

    fun denyDomain(domain: String) {
        viewModelScope.launch {
            _loading.value = true
            val r = repo.postRule(domain, false)
            if (r.isSuccess) {
                fetchLogs()
                _error.value = null
            } else {
                _error.value = r.exceptionOrNull()?.localizedMessage ?: "Failed to deny"
            }
            _loading.value = false
        }
    }
}
