package com.taut.app.di

import android.content.Context
import com.taut.app.data.local.TautDatabase
import com.taut.app.data.local.dao.CustomerDao
import com.taut.app.data.local.dao.OperatorProfileDao
import com.taut.app.data.local.dao.SmsQueueDao
import com.taut.app.data.local.dao.TransactionDao
import com.taut.app.data.local.dao.TransactionItemDao
import com.taut.app.data.local.dao.UserDao
import com.taut.app.data.local.dao.WasteCategoryDao
import com.taut.app.util.CryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database and DAO instances.
 * Uses SQLCipher encryption via passphrase from CryptoManager.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TautDatabase {
        val passphrase = CryptoManager.getOrCreateDbKey()
        // Defensive copy — SupportFactory may retain a reference to the original array,
        // so we pass a copy and zero the original immediately.
        val passphraseCopy = passphrase.copyOf()
        passphrase.fill(0)
        return TautDatabase.getInstance(context, passphraseCopy)
    }

    @Provides
    fun provideWasteCategoryDao(db: TautDatabase): WasteCategoryDao =
        db.wasteCategoryDao()

    @Provides
    fun provideCustomerDao(db: TautDatabase): CustomerDao =
        db.customerDao()

    @Provides
    fun provideTransactionDao(db: TautDatabase): TransactionDao =
        db.transactionDao()

    @Provides
    fun provideTransactionItemDao(db: TautDatabase): TransactionItemDao =
        db.transactionItemDao()

    @Provides
    fun provideUserDao(db: TautDatabase): UserDao =
        db.userDao()

    @Provides
    fun provideOperatorProfileDao(db: TautDatabase): OperatorProfileDao =
        db.operatorProfileDao()

    @Provides
    fun provideSmsQueueDao(db: TautDatabase): SmsQueueDao =
        db.smsQueueDao()
}
