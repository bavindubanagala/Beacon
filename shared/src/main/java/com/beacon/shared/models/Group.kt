package com.beacon.shared.models

data class Group(
    val groupId: String = "",
    val name: String = "",
    val deviceIds: List<String> = emptyList()
)
