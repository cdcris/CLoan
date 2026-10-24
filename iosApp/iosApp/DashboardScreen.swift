import Foundation
import SwiftUI
import Shared

struct DashboardScreen: View {
    @ObservedObject var store: AppStore
    @State private var reportType = "Collection report"
    @State private var reportFrom = todayIso()
    @State private var reportTo = todayIso()
    @State private var reportResult = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Portfolio") {
                    LabeledContent("Clients", value: "\(store.state.clients.count)")
                    LabeledContent("Loans", value: "\(store.state.loans.count)")
                    LabeledContent("Payments", value: "\(store.state.payments.count)")
                    LabeledContent("Outstanding balance", value: money(store.state.loans.reduce(0) { $0 + store.viewModel.remainingLoanBalance(loanId: $1.id) }))
                    LabeledContent("Total released", value: money(store.state.loans.reduce(0) { $0 + ($1.releaseAmount?.doubleValue ?? 0) }))
                    LabeledContent("Total collected", value: money(store.state.payments.filter { $0.voidReason.isEmpty }.reduce(0) { $0 + $1.amount }))
                }
                Section("Overdue loans") {
                    let overdue = store.state.loans.filter { loan in
                        store.viewModel.loanInstallments(loanId: loan.id).contains { $0.isOverdue }
                    }
                    if overdue.isEmpty {
                        Text("No loans are marked overdue.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(overdue, id: \.id) { loan in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(clientName(for: loan, in: store.state.clients)).font(.headline)
                                Text("Loan #\(loan.id) · \(money(loan.principal))")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Section("Reports") {
                    Picker("Report", selection: $reportType) {
                        ForEach(["Collection report", "Loan release report", "Overdue / aging report"], id: \.self) { Text($0) }
                    }
                    if reportType != "Overdue / aging report" {
                        TextField("From (YYYY-MM-DD)", text: $reportFrom)
                    }
                    TextField(reportType == "Overdue / aging report" ? "As of (YYYY-MM-DD)" : "To (YYYY-MM-DD)", text: $reportTo)
                    Button("Generate PDF") {
                        reportResult = store.viewModel.generateReportPdf(reportType: reportType, from: reportFrom, to: reportTo)
                    }
                    if !reportResult.isEmpty { Text(reportResult).font(.footnote).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("CLoan")
        }
    }
}

func clientName(for loan: Loan, in clients: [Client]) -> String {
    clients.first(where: { $0.id == loan.clientId })?.name ?? "Unknown client"
}

func clientName(for payment: Payment, state: AppUiState) -> String {
    guard let loan = state.loans.first(where: { $0.id == payment.loanId }) else { return "Unknown client" }
    return clientName(for: loan, in: state.clients)
}

func money(_ value: Double) -> String {
    value.formatted(.currency(code: "PHP"))
}

func todayIso() -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: Date())
}
