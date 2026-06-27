package com.taut.app.ui.screens.weigh

import com.taut.app.data.local.entity.CustomerEntity
import com.taut.app.data.local.entity.TransactionEntity
import com.taut.app.data.local.entity.WasteCategoryEntity
import com.taut.app.data.repository.CustomerRepository
import com.taut.app.data.repository.OperatorRepository
import com.taut.app.data.repository.SmsQueueRepository
import com.taut.app.data.repository.TransactionRepository
import com.taut.app.data.repository.WasteCategoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for WeighViewModel — monetary calculation, weight display, and transaction saving.
 */
@RunWith(RobolectricTestRunner::class)
class WeighViewModelTest {

    private lateinit var categoryRepository: WasteCategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var operatorRepository: OperatorRepository
    private lateinit var smsQueueRepository: SmsQueueRepository
    private lateinit var viewModel: WeighViewModel

    private val cardboardCategory = WasteCategoryEntity(
        id = "cat-kardus",
        code = "KRD-01",
        nameId = "Kardus",
        nameEn = "Cardboard",
        categoryGroup = "kardus",
        unitType = "kg",
        unitPrice = 5000L, // Rp 5,000/kg
        photoUrl = null,
        photoPath = null,
        sortOrder = 1,
        isActive = true,
        version = 0,
        updatedAt = System.currentTimeMillis()
    )

    private val plasticCategory = WasteCategoryEntity(
        id = "cat-plastik",
        code = "PLS-01",
        nameId = "Plastik",
        nameEn = "Plastic",
        categoryGroup = "plastik",
        unitType = "kg",
        unitPrice = 3000L, // Rp 3,000/kg
        photoUrl = null,
        photoPath = null,
        sortOrder = 2,
        isActive = true,
        version = 0,
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        categoryRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        customerRepository = mockk(relaxed = true)
        operatorRepository = mockk(relaxed = true)
        smsQueueRepository = mockk(relaxed = true)

        // Set up default mock behavior for categories
        every { categoryRepository.getAllCategories() } returns flowOf(
            listOf(cardboardCategory, plasticCategory)
        )

        viewModel = WeighViewModel(
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            customerRepository = customerRepository,
            operatorRepository = operatorRepository,
            smsQueueRepository = smsQueueRepository
        )
    }

    // ── Monetary Calculation (Integer Arithmetic) ──────────────────────────

    @Test
    fun totalValue_exactly1kg_returnsCategoryUnitPrice() {
        viewModel.selectCategory(cardboardCategory)
        viewModel.setWeightGrams(1000L) // 1 kg

        assertEquals("1 kg of cardboard should equal unit price (Rp 5,000)",
            5000L, viewModel.totalValue)
    }

    @Test
    fun totalValue_multipleKg_multipliesCorrectly() {
        viewModel.selectCategory(cardboardCategory)
        viewModel.setWeightGrams(3000L) // 3 kg

        assertEquals("3 kg of cardboard = 3 × 5000 = 15000",
            15000L, viewModel.totalValue)
    }

    @Test
    fun totalValue_lessThan1kg_usesFractionalMath() {
        viewModel.selectCategory(plasticCategory) // unitPrice = 3000
        viewModel.setWeightGrams(500L) // 0.5 kg

        // (500 * 3000) / 1000 = 1500
        assertEquals("500g of plastic = (500 × 3000) / 1000 = 1500",
            1500L, viewModel.totalValue)
    }

    @Test
    fun totalValue_exactFraction_roundsDownCorrectly() {
        viewModel.selectCategory(plasticCategory) // unitPrice = 3000
        viewModel.setWeightGrams(333L) // 0.333 kg

        // (333 * 3000) / 1000 = 999
        assertEquals("333g of plastic = (333 × 3000) / 1000 = 999",
            999L, viewModel.totalValue)
    }

    @Test
    fun totalValue_zeroWeight_returnsZero() {
        viewModel.selectCategory(cardboardCategory)
        viewModel.setWeightGrams(0L)

        assertEquals("Zero weight should give zero value", 0L, viewModel.totalValue)
    }

    @Test
    fun totalValue_noCategorySelected_returnsZero() {
        viewModel.setWeightGrams(1000L)
        // No category selected

        assertEquals("No category should give zero value", 0L, viewModel.totalValue)
    }

    @Test
    fun totalValue_heavyWeight_calculatesCorrectly() {
        viewModel.selectCategory(cardboardCategory) // unitPrice = 5000
        viewModel.setWeightGrams(15000L) // 15 kg

        // (15000 / 1000) * 5000 = 15 * 5000 = 75000
        assertEquals("15 kg of cardboard = (15000/1000) * 5000 = 75000",
            75000L, viewModel.totalValue)
    }

    // ── Weight Display ─────────────────────────────────────────────────────

    @Test
    fun weightDisplay_grams_returnsGramsLabel() {
        viewModel.setWeightGrams(500L)
        assertEquals("500 g", viewModel.weightDisplay())
    }

    @Test
    fun weightDisplay_1kgFormatsAsKilograms() {
        viewModel.setWeightGrams(1000L)
        assertEquals("1.0 kg", viewModel.weightDisplay())
    }

    @Test
    fun weightDisplay_multipleKgFormatsCorrectly() {
        viewModel.setWeightGrams(2500L)
        assertEquals("2.5 kg", viewModel.weightDisplay())
    }

    @Test
    fun weightDisplay_zeroGramsFormatsCorrectly() {
        viewModel.setWeightGrams(0L)
        assertEquals("0 g", viewModel.weightDisplay())
    }

    // ── Price Display ──────────────────────────────────────────────────────

    @Test
    fun priceDisplay_formatsRupiahPerKg() {
        val display = viewModel.priceDisplay(5000L)
        assertEquals("Rp50/kg", display)
    }

    @Test
    fun priceDisplay_largeValueUsesThousandsSeparator() {
        val display = viewModel.priceDisplay(12500L)
        assertEquals("Rp125/kg", display)
    }

    // ── Category Selection ─────────────────────────────────────────────────

    @Test
    fun selectCategory_updatesSelectedCategory() {
        viewModel.selectCategory(cardboardCategory)
        assertEquals(cardboardCategory, viewModel.selectedCategory.value)
    }

    @Test
    fun selectCategory_changesTotalValue() {
        viewModel.setWeightGrams(1000L)
        viewModel.selectCategory(cardboardCategory)
        assertEquals(5000L, viewModel.totalValue)

        viewModel.selectCategory(plasticCategory)
        assertEquals(3000L, viewModel.totalValue)
    }

    // ── Customer Display ──────────────────────────────────────────────────

    @Test
    fun customerDisplay_withNameAndPhone() {
        viewModel.setCustomerName("Budi")
        viewModel.setCustomerPhone("08123456789")
        assertEquals("Budi (08123456789)", viewModel.customerDisplay())
    }

    @Test
    fun customerDisplay_nameOnly() {
        viewModel.setCustomerName("Siti")
        assertEquals("Siti", viewModel.customerDisplay())
    }

    @Test
    fun customerDisplay_emptyFields() {
        assertEquals("", viewModel.customerDisplay())
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsAllTransientState() {
        viewModel.setCustomerName("Test")
        viewModel.setCustomerPhone("123")
        viewModel.setWeightGrams(1000L)
        viewModel.selectCategory(cardboardCategory)

        viewModel.reset()

        assertEquals("", viewModel.customerName.value)
        assertEquals("", viewModel.customerPhone.value)
        assertEquals(0L, viewModel.weightGrams.value)
        assertNull(viewModel.selectedCategory.value)
        assertNull(viewModel.photoUri.value)
    }

    // ── Save Transaction ───────────────────────────────────────────────────

    @Test
    fun saveTransaction_savesWithCorrectValues() {
        viewModel.selectCategory(cardboardCategory)
        viewModel.setWeightGrams(2000L)
        viewModel.setCustomerName("Budi")
        viewModel.setCustomerPhone("08123456789")

        var result = false
        val callback: (Boolean) -> Unit = { result = it }

        // Capture the transaction entity
        val txSlot = slot<TransactionEntity>()
        val itemSlot = slot<List<com.taut.app.data.local.entity.TransactionItemEntity>>()
        coEvery {
            transactionRepository.insertTransactionWithItems(
                capture(txSlot), capture(itemSlot)
            )
        } just Runs

        viewModel.saveTransaction(callback)

        // Wait for coroutine to complete
        Thread.sleep(200)

        assertTrue("Save should succeed", result)
        assertEquals(2000L, txSlot.captured.totalWeight)
        assertEquals(10000L, txSlot.captured.totalValue) // 2kg * 5000
        assertEquals("pending_sync", txSlot.captured.status)
        assertTrue(txSlot.captured.isOfflineCreated)
        assertEquals(1, itemSlot.captured.size)
    }

    @Test
    fun saveTransaction_failsWhenNoCategorySelected() {
        viewModel.setWeightGrams(1000L)
        // No category selected

        var result: Boolean? = null
        viewModel.saveTransaction { result = it }

        assertFalse("Save should fail when no category selected", result!!)
    }

    @Test
    fun saveTransaction_insertsItemWithCorrectValue() = runTest {
        viewModel.selectCategory(cardboardCategory)
        viewModel.setWeightGrams(1500L)

        val itemSlot = slot<List<com.taut.app.data.local.entity.TransactionItemEntity>>()
        coEvery {
            transactionRepository.insertTransactionWithItems(
                any(), capture(itemSlot)
            )
        } just Runs

        viewModel.saveTransaction {}
        Thread.sleep(200)

        val item = itemSlot.captured.first()
        assertEquals("cat-kardus", item.categoryId)
        assertEquals(1500L, item.weight)
        assertEquals(5000L, item.pricePerUnit)
        // ViewModel uses integer arithmetic: (1500 / 1000) * 5000 = 5000
        assertEquals(5000L, item.totalValue)
    }

    // ── Search Customers ──────────────────────────────────────────────────

    @Test
    fun searchCustomers_shortQuery_returnsEmpty() {
        every { customerRepository.searchCustomers(any()) } returns flowOf(emptyList())

        val result = viewModel.searchCustomers("a")
        // Query shorter than 2 char should return empty
        assertEquals(0, result.value.size)
    }

    @Test
    fun searchCustomers_longQuery_filtersResults() = runTest {
        val customers = listOf(
            CustomerEntity(
                id = "cust-budi",
                phoneNumber = "08123456789",
                name = "Budi Santoso",
                address = "Jl. Merdeka",
                villageId = 1,
                isActive = true,
                lastVisitedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        every { customerRepository.searchCustomers("Budi") } returns flowOf(customers)

        val result = viewModel.searchCustomers("Budi")
        // Subscribe to trigger WhileSubscribed flow emission
        val value = result.first()
        assertEquals(1, value.size)
        assertEquals("Budi Santoso", value[0].name)
    }
}
