import Foundation
import SwiftUI
import Shared

struct LoanScreen: View {
    @ObservedObject var store: AppStore
    @State private var editingLoan: Loan?
    @State private var showingEditor = false
    @State private var releasingLoan: Loan?

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.state.loans, id: \.id) { loan in
                    VStack(alignment: .leading, spacing: 6) {
                        NavigationLink {
                            LoanDetailsScreen(loanId: loan.id, store: store)
                        } label: {
                            HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(clientName(for: loan, in: store.state.clients)).font(.headline)
                                Text("Loan #\(loan.id) · \(loan.frequency.label) · \(loan.term) payments")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text(money(loan.principal)).font(.headline)
                                Text(loan.status.label).font(.caption).foregroundStyle(.secondary)
                            }
                            }
                        }
                        HStack {
                            Button("Edit") { editingLoan = loan; showingEditor = true }
                            Menu("Status") {
                                ForEach(["Pending", "Approved", "Released", "Active", "Fully Paid", "Cancelled"], id: \.self) { status in
                                    Button(status) { store.viewModel.updateLoanStatus(loanId: loan.id, statusLabel: status) }
                                }
                            }
                            if loan.status.label == "Approved" {
                                Button("Record release") { releasingLoan = loan }
                            }
                        }
                        .font(.subheadline)
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Loans")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editingLoan = nil; showingEditor = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingEditor) {
                LoanEditor(existing: editingLoan, clients: store.state.clients) { values in
                    store.saveLoan(existing: editingLoan, clientId: values.clientId, principal: values.principal,
                                   annualRate: values.annualRate, term: values.term, startDate: values.startDate,
                                   frequency: values.frequency, penaltyMode: values.penaltyMode,
                                   penaltyRate: values.penaltyRate)
                }
            }
            .sheet(isPresented: Binding(
                get: { releasingLoan != nil },
                set: { if !$0 { releasingLoan = nil } }
            )) {
                if let loan = releasingLoan {
                    ReleaseEditor(loan: loan) { date, amount, method, reference in
                        store.viewModel.releaseLoan(loanId: loan.id, date: date, amount: amount,
                                                    method: method, reference: reference)
                    }
                }
            }
        }
    }
}

struct LoanDetailsScreen: View {
    let loanId: Int32
    @ObservedObject var store: AppStore
    @State private var showingPenaltyEditor = false
    @State private var penaltyValue = ""

    var body: some View {
        if let loan = store.state.loans.first(where: { $0.id == loanId }) {
            List {
                Section("Account") {
                    LabeledContent("Client", value: clientName(for: loan, in: store.state.clients))
                    LabeledContent("Principal", value: money(loan.principal))
                    LabeledContent("Status", value: loan.status.label)
                    LabeledContent("Start date", value: loan.startDate)
                    LabeledContent("Penalty balance", value: money(loan.penaltyBalance))
                    Button("Update penalty balance") {
                        penaltyValue = String(loan.penaltyBalance)
                        showingPenaltyEditor = true
                    }
                }
                Section("Repayment schedule") {
                    ForEach(store.viewModel.loanInstallments(loanId: loanId), id: \.installment.number) { installmentStatus in
                        HStack {
                            VStack(alignment: .leading) {
                                Text("Payment \(installmentStatus.installment.number) · \(installmentStatus.installment.dueDate)")
                                Text(installmentStatus.isOverdue ? "Overdue · \(installmentStatus.daysOverdue) days" : "Scheduled")
                                    .font(.caption)
                                    .foregroundStyle(installmentStatus.isOverdue ? Color.red : Color.secondary)
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text(money(installmentStatus.unpaid))
                                Text("Penalty: \(money(installmentStatus.penaltyOutstanding))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Loan #\(loan.id)")
            .alert("Update penalty balance", isPresented: $showingPenaltyEditor) {
                TextField("Balance", text: $penaltyValue).keyboardType(.decimalPad)
                Button("Save") {
                    if let balance = Double(penaltyValue), balance >= 0 {
                        store.viewModel.updateLoanPenaltyBalance(loanId: loanId, balance: balance)
                    }
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Payments apply to penalties before interest and principal.")
            }
        } else {
            Text("Loan not found")
                .foregroundStyle(.secondary)
        }
    }
}

struct LoanEditor: View {
    struct Values {
        let clientId: Int32
        let principal: Double
        let annualRate: Double
        let term: Int32
        let startDate: String
        let frequency: String
        let penaltyMode: String
        let penaltyRate: Double
    }

    let existing: Loan?
    let clients: [Client]
    let onSave: (Values) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var clientId: Int32
    @State private var principal: String
    @State private var annualRate: String
    @State private var term: String
    @State private var startDate: String
    @State private var frequency: String
    @State private var penaltyMode: String
    @State private var penaltyRate: String

    init(existing: Loan?, clients: [Client], onSave: @escaping (Values) -> Void) {
        self.existing = existing
        self.clients = clients
        self.onSave = onSave
        _clientId = State(initialValue: existing?.clientId ?? clients.first?.id ?? 0)
        _principal = State(initialValue: existing.map { String($0.principal) } ?? "")
        _annualRate = State(initialValue: existing.map { String($0.annualInterestRate) } ?? "")
        _term = State(initialValue: existing.map { String($0.term) } ?? "12")
        _startDate = State(initialValue: existing?.startDate ?? todayIso())
        _frequency = State(initialValue: existing?.frequency.label ?? "Monthly")
        _penaltyMode = State(initialValue: existing?.penaltyRule?.mode.label ?? "Default")
        _penaltyRate = State(initialValue: existing?.penaltyRule.map { String($0.rate) } ?? "5")
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Client", selection: $clientId) {
                    ForEach(clients, id: \.id) { client in Text(client.name).tag(client.id) }
                }
                TextField("Principal", text: $principal).keyboardType(.decimalPad)
                TextField("Annual interest rate (%)", text: $annualRate).keyboardType(.decimalPad)
                TextField("Number of payments", text: $term).keyboardType(.numberPad)
                TextField("Start date (YYYY-MM-DD)", text: $startDate)
                Picker("Frequency", selection: $frequency) {
                    ForEach(["Weekly", "Semi-monthly", "Monthly"], id: \.self) { Text($0) }
                }
                Picker("Penalty rule", selection: $penaltyMode) {
                    ForEach(["Default", "Flat amount per overdue installment", "Percentage of unpaid installment"], id: \.self) { Text($0) }
                }
                if penaltyMode != "Default" {
                    TextField(penaltyMode == "Flat amount per overdue installment" ? "Penalty amount" : "Penalty rate (%)", text: $penaltyRate)
                        .keyboardType(.decimalPad)
                }
            }
            .navigationTitle(existing == nil ? "New loan" : "Edit loan")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let principal = Double(principal), let rate = Double(annualRate),
                              let term = Int32(term), clients.contains(where: { $0.id == clientId }) else { return }
                        onSave(Values(clientId: clientId, principal: principal, annualRate: rate, term: term,
                                      startDate: startDate, frequency: frequency, penaltyMode: penaltyMode,
                                      penaltyRate: Double(penaltyRate) ?? 0))
                        dismiss()
                    }
                    .disabled(Double(principal) == nil || Double(annualRate) == nil || Int32(term) == nil || clients.isEmpty)
                }
            }
        }
    }
}

struct ReleaseEditor: View {
    let loan: Loan
    let onSave: (String, Double, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = todayIso()
    @State private var amount: String
    @State private var method = "Cash"
    @State private var reference = ""

    init(loan: Loan, onSave: @escaping (String, Double, String, String) -> Void) {
        self.loan = loan
        self.onSave = onSave
        _amount = State(initialValue: String(loan.principal))
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Release date", text: $date)
                TextField("Release amount", text: $amount).keyboardType(.decimalPad)
                TextField("Method", text: $method)
                TextField("Reference", text: $reference)
            }
            .navigationTitle("Record release")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let amount = Double(amount) else { return }
                        onSave(date, amount, method, reference)
                        dismiss()
                    }
                    .disabled(Double(amount) == nil)
                }
            }
        }
    }
}
