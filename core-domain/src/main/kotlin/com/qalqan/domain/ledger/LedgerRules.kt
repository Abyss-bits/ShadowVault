package com.qalqan.domain.ledger

enum class LedgerSide {
  DEBIT,
  CREDIT
}

data class LedgerPosting(
  val accountOrDepositId: String,
  val side: LedgerSide,
  val amountTenge: Long
)

object LedgerRules {
  const val SYSTEM_CAPITAL_ID = "SYSTEM_CAPITAL"
  const val EXTERNAL_SETTLEMENT_ID = "EXTERNAL_SETTLEMENT"

  fun validateBalancedGroup(postings: List<LedgerPosting>): Boolean {
    if (postings.size < 2) return false
    if (postings.any { it.amountTenge <= 0L }) return false
    val totalDebit = postings.filter { it.side == LedgerSide.DEBIT }.sumOf { it.amountTenge }
    val totalCredit = postings.filter { it.side == LedgerSide.CREDIT }.sumOf { it.amountTenge }
    return totalDebit > 0L && totalDebit == totalCredit
  }
}
