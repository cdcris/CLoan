package org.example.project

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.example.project.db.Database
import org.example.project.shared.R

private data class MenuItem(val title: Int, val icon: Int)

private val menuItems = listOf(
    MenuItem(R.string.nav_overview, R.drawable.ic_overview),
    MenuItem(R.string.nav_clients, R.drawable.ic_clients),
    MenuItem(R.string.nav_loans, R.drawable.ic_loans),
    MenuItem(R.string.nav_payments, R.drawable.ic_payments),
    MenuItem(R.string.nav_settings, R.drawable.ic_settings),
)

@Composable
fun App(database: Database) {
    LedgerTheme {
        val appViewModel: AppViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AppViewModel(database) }
            },
        )
        val state by appViewModel.uiState.collectAsStateWithLifecycle()
        var selectedIndex by rememberSaveable { mutableStateOf(0) }

        var focusClientSearch by rememberSaveable { mutableStateOf(false) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { LedgerDock(selectedIndex) { selectedIndex = it } },
        ) { contentPadding ->
            val direction = LocalLayoutDirection.current
            val page = Modifier.fillMaxSize().padding(
                start = contentPadding.calculateStartPadding(direction),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(direction),
            )
            val dockClearance = contentPadding.calculateBottomPadding()
            if (selectedIndex == 0) {
                DashboardManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    defaultPenaltyRule = state.defaultPenaltyRule,
                    onFindClient = { focusClientSearch = true; selectedIndex = 1 },
                    onViewLoans = { selectedIndex = 2 },
                    onSettings = { selectedIndex = 4 },
                    bottomContentPadding = dockClearance,
                    modifier = page,
                )
            } else if (selectedIndex == 1) {
                ClientManagement(
                    clients = state.clients,
                    focusSearch = focusClientSearch,
                    onSearchFocused = { focusClientSearch = false },
                    onSaveClient = appViewModel::saveClient,
                    onToggleClientArchived = appViewModel::toggleClientArchived,
                    onHardDeleteClient = appViewModel::hardDeleteClient,
                    bottomContentPadding = dockClearance,
                    modifier = page,
                )
            } else if (selectedIndex == 2) {
                LoanManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    defaultPenaltyRule = state.defaultPenaltyRule,
                    onSaveLoan = appViewModel::saveLoan,
                    onUpdateLoan = appViewModel::updateLoan,
                    onReleaseLoan = appViewModel::releaseLoan,
                    onRenewOrTopUpLoan = appViewModel::renewOrTopUpLoan,
                    onHardDeleteLoan = appViewModel::hardDeleteLoan,
                    bottomContentPadding = dockClearance,
                    modifier = page,
                )
            } else if (selectedIndex == 3) {
                PaymentManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    onSavePayment = appViewModel::savePayment,
                    onVoidPayment = appViewModel::voidPayment,
                    onHardDeletePayment = appViewModel::hardDeletePayment,
                    bottomContentPadding = dockClearance,
                    modifier = page,
                )
            } else {
                SettingsManagement(
                    onExport = appViewModel::exportBackup,
                    onImport = appViewModel::importBackup,
                    bottomContentPadding = dockClearance,
                    modifier = page,
                )
            }
        }
    }
}

@Composable
private fun LedgerDock(selectedIndex: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val showLabel = maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.3f
        Row(Modifier.selectableGroup().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            menuItems.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val title = stringResource(item.title)
                val background by animateColorAsState(if (selected) LedgerInk else LedgerInk.copy(alpha = 0.76f))
                Row(
                    Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium)
                        .background(background).selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(item.icon), if (selected && showLabel) null else title,
                        Modifier.size(21.dp), tint = Color.White)
                    if (selected && showLabel) Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
