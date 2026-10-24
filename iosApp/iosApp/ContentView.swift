import SwiftUI

struct ContentView: View {
    @StateObject private var store = AppStore()

    var body: some View {
        TabView {
            DashboardScreen(store: store)
                .tabItem { Label("Dashboard", systemImage: "square.grid.2x2") }
            ClientScreen(store: store)
                .tabItem { Label("Clients", systemImage: "person.2") }
            LoanScreen(store: store)
                .tabItem { Label("Loans", systemImage: "doc.text") }
            PaymentScreen(store: store)
                .tabItem { Label("Payments", systemImage: "creditcard") }
            SettingsScreen(store: store)
                .tabItem { Label("Settings", systemImage: "gear") }
        }
        .tint(.blue)
    }
}
