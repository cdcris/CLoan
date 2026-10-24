package org.example.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.example.project.db.Database

internal data class MenuItem(val title: String, val symbol: String)

internal val menuItems = listOf(
    MenuItem("Dashboard", "⌂"),
    MenuItem("Client", "♙"),
    MenuItem("Loan", "▤"),
    MenuItem("Payment", "$"),
    MenuItem("Settings", "⚙"),
)

@Composable
fun App(database: Database) {
    MaterialTheme {
        val appViewModel: AppViewModel = viewModel(
            factory = viewModelFactory {
                initializer { AppViewModel(database) }
            },
        )
        val state by appViewModel.uiState.collectAsStateWithLifecycle()
        var selectedIndex by remember { mutableStateOf(0) }

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
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else if (selectedIndex == 3) {
                PaymentManagement(
                    clients = state.clients,
                    loans = state.loans,
                    payments = state.payments,
                    onSavePayment = appViewModel::savePayment,
                    onVoidPayment = appViewModel::voidPayment,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            } else {
                SettingsManagement(
                    currentRule = state.defaultPenaltyRule,
                    onRuleChange = appViewModel::updateDefaultPenaltyRule,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            }
        }
    }
}
