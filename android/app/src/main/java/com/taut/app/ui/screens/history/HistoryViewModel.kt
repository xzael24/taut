package com.taut.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.dao.PeriodSummary
import com.taut.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Time filter options for the history screen. */
enum class HistoryFilter(val label: String) {
    TODAY("Hari Ini"),
    WEEK("Minggu Ini"),
    MONTH("Bulan Ini")
}

/**
 * ViewModel for the Transaction History screen.
 * Supports time-based filtering and displays transaction list + summary.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(HistoryFilter.TODAY)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    /** Transactions for the current filter period, auto-refreshing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionEntity>> = _selectedFilter
        .flatMapLatest { filter ->
            val (start, end) = timeRangeFor(filter)
            transactionRepository.getTransactionsInRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Summary for the current filter period. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val periodSummary: StateFlow<PeriodSummary?> = _selectedFilter
        .flatMapLatest { filter ->
            val (start, _) = timeRangeFor(filter)
            transactionRepository.getSummarySince(start)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Formatted summary string for the period header. */
    val summaryText: StateFlow<String> = periodSummary.map { summary ->
        if (summary == null) {
            "Total: 0 transaksi | 0 kg | Rp0"
        } else {
            "Total: ${summary.transactionCount} transaksi | %.1f kg | Rp%,d".format(
                summary.totalWeight / 1000.0,
                summary.totalValue / 100
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Memuat...")

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    /** Get time range for a given filter option. */
    private fun timeRangeFor(filter: HistoryFilter): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val start = when (filter) {
            HistoryFilter.TODAY -> transactionRepository.todayStartOfDay()
            HistoryFilter.WEEK -> transactionRepository.weekStart()
            HistoryFilter.MONTH -> transactionRepository.monthStart()
        }
        return start to now
    }
}
