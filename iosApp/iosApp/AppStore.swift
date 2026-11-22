import Foundation
import SwiftUI
import Shared

private final class SwiftAppStateListener: NSObject, AppStateListener {
    var onChange: ((AppUiState) -> Void)?

    func onStateChanged(state: AppUiState) {
        onChange?(state)
    }
}

@MainActor
final class AppStore: ObservableObject {
    let viewModel: AppViewModel
    @Published var state: AppUiState

    private let listener: SwiftAppStateListener
    private var subscription: AppStateSubscription?

    init() {
        let viewModel = AppDependenciesKt.createAppViewModel()
        self.viewModel = viewModel
        self.state = viewModel.currentState
        let listener = SwiftAppStateListener()
        self.listener = listener
        listener.onChange = { [weak self] state in
            Task { @MainActor in self?.state = state }
        }
        self.subscription = AppStateSubscription(viewModel: viewModel, listener: listener)
    }

    deinit {
        subscription?.cancel()
    }

    func saveClient(
        existing: Client?,
        name: String,
        birthdate: String,
        contact: String,
        address: String,
        emergencyContactName: String,
        emergencyContactNumber: String,
        documents: String,
        status: String
    ) {
        viewModel.saveClientForm(
            existingId: existing.map { KotlinInt(int: $0.id) },
            name: name,
            birthdate: birthdate,
            contactNumber: contact,
            address: address,
            emergencyContactName: emergencyContactName,
            emergencyContactNumber: emergencyContactNumber,
            documentsText: documents,
            statusLabel: status
        )
    }

    func saveLoan(
        existing: Loan?,
        clientId: Int32,
        principal: Double,
        loanType: LoanType,
        startDate: String
    ) {
        viewModel.saveLoanForm(
            existingId: existing.map { KotlinInt(int: $0.id) },
            clientId: clientId,
            principal: principal,
            loanType: loanType,
            startDate: startDate
        )
    }

    func savePayment(existing: Payment?, loanId: Int32, amount: Double, date: String, method: String, reference: String) {
        viewModel.savePaymentForm(
            existingId: existing.map { KotlinInt(int: $0.id) },
            loanId: loanId,
            amount: amount,
            date: date,
            method: method,
            reference: reference
        )
    }

    func updateDefaultPenalty(mode: String, rate: Double) {
        viewModel.updateDefaultPenaltyRuleForm(modeLabel: mode, rate: rate)
    }

    func exportBackup() -> String {
        viewModel.exportBackup()
    }

    func importBackup(_ content: String) -> Bool {
        viewModel.importBackup(content: content)
    }
}
