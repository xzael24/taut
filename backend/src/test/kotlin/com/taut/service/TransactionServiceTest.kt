package com.taut.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for TransactionService.calculateItemValue().
 *
 * Logic:
 * - weight >= 1000g: (weight / 1000) * pricePerUnit
 * - weight < 1000g && weight > 0: proportional, minimum 1
 * - weight == 0: return 0
 */
class TransactionServiceTest {

    @Test
    fun `weight exactly 1kg returns price per kg`() {
        // 1000 / 1000 * 5000 = 5000
        assertEquals(5_000, TransactionService.calculateItemValue(1000, 5_000))
    }

    @Test
    fun `weight 2kg returns 2x price per kg`() {
        // 2000 / 1000 * 3000 = 6000
        assertEquals(6_000, TransactionService.calculateItemValue(2000, 3_000))
    }

    @Test
    fun `weight less than 1kg returns proportional value`() {
        // 500 * 2000 / 1000 = 1000
        assertEquals(1_000, TransactionService.calculateItemValue(500, 2_000))
    }

    @Test
    fun `weight less than 1kg with tiny proportional value returns min 1`() {
        // 1 * 500 / 1000 = 0, but weight > 0 so min = 1
        assertEquals(1, TransactionService.calculateItemValue(1, 500))
    }

    @Test
    fun `weight zero returns 0 regardless of price`() {
        assertEquals(0, TransactionService.calculateItemValue(0, 10_000))
        assertEquals(0, TransactionService.calculateItemValue(0, 0))
    }

    @Test
    fun `large weight and price does not overflow`() {
        // 100_000 / 1000 * 10_000 = 100 * 10_000 = 1_000_000
        assertEquals(1_000_000, TransactionService.calculateItemValue(100_000, 10_000))
    }

    @Test
    fun `very large values within Long range`() {
        // 1_000_000g (1000kg) @ 1_000_000 per kg = 1_000_000_000
        assertEquals(1_000_000_000, TransactionService.calculateItemValue(1_000_000, 1_000_000))
    }

    @Test
    fun `weight exactly 999g returns proportional`() {
        // 999 * 2000 / 1000 = 1998
        assertEquals(1_998, TransactionService.calculateItemValue(999, 2_000))
    }

    @Test
    fun `weight exactly 1001g uses integer division`() {
        // 1001 / 1000 * 2000 = 1 * 2000 = 2000
        assertEquals(2_000, TransactionService.calculateItemValue(1001, 2_000))
    }

    @ParameterizedTest
    @CsvSource(
        "1000, 1000, 1000",
        "500,  1000, 500",
        "250,  400,  100",
        "999,  1000, 999",
        "1001, 1000, 1000",
        "0,    5000, 0",
        "1,    1,    1",
        "100,  10,   1",
        "200,  5,    1",
        "3000, 700,  2100"
    )
    fun `calculateItemValue parameterized scenarios`(weight: Long, pricePerUnit: Long, expected: Long) {
        assertEquals(expected, TransactionService.calculateItemValue(weight, pricePerUnit))
    }
}
