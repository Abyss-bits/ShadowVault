package com.qalqan.domain.model

enum class SessionMode {
  LOCKED,
  STANDARD,
  DURESS
}

enum class UserRole {
  CLIENT_STANDARD,
  CLIENT_DURESS,
  SECOND_HAND,
  NBRK_INSPECTOR
}

enum class AccountType {
  MAIN,
  ISOLATED_SAFE,
  ESCROW_HOLD
}

enum class DepositStatus {
  ACTIVE,
  PENDING_EARLY_CLOSURE,
  CLOSED
}

enum class TransferType {
  REGULAR,
  EARLY_DEPOSIT_PAYOUT,
  MERCHANT_PURCHASE
}

enum class TransferCodeType {
  STANDARD,
  DURESS,
  NONE
}

enum class TransferStatus {
  COMPLETED,
  COOLING_OFF,
  SOFT_ESCROW_HOLD,
  AWAITING_SECOND_HAND,
  BLOCKED_FRAUD
}

enum class SecondHandStatus {
  PENDING,
  APPROVED,
  REJECTED_FRAUD,
  EXPIRED
}

enum class NbrkTrigger {
  DURESS_LOGIN_SILENT,
  DURESS_TRANSFER,
  DURESS_TRANSFER_ESCROW,
  SECOND_HAND_REJECTED,
  HIGH_RISK_NEW_RECIPIENT
}

data class Account(
  val id: String,
  val title: String,
  val iban: String,
  val type: AccountType,
  val balanceTenge: Long
)

data class Deposit(
  val id: String,
  val title: String,
  val balanceTenge: Long,
  val annualRatePercent: Double,
  val isMoneyLocked: Boolean,
  val isDecoy: Boolean,
  val status: DepositStatus
)

data class TransferRecord(
  val id: String,
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

data class SecuritySummary(
  val thresholdTenge: Long,
  val isolatedSafeTargetTenge: Long,
  val coolingOffMinutes: Int,
  val escrowBusinessDays: Int,
  val primaryTrustedName: String,
  val primaryTrustedPhone: String,
  val backupTrustedName: String,
  val backupTrustedPhone: String,
  val failedPinAttempts: Int,
  val lockedUntilMillis: Long
)

sealed interface PinAuthResult {
  data class Success(val mode: SessionMode) : PinAuthResult
  data class Invalid(val remainingAttempts: Int) : PinAuthResult
  data class LockedOut(val lockedUntilMillis: Long) : PinAuthResult
}
