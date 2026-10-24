package org.example.project

import kotlin.math.round

internal data class SimpleDate(val year: Int, val month: Int, val day: Int) {
    fun plusDays(count: Int): SimpleDate {
        var result = this
        repeat(count) {
            if (result.day < daysInMonth(result.year, result.month)) result = result.copy(day = result.day + 1)
            else if (result.month < 12) result = SimpleDate(result.year, result.month + 1, 1)
            else result = SimpleDate(result.year + 1, 1, 1)
        }
        return result
    }

    fun plusMonths(count: Int): SimpleDate {
        val absoluteMonth = year * 12 + (month - 1) + count
        val nextYear = absoluteMonth / 12
        val nextMonth = absoluteMonth % 12 + 1
        return SimpleDate(nextYear, nextMonth, day.coerceAtMost(daysInMonth(nextYear, nextMonth)))
    }

    override fun toString(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

internal fun SimpleDate.toUtcMillis(): Long {
    var days = 0L
    if (year >= 1970) {
        for (currentYear in 1970 until year) days += if (daysInMonth(currentYear, 2) == 29) 366 else 365
    } else {
        for (currentYear in year until 1970) days -= if (daysInMonth(currentYear, 2) == 29) 366 else 365
    }
    for (currentMonth in 1 until month) days += daysInMonth(year, currentMonth)
    days += day - 1
    return days * 86_400_000L
}

internal fun millisToSimpleDate(millis: Long): SimpleDate {
    var days = millis / 86_400_000L
    var year = 1970
    while (days < 0) {
        year--
        days += if (daysInMonth(year, 2) == 29) 366 else 365
    }
    while (true) {
        val yearDays = if (daysInMonth(year, 2) == 29) 366 else 365
        if (days < yearDays) break
        days -= yearDays
        year++
    }
    var month = 1
    while (days >= daysInMonth(year, month)) {
        days -= daysInMonth(year, month)
        month++
    }
    return SimpleDate(year, month, days.toInt() + 1)
}

internal fun parseDate(value: String): SimpleDate? {
    val pieces = value.split('-')
    if (pieces.size != 3) return null
    val year = pieces[0].toIntOrNull() ?: return null
    val month = pieces[1].toIntOrNull() ?: return null
    val day = pieces[2].toIntOrNull() ?: return null
    if (year !in 1..9999 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
    return SimpleDate(year, month, day)
}

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

internal fun createSchedule(principal: Double, annualRate: Double, term: Int, startDate: String, frequency: PaymentFrequency): List<Installment> {
    val start = parseDate(startDate) ?: return emptyList()
    if (term <= 0 || principal <= 0 || annualRate < 0) return emptyList()
    var outstanding = roundMoney(principal)
    return (1..term).map { paymentNumber ->
        val dueDate = when (frequency) {
            PaymentFrequency.Weekly -> start.plusDays(7 * paymentNumber)
            PaymentFrequency.SemiMonthly -> start.plusDays(15 * paymentNumber)
            PaymentFrequency.Monthly -> start.plusMonths(paymentNumber)
        }
        val paymentPrincipal = if (paymentNumber == term) outstanding else roundMoney(principal / term).coerceAtMost(outstanding)
        val interest = roundMoney(outstanding * (annualRate / 100.0 / frequency.periodsPerYear))
        outstanding = roundMoney((outstanding - paymentPrincipal).coerceAtLeast(0.0))
        Installment(paymentNumber, dueDate.toString(), paymentPrincipal, interest)
    }
}

internal fun roundMoney(amount: Double): Double = round(amount * 100.0) / 100.0

internal fun money(amount: Double): String {
    val rounded = roundMoney(amount)
    val whole = rounded.toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = (rounded * 100).toLong().rem(100).toString().padStart(2, '0')
    return "₱$whole.$cents"
}

internal fun reallocateLoanPayments(loan: Loan, payments: MutableList<Payment>, defaultPenaltyRule: PenaltyRule) {
    val related = payments.filter { it.loanId == loan.id }.sortedWith(compareBy<Payment> { it.date }.thenBy { it.id })
    var paidPenalty = 0.0
    var paidInterest = 0.0
    var paidPrincipal = 0.0
    val schedule = createSchedule(loan.principal, loan.annualInterestRate, loan.term, loan.startDate, loan.frequency)
    val installmentRemaining = schedule.map { it.total }.toMutableList()
    val installmentPenalty = MutableList(schedule.size) { 0.0 }
    val penaltyAssessed = MutableList(schedule.size) { false }
    val rule = loan.penaltyRule ?: defaultPenaltyRule
    related.forEach { payment ->
        val index = payments.indexOfFirst { it.id == payment.id }
        if (index < 0) return@forEach
        if (payment.voidReason.isNotBlank()) {
            payments[index] = payment.copy(allocatedPenalty = 0.0, allocatedInterest = 0.0, allocatedPrincipal = 0.0, unappliedAmount = 0.0)
        } else {
            schedule.forEachIndexed { installmentIndex, installment ->
                val triggerDate = parseDate(installment.dueDate)?.plusDays(1)?.toString() ?: return@forEachIndexed
                if (!penaltyAssessed[installmentIndex] && triggerDate <= payment.date) {
                    penaltyAssessed[installmentIndex] = true
                    if (installmentRemaining[installmentIndex] > 0.009) {
                        installmentPenalty[installmentIndex] = penaltyAmount(rule, installmentRemaining[installmentIndex])
                    }
                }
            }
            val dueInterest = schedule.filter { it.dueDate <= payment.date }.sumOf { it.interest }
            val penaltyRemaining = (loan.penaltyBalance + installmentPenalty.sum() - paidPenalty).coerceAtLeast(0.0)
            val interestRemaining = (dueInterest - paidInterest).coerceAtLeast(0.0)
            val principalRemaining = (loan.principal - paidPrincipal).coerceAtLeast(0.0)
            var remainder = roundMoney(payment.amount)
            val penaltyApplied = minOf(remainder, penaltyRemaining)
            remainder = roundMoney(remainder - penaltyApplied)
            val interestApplied = minOf(remainder, interestRemaining)
            remainder = roundMoney(remainder - interestApplied)
            val principalApplied = minOf(remainder, principalRemaining)
            remainder = roundMoney(remainder - principalApplied)
            var schedulePaid = interestApplied + principalApplied
            schedule.indices.forEach { installmentIndex ->
                if (schedule[installmentIndex].dueDate <= payment.date && schedulePaid > 0.0) {
                    val applied = minOf(installmentRemaining[installmentIndex], schedulePaid)
                    installmentRemaining[installmentIndex] = roundMoney((installmentRemaining[installmentIndex] - applied).coerceAtLeast(0.0))
                    schedulePaid = roundMoney(schedulePaid - applied)
                }
            }
            payments[index] = payment.copy(
                allocatedPenalty = penaltyApplied,
                allocatedInterest = interestApplied,
                allocatedPrincipal = principalApplied,
                unappliedAmount = remainder,
            )
            paidPenalty += penaltyApplied
            paidInterest += interestApplied
            paidPrincipal += principalApplied
        }
    }
}

internal fun penaltyAmount(rule: PenaltyRule, unpaid: Double): Double = roundMoney(
    when (rule.mode) {
        PenaltyMode.Flat -> rule.rate
        PenaltyMode.Percentage -> unpaid * rule.rate / 100.0
    }.coerceAtLeast(0.0),
)

internal fun installmentStatuses(loan: Loan, payments: List<Payment>, asOfDate: String, defaultPenaltyRule: PenaltyRule): List<InstallmentStatus> {
    val schedule = createSchedule(loan.principal, loan.annualInterestRate, loan.term, loan.startDate, loan.frequency)
    val validPayments = payments.filter { it.loanId == loan.id && it.voidReason.isBlank() && it.date <= asOfDate }
    val rule = loan.penaltyRule ?: defaultPenaltyRule
    val eligibleForOverdue = loan.status == LoanStatus.Released || loan.status == LoanStatus.Active || loan.status == LoanStatus.Overdue
    var installmentPenaltiesPaid = (validPayments.sumOf { it.allocatedPenalty } - loan.penaltyBalance).coerceAtLeast(0.0)
    return schedule.map { installment ->
        val duePayments = validPayments.filter { it.date <= asOfDate }.sumOf { it.allocatedInterest + it.allocatedPrincipal }
        val earlierScheduled = schedule.take(installment.number - 1).sumOf { it.total }
        val paidForThis = (duePayments - earlierScheduled).coerceIn(0.0, installment.total)
        val unpaid = roundMoney((installment.total - paidForThis).coerceAtLeast(0.0))
        val triggerDate = parseDate(installment.dueDate)?.plusDays(1)?.toString()
        val paidBeforePenalty = if (triggerDate == null) 0.0 else validPayments.filter { it.date < triggerDate }.sumOf { it.allocatedInterest + it.allocatedPrincipal }
        val unpaidAtTrigger = (installment.total - (paidBeforePenalty - earlierScheduled).coerceIn(0.0, installment.total)).coerceAtLeast(0.0)
        val penalty = if (eligibleForOverdue && triggerDate != null && triggerDate <= asOfDate && unpaidAtTrigger > 0.009) penaltyAmount(rule, unpaidAtTrigger) else 0.0
        val penaltyPaidHere = minOf(installmentPenaltiesPaid, penalty)
        installmentPenaltiesPaid = roundMoney((installmentPenaltiesPaid - penaltyPaidHere).coerceAtLeast(0.0))
        val daysLate = if (eligibleForOverdue && installment.dueDate < asOfDate && (unpaid > 0.009 || penalty - penaltyPaidHere > 0.009)) daysBetween(installment.dueDate, asOfDate) else 0
        InstallmentStatus(installment, unpaid, penalty, roundMoney(penalty - penaltyPaidHere), daysLate)
    }
}

internal data class OverdueLoanSummary(
    val loan: Loan,
    val clientName: String,
    val pastDue: Double,
    val oldestDueDate: String,
    val daysOverdue: Int,
)

internal fun remainingLoanBalance(loan: Loan, payments: List<Payment>, asOfDate: String, defaultPenaltyRule: PenaltyRule): Double {
    val valid = payments.filter { it.loanId == loan.id && it.voidReason.isBlank() && it.date <= asOfDate }
    val dueInterest = createSchedule(loan.principal, loan.annualInterestRate, loan.term, loan.startDate, loan.frequency)
        .filter { it.dueDate <= asOfDate }
        .sumOf { it.interest }
    val principalLeft = (loan.principal - valid.sumOf { it.allocatedPrincipal }).coerceAtLeast(0.0)
    val interestLeft = (dueInterest - valid.sumOf { it.allocatedInterest }).coerceAtLeast(0.0)
    val assessedPenalty = installmentStatuses(loan, payments, asOfDate, defaultPenaltyRule).sumOf { it.penalty }
    val penaltyLeft = (loan.penaltyBalance + assessedPenalty - valid.sumOf { it.allocatedPenalty }).coerceAtLeast(0.0)
    return roundMoney(principalLeft + interestLeft + penaltyLeft)
}

internal fun loansDueOn(loans: List<Loan>, payments: List<Payment>, start: String, end: String): List<Loan> = loans.filter { loan ->
    val eligible = loan.status == LoanStatus.Released || loan.status == LoanStatus.Active || loan.status == LoanStatus.Overdue
    if (!eligible) return@filter false
    val schedule = createSchedule(loan.principal, loan.annualInterestRate, loan.term, loan.startDate, loan.frequency)
    val inWindow = schedule.filter { it.dueDate in start..end }
    if (inWindow.isEmpty()) return@filter false
    val paid = payments.filter { it.loanId == loan.id && it.voidReason.isBlank() && it.date <= end }
    val principalBefore = schedule.filter { it.dueDate < start }.sumOf { it.principal }
    val interestBefore = schedule.filter { it.dueDate < start }.sumOf { it.interest }
    val principalStillDue = inWindow.sumOf { it.principal } - (paid.sumOf { it.allocatedPrincipal } - principalBefore).coerceAtLeast(0.0)
    val interestStillDue = inWindow.sumOf { it.interest } - (paid.sumOf { it.allocatedInterest } - interestBefore).coerceAtLeast(0.0)
    principalStillDue.coerceAtLeast(0.0) + interestStillDue.coerceAtLeast(0.0) > 0.009
}

internal fun overdueSummary(loan: Loan, clients: List<Client>, payments: List<Payment>, asOfDate: String, defaultPenaltyRule: PenaltyRule): OverdueLoanSummary? {
    val eligible = loan.status == LoanStatus.Released || loan.status == LoanStatus.Active || loan.status == LoanStatus.Overdue
    if (!eligible) return null
    val overdue = installmentStatuses(loan, payments, asOfDate, defaultPenaltyRule).filter { it.isOverdue }
    if (overdue.isEmpty()) return null
    val oldestDue = overdue.minOf { it.installment.dueDate }
    val daysLate = overdue.maxOf { it.daysOverdue }
    val amount = roundMoney(overdue.sumOf { it.unpaid + it.penaltyOutstanding })
    return OverdueLoanSummary(loan, clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client", amount, oldestDue, daysLate)
}

internal fun daysBetween(start: String, end: String): Int {
    val first = parseDate(start) ?: return 0
    val last = parseDate(end) ?: return 0
    fun dayIndex(date: SimpleDate): Int {
        val years = date.year - 1
        var days = years * 365 + years / 4 - years / 100 + years / 400
        for (month in 1 until date.month) days += daysInMonth(date.year, month)
        return days + date.day - 1
    }
    return dayIndex(last) - dayIndex(first)
}

internal fun buildReportLines(
    reportType: String,
    from: String,
    to: String,
    clients: List<Client>,
    loans: List<Loan>,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
): List<String> = when (reportType) {
    "Collection report" -> {
        val rows = payments.filter { it.voidReason.isBlank() && it.date in from..to }.sortedBy { it.date }
        listOf("CLoan - COLLECTION REPORT", "Date range: " + from + " to " + to, "") +
            rows.map { payment ->
                val loan = loans.firstOrNull { it.id == payment.loanId }
                val client = clients.firstOrNull { it.id == loan?.clientId }?.name ?: "Unknown client"
                payment.date + " | " + client + " | Loan #" + payment.loanId + " | " + money(payment.amount) + " | " + payment.method + " | " + payment.reference
            } + listOf("", "Payments: " + rows.size, "Total collected: " + money(rows.sumOf { it.amount }))
    }
    "Loan release report" -> {
        val rows = loans.filter { it.releaseDate in from..to && it.releaseDate.isNotBlank() }.sortedBy { it.releaseDate }
        listOf("CLoan - LOAN RELEASE REPORT", "Date range: " + from + " to " + to, "") +
            rows.map { loan ->
                val client = clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client"
                loan.releaseDate + " | " + client + " | Loan #" + loan.id + " | " + money(loan.releaseAmount ?: 0.0) + " | " + loan.releaseMethod + " | " + loan.releaseReference
            } + listOf("", "Loans released: " + rows.size, "Total released: " + money(rows.sumOf { it.releaseAmount ?: 0.0 }))
    }
    else -> {
        val rows = loans.mapNotNull { overdueSummary(it, clients, payments, to, defaultPenaltyRule) }.sortedByDescending { it.daysOverdue }
        listOf("CLoan - OVERDUE / AGING REPORT", "As of: " + to, "Aging uses the oldest unpaid scheduled installment.", "") +
            rows.map { item ->
                val bucket = when {
                    item.daysOverdue <= 30 -> "1-30 days"
                    item.daysOverdue <= 60 -> "31-60 days"
                    item.daysOverdue <= 90 -> "61-90 days"
                    else -> "90+ days"
                }
                item.clientName + " | Loan #" + item.loan.id + " | Due " + item.oldestDueDate + " | " + item.daysOverdue + " days | " + bucket + " | " + money(item.pastDue)
            } + listOf("", "Overdue loans: " + rows.size, "Total overdue: " + money(rows.sumOf { it.pastDue }))
    }
}
