package org.example.project

import org.example.project.db.Database

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
    "v2", value.name, value.birthdate, value.contactNumber, value.address, value.emergencyContactName,
    value.emergencyContactNumber, value.status.name, value.documents.joinToString("\u001f"), value.archived.toString(),
))

internal fun decodeClient(id: Int, payload: String): Client {
    val fields = decodeFields(payload)
    return if (fields[0] == "v2") {
        Client(id, fields[1], fields[2], fields[3], fields[4], fields[5], fields[6],
            ClientStatus.valueOf(fields[7]), fields.getOrNull(8)?.takeIf { it.isNotEmpty() }?.split("\u001f").orEmpty(),
            fields.getOrNull(9)?.toBoolean() ?: false)
    } else {
        Client(id, fields[0], fields[1], fields[2], fields[3], fields[5], "",
            ClientStatus.valueOf(fields[6]), fields.getOrNull(7)?.takeIf { it.isNotEmpty() }?.split("\u001f").orEmpty(),
            fields.getOrNull(8)?.toBoolean() ?: false)
    }
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

