import SwiftUI

struct ContentView: View {
    @StateObject private var store = AppStore()

    var body: some View {
        TabView {
            DashboardScreen(store: store)
                .tabItem { Label("Overview", systemImage: "square.grid.2x2") }
            ClientScreen(store: store)
                .tabItem { Label("Clients", systemImage: "person.2") }
            LoanScreen(store: store)
                .tabItem { Label("Loans", systemImage: "doc.text") }
            PaymentScreen(store: store)
                .tabItem { Label("Payments", systemImage: "creditcard") }
            SettingsScreen(store: store)
                .tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
        }
        .tint(.ledgerAccent)
        .buttonBorderShape(.roundedRectangle(radius: 12))
    }
}

extension Color {
    static let ledgerForest = Color(red: 0.08, green: 0.24, blue: 0.21)
    static let ledgerCitron = Color(red: 0.85, green: 0.95, blue: 0.60)
    static let ledgerAccent = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.64, green: 0.84, blue: 0.73, alpha: 1)
            : UIColor(red: 0.13, green: 0.34, blue: 0.28, alpha: 1)
    })
    static let ledgerCanvas = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.06, green: 0.10, blue: 0.08, alpha: 1)
            : UIColor(red: 0.96, green: 0.96, blue: 0.93, alpha: 1)
    })
    static let ledgerSurface = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.11, green: 0.16, blue: 0.13, alpha: 1)
            : UIColor(red: 1, green: 0.996, blue: 0.98, alpha: 1)
    })
}

struct LedgerFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 10))
            .overlay {
                RoundedRectangle(cornerRadius: 10).strokeBorder(Color.ledgerAccent.opacity(0.25))
            }
    }
}

extension View {
    func ledgerPage() -> some View {
        scrollContentBackground(.hidden)
            .background(Color.ledgerCanvas)
            .toolbarBackground(Color.ledgerSurface, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
            .textFieldStyle(LedgerFieldStyle())
    }
}
