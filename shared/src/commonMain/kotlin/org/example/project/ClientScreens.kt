package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val baselinePersonIcon = ImageVector.Builder(
    name = "baseline_person_24",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF3556A8)), pathFillType = PathFillType.NonZero) {
        moveTo(12f, 12f)
        curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
        curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
        curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
        curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
        moveTo(12f, 14f)
        curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
        verticalLineTo(20f)
        horizontalLineTo(20f)
        verticalLineTo(18f)
        curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
        close()
    }
}.build()

@Composable
internal fun SettingsManagement(currentRule: PenaltyRule, onRuleChange: (PenaltyRule) -> Unit, modifier: Modifier = Modifier) {
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
internal fun ClientManagement(clients: MutableList<Client>, onClientsChanged: () -> Unit, modifier: Modifier = Modifier) {
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
internal fun ClientCard(client: Client, onEdit: () -> Unit, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(42.dp).background(Color(0xFFE8EEFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (client.documents.isEmpty()) {
                        Image(imageVector = baselinePersonIcon, contentDescription = "Default client photo", modifier = Modifier.size(24.dp))
                    } else {
                        Text(client.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), color = Color(0xFF3556A8), fontWeight = FontWeight.Bold)
                    }
                }
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
internal fun StatusBadge(status: ClientStatus) {
    val (background, foreground) = when (status) {
        ClientStatus.Active -> Color(0xFFE5F5EB) to Color(0xFF237A45)
        ClientStatus.Inactive -> Color(0xFFF0F1F3) to Color(0xFF626873)
        ClientStatus.Blacklisted -> Color(0xFFFDE9E7) to Color(0xFFB43D32)
    }
    Text(status.label, modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
}

@Composable
internal fun ClientEditorDialog(client: Client?, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
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
                DatePickerField("Birthdate", birthdate) { birthdate = it }
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
                Text("ID photos and documents (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("You can save the client without attaching documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (documents.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(imageVector = baselinePersonIcon, contentDescription = "Default client photo preview", modifier = Modifier.size(42.dp))
                        Text("Default photo preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
internal fun EditorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Address")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DatePickerField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        modifier = modifier.fillMaxWidth().clickable { pickerOpen = true },
        label = { Text(label) },
        singleLine = true,
        readOnly = true,
        trailingIcon = { TextButton(onClick = { pickerOpen = true }) { Text("Choose") } },
    )
    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = parseDate(value)?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onValueChange(millisToSimpleDate(it).toString()) }
                    pickerOpen = false
                }, enabled = pickerState.selectedDateMillis != null) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
