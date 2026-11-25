package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverter
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SecondHandStatus
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import com.qalqan.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

class RoomEnumConverters {
  @TypeConverter fun fromAccountType(v: AccountType): String = v.name
  @TypeConverter fun toAccountType(v: String): AccountType = AccountType.valueOf(v)

  @TypeConverter fun fromDepositStatus(v: DepositStatus): String = v.name
  @TypeConverter fun toDepositStatus(v: String): DepositStatus = DepositStatus.valueOf(v)

  @TypeConverter fun fromTransferType(v: TransferType): String = v.name
  @TypeConverter fun toTransferType(v: String): TransferType = TransferType.valueOf(v)

  @TypeConverter fun fromTransferCodeType(v: TransferCodeType): String = v.name
  @TypeConverter fun toTransferCodeType(v: String): TransferCodeType = TransferCodeType.valueOf(v)

  @TypeConverter fun fromTransferStatus(v: TransferStatus): String = v.name
  @TypeConverter fun toTransferStatus(v: String): TransferStatus = TransferStatus.valueOf(v)

  @TypeConverter fun fromSecondHandStatus(v: SecondHandStatus): String = v.name
  @TypeConverter fun toSecondHandStatus(v: String): SecondHandStatus = SecondHandStatus.valueOf(v)

  @TypeConverter fun fromNbrkTrigger(v: NbrkTrigger): String = v.name
  @TypeConverter fun toNbrkTrigger(v: String): NbrkTrigger = NbrkTrigger.valueOf(v)

  @TypeConverter fun fromUserRole(v: UserRole): String = v.name
  @TypeConverter fun toUserRole(v: String): UserRole = UserRole.valueOf(v)
}

@Dao
interface AccountDao {
  @Query("SELECT * FROM accounts WHERE type IN (:allowedTypes) ORDER BY id ASC")
  fun observeByTypes(allowedTypes: List<AccountType>): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): AccountEntity?

  @Query("SELECT * FROM accounts")
  suspend fun getAllSnapshot(): List<AccountEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<AccountEntity>)

  @Query("UPDATE accounts SET balanceTenge = :newBalance WHERE id = :id")
  suspend fun updateBalance(id: String, newBalance: Long)

  @Query("DELETE FROM accounts")
  suspend fun clearAll()
}

@Dao
interface DepositDao {
  @Query("SELECT * FROM deposits WHERE isDecoy = :isDecoy ORDER BY balanceTenge DESC")
  fun observeByDecoyFlag(isDecoy: Boolean): Flow<List<DepositEntity>>

  @Query("SELECT * FROM deposits WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): DepositEntity?

  @Query("SELECT * FROM deposits")
  suspend fun getAllSnapshot(): List<DepositEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<DepositEntity>)

  @Query("UPDATE deposits SET status = :status, isMoneyLocked = :isLocked, balanceTenge = :balance WHERE id = :id")
  suspend fun updateDepositState(id: String, status: DepositStatus, isLocked: Boolean, balance: Long)

  @Query("DELETE FROM deposits")
  suspend fun clearAll()
}

@Dao
interface TransferDao {
  @Query("SELECT * FROM transfers WHERE sourceAccountId IN (:allowedAccountIds) ORDER BY createdAtMillis DESC")
  fun observeByAccountIds(allowedAccountIds: List<String>): Flow<List<TransferEntity>>

  @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): TransferEntity?

  @Query("SELECT COUNT(*) FROM transfers WHERE sourceAccountId = :accountId AND createdAtMillis >= :sinceMillis AND transferType != 'MERCHANT_PURCHASE'")
  suspend fun countOutgoingSince(accountId: String, sinceMillis: Long): Int

  @Query("SELECT * FROM transfers WHERE status IN (:statuses)")
  suspend fun getByStatuses(statuses: List<TransferStatus>): List<TransferEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: TransferEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<TransferEntity>)

  @Query("UPDATE transfers SET status = :status WHERE id = :id")
  suspend fun updateStatus(id: String, status: TransferStatus)

  @Query("DELETE FROM transfers")
  suspend fun clearAll()
}

@Dao
interface SecondHandDao {
  @Query("SELECT * FROM second_hand_requests ORDER BY createdAtMillis DESC")
  fun observeAll(): Flow<List<SecondHandRequestEntity>>

  @Query("SELECT * FROM second_hand_requests WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): SecondHandRequestEntity?

  @Query("SELECT * FROM second_hand_requests WHERE status = 'PENDING'")
  suspend fun getPending(): List<SecondHandRequestEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: SecondHandRequestEntity)

  @Query("DELETE FROM second_hand_requests")
  suspend fun clearAll()
}

@Dao
interface NbrkIncidentDao {
  @Query("SELECT * FROM nbrk_incidents ORDER BY createdAtMillis DESC")
  fun observeAll(): Flow<List<NbrkIncidentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: NbrkIncidentEntity)

  @Query("DELETE FROM nbrk_incidents")
  suspend fun clearAll()
}

@Dao
interface SecurityConfigDao {
  @Query("SELECT * FROM security_config WHERE id = 1 LIMIT 1")
  fun observeConfig(): Flow<SecurityConfigEntity?>

  @Query("SELECT * FROM security_config WHERE id = 1 LIMIT 1")
  suspend fun getConfig(): SecurityConfigEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(config: SecurityConfigEntity)

  @Query("UPDATE security_config SET failedPinAttempts = :attempts, lockedUntilMillis = :lockedUntil WHERE id = 1")
  suspend fun updateAttemptState(attempts: Int, lockedUntil: Long)

  @Query("DELETE FROM security_config")
  suspend fun clearAll()
}

@Dao
interface LedgerDao {
  @Query("SELECT * FROM ledger_entries ORDER BY createdAtMillis DESC")
  fun observeAll(): Flow<List<LedgerEntryEntity>>

  @Query("SELECT * FROM ledger_entries")
  suspend fun getAllSnapshot(): List<LedgerEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(entries: List<LedgerEntryEntity>)

  @Query("DELETE FROM ledger_entries")
  suspend fun clearAll()
}

@Dao
interface AuditLogDao {
  @Query("SELECT * FROM audit_logs ORDER BY createdAtMillis DESC LIMIT 200")
  fun observeRecent(): Flow<List<AuditLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: AuditLogEntity)

  @Query("DELETE FROM audit_logs")
  suspend fun clearAll()
}

@Dao
interface KnownRecipientDao {
  @Query("SELECT * FROM known_recipients WHERE isDropperFlag = 0 ORDER BY name ASC")
  fun observeTrusted(): Flow<List<KnownRecipientEntity>>

  @Query("SELECT * FROM known_recipients WHERE iban = :iban LIMIT 1")
  suspend fun findByIban(iban: String): KnownRecipientEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<KnownRecipientEntity>)

  @Query("DELETE FROM known_recipients")
  suspend fun clearAll()
}
