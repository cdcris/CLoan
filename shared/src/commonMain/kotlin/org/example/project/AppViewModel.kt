package org.example.project

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.db.Database

data class AppUiState(
    val clients: List<Client>,
    val loans: List<Loan>,
    val payments: List<Payment>,
    val defaultPenaltyRule: PenaltyRule,
)

class AppViewModel(private val database: Database) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(
            clients = database.readEntities("clients", ::decodeClient),
            loans = database.readEntities("loans", ::decodeLoan),
            payments = database.readEntities("payments", ::decodePayment),
            defaultPenaltyRule = database.readPenaltyRule(),
        ),
    )

    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    val currentState: AppUiState get() = _uiState.value

    fun saveClient(client: Client, existingId: Int? = null) {
        val state = _uiState.value
        val updatedClients = if (existingId == null) {
            state.clients + client.copy(id = (state.clients.maxOfOrNull { it.id } ?: 0) + 1)
        } else {
            state.clients.map { existing ->
                if (existing.id == existingId) {
                    client.copy(id = existing.id, archived = existing.archived)
                } else {
                    existing
                }
            }
        }
        _uiState.value = state.copy(clients = updatedClients)
        database.replaceEntities("clients", updatedClients, Client::id, ::encodeClient)
    }

    fun toggleClientArchived(client: Client) {
        val state = _uiState.value
        val updatedClients = state.clients.map { existing ->
            if (existing.id == client.id) existing.copy(archived = !existing.archived) else existing
        }
        _uiState.value = state.copy(clients = updatedClients)
        database.replaceEntities("clients", updatedClients, Client::id, ::encodeClient)
    }

    fun saveClientForm(
        existingId: Int?,
        name: String,
        birthdate: String,
        contactNumber: String,
        address: String,
        employerIncome: String,
        emergencyContact: String,
        documentsText: String,
        statusLabel: String,
    ) {
        val existing = existingId?.let { id -> _uiState.value.clients.firstOrNull { it.id == id } }
        val status = ClientStatus.entries.firstOrNull { it.label == statusLabel } ?: ClientStatus.Active
        saveClient(
            Client(
                id = existing?.id ?: 0,
                name = name,
                birthdate = birthdate,
                contactNumber = contactNumber,
                address = address,
                employerIncome = employerIncome,
                emergencyContact = emergencyContact,
                status = status,
                documents = documentsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                archived = existing?.archived ?: false,
            ),
            existingId,
        )
    }

    fun saveLoan(loan: Loan, existingId: Int? = null) {
        val state = _uiState.value
        val updatedLoans = if (existingId == null) {
            state.loans + loan.copy(id = (state.loans.maxOfOrNull { it.id } ?: 0) + 1)
        } else {
            state.loans.map { existing ->
                if (existing.id == existingId) loan.copy(id = existing.id, status = existing.status) else existing
            }
        }
        persistLoanChanges(state, updatedLoans)
    }

    fun saveLoanForm(
        existingId: Int?,
        clientId: Int,
        principal: Double,
        annualInterestRate: Double,
        term: Int,
        startDate: String,
        frequencyLabel: String,
        customPenaltyModeLabel: String,
        customPenaltyRate: Double,
    ) {
        val existing = existingId?.let { id -> _uiState.value.loans.firstOrNull { it.id == id } }
        val frequency = PaymentFrequency.entries.firstOrNull { it.label == frequencyLabel } ?: PaymentFrequency.Monthly
        val customRule = PenaltyMode.entries.firstOrNull { it.label == customPenaltyModeLabel }
            ?.let { PenaltyRule(it, customPenaltyRate) }
        saveLoan(
            Loan(
                id = existing?.id ?: 0,
                clientId = clientId,
                principal = principal,
                annualInterestRate = annualInterestRate,
                term = term,
                startDate = startDate,
                frequency = frequency,
                status = existing?.status ?: LoanStatus.Pending,
                releaseDate = existing?.releaseDate.orEmpty(),
                releaseAmount = existing?.releaseAmount,
                releaseMethod = existing?.releaseMethod.orEmpty(),
                releaseReference = existing?.releaseReference.orEmpty(),
                penaltyBalance = existing?.penaltyBalance ?: 0.0,
                penaltyRule = customRule,
            ),
            existingId,
        )
    }

    fun updateLoanStatus(loanId: Int, statusLabel: String) {
        val status = LoanStatus.entries.firstOrNull { it.label == statusLabel } ?: return
        _uiState.value.loans.firstOrNull { it.id == loanId }?.let { updateLoan(it.copy(status = status)) }
    }

    fun updateLoanPenaltyBalance(loanId: Int, balance: Double) {
        _uiState.value.loans.firstOrNull { it.id == loanId }?.let { updateLoan(it.copy(penaltyBalance = balance)) }
    }

    fun updateLoan(loan: Loan) {
        val updatedLoans = _uiState.value.loans.map { existing -> if (existing.id == loan.id) loan else existing }
        persistLoanChanges(_uiState.value, updatedLoans, reallocateLoanId = loan.id)
    }

    fun releaseLoan(loanId: Int, date: String, amount: Double, method: String, reference: String) {
        val updatedLoans = _uiState.value.loans.map { loan ->
            if (loan.id == loanId) {
                loan.copy(
                    status = LoanStatus.Released,
                    releaseDate = date,
                    releaseAmount = amount,
                    releaseMethod = method,
                    releaseReference = reference,
                )
            } else {
                loan
            }
        }
        persistLoanChanges(_uiState.value, updatedLoans)
    }

    fun savePayment(payment: Payment, existingId: Int? = null) {
        val state = _uiState.value
        val updatedPayments = if (existingId == null) {
            state.payments + payment.copy(id = (state.payments.maxOfOrNull { it.id } ?: 0) + 1)
        } else {
            state.payments.map { existing -> if (existing.id == existingId) payment.copy(id = existing.id) else existing }
        }
        persistPaymentChanges(state, updatedPayments, listOfNotNull(existingId?.let { state.payments.firstOrNull { p -> p.id == it }?.loanId }, payment.loanId))
    }

    fun savePaymentForm(existingId: Int?, loanId: Int, amount: Double, date: String, method: String, reference: String) {
        val existing = existingId?.let { id -> _uiState.value.payments.firstOrNull { it.id == id } }
        savePayment(
            Payment(
                id = existing?.id ?: 0,
                loanId = loanId,
                amount = amount,
                date = date,
                method = method,
                reference = reference,
                voidReason = existing?.voidReason.orEmpty(),
                allocatedPenalty = existing?.allocatedPenalty ?: 0.0,
                allocatedInterest = existing?.allocatedInterest ?: 0.0,
                allocatedPrincipal = existing?.allocatedPrincipal ?: 0.0,
                unappliedAmount = existing?.unappliedAmount ?: 0.0,
            ),
            existingId,
        )
    }

    fun voidPayment(payment: Payment, reason: String) {
        val state = _uiState.value
        val updatedPayments = state.payments.map { existing ->
            if (existing.id == payment.id) existing.copy(voidReason = reason) else existing
        }
        persistPaymentChanges(state, updatedPayments, listOf(payment.loanId))
    }

    fun updateDefaultPenaltyRule(rule: PenaltyRule) {
        val state = _uiState.value
        val updatedPayments = state.payments.toMutableList()
        state.loans.forEach { loan -> reallocateLoanPayments(loan, updatedPayments, rule) }
        val payments = updatedPayments.toList()
        _uiState.value = state.copy(defaultPenaltyRule = rule, payments = payments)
        database.savePenaltyRule(rule)
        database.replaceEntities("payments", payments, Payment::id, ::encodePayment)
    }

    fun updateDefaultPenaltyRuleForm(modeLabel: String, rate: Double) {
        val mode = PenaltyMode.entries.firstOrNull { it.label == modeLabel } ?: return
        updateDefaultPenaltyRule(PenaltyRule(mode, rate))
    }

    fun loanInstallments(loanId: Int): List<InstallmentStatus> {
        val state = _uiState.value
        val loan = state.loans.firstOrNull { it.id == loanId } ?: return emptyList()
        return installmentStatuses(loan, state.payments, currentDateIso(), state.defaultPenaltyRule)
    }

    fun remainingLoanBalance(loanId: Int): Double {
        val state = _uiState.value
        val loan = state.loans.firstOrNull { it.id == loanId } ?: return 0.0
        return remainingLoanBalance(loan, state.payments, currentDateIso(), state.defaultPenaltyRule)
    }

    fun generateReportPdf(reportType: String, from: String, to: String): String {
        val isOverdueReport = reportType == "Overdue / aging report"
        if (parseDate(to) == null || (!isOverdueReport && (parseDate(from) == null || from > to))) {
            return "Enter valid dates in YYYY-MM-DD order."
        }
        val state = _uiState.value
        val reportLines = buildReportLines(reportType, from, to, state.clients, state.loans, state.payments, state.defaultPenaltyRule)
        val key = reportType.lowercase().replace(" ", "-").replace("/", "")
        return saveOfficialReceiptPdf("$key-$from-$to.pdf", buildPdfDocument(reportLines))
    }

    fun generatePaymentReceiptPdf(paymentId: Int): String {
        val state = _uiState.value
        val payment = state.payments.firstOrNull { it.id == paymentId } ?: return "Payment not found."
        val loan = state.loans.firstOrNull { it.id == payment.loanId }
        val clientName = state.clients.firstOrNull { it.id == loan?.clientId }?.name ?: "Unknown client"
        val receiptNumber = "RC-${payment.id.toString().padStart(6, '0')}"
        val lines = listOf(
            "CLoan - OFFICIAL PAYMENT RECEIPT",
            "Receipt no: $receiptNumber",
            "Received from: $clientName",
            "Amount received: ${money(payment.amount)}",
            "Payment date: ${payment.date}",
            "Payment method: ${payment.method}",
            "Reference no: ${payment.reference}",
            "Applied to penalties: ${money(payment.allocatedPenalty)}",
            "Applied to interest: ${money(payment.allocatedInterest)}",
            "Applied to principal: ${money(payment.allocatedPrincipal)}",
        )
        return saveOfficialReceiptPdf("$receiptNumber.pdf", buildPdfDocument(lines))
    }

    private fun persistLoanChanges(
        previousState: AppUiState,
        updatedLoans: List<Loan>,
        reallocateLoanId: Int? = null,
    ) {
        val updatedPayments = previousState.payments.toMutableList()
        if (reallocateLoanId != null) {
            updatedLoans.firstOrNull { it.id == reallocateLoanId }?.let { loan ->
                reallocateLoanPayments(loan, updatedPayments, previousState.defaultPenaltyRule)
            }
        }
        val payments = updatedPayments.toList()
        _uiState.value = previousState.copy(loans = updatedLoans, payments = payments)
        database.replaceEntities("loans", updatedLoans, Loan::id, ::encodeLoan)
        database.replaceEntities("payments", payments, Payment::id, ::encodePayment)
    }

    private fun persistPaymentChanges(previousState: AppUiState, changedPayments: List<Payment>, affectedLoanIds: List<Int>) {
        val updatedPayments = changedPayments.toMutableList()
        affectedLoanIds.distinct().forEach { loanId ->
            previousState.loans.firstOrNull { it.id == loanId }?.let { loan ->
                reallocateLoanPayments(loan, updatedPayments, previousState.defaultPenaltyRule)
            }
        }
        val payments = updatedPayments.toList()
        _uiState.value = previousState.copy(payments = payments)
        database.replaceEntities("payments", payments, Payment::id, ::encodePayment)
    }
}
