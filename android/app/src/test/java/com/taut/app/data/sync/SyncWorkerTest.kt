package com.taut.app.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.taut.app.data.repository.TransactionRepository
import com.taut.app.data.sync.SyncMetadataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

/**
 * Tests for SyncWorker — verifying sync execution and error handling.
 *
 * Tests isolate the worker's doWork() logic by mocking all dependencies and
 * avoiding real gRPC calls (which would fail in unit tests).
 */
@RunWith(JUnit4::class)
class SyncWorkerTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var syncMetadataStore: SyncMetadataStore
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        transactionRepository = mockk(relaxed = true)
        syncMetadataStore = mockk(relaxed = true)
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)

        // Mock workerParams to allow runAttemptCount access (needed by CoroutineWorker)
        every { workerParams.runAttemptCount } returns 0
        every { workerParams.id } returns UUID.randomUUID()
        every { workerParams.inputData } returns androidx.work.Data.EMPTY
        every { workerParams.tags } returns emptySet()
    }

    @Test
    fun doWork_successWhenNoPendingTransactions() = runTest {
        coEvery { transactionRepository.getPendingTransactions() } returns emptyList()

        val worker = createWorker()
        val result = worker.doWork()

        assertTrue("Should return success when no pending transactions", result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_retryWhenNetworkErrorOccurs() = runTest {
        coEvery { transactionRepository.getPendingTransactions() } throws RuntimeException("Network error")

        val worker = createWorker()
        val result = worker.doWork()

        assertTrue("Should return retry when network error occurs", result is ListenableWorker.Result.Retry)
    }

    @Test
    fun doWork_failureWhenRetriesExhausted() = runTest {
        coEvery { transactionRepository.getPendingTransactions() } throws RuntimeException("Network error")

        val exhaustedParams = mockk<WorkerParameters>(relaxed = true) {
            every { runAttemptCount } returns 5
            every { id } returns UUID.randomUUID()
            every { inputData } returns androidx.work.Data.EMPTY
            every { tags } returns emptySet()
        }

        val worker = SyncWorker(
            appContext = context,
            workerParams = exhaustedParams,
            transactionRepository = transactionRepository,
            syncMetadataStore = syncMetadataStore
        )
        val result = worker.doWork()

        assertTrue("Should return failure when retries exhausted", result is ListenableWorker.Result.Failure)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createWorker(): SyncWorker {
        return SyncWorker(
            appContext = context,
            workerParams = workerParams,
            transactionRepository = transactionRepository,
            syncMetadataStore = syncMetadataStore
        )
    }
}
