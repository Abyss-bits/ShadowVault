package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.crypto.DatabaseKeyProvider
import com.example.data.local.AccountDao
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogDao
import com.example.data.local.DepositDao
import com.example.data.local.KnownRecipientDao
import com.example.data.local.LedgerDao
import com.example.data.local.NbrkIncidentDao
import com.example.data.local.SecondHandDao
import com.example.data.local.SecurityConfigDao
import com.example.data.local.TransferDao
import com.qalqan.domain.clock.AppClock
import com.qalqan.domain.clock.BusinessCalendar
import com.qalqan.domain.clock.SimulatedClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideSimulatedClock(): SimulatedClock = SimulatedClock()

  @Provides
  @Singleton
  fun provideAppClock(simulatedClock: SimulatedClock): AppClock = simulatedClock

  @Provides
  @Singleton
  fun provideBusinessCalendar(): BusinessCalendar = BusinessCalendar()

  @Provides
  @Singleton
  fun provideAppDatabase(
    @ApplicationContext context: Context,
    keyProvider: DatabaseKeyProvider
  ): AppDatabase {
    System.loadLibrary("sqlcipher")
    val passphrase = keyProvider.getOrCreateDatabasePassphrase()
    val factory = SupportOpenHelperFactory(passphrase)
    return Room.databaseBuilder(context, AppDatabase::class.java, "qalqan_vault.db")
      .openHelperFactory(factory)
      .fallbackToDestructiveMigration(false)
      .build()
  }

  @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
  @Provides fun provideDepositDao(db: AppDatabase): DepositDao = db.depositDao()
  @Provides fun provideTransferDao(db: AppDatabase): TransferDao = db.transferDao()
  @Provides fun provideSecondHandDao(db: AppDatabase): SecondHandDao = db.secondHandDao()
  @Provides fun provideNbrkIncidentDao(db: AppDatabase): NbrkIncidentDao = db.nbrkIncidentDao()
  @Provides fun provideSecurityConfigDao(db: AppDatabase): SecurityConfigDao = db.securityConfigDao()
  @Provides fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()
  @Provides fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
  @Provides fun provideKnownRecipientDao(db: AppDatabase): KnownRecipientDao = db.knownRecipientDao()
}
