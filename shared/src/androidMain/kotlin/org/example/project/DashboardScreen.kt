package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.shared.R

@Composable
internal fun DashboardManagement(
    clients: List<Client>,
    loans: List<Loan>,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onFindClient: () -> Unit,
    onViewLoans: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = currentDateIso()
    var group by rememberSaveable { mutableStateOf(R.string.accounts) }
    var reportType by remember { mutableStateOf("Collection report") }
    var reportFrom by remember { mutableStateOf(today) }
    var reportTo by remember { mutableStateOf(today) }
    var reportMenu by remember { mutableStateOf(false) }
    var reportResult by remember { mutableStateOf("") }
    val currentLoans = loans.filter { it.status == LoanStatus.Released || it.status == LoanStatus.Active || it.status == LoanStatus.Overdue }
    val arrears = currentLoans.mapNotNull { overdueSummary(it, clients, payments, today, defaultPenaltyRule) }.sortedByDescending { it.daysOverdue }
    val activeCount = currentLoans.count { remainingLoanBalance(it, payments, today, defaultPenaltyRule) > 0.009 }
    val outstanding = currentLoans.sumOf { remainingLoanBalance(it, payments, today, defaultPenaltyRule) }
    val released = loans.filter { it.releaseDate.isNotBlank() }.sumOf { it.releaseAmount ?: 0.0 }
    val collected = payments.filter { it.voidReason.isBlank() }.sumOf { it.amount }
    val dueToday = loansDueOn(loans, payments, today, today)
    val weekStart = parseDate(today)?.plusDays(1)?.toString() ?: today
    val weekEnd = parseDate(today)?.plusDays(7)?.toString() ?: today
    val dueThisWeek = loansDueOn(loans, payments, weekStart, weekEnd)
    val reportTypes = listOf("Collection report", "Loan release report", "Overdue / aging report")
    val isOverdueReport = reportType == "Overdue / aging report"
    val datesValid = parseDate(reportTo) != null && (isOverdueReport || (parseDate(reportFrom) != null && reportFrom <= reportTo))

    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_overview), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.ledger_brand), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(today, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.portfolio_overview), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onFindClient, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.ic_search), null, Modifier.size(19.dp))
                    Text(stringResource(R.string.find_client), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(onClick = onSettings, shape = MaterialTheme.shapes.small, color = BrandBrown) {
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.nav_settings), tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        BalanceCard(money(outstanding), onViewLoans)
        SectionTitle(stringResource(R.string.portfolio_activity))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.accounts, R.string.collections, R.string.cash_flow).forEach { option ->
                LedgerFilter(stringResource(option), selected = group == option, onClick = { group = option })
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (group) {
                R.string.collections -> {
                    DashboardMetric(R.string.due_today, dueToday.size.toString(), R.drawable.ic_calendar, Modifier.weight(1f))
                    DashboardMetric(R.string.due_week, dueThisWeek.size.toString(), R.drawable.ic_calendar, Modifier.weight(1f))
                }
                R.string.cash_flow -> {
                    DashboardMetric(R.string.total_released, money(released), R.drawable.ic_loans, Modifier.weight(1f))
                    DashboardMetric(R.string.total_collected, money(collected), R.drawable.ic_payments, Modifier.weight(1f))
                }
                else -> {
                    DashboardMetric(R.string.total_clients, clients.size.toString(), R.drawable.ic_clients, Modifier.weight(1f))
                    DashboardMetric(R.string.active_loans, activeCount.toString(), R.drawable.ic_loans, Modifier.weight(1f))
                }
            }
        }
        SectionTitle("Overdue loans · " + arrears.size)
        if (arrears.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("No overdue loans", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            arrears.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.clientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Loan #" + item.loan.id + " · " + item.daysOverdue + " days late", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(item.pastDue), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Generate a PDF report for the selected date range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box {
                    MenuSelector(reportType, onClick = { reportMenu = true }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = reportMenu, onDismissRequest = { reportMenu = false }) {
                        reportTypes.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { reportType = option; reportMenu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!isOverdueReport) {
                        DatePickerField("From", reportFrom, Modifier.weight(1f)) { reportFrom = it }
                    }
                    DatePickerField(if (isOverdueReport) "As of date" else "To date", reportTo, Modifier.weight(1f)) { reportTo = it }
                }
                if (!datesValid) Text("Enter valid dates in YYYY-MM-DD order.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                LedgerButton(onClick = {
                        val reportLines = buildReportLines(reportType, reportFrom, reportTo, clients, loans, payments, defaultPenaltyRule)
                        val key = reportType.lowercase().replace(" ", "-").replace("/", "")
                        reportResult = saveOfficialReceiptPdf(key + "-" + reportFrom + "-" + reportTo + ".pdf", buildPdfDocument(reportLines))
                    },
                    enabled = datesValid,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate report PDF") }
                if (reportResult.isNotBlank()) Text(reportResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(top = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun BalanceCard(balance: String, onViewLoans: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = LedgerInk, contentColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.6f)) {
                Text(stringResource(R.string.outstanding_balance), color = Color(0xFFEBD4C3), style = MaterialTheme.typography.labelLarge)
                Text(balance, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onViewLoans, shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = LedgerInk)) {
                    Text(stringResource(R.string.view_loans))
                }
            }
            Image(painterResource(R.drawable.portfolio_ledger), null,
                Modifier.weight(1f).height(118.dp))
        }
    }
}

@Composable
private fun DashboardMetric(label: Int, value: String, icon: Int, modifier: Modifier) {
    Card(
        modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(label), Modifier.fillMaxWidth().background(LedgerInk).padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun BalancePreview() {
    LedgerTheme { BalanceCard("125,000.00") {} }
}
