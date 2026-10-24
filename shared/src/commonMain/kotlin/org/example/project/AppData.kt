package org.example.project

import org.example.project.db.Database

enum class ClientStatus(val label: String) {
    Active("Active"),
    Inactive("Inactive"),
    Blacklisted("Blacklisted"),
}

data class Client(
    val id: Int,
    val name: String,
    val birthdate: String,
    val contactNumber: String,
    val address: String,
    val employerIncome: String,
    val emergencyContact: String,
    val status: ClientStatus,
    val documents: List<String> = emptyList(),
    val archived: Boolean = false,
)

enum class PaymentFrequency(val label: String, val periodsPerYear: Int) {
    Weekly("Weekly", 52),
    SemiMonthly("Semi-monthly", 24),
    Monthly("Monthly", 12),
}

enum class LoanStatus(val label: String) {
    Pending("Pending"),
    Approved("Approved"),
    Released("Released"),
    Active("Active"),
    FullyPaid("Fully Paid"),
    Overdue("Overdue"),
    Cancelled("Cancelled"),
}

enum class PenaltyMode(val label: String) {
    Flat("Flat amount per overdue installment"),
    Percentage("Percentage of unpaid installment"),
}

data class PenaltyRule(val mode: PenaltyMode, val rate: Double)

internal fun <T> Database.readEntities(section: String, decode: (Int, String) -> T): List<T> =
    appDataQueries.readSection(section).executeAsList().map { decode(it.id.toInt(), it.payload) }

internal fun <T> Database.replaceEntities(
    section: String,
    entities: List<T>,
    id: (T) -> Int,
    encode: (T) -> String,
) {
    transaction {
        appDataQueries.replaceSection(section)
        entities.forEach { entity -> appDataQueries.insertRecord(section, id(entity).toLong(), encode(entity)) }
    }
}

internal fun Database.readPenaltyRule(): PenaltyRule {
    val parts = appDataQueries.readSetting(1L).executeAsOneOrNull()?.split('|') ?: return PenaltyRule(PenaltyMode.Percentage, 5.0)
    val mode = PenaltyMode.entries.firstOrNull { it.name == parts[0] } ?: PenaltyMode.Percentage
    return PenaltyRule(mode, parts.getOrNull(1)?.toDoubleOrNull() ?: 5.0)
}

internal fun Database.savePenaltyRule(rule: PenaltyRule) {
    appDataQueries.writeSetting(1L, "${rule.mode.name}|${rule.rate}")
}

internal fun encodeFields(fields: List<String>): String = fields.joinToString("|") { field ->
    field.replace("%", "%25").replace("|", "%7C").replace(";", "%3B")
}

internal fun decodeFields(payload: String): List<String> = payload.split('|').map { field ->
    field.replace("%7C", "|").replace("%3B", ";").replace("%25", "%")
}

internal fun encodeClient(value: Client) = encodeFields(listOf(
    value.name, value.birthdate, value.contactNumber, value.address, value.employerIncome,
    value.emergencyContact, value.status.name, value.documents.joinToString("\u001f"), value.archived.toString(),
))

internal fun decodeClient(id: Int, payload: String): Client {
    val fields = decodeFields(payload)
    return Client(id, fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
        ClientStatus.valueOf(fields[6]), fields.getOrNull(7)?.takeIf { it.isNotEmpty() }?.split("\u001f").orEmpty(),
        fields.getOrNull(8)?.toBoolean() ?: false)
}

internal fun encodeLoan(value: Loan) = encodeFields(listOf(
    value.clientId.toString(), value.principal.toString(), value.annualInterestRate.toString(), value.term.toString(),
    value.startDate, value.frequency.name, value.status.name, value.releaseDate, value.releaseAmount?.toString().orEmpty(),
    value.releaseMethod, value.releaseReference, value.penaltyBalance.toString(), value.penaltyRule?.mode?.name.orEmpty(),
    value.penaltyRule?.rate?.toString().orEmpty(),
))

internal fun decodeLoan(id: Int, payload: String): Loan {
    val fields = decodeFields(payload)
    val penaltyMode = fields[12].takeIf { it.isNotEmpty() }?.let(PenaltyMode::valueOf)
    val penaltyRule = penaltyMode?.let { PenaltyRule(it, fields[13].toDouble()) }
    return Loan(id, fields[0].toInt(), fields[1].toDouble(), fields[2].toDouble(), fields[3].toInt(), fields[4],
        PaymentFrequency.valueOf(fields[5]), LoanStatus.valueOf(fields[6]), fields[7], fields[8].toDoubleOrNull(),
        fields[9], fields[10], fields[11].toDouble(), penaltyRule)
}

internal fun encodePayment(value: Payment) = encodeFields(listOf(
    value.loanId.toString(), value.amount.toString(), value.date, value.method, value.reference, value.voidReason,
    value.allocatedPenalty.toString(), value.allocatedInterest.toString(), value.allocatedPrincipal.toString(), value.unappliedAmount.toString(),
))

internal fun decodePayment(id: Int, payload: String): Payment {
    val fields = decodeFields(payload)
    return Payment(id, fields[0].toInt(), fields[1].toDouble(), fields[2], fields[3], fields[4], fields[5],
        fields[6].toDouble(), fields[7].toDouble(), fields[8].toDouble(), fields[9].toDouble())
}

data class Loan(
    val id: Int,
    val clientId: Int,
    val principal: Double,
    val annualInterestRate: Double,
    val term: Int,
    val startDate: String,
    val frequency: PaymentFrequency,
    val status: LoanStatus = LoanStatus.Pending,
    val releaseDate: String = "",
    val releaseAmount: Double? = null,
    val releaseMethod: String = "",
    val releaseReference: String = "",
    val penaltyBalance: Double = 0.0,
    val penaltyRule: PenaltyRule? = null,
)

data class Payment(
    val id: Int,
    val loanId: Int,
    val amount: Double,
    val date: String,
    val method: String,
    val reference: String,
    val voidReason: String = "",
    val allocatedPenalty: Double = 0.0,
    val allocatedInterest: Double = 0.0,
    val allocatedPrincipal: Double = 0.0,
    val unappliedAmount: Double = 0.0,
)

data class Installment(val number: Int, val dueDate: String, val principal: Double, val interest: Double) {
    val total: Double get() = principal + interest
}

data class InstallmentStatus(val installment: Installment, val unpaid: Double, val penalty: Double, val penaltyOutstanding: Double, val daysOverdue: Int) {
    val isOverdue: Boolean get() = daysOverdue > 0 && (unpaid > 0.009 || penaltyOutstanding > 0.009)
}
