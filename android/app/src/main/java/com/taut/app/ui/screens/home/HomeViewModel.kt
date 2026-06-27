package com.taut.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.local.dao.PeriodSummary
import com.taut.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 * Provides today's transaction summary and operator/bank info.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    /** Today's aggregated summary (count, total weight, total value). */
    val todaySummary: StateFlow<PeriodSummary?> = transactionRepository
        .getSummarySince(transactionRepository.todayStartOfDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Placeholder: will be loaded from OperatorRepository / DeviceRepository
    val bankName: String = "Bank Sampah Melati"

    /** Operator greeting based on time of day. */
    val greeting: String
        get() {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return when {
                hour < 11 -> "Selamat pagi"
                hour < 15 -> "Selamat siang"
                hour < 18 -> "Selamat sore"
                else -> "Selamat malam"
            }
        }
}
