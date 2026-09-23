package com.example.service

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.model.GoogleSignInAccountInfo
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthService(private val context: Context) {
    companion object {
        private const val TAG = "FirebaseAuthService"
        const val GOOGLE_WEB_CLIENT_ID = "914385564657-3hmr6l7494v7i86a4vlvm897cm5hc6rl.apps.googleusercontent.com"

        @Volatile
        private var isRemoteAuthDisabled: Boolean? = null

        /**
         * Checks whether a valid, non-placeholder Google/Firebase API key is configured.
         * Prevents making doomed network requests that fail with "API key not valid".
         */
        fun isApiKeyConfigured(): Boolean {
            if (isRemoteAuthDisabled == true) return false
            return try {
                val app = com.google.firebase.FirebaseApp.getInstance()
                val apiKey = app.options.apiKey
                val isPlaceholder = apiKey.isNullOrBlank() ||
                        apiKey.contains("Fake", ignoreCase = true) ||
                        apiKey.contains("Testing", ignoreCase = true) ||
                        apiKey.contains("PLACEHOLDER", ignoreCase = true)
                if (isPlaceholder) {
                    isRemoteAuthDisabled = true
                    false
                } else {
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        fun markRemoteAuthUnavailable() {
            isRemoteAuthDisabled = true
        }
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(try { auth.currentUser } catch (_: Exception) { null })
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState

    init {
        try {
            auth.addAuthStateListener { firebaseAuth ->
                _currentUserState.value = firebaseAuth.currentUser
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auth state listener notice: ${e.message}")
        }
    }

    val currentFirebaseUser: FirebaseUser?
        get() = try { auth.currentUser } catch (_: Exception) { null }

    fun getServerClientId(): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val resourceClientId = if (resId != 0) context.getString(resId) else null
            if (!resourceClientId.isNullOrBlank() && !resourceClientId.contains("719083488800")) {
                resourceClientId
            } else {
                GOOGLE_WEB_CLIENT_ID
            }
        } catch (e: Exception) {
            GOOGLE_WEB_CLIENT_ID
        }
    }

    suspend fun signInWithGoogleAccount(activity: Activity): Result<GoogleSignInAccountInfo> {
        return try {
            val webClientId = getServerClientId()
            Log.d(TAG, "Starting Google Sign-In with client ID: $webClientId")

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                
                // Also attempt Firebase Auth link in background if API key is active
                if (isApiKeyConfigured()) {
                    try {
                        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(authCredential).await()
                        _currentUserState.value = authResult.user
                    } catch (e: Exception) {
                        Log.w(TAG, "Optional Firebase link notice: ${e.message}")
                    }
                }

                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName
                    ?: googleIdTokenCredential.givenName
                    ?: email.substringBefore('@')
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                val googleId = googleIdTokenCredential.id

                Result.success(
                    GoogleSignInAccountInfo(
                        googleId = googleId,
                        email = email,
                        displayName = displayName,
                        photoUrl = photoUrl,
                        idToken = idToken
                    )
                )
            } else {
                Result.failure(Exception("نوع بيانات الاعتماد المستلمة من Google غير مدعوم"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled by user")
            Result.failure(Exception("تم إلغاء تسجيل الدخول بواسطة المستخدم"))
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException during Google Sign-In: ${e.message}")
            Result.failure(Exception("تعذر إتمام المصادقة عبر Google. يرجى التحقق من اتصال الإنترنت أو خدمات Google Play على الجهاز."))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.type}", e)
            Result.failure(Exception("خطأ في خدمات Google Identity: ${e.message ?: "تعذر التحقق من الهوية"}"))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In flow error", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> {
        return try {
            val accountResult = signInWithGoogleAccount(activity)
            if (accountResult.isFailure) {
                return Result.failure(accountResult.exceptionOrNull() ?: Exception("Google Sign-In failed"))
            }
            val account = accountResult.getOrNull()!!
            if (isApiKeyConfigured() && account.idToken != null) {
                val authCredential = GoogleAuthProvider.getCredential(account.idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    _currentUserState.value = user
                    return Result.success(user)
                }
            }
            Result.failure(Exception("Google Sign-In succeeded at identity level, please use signInWithGoogleAccount"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun linkCurrentUserWithGoogle(activity: Activity): Result<FirebaseUser> {
        val currentFirebaseUser = auth.currentUser
        return try {
            val webClientId = getServerClientId()
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                if (currentFirebaseUser != null && isApiKeyConfigured()) {
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = currentFirebaseUser.linkWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUserState.value = user
                        return Result.success(user)
                    }
                }
                Result.failure(Exception("Google Sign-In Account was authenticated"))
            } else {
                Result.failure(Exception("نوع بيانات الاعتماد غير مدعوم"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("تم إلغاء عملية ربط حساب Google"))
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("حساب Google هذا مرتبط بالفعل بحساب مستخدم آخر."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(customUsername: String? = null): Result<FirebaseUser> {
        if (!isApiKeyConfigured()) {
            return Result.failure(Exception("Firebase Auth غير مهيأ بمفتاح API سحابي صالح."))
        }
        return try {
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user
            if (user != null) {
                if (!customUsername.isNullOrBlank()) {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(customUsername)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}
                }
                _currentUserState.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("فشل تسجيل الدخول في Firebase"))
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("API key not valid", ignoreCase = true)) {
                markRemoteAuthUnavailable()
                Log.w(TAG, "Anonymous auth notice: API key not valid on cloud server.")
            } else {
                Log.w(TAG, "Anonymous auth notice: ${e.message}")
            }
            Result.failure(e)
        }
    }

    suspend fun createAccountWithEmail(email: String, password: String, displayName: String? = null): Result<FirebaseUser> {
        if (!isApiKeyConfigured()) {
            return Result.failure(Exception("Firebase Auth غير مهيأ بمفتاح API سحابي صالح."))
        }
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                if (!displayName.isNullOrBlank()) {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}
                }
                _currentUserState.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to create Firebase Auth user"))
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("API key not valid", ignoreCase = true)) {
                markRemoteAuthUnavailable()
                Log.w(TAG, "Create account notice: API key not valid on cloud server.")
            } else {
                Log.w(TAG, "Create account with email notice: ${e.message}")
            }
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        if (!isApiKeyConfigured()) {
            return Result.failure(Exception("Firebase Auth غير مهيأ بمفتاح API سحابي صالح."))
        }
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                _currentUserState.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("API key not valid", ignoreCase = true)) {
                markRemoteAuthUnavailable()
                Log.w(TAG, "Sign in notice: API key not valid on cloud server.")
            } else {
                Log.w(TAG, "Sign in with email notice: ${e.message}")
            }
            Result.failure(e)
        }
    }

    suspend fun verifyAndChangeFirebasePassword(
        email: String,
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        if (!isApiKeyConfigured()) {
            return Result.success(Unit)
        }
        return try {
            val user = auth.currentUser
            val effectiveEmail = user?.email?.ifBlank { null } ?: email

            if (user != null) {
                val credential = EmailAuthProvider.getCredential(effectiveEmail, oldPassword)
                
                // Step 1: Verify the old password via Firebase re-authentication
                try {
                    user.reauthenticate(credential).await()
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    return Result.failure(Exception("كلمة المرور القديمة غير صحيحة لحساب Firebase Auth"))
                } catch (e: Exception) {
                    Log.w(TAG, "Reauthentication notice: ${e.message}")
                    if (user.isAnonymous) {
                        try {
                            user.linkWithCredential(EmailAuthProvider.getCredential(effectiveEmail, newPassword)).await()
                            return Result.success(Unit)
                        } catch (linkEx: Exception) {
                            user.updatePassword(newPassword).await()
                            return Result.success(Unit)
                        }
                    } else {
                        // Fallback: Verify old password by testing credentials against email
                        try {
                            val authResult = auth.signInWithEmailAndPassword(effectiveEmail, oldPassword).await()
                            val verifiedUser = authResult.user ?: throw e
                            verifiedUser.updatePassword(newPassword).await()
                            _currentUserState.value = verifiedUser
                            return Result.success(Unit)
                        } catch (signInEx: Exception) {
                            return Result.failure(Exception("كلمة المرور القديمة غير صحيحة لحساب Firebase Auth"))
                        }
                    }
                }

                // Step 2: Apply password update to Firebase Auth account after old password verification
                user.updatePassword(newPassword).await()
                Result.success(Unit)
            } else {
                // If not signed in to Firebase Auth, verify old password by authenticating
                try {
                    val authResult = auth.signInWithEmailAndPassword(effectiveEmail, oldPassword).await()
                    val signedInUser = authResult.user ?: return Result.failure(Exception("تعذر التحقق من الحساب في Firebase"))
                    signedInUser.updatePassword(newPassword).await()
                    _currentUserState.value = signedInUser
                    Result.success(Unit)
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    Result.failure(Exception("كلمة المرور القديمة غير صحيحة لحساب Firebase Auth"))
                } catch (e: Exception) {
                    // If no existing email account, create it with new password
                    try {
                        val createResult = auth.createUserWithEmailAndPassword(effectiveEmail, newPassword).await()
                        _currentUserState.value = createResult.user
                        Result.success(Unit)
                    } catch (createEx: Exception) {
                        Result.failure(Exception("فشل التحقق وتحديث كلمة المرور في Firebase Auth: ${e.localizedMessage}"))
                    }
                }
            }
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("كلمة المرور ضعيفة. يجب أن تتكون من 6 أحرف على الأقل في Firebase Auth."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("كلمة المرور القديمة غير صحيحة لحساب Firebase Auth"))
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("API key not valid", ignoreCase = true)) {
                markRemoteAuthUnavailable()
                Log.w(TAG, "Change password notice: API key not valid.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Error changing Firebase Auth password", e)
                Result.failure(e)
            }
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _currentUserState.value = null
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Sign out error", e)
        }
    }
}
