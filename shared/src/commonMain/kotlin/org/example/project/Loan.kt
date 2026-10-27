package org.example.project

enum class PaymentFrequency(val label: String, val periodsPerYear: Int) {
    Weekly("Weekly", 52),
    SemiMonthly("Semi-monthly", 24),
    Monthly("Monthly", 12),
}

enum class LoanStatus(val label: String) {
    Pending("Pending"),
    Approved("Approved"),
    Released("Released"),
    Active("Active"),
    FullyPaid("Fully Paid"),
    Overdue("Overdue"),
    Cancelled("Cancelled"),
}

enum class PenaltyMode(val label: String) {
    Flat("Flat amount per overdue installment"),
    Percentage("Percentage of unpaid installment"),
}

data class PenaltyRule(val mode: PenaltyMode, val rate: Double)

data class Loan(
    val id: Int,
    val clientId: Int,
    val principal: Double,
    val annualInterestRate: Double,
    val term: Int,
    val startDate: String,
    val frequency: PaymentFrequency,
    val status: LoanStatus = LoanStatus.Pending,
    val releaseDate: String = "",
    val releaseAmount: Double? = null,
    val releaseMethod: String = "",
    val releaseReference: String = "",
    val penaltyBalance: Double = 0.0,
    val penaltyRule: PenaltyRule? = null,
)

data class Installment(val number: Int, val dueDate: String, val principal: Double, val interest: Double) {
    val total: Double get() = principal + interest
}

data class InstallmentStatus(
    val installment: Installment,
    val unpaid: Double,
    val penalty: Double,
    val penaltyOutstanding: Double,
    val daysOverdue: Int,
) {
    val isOverdue: Boolean get() = daysOverdue > 0 && (unpaid > 0.009 || penaltyOutstanding > 0.009)
}
