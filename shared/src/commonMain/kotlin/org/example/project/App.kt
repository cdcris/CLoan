package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.round
import org.example.project.db.Database

private data class MenuItem(val title: String, val symbol: String)

private val menuItems = listOf(
    MenuItem("Dashboard", "⌂"),
    MenuItem("Client", "♙"),
    MenuItem("Loan", "▤"),
    MenuItem("Payment", "$"),
    MenuItem("Settings", "⚙"),
)

private enum class ClientStatus(val label: String) {
    Active("Active"),
    Inactive("Inactive"),
    Blacklisted("Blacklisted"),
}

private data class Client(
    val id: Int,
    val name: String,
    val birthdate: String,
    val contactNumber: String,
    val address: String,
    val employerIncome: String,
    val emergencyContact: String,
    val status: ClientStatus,
    val documents: List<String> = emptyList(),
    val archived: Boolean = false,
)

internal enum class PaymentFrequency(val label: String, val periodsPerYear: Int) {
    Weekly("Weekly", 52),
    SemiMonthly("Semi-monthly", 24),
    Monthly("Monthly", 12),
}

internal enum class LoanStatus(val label: String) {
    Pending("Pending"),
    Approved("Approved"),
    Released("Released"),
    Active("Active"),
    FullyPaid("Fully Paid"),
    Overdue("Overdue"),
    Cancelled("Cancelled"),
}

internal enum class PenaltyMode(val label: String) {
    Flat("Flat amount per overdue installment"),
    Percentage("Percentage of unpaid installment"),
}

internal data class PenaltyRule(val mode: PenaltyMode, val rate: Double)

private fun <T> Database.readEntities(section: String, decode: (Int, String) -> T): List<T> =
    appDataQueries.readSection(section).executeAsList().map { decode(it.id.toInt(), it.payload) }

private fun <T> Database.replaceEntities(
    section: String,
    entities: List<T>,
    id: (T) -> Int,
    encode: (T) -> String,
) {
    transaction {
        appDataQueries.replaceSection(section)
        entities.forEach { entity -> appDataQueries.insertRecord(section, id(entity).toLong(), encode(entity)) }
    }
}

private fun Database.readPenaltyRule(): PenaltyRule {
    val parts = appDataQueries.readSetting(1L).executeAsOneOrNull()?.split('|') ?: return PenaltyRule(PenaltyMode.Percentage, 5.0)
    val mode = PenaltyMode.entries.firstOrNull { it.name == parts[0] } ?: PenaltyMode.Percentage
    return PenaltyRule(mode, parts.getOrNull(1)?.toDoubleOrNull() ?: 5.0)
}

private fun Database.savePenaltyRule(rule: PenaltyRule) {
    appDataQueries.writeSetting(1L, "${rule.mode.name}|${rule.rate}")
}

internal fun encodeFields(fields: List<String>): String = fields.joinToString("|") { field ->
    field.replace("%", "%25").replace("|", "%7C").replace(";", "%3B")
}

internal fun decodeFields(payload: String): List<String> = payload.split('|').map { field ->
    field.replace("%7C", "|").replace("%3B", ";").replace("%25", "%")
}

private fun encodeClient(value: Client) = encodeFields(listOf(
    value.name, value.birthdate, value.contactNumber, value.address, value.employerIncome,
    value.emergencyContact, value.status.name, value.documents.joinToString("\u001f"), value.archived.toString(),
))

private fun decodeClient(id: Int, payload: String): Client {
    val fields = decodeFields(payload)
    return Client(id, fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
        ClientStatus.valueOf(fields[6]), fields[7].takeIf { it.isNotEmpty() }?.split("\u001f").orEmpty(), fields[8].toBoolean())
}

private fun encodeLoan(value: Loan) = encodeFields(listOf(
    value.clientId.toString(), value.principal.toString(), value.annualInterestRate.toString(), value.term.toString(),
    value.startDate, value.frequency.name, value.status.name, value.releaseDate, value.releaseAmount?.toString().orEmpty(),
    value.releaseMethod, value.releaseReference, value.penaltyBalance.toString(), value.penaltyRule?.mode?.name.orEmpty(),
    value.penaltyRule?.rate?.toString().orEmpty(),
))

private fun decodeLoan(id: Int, payload: String): Loan {
    val fields = decodeFields(payload)
    val penaltyMode = fields[12].takeIf { it.isNotEmpty() }?.let(PenaltyMode::valueOf)
    val penaltyRule = penaltyMode?.let { PenaltyRule(it, fields[13].toDouble()) }
    return Loan(id, fields[0].toInt(), fields[1].toDouble(), fields[2].toDouble(), fields[3].toInt(), fields[4],
        PaymentFrequency.valueOf(fields[5]), LoanStatus.valueOf(fields[6]), fields[7], fields[8].toDoubleOrNull(),
        fields[9], fields[10], fields[11].toDouble(), penaltyRule)
}

private fun encodePayment(value: Payment) = encodeFields(listOf(
    value.loanId.toString(), value.amount.toString(), value.date, value.method, value.reference, value.voidReason,
    value.allocatedPenalty.toString(), value.allocatedInterest.toString(), value.allocatedPrincipal.toString(), value.unappliedAmount.toString(),
))

private fun decodePayment(id: Int, payload: String): Payment {
    val fields = decodeFields(payload)
    return Payment(id, fields[0].toInt(), fields[1].toDouble(), fields[2], fields[3], fields[4], fields[5],
        fields[6].toDouble(), fields[7].toDouble(), fields[8].toDouble(), fields[9].toDouble())
}

internal data class Loan(
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

internal data class Payment(
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

internal data class Installment(val number: Int, val dueDate: String, val principal: Double, val interest: Double) {
    val total: Double get() = principal + interest
}

internal data class InstallmentStatus(val installment: Installment, val unpaid: Double, val penalty: Double, val penaltyOutstanding: Double, val daysOverdue: Int) {
    val isOverdue: Boolean get() = daysOverdue > 0 && (unpaid > 0.009 || penaltyOutstanding > 0.009)
}

@Composable
fun App(database: Database) {
    MaterialTheme {
        var selectedIndex by remember { mutableStateOf(0) }
        val clients = remember(database) { mutableStateListOf(*database.readEntities("clients", ::decodeClient).toTypedArray()) }
        val loans = remember(database) { mutableStateListOf(*database.readEntities("loans", ::decodeLoan).toTypedArray()) }
        val payments = remember(database) { mutableStateListOf(*database.readEntities("payments", ::decodePayment).toTypedArray()) }
        var defaultPenaltyRule by remember(database) { mutableStateOf(database.readPenaltyRule()) }
        val saveClients: (List<Client>) -> Unit = { database.replaceEntities("clients", it, Client::id, ::encodeClient) }
        val saveLoans: (List<Loan>) -> Unit = { database.replaceEntities("loans", it, Loan::id, ::encodeLoan) }
        val savePayments: (List<Payment>) -> Unit = { database.replaceEntities("payments", it, Payment::id, ::encodePayment) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    menuItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { Text(item.symbol) },
                            label = { Text(item.title) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { contentPadding ->
            if (selectedIndex == 0) {
                DashboardManagement(
                    clients = clients,
                    loans = loans,
                    payments = payments,
                    defaultPenaltyRule = defaultPenaltyRule,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 1) {
                ClientManagement(
                    clients = clients,
                    onClientsChanged = { saveClients(clients) },
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 2) {
                LoanManagement(
                    clients = clients,
                    loans = loans,
                    payments = payments,
                    defaultPenaltyRule = defaultPenaltyRule,
                    onDataChanged = { saveLoans(loans); savePayments(payments) },
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 3) {
                PaymentManagement(
                    clients = clients,
                    loans = loans,
                    payments = payments,
                    defaultPenaltyRule = defaultPenaltyRule,
                    onPaymentsChanged = { savePayments(payments) },
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else {
                SettingsManagement(
                    currentRule = defaultPenaltyRule,
                    onRuleChange = { updated ->
                        defaultPenaltyRule = updated
                        database.savePenaltyRule(updated)
                        loans.forEach { loan -> reallocateLoanPayments(loan, payments, updated) }
                        savePayments(payments)
                    },
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun SettingsManagement(currentRule: PenaltyRule, onRuleChange: (PenaltyRule) -> Unit, modifier: Modifier = Modifier) {
    var mode by remember(currentRule.mode) { mutableStateOf(currentRule.mode) }
    var rate by remember(currentRule.rate) { mutableStateOf(currentRule.rate.toString()) }
    var menuExpanded by remember { mutableStateOf(false) }
    val parsedRate = rate.toDoubleOrNull()
    Column(modifier.background(Color(0xFFF7F8FA)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Default overdue penalty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("New loans use this rule unless a custom rule is set on the loan. Changes also apply to existing loans that use the default.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("${mode.label}  ▾") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                PenaltyMode.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { mode = option; menuExpanded = false }) }
            }
        }
        EditorField(if (mode == PenaltyMode.Flat) "Flat penalty per installment (₱)" else "Penalty rate (%)", rate) { rate = it }
        Button(onClick = { if (parsedRate != null && parsedRate >= 0) onRuleChange(PenaltyRule(mode, parsedRate)) }, enabled = parsedRate != null && parsedRate >= 0) { Text("Save default rule") }
        Text("A penalty is assessed once, the day after an installment is due, based on the unpaid installment balance at that point. Payments are applied to penalty, then due interest, then principal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClientManagement(clients: MutableList<Client>, onClientsChanged: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All status") }
    var recordFilter by remember { mutableStateOf("Current") }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf((clients.maxOfOrNull { it.id } ?: 0) + 1) }

    val visibleClients = clients.filter { client ->
        val matchesQuery = query.isBlank() || listOf(client.name, client.contactNumber).any { it.contains(query.trim(), ignoreCase = true) }
        val matchesStatus = statusFilter == "All status" || client.status.label == statusFilter
        val matchesArchive = when (recordFilter) {
            "Archived" -> client.archived
            "All records" -> true
            else -> !client.archived
        }
        matchesQuery && matchesStatus && matchesArchive
    }

    Column(modifier = modifier.background(Color(0xFFF7F8FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Clients", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Manage your client records", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { editingClient = null; showCreate = true }) { Text("+ Add") }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search name or contact number") },
            leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var statusMenuOpen by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { statusMenuOpen = true }) { Text("$statusFilter  ▾") }
                DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                    (listOf("All status") + ClientStatus.entries.map { it.label }).forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = { statusFilter = status; statusMenuOpen = false })
                    }
                }
            }
            listOf("Current", "Archived").forEach { filter ->
                FilterChip(selected = recordFilter == filter, onClick = { recordFilter = filter }, label = { Text(filter) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${visibleClients.size} ${if (visibleClients.size == 1) "client" else "clients"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (visibleClients.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No clients found", style = MaterialTheme.typography.titleMedium)
                    Text("Try another search or add a client.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(visibleClients, key = { it.id }) { client ->
                    ClientCard(
                        client = client,
                        onEdit = { editingClient = client },
                        onArchive = {
                            val position = clients.indexOfFirst { it.id == client.id }
                            if (position >= 0) clients[position] = client.copy(archived = !client.archived)
                            onClientsChanged()
                        },
                    )
                }
            }
        }
    }

    if (showCreate || editingClient != null) {
        ClientEditorDialog(
            client = editingClient,
            onDismiss = { showCreate = false; editingClient = null },
            onSave = { saved ->
                if (editingClient == null) {
                    clients.add(saved.copy(id = nextId++))
                } else {
                    val position = clients.indexOfFirst { it.id == editingClient?.id }
                    if (position >= 0) clients[position] = saved.copy(id = editingClient!!.id, archived = editingClient!!.archived)
                }
                onClientsChanged()
                showCreate = false
                editingClient = null
            },
        )
    }
}

@Composable
private fun ClientCard(client: Client, onEdit: () -> Unit, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(42.dp).background(Color(0xFFE8EEFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(client.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), color = Color(0xFF3556A8), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(client.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(client.contactNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(client.status)
            }
            Spacer(Modifier.height(12.dp))
            Text(client.employerIncome, style = MaterialTheme.typography.bodyMedium)
            Text(client.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (client.documents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("📎 ${client.documents.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF52688F))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onArchive) { Text(if (client.archived) "Restore" else "Archive") }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ClientStatus) {
    val (background, foreground) = when (status) {
        ClientStatus.Active -> Color(0xFFE5F5EB) to Color(0xFF237A45)
        ClientStatus.Inactive -> Color(0xFFF0F1F3) to Color(0xFF626873)
        ClientStatus.Blacklisted -> Color(0xFFFDE9E7) to Color(0xFFB43D32)
    }
    Text(status.label, modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
}

@Composable
private fun ClientEditorDialog(client: Client?, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by remember(client) { mutableStateOf(client?.name.orEmpty()) }
    var birthdate by remember(client) { mutableStateOf(client?.birthdate.orEmpty()) }
    var contact by remember(client) { mutableStateOf(client?.contactNumber.orEmpty()) }
    var address by remember(client) { mutableStateOf(client?.address.orEmpty()) }
    var employer by remember(client) { mutableStateOf(client?.employerIncome.orEmpty()) }
    var emergency by remember(client) { mutableStateOf(client?.emergencyContact.orEmpty()) }
    var status by remember(client) { mutableStateOf(client?.status ?: ClientStatus.Active) }
    val documents = remember(client) { mutableStateListOf<String>().apply { addAll(client?.documents.orEmpty()) } }
    var newDocument by remember { mutableStateOf("") }
    var statusMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client == null) "Add client" else "Edit client") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personal details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                EditorField("Full name", name) { name = it }
                EditorField("Birthdate (e.g. 12 Mar 1990)", birthdate) { birthdate = it }
                EditorField("Contact number", contact) { contact = it }
                EditorField("Address", address) { address = it }
                EditorField("Employer and monthly income", employer) { employer = it }
                EditorField("Emergency contact name and number", emergency) { emergency = it }
                Text("Client status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { statusMenuOpen = true }) { Text("${status.label}  ▾") }
                    DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                        ClientStatus.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { status = option; statusMenuOpen = false }) }
                    }
                }
                Text("ID photos and documents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Demo mode: enter a filename to attach it to this client.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newDocument, onValueChange = { newDocument = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Document filename") })
                    TextButton(onClick = { if (newDocument.isNotBlank()) { documents.add(newDocument.trim()); newDocument = "" } }) { Text("Add") }
                }
                documents.forEachIndexed { index, document ->
                    AssistChip(onClick = { documents.removeAt(index) }, label = { Text("📎 $document  ×") })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(Client(client?.id ?: 0, name.trim(), birthdate.trim(), contact.trim(), address.trim(), employer.trim(), emergency.trim(), status, documents.toList(), client?.archived ?: false))
            }, enabled = name.isNotBlank() && contact.isNotBlank()) { Text("Save client") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Address")
}

@Composable
private fun LoanManagement(clients: List<Client>, loans: MutableList<Loan>, payments: MutableList<Payment>, defaultPenaltyRule: PenaltyRule, onDataChanged: () -> Unit, modifier: Modifier = Modifier) {
    var showCreate by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }
    var releasingLoan by remember { mutableStateOf<Loan?>(null) }
    var nextId by remember { mutableStateOf((loans.maxOfOrNull { it.id } ?: 0) + 1) }

    Column(modifier.background(Color(0xFFF7F8FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Loans", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Create and track loan accounts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { editingLoan = null; showCreate = true }) { Text("+ New loan") }
        }
        Spacer(Modifier.height(16.dp))
        Text("${loans.size} ${if (loans.size == 1) "loan" else "loans"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (loans.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No loans yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create a loan to see its repayment schedule.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(loans, key = { it.id }) { loan ->
                    LoanCard(
                        loan = loan,
                        clientName = clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client",
                        payments = payments,
                        defaultPenaltyRule = defaultPenaltyRule,
                        onOpen = { selectedLoan = loan },
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
                if (editingLoan == null) loans.add(saved.copy(id = nextId++))
                else {
                    val index = loans.indexOfFirst { it.id == editingLoan?.id }
                    if (index >= 0) loans[index] = saved.copy(id = editingLoan!!.id, status = editingLoan!!.status)
                }
                onDataChanged()
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
            onUpdate = { updated ->
                val index = loans.indexOfFirst { it.id == updated.id }
                if (index >= 0) loans[index] = updated
                reallocateLoanPayments(updated, payments, defaultPenaltyRule)
                onDataChanged()
            },
            onEdit = { editingLoan = currentLoan; selectedLoan = null },
            onRelease = { releasingLoan = currentLoan; selectedLoan = null },
        )
    }

    releasingLoan?.let { loan ->
        ReleaseFundsDialog(
            loan = loan,
            onDismiss = { releasingLoan = null },
            onSave = { date, amount, method, reference ->
                val index = loans.indexOfFirst { it.id == loan.id }
                if (index >= 0) loans[index] = loan.copy(
                    status = LoanStatus.Released,
                    releaseDate = date,
                    releaseAmount = amount,
                    releaseMethod = method,
                    releaseReference = reference,
                )
                onDataChanged()
                releasingLoan = null
            },
        )
    }
}

@Composable
private fun LoanCard(loan: Loan, clientName: String, payments: List<Payment>, defaultPenaltyRule: PenaltyRule, onOpen: () -> Unit) {
    val autoOverdue = installmentStatuses(loan, payments, currentDateIso(), defaultPenaltyRule).any { it.isOverdue }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${loan.frequency.label} · ${loan.term} payments · ${loan.annualInterestRate}% p.a.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LoanStatusBadge(if (autoOverdue) LoanStatus.Overdue else loan.status)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${money(loan.principal)} principal", style = MaterialTheme.typography.bodyMedium)
                    Text("Starts ${loan.startDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Details  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LoanStatusBadge(status: LoanStatus) {
    val colors = when (status) {
        LoanStatus.Pending -> Color(0xFFFFF1D6) to Color(0xFF93600B)
        LoanStatus.Approved -> Color(0xFFE8EEFF) to Color(0xFF3556A8)
        LoanStatus.Released -> Color(0xFFEDE8FF) to Color(0xFF6549A3)
        LoanStatus.Active -> Color(0xFFE5F5EB) to Color(0xFF237A45)
        LoanStatus.FullyPaid -> Color(0xFFE6F4F5) to Color(0xFF237278)
        LoanStatus.Overdue -> Color(0xFFFDE9E7) to Color(0xFFB43D32)
        LoanStatus.Cancelled -> Color(0xFFF0F1F3) to Color(0xFF626873)
    }
    Text(status.label, modifier = Modifier.background(colors.first, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), color = colors.second, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
}

@Composable
private fun LoanEditorDialog(clients: List<Client>, loan: Loan?, defaultPenaltyRule: PenaltyRule, onDismiss: () -> Unit, onSave: (Loan) -> Unit) {
    var clientId by remember(loan) { mutableStateOf(loan?.clientId ?: clients.firstOrNull()?.id ?: 0) }
    var principal by remember(loan) { mutableStateOf(loan?.principal?.toString().orEmpty()) }
    var rate by remember(loan) { mutableStateOf(loan?.annualInterestRate?.toString().orEmpty()) }
    var term by remember(loan) { mutableStateOf(loan?.term?.toString().orEmpty()) }
    var startDate by remember(loan) { mutableStateOf(loan?.startDate ?: "2026-10-25") }
    var frequency by remember(loan) { mutableStateOf(loan?.frequency ?: PaymentFrequency.Monthly) }
    var useLoanPenaltyRule by remember(loan) { mutableStateOf(loan?.penaltyRule != null) }
    var penaltyMode by remember(loan) { mutableStateOf(loan?.penaltyRule?.mode ?: defaultPenaltyRule.mode) }
    var penaltyRate by remember(loan) { mutableStateOf((loan?.penaltyRule?.rate ?: defaultPenaltyRule.rate).toString()) }
    var penaltyMenu by remember { mutableStateOf(false) }
    var clientMenu by remember { mutableStateOf(false) }
    var frequencyMenu by remember { mutableStateOf(false) }
    val computedPrincipal = principal.toDoubleOrNull()
    val computedRate = rate.toDoubleOrNull()
    val computedTerm = term.toIntOrNull()
    val preview = if (computedPrincipal != null && computedRate != null && computedTerm != null && computedPrincipal > 0 && computedRate >= 0 && computedTerm in 1..120) {
        createSchedule(computedPrincipal, computedRate, computedTerm, startDate, frequency)
    } else emptyList()
    val valid = clients.any { it.id == clientId } && computedPrincipal != null && computedPrincipal > 0 && computedRate != null && computedRate >= 0 && computedTerm != null && computedTerm in 1..120 && parseDate(startDate) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (loan == null) "Create loan" else "Edit pending loan") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Loan details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { clientMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${clients.firstOrNull { it.id == clientId }?.name ?: "Select client"}  ▾")
                    }
                    DropdownMenu(expanded = clientMenu, onDismissRequest = { clientMenu = false }) {
                        clients.forEach { client -> DropdownMenuItem(text = { Text(client.name) }, onClick = { clientId = client.id; clientMenu = false }) }
                    }
                }
                EditorField("Principal amount", principal) { principal = it }
                EditorField("Annual interest rate (%)", rate) { rate = it }
                EditorField("Term (number of payments, max 120)", term) { term = it }
                EditorField("Start date (YYYY-MM-DD)", startDate) { startDate = it }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { frequencyMenu = true }) { Text("${frequency.label} payment frequency  ▾") }
                    DropdownMenu(expanded = frequencyMenu, onDismissRequest = { frequencyMenu = false }) {
                        PaymentFrequency.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { frequency = option; frequencyMenu = false }) }
                    }
                }
                Text("Overdue penalty", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { useLoanPenaltyRule = !useLoanPenaltyRule }) {
                    val defaultValue = if (defaultPenaltyRule.mode == PenaltyMode.Flat) money(defaultPenaltyRule.rate) else "${defaultPenaltyRule.rate}%"
                    Text(if (useLoanPenaltyRule) "Using this loan's custom rule" else "Using default rule (${defaultPenaltyRule.mode.label}: $defaultValue)")
                }
                if (useLoanPenaltyRule) {
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { penaltyMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("${penaltyMode.label}  ▾") }
                        DropdownMenu(expanded = penaltyMenu, onDismissRequest = { penaltyMenu = false }) {
                            PenaltyMode.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { penaltyMode = option; penaltyMenu = false }) }
                        }
                    }
                    EditorField(if (penaltyMode == PenaltyMode.Flat) "Penalty amount per overdue installment" else "Penalty percentage (%)", penaltyRate) { penaltyRate = it }
                }
                if (preview.isNotEmpty()) {
                    val total = preview.sumOf { it.total }
                    Text("Schedule preview · ${preview.size} payments · ${money(total)} total", style = MaterialTheme.typography.labelLarge)
                    Text("Annual rate is divided across payment periods; principal is evenly amortized and interest declines with the balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = { if (valid) onSave(Loan(loan?.id ?: 0, clientId, computedPrincipal!!, computedRate!!, computedTerm!!, startDate, frequency, loan?.status ?: LoanStatus.Pending, penaltyRule = if (useLoanPenaltyRule) PenaltyRule(penaltyMode, penaltyRate.toDouble()) else null, penaltyBalance = loan?.penaltyBalance ?: 0.0)) }, enabled = valid && (!useLoanPenaltyRule || ((penaltyRate.toDoubleOrNull() ?: -1.0) >= 0.0))) { Text("Save loan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoanDetailsDialog(
    loan: Loan,
    clientName: String,
    payments: List<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onDismiss: () -> Unit,
    onUpdate: (Loan) -> Unit,
    onEdit: () -> Unit,
    onRelease: () -> Unit,
) {
    val schedule = installmentStatuses(loan, payments, currentDateIso(), defaultPenaltyRule)
    var showPenaltyEditor by remember(loan.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loan details") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${loan.frequency.label} · ${loan.term} payments · ${loan.annualInterestRate}% p.a.", style = MaterialTheme.typography.bodySmall)
                    }
                    LoanStatusBadge(if (schedule.any { it.isOverdue }) LoanStatus.Overdue else loan.status)
                }
                Text("Principal: ${money(loan.principal)}", style = MaterialTheme.typography.bodyMedium)
                val manualPenaltyOutstanding = (loan.penaltyBalance - payments.filter { it.loanId == loan.id && it.voidReason.isBlank() && it.date <= currentDateIso() }.sumOf { it.allocatedPenalty }).coerceAtLeast(0.0)
                val penaltyOutstanding = manualPenaltyOutstanding + schedule.sumOf { it.penaltyOutstanding }
                Text("Penalty balance: ${money(penaltyOutstanding)} (manual ${money(manualPenaltyOutstanding)} · installment ${money(schedule.sumOf { it.penaltyOutstanding })})", style = MaterialTheme.typography.bodyMedium)
                Text("Start date: ${loan.startDate}", style = MaterialTheme.typography.bodyMedium)
                Text("Total scheduled: ${money(schedule.sumOf { it.installment.total })}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (loan.releaseDate.isNotBlank()) {
                    Text("Released ${loan.releaseDate} · ${money(loan.releaseAmount ?: 0.0)} · ${loan.releaseMethod} · Ref ${loan.releaseReference}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showPenaltyEditor = true }) { Text("Update penalty balance") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loan.status == LoanStatus.Pending) {
                        OutlinedButton(onClick = onEdit) { Text("Edit") }
                        Button(onClick = { onUpdate(loan.copy(status = LoanStatus.Approved)) }) { Text("Approve") }
                    }
                    if (loan.status == LoanStatus.Approved) Button(onClick = onRelease) { Text("Record release") }
                    if (loan.status == LoanStatus.Released) Button(onClick = { onUpdate(loan.copy(status = LoanStatus.Active)) }) { Text("Mark active") }
                }
                if (loan.status == LoanStatus.Active || loan.status == LoanStatus.Overdue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { onUpdate(loan.copy(status = LoanStatus.FullyPaid)) }) { Text("Mark fully paid") }
                    }
                }
                if (loan.status != LoanStatus.Cancelled && loan.status != LoanStatus.FullyPaid) {
                    TextButton(onClick = { onUpdate(loan.copy(status = LoanStatus.Cancelled)); onDismiss() }) { Text("Cancel loan") }
                }
                Text("Amortization schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Due · status", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Remaining", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Penalty", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                schedule.forEach { status ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(status.installment.dueDate + if (status.isOverdue) " · OVERDUE ${status.daysOverdue}d" else if (status.unpaid <= 0.009) " · Paid" else " · Upcoming", style = MaterialTheme.typography.labelSmall, color = if (status.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text(money(status.unpaid), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            Text(money(status.penaltyOutstanding), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

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
}

@Composable
private fun PenaltyBalanceDialog(currentBalance: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember(currentBalance) { mutableStateOf(currentBalance.toString()) }
    val amount = value.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Outstanding penalties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the current penalty balance for this loan. Payments will apply to it before interest and principal.", style = MaterialTheme.typography.bodySmall)
                EditorField("Penalty balance", value) { value = it }
            }
        },
        confirmButton = { Button(onClick = { if (amount != null) onSave(roundMoney(amount)) }, enabled = amount != null && amount >= 0) { Text("Save balance") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReleaseFundsDialog(loan: Loan, onDismiss: () -> Unit, onSave: (String, Double, String, String) -> Unit) {
    var date by remember(loan) { mutableStateOf("2026-10-25") }
    var amount by remember(loan) { mutableStateOf(loan.principal.toString()) }
    var method by remember(loan) { mutableStateOf("Cash") }
    var reference by remember(loan) { mutableStateOf("") }
    var methodMenu by remember { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = parseDate(date) != null && parsedAmount != null && parsedAmount > 0 && method.isNotBlank() && reference.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record release of funds") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorField("Release date (YYYY-MM-DD)", date) { date = it }
                EditorField("Amount released", amount) { amount = it }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { methodMenu = true }) { Text("$method  ▾") }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        listOf("Cash", "Bank transfer", "Mobile wallet", "Check").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { method = option; methodMenu = false }) }
                    }
                }
                EditorField("Reference number", reference) { reference = it }
            }
        },
        confirmButton = { Button(onClick = { if (valid) onSave(date, parsedAmount!!, method, reference.trim()) }, enabled = valid) { Text("Save release") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class SimpleDate(val year: Int, val month: Int, val day: Int) {
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

private fun parseDate(value: String): SimpleDate? {
    val pieces = value.split('-')
    if (pieces.size != 3) return null
    val year = pieces[0].toIntOrNull() ?: return null
    val month = pieces[1].toIntOrNull() ?: return null
    val day = pieces[2].toIntOrNull() ?: return null
    if (year !in 1..9999 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
    return SimpleDate(year, month, day)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun createSchedule(principal: Double, annualRate: Double, term: Int, startDate: String, frequency: PaymentFrequency): List<Installment> {
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

private fun roundMoney(amount: Double): Double = round(amount * 100.0) / 100.0

private fun money(amount: Double): String {
    val rounded = roundMoney(amount)
    val whole = rounded.toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    val cents = (rounded * 100).toLong().rem(100).toString().padStart(2, '0')
    return "₱$whole.$cents"
}

@Composable
private fun PaymentManagement(
    clients: List<Client>,
    loans: List<Loan>,
    payments: MutableList<Payment>,
    defaultPenaltyRule: PenaltyRule,
    onPaymentsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var voidingPayment by remember { mutableStateOf<Payment?>(null) }
    var receiptPayment by remember { mutableStateOf<Payment?>(null) }
    var filter by remember { mutableStateOf("All loans and clients") }
    var filterMenu by remember { mutableStateOf(false) }
    var nextId by remember { mutableStateOf((payments.maxOfOrNull { it.id } ?: 0) + 1) }
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

    Column(modifier.background(Color(0xFFF7F8FA)).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Payments", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Record collections and issue receipts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { editingPayment = null; showCreate = true }) { Text("+ Record") }
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { filterMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("History: $filter  ▾") }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                filterOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { filter = option; filterMenu = false }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${filteredPayments.size} ${if (filteredPayments.size == 1) "payment" else "payments"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (filteredPayments.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No payments found", style = MaterialTheme.typography.titleMedium)
                    Text("Choose another history filter or record a payment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(filteredPayments, key = { it.id }) { payment ->
                    val loan = loans.firstOrNull { it.id == payment.loanId }
                    val clientName = clients.firstOrNull { it.id == loan?.clientId }?.name ?: "Unknown client"
                    PaymentCard(
                        payment = payment,
                        clientName = clientName,
                        onReceipt = { receiptPayment = payment },
                        onEdit = { editingPayment = payment; showCreate = true },
                        onVoid = { voidingPayment = payment },
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
                val priorLoanId = editingPayment?.loanId
                if (editingPayment == null) {
                    payments.add(saved.copy(id = nextId++))
                } else {
                    val index = payments.indexOfFirst { it.id == editingPayment?.id }
                    if (index >= 0) payments[index] = saved.copy(id = editingPayment!!.id)
                }
                val affectedLoanId = saved.loanId
                if (priorLoanId != null && priorLoanId != affectedLoanId) {
                    loans.firstOrNull { it.id == priorLoanId }?.let { reallocateLoanPayments(it, payments, defaultPenaltyRule) }
                }
                val loan = loans.firstOrNull { it.id == affectedLoanId }
                if (loan != null) reallocateLoanPayments(loan, payments, defaultPenaltyRule)
                onPaymentsChanged()
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
                val index = payments.indexOfFirst { it.id == payment.id }
                if (index >= 0) payments[index] = payment.copy(voidReason = reason)
                loans.firstOrNull { it.id == payment.loanId }?.let { reallocateLoanPayments(it, payments, defaultPenaltyRule) }
                onPaymentsChanged()
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
}

@Composable
private fun PaymentCard(
    payment: Payment,
    clientName: String,
    onReceipt: () -> Unit,
    onEdit: () -> Unit,
    onVoid: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Loan #${payment.loanId} · ${payment.date} · ${payment.method}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (payment.voidReason.isNotBlank()) {
                    Text("VOID", color = Color(0xFFB43D32), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                } else {
                    Text(money(payment.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Text("Ref ${payment.reference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (payment.voidReason.isNotBlank()) {
                Text("Void reason: ${payment.voidReason}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB43D32))
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Applied · Penalty ${money(payment.allocatedPenalty)}  |  Interest ${money(payment.allocatedInterest)}  |  Principal ${money(payment.allocatedPrincipal)}", style = MaterialTheme.typography.labelSmall)
                if (payment.unappliedAmount > 0) Text("Unapplied: ${money(payment.unappliedAmount)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onReceipt) { Text("Receipt PDF") }
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onVoid) { Text("Void") }
                }
            }
        }
    }
}

@Composable
private fun PaymentEditorDialog(loans: List<Loan>, payment: Payment?, onDismiss: () -> Unit, onSave: (Payment) -> Unit) {
    val eligibleLoans = loans.filter { it.status != LoanStatus.Cancelled && it.status != LoanStatus.FullyPaid }
    var loanId by remember(payment) { mutableStateOf(payment?.loanId ?: eligibleLoans.firstOrNull()?.id ?: 0) }
    var amount by remember(payment) { mutableStateOf(payment?.amount?.toString().orEmpty()) }
    var date by remember(payment) { mutableStateOf(payment?.date ?: "2026-10-25") }
    var method by remember(payment) { mutableStateOf(payment?.method ?: "Cash") }
    var reference by remember(payment) { mutableStateOf(payment?.reference.orEmpty()) }
    var loanMenu by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = eligibleLoans.any { it.id == loanId } && parsedAmount != null && parsedAmount > 0 && parseDate(date) != null && reference.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (payment == null) "Record payment" else "Edit payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { loanMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${eligibleLoans.firstOrNull { it.id == loanId }?.let { "Loan #${it.id}" } ?: "Select loan"}  ▾")
                    }
                    DropdownMenu(expanded = loanMenu, onDismissRequest = { loanMenu = false }) {
                        eligibleLoans.forEach { option -> DropdownMenuItem(text = { Text("Loan #${option.id} · ${money(option.principal)}") }, onClick = { loanId = option.id; loanMenu = false }) }
                    }
                }
                EditorField("Amount", amount) { amount = it }
                EditorField("Payment date (YYYY-MM-DD)", date) { date = it }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { methodMenu = true }) { Text("$method  ▾") }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        listOf("Cash", "Bank transfer", "Mobile wallet", "Check").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { method = option; methodMenu = false }) }
                    }
                }
                EditorField("Reference number", reference) { reference = it }
                Text("Payments apply to penalties first, then due interest, then outstanding principal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { if (valid) onSave(Payment(payment?.id ?: 0, loanId, parsedAmount!!, date, method, reference.trim())) }, enabled = valid) { Text("Save payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VoidPaymentDialog(payment: Payment, onDismiss: () -> Unit, onVoid: (String) -> Unit) {
    var reason by remember(payment) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payment ${money(payment.amount)} · Ref ${payment.reference}")
                OutlinedTextField(value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reason for voiding") })
            }
        },
        confirmButton = { Button(onClick = { if (reason.isNotBlank()) onVoid(reason.trim()) }, enabled = reason.isNotBlank()) { Text("Void payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep payment") } },
    )
}

@Composable
private fun ReceiptDialog(payment: Payment, clientName: String, onDismiss: () -> Unit) {
    var result by remember(payment.id) { mutableStateOf("") }
    val loanAmount = money(payment.amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Official receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CLoan · PAYMENT RECEIPT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Receipt no. RC-${payment.id.toString().padStart(6, '0')}")
                Text("Received from: $clientName")
                Text("Amount received: $loanAmount")
                Text("Date: ${payment.date} · Method: ${payment.method}")
                Text("Reference: ${payment.reference}")
                Text("Applied: Penalties ${money(payment.allocatedPenalty)}, Interest ${money(payment.allocatedInterest)}, Principal ${money(payment.allocatedPrincipal)}")
                if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = {
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

private fun reallocateLoanPayments(loan: Loan, payments: MutableList<Payment>, defaultPenaltyRule: PenaltyRule) {
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

private fun penaltyAmount(rule: PenaltyRule, unpaid: Double): Double = roundMoney(
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

private data class OverdueLoanSummary(
    val loan: Loan,
    val clientName: String,
    val pastDue: Double,
    val oldestDueDate: String,
    val daysOverdue: Int,
)

@Composable
private fun DashboardManagement(
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
            Column(Modifier.weight(1f)) {
                Text("Due today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(dueToday.size.toString() + " loans", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text("Due this week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(dueThisWeek.size.toString() + " loans", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Overdue loans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (arrears.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("No overdue loans", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            arrears.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
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
                OutlinedTextField(value = reportFrom, onValueChange = { reportFrom = it }, modifier = Modifier.weight(1f), label = { Text("From (YYYY-MM-DD)") }, singleLine = true)
            }
            OutlinedTextField(value = reportTo, onValueChange = { reportTo = it }, modifier = Modifier.weight(1f), label = { Text(if (isOverdueReport) "As of date" else "To date") }, singleLine = true)
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier, tint: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().background(tint.copy(alpha = 0.45f)).padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun remainingLoanBalance(loan: Loan, payments: List<Payment>, asOfDate: String, defaultPenaltyRule: PenaltyRule): Double {
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

private fun loansDueOn(loans: List<Loan>, payments: List<Payment>, start: String, end: String): List<Loan> = loans.filter { loan ->
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

private fun overdueSummary(loan: Loan, clients: List<Client>, payments: List<Payment>, asOfDate: String, defaultPenaltyRule: PenaltyRule): OverdueLoanSummary? {
    val eligible = loan.status == LoanStatus.Released || loan.status == LoanStatus.Active || loan.status == LoanStatus.Overdue
    if (!eligible) return null
    val overdue = installmentStatuses(loan, payments, asOfDate, defaultPenaltyRule).filter { it.isOverdue }
    if (overdue.isEmpty()) return null
    val oldestDue = overdue.minOf { it.installment.dueDate }
    val daysLate = overdue.maxOf { it.daysOverdue }
    val amount = roundMoney(overdue.sumOf { it.unpaid + it.penaltyOutstanding })
    return OverdueLoanSummary(loan, clients.firstOrNull { it.id == loan.clientId }?.name ?: "Unknown client", amount, oldestDue, daysLate)
}

private fun daysBetween(start: String, end: String): Int {
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

private fun buildReportLines(
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
