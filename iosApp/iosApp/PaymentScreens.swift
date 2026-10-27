import Foundation
import SwiftUI
import Shared

struct PaymentScreen: View {
    @ObservedObject var store: AppStore
    @State private var editingPayment: Payment?
    @State private var showingEditor = false
    @State private var receiptPayment: Payment?

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.state.payments.sorted { $0.date > $1.date }, id: \.id) { payment in
                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(clientName(for: payment, state: store.state)) · Loan #\(payment.loanId)").font(.headline)
                        Text("\(money(payment.amount)) · \(payment.date) · \(payment.method)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if !payment.voidReason.isEmpty { Text("Voided: \(payment.voidReason)").foregroundStyle(.red) }
                        Button("Receipt") { receiptPayment = payment }
                            .font(.caption)
                    }
                    .swipeActions {
                        if payment.voidReason.isEmpty {
                            Button("Void", role: .destructive) {
                                store.viewModel.voidPayment(payment: payment, reason: "Voided from iOS")
                            }
                        }
                        Button("Edit") { editingPayment = payment; showingEditor = true }
                            .tint(.blue)
                    }
                }
            }
            .navigationTitle("Payments")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editingPayment = nil; showingEditor = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingEditor) {
                PaymentEditor(existing: editingPayment, loans: store.state.loans) { values in
                    store.savePayment(existing: editingPayment, loanId: values.loanId, amount: values.amount,
                                      date: values.date, method: values.method, reference: values.reference)
                }
            }
            .sheet(isPresented: Binding(
                get: { receiptPayment != nil },
                set: { if !$0 { receiptPayment = nil } }
            )) {
                if let payment = receiptPayment {
                    ReceiptView(payment: payment, clientName: clientName(for: payment, state: store.state), store: store)
                }
            }
        }
    }
}

struct ReceiptView: View {
    let payment: Payment
    let clientName: String
    @ObservedObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var result = ""

    var body: some View {
        NavigationStack {
            List {
                LabeledContent("Receipt no.", value: "RC-\(String(format: "%06d", payment.id))")
                LabeledContent("Received from", value: clientName)
                LabeledContent("Amount", value: money(payment.amount))
                LabeledContent("Date", value: payment.date)
                LabeledContent("Method", value: payment.method)
                LabeledContent("Reference", value: payment.reference)
                LabeledContent("Applied to penalty", value: money(payment.allocatedPenalty))
                LabeledContent("Applied to interest", value: money(payment.allocatedInterest))
                LabeledContent("Applied to principal", value: money(payment.allocatedPrincipal))
                Button("Generate PDF") {
                    result = store.viewModel.generatePaymentReceiptPdf(paymentId: payment.id)
                }
                if !result.isEmpty { Text(result).font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("Official receipt")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } } }
        }
    }
}

struct PaymentEditor: View {
    struct Values {
        let loanId: Int32
        let amount: Double
        let date: String
        let method: String
        let reference: String
    }

    let existing: Payment?
    let loans: [Loan]
    let onSave: (Values) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var loanId: Int32
    @State private var amount: String
    @State private var date: String
    @State private var method: String
    @State private var reference: String

    init(existing: Payment?, loans: [Loan], onSave: @escaping (Values) -> Void) {
        self.existing = existing
        self.loans = loans
        self.onSave = onSave
        _loanId = State(initialValue: existing?.loanId ?? loans.first?.id ?? 0)
        _amount = State(initialValue: existing.map { String($0.amount) } ?? "")
        _date = State(initialValue: existing?.date ?? todayIso())
        _method = State(initialValue: existing?.method ?? "Cash")
        _reference = State(initialValue: existing?.reference ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Loan *", selection: $loanId) {
                    ForEach(loans, id: \.id) { loan in
                        Text("Loan #\(loan.id) · \(money(loan.principal))").tag(loan.id)
                    }
                }
                TextField("Amount *", text: $amount).keyboardType(.decimalPad)
                TextField("Date (YYYY-MM-DD) *", text: $date)
                TextField("Method *", text: $method)
                TextField("Reference *", text: $reference)
            }
            .navigationTitle(existing == nil ? "Record payment" : "Edit payment")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let amount = Double(amount), loans.contains(where: { $0.id == loanId }) else { return }
                        onSave(Values(loanId: loanId, amount: amount, date: date, method: method, reference: reference))
                        dismiss()
                    }
                    .disabled(Double(amount) == nil || loans.isEmpty)
                }
            }
        }
    }
}
