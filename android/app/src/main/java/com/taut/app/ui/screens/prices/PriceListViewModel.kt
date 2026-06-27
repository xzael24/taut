package com.taut.app.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.local.entity.WasteCategoryEntity
import com.taut.app.data.repository.WasteCategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for the Price List screen.
 * Shows all waste categories with current prices.
 */
@HiltViewModel
class PriceListViewModel @Inject constructor(
    private val categoryRepository: WasteCategoryRepository
) : ViewModel() {

    /** All active categories. */
    val categories: StateFlow<List<WasteCategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Filtered categories based on search query (debounced). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredCategories: StateFlow<List<WasteCategoryEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                categoryRepository.getAllCategories()
            } else {
                categoryRepository.searchCategories(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Timestamp of last update (placeholder — will be from sync metadata). */
    val lastUpdatedDisplay: String
        get() {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.of("Asia/Jakarta"))
            return "Terakhir diperbarui: ${formatter.format(Instant.now())}"
        }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Format price per unit as a readable rupiah string.
     *  Raw price is stored in satuan (cent-like units); divide by 100 for Rp display. */
    fun formatPrice(pricePerUnit: Long): String = "Rp%,d/kg".format(pricePerUnit / 100)
}
