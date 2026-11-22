package org.example.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.example.project.shared.R
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.example.project.db.Database

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

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    menuItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.title), style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { contentPadding ->
            if (selectedIndex == 0) {
                DashboardManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    defaultPenaltyRule = state.defaultPenaltyRule,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 1) {
                ClientManagement(
                    clients = state.clients,
                    onSaveClient = appViewModel::saveClient,
                    onToggleClientArchived = appViewModel::toggleClientArchived,
                    onHardDeleteClient = appViewModel::hardDeleteClient,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
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
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 3) {
                PaymentManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    onSavePayment = appViewModel::savePayment,
                    onVoidPayment = appViewModel::voidPayment,
                    onHardDeletePayment = appViewModel::hardDeletePayment,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else {
                SettingsManagement(
                    onExport = appViewModel::exportBackup,
                    onImport = appViewModel::importBackup,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            }
        }
    }
}
