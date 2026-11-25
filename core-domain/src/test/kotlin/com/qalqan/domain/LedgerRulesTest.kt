package com.qalqan.domain

import com.qalqan.domain.ledger.LedgerPosting
import com.qalqan.domain.ledger.LedgerRules
import com.qalqan.domain.ledger.LedgerSide
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerRulesTest {

  @Test
  fun `balanced debit and credit group passes validation`() {
    val valid = LedgerRules.validateBalancedGroup(
      listOf(
        LedgerPosting("acc_main", LedgerSide.DEBIT, 100_000L),
        LedgerPosting("acc_escrow_hold", LedgerSide.CREDIT, 100_000L)
      )
    )
    assertTrue(valid)
  }

  @Test
  fun `unbalanced or zero-amount posting fails validation`() {
    assertFalse(
      LedgerRules.validateBalancedGroup(
        listOf(
          LedgerPosting("acc_main", LedgerSide.DEBIT, 100_000L),
          LedgerPosting("acc_escrow_hold", LedgerSide.CREDIT, 99_999L)
        )
      )
    )
    assertFalse(
      LedgerRules.validateBalancedGroup(
        listOf(
          LedgerPosting("acc_main", LedgerSide.DEBIT, 0L),
          LedgerPosting("acc_escrow_hold", LedgerSide.CREDIT, 0L)
        )
      )
    )
  }
}
