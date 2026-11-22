package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.example.project.shared.R

@Composable
internal fun LoanManagement(
    clients: List<Client>,
    loans: List<Loan>,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onSaveLoan: (Loan, Int?) -> Unit,
    onUpdateLoan: (Loan) -> Unit,
    onReleaseLoan: (Int, String, Double, String) -> Unit,
    onRenewOrTopUpLoan: (Int, Double, String) -> Unit,
    onHardDeleteLoan: (Int) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }
    var releasingLoan by remember { mutableStateOf<Loan?>(null) }
    var deletingLoan by remember { mutableStateOf<Loan?>(null) }
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_loans), style = MaterialTheme.typography.headlineMedium)
            }
            LedgerButton(onClick = { editingLoan = null; showCreate = true }) { Text(stringResource(R.string.new_loan)) }
        }
        Spacer(Modifier.height(16.dp))
        Text("${loans.size} ${if (loans.size == 1) "loan" else "loans"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (loans.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LedgerEmptyIcon(R.drawable.ic_loans)
                    Text("No loans yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create a loan to see its repayment schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(loans, key = { it.id }) { loan ->
                    LoanCard(
                        loan = loan,
                        clientName = clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client",
                        payments = payments,
                        defaultPenaltyRule = defaultPenaltyRule,
                        onOpen = { selectedLoan = loan },
                        onHardDelete = { deletingLoan = loan },
                    )
                }
            }
        }
    }

    if (showCreate || editingLoan != null) {
        LoanEditorDialog(
            clients = clients,
            loan = editingLoan,
            defaultPenaltyRule = defaultPenaltyRule,
            onDismiss = { showCreate = false; editingLoan = null },
            onSave = { saved ->
                onSaveLoan(saved, editingLoan?.id)
                showCreate = false
                editingLoan = null
            },
        )
    }

    selectedLoan?.let { loan ->
        val currentLoan = loans.firstOrNull { it.id == loan.id } ?: loan
        LoanDetailsDialog(
            loan = currentLoan,
            clientName = clients.firstOrNull { it.id == currentLoan.clientId }?.name ?: "Unknown client",
            payments = payments,
            defaultPenaltyRule = defaultPenaltyRule,
            onDismiss = { selectedLoan = null },
            onUpdate = onUpdateLoan,
            onEdit = { editingLoan = currentLoan; selectedLoan = null },
            onRelease = { releasingLoan = currentLoan; selectedLoan = null },
            onRenewOrTopUp = { sourceId, cashAdvance, date ->
                onRenewOrTopUpLoan(sourceId, cashAdvance, date)
                selectedLoan = null
            },
        )
    }

    releasingLoan?.let { loan ->
        ReleaseFundsDialog(
            loan = loan,
            onDismiss = { releasingLoan = null },
            onSave = { date, amount, method ->
                onReleaseLoan(loan.id, date, amount, method)
                releasingLoan = null
            },
        )
    }

    deletingLoan?.let { loan ->
        HardDeleteConfirmationDialog(
            title = "Permanently delete loan #${loan.id}?",
            description = "This permanently deletes the loan and all its payment records.",
            onDismiss = { deletingLoan = null },
            onConfirm = { onHardDeleteLoan(loan.id); deletingLoan = null },
        )
    }
}

@Composable
internal fun LoanCard(loan: Loan, clientName: String, payments: List<Payment>, defaultPenaltyRule: PenaltyRule, onOpen: () -> Unit, onHardDelete: () -> Unit) {
    val autoOverdue = installmentStatuses(loan, payments, currentDateIso(), defaultPenaltyRule).any { it.isOverdue }
    SwipeReveal(
        key = loan.id,
        actionsWidth = 176.dp,
        actions = {
            TextButton(onClick = onOpen) { Text("Details") }
            TextButton(onClick = onHardDelete) { Text("Delete") }
        },
    ) {
      Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${loan.loanType.duration.label} · ${formatPercentage(loan.loanType.interestRate)}% interest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LoanStatusBadge(if (autoOverdue) LoanStatus.Overdue else loan.status)
            }
            Spacer(Modifier.height(10.dp))
            Text("Starts ${loan.startDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().background(LedgerInk).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.principal), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(money(loan.principal), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
      }
    }
}

@Composable
internal fun HardDeleteConfirmationDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(description)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Permanently delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun LoanStatusBadge(status: LoanStatus) {
    val colors = when (status) {
        LoanStatus.Pending -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        LoanStatus.Approved -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LoanStatus.Released -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LoanStatus.Active -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LoanStatus.FullyPaid -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LoanStatus.Overdue -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LoanStatus.Cancelled -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(status.label, modifier = Modifier.background(colors.first, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), color = colors.second, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
}

@Composable
internal fun LoanEditorDialog(clients: List<Client>, loan: Loan?, defaultPenaltyRule: PenaltyRule, onDismiss: () -> Unit, onSave: (Loan) -> Unit) {
    var clientId by remember(loan) { mutableStateOf(loan?.clientId ?: clients.firstOrNull()?.id ?: 0) }
    var principal by remember(loan) { mutableStateOf(loan?.principal?.toString().orEmpty()) }
    var rate by remember(loan) { mutableStateOf(loan?.loanType?.interestRate?.toString().orEmpty()) }
    var duration by remember(loan) { mutableStateOf(loan?.loanType?.duration ?: LoanDuration.Monthly) }
    var customDays by remember(loan) { mutableStateOf((loan?.loanType?.customDurationDays ?: 30).toString()) }
    var startDate by remember(loan) { mutableStateOf(loan?.startDate ?: "2026-10-25") }
    var penaltyMode by remember(loan) { mutableStateOf(loan?.loanType?.penaltyRule?.mode ?: defaultPenaltyRule.mode) }
    var penaltyRate by remember(loan) { mutableStateOf((loan?.loanType?.penaltyRule?.rate ?: defaultPenaltyRule.rate).toString()) }
    var penaltyMenu by remember { mutableStateOf(false) }
    var clientMenu by remember { mutableStateOf(false) }
    var durationMenu by remember { mutableStateOf(false) }
    val computedPrincipal = principal.toDoubleOrNull()
    val computedRate = rate.toDoubleOrNull()
    val computedDays = customDays.toIntOrNull()
    val loanType = if (computedRate != null && penaltyRate.toDoubleOrNull() != null) LoanType(duration, roundMoney(computedRate), PenaltyRule(penaltyMode, penaltyRate.toDouble()), computedDays ?: 30) else null
    val preview = if (computedPrincipal != null && loanType != null && computedPrincipal > 0 && computedRate?.let { it >= 0 } == true && (duration != LoanDuration.Custom || computedDays != null && computedDays > 0)) {
        createSchedule(computedPrincipal, loanType, startDate)
    } else emptyList()
    val valid = clients.any { it.id == clientId } && computedPrincipal != null && computedPrincipal > 0 && computedRate != null && computedRate >= 0 && (duration != LoanDuration.Custom || computedDays != null && computedDays > 0) && penaltyRate.toDoubleOrNull()?.let { it >= 0 } == true && parseDate(startDate) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (loan == null) "Create loan" else "Edit pending loan") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Loan details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Box {
                    MenuSelector("${clients.firstOrNull { it.id == clientId }?.name ?: "Select client"}", onClick = { clientMenu = true }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = clientMenu, onDismissRequest = { clientMenu = false }) {
                        clients.forEach { client -> DropdownMenuItem(text = { Text(client.name) }, onClick = { clientId = client.id; clientMenu = false }) }
                    }
                }
                Text("Client *", style = MaterialTheme.typography.labelMedium)
                EditorField("Principal amount *", principal) { principal = it }
                androidx.compose.foundation.layout.Box {
                    MenuSelector("Loan type: ${duration.label}", onClick = { durationMenu = true }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                        LoanDuration.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { duration = option; durationMenu = false }) }
                    }
                }
                if (duration == LoanDuration.Custom) EditorField("Custom duration (days) *", customDays) { customDays = it }
                EditorField("Interest (%) for this loan type *", rate) { rate = it }
                DatePickerField("Start date *", startDate) { startDate = it }
                Text("Penalty for this loan type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                run {
                    androidx.compose.foundation.layout.Box {
                        MenuSelector(penaltyMode.label, onClick = { penaltyMenu = true }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = penaltyMenu, onDismissRequest = { penaltyMenu = false }) {
                            PenaltyMode.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { penaltyMode = option; penaltyMenu = false }) }
                        }
                    }
                    EditorField(if (penaltyMode == PenaltyMode.Flat) "Penalty amount per overdue payment *" else "Penalty percentage (%) *", penaltyRate) { penaltyRate = it }
                }
                if (preview.isNotEmpty()) {
                    val total = preview.sumOf { it.total }
                    Text("Schedule preview · ${preview.size} payment · ${money(total)} total", style = MaterialTheme.typography.labelLarge)
                    Text("Interest is calculated as a percentage of principal for the selected loan type.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { LedgerButton(onClick = { if (valid) onSave(Loan(loan?.id ?: 0, clientId, computedPrincipal, loanType!!, startDate, loan?.status ?: LoanStatus.Pending, penaltyBalance = loan?.penaltyBalance ?: 0.0)) }, enabled = valid) { Text("Save loan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun LoanDetailsDialog(
    loan: Loan,
    clientName: String,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onDismiss: () -> Unit,
    onUpdate: (Loan) -> Unit,
    onEdit: () -> Unit,
    onRelease: () -> Unit,
    onRenewOrTopUp: (Int, Double, String) -> Unit,
) {
    val schedule = installmentStatuses(loan, payments, currentDateIso(), defaultPenaltyRule)
    var showPenaltyEditor by remember(loan.id) { mutableStateOf(false) }
    var showRenewalDialog by remember(loan.id) { mutableStateOf(false) }
    var showSchedule by rememberSaveable(loan.id) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart),
                        shape = MaterialTheme.shapes.small) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.close))
                    }
                    Text(stringResource(R.string.loan_details), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                        .padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.5f)) {
                            Text(stringResource(R.string.principal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(money(loan.principal), style = MaterialTheme.typography.headlineSmall)
                        }
                        Image(painterResource(R.drawable.portfolio_ledger), null, Modifier.weight(1f).height(116.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(clientName, style = MaterialTheme.typography.titleLarge)
                            Text("${loan.loanType.duration.label} · ${formatPercentage(loan.loanType.interestRate)}% interest", style = MaterialTheme.typography.bodySmall)
                        }
                        LoanStatusBadge(if (schedule.any { it.isOverdue }) LoanStatus.Overdue else loan.status)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerFilter(stringResource(R.string.nav_overview), !showSchedule) { showSchedule = false }
                        LedgerFilter(stringResource(R.string.repayment_schedule), showSchedule) { showSchedule = true }
                    }
                    if (showSchedule) {
                        RepaymentSchedule(schedule)
                    } else {
                        val manualPenaltyOutstanding = (loan.penaltyBalance - payments.filter { it.loanId == loan.id && it.voidReason.isBlank() && it.date <= currentDateIso() }.sumOf { it.allocatedPenalty }).coerceAtLeast(0.0)
                        val penaltyOutstanding = manualPenaltyOutstanding + schedule.sumOf { it.penaltyOutstanding }
                        LoanFact(R.string.penalty_balance, money(penaltyOutstanding))
                        Text(stringResource(R.string.penalty_parts, money(manualPenaltyOutstanding), money(schedule.sumOf { it.penaltyOutstanding })),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LoanFact(R.string.start_date, loan.startDate)
                        LoanFact(R.string.payoff_balance, money(loanPayoffBalance(loan, payments, currentDateIso(), defaultPenaltyRule)))
                        if (loan.sourceLoanId != null && loan.origin != null) {
                            Text("${loan.origin.label} from loan #${loan.sourceLoanId} · carried ${money(loan.carriedBalance)} · cash advanced ${money(loan.cashAdvanced)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (loan.successorLoanId != null) Text("Renewed into loan #${loan.successorLoanId}", style = MaterialTheme.typography.bodySmall)
                        LoanFact(R.string.total_scheduled, money(schedule.sumOf { it.installment.total }))
                        if (loan.releaseDate.isNotBlank()) {
                            Text("Released ${loan.releaseDate} · ${money(loan.releaseAmount ?: 0.0)} · ${loan.releaseMethod} · Ref ${loan.releaseReference}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { showPenaltyEditor = true }) { Text("Update penalty balance") }
                        if (loan.status != LoanStatus.Cancelled && loan.status != LoanStatus.FullyPaid) {
                            TextButton(onClick = { onUpdate(loan.copy(status = LoanStatus.Cancelled)); onDismiss() }) { Text("Cancel loan") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (loan.status) {
                        LoanStatus.Pending -> {
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onEdit) { Text(stringResource(R.string.edit)) }
                            LedgerButton(onClick = { onUpdate(loan.copy(status = LoanStatus.Approved)) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.approve))
                            }
                        }
                        LoanStatus.Approved -> LedgerButton(onRelease, Modifier.fillMaxWidth()) { Text(stringResource(R.string.record_release)) }
                        LoanStatus.Released -> LedgerButton({ onUpdate(loan.copy(status = LoanStatus.Active)) }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.mark_active))
                        }
                        LoanStatus.Active, LoanStatus.Overdue -> {
                            TextButton(onClick = { onUpdate(loan.copy(status = LoanStatus.FullyPaid)) }) { Text(stringResource(R.string.mark_paid)) }
                            LedgerButton({ showRenewalDialog = true }, Modifier.weight(1f)) { Text(stringResource(R.string.renew_top_up)) }
                        }
                        LoanStatus.FullyPaid, LoanStatus.Cancelled -> Unit
                    }
                }
            }
        }
    }

    if (showPenaltyEditor) {
        PenaltyBalanceDialog(
            currentBalance = loan.penaltyBalance,
            onDismiss = { showPenaltyEditor = false },
            onSave = { balance ->
                onUpdate(loan.copy(penaltyBalance = balance))
                showPenaltyEditor = false
            },
        )
    }

    if (showRenewalDialog) {
        RenewalOrTopUpDialog(
            sourceLoan = loan,
            payments = payments,
            defaultPenaltyRule = defaultPenaltyRule,
            onDismiss = { showRenewalDialog = false },
            onSave = { cashAdvance, date ->
                onRenewOrTopUp(loan.id, cashAdvance, date)
                showRenewalDialog = false
            },
        )
    }
}

@Composable
private fun LoanFact(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RepaymentSchedule(schedule: List<InstallmentStatus>) {
    schedule.forEach { status ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status.installment.dueDate, style = MaterialTheme.typography.titleMedium)
                val label = when {
                    status.isOverdue -> stringResource(R.string.installment_overdue, status.daysOverdue)
                    status.unpaid <= 0.009 -> stringResource(R.string.installment_paid)
                    else -> stringResource(R.string.installment_upcoming)
                }
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (status.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                LoanFact(R.string.remaining, money(status.unpaid))
                LoanFact(R.string.penalty_balance, money(status.penaltyOutstanding))
            }
        }
    }
}

@Composable
internal fun RenewalOrTopUpDialog(
    sourceLoan: Loan,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
) {
    var cashAdvanceText by remember(sourceLoan.id) { mutableStateOf("0") }
    var date by remember(sourceLoan.id) { mutableStateOf(currentDateIso()) }
    val carriedBalance = if (parseDate(date) != null) loanPayoffBalance(sourceLoan, payments, date, defaultPenaltyRule) else 0.0
    val cashAdvance = cashAdvanceText.toDoubleOrNull()
    val newPrincipal = cashAdvance?.let { roundMoney(carriedBalance + it) }
    val newInterest = newPrincipal?.let { roundMoney(it * sourceLoan.loanType.interestRate / 100.0) }
    val valid = cashAdvance != null && cashAdvance >= 0 && carriedBalance > 0 && parseDate(date) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew or top up loan #${sourceLoan.id}") },
        text = {
            Column(Modifier.fillMaxWidth().height(460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The old payoff balance moves into a new loan. Any extra amount is paid out as cash. The old loan will be marked paid and linked to the new loan.", style = MaterialTheme.typography.bodySmall)
                Text("Balance carried forward: ${money(carriedBalance)}", style = MaterialTheme.typography.bodyMedium)
                EditorField("Extra cash to advance (enter 0 to renew only) *", cashAdvanceText) { cashAdvanceText = it }
                DatePickerField("New loan date *", date) { date = it }
                Text("A reference number will be generated automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (newPrincipal != null && newInterest != null) {
                    Text("New principal: ${money(newPrincipal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("New interest (${formatPercentage(sourceLoan.loanType.interestRate)}%): ${money(newInterest)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Total scheduled: ${money(newPrincipal + newInterest)} · cash paid to client: ${money(cashAdvance)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Uses the same ${sourceLoan.loanType.duration.label.lowercase()} loan type and penalty rule as the old loan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            LedgerButton(onClick = { if (valid) onSave(cashAdvance, date) }, enabled = valid) {
                Text(if ((cashAdvance ?: 0.0) > 0) "Confirm top-up" else "Confirm renewal")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun PenaltyBalanceDialog(currentBalance: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember(currentBalance) { mutableStateOf(currentBalance.toString()) }
    val amount = value.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Outstanding penalties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the current penalty balance for this loan. Payments will apply to it before interest and principal.", style = MaterialTheme.typography.bodySmall)
                EditorField("Penalty balance *", value) { value = it }
            }
        },
        confirmButton = { LedgerButton(onClick = { if (amount != null) onSave(roundMoney(amount)) }, enabled = amount != null && amount >= 0) { Text("Save balance") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ReleaseFundsDialog(loan: Loan, onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var date by remember(loan) { mutableStateOf("2026-10-25") }
    var amount by remember(loan) { mutableStateOf(loan.principal.toString()) }
    var method by remember(loan) { mutableStateOf("Cash") }
    var methodMenu by remember { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = parseDate(date) != null && parsedAmount != null && parsedAmount > 0 && method.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record release of funds") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField("Release date *", date) { date = it }
                EditorField("Amount released *", amount) { amount = it }
                androidx.compose.foundation.layout.Box {
                    MenuSelector("Method *: $method", onClick = { methodMenu = true })
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        listOf("Cash", "Bank transfer", "Mobile wallet", "Check").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { method = option; methodMenu = false }) }
                    }
                }
                Text("A reference number will be generated automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { LedgerButton(onClick = { if (valid) onSave(date, parsedAmount, method) }, enabled = valid) { Text("Save release") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
