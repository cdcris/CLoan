import Foundation
import SwiftUI
import Shared

struct ClientScreen: View {
    @ObservedObject var store: AppStore
    @State private var editingClient: Client?
    @State private var showingEditor = false
    @State private var query = ""
    @State private var showingArchived = false

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.state.clients.filter { client in
                    client.archived == showingArchived &&
                    (query.isEmpty || client.name.localizedCaseInsensitiveContains(query) || client.contactNumber.localizedCaseInsensitiveContains(query))
                }, id: \.id) { client in
                    Button {
                        editingClient = client
                        showingEditor = true
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(client.name).font(.headline)
                            Text("\(client.contactNumber) · \(client.status.label)\(client.archived ? " · Archived" : "")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .swipeActions {
                        Button(client.archived ? "Restore" : "Archive") {
                            store.viewModel.toggleClientArchived(client: client)
                        }
                        .tint(client.archived ? .green : .orange)
                    }
                }
            }
            .navigationTitle("Clients")
            .searchable(text: $query, prompt: "Search name or contact")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(showingArchived ? "Current" : "Archived") { showingArchived.toggle() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editingClient = nil; showingEditor = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingEditor) {
                ClientEditor(existing: editingClient) { values in
                    store.saveClient(existing: editingClient, name: values.name, birthdate: values.birthdate,
                                     contact: values.contact, address: values.address,
                                     employerIncome: values.employerIncome, emergencyContact: values.emergencyContact,
                                     documents: values.documents, status: values.status)
                }
            }
        }
    }
}

struct ClientEditor: View {
    struct Values {
        let name: String
        let birthdate: String
        let contact: String
        let address: String
        let employerIncome: String
        let emergencyContact: String
        let documents: String
        let status: String
    }

    let existing: Client?
    let onSave: (Values) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var birthdate: String
    @State private var contact: String
    @State private var address: String
    @State private var employerIncome: String
    @State private var emergencyContact: String
    @State private var documents: String
    @State private var status: String

    init(existing: Client?, onSave: @escaping (Values) -> Void) {
        self.existing = existing
        self.onSave = onSave
        _name = State(initialValue: existing?.name ?? "")
        _birthdate = State(initialValue: existing?.birthdate ?? "")
        _contact = State(initialValue: existing?.contactNumber ?? "")
        _address = State(initialValue: existing?.address ?? "")
        _employerIncome = State(initialValue: existing?.employerIncome ?? "")
        _emergencyContact = State(initialValue: existing?.emergencyContact ?? "")
        _documents = State(initialValue: existing?.documents.joined(separator: "\n") ?? "")
        _status = State(initialValue: existing?.status.label ?? "Active")
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Full name", text: $name)
                TextField("Birthdate", text: $birthdate)
                TextField("Contact number", text: $contact)
                    .keyboardType(.phonePad)
                TextField("Address", text: $address, axis: .vertical)
                TextField("Employer / income", text: $employerIncome)
                TextField("Emergency contact", text: $emergencyContact)
                TextField("Documents (one per line)", text: $documents, axis: .vertical)
                Picker("Status", selection: $status) {
                    ForEach(["Active", "Inactive", "Blacklisted"], id: \.self) { Text($0) }
                }
            }
            .navigationTitle(existing == nil ? "New client" : "Edit client")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(Values(name: name, birthdate: birthdate, contact: contact, address: address,
                                      employerIncome: employerIncome, emergencyContact: emergencyContact,
                                      documents: documents, status: status))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
