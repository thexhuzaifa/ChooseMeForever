package com.chooseme.forever.data.model

data class User(
    val userId: String = "",
    val name: String = "",
    val age: Int = 0,
    val bio: String = "",
    val city: String = "",
    val interests: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
