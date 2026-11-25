package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities = [
    AccountEntity::class,
    DepositEntity::class,
    TransferEntity::class,
    SecondHandRequestEntity::class,
    NbrkIncidentEntity::class,
    SecurityConfigEntity::class,
    LedgerEntryEntity::class,
    AuditLogEntity::class,
    KnownRecipientEntity::class
  ],
  version = 1,
  exportSchema = false
)
@TypeConverters(RoomEnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun accountDao(): AccountDao
  abstract fun depositDao(): DepositDao
  abstract fun transferDao(): TransferDao
  abstract fun secondHandDao(): SecondHandDao
  abstract fun nbrkIncidentDao(): NbrkIncidentDao
  abstract fun securityConfigDao(): SecurityConfigDao
  abstract fun ledgerDao(): LedgerDao
  abstract fun auditLogDao(): AuditLogDao
  abstract fun knownRecipientDao(): KnownRecipientDao
}
