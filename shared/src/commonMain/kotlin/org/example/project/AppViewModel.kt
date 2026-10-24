package org.example.project

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.db.Database

internal data class AppUiState(
    val clients: List<Client>,
    val loans: List<Loan>,
    val payments: List<Payment>,
    val defaultPenaltyRule: PenaltyRule,
)

internal class AppViewModel(private val database: Database) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(
            clients = database.readEntities("clients", ::decodeClient),
            loans = database.readEntities("loans", ::decodeLoan),
            payments = database.readEntities("payments", ::decodePayment),
            defaultPenaltyRule = database.readPenaltyRule(),
        ),
    )

    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

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
