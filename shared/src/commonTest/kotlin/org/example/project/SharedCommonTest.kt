package org.example.project

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun installmentBecomesOverdueAndAssessesFlatPenaltyTheDayAfterDue() {
        val loan = testLoan()

        val dueDate = installmentStatuses(loan, emptyList(), "2026-10-01", PenaltyRule(PenaltyMode.Flat, 10.0)).single()
        val dayAfterDue = installmentStatuses(loan, emptyList(), "2026-10-02", PenaltyRule(PenaltyMode.Flat, 10.0)).single()

        assertEquals(false, dueDate.isOverdue)
        assertEquals(0.0, dueDate.penalty)
        assertEquals(true, dayAfterDue.isOverdue)
        assertEquals(1, dayAfterDue.daysOverdue)
        assertEquals(10.0, dayAfterDue.penalty)
    }

    @Test
    fun percentagePenaltyUsesUnpaidInstallmentBalanceAtTriggerDate() {
        val loan = testLoan()
        val partialPayment = Payment(1, loan.id, 60.0, "2026-10-01", "Cash", "P1", allocatedPrincipal = 60.0)

        val status = installmentStatuses(loan, listOf(partialPayment), "2026-10-02", PenaltyRule(PenaltyMode.Percentage, 5.0)).single()

        assertEquals(61.2, status.unpaid)
        assertEquals(3.06, status.penalty)
    }

    @Test
    fun databaseFieldEncodingPreservesSeparatorsAndUnicode() {
        val fields = listOf("A|B;C%", "Quezon City · ₱32,000/mo", "", "file.pdf")

        assertEquals(fields, decodeFields(encodeFields(fields)))
    }

    private fun testLoan() = Loan(
        id = 1,
        clientId = 1,
        principal = 120.0,
        annualInterestRate = 12.0,
        term = 1,
        startDate = "2026-09-01",
        frequency = PaymentFrequency.Monthly,
        status = LoanStatus.Active,
    )
}
