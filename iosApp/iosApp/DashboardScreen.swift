import Foundation
import SwiftUI
import Shared

struct DashboardScreen: View {
    @ObservedObject var store: AppStore
    @State private var reportType = "Collection report"
    @State private var reportFrom = todayIso()
    @State private var reportTo = todayIso()
    @State private var reportResult = ""

    private func balanceDetail(_ title: LocalizedStringKey, _ amount: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(Color.ledgerCitron)
            Text(amount).font(.headline.monospacedDigit())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var eligibleLoans: [Loan] {
        store.state.loans.filter { ["Released", "Active", "Overdue"].contains($0.status.label) }
    }

    private var activeLoanCount: Int {
        eligibleLoans.filter { store.viewModel.remainingLoanBalance(loanId: $0.id) > 0.009 }.count
    }

    private var overdueLoanCount: Int {
        eligibleLoans.filter { loan in
            store.viewModel.loanInstallments(loanId: loan.id).contains { $0.isOverdue }
        }.count
    }

    private func dueLoanCount(from start: String, through end: String) -> Int {
        eligibleLoans.filter { loan in
            store.viewModel.loanInstallments(loanId: loan.id).contains { installment in
                installment.installment.dueDate >= start && installment.installment.dueDate <= end && installment.unpaid > 0.009
            }
        }.count
    }

    private func isoDate(daysFromToday: Int) -> String {
        let calendar = Calendar(identifier: .gregorian)
        let date = calendar.date(byAdding: .day, value: daysFromToday, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(todayIso())
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Outstanding balance")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(Color.ledgerCitron)
                        Text(money(store.state.loans.reduce(0) { $0 + store.viewModel.remainingLoanBalance(loanId: $1.id) }))
                            .font(.system(.largeTitle, design: .serif))
                        Divider().overlay(.white.opacity(0.3))
                        HStack(alignment: .top, spacing: 16) {
                            balanceDetail("Total released", money(store.state.loans.reduce(0) { $0 + ($1.releaseAmount?.doubleValue ?? 0) }))
                            balanceDetail("Total collected", money(store.state.payments.filter { $0.voidReason.isEmpty }.reduce(0) { $0 + $1.amount }))
                        }
                    }
                    .padding(22)
                    .foregroundStyle(.white)
                    .background {
                        ZStack(alignment: .topTrailing) {
                            Color.ledgerForest
                            Canvas { context, size in
                                for index in 0..<12 {
                                    let radius = CGFloat(32 + index * 10)
                                    let rect = CGRect(x: size.width * 1.08 - radius, y: size.height * 0.13 - radius,
                                                      width: radius * 2, height: radius * 2)
                                    context.stroke(Path(ellipseIn: rect), with: .color(.ledgerCitron.opacity(0.15)))
                                }
                            }
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 24))

                    Text("Portfolio activity").font(.system(.title3, design: .serif))
                    HStack(spacing: 10) {
                        DashboardMetricCard(title: "Total clients", value: "\(store.state.clients.count)")
                        DashboardMetricCard(title: "Active loans", value: "\(activeLoanCount)")
                    }
                    Text("Collection schedule").font(.system(.title3, design: .serif))
                    HStack(spacing: 10) {
                        DashboardMetricCard(title: "Due today", value: "\(dueLoanCount(from: todayIso(), through: todayIso())) loans")
                        DashboardMetricCard(title: "Due this week", value: "\(dueLoanCount(from: isoDate(daysFromToday: 1), through: isoDate(daysFromToday: 7))) loans")
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Overdue loans · \(overdueLoanCount)")
                            .font(.system(.title3, design: .serif))
                        let overdue = eligibleLoans.filter { loan in
                            store.viewModel.loanInstallments(loanId: loan.id).contains { $0.isOverdue }
                        }
                        if overdue.isEmpty {
                            Text("No loans are marked overdue.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(Array(overdue.enumerated()), id: \.element.id) { index, loan in
                                if index > 0 { Divider() }
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(clientName(for: loan, in: store.state.clients))
                                        .font(.subheadline.weight(.semibold))
                                    Text("Loan #\(loan.id) · \(money(loan.principal))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .dashboardCard()

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Reports")
                            .font(.system(.title3, design: .serif))
                        Text("Generate a PDF report for the selected date range.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Picker("Report", selection: $reportType) {
                            ForEach(["Collection report", "Loan release report", "Overdue / aging report"], id: \.self) { Text($0) }
                        }
                        .pickerStyle(.menu)
                        if reportType != "Overdue / aging report" {
                            TextField("From (YYYY-MM-DD)", text: $reportFrom)
                                .textFieldStyle(LedgerFieldStyle())
                        }
                        TextField(reportType == "Overdue / aging report" ? "As of (YYYY-MM-DD)" : "To (YYYY-MM-DD)", text: $reportTo)
                            .textFieldStyle(LedgerFieldStyle())
                        Button("Generate PDF") {
                            reportResult = store.viewModel.generateReportPdf(reportType: reportType, from: reportFrom, to: reportTo)
                        }
                        .buttonStyle(.borderedProminent)
                        if !reportResult.isEmpty {
                            Text(reportResult)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .dashboardCard()
                }
                .padding(16)
            }
            .ledgerPage()
            .navigationTitle("Overview")
        }
    }
}

private struct DashboardMetricCard: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(.title2, design: .serif))
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .leading)
        .padding(14)
        .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.10), lineWidth: 1)
        }
    }
}

private extension View {
    func dashboardCard() -> some View {
        padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.primary.opacity(0.10), lineWidth: 1)
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
    value.formatted(.currency(code: "PHP").precision(.fractionLength(0...2)))
}

func todayIso() -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: Date())
}
