package org.example.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import org.example.project.shared.R

private val baselinePersonIcon = ImageVector.Builder(
    name = "baseline_person_24",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
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

private fun settingsActionIcon(action: String): ImageVector = ImageVector.Builder(
    name = if (action == "Export") "upload" else "download",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(5f, 20f); horizontalLineTo(19f); verticalLineTo(18f); horizontalLineTo(5f); close()
        if (action == "Export") {
            moveTo(12f, 3f); lineTo(19f, 10f); horizontalLineTo(15f); verticalLineTo(16f)
            horizontalLineTo(9f); verticalLineTo(10f); horizontalLineTo(5f); close()
        } else {
            moveTo(12f, 17f); lineTo(5f, 10f); horizontalLineTo(9f); verticalLineTo(4f)
            horizontalLineTo(15f); verticalLineTo(10f); horizontalLineTo(19f); close()
        }
    }
}.build()

@Composable
internal fun SettingsManagement(
    onExport: () -> String,
    onImport: (String) -> Boolean,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val backup = pendingExport
        pendingExport = null
        if (uri == null || backup == null) return@rememberLauncherForActivityResult
        message = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup) }
                ?: error("Could not open the selected file.")
            "Backup exported successfully."
        }.getOrElse { "Export failed: ${it.message ?: "Unable to write file."}" }
    }
    val importPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not open the selected file.")
        }.getOrElse {
            message = "Import failed: ${it.message ?: "Unable to read file."}"
            return@rememberLauncherForActivityResult
        }
        pendingImport = content
    }

    Column(modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        .padding(20.dp).padding(bottom = bottomContentPadding)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Export", "Import").forEach { action ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (action == "Export") {
                            pendingExport = onExport()
                            exportPicker.launch("cloan-backup.txt")
                        } else {
                            importPicker.launch(arrayOf("text/*", "application/octet-stream"))
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tint = MaterialTheme.colorScheme.primary, imageVector = settingsActionIcon(action), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(action, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    pendingImport?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace app data?") },
            text = { Text("Importing this backup replaces all current clients, loans, payments, and default penalty settings.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    message = if (onImport(content)) "Backup imported successfully." else "Import failed: the selected file is invalid or incompatible."
                }) { Text("Replace data") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun ClientManagement(
    clients: List<Client>,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    onSaveClient: (Client, Int?) -> Unit,
    onToggleClientArchived: (Client) -> Unit,
    onHardDeleteClient: (Int) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            searchFocus.requestFocus()
            onSearchFocused()
        }
    }
    var statusFilter by remember { mutableStateOf("All status") }
    var recordFilter by remember { mutableStateOf("Current") }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var showCreate by remember { mutableStateOf(false) }
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

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_clients), style = MaterialTheme.typography.headlineMedium)
            }
            LedgerButton(onClick = { editingClient = null; showCreate = true }) { Text("+ Add") }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(colors = ledgerFieldColors(),
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_clients)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            shape = MaterialTheme.shapes.small,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var statusMenuOpen by remember { mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                MenuSelector(statusFilter, onClick = { statusMenuOpen = true })
                DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                    (listOf("All status") + ClientStatus.entries.map { it.label }).forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = { statusFilter = status; statusMenuOpen = false })
                    }
                }
            }
            listOf("Current", "Archived").forEach { filter ->
                LedgerFilter(filter, selected = recordFilter == filter, onClick = { recordFilter = filter })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${visibleClients.size} ${if (visibleClients.size == 1) "client" else "clients"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (visibleClients.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    LedgerEmptyIcon(R.drawable.ic_clients)
                    Text("No clients found", style = MaterialTheme.typography.titleMedium)
                    Text("Try another search or add a client.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleClients, key = { it.id }) { client ->
                    ClientCard(
                        client = client,
                        onEdit = { editingClient = client },
                        onArchive = { onToggleClientArchived(client) },
                        onHardDelete = { onHardDeleteClient(client.id) },
                    )
                }
            }
        }
    }

    if (showCreate || editingClient != null) {
        ClientEditorDialog(
            client = editingClient,
            nextClientId = clients.maxOfOrNull { it.id }?.plus(1) ?: 1,
            onDismiss = { showCreate = false; editingClient = null },
            onSave = { saved ->
                onSaveClient(saved, editingClient?.id)
                showCreate = false
                editingClient = null
            },
        )
    }
}

@Composable
internal fun ClientCard(client: Client, onEdit: () -> Unit, onArchive: () -> Unit, onHardDelete: () -> Unit) {
    var deleteVerificationOpen by remember(client.id) { mutableStateOf(false) }
    val actionsWidth = 228.dp
    val actionsWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionsWidth.toPx() }
    var offsetX by remember(client.id) { mutableFloatStateOf(0f) }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).width(actionsWidth).height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onEdit(); offsetX = 0f }) { Text("Edit") }
            TextButton(onClick = { onArchive(); offsetX = 0f }) { Text(if (client.archived) "Restore" else "Archive") }
            TextButton(onClick = { deleteVerificationOpen = true }) { Text("Delete") }
        }
        Card(
            modifier = Modifier
                .width(maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { (-offsetX).toDp() })
                .align(Alignment.CenterStart)
                .pointerInput(client.id, actionsWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-actionsWidthPx, 0f)
                        },
                        onDragEnd = { offsetX = if (offsetX < -actionsWidthPx / 2) -actionsWidthPx else 0f },
                        onDragCancel = { offsetX = if (offsetX < -actionsWidthPx / 2) -actionsWidthPx else 0f },
                    )
                },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (client.documents.isEmpty()) {
                        Icon(tint = MaterialTheme.colorScheme.primary, imageVector = baselinePersonIcon, contentDescription = "Default client photo", modifier = Modifier.size(24.dp))
                    } else {
                        Text(client.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
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
            if (client.emergencyContactName.isNotBlank() || client.emergencyContactNumber.isNotBlank()) {
                Text("Emergency contact: ${client.emergencyContactName} · ${client.emergencyContactNumber}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(client.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (client.documents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("📎 ${client.documents.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    }
    if (deleteVerificationOpen) {
        AlertDialog(
            onDismissRequest = { deleteVerificationOpen = false },
            title = { Text("Permanently delete client?") },
            text = { Text("This permanently deletes ${client.name}, their loans, and payment records.") },
            confirmButton = {
                TextButton(onClick = { onHardDelete(); deleteVerificationOpen = false }) {
                    Text("Permanently delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteVerificationOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun StatusBadge(status: ClientStatus) {
    val (background, foreground) = when (status) {
        ClientStatus.Active -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ClientStatus.Inactive -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ClientStatus.Blacklisted -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Text(status.label, modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
}

@Composable
internal fun ClientEditorDialog(client: Client?, nextClientId: Int, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by remember(client) { mutableStateOf(client?.name.orEmpty()) }
    var birthdate by remember(client) { mutableStateOf(client?.birthdate.orEmpty()) }
    var contact by remember(client) { mutableStateOf(client?.contactNumber.orEmpty()) }
    var address by remember(client) { mutableStateOf(client?.address.orEmpty()) }
    var emergencyName by remember(client) { mutableStateOf(client?.emergencyContactName.orEmpty()) }
    var emergencyNumber by remember(client) { mutableStateOf(client?.emergencyContactNumber.orEmpty()) }
    var status by remember(client) { mutableStateOf(client?.status ?: ClientStatus.Active) }
    val documents = remember(client) { mutableStateListOf<String>().apply { addAll(client?.documents.orEmpty()) } }
    val context = LocalContext.current
    var documentPickerError by remember { mutableStateOf<String?>(null) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val clientId = client?.id ?: nextClientId
        uris.forEach { uri ->
            runCatching { copyClientDocument(context, uri, clientId, name, documents.size + 1) }
                .onSuccess { documents.add(it) }
                .onFailure { documentPickerError = "Could not add one of the selected files." }
        }
    }
    var statusMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (client == null) "Add client" else "Edit client") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personal details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                EditorField("Full name *", name) { name = it }
                DatePickerField("Birthdate", birthdate) { birthdate = it }
                EditorField("Contact number *", contact) { contact = it }
                EditorField("Address", address) { address = it }
                EditorField("Emergency contact name", emergencyName) { emergencyName = it }
                EditorField("Emergency contact number", emergencyNumber) { emergencyNumber = it }
                Text("Client status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Box {
                    MenuSelector(status.label, onClick = { statusMenuOpen = true })
                    DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                        ClientStatus.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { status = option; statusMenuOpen = false }) }
                    }
                }
                Text("ID photos and documents", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("You can save the client without attaching documents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (documents.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(tint = MaterialTheme.colorScheme.primary, imageVector = baselinePersonIcon, contentDescription = "Default client photo preview", modifier = Modifier.size(42.dp))
                        Text("Default photo preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { documentPicker.launch(arrayOf("*/*")) }) { Text("Choose documents") }
                Text("Files are saved as ${client?.id ?: nextClientId}_${documentNameSlug(name)}_N.extension", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                documentPickerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                documents.forEachIndexed { index, document ->
                    AssistChip(onClick = { documents.removeAt(index) }, label = { Text("📎 $document  ×") })
                }
            }
        },
        confirmButton = {
            LedgerButton(onClick = {
                onSave(Client(client?.id ?: nextClientId, name.trim(), birthdate.trim(), contact.trim(), address.trim(), emergencyName.trim(), emergencyNumber.trim(), status, documents.toList(), client?.archived ?: false))
            }, enabled = name.isNotBlank() && contact.isNotBlank()) { Text("Save client") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun copyClientDocument(context: Context, uri: Uri, clientId: Int, clientName: String, sequence: Int): String {
    val originalName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }.orEmpty()
    val extension = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    val directory = File(context.filesDir, "client_documents").apply { mkdirs() }
    var fileSequence = sequence
    var destination = File(directory, "${clientId}_${documentNameSlug(clientName)}_${fileSequence}$extension")
    while (destination.exists()) {
        fileSequence++
        destination = File(directory, "${clientId}_${documentNameSlug(clientName)}_${fileSequence}$extension")
    }
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Unable to read selected file" }.use { source -> destination.outputStream().use(source::copyTo) }
    }
    return destination.name
}

private fun documentNameSlug(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "client" }

@Composable
internal fun EditorField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(colors = ledgerFieldColors(), value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = label != "Address")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DatePickerField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(colors = ledgerFieldColors(),
        value = value,
        onValueChange = {},
        modifier = modifier.fillMaxWidth().clickable { pickerOpen = true },
        label = { Text(label) },
        singleLine = true,
        readOnly = true,
        trailingIcon = { IconButton(onClick = { pickerOpen = true }) { Icon(painterResource(R.drawable.ic_calendar), stringResource(R.string.choose_date, label)) } },
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
