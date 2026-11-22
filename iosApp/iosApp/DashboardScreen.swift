import Foundation
import SwiftUI
import Shared

struct DashboardScreen: View {
    @ObservedObject var store: AppStore
    let onFindClient: () -> Void
    let onViewLoans: () -> Void
    let onSettings: () -> Void
    private enum Activity { case accounts, collections, cashFlow }
    @State private var activity = Activity.accounts
    @State private var reportType = "Collection report"
    @State private var reportFrom = todayIso()
    @State private var reportTo = todayIso()
    @State private var reportResult = ""

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
                    HStack(spacing: 10) {
                        Image(systemName: "square.grid.2x2.fill")
                            .foregroundStyle(Color.ledgerBrown)
                            .frame(width: 38, height: 38)
                            .background(Color.ledgerBrown.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
                        Text("CLoan").font(.headline)
                        Spacer()
                        Label(todayIso(), systemImage: "calendar").font(.caption).foregroundStyle(.secondary)
                    }
                    Text("Portfolio overview")
                        .font(.largeTitle.bold())
                        .accessibilityAddTraits(.isHeader)
                    HStack(spacing: 8) {
                        Button(action: onFindClient) {
                            Label("Find a client", systemImage: "magnifyingglass")
                                .font(.subheadline)
                                .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                                .padding(.horizontal, 14)
                                .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 12))
                        }
                        Button(action: onSettings) {
                            Image(systemName: "slider.horizontal.3")
                                .frame(width: 50, height: 50)
                                .foregroundStyle(.white)
                                .background(Color.ledgerBrown, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .accessibilityLabel("Settings")
                    }
                    .buttonStyle(.plain)
                    balanceCard
                    Text("Portfolio activity").font(.title2.weight(.semibold))
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            LedgerFilter(title: "Accounts", selected: activity == .accounts) { activity = .accounts }
                            LedgerFilter(title: "Collections", selected: activity == .collections) { activity = .collections }
                            LedgerFilter(title: "Cash flow", selected: activity == .cashFlow) { activity = .cashFlow }
                        }
                    }
                    .scrollIndicators(.hidden)
                    HStack(alignment: .top, spacing: 10) {
                        switch activity {
                        case .accounts:
                            DashboardMetricCard(title: "Total clients", value: "\(store.state.clients.count)", icon: "person.2")
                            DashboardMetricCard(title: "Active loans", value: "\(activeLoanCount)", icon: "doc.text")
                        case .collections:
                            DashboardMetricCard(title: "Due today", value: "\(dueLoanCount(from: todayIso(), through: todayIso()))", icon: "calendar")
                            DashboardMetricCard(title: "Due this week", value: "\(dueLoanCount(from: isoDate(daysFromToday: 1), through: isoDate(daysFromToday: 7)))", icon: "calendar")
                        case .cashFlow:
                            DashboardMetricCard(title: "Total released", value: money(store.state.loans.reduce(0) { $0 + ($1.releaseAmount?.doubleValue ?? 0) }), icon: "doc.text")
                            DashboardMetricCard(title: "Total collected", value: money(store.state.payments.filter { $0.voidReason.isEmpty }.reduce(0) { $0 + $1.amount }), icon: "creditcard")
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Overdue loans · \(overdueLoanCount)")
                            .font(.system(.title3, design: .default, weight: .bold))
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
                        Label("Reports", systemImage: "doc.text")
                            .font(.system(.title3, design: .default, weight: .bold))
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
                        .tint(.ledgerBrown)
                        .foregroundStyle(.white)
                        if !reportResult.isEmpty {
                            Text(reportResult)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .dashboardCard()
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
            }
            .ledgerTabPage()
            .toolbar(.hidden, for: .navigationBar)
        }
    }
    private var balanceCard: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Outstanding balance").font(.subheadline.weight(.medium)).foregroundStyle(.white.opacity(0.85))
                Text(money(store.state.loans.reduce(0) { $0 + store.viewModel.remainingLoanBalance(loanId: $1.id) }))
                    .font(.largeTitle.bold()).monospacedDigit().minimumScaleFactor(0.65)
                Button("View loans", action: onViewLoans)
                    .buttonStyle(.borderedProminent)
                    .tint(.white)
                    .foregroundStyle(Color.ledgerInk)
                    .padding(.top, 4)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(decorative: "PortfolioLedger")
                .resizable().scaledToFit().frame(maxWidth: 118, maxHeight: 118)
        }
        .padding(18)
        .foregroundStyle(.white)
        .background(Color.ledgerInk, in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct DashboardMetricCard: View {
    let title: LocalizedStringKey
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: icon).foregroundStyle(Color.ledgerAccent)
                Text(value).font(.title2.bold()).monospacedDigit().minimumScaleFactor(0.7)
            }
            .padding(14)
            Text(title).font(.subheadline.weight(.medium))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .foregroundStyle(.white).background(Color.ledgerInk)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.ledgerSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }
}

private extension View {
    func dashboardCard() -> some View {
        padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.ledgerSurface, in: RoundedRectangle(cornerRadius: 16))
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
