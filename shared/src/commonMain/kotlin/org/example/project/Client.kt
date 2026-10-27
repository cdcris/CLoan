package org.example.project

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
    val emergencyContactName: String,
    val emergencyContactNumber: String,
    val status: ClientStatus,
    val documents: List<String> = emptyList(),
    val archived: Boolean = false,
)
