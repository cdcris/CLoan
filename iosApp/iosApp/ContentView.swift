import SwiftUI

struct ContentView: View {
    @StateObject private var store = AppStore()
    @State private var selection = LedgerTab.overview
    @State private var searchingClients = false

    var body: some View {
        TabView(selection: $selection) {
            DashboardScreen(store: store, onFindClient: {
                selection = .clients
                searchingClients = true
            }, onViewLoans: { selection = .loans }, onSettings: { selection = .settings })
                .ledgerTab(.overview)
            ClientScreen(store: store, searching: $searchingClients).ledgerTab(.clients)
            LoanScreen(store: store).ledgerTab(.loans)
            PaymentScreen(store: store).ledgerTab(.payments)
            SettingsScreen(store: store).ledgerTab(.settings)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ViewThatFits(in: .horizontal) {
                dock(showLabel: true)
                dock(showLabel: false)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
        }
        .tint(.ledgerAccent)
        .buttonBorderShape(.roundedRectangle(radius: 12))
    }

    private func dock(showLabel: Bool) -> some View {
        HStack(spacing: 2) {
            ForEach(LedgerTab.allCases, id: \.self) { tab in
                let selected = tab == selection
                Button {
                    selection = tab
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: tab.icon).font(.body.weight(.medium))
                        if selected && showLabel { Text(tab.title).font(.caption.weight(.semibold)) }
                    }
                    .padding(.horizontal, 8)
                    .frame(minWidth: 46, minHeight: 46)
                    .foregroundStyle(selected ? Color.white : Color.primary)
                    .background(selected ? Color.ledgerInk : Color.clear, in: RoundedRectangle(cornerRadius: 15))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(tab.title))
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .fixedSize(horizontal: true, vertical: false)
        .padding(5)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.13), radius: 6, y: 3)
    }
}

private enum LedgerTab: CaseIterable {
    case overview, clients, loans, payments, settings

    var title: LocalizedStringKey {
        switch self {
        case .overview: "Overview"
        case .clients: "Clients"
        case .loans: "Loans"
        case .payments: "Payments"
        case .settings: "Settings"
        }
    }

    var icon: String {
        switch self {
        case .overview: "square.grid.2x2"
        case .clients: "person.2"
        case .loans: "doc.text"
        case .payments: "creditcard"
        case .settings: "slider.horizontal.3"
        }
    }
}

private extension View {
    func ledgerTab(_ tab: LedgerTab) -> some View {
        tag(tab).tabItem { Label(tab.title, systemImage: tab.icon) }.toolbar(.hidden, for: .tabBar)
    }
}

extension Color {
    static let ledgerInk = Color(red: 35 / 255.0, green: 34 / 255.0, blue: 32 / 255.0)
    static let ledgerBrown = Color(red: 147 / 255.0, green: 85 / 255.0, blue: 57 / 255.0)
    static let ledgerAccent = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 240 / 255.0, green: 182 / 255.0, blue: 154 / 255.0, alpha: 1)
            : UIColor(red: 147 / 255.0, green: 85 / 255.0, blue: 57 / 255.0, alpha: 1)
    })
    static let ledgerCanvas = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 23 / 255.0, green: 22 / 255.0, blue: 20 / 255.0, alpha: 1) : .white
    })
    static let ledgerSurface = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(white: 45 / 255.0, alpha: 1)
            : UIColor(red: 246 / 255.0, green: 244 / 255.0, blue: 241 / 255.0, alpha: 1)
    })
}

struct LedgerFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 12))
    }
}

extension View {
    func ledgerPage() -> some View {
        scrollContentBackground(.hidden)
            .background(Color.ledgerCanvas)
            .textFieldStyle(LedgerFieldStyle())
    }
}

struct LedgerFilter: View {
    let title: LocalizedStringKey
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .foregroundStyle(selected ? Color.white : Color.primary)
                .background(selected ? Color.ledgerBrown : Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 11))
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct LedgerEmptyState: View {
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    let icon: String

    var body: some View {
        VStack(spacing: 7) {
            Image(systemName: icon)
                .foregroundStyle(Color.ledgerAccent)
                .frame(width: 48, height: 48)
                .background(Color.ledgerAccent.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            Text(title).font(.headline)
            Text(detail).font(.subheadline).foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity)
        .listRowBackground(Color.ledgerSurface)
    }
}
