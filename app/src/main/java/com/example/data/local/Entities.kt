package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.qalqan.domain.model.AccountType
import com.qalqan.domain.model.DepositStatus
import com.qalqan.domain.model.NbrkTrigger
import com.qalqan.domain.model.SecondHandStatus
import com.qalqan.domain.model.TransferCodeType
import com.qalqan.domain.model.TransferStatus
import com.qalqan.domain.model.TransferType
import com.qalqan.domain.model.UserRole

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey val id: String,
  val title: String,
  val iban: String,
  val type: AccountType,
  val balanceTenge: Long
)

@Entity(tableName = "deposits")
data class DepositEntity(
  @PrimaryKey val id: String,
  val title: String,
  val balanceTenge: Long,
  val annualRatePercent: Double,
  val isMoneyLocked: Boolean,
  val isDecoy: Boolean,
  val status: DepositStatus
)

@Entity(tableName = "transfers")
data class TransferEntity(
  @PrimaryKey val id: String,
  val sourceAccountId: String,
  val recipientName: String,
  val recipientIban: String,
  val isNewRecipient: Boolean,
  val amountTenge: Long,
  val transferType: TransferType,
  val codeType: TransferCodeType,
  val status: TransferStatus,
  val reviewDeadlineMillis: Long?,
  val coolingOffUntilMillis: Long?,
  val riskScore: Int,
  val riskFactorsSummary: String,
  val createdAtMillis: Long,
  val isDuressOperation: Boolean
)

@Entity(tableName = "second_hand_requests")
data class SecondHandRequestEntity(
  @PrimaryKey val id: String,
  val transferId: String,
  val depositId: String,
  val depositTitle: String,
  val initiatorName: String,
  val recipientName: String,
  val recipientIban: String,
  val amountTenge: Long,
  val riskScore: Int,
  val riskFactorsJson: String,
  val status: SecondHandStatus,
  val createdAtMillis: Long,
  val unlockAfterMillis: Long,
  val expiresAtMillis: Long,
  val assignedContactName: String,
  val assignedContactPhone: String,
  val isBackupAssigned: Boolean,
  val comment: String
)

@Entity(tableName = "nbrk_incidents")
data class NbrkIncidentEntity(
  @PrimaryKey val id: String,
  val incidentCode: String,
  val triggerType: NbrkTrigger,
  val recipientIban: String,
  val amountTenge: Long,
  val riskScore: Int,
  val riskBreakdownJson: String,
  val requestJson: String,
  val responseJson: String,
  val latencyMs: Long,
  val httpCode: Int,
  val createdAtMillis: Long
)

@Entity(tableName = "security_config")
data class SecurityConfigEntity(
  @PrimaryKey val id: Int = 1,
  val standardPinHash: String,
  val duressPinHash: String,
  val standardCodeHash: String,
  val duressCodeHash: String,
  val secondHandPinHash: String,
  val thresholdTenge: Long,
  val isolatedSafeTargetTenge: Long,
  val coolingOffMinutes: Int,
  val escrowBusinessDays: Int,
  val primaryTrustedName: String,
  val primaryTrustedPhone: String,
  val backupTrustedName: String,
  val backupTrustedPhone: String,
  val failedPinAttempts: Int,
  val lockedUntilMillis: Long,
  val lastSecurityChangeMillis: Long,
  val seed: Long
)

@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
  @PrimaryKey val id: String,
  val transactionGroupId: String,
  val accountOrDepositId: String,
  val entrySide: String, // "DEBIT" or "CREDIT"
  val amountTenge: Long,
  val description: String,
  val createdAtMillis: Long
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
  @PrimaryKey val id: String,
  val actorRole: UserRole,
  val action: String,
  val details: String,
  val createdAtMillis: Long
)

@Entity(tableName = "known_recipients")
data class KnownRecipientEntity(
  @PrimaryKey val id: String,
  val name: String,
  val iban: String,
  val bankName: String,
  val isDropperFlag: Boolean
)
