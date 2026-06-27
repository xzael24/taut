package com.taut.app.ui.screens.weigh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taut.app.data.local.entity.CustomerEntity
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.TransactionItemEntity
import com.taut.app.data.local.entity.WasteCategoryEntity
import com.taut.app.data.repository.CustomerRepository
import com.taut.app.data.repository.OperatorRepository
import com.taut.app.data.repository.SmsQueueRepository
import com.taut.app.data.repository.TransactionRepository
import com.taut.app.data.repository.WasteCategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Shared ViewModel for the weigh flow (WeightEntry → CategorySelection → Confirmation → Saved).
 *
 * Scoped to the Activity so all weigh screens share the same instance.
 * Holds transient state for the current transaction being built.
 */
@HiltViewModel
class WeighViewModel @Inject constructor(
    private val categoryRepository: WasteCategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val customerRepository: CustomerRepository,
    private val operatorRepository: OperatorRepository,
    private val smsQueueRepository: SmsQueueRepository
) : ViewModel() {

    // ---- Transient weigh-flow state ----

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _weightGrams = MutableStateFlow(0L)
    val weightGrams: StateFlow<Long> = _weightGrams.asStateFlow()

    private val _selectedCategory = MutableStateFlow<WasteCategoryEntity?>(null)
    val selectedCategory: StateFlow<WasteCategoryEntity?> = _selectedCategory.asStateFlow()

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    // ---- Derived state ----

    /** Computed total value: weight * price per kg (integer arithmetic). */
    val totalValue: Long
        get() {
            val category = _selectedCategory.value ?: return 0L
            val weightGrams = _weightGrams.value
            return if (weightGrams >= 1000) {
                (weightGrams / 1000) * category.unitPrice
            } else {
                (weightGrams * category.unitPrice) / 1000
            }
        }

    // ---- DB-backed state ----

    /** All active categories (auto-refreshing). */
    val categories: StateFlow<List<WasteCategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Search categories by query. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchCategories(query: String): StateFlow<List<WasteCategoryEntity>> {
        return if (query.isBlank()) {
            categories
        } else {
            categoryRepository.searchCategories(query)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    /** Search customers by name or phone (auto-refreshing). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun searchCustomers(query: String): StateFlow<List<CustomerEntity>> {
        return if (query.length < 2) {
            MutableStateFlow<List<CustomerEntity>>(emptyList()).asStateFlow()
        } else {
            customerRepository.searchCustomers(query)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())
        }
    }

    // ---- Actions ----

    fun setCustomerName(name: String) { _customerName.value = name }

    fun setCustomerPhone(phone: String) { _customerPhone.value = phone }

    fun setWeightGrams(grams: Long) { _weightGrams.value = grams }

    fun selectCategory(category: WasteCategoryEntity) {
        _selectedCategory.value = category
    }

    fun setPhotoUri(uri: String?) { _photoUri.value = uri }

    /** Reset all transient state for a new transaction. */
    fun reset() {
        _customerName.value = ""
        _customerPhone.value = ""
        _weightGrams.value = 0L
        _selectedCategory.value = null
        _photoUri.value = null
    }

    /**
     * Save the current transaction to the local database.
     * Returns the transaction ID on success, null on failure.
     */
    fun saveTransaction(onResult: (Boolean) -> Unit) {
        val category = _selectedCategory.value ?: run {
            onResult(false)
            return
        }
        val transactionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val transaction = TransactionEntity(
            id = transactionId,
            bankSampahId = "local",       // Overridden during sync
            operatorId = "local_operator", // Will be set by auth
            customerId = "walk_in",        // Will be matched to real customer
            transactionType = "deposit",
            status = "pending_sync",
            totalWeight = _weightGrams.value,
            totalValue = totalValue,
            deviceTimestamp = now,
            serverTimestamp = null,
            syncId = null,
            isOfflineCreated = true,
            hmacSignature = null,
            priceSnapshot = null,
            smsSent = false,
            createdAt = now,
            updatedAt = now
        )

        val item = TransactionItemEntity(
            id = UUID.randomUUID().toString(),
            transactionId = transactionId,
            categoryId = category.id,
            weight = _weightGrams.value,
            pricePerUnit = category.unitPrice,
            totalValue = totalValue,
            createdAt = now
        )

        viewModelScope.launch {
            try {
                transactionRepository.insertTransactionWithItems(transaction, listOf(item))
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    /** Formatted customer display string. */
    fun customerDisplay(): String =
        buildString {
            append(_customerName.value)
            if (_customerPhone.value.isNotBlank()) {
                append(" (${_customerPhone.value})")
            }
        }

    /** Formatted weight display string. */
    fun weightDisplay(): String {
        val g = _weightGrams.value
        return if (g >= 1000) {
            "%.1f kg".format(g / 1000.0)
        } else {
            "$g g"
        }
    }

    /** Formatted price display for a category. */
    fun priceDisplay(pricePerUnit: Long): String =
        "Rp%,d/kg".format(pricePerUnit / 100)
}
