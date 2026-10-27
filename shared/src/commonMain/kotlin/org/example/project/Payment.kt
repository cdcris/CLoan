package org.example.project

data class Payment(
    val id: Int,
    val loanId: Int,
    val amount: Double,
    val date: String,
    val method: String,
    val reference: String,
    val voidReason: String = "",
    val allocatedPenalty: Double = 0.0,
    val allocatedInterest: Double = 0.0,
    val allocatedPrincipal: Double = 0.0,
    val unappliedAmount: Double = 0.0,
)
