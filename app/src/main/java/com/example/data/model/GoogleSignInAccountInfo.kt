package com.example.data.model

data class GoogleSignInAccountInfo(
    val googleId: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val idToken: String? = null
)
