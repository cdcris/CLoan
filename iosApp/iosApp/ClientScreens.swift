import Foundation
import SwiftUI
import Shared
import UniformTypeIdentifiers

struct ClientScreen: View {
    @ObservedObject var store: AppStore
    @State private var editingClient: Client?
    @State private var showingEditor = false
    @State private var query = ""
    @State private var showingArchived = false
    @State private var deletingClient: Client?

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
                        Button("Edit") {
                            editingClient = client
                            showingEditor = true
                        }
                        .tint(.blue)
                        Button(client.archived ? "Restore" : "Archive") {
                            store.viewModel.toggleClientArchived(client: client)
                        }
                        .tint(client.archived ? .green : .orange)
                        Button(role: .destructive) {
                            deletingClient = client
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .navigationTitle("Clients")
            .searchable(text: $query, prompt: "Search name or contact")
            .alert("Permanently delete client?", isPresented: Binding(
                get: { deletingClient != nil },
                set: { if !$0 { deletingClient = nil } }
            ), presenting: deletingClient) { client in
                Button("Permanently delete", role: .destructive) {
                    store.viewModel.hardDeleteClient(clientId: client.id)
                    deletingClient = nil
                }
                Button("Cancel", role: .cancel) { deletingClient = nil }
            } message: { client in
                Text("This permanently deletes \(client.name), their loans, and payment records.")
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(showingArchived ? "Current" : "Archived") { showingArchived.toggle() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { editingClient = nil; showingEditor = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingEditor) {
                ClientEditor(existing: editingClient, clientId: editingClient?.id ?? (store.state.clients.map(\.id).max() ?? 0) + 1) { values in
                    store.saveClient(existing: editingClient, name: values.name, birthdate: values.birthdate,
                                     contact: values.contact, address: values.address,
                                     emergencyContactName: values.emergencyContactName, emergencyContactNumber: values.emergencyContactNumber,
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
        let emergencyContactName: String
        let emergencyContactNumber: String
        let documents: String
        let status: String
    }

    let existing: Client?
    let clientId: Int32
    let onSave: (Values) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var birthdate: String
    @State private var contact: String
    @State private var address: String
    @State private var emergencyContactName: String
    @State private var emergencyContactNumber: String
    @State private var documents: [String]
    @State private var status: String
    @State private var showingDocumentImporter = false
    @State private var documentImportError: String?

    init(existing: Client?, clientId: Int32, onSave: @escaping (Values) -> Void) {
        self.existing = existing
        self.clientId = clientId
        self.onSave = onSave
        _name = State(initialValue: existing?.name ?? "")
        _birthdate = State(initialValue: existing?.birthdate ?? "")
        _contact = State(initialValue: existing?.contactNumber ?? "")
        _address = State(initialValue: existing?.address ?? "")
        _emergencyContactName = State(initialValue: existing?.emergencyContactName ?? "")
        _emergencyContactNumber = State(initialValue: existing?.emergencyContactNumber ?? "")
        _documents = State(initialValue: existing?.documents ?? [])
        _status = State(initialValue: existing?.status.label ?? "Active")
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Full name *", text: $name)
                TextField("Birthdate", text: $birthdate)
                TextField("Contact number *", text: $contact)
                    .keyboardType(.phonePad)
                TextField("Address", text: $address, axis: .vertical)
                TextField("Emergency contact name", text: $emergencyContactName)
                TextField("Emergency contact number", text: $emergencyContactNumber)
                    .keyboardType(.phonePad)
                Section("Documents") {
                    Button("Choose documents") { showingDocumentImporter = true }
                    Text("Files are saved as \(clientId)_\(documentNameSlug(name))_N.extension")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(documents, id: \.self) { document in
                        HStack {
                            Text(document)
                            Spacer()
                            Button(role: .destructive) { documents.removeAll { $0 == document } } label: {
                                Image(systemName: "trash")
                            }
                        }
                    }
                    if let documentImportError {
                        Text(documentImportError).foregroundStyle(.red)
                    }
                }
                Picker("Status", selection: $status) {
                    ForEach(["Active", "Inactive", "Blacklisted"], id: \.self) { Text($0) }
                }
            }
            .fileImporter(isPresented: $showingDocumentImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                importDocuments(result, clientId: clientId)
            }
            .navigationTitle(existing == nil ? "New client" : "Edit client")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(Values(name: name, birthdate: birthdate, contact: contact, address: address,
                                      emergencyContactName: emergencyContactName, emergencyContactNumber: emergencyContactNumber,
                                      documents: documents.joined(separator: "\n"), status: status))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func importDocuments(_ result: Result<[URL], Error>, clientId: Int32) {
        do {
            let urls = try result.get()
            let directory = try FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("ClientDocuments", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            for sourceURL in urls {
                let hasAccess = sourceURL.startAccessingSecurityScopedResource()
                defer { if hasAccess { sourceURL.stopAccessingSecurityScopedResource() } }
                let ext = sourceURL.pathExtension
                let suffix = ext.isEmpty ? "" : ".\(ext)"
                var sequence = documents.count + 1
                var destination = directory.appendingPathComponent("\(clientId)_\(documentNameSlug(name))_\(sequence)\(suffix)")
                while FileManager.default.fileExists(atPath: destination.path) {
                    sequence += 1
                    destination = directory.appendingPathComponent("\(clientId)_\(documentNameSlug(name))_\(sequence)\(suffix)")
                }
                try FileManager.default.copyItem(at: sourceURL, to: destination)
                documents.append(destination.lastPathComponent)
            }
            documentImportError = nil
        } catch {
            documentImportError = "Could not add the selected files."
        }
    }
}

private func documentNameSlug(_ value: String) -> String {
    let lowered = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let slug = lowered.replacingOccurrences(of: "[^a-z0-9]+", with: "_", options: .regularExpression)
        .trimmingCharacters(in: CharacterSet(charactersIn: "_"))
    return slug.isEmpty ? "client" : slug
}
