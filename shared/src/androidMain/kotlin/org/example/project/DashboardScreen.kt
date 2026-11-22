package org.example.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.example.project.shared.R

@Composable
internal fun DashboardManagement(
    clients: List<Client>,
    loans: List<Loan>,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    modifier: Modifier = Modifier,
) {
    val today = currentDateIso()
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
        Spacer(Modifier.height(22.dp))
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Portfolio overview · " + today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        BalanceCard(money(outstanding), money(released), money(collected))
        SectionTitle(stringResource(R.string.portfolio_activity))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Total clients", clients.size.toString(), Modifier.weight(1f))
            DashboardMetric("Active loans", activeCount.toString(), Modifier.weight(1f))
        }
        SectionTitle(stringResource(R.string.collection_schedule))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("Due today", dueToday.size.toString() + " loans", Modifier.weight(1f))
            DashboardMetric("Due this week", dueThisWeek.size.toString() + " loans", Modifier.weight(1f))
        }
        SectionTitle("Overdue loans · " + arrears.size)
        if (arrears.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text("No overdue loans", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            arrears.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
    Text(title, Modifier.padding(top = 20.dp, bottom = 10.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun BalanceCard(balance: String, released: String, collected: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Forest, contentColor = Color.White),
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                val origin = Offset(size.width * 1.08f, size.height * 0.13f)
                repeat(12) { index ->
                    drawCircle(Citron.copy(alpha = 0.15f), (32 + index * 10).dp.toPx(), origin, style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(stringResource(R.string.outstanding_balance), color = Citron, style = MaterialTheme.typography.labelLarge)
                Text(balance, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineMedium)
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.22f))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Total released", color = Citron, style = MaterialTheme.typography.labelMedium)
                        Text(released, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Total collected", color = Citron, style = MaterialTheme.typography.labelMedium)
                        Text(collected, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview
@Composable
private fun BalancePreview() {
    LedgerTheme { BalanceCard("125,000.00", "185,000.00", "60,000.00") }
}
