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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
