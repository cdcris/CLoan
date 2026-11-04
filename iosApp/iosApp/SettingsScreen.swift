import SwiftUI
import Shared
import UniformTypeIdentifiers

private struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }
    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        text = String(decoding: configuration.file.regularFileContents ?? Data(), as: UTF8.self)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

struct SettingsScreen: View {
    @ObservedObject var store: AppStore
    @State private var mode: String
    @State private var rate: String
    @State private var exporting = false
    @State private var importing = false
    @State private var confirmingImport = false
    @State private var pendingImport: String?
    @State private var message: String?

    init(store: AppStore) {
        self.store = store
        _mode = State(initialValue: store.state.defaultPenaltyRule.mode.label)
        _rate = State(initialValue: String(store.state.defaultPenaltyRule.rate))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(spacing: 12) {
                        ForEach(["Export", "Import"], id: \.self) { action in
                            Button {
                                if action == "Export" {
                                    exporting = true
                                } else {
                                    importing = true
                                }
                            } label: {
                              HStack(spacing: 12) {
                                Image(systemName: action == "Export" ? "square.and.arrow.up" : "square.and.arrow.down")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundStyle(.tint)
                                Text(action)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                Spacer()
                              }
                                .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
                                .padding(.horizontal, 12)
                                .background(.background, in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                }
                if let message {
                    Section { Text(message).font(.footnote).foregroundStyle(.secondary) }
                }
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
            .fileExporter(
                isPresented: $exporting,
                document: BackupDocument(text: store.exportBackup()),
                contentType: .plainText,
                defaultFilename: "cloan-backup.txt"
            ) { result in
                switch result {
                case .success:
                    message = "Backup exported successfully."
                case .failure(let error):
                    message = "Export failed: \(error.localizedDescription)"
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText]) { result in
                do {
                    let url = try result.get()
                    let hasAccess = url.startAccessingSecurityScopedResource()
                    defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
                    pendingImport = try String(contentsOf: url, encoding: .utf8)
                    confirmingImport = true
                } catch {
                    message = "Import failed: \(error.localizedDescription)"
                }
            }
            .confirmationDialog("Replace app data?", isPresented: $confirmingImport, titleVisibility: .visible) {
                Button("Replace data", role: .destructive) {
                    if let pendingImport {
                        message = store.importBackup(pendingImport)
                            ? "Backup imported successfully."
                            : "Import failed: the selected file is invalid or incompatible."
                    }
                    pendingImport = nil
                }
                Button("Cancel", role: .cancel) { pendingImport = nil }
            } message: {
                Text("Importing this backup replaces all current clients, loans, payments, and default penalty settings.")
            }
        }
    }
}
