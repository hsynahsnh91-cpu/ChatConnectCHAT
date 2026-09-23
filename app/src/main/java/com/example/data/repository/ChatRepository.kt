package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entities.*
import com.example.service.FirebaseAuthService
import com.example.service.FirestoreService
import com.example.service.RealtimeCallDispatcher
import com.example.service.call.CallHistoryRecord
import com.example.utils.AdminConstants
import com.example.utils.IdGenerator
import com.example.utils.PasswordUtils
import com.google.firebase.auth.FirebaseUser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val contactDao = db.contactDao()
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    val attachmentDao = db.attachmentDao()
    val authIdentityDao = db.authIdentityDao()
    private val statusDao = db.statusDao()
    private val callDao = db.callDao()
    private val blockedUserDao = db.blockedUserDao()
    private val userSettingsDao = db.userSettingsDao()
    private val deviceSessionDao = db.deviceSessionDao()
    private val specialIdentifierDao = db.specialIdentifierDao()
    private val messageReactionDao = db.messageReactionDao()

    val authService = FirebaseAuthService(context)
    val firestoreService = FirestoreService()
    val specialIdentifierManager = com.example.service.SpecialIdentifierManager(
        firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
        specialIdentifierDao = specialIdentifierDao,
        userDao = userDao
    )
    val adminSpecialIdentifierManager = com.example.service.admin.AdminSpecialIdentifierManager(
        firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
        specialIdentifierDao = specialIdentifierDao,
        userDao = userDao
    )
    val storageService = com.example.service.StorageService(context)
    val locationService = com.example.service.LocationService(context)
    val contactsService = com.example.service.ContactsService(context)

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Current active logged-in user state
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    val allRegisteredUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    val isFirestoreConnected: StateFlow<Boolean> = firestoreService.isConnected

    // Local device session tracking: only accounts logged in on this specific device will be remembered
    private val devicePrefs = context.getSharedPreferences("chatconnect_device_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_ACTIVE_USER_ID = "active_logged_in_user_id"
    }

    fun getActiveUserId(): String? {
        return devicePrefs.getString(KEY_ACTIVE_USER_ID, null)
    }

    fun setActiveUserId(userId: String) {
        devicePrefs.edit().putString(KEY_ACTIVE_USER_ID, userId).apply()
        markUserLoggedInOnThisDevice(userId)
    }

    fun clearActiveSession() {
        devicePrefs.edit().remove(KEY_ACTIVE_USER_ID).apply()
    }

    fun getDeviceLoggedInUserIds(): Set<String> {
        return devicePrefs.getStringSet("device_logged_in_user_ids", emptySet()) ?: emptySet()
    }

    fun markUserLoggedInOnThisDevice(userId: String) {
        val currentSet = getDeviceLoggedInUserIds().toMutableSet()
        currentSet.add(userId)
        devicePrefs.edit().putStringSet("device_logged_in_user_ids", currentSet).apply()
    }

    fun removeUserLoggedInOnThisDevice(userId: String) {
        val currentSet = getDeviceLoggedInUserIds().toMutableSet()
        currentSet.remove(userId)
        devicePrefs.edit().putStringSet("device_logged_in_user_ids", currentSet).apply()
    }

    // Displays ONLY accounts logged into on this device, strictly excluding the Super Admin account
    val deviceLoggedInUsers: Flow<List<UserEntity>> = userDao.getAllUsers().map { users ->
        val loggedInIds = getDeviceLoggedInUserIds()
        users.filter { user ->
            user.userId in loggedInIds &&
                    user.userId != AdminConstants.ADMIN_USER_ID &&
                    user.customId != AdminConstants.ADMIN_CUSTOM_ID
        }
    }

    fun observeSpecialIdentifiers(): Flow<List<SpecialIdentifierEntity>> = specialIdentifierDao.getAll()

    init {
        // 1. Immediately restore local session from persistent device storage
        val savedActiveUserId = getActiveUserId()
        if (savedActiveUserId != null) {
            repositoryScope.launch {
                val localUser = userDao.getUserById(savedActiveUserId)
                if (localUser != null) {
                    _currentUser.value = localUser
                    Log.d("ChatRepository", "Session successfully restored for: ${localUser.username} (${localUser.customId})")
                    if (FirebaseAuthService.isApiKeyConfigured() && authService.currentFirebaseUser == null) {
                        try {
                            val email = localUser.email?.ifBlank { null } ?: "${localUser.customId}@chatconnect.app"
                            val pass = if (localUser.userId == AdminConstants.ADMIN_USER_ID) AdminConstants.ADMIN_PASSWORD else "ChatConnectUserPass2026!"
                            val res = authService.signInWithEmail(email, pass)
                            if (res.isFailure) {
                                authService.signInAnonymously(localUser.username)
                            }
                        } catch (_: Exception) {
                            try { authService.signInAnonymously(localUser.username) } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

        // 2. Real-time Special Identifiers Sync from Firestore
        repositoryScope.launch {
            try {
                adminSpecialIdentifierManager.observeSpecialIdentifiers().collect { cloudItems ->
                    specialIdentifierDao.insertAll(cloudItems)
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Special identifiers sync notice: ${e.message}")
            }
        }

        // Seed unique Administrator Account if not present
        repositoryScope.launch {
            try {
                val existingAdmin = userDao.getUserByCustomId(AdminConstants.ADMIN_CUSTOM_ID)
                    ?: userDao.getUserById(AdminConstants.ADMIN_USER_ID)
                if (existingAdmin == null) {
                    val adminUser = UserEntity(
                        userId = AdminConstants.ADMIN_USER_ID,
                        customId = AdminConstants.ADMIN_CUSTOM_ID,
                        username = AdminConstants.ADMIN_USERNAME,
                        email = AdminConstants.ADMIN_EMAIL,
                        firebaseUid = AdminConstants.ADMIN_USER_ID,
                        passwordHash = PasswordUtils.hashPassword(AdminConstants.ADMIN_PASSWORD),
                        bio = AdminConstants.ADMIN_BIO,
                        isOnline = true,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                    userDao.insertUser(adminUser)
                    firestoreService.saveUser(adminUser)
                    if (FirebaseAuthService.isApiKeyConfigured()) {
                        try {
                            val authRes = authService.signInWithEmail(AdminConstants.ADMIN_EMAIL, AdminConstants.ADMIN_PASSWORD)
                            if (authRes.isFailure) {
                                authService.createAccountWithEmail(AdminConstants.ADMIN_EMAIL, AdminConstants.ADMIN_PASSWORD, AdminConstants.ADMIN_USERNAME)
                            }
                        } catch (_: Exception) {}
                    }
                    Log.d("ChatRepository", "Seeded Admin account successfully with ID: ${AdminConstants.ADMIN_CUSTOM_ID}")
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Error seeding admin account: ${e.message}")
            }
        }

        // Automatically maintain active device session for logged-in user
        repositoryScope.launch {
            _currentUser.filterNotNull().collect { user ->
                ensureCurrentDeviceSession(user.userId)
            }
        }

        // Initialize Realtime Call Dispatcher and hardware services
        RealtimeCallDispatcher.initialize(context)
        RealtimeCallDispatcher.onCallEndedListener = { callId, callerUserId, receiverUserId, isVideo, finalStatus, durationSec ->
            repositoryScope.launch {
                saveCallLog(callId, callerUserId, receiverUserId, isVideo, finalStatus, durationSec)
            }
        }

        // Observe incoming VoIP calls in real time
        repositoryScope.launch {
            val signaling = com.example.service.call.CallSignalingService()
            _currentUser.filterNotNull().collectLatest { user ->
                signaling.listenForIncomingCalls(user.userId).collect { incomingSession ->
                    if (incomingSession != null && RealtimeCallDispatcher.currentCallState.value.status == com.example.service.CallStatus.IDLE) {
                        RealtimeCallDispatcher.receiveCall(
                            callId = incomingSession.callId,
                            callerUserId = incomingSession.callerUserId,
                            callerName = incomingSession.callerName,
                            callerAvatar = incomingSession.callerAvatar,
                            receiverUserId = incomingSession.receiverUserId,
                            isVideo = incomingSession.isVideo
                        )
                    }
                }
            }
        }

        // Initial check for purge
        repositoryScope.launch {
            statusDao.purgeExpiredStatuses()
        }

        // Real-time Firestore Users Sync into local Room Database
        repositoryScope.launch {
            try {
                firestoreService.observeUsers().collect { cloudUsers ->
                    for (cloudUser in cloudUsers) {
                        val existing = userDao.getUserById(cloudUser.userId)
                        if (existing == null) {
                            userDao.insertUser(cloudUser)
                        } else if (existing.lastSeenTimestamp < cloudUser.lastSeenTimestamp) {
                            userDao.updateUser(cloudUser)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Firestore observe users error: ${e.message}")
            }
        }

        // Restore Firebase session if already authenticated
        repositoryScope.launch {
            val firebaseUser = authService.currentFirebaseUser
            if (firebaseUser != null) {
                val localUser = userDao.getUserById(firebaseUser.uid)
                if (localUser != null) {
                    _currentUser.value = localUser
                }
            }
        }
    }

    suspend fun loginWithGoogle(activity: Activity): Result<UserEntity> {
        val authResult = authService.signInWithGoogleAccount(activity)
        if (authResult.isFailure) {
            return Result.failure(authResult.exceptionOrNull() ?: Exception("Google Sign-In failed"))
        }

        val googleAccount = authResult.getOrNull()!!
        return onGoogleAccountAuthenticated(googleAccount)
    }

    suspend fun linkCurrentUserWithGoogle(activity: Activity): Result<UserEntity> {
        val current = _currentUser.value ?: return Result.failure(Exception("لا يوجد مستخدم نشط لربط الحساب"))
        val linkResult = authService.signInWithGoogleAccount(activity)
        if (linkResult.isFailure) {
            return Result.failure(linkResult.exceptionOrNull() ?: Exception("فشل ربط الحساب بـ Google"))
        }
        val googleAccount = linkResult.getOrNull()!!
        val googleSubId = googleAccount.googleId

        val existingIdentity = authIdentityDao.getIdentity("google", googleSubId)
            ?: firestoreService.getAuthIdentity("google", googleSubId)
        if (existingIdentity != null && existingIdentity.userId != current.userId) {
            return Result.failure(Exception("حساب Google هذا مرتبط بالفعل بحساب مستخدم آخر."))
        }

        val authIdentity = com.example.data.local.entities.AuthIdentityEntity(
            provider = "google",
            providerUserId = googleSubId,
            userId = current.userId,
            email = googleAccount.email,
            displayName = googleAccount.displayName,
            photoUrl = googleAccount.photoUrl,
            linkedAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        authIdentityDao.insertOrUpdateIdentity(authIdentity)
        firestoreService.saveAuthIdentity(authIdentity)

        val updatedUser = current.copy(
            email = googleAccount.email.ifBlank { current.email },
            profilePicUri = current.profilePicUri ?: googleAccount.photoUrl
        )
        userDao.updateUser(updatedUser)
        firestoreService.saveUser(updatedUser)
        _currentUser.value = updatedUser
        return Result.success(updatedUser)
    }

    private suspend fun onGoogleAccountAuthenticated(account: com.example.data.model.GoogleSignInAccountInfo): Result<UserEntity> {
        val googleSubId = account.googleId
        val email = account.email
        val displayName = account.displayName?.ifBlank { null }
            ?: email.substringBefore('@')
            ?: "User-${googleSubId.take(4)}"
        val photoUrl = account.photoUrl

        // 1. Check local & cloud auth identities table
        var identity = authIdentityDao.getIdentity("google", googleSubId)
        if (identity == null) {
            identity = firestoreService.getAuthIdentity("google", googleSubId)
            if (identity != null) {
                authIdentityDao.insertOrUpdateIdentity(identity)
            }
        }

        var localUser: UserEntity? = null
        if (identity != null) {
            localUser = userDao.getUserById(identity.userId)
                ?: firestoreService.getUser(identity.userId)
        }

        // 2. If no identity, check if an existing user matches the verified email
        if (localUser == null && email.isNotBlank()) {
            localUser = userDao.getUserByEmail(email)
                ?: firestoreService.findUserByEmail(email)
        }

        // 3. Fallback: check by googleId
        if (localUser == null) {
            localUser = userDao.getUserById(googleSubId)
                ?: firestoreService.getUser(googleSubId)
        }

        // 4. If user still does not exist: create brand new user ONCE!
        if (localUser == null) {
            val newUserId = IdGenerator.generateUuid()
            var customId = IdGenerator.generate10DigitId()
            while (userDao.getUserByCustomId(customId) != null || firestoreService.findUserByCustomId(customId) != null) {
                customId = IdGenerator.generate10DigitId()
            }

            localUser = UserEntity(
                userId = newUserId,
                customId = customId,
                username = displayName,
                email = email,
                firebaseUid = googleSubId,
                profilePicUri = photoUrl,
                bio = "Hey there! I am using ChatConnect.",
                isOnline = true,
                createdAt = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            userDao.insertUser(localUser)
            firestoreService.saveUser(localUser)
        } else {
            localUser = localUser.copy(
                isOnline = true,
                email = email.ifBlank { localUser.email },
                profilePicUri = photoUrl ?: localUser.profilePicUri,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            userDao.updateUser(localUser)
            firestoreService.saveUser(localUser)
        }

        // 5. Ensure AuthIdentity is saved and linked to this internalUserId
        val authIdentity = com.example.data.local.entities.AuthIdentityEntity(
            provider = "google",
            providerUserId = googleSubId,
            userId = localUser.userId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            linkedAt = identity?.linkedAt ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        authIdentityDao.insertOrUpdateIdentity(authIdentity)
        firestoreService.saveAuthIdentity(authIdentity)

        val settings = userSettingsDao.getSettings(localUser.userId)
        if (settings == null) {
            userSettingsDao.insertOrUpdateSettings(UserSettingsEntity(userId = localUser.userId))
        }

        _currentUser.value = localUser
        setActiveUserId(localUser.userId)
        return Result.success(localUser)
    }

    suspend fun loginWithAnonymousFirebase(customUsername: String): Result<UserEntity> {
        val authResult = authService.signInAnonymously(customUsername)
        if (authResult.isFailure) {
            return Result.failure(authResult.exceptionOrNull() ?: Exception("Firebase Auth failed"))
        }

        val firebaseUser = authResult.getOrNull()!!
        return onFirebaseUserAuthenticated(firebaseUser)
    }

    private suspend fun onFirebaseUserAuthenticated(firebaseUser: FirebaseUser): Result<UserEntity> {
        val googleSubId = firebaseUser.providerData.firstOrNull { it.providerId == "google.com" }?.uid ?: firebaseUser.uid
        val email = firebaseUser.email
        val displayName = firebaseUser.displayName?.ifBlank { null }
            ?: email?.substringBefore('@')
            ?: "User-${googleSubId.take(4)}"
        val photoUrl = firebaseUser.photoUrl?.toString()

        // 1. Check local & cloud auth identities table
        var identity = authIdentityDao.getIdentity("google", googleSubId)
        if (identity == null) {
            identity = firestoreService.getAuthIdentity("google", googleSubId)
            if (identity != null) {
                authIdentityDao.insertOrUpdateIdentity(identity)
            }
        }

        var localUser: UserEntity? = null
        if (identity != null) {
            localUser = userDao.getUserById(identity.userId)
                ?: firestoreService.getUser(identity.userId)
        }

        // 2. If no identity, check if an existing user matches the verified email
        if (localUser == null && !email.isNullOrBlank()) {
            localUser = userDao.getUserByEmail(email)
                ?: firestoreService.findUserByEmail(email)
        }

        // 3. Fallback: check by firebaseUid
        if (localUser == null) {
            localUser = userDao.getUserById(firebaseUser.uid)
                ?: firestoreService.getUser(firebaseUser.uid)
        }

        // 4. If user still does not exist: create brand new user ONCE!
        if (localUser == null) {
            val newUserId = IdGenerator.generateUuid()
            var customId = IdGenerator.generate10DigitId()
            while (userDao.getUserByCustomId(customId) != null || firestoreService.findUserByCustomId(customId) != null) {
                customId = IdGenerator.generate10DigitId()
            }

            localUser = UserEntity(
                userId = newUserId,
                customId = customId,
                username = displayName,
                email = email,
                firebaseUid = firebaseUser.uid,
                profilePicUri = photoUrl,
                bio = "Hey there! I am using ChatConnect with Firebase.",
                isOnline = true,
                createdAt = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            userDao.insertUser(localUser)
            firestoreService.saveUser(localUser)
        } else {
            localUser = localUser.copy(
                isOnline = true,
                email = email ?: localUser.email,
                firebaseUid = firebaseUser.uid,
                profilePicUri = photoUrl ?: localUser.profilePicUri,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            userDao.updateUser(localUser)
            firestoreService.saveUser(localUser)
        }

        // 5. Ensure AuthIdentity is saved and linked to this internalUserId
        val authIdentity = com.example.data.local.entities.AuthIdentityEntity(
            provider = "google",
            providerUserId = googleSubId,
            userId = localUser.userId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            linkedAt = identity?.linkedAt ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        authIdentityDao.insertOrUpdateIdentity(authIdentity)
        firestoreService.saveAuthIdentity(authIdentity)

        val settings = userSettingsDao.getSettings(localUser.userId)
        if (settings == null) {
            userSettingsDao.insertOrUpdateSettings(UserSettingsEntity(userId = localUser.userId))
        }

        _currentUser.value = localUser
        setActiveUserId(localUser.userId)
        return Result.success(localUser)
    }

    suspend fun registerUser(username: String, password: String, profilePicUri: String? = null): UserEntity {
        var customId = IdGenerator.generate10DigitId()
        while (userDao.getUserByCustomId(customId) != null) {
            customId = IdGenerator.generate10DigitId()
        }

        val userId = IdGenerator.generateUuid()
        val passwordHash = PasswordUtils.hashPassword(password)
        val newUser = UserEntity(
            userId = userId,
            customId = customId,
            username = username,
            passwordHash = passwordHash,
            profilePicUri = profilePicUri,
            bio = "Hey there! I am using ChatConnect.",
            isOnline = true
        )
        userDao.insertUser(newUser)

        val settings = UserSettingsEntity(userId = userId)
        userSettingsDao.insertOrUpdateSettings(settings)

        _currentUser.value = newUser
        setActiveUserId(newUser.userId)

        repositoryScope.launch {
            firestoreService.saveUser(newUser)
            // Synchronize user account with Firebase Auth if configured
            if (FirebaseAuthService.isApiKeyConfigured()) {
                val email = "${customId}@chatconnect.app"
                try {
                    authService.createAccountWithEmail(email, password, username)
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Firebase Auth account creation notice: ${e.message}")
                }
            }
        }

        return newUser
    }

    suspend fun loginUserByCustomId(customId: String, password: String): Result<UserEntity> {
        var user = userDao.getUserByCustomId(customId)
        if (user == null) {
            if (customId == AdminConstants.ADMIN_CUSTOM_ID) {
                val admin = UserEntity(
                    userId = AdminConstants.ADMIN_USER_ID,
                    customId = AdminConstants.ADMIN_CUSTOM_ID,
                    username = AdminConstants.ADMIN_USERNAME,
                    email = AdminConstants.ADMIN_EMAIL,
                    firebaseUid = AdminConstants.ADMIN_USER_ID,
                    passwordHash = PasswordUtils.hashPassword(AdminConstants.ADMIN_PASSWORD),
                    bio = AdminConstants.ADMIN_BIO,
                    isOnline = true,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
                userDao.insertUser(admin)
                firestoreService.saveUser(admin)
                user = admin
            } else {
                // Check Firestore
                user = firestoreService.findUserByCustomId(customId)
                if (user != null) {
                    userDao.insertUser(user)
                }
            }
        }

        if (user == null) {
            return Result.failure(Exception("لم يتم العثور على حساب بهذا المعرف (ID)"))
        }

        if (user.passwordHash.isNotBlank()) {
            if (!PasswordUtils.verifyPassword(password, user.passwordHash)) {
                return Result.failure(Exception("كلمة المرور غير صحيحة! يرجى التأكد من كلمة المرور"))
            }
        } else if (password.isNotBlank()) {
            // Account had no password yet, initialize it
            val updatedWithPass = user.copy(passwordHash = PasswordUtils.hashPassword(password))
            user = updatedWithPass
        }

        val updated = user.copy(isOnline = true, lastSeenTimestamp = System.currentTimeMillis())
        userDao.updateUser(updated)
        _currentUser.value = updated
        setActiveUserId(updated.userId)
        repositoryScope.launch {
            firestoreService.saveUser(updated)
            // Also sign in to Firebase Auth in background if configured
            if (FirebaseAuthService.isApiKeyConfigured()) {
                val email = updated.email?.ifBlank { null } ?: "${updated.customId}@chatconnect.app"
                try {
                    val signInResult = authService.signInWithEmail(email, password)
                    if (signInResult.isFailure) {
                        val createResult = authService.createAccountWithEmail(email, password, updated.username)
                        if (createResult.isFailure) {
                            authService.signInAnonymously(updated.username)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Firebase Auth sign in notice: ${e.message}")
                    try { authService.signInAnonymously(updated.username) } catch (_: Exception) {}
                }
            }
        }
        return Result.success(updated)
    }

    suspend fun switchAccount(user: UserEntity, password: String? = null): Result<UserEntity> {
        if (user.passwordHash.isNotBlank()) {
            if (password.isNullOrBlank() || !PasswordUtils.verifyPassword(password, user.passwordHash)) {
                return Result.failure(Exception("كلمة المرور غير صحيحة للدخول إلى حساب ${user.username}"))
            }
        }
        val updated = user.copy(isOnline = true, lastSeenTimestamp = System.currentTimeMillis())
        userDao.updateUser(updated)
        _currentUser.value = updated
        setActiveUserId(updated.userId)
        repositoryScope.launch {
            firestoreService.saveUser(updated)
            if (!password.isNullOrBlank()) {
                val email = updated.email?.ifBlank { null } ?: "${updated.customId}@chatconnect.app"
                try {
                    authService.signInWithEmail(email, password)
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Firebase Auth switch sign-in notice: ${e.message}")
                }
            }
        }
        return Result.success(updated)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("المستخدم غير مسجل"))
        if (newPassword.length < 6) {
            return Result.failure(Exception("يجب ألا تقل كلمة المرور عن 6 أحرف لحساب Firebase Auth"))
        }

        // 1. Verify old password locally before attempting any updates
        if (current.passwordHash.isNotBlank()) {
            if (!PasswordUtils.verifyPassword(currentPassword, current.passwordHash)) {
                return Result.failure(Exception("كلمة المرور القديمة غير صحيحة! تعذر تطبيق التحديث."))
            }
        }

        // 2. Verify old password and apply update to Firebase Auth account
        val email = current.email?.ifBlank { null } ?: "${current.customId}@chatconnect.app"
        val fbAuthResult = authService.verifyAndChangeFirebasePassword(email, currentPassword, newPassword)
        if (fbAuthResult.isFailure) {
            val ex = fbAuthResult.exceptionOrNull()
            Log.e("ChatRepository", "Firebase Auth password update failed", ex)
            return Result.failure(Exception(ex?.message ?: "فشل التحقق وتحديث كلمة المرور في حساب Firebase Auth"))
        }

        // 3. Update local database and Cloud Firestore with the new hashed password
        val newHash = PasswordUtils.hashPassword(newPassword)
        val updated = current.copy(passwordHash = newHash)
        userDao.updateUser(updated)
        _currentUser.value = updated
        repositoryScope.launch { firestoreService.saveUser(updated) }
        return Result.success(Unit)
    }

    fun logout() {
        val current = _currentUser.value
        clearActiveSession()
        if (current != null) {
            repositoryScope.launch {
                val updated = current.copy(isOnline = false, lastSeenTimestamp = System.currentTimeMillis())
                userDao.updateUser(updated)
                firestoreService.saveUser(updated)
                authService.signOut()
            }
        }
        _currentUser.value = null
    }

    // --- Linked Devices & Sessions Management ---

    fun getActiveSessionsForUser(userId: String): Flow<List<DeviceSessionEntity>> {
        return deviceSessionDao.getActiveSessionsForUser(userId)
    }

    suspend fun ensureCurrentDeviceSession(userId: String) {
        try {
            val model = android.os.Build.MODEL.ifBlank { "Android Device" }
            val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            val currentSessions = deviceSessionDao.getAllSessionsForUser(userId).first()
            val currentDeviceSession = currentSessions.find { it.isCurrentDevice }

            if (currentDeviceSession == null) {
                val newSession = DeviceSessionEntity(
                    sessionId = IdGenerator.generateUuid(),
                    userId = userId,
                    deviceName = "$deviceName (هذا الجهاز)",
                    deviceType = "PHONE",
                    platform = "Android ${android.os.Build.VERSION.RELEASE}",
                    clientApp = "ChatConnect Android v2.4",
                    ipAddress = "192.168.1.108",
                    location = "الرياض، المملكة العربية السعودية",
                    isCurrentDevice = true,
                    createdAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis(),
                    isActive = true
                )
                deviceSessionDao.insertSession(newSession)
                firestoreService.saveDeviceSession(newSession)

                // If user has no other sessions, create a realistic linked web session so the user can test revoking
                if (currentSessions.isEmpty()) {
                    val webSession = DeviceSessionEntity(
                        sessionId = IdGenerator.generateUuid(),
                        userId = userId,
                        deviceName = "ChatConnect Web (Chrome)",
                        deviceType = "WEB",
                        platform = "Windows 11",
                        clientApp = "Chrome 128.0",
                        ipAddress = "82.178.45.19",
                        location = "الرياض، المملكة العربية السعودية",
                        isCurrentDevice = false,
                        createdAt = System.currentTimeMillis() - 86400000L * 2,
                        lastActiveAt = System.currentTimeMillis() - 3600000L * 3,
                        isActive = true
                    )
                    deviceSessionDao.insertSession(webSession)
                    firestoreService.saveDeviceSession(webSession)
                }
            } else {
                val updated = currentDeviceSession.copy(lastActiveAt = System.currentTimeMillis(), isActive = true)
                deviceSessionDao.updateSession(updated)
                firestoreService.saveDeviceSession(updated)
            }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Failed to ensure current device session: ${e.message}")
        }
    }

    suspend fun revokeDeviceSession(session: DeviceSessionEntity): Boolean {
        return try {
            deviceSessionDao.revokeSession(session.sessionId)
            firestoreService.revokeDeviceSession(session.userId, session.sessionId)
            if (session.isCurrentDevice) {
                logout()
            }
            true
        } catch (e: Exception) {
            Log.w("ChatRepository", "Failed to revoke session: ${e.message}")
            false
        }
    }

    suspend fun revokeAllOtherSessions(userId: String): Boolean {
        return try {
            val sessions = deviceSessionDao.getAllSessionsForUser(userId).first()
            deviceSessionDao.revokeAllOtherSessions(userId)
            for (s in sessions) {
                if (!s.isCurrentDevice && s.isActive) {
                    firestoreService.revokeDeviceSession(userId, s.sessionId)
                }
            }
            true
        } catch (e: Exception) {
            Log.w("ChatRepository", "Failed to revoke all other sessions: ${e.message}")
            false
        }
    }

    suspend fun linkNewDevice(userId: String, deviceName: String, deviceType: String, platform: String): DeviceSessionEntity {
        val newSession = DeviceSessionEntity(
            sessionId = IdGenerator.generateUuid(),
            userId = userId,
            deviceName = deviceName,
            deviceType = deviceType,
            platform = platform,
            clientApp = if (deviceType == "WEB") "ChatConnect Web" else "ChatConnect Desktop Client",
            ipAddress = "82.178.45.25",
            location = "الرياض، المملكة العربية السعودية",
            isCurrentDevice = false,
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            isActive = true
        )
        deviceSessionDao.insertSession(newSession)
        firestoreService.saveDeviceSession(newSession)
        return newSession
    }

    suspend fun updateUserProfile(username: String, bio: String, profilePicUri: String?) {
        val current = _currentUser.value ?: return
        val updated = current.copy(username = username, bio = bio, profilePicUri = profilePicUri)
        userDao.updateUser(updated)
        _currentUser.value = updated
        repositoryScope.launch { firestoreService.saveUser(updated) }
    }

    suspend fun deleteProfilePhoto() {
        val current = _currentUser.value ?: return
        val updated = current.copy(profilePicUri = null)
        userDao.updateUser(updated)
        _currentUser.value = updated
        repositoryScope.launch { firestoreService.saveUser(updated) }
    }

    suspend fun deleteAccount() {
        val current = _currentUser.value ?: return
        clearActiveSession()
        removeUserLoggedInOnThisDevice(current.userId)
        userDao.deleteUser(current.userId)
        _currentUser.value = null
        repositoryScope.launch { authService.signOut() }
    }

    // Contacts
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMyContacts(): Flow<List<ContactEntity>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else contactDao.getContactsForOwner(user.userId)
        }
    }

    suspend fun findUserByCustomId(customId: String): UserEntity? {
        val local = userDao.getUserByCustomId(customId)
        if (local != null) return local
        val cloud = firestoreService.findUserByCustomId(customId)
        if (cloud != null) {
            userDao.insertUser(cloud)
        }
        return cloud
    }

    suspend fun addContactByCustomId(customId: String, nickname: String): String? {
        val current = _currentUser.value ?: return "Not logged in"
        val cleanId = customId.trim()
        if (current.customId.equals(cleanId, ignoreCase = true)) {
            return "لا يمكنك إضافة نفسك كجهة اتصال."
        }
        var targetUser = userDao.getUserByCustomId(cleanId)
        if (targetUser == null) {
            if (cleanId == AdminConstants.ADMIN_CUSTOM_ID) {
                targetUser = userDao.getUserById(AdminConstants.ADMIN_USER_ID) ?: UserEntity(
                    userId = AdminConstants.ADMIN_USER_ID,
                    customId = AdminConstants.ADMIN_CUSTOM_ID,
                    username = AdminConstants.ADMIN_USERNAME,
                    email = AdminConstants.ADMIN_EMAIL,
                    firebaseUid = AdminConstants.ADMIN_USER_ID,
                    passwordHash = PasswordUtils.hashPassword(AdminConstants.ADMIN_PASSWORD),
                    bio = AdminConstants.ADMIN_BIO,
                    isOnline = true,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                ).also {
                    userDao.insertUser(it)
                }
            } else {
                targetUser = firestoreService.findUserByCustomId(cleanId)
                // If not found directly by customId, check Special Identifiers!
                if (targetUser == null) {
                    val special = specialIdentifierDao.getByIdentifier(cleanId)
                        ?: firestoreService.getSpecialIdentifier(cleanId)
                    if (special != null && special.assignedUserId != null) {
                        targetUser = userDao.getUserById(special.assignedUserId)
                            ?: firestoreService.getUser(special.assignedUserId)
                    } else {
                        val pubRes = adminSpecialIdentifierManager.lookupContactByIdentifier(cleanId)
                        val pub = pubRes.getOrNull()
                        if (pub != null) {
                            targetUser = userDao.getUserById(pub.userId) ?: firestoreService.getUser(pub.userId)
                        }
                    }
                }
                if (targetUser != null) {
                    userDao.insertUser(targetUser)
                } else {
                    return "لم يتم العثور على مستخدم بهذا المعرف (ID)."
                }
            }
        }

        if (targetUser.userId == current.userId) {
            return "لا يمكنك إضافة نفسك كجهة اتصال."
        }

        val existing = contactDao.getContact(current.userId, targetUser.userId)
        if (existing != null) {
            return "هذا المستخدم موجود بالفعل في جهات الاتصال."
        }

        val contact = ContactEntity(
            ownerUserId = current.userId,
            contactUserId = targetUser.userId,
            nickname = if (nickname.isBlank()) targetUser.username else nickname.trim()
        )
        contactDao.insertContact(contact)
        return null // Success
    }

    suspend fun updateContactNickname(contactUserId: String, nickname: String) {
        val current = _currentUser.value ?: return
        contactDao.updateNickname(current.userId, contactUserId, nickname)
    }

    suspend fun deleteContact(contactUserId: String) {
        val current = _currentUser.value ?: return
        contactDao.deleteContact(current.userId, contactUserId)
    }

    // Chats
    fun getMyChats(): Flow<List<ChatEntity>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else chatDao.getChatsForUser(user.userId)
        }
    }

    suspend fun getOrCreateIndividualChat(otherUserId: String): ChatEntity {
        val current = _currentUser.value ?: throw IllegalStateException("User not logged in")
        val existing = chatDao.findIndividualChatBetween(current.userId, otherUserId)
        if (existing != null) return existing

        val chatId = IdGenerator.generateUuid()
        val newChat = ChatEntity(
            chatId = chatId,
            type = "INDIVIDUAL",
            createdByUserId = current.userId
        )
        chatDao.insertChat(newChat)
        chatDao.insertChatMembers(
            listOf(
                ChatMemberEntity(chatId = chatId, userId = current.userId, role = "MEMBER"),
                ChatMemberEntity(chatId = chatId, userId = otherUserId, role = "MEMBER")
            )
        )
        repositoryScope.launch {
            firestoreService.saveChat(newChat, listOf(current.userId, otherUserId))
        }
        return newChat
    }

    // --- Group & Chat Management ---

    suspend fun createGroupChat(
        name: String,
        iconUri: String?,
        memberUserIds: List<String>,
        description: String? = null,
        editGroupInfoPermission: String = "ALL",
        sendMessagesPermission: String = "ALL",
        addMembersPermission: String = "ALL"
    ): ChatEntity {
        val current = _currentUser.value ?: throw IllegalStateException("User not logged in")
        val chatId = IdGenerator.generateUuid()
        val inviteToken = UUID.randomUUID().toString().replace("-", "").take(16)
        val timestamp = System.currentTimeMillis()

        val newChat = ChatEntity(
            chatId = chatId,
            type = "GROUP",
            groupName = name.trim(),
            groupIconUri = iconUri,
            groupDescription = description?.trim()?.ifBlank { null },
            createdByUserId = current.userId,
            groupOwnerId = current.userId,
            editGroupInfoPermission = editGroupInfoPermission,
            sendMessagesPermission = sendMessagesPermission,
            addMembersPermission = addMembersPermission,
            inviteToken = inviteToken,
            createdAt = timestamp,
            updatedAt = timestamp,
            lastMessageTimestamp = timestamp,
            isActive = true
        )
        chatDao.insertChat(newChat)

        val allMembers = (memberUserIds + current.userId).distinct().map { uid ->
            ChatMemberEntity(
                chatId = chatId,
                userId = uid,
                role = if (uid == current.userId) "OWNER" else "MEMBER",
                joinedAt = timestamp,
                addedBy = if (uid == current.userId) "" else current.userId
            )
        }
        chatDao.insertChatMembers(allMembers)

        // Add initial system message
        val systemMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "قام ${current.username} بإنشاء المجموعة \"${name.trim()}\"",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(systemMsg)

        repositoryScope.launch {
            firestoreService.saveChat(newChat, allMembers.map { it.userId })
            firestoreService.saveMessage(systemMsg)
        }

        return newChat
    }

    suspend fun getChatById(chatId: String): ChatEntity? = chatDao.getChatById(chatId)

    fun observeChatMembers(chatId: String): Flow<List<ChatMemberEntity>> = chatDao.getChatMembers(chatId)

    fun observeMembersForChats(chatIds: List<String>): Flow<List<ChatMemberEntity>> {
        if (chatIds.isEmpty()) return flowOf(emptyList())
        return chatDao.getMembersForChats(chatIds)
    }

    suspend fun getChatMembersList(chatId: String): List<ChatMemberEntity> = chatDao.getChatMembersList(chatId)

    suspend fun addGroupMembers(chatId: String, newMemberIds: List<String>): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val chat = chatDao.getChatById(chatId) ?: return Result.failure(Exception("Group not found"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        val isPrivileged = callerMember?.role in listOf("OWNER", "ADMIN")

        if (chat.addMembersPermission == "ADMIN_ONLY" && !isPrivileged) {
            return Result.failure(Exception("فقط المشرفون يمكنهم إضافة أعضاء لهذه المجموعة."))
        }

        val existingMembers = chatDao.getChatMembersList(chatId).map { it.userId }.toSet()
        val toAdd = newMemberIds.filter { it !in existingMembers }
        if (toAdd.isEmpty()) return Result.success(Unit)

        val timestamp = System.currentTimeMillis()
        val newEntities = toAdd.map { uid ->
            ChatMemberEntity(
                chatId = chatId,
                userId = uid,
                role = "MEMBER",
                joinedAt = timestamp,
                addedBy = current.userId
            )
        }
        chatDao.insertChatMembers(newEntities)

        for (uid in toAdd) {
            val targetUser = userDao.getUserById(uid)
            val name = targetUser?.username ?: "عضو جديد"
            val sysMsg = MessageEntity(
                messageId = IdGenerator.generateUuid(),
                chatId = chatId,
                senderUserId = current.userId,
                type = "SYSTEM",
                content = "أضاف ${current.username} $name",
                timestamp = timestamp,
                status = "SENT"
            )
            messageDao.insertMessage(sysMsg)
            repositoryScope.launch {
                firestoreService.addMemberToGroupFirestore(chatId, uid)
                firestoreService.saveMessage(sysMsg)
            }
        }
        return Result.success(Unit)
    }

    suspend fun removeGroupMember(chatId: String, targetUserId: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val chat = chatDao.getChatById(chatId) ?: return Result.failure(Exception("Group not found"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        val targetMember = chatDao.getMember(chatId, targetUserId) ?: return Result.failure(Exception("User is not a member"))

        if (targetUserId == chat.groupOwnerId) {
            return Result.failure(Exception("لا يمكن إزالة مالك المجموعة."))
        }

        val isCallerOwner = callerMember?.role == "OWNER"
        val isCallerAdmin = callerMember?.role == "ADMIN"

        if (!isCallerOwner && !isCallerAdmin) {
            return Result.failure(Exception("فقط المشرفون أو المالك يمكنهم إزالة الأعضاء."))
        }
        if (isCallerAdmin && targetMember.role == "ADMIN" && !isCallerOwner) {
            return Result.failure(Exception("المشرف لا يمكنه إزالة مشرف آخر، هذا الحق للمالك فقط."))
        }

        chatDao.removeChatMember(chatId, targetUserId)
        val targetUser = userDao.getUserById(targetUserId)
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "قام ${current.username} بإزالة ${targetUser?.username ?: "عضو"}",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.removeMemberFromGroupFirestore(chatId, targetUserId)
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun updateMemberRole(chatId: String, targetUserId: String, newRole: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        if (callerMember?.role != "OWNER") {
            return Result.failure(Exception("فقط مالك المجموعة يمكنه ترقية أو إعفاء المشرفين."))
        }

        chatDao.updateMemberRole(chatId, targetUserId, newRole)
        val targetUser = userDao.getUserById(targetUserId)
        val roleArabic = if (newRole == "ADMIN") "مشرفاً" else "عضواً"
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "عيّن ${current.username} ${targetUser?.username ?: "العضو"} $roleArabic",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun transferGroupOwnership(chatId: String, newOwnerUserId: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        if (callerMember?.role != "OWNER") {
            return Result.failure(Exception("فقط مالك المجموعة الحالي يمكنه نقل الملكية."))
        }

        chatDao.updateGroupOwner(chatId, newOwnerUserId)
        chatDao.updateMemberRole(chatId, current.userId, "ADMIN")
        chatDao.updateMemberRole(chatId, newOwnerUserId, "OWNER")

        val targetUser = userDao.getUserById(newOwnerUserId)
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "نقل ${current.username} ملكية المجموعة إلى ${targetUser?.username ?: "المالك الجديد"}",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.updateGroupOwnerFirestore(chatId, newOwnerUserId)
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun updateGroupDetails(chatId: String, name: String, iconUri: String?, description: String?): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val chat = chatDao.getChatById(chatId) ?: return Result.failure(Exception("Group not found"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        val isPrivileged = callerMember?.role in listOf("OWNER", "ADMIN")

        if (chat.editGroupInfoPermission == "ADMIN_ONLY" && !isPrivileged) {
            return Result.failure(Exception("فقط المشرفون يمكنهم تعديل معلومات المجموعة."))
        }

        chatDao.updateGroupInfoFull(chatId, name.trim(), iconUri, description?.trim()?.ifBlank { null })
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "قام ${current.username} بتعديل معلومات المجموعة",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.updateGroupFirestoreInfo(chatId, name.trim(), iconUri, description?.trim()?.ifBlank { null })
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun updateGroupPermissions(chatId: String, editInfo: String, sendMessages: String, addMembers: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        if (callerMember?.role !in listOf("OWNER", "ADMIN")) {
            return Result.failure(Exception("فقط المشرفون أو المالك يمكنهم تغيير إعدادات الصلاحيات."))
        }

        chatDao.updateGroupPermissions(chatId, editInfo, sendMessages, addMembers)
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "قام ${current.username} بتعديل صلاحيات المجموعة",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.updateGroupPermissionsFirestore(chatId, editInfo, sendMessages, addMembers)
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun resetGroupInviteToken(chatId: String): Result<String> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        if (callerMember?.role !in listOf("OWNER", "ADMIN")) {
            return Result.failure(Exception("فقط المشرفون يمكنهم إعادة ضبط رابط الدعوة."))
        }
        val newToken = UUID.randomUUID().toString().replace("-", "").take(16)
        chatDao.updateInviteToken(chatId, newToken)
        repositoryScope.launch {
            firestoreService.updateGroupInviteTokenFirestore(chatId, newToken)
        }
        return Result.success(newToken)
    }

    suspend fun joinGroupByInviteToken(token: String): Result<ChatEntity> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val cleanToken = token.trim().removePrefix("chatconnect://invite/").removePrefix("https://chatconnect.app/invite/")
        var chat = chatDao.getChatByInviteToken(cleanToken)
        if (chat == null) {
            chat = firestoreService.findGroupByInviteToken(cleanToken)
            if (chat != null) {
                chatDao.insertChat(chat)
            }
        }
        if (chat == null) {
            return Result.failure(Exception("رابط الدعوة غير صالح أو انتهت صلاحيته."))
        }

        val existingMember = chatDao.getMember(chat.chatId, current.userId)
        if (existingMember != null) {
            return Result.success(chat)
        }

        val timestamp = System.currentTimeMillis()
        val newMember = ChatMemberEntity(
            chatId = chat.chatId,
            userId = current.userId,
            role = "MEMBER",
            joinedAt = timestamp,
            addedBy = "INVITE_LINK"
        )
        chatDao.insertChatMember(newMember)

        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chat.chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "انضم ${current.username} عبر رابط الدعوة",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.addMemberToGroupFirestore(chat.chatId, current.userId)
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(chat)
    }

    suspend fun leaveGroup(chatId: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val chat = chatDao.getChatById(chatId) ?: return Result.failure(Exception("Group not found"))
        val callerMember = chatDao.getMember(chatId, current.userId) ?: return Result.failure(Exception("Not a member"))

        val allMembers = chatDao.getChatMembersList(chatId)
        val otherMembers = allMembers.filter { it.userId != current.userId }

        if (callerMember.role == "OWNER") {
            if (otherMembers.isNotEmpty()) {
                val newOwnerCandidate = otherMembers.firstOrNull { it.role == "ADMIN" } ?: otherMembers.first()
                transferGroupOwnership(chatId, newOwnerCandidate.userId)
            } else {
                deleteGroup(chatId)
                return Result.success(Unit)
            }
        }

        chatDao.removeChatMember(chatId, current.userId)
        val timestamp = System.currentTimeMillis()
        val sysMsg = MessageEntity(
            messageId = IdGenerator.generateUuid(),
            chatId = chatId,
            senderUserId = current.userId,
            type = "SYSTEM",
            content = "غادر ${current.username} المجموعة",
            timestamp = timestamp,
            status = "SENT"
        )
        messageDao.insertMessage(sysMsg)

        repositoryScope.launch {
            firestoreService.removeMemberFromGroupFirestore(chatId, current.userId)
            firestoreService.saveMessage(sysMsg)
        }
        return Result.success(Unit)
    }

    suspend fun pinMessage(chatId: String, messageId: String?): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val callerMember = chatDao.getMember(chatId, current.userId)
        if (callerMember?.role !in listOf("OWNER", "ADMIN")) {
            return Result.failure(Exception("فقط المشرفون يمكنهم تثبيت الرسائل."))
        }

        chatDao.updatePinnedMessage(chatId, messageId)
        if (messageId != null) {
            val msg = messageDao.getMessageById(messageId)
            val snippet = msg?.content?.take(25) ?: "رسالة"
            val sysMsg = MessageEntity(
                messageId = IdGenerator.generateUuid(),
                chatId = chatId,
                senderUserId = current.userId,
                type = "SYSTEM",
                content = "قام ${current.username} بتثبيت: $snippet",
                timestamp = System.currentTimeMillis(),
                status = "SENT"
            )
            messageDao.insertMessage(sysMsg)
            repositoryScope.launch {
                firestoreService.pinMessageInGroupFirestore(chatId, messageId)
                firestoreService.saveMessage(sysMsg)
            }
        } else {
            repositoryScope.launch {
                firestoreService.pinMessageInGroupFirestore(chatId, null)
            }
        }
        return Result.success(Unit)
    }

    suspend fun deleteGroup(chatId: String): Result<Unit> {
        val current = _currentUser.value ?: return Result.failure(Exception("User not logged in"))
        val chat = chatDao.getChatById(chatId) ?: return Result.failure(Exception("Group not found"))
        if (chat.groupOwnerId != current.userId && chat.createdByUserId != current.userId) {
            return Result.failure(Exception("فقط مالك المجموعة يمكنه حذف المجموعة نهائياً."))
        }

        chatDao.deleteChat(chatId)
        repositoryScope.launch {
            firestoreService.deleteGroupFirestore(chatId)
        }
        return Result.success(Unit)
    }

    fun observeMediaMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getMediaMessagesForChat(chatId)

    fun observeDocumentMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getDocumentMessagesForChat(chatId)

    suspend fun getMessageById(messageId: String): MessageEntity? = messageDao.getMessageById(messageId)

    // Messages with Firestore persistence
    fun observeMessages(chatId: String): Flow<List<MessageEntity>> {
        repositoryScope.launch {
            try {
                firestoreService.observeMessages(chatId).collect { cloudMessages ->
                    for (msg in cloudMessages) {
                        messageDao.insertMessage(msg)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Firestore message sync error: ${e.message}")
            }
        }
        return messageDao.getMessagesForChat(chatId)
    }

    suspend fun sendMessage(
        chatId: String,
        type: String,
        content: String = "",
        mediaUri: String? = null,
        mediaFileName: String? = null,
        mediaFileSize: Long = 0,
        mediaDurationMs: Long = 0,
        caption: String? = null,
        mimeType: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAddress: String? = null,
        contactName: String? = null,
        contactPhone: String? = null,
        contactEmail: String? = null
    ): MessageEntity {
        val current = _currentUser.value ?: throw IllegalStateException("User not logged in")

        val chat = chatDao.getChatById(chatId)
        if (chat != null && chat.type == "GROUP") {
            if (chat.sendMessagesPermission == "ADMIN_ONLY") {
                val member = chatDao.getMember(chatId, current.userId)
                if (member?.role !in listOf("OWNER", "ADMIN")) {
                    throw IllegalStateException("فقط المشرفون يمكنهم إرسال الرسائل في هذه المجموعة.")
                }
            }
        } else if (chat != null && chat.type == "INDIVIDUAL") {
            val chatMembers = chatDao.getChatMembersList(chatId)
            val targetUser = chatMembers.firstOrNull { it.userId != current.userId }
            if (targetUser != null && blockedUserDao.isBlocked(targetUser.userId, current.userId)) {
                throw IllegalStateException("You are blocked by this user.")
            }
        }

        val messageId = IdGenerator.generateUuid()
        val timestamp = System.currentTimeMillis()
        val isMediaAttachment = type in listOf("IMAGE", "VIDEO", "FILE", "AUDIO", "VOICE")
        val initialStatus = if (isMediaAttachment) "UPLOADING" else "SENT"
        val initialProgress = if (isMediaAttachment) 0.2f else 1.0f

        val message = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderUserId = current.userId,
            type = type,
            content = content,
            mediaUri = mediaUri,
            mediaFileName = mediaFileName,
            mediaFileSize = mediaFileSize,
            mediaDurationMs = mediaDurationMs,
            caption = caption,
            timestamp = timestamp,
            status = initialStatus,
            uploadProgress = initialProgress,
            mimeType = mimeType,
            latitude = latitude,
            longitude = longitude,
            locationAddress = locationAddress,
            contactName = contactName,
            contactPhone = contactPhone,
            contactEmail = contactEmail
        )
        messageDao.insertMessage(message)
        chatDao.updateLastMessageTime(chatId, timestamp)

        // Save Attachment record if media
        if (isMediaAttachment && mediaUri != null) {
            val attachment = com.example.data.local.entities.AttachmentEntity(
                attachmentId = IdGenerator.generateUuid(),
                messageId = messageId,
                chatId = chatId,
                type = type,
                fileName = mediaFileName,
                mimeType = mimeType,
                fileSize = mediaFileSize,
                storagePath = mediaUri,
                durationMs = mediaDurationMs,
                latitude = latitude,
                longitude = longitude,
                address = locationAddress,
                contactName = contactName,
                contactPhone = contactPhone,
                contactEmail = contactEmail,
                uploadProgress = initialProgress,
                uploadStatus = if (isMediaAttachment) "UPLOADING" else "SUCCESS"
            )
            attachmentDao.insertAttachment(attachment)
        }

        repositoryScope.launch {
            try {
                if (isMediaAttachment) {
                    kotlinx.coroutines.delay(250)
                    messageDao.updateUploadProgress(messageId, "UPLOADING", 0.65f)
                    kotlinx.coroutines.delay(200)
                    messageDao.updateUploadProgress(messageId, "SENT", 1.0f)
                }
                val finalMsg = messageDao.getMessageById(messageId) ?: message.copy(status = "SENT", uploadProgress = 1.0f)
                firestoreService.saveMessage(finalMsg)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed uploading/sending message: ${e.message}")
                messageDao.updateUploadProgress(messageId, "FAILED", 0f)
            }
        }

        return message
    }

    suspend fun retrySendMessage(messageId: String) {
        val msg = messageDao.getMessageById(messageId) ?: return
        messageDao.updateUploadProgress(messageId, "UPLOADING", 0.3f)
        repositoryScope.launch {
            try {
                kotlinx.coroutines.delay(300)
                messageDao.updateUploadProgress(messageId, "SENT", 1.0f)
                val finalMsg = messageDao.getMessageById(messageId) ?: msg.copy(status = "SENT", uploadProgress = 1.0f)
                firestoreService.saveMessage(finalMsg)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed retrying message: ${e.message}")
                messageDao.updateUploadProgress(messageId, "FAILED", 0f)
            }
        }
    }

    suspend fun cancelSendMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
        attachmentDao.deleteAttachmentsForMessage(messageId)
    }

    suspend fun markChatAsRead(chatId: String) {
        val current = _currentUser.value ?: return
        val readAt = System.currentTimeMillis()
        
        // 1. Fetch unread messages BEFORE local update so we have the message IDs
        val unreadList = try {
            messageDao.getUnreadMessagesForChatSync(chatId, current.userId)
        } catch (e: Exception) {
            emptyList()
        }
        val unreadIds = unreadList.map { it.messageId }

        // 2. Mark local messages as READ with readAt timestamp immediately for responsive UI
        messageDao.markChatMessagesAsRead(chatId, current.userId, "READ", readAt)

        // 3. Synchronize read receipts with Firestore in real-time so sender receives double blue ticks
        repositoryScope.launch {
            try {
                firestoreService.markChatAsReadInFirestore(chatId, current.userId, readAt, unreadIds)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Failed to sync read receipt to Firestore: ${e.message}")
            }
        }
    }

    // --- Message Reactions Persistence & Sync (Sub-collection) ---

    private val activeReactionListenerJobs = ConcurrentHashMap<String, Job>()

    fun observeReactionsForChat(chatId: String): Flow<List<com.example.data.local.entities.MessageReactionEntity>> {
        // Automatically sync reactions from Firestore's sub-collection for messages in this chat in real-time
        repositoryScope.launch {
            try {
                messageDao.getMessagesForChat(chatId).collect { messages ->
                    for (msg in messages) {
                        val key = "${chatId}_${msg.messageId}"
                        if (!activeReactionListenerJobs.containsKey(key)) {
                            val job = launch {
                                try {
                                    firestoreService.observeMessageReactions(chatId, msg.messageId).collect { remoteReactions ->
                                        // Synchronize with local DB: remove local reactions deleted in Firestore
                                        val remoteUserIds = remoteReactions.map { it.userId }.toSet()
                                        val local = messageReactionDao.getReactionsForMessageSync(msg.messageId)
                                        for (loc in local) {
                                            if (loc.userId !in remoteUserIds) {
                                                messageReactionDao.deleteReaction(msg.messageId, loc.userId)
                                            }
                                        }
                                        if (remoteReactions.isNotEmpty()) {
                                            messageReactionDao.insertReactions(remoteReactions)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("ChatRepository", "Error observing reactions for msg ${msg.messageId}: ${e.message}")
                                }
                            }
                            activeReactionListenerJobs[key] = job
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Error in reactions collector for chat $chatId: ${e.message}")
            }
        }
        return messageReactionDao.getReactionsForChat(chatId)
    }

    fun observeReactionsForMessage(messageId: String): Flow<List<com.example.data.local.entities.MessageReactionEntity>> =
        messageReactionDao.getReactionsForMessage(messageId)

    suspend fun toggleMessageReaction(chatId: String, messageId: String, emoji: String) {
        val current = _currentUser.value ?: return
        val existing = messageReactionDao.getReactionsForChatSync(chatId)
            .firstOrNull { it.messageId == messageId && it.userId == current.userId }

        if (existing != null && existing.emoji == emoji) {
            // User tapped the same emoji -> remove reaction locally and in Firestore sub-collection
            messageReactionDao.deleteReaction(messageId, current.userId)
            repositoryScope.launch {
                firestoreService.removeMessageReaction(chatId, messageId, current.userId)
            }
        } else {
            // Add or replace emoji reaction locally and in Firestore sub-collection
            val reaction = com.example.data.local.entities.MessageReactionEntity(
                messageId = messageId,
                chatId = chatId,
                userId = current.userId,
                username = current.username,
                emoji = emoji,
                timestamp = System.currentTimeMillis()
            )
            messageReactionDao.insertReaction(reaction)
            repositoryScope.launch {
                firestoreService.saveMessageReaction(chatId, messageId, current.userId, current.username, emoji)
            }
        }
        // Ensure listener is active so real-time updates from other users continue smoothly
        syncMessageReactionsFromFirestore(chatId, messageId)
    }

    fun syncMessageReactionsFromFirestore(chatId: String, messageId: String) {
        val key = "${chatId}_${messageId}"
        if (activeReactionListenerJobs.containsKey(key)) return
        val job = repositoryScope.launch {
            try {
                firestoreService.observeMessageReactions(chatId, messageId).collect { remoteReactions ->
                    val remoteUserIds = remoteReactions.map { it.userId }.toSet()
                    val local = messageReactionDao.getReactionsForMessageSync(messageId)
                    for (loc in local) {
                        if (loc.userId !in remoteUserIds) {
                            messageReactionDao.deleteReaction(messageId, loc.userId)
                        }
                    }
                    if (remoteReactions.isNotEmpty()) {
                        messageReactionDao.insertReactions(remoteReactions)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Reaction sync error for msg $messageId: ${e.message}")
            }
        }
        activeReactionListenerJobs[key] = job
    }

    // --- Local Search across Chats and Message Content ---

    suspend fun searchChatIdsByMessageContent(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return messageDao.getChatIdsMatchingMessageContentSync(query.trim())
    }

    fun searchMessagesAcrossChats(query: String): Flow<List<MessageEntity>> =
        messageDao.searchMessagesAcrossAllChats(query.trim())

    // --- Contact Discovery via SpecialIdentifiers ---

    suspend fun lookupContactByVanityId(vanityId: String): Result<com.example.data.model.PublicUserProfile?> {
        return specialIdentifierManager.lookupPublicProfileByVanityId(vanityId)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    // Statuses
    fun observeAllActiveStatuses(): Flow<List<StatusEntity>> {
        repositoryScope.launch {
            try {
                firestoreService.observeActiveStatuses().collect { cloudStatuses ->
                    for (st in cloudStatuses) {
                        statusDao.insertStatus(st)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Firestore status sync error: ${e.message}")
            }
        }
        return statusDao.getAllActiveStatuses()
    }

    suspend fun postStatus(
        type: String,
        contentText: String? = null,
        mediaUri: String? = null,
        caption: String? = null,
        bgColorHex: String = "#075E54",
        textStyle: String = "NORMAL",
        textAlignment: String = "CENTER",
        privacyType: String = "MY_CONTACTS",
        privacyTargetIds: String = ""
    ) {
        val current = _currentUser.value ?: return
        val status = StatusEntity(
            statusId = IdGenerator.generateUuid(),
            userId = current.userId,
            type = type,
            contentText = contentText,
            mediaUri = mediaUri,
            caption = caption,
            bgColorHex = bgColorHex,
            textStyle = textStyle,
            textAlignment = textAlignment,
            privacyType = privacyType,
            privacyTargetIds = privacyTargetIds,
            isDeleted = false,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
        )
        statusDao.insertStatus(status)

        repositoryScope.launch {
            firestoreService.saveStatus(status)
        }
    }

    suspend fun deleteStatus(statusId: String) {
        statusDao.deleteStatus(statusId)
        repositoryScope.launch {
            firestoreService.deleteStatus(statusId)
        }
    }

    suspend fun recordStatusView(statusId: String) {
        val current = _currentUser.value ?: return
        val status = statusDao.getStatusById(statusId) ?: return
        if (status.userId == current.userId) return // Do not record self view

        val existing = statusDao.getViewByViewer(statusId, current.userId)
        if (existing == null) {
            val view = StatusViewEntity(
                statusId = statusId,
                viewerUserId = current.userId,
                viewerName = current.username,
                viewerProfilePic = current.profilePicUri,
                statusOwnerUserId = status.userId,
                viewedAt = System.currentTimeMillis()
            )
            statusDao.insertStatusView(view)
            repositoryScope.launch {
                firestoreService.recordStatusView(view)
            }
        }
    }

    fun observeStatusViews(statusId: String): Flow<List<StatusViewEntity>> {
        repositoryScope.launch {
            try {
                firestoreService.observeStatusViewsForStatus(statusId).collect { cloudViews ->
                    for (v in cloudViews) {
                        statusDao.insertStatusView(v)
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Status views sync error: ${e.message}")
            }
        }
        return statusDao.getViewsForStatus(statusId)
    }

    fun observeStatusViewsCount(statusId: String): Flow<Int> = statusDao.getViewsCountForStatus(statusId)

    fun observeViewedStatusIds(): Flow<List<String>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else statusDao.getViewedStatusIdsForUser(user.userId)
        }
    }

    suspend fun muteUserStatus(mutedUserId: String) {
        val current = _currentUser.value ?: return
        val mute = StatusMuteEntity(userId = current.userId, mutedUserId = mutedUserId)
        statusDao.insertStatusMute(mute)
        repositoryScope.launch {
            firestoreService.saveStatusMute(current.userId, mutedUserId)
        }
    }

    suspend fun unmuteUserStatus(mutedUserId: String) {
        val current = _currentUser.value ?: return
        statusDao.deleteStatusMute(current.userId, mutedUserId)
        repositoryScope.launch {
            firestoreService.deleteStatusMute(current.userId, mutedUserId)
        }
    }

    fun observeMutedUserIds(): Flow<List<String>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else {
                repositoryScope.launch {
                    try {
                        firestoreService.observeUserStatusMutes(user.userId).collect { cloudMuted ->
                            for (mId in cloudMuted) {
                                statusDao.insertStatusMute(StatusMuteEntity(userId = user.userId, mutedUserId = mId))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Mute sync error: ${e.message}")
                    }
                }
                statusDao.observeMutedUserIds(user.userId)
            }
        }
    }

    suspend fun replyToStatus(statusId: String, replyText: String) {
        val current = _currentUser.value ?: return
        val status = statusDao.getStatusById(statusId) ?: return
        if (status.userId == current.userId) return
        val chat = getOrCreateIndividualChat(status.userId)
        val preview = status.caption?.takeIf { it.isNotBlank() }
            ?: status.contentText?.takeIf { it.isNotBlank() }
            ?: (if (status.type == "VIDEO") "فيديو" else if (status.type == "IMAGE") "صورة" else "حالة")
        val content = "رد على الحالة: \"$preview\"\n$replyText"
        sendMessage(chatId = chat.chatId, type = "TEXT", content = content)
    }

    suspend fun reactToStatus(statusId: String, reactionEmoji: String) {
        val current = _currentUser.value ?: return
        val status = statusDao.getStatusById(statusId) ?: return
        if (status.userId == current.userId) return
        val chat = getOrCreateIndividualChat(status.userId)
        val preview = status.caption?.takeIf { it.isNotBlank() }
            ?: status.contentText?.takeIf { it.isNotBlank() }
            ?: (if (status.type == "VIDEO") "فيديو" else if (status.type == "IMAGE") "صورة" else "حالة")
        val content = "تفاعل $reactionEmoji مع حالتك: \"$preview\""
        sendMessage(chatId = chat.chatId, type = "TEXT", content = content)
    }

    suspend fun updateStatusPrivacySettings(privacy: String, excludedIds: String, includedIds: String) {
        val current = _currentUser.value ?: return
        val currentSettings = userSettingsDao.getSettings(current.userId) ?: UserSettingsEntity(userId = current.userId)
        val updated = currentSettings.copy(
            statusPrivacy = privacy,
            statusPrivacyExcludedIds = excludedIds,
            statusPrivacyIncludedIds = includedIds
        )
        userSettingsDao.insertOrUpdateSettings(updated)
    }

    // Calls
    fun observeCallLogs(): Flow<List<CallLogEntity>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else callDao.getCallLogsForUser(user.userId)
        }
    }

    suspend fun saveCallLog(
        callId: String,
        callerUserId: String,
        receiverUserId: String,
        isVideo: Boolean,
        status: String,
        durationSec: Int
    ) {
        val log = CallLogEntity(
            callId = callId,
            callerUserId = callerUserId,
            receiverUserId = receiverUserId,
            isVideo = isVideo,
            status = status,
            durationSec = durationSec,
            timestamp = System.currentTimeMillis()
        )
        callDao.insertCallLog(log)
    }

    suspend fun initiateCall(targetUserId: String, isVideo: Boolean) {
        val current = _currentUser.value ?: return
        var targetUser = userDao.getUserById(targetUserId)
        if (targetUser == null) {
            targetUser = firestoreService.findUserByCustomId(targetUserId)
            if (targetUser != null) {
                userDao.insertUser(targetUser)
            }
        }
        val targetName = targetUser?.username ?: "جهة اتصال"
        val callId = IdGenerator.generateUuid()
        RealtimeCallDispatcher.initiateCall(
            callId = callId,
            callerUserId = current.userId,
            callerName = current.username,
            callerAvatar = current.profilePicUri,
            receiverUserId = targetUserId,
            receiverName = targetName,
            isVideo = isVideo
        )
    }

    /**
     * Observes real-time call history synced from Firestore 'call_history' collection.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCloudCallHistory(): Flow<List<CallHistoryRecord>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else firestoreService.observeUserCallHistory(user.userId)
        }
    }

    // User Settings
    fun observeUserSettings(): Flow<UserSettingsEntity?> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(null)
            else userSettingsDao.observeSettings(user.userId)
        }
    }

    suspend fun updateSettings(settings: UserSettingsEntity) {
        userSettingsDao.insertOrUpdateSettings(settings)
    }

    // Blocked Users
    suspend fun blockUser(targetUserId: String) {
        val current = _currentUser.value ?: return
        blockedUserDao.blockUser(BlockedUserEntity(blockerUserId = current.userId, blockedUserId = targetUserId))
    }

    suspend fun unblockUser(targetUserId: String) {
        val current = _currentUser.value ?: return
        blockedUserDao.unblockUser(current.userId, targetUserId)
    }

    fun observeBlockedUsers(): Flow<List<BlockedUserEntity>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else blockedUserDao.getBlockedUsers(user.userId)
        }
    }

    // ==========================================
    // Admin Special Identifier Manager
    // ==========================================

    fun isCurrentUserAdmin(): Boolean {
        val current = _currentUser.value ?: return false
        return current.userId == AdminConstants.ADMIN_USER_ID ||
                current.customId == AdminConstants.ADMIN_CUSTOM_ID ||
                current.email == AdminConstants.ADMIN_EMAIL
    }

    suspend fun createSpecialIdentifier(identifier: String, category: String, notes: String?): Result<SpecialIdentifierEntity> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية إنشاء المعرفات المميزة."))
        }
        val adminId = _currentUser.value?.userId ?: AdminConstants.ADMIN_USER_ID
        return adminSpecialIdentifierManager.createSpecialIdentifier(
            identifier = identifier,
            category = category,
            notes = notes,
            adminUserId = adminId
        )
    }

    suspend fun reserveSpecialIdentifier(identifierId: String, reservedForName: String, notes: String?): Result<Unit> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية حجز المعرفات المميزة."))
        }
        val adminId = _currentUser.value?.userId ?: AdminConstants.ADMIN_USER_ID
        return adminSpecialIdentifierManager.reserveSpecialIdentifier(
            identifier = identifierId,
            reservedForName = reservedForName,
            notes = notes,
            adminUserId = adminId
        )
    }

    suspend fun assignSpecialIdentifierToUser(identifierId: String, targetUserId: String): Result<Unit> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية تعيين المعرفات المميزة."))
        }
        val targetUser = userDao.getUserById(targetUserId) ?: firestoreService.getUser(targetUserId)
        val adminId = _currentUser.value?.userId ?: AdminConstants.ADMIN_USER_ID
        val result = adminSpecialIdentifierManager.assignSpecialIdentifierToUser(
            identifier = identifierId,
            targetUserId = targetUserId,
            targetUsername = targetUser?.username,
            adminUserId = adminId
        )
        if (result.isSuccess && targetUser != null) {
            val updatedUser = targetUser.copy(customId = identifierId.trim().uppercase())
            if (_currentUser.value?.userId == targetUser.userId) {
                _currentUser.value = updatedUser
            }
        }
        return result
    }

    suspend fun releaseSpecialIdentifier(identifierId: String): Result<Unit> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية إدارة المعرفات المميزة."))
        }
        val adminId = _currentUser.value?.userId ?: AdminConstants.ADMIN_USER_ID
        val result = adminSpecialIdentifierManager.releaseSpecialIdentifier(
            identifier = identifierId,
            adminUserId = adminId
        )
        if (result.isSuccess) {
            val local = userDao.getUserById(_currentUser.value?.userId ?: "")
            if (local != null) {
                _currentUser.value = local
            }
        }
        return result
    }

    suspend fun disableSpecialIdentifier(identifierId: String, reason: String?): Result<Unit> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية إدارة المعرفات المميزة."))
        }
        val adminId = _currentUser.value?.userId ?: AdminConstants.ADMIN_USER_ID
        val result = adminSpecialIdentifierManager.disableSpecialIdentifier(
            identifier = identifierId,
            reason = reason,
            adminUserId = adminId
        )
        if (result.isSuccess) {
            val local = userDao.getUserById(_currentUser.value?.userId ?: "")
            if (local != null) {
                _currentUser.value = local
            }
        }
        return result
    }

    suspend fun deleteSpecialIdentifier(identifierId: String): Result<Unit> {
        if (!isCurrentUserAdmin()) {
            return Result.failure(SecurityException("فقط المسؤول العام (Admin) يملك صلاحية حذف المعرفات المميزة."))
        }
        return adminSpecialIdentifierManager.deleteSpecialIdentifier(identifierId)
    }

    /**
     * Contact Discovery lookup without exposing sensitive OAuth UIDs, email, or credentials.
     */
    suspend fun lookupContactByIdentifier(identifier: String): Result<com.example.data.model.PublicUserProfile?> {
        return adminSpecialIdentifierManager.lookupContactByIdentifier(identifier)
    }

    fun searchSpecialIdentifiers(query: String): Flow<List<SpecialIdentifierEntity>> {
        val clean = query.trim()
        return if (clean.isEmpty()) specialIdentifierDao.getAll()
        else specialIdentifierDao.searchIdentifiers(clean)
    }
}
