package com.taut.app.data.repository

import com.taut.app.data.local.dao.WasteCategoryDao
import com.taut.app.data.local.dao.escapeForLike
import com.taut.app.data.local.entity.WasteCategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for waste category operations.
 * Wraps [WasteCategoryDao] with any business logic needed.
 */
@Singleton
class WasteCategoryRepository @Inject constructor(
    private val dao: WasteCategoryDao
) {
    /** Observe all active categories, sorted by sort order. */
    fun getAllCategories(): Flow<List<WasteCategoryEntity>> = dao.getAllCategories()

    /** Get a single category by ID (suspend). */
    suspend fun getCategoryById(id: String): WasteCategoryEntity? = dao.getCategoryById(id)

    /** Search categories by name. */
    fun searchCategories(query: String): Flow<List<WasteCategoryEntity>> = dao.searchCategories(escapeForLike(query))

    /** Bulk replace all categories (used during sync). */
    suspend fun replaceAll(categories: List<WasteCategoryEntity>) {
        dao.deleteAll()
        dao.insertAll(categories)
    }

    /** Insert or update a single category. */
    suspend fun save(category: WasteCategoryEntity) = dao.insert(category)

    /** Update a single category. */
    suspend fun update(category: WasteCategoryEntity) = dao.update(category)

    /** Delete a category by ID. */
    suspend fun deleteById(id: String) = dao.deleteById(id)
}
