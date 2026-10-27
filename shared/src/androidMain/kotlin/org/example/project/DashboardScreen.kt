package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    Column(modifier.background(Color(0xFFF7F8FA)).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Portfolio overview · " + today, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric("Total clients", clients.size.toString(), Modifier.weight(1f), Color(0xFFE8EEFF))
            DashboardMetric("Active loans", activeCount.toString(), Modifier.weight(1f), Color(0xFFE5F5EB))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric("Total released", money(released), Modifier.weight(1f), Color(0xFFEDE8FF))
            DashboardMetric("Total collected", money(collected), Modifier.weight(1f), Color(0xFFE6F4F5))
        }
        Spacer(Modifier.height(10.dp))
        DashboardMetric("Outstanding balance", money(outstanding), Modifier.fillMaxWidth(), Color(0xFFFFF1D6))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric("Due today", dueToday.size.toString() + " loans", Modifier.weight(1f), Color(0xFFE6F4F5))
            DashboardMetric("Due this week", dueThisWeek.size.toString() + " loans", Modifier.weight(1f), Color(0xFFFFF1D6))
        }
        Spacer(Modifier.height(18.dp))
        DashboardMetric("Overdue loans", arrears.size.toString(), Modifier.fillMaxWidth(), Color(0xFFFDE9E7))
        Spacer(Modifier.height(8.dp))
        if (arrears.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE3E7EE))) {
                Text("No overdue loans", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            arrears.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE3E7EE))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.clientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Loan #" + item.loan.id + " · " + item.daysOverdue + " days late", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(item.pastDue), style = MaterialTheme.typography.titleSmall, color = Color(0xFFB43D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE3E7EE))) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Generate a PDF report for the selected date range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { reportMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(reportType + "  ▾") }
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
                Button(
                    onClick = {
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
internal fun DashboardMetric(label: String, value: String, modifier: Modifier, tint: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE3E7EE))) {
        Column(Modifier.fillMaxWidth().background(tint.copy(alpha = 0.45f)).padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
