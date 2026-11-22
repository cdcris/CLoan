import Foundation
import SwiftUI
import Shared

struct LoanScreen: View {
    @ObservedObject var store: AppStore
    @State private var editingLoan: Loan?
    @State private var showingEditor = false
    @State private var releasingLoan: Loan?
    @State private var deletingLoan: Loan?

    var body: some View {
        NavigationStack {
            List {
                if store.state.loans.isEmpty {
                    LedgerEmptyState(title: "No loans yet", detail: "Create a loan to see its repayment schedule.", icon: "doc.text")
                }
                ForEach(store.state.loans, id: \.id) { loan in
                    VStack(alignment: .leading, spacing: 6) {
                        NavigationLink {
                            LoanDetailsScreen(loanId: loan.id, store: store)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(clientName(for: loan, in: store.state.clients)).font(.headline)
                                    Text("Loan #\(loan.id) · \(loan.loanType.duration.label)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                VStack(alignment: .trailing) {
                                    Text(money(loan.principal)).font(.headline).monospacedDigit()
                                    Text(loan.status.label).font(.caption).foregroundStyle(Color.ledgerAccent)
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
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                    .listRowBackground(Color.ledgerSurface)
                    .swipeActions {
                        Button(role: .destructive) {
                            deletingLoan = loan
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .ledgerPage()
            .navigationTitle("Loans")
            .alert("Permanently delete loan?", isPresented: Binding(
                get: { deletingLoan != nil },
                set: { if !$0 { deletingLoan = nil } }
            ), presenting: deletingLoan) { loan in
                Button("Permanently delete", role: .destructive) {
                    store.viewModel.hardDeleteLoan(loanId: loan.id)
                    deletingLoan = nil
                }
                Button("Cancel", role: .cancel) { deletingLoan = nil }
            } message: { loan in
                Text("This permanently deletes the loan and all its payment records.")
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("New loan", systemImage: "plus") { editingLoan = nil; showingEditor = true }
                        .buttonStyle(.borderedProminent).tint(.ledgerBrown)
                }
            }
            .sheet(isPresented: $showingEditor) {
                LoanEditor(existing: editingLoan, clients: store.state.clients, defaultRule: store.state.defaultPenaltyRule) { values in
                    store.saveLoan(existing: editingLoan, clientId: values.clientId, principal: values.principal,
                                   loanType: values.loanType, startDate: values.startDate)
                }
            }
            .sheet(isPresented: Binding(
                get: { releasingLoan != nil },
                set: { if !$0 { releasingLoan = nil } }
            )) {
                if let loan = releasingLoan {
                    ReleaseEditor(loan: loan) { date, amount, method in
                        store.viewModel.releaseLoan(loanId: loan.id, date: date, amount: amount,
                                                    method: method)
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
    @State private var showSchedule = false

    var body: some View {
        if let loan = store.state.loans.first(where: { $0.id == loanId }) {
            List {
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Principal").font(.subheadline).foregroundStyle(.secondary)
                        Text(money(loan.principal)).font(.title.bold()).minimumScaleFactor(0.7)
                    }
                    Spacer()
                    Image(decorative: "PortfolioLedger").resizable().scaledToFit().frame(maxWidth: 118, maxHeight: 118)
                }
                .padding(.vertical, 14)
                .listRowBackground(Color.ledgerSurface)
                .listRowSeparator(.hidden)
                VStack(alignment: .leading, spacing: 8) {
                    Text(clientName(for: loan, in: store.state.clients)).font(.title2.bold())
                    Text(loan.status.label).font(.subheadline).foregroundStyle(Color.ledgerAccent)
                    HStack(spacing: 8) {
                        LedgerFilter(title: "Overview", selected: !showSchedule) { showSchedule = false }
                        LedgerFilter(title: "Schedule", selected: showSchedule) { showSchedule = true }
                    }
                }
                .listRowBackground(Color.ledgerCanvas)
                .listRowSeparator(.hidden)
                if !showSchedule {
                    Section("Account") {
                        LabeledContent("Start date", value: loan.startDate)
                        LabeledContent("Penalty balance", value: money(loan.penaltyBalance))
                    }
                } else {
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
            }
            .ledgerPage()
            .navigationTitle("Loan #\(loan.id)")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                Button {
                    penaltyValue = String(loan.penaltyBalance)
                    showingPenaltyEditor = true
                } label: {
                    Text("Update penalty balance").frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.borderedProminent)
                .tint(.ledgerBrown)
                .padding(.horizontal, 20)
            }
            .alert("Update penalty balance", isPresented: $showingPenaltyEditor) {
                TextField("Balance *", text: $penaltyValue).keyboardType(.decimalPad)
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
        let loanType: LoanType
        let startDate: String
    }

    let existing: Loan?
    let clients: [Client]
    let onSave: (Values) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var clientId: Int32
    @State private var principal: String
    @State private var interestRate: String
    @State private var customDays: String
    @State private var startDate: String
    @State private var duration: LoanDuration
    @State private var penaltyMode: PenaltyMode
    @State private var penaltyRate: String

    init(existing: Loan?, clients: [Client], defaultRule: PenaltyRule, onSave: @escaping (Values) -> Void) {
        self.existing = existing
        self.clients = clients
        self.onSave = onSave
        _clientId = State(initialValue: existing?.clientId ?? clients.first?.id ?? 0)
        _principal = State(initialValue: existing.map { String($0.principal) } ?? "")
        _interestRate = State(initialValue: existing.map { String($0.loanType.interestRate) } ?? "")
        _customDays = State(initialValue: existing.map { String($0.loanType.customDurationDays) } ?? "30")
        _startDate = State(initialValue: existing?.startDate ?? todayIso())
        _duration = State(initialValue: existing?.loanType.duration ?? .monthly)
        let rule = existing?.loanType.penaltyRule ?? defaultRule
        _penaltyMode = State(initialValue: rule.mode)
        _penaltyRate = State(initialValue: String(rule.rate))
    }

    private var values: Values? {
        guard clients.contains(where: { $0.id == clientId }), let amount = Double(principal), amount > 0,
              let interest = Double(interestRate), interest >= 0, let penalty = Double(penaltyRate), penalty >= 0
        else { return nil }
        let days = Int32(customDays) ?? 0
        guard duration != .custom || days > 0 else { return nil }
        let rule = PenaltyRule(mode: penaltyMode, rate: penalty)
        let type = LoanType(duration: duration, interestRate: interest, penaltyRule: rule, customDurationDays: max(1, days))
        return Values(clientId: clientId, principal: amount, loanType: type, startDate: startDate)
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Client *", selection: $clientId) {
                    ForEach(clients, id: \.id) { client in Text(client.name).tag(client.id) }
                }
                TextField("Principal *", text: $principal).keyboardType(.decimalPad)
                TextField("Interest rate (%) *", text: $interestRate).keyboardType(.decimalPad)
                TextField("Start date (YYYY-MM-DD) *", text: $startDate)
                Picker("Duration", selection: $duration) {
                    ForEach([LoanDuration.fifteenDays, .monthly, .oneYear, .custom], id: \.label) { Text($0.label).tag($0) }
                }
                if duration == .custom {
                    TextField("Duration in days", text: $customDays).keyboardType(.numberPad)
                }
                Picker("Penalty rule", selection: $penaltyMode) {
                    ForEach([PenaltyMode.flat, .percentage], id: \.label) { Text($0.label).tag($0) }
                }
                TextField(penaltyMode == .flat ? "Penalty amount *" : "Penalty rate (%) *", text: $penaltyRate)
                    .keyboardType(.decimalPad)
            }
            .ledgerPage()
            .navigationTitle(existing == nil ? "New loan" : "Edit loan")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let values else { return }
                        onSave(values)
                        dismiss()
                    }
                    .disabled(values == nil)
                }
            }
        }
    }
}

struct ReleaseEditor: View {
    let loan: Loan
    let onSave: (String, Double, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = todayIso()
    @State private var amount: String
    @State private var method = "Cash"

    init(loan: Loan, onSave: @escaping (String, Double, String) -> Void) {
        self.loan = loan
        self.onSave = onSave
        _amount = State(initialValue: String(loan.principal))
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Release date *", text: $date)
                TextField("Release amount *", text: $amount).keyboardType(.decimalPad)
                TextField("Method *", text: $method)
            }
            .ledgerPage()
            .navigationTitle("Record release")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let amount = Double(amount) else { return }
                        onSave(date, amount, method)
                        dismiss()
                    }
                    .disabled(Double(amount) == nil)
                }
            }
        }
    }
}
