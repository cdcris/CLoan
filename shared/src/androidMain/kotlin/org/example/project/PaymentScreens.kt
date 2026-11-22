package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.project.shared.R

@Composable
internal fun PaymentManagement(
    clients: List<Client>,
    loans: List<Loan>,
    payments: List<Payment>,
    onSavePayment: (Payment, Int?) -> Unit,
    onVoidPayment: (Payment, String) -> Unit,
    onHardDeletePayment: (Int) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var voidingPayment by remember { mutableStateOf<Payment?>(null) }
    var receiptPayment by remember { mutableStateOf<Payment?>(null) }
    var filter by remember { mutableStateOf("All loans and clients") }
    var filterMenu by remember { mutableStateOf(false) }
    var deletingPayment by remember { mutableStateOf<Payment?>(null) }
    val filterOptions = listOf("All loans and clients") + clients.map { client ->
        "Client #${client.id}: ${client.name}"
    } + loans.map { loan ->
        val clientName = clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client"
        "${clientName} · Loan #${loan.id}"
    }
    val filteredPayments = payments.filter { payment ->
        val loan = loans.firstOrNull { it.id == payment.loanId }
        filter == "All loans and clients" || (loan != null && run {
            val client = clients.firstOrNull { it.id == loan.clientId }
            val name = client?.name ?: "Unknown client"
            filter == "Client #${client?.id}: $name" || filter == "$name · Loan #${loan.id}"
        })
    }.sortedByDescending { it.date }

    Column(modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_payments), style = MaterialTheme.typography.headlineMedium)
            }
            LedgerButton(onClick = { editingPayment = null; showCreate = true }) { Text("+ Record") }
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box {
            MenuSelector("History: $filter", onClick = { filterMenu = true }, modifier = Modifier.fillMaxWidth())
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                filterOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { filter = option; filterMenu = false }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${filteredPayments.size} ${if (filteredPayments.size == 1) "payment" else "payments"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (filteredPayments.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LedgerEmptyIcon(R.drawable.ic_payments)
                    Text("No payments found", style = MaterialTheme.typography.titleMedium)
                    Text("Choose another history filter or record a payment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredPayments, key = { it.id }) { payment ->
                    val loan = loans.firstOrNull { it.id == payment.loanId }
                    val clientName = clients.firstOrNull { it.id == loan?.clientId }?.name ?: "Unknown client"
                    PaymentCard(
                        payment = payment,
                        clientName = clientName,
                        onReceipt = { receiptPayment = payment },
                        onEdit = { editingPayment = payment; showCreate = true },
                        onVoid = { voidingPayment = payment },
                        onHardDelete = { deletingPayment = payment },
                    )
                }
            }
        }
    }

    if (showCreate || editingPayment != null) {
        PaymentEditorDialog(
            loans = loans,
            payment = editingPayment,
            onDismiss = { showCreate = false; editingPayment = null },
            onSave = { saved ->
                onSavePayment(saved, editingPayment?.id)
                showCreate = false
                editingPayment = null
            },
        )
    }

    voidingPayment?.let { payment ->
        VoidPaymentDialog(
            payment = payment,
            onDismiss = { voidingPayment = null },
            onVoid = { reason ->
                onVoidPayment(payment, reason)
                voidingPayment = null
            },
        )
    }

    receiptPayment?.let { payment ->
        val loan = loans.firstOrNull { it.id == payment.loanId }
        val clientName = clients.firstOrNull { it.id == loan?.clientId }?.name ?: "Unknown client"
        ReceiptDialog(
            payment = payment,
            clientName = clientName,
            onDismiss = { receiptPayment = null },
        )
    }

    deletingPayment?.let { payment ->
        HardDeleteConfirmationDialog(
            title = "Permanently delete payment?",
            description = "This removes the payment from the record and recalculates the loan balance.",
            onDismiss = { deletingPayment = null },
            onConfirm = { onHardDeletePayment(payment.id); deletingPayment = null },
        )
    }
}

@Composable
internal fun PaymentCard(
    payment: Payment,
    clientName: String,
    onReceipt: () -> Unit,
    onEdit: () -> Unit,
    onVoid: () -> Unit,
    onHardDelete: () -> Unit,
) {
    SwipeReveal(
        key = payment.id,
        actionsWidth = if (payment.voidReason.isBlank()) 288.dp else 216.dp,
        actions = {
            TextButton(onClick = onReceipt) { Text("Receipt") }
            TextButton(onClick = onEdit) { Text("Edit") }
            if (payment.voidReason.isBlank()) TextButton(onClick = onVoid) { Text("Void") }
            TextButton(onClick = onHardDelete) { Text("Delete") }
        },
    ) {
      Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Loan #${payment.loanId} · ${payment.date} · ${payment.method}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (payment.voidReason.isNotBlank()) {
                    Text("VOID", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                } else {
                    Text(money(payment.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text("Ref ${payment.reference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (payment.voidReason.isNotBlank()) {
                Text("Void reason: ${payment.voidReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Applied · Penalty ${money(payment.allocatedPenalty)}  |  Interest ${money(payment.allocatedInterest)}  |  Principal ${money(payment.allocatedPrincipal)}", style = MaterialTheme.typography.labelSmall)
                if (payment.unappliedAmount > 0) Text("Unapplied: ${money(payment.unappliedAmount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
      }
    }
}

@Composable
internal fun PaymentEditorDialog(loans: List<Loan>, payment: Payment?, onDismiss: () -> Unit, onSave: (Payment) -> Unit) {
    val eligibleLoans = loans.filter { it.status != LoanStatus.Cancelled && it.status != LoanStatus.FullyPaid }
    var loanId by remember(payment) { mutableStateOf(payment?.loanId ?: eligibleLoans.firstOrNull()?.id ?: 0) }
    var amount by remember(payment) { mutableStateOf(payment?.amount?.toString().orEmpty()) }
    var date by remember(payment) { mutableStateOf(payment?.date ?: "2026-10-25") }
    var method by remember(payment) { mutableStateOf(payment?.method ?: "Cash") }
    var loanMenu by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = eligibleLoans.any { it.id == loanId } && parsedAmount != null && parsedAmount > 0 && parseDate(date) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (payment == null) "Record payment" else "Edit payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    MenuSelector("${eligibleLoans.firstOrNull { it.id == loanId }?.let { "Loan #${it.id}" } ?: "Select loan"}", onClick = { loanMenu = true }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = loanMenu, onDismissRequest = { loanMenu = false }) {
                        eligibleLoans.forEach { option -> DropdownMenuItem(text = { Text("Loan #${option.id} · ${money(option.principal)}") }, onClick = { loanId = option.id; loanMenu = false }) }
                    }
                }
                Text("Loan *", style = MaterialTheme.typography.labelMedium)
                EditorField("Amount *", amount) { amount = it }
                DatePickerField("Payment date *", date) { date = it }
                androidx.compose.foundation.layout.Box {
                    MenuSelector(method, onClick = { methodMenu = true })
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        listOf("Cash", "Bank transfer", "Mobile wallet", "Check").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { method = option; methodMenu = false }) }
                    }
                }
                Text(if (payment == null) "A reference number will be generated automatically." else "Reference: ${payment.reference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("You can pay before the due date. Early payments reduce the outstanding principal immediately; payments then cover overdue penalties, due interest, and principal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { LedgerButton(onClick = { if (valid) onSave(Payment(payment?.id ?: 0, loanId, parsedAmount, date, method, "")) }, enabled = valid) { Text("Save payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun VoidPaymentDialog(payment: Payment, onDismiss: () -> Unit, onVoid: (String) -> Unit) {
    var reason by remember(payment) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payment ${money(payment.amount)} · Ref ${payment.reference}")
                OutlinedTextField(colors = ledgerFieldColors(), value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reason for voiding *") })
            }
        },
        confirmButton = { LedgerButton(onClick = { if (reason.isNotBlank()) onVoid(reason.trim()) }, enabled = reason.isNotBlank()) { Text("Void payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep payment") } },
    )
}

@Composable
internal fun ReceiptDialog(payment: Payment, clientName: String, onDismiss: () -> Unit) {
    var result by remember(payment.id) { mutableStateOf("") }
    val loanAmount = money(payment.amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Official receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().background(LedgerInk, MaterialTheme.shapes.medium).padding(16.dp)) {
                    Text(stringResource(R.string.amount_received), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text(loanAmount, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                Text("Receipt no. RC-${payment.id.toString().padStart(6, '0')}")
                Text("Received from: $clientName")
                Text("Date: ${payment.date} · Method: ${payment.method}")
                Text("Reference: ${payment.reference}")
                Text("Applied: Penalties ${money(payment.allocatedPenalty)}, Interest ${money(payment.allocatedInterest)}, Principal ${money(payment.allocatedPrincipal)}")
                if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            LedgerButton(onClick = {
                val lines = listOf(
                    "CLoan - OFFICIAL PAYMENT RECEIPT",
                    "Receipt no: RC-${payment.id.toString().padStart(6, '0')}",
                    "Received from: $clientName",
                    "Amount received: $loanAmount",
                    "Payment date: ${payment.date}",
                    "Payment method: ${payment.method}",
                    "Reference no: ${payment.reference}",
                    "Applied to penalties: ${money(payment.allocatedPenalty)}",
                    "Applied to interest: ${money(payment.allocatedInterest)}",
                    "Applied to principal: ${money(payment.allocatedPrincipal)}",
                )
                result = saveOfficialReceiptPdf("RC-${payment.id.toString().padStart(6, '0')}.pdf", buildPdfDocument(lines))
            }) { Text("Generate PDF") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
