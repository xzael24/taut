package com.taut.app.data.repository

import com.taut.app.data.local.dao.CustomerDao
import com.taut.app.data.local.dao.escapeForLike
import com.taut.app.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for customer (nasabah) operations.
 * Used for customer lookup by name/phone during weigh-in.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val dao: CustomerDao
) {
    /** Observe all active customers, sorted by name. */
    fun getAllCustomers(): Flow<List<CustomerEntity>> = dao.getAllCustomers()

    /** Get a customer by ID. */
    suspend fun getCustomerById(id: String): CustomerEntity? = dao.getCustomerById(id)

    /** Search customers by name or phone number. */
    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = dao.searchCustomers(escapeForLike(query))

    /** Find a customer by phone number. */
    suspend fun getCustomerByPhone(phone: String): CustomerEntity? = dao.getCustomerByPhone(phone)

    /** Insert or update a customer. */
    suspend fun save(customer: CustomerEntity) = dao.insert(customer)

    /** Bulk insert/update customers (during sync). */
    suspend fun saveAll(customers: List<CustomerEntity>) = dao.insertAll(customers)

    /** Update an existing customer. */
    suspend fun update(customer: CustomerEntity) = dao.update(customer)

    /** Delete a customer by ID. */
    suspend fun deleteById(id: String) = dao.deleteById(id)
}
