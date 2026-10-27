import SwiftUI
import Shared

struct SettingsScreen: View {
    @ObservedObject var store: AppStore
    @State private var mode: String
    @State private var rate: String

    init(store: AppStore) {
        self.store = store
        _mode = State(initialValue: store.state.defaultPenaltyRule.mode.label)
        _rate = State(initialValue: String(store.state.defaultPenaltyRule.rate))
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Default penalty", selection: $mode) {
                    ForEach(["Flat amount per overdue installment", "Percentage of unpaid installment"], id: \.self) { Text($0) }
                }
                TextField(mode == "Flat amount per overdue installment" ? "Amount per installment *" : "Rate (%) *", text: $rate)
                    .keyboardType(.decimalPad)
                Button("Save default rule") {
                    guard let value = Double(rate), value >= 0 else { return }
                    store.updateDefaultPenalty(mode: mode, rate: value)
                }
                Section {
                    Text("Changes apply to existing loans that use the default penalty rule.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .onChange(of: store.state.defaultPenaltyRule.rate) { _, rate in self.rate = String(rate) }
            .onChange(of: store.state.defaultPenaltyRule.mode.label) { _, mode in self.mode = mode }
        }
    }
}
