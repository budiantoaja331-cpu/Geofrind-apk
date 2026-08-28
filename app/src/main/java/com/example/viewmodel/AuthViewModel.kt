package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.User
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth? by lazy {
        try { FirebaseAuth.getInstance() } catch (e: Throwable) { null }
    }
    private val firestore: FirebaseFirestore? by lazy {
        try { FirebaseFirestore.getInstance() } catch (e: Throwable) { null }
    }

    private var currentUserListener: ListenerRegistration? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val fbUser = try { auth?.currentUser } catch (e: Throwable) { null }
        if (fbUser != null) {
            _currentUser.value = User(
                id = fbUser.uid,
                name = fbUser.displayName ?: "Pengguna Global",
                profilePic = fbUser.photoUrl?.toString() ?: "",
                country = "Indonesia"
            )
            attachUserListener(fbUser.uid)
        }
    }

    fun attachUserListener(uid: String) {
        currentUserListener?.remove()
        currentUserListener = firestore?.collection("users")?.document(uid)
            ?.addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.w("AuthViewModel", "User snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (doc != null && doc.exists()) {
                    val current = _currentUser.value
                    val id = doc.getString("id") ?: uid
                    val name = doc.getString("name") ?: (current?.name ?: "Pengguna")
                    val profilePic = doc.getString("profilePic") ?: (current?.profilePic ?: "")
                    val lat = doc.getDouble("latitude") ?: (current?.latitude ?: 0.0)
                    val lon = doc.getDouble("longitude") ?: (current?.longitude ?: 0.0)
                    val country = doc.getString("country") ?: (current?.country ?: "Indonesia")
                    val isOnline = doc.getBoolean("isOnline") ?: true
                    val lastActive = doc.getLong("lastActive") ?: System.currentTimeMillis()
                    val blocked = (doc.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    _currentUser.value = User(
                        id = id,
                        name = name,
                        profilePic = profilePic,
                        latitude = lat,
                        longitude = lon,
                        country = country,
                        isOnline = isOnline,
                        lastActive = lastActive,
                        blockedUsers = blocked
                    )
                }
            }
    }

    fun refreshUserLocation(context: Context) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc != null) {
                    val updated = user.copy(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        country = loc.country
                    )
                    _currentUser.value = updated
                    firestore?.collection("users")?.document(updated.id)?.update(
                        mapOf(
                            "latitude" to loc.latitude,
                            "longitude" to loc.longitude,
                            "country" to loc.country
                        )
                    )?.await()
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to refresh user location: ${e.message}")
            }
        }
    }

    fun signInWithGoogle(context: Context, webClientId: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                if (webClientId.isNotBlank()) {
                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(webClientId)
                        .setAutoSelectEnabled(false)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context = context, request = request)
                    val credential = result.credential
                    if (credential is GoogleIdTokenCredential) {
                        val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                        val authResult = auth?.signInWithCredential(firebaseCredential)?.await()
                        val fbUser = authResult?.user
                        if (fbUser != null) {
                            saveUserToFirestore(context, fbUser.uid, fbUser.displayName ?: "User", fbUser.photoUrl?.toString() ?: "")
                            attachUserListener(fbUser.uid)
                            return@launch
                        }
                    }
                }
                // Fallback to quick demo sign in if Web Client ID is not configured or fails
                signInDemoUser(context, "Siti Rahma", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150")
            } catch (e: Exception) {
                Log.w("AuthViewModel", "Google sign-in error, using fallback: ${e.message}")
                signInDemoUser(context, "Budi Santoso", "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInDemoUser(context: Context, name: String = "Budi Santoso", profilePic: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val demoUid = "demo_user_${System.currentTimeMillis() % 10000}"
            val pic = if (profilePic.isNotBlank()) profilePic else "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150"
            saveUserToFirestore(context, demoUid, name, pic)
            attachUserListener(demoUid)
            _isLoading.value = false
        }
    }

    private suspend fun saveUserToFirestore(
        context: Context,
        uid: String,
        name: String,
        profilePic: String
    ) {
        val locationResult = LocationHelper.getCurrentLocation(context)
        val lat = locationResult?.latitude ?: -6.2088
        val lon = locationResult?.longitude ?: 106.8456
        val country = locationResult?.country ?: "Indonesia"

        // Fetch existing blocked users if document already exists
        var existingBlockedUsers = _currentUser.value?.blockedUsers ?: emptyList()
        try {
            val existingDoc = firestore?.collection("users")?.document(uid)?.get()?.await()
            if (existingDoc != null && existingDoc.exists()) {
                val dbBlocked = (existingDoc.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String }
                if (dbBlocked != null) {
                    existingBlockedUsers = (existingBlockedUsers + dbBlocked).distinct()
                }
            }
        } catch (_: Exception) {}

        val user = User(
            id = uid,
            name = name,
            profilePic = profilePic,
            latitude = lat,
            longitude = lon,
            country = country,
            isOnline = true,
            lastActive = System.currentTimeMillis(),
            blockedUsers = existingBlockedUsers
        )

        _currentUser.value = user

        try {
            val userMap = hashMapOf(
                "id" to uid,
                "name" to name,
                "profilePic" to profilePic,
                "latitude" to lat,
                "longitude" to lon,
                "country" to country,
                "isOnline" to true,
                "lastActive" to System.currentTimeMillis(),
                "blockedUsers" to existingBlockedUsers
            )
            firestore?.collection("users")?.document(uid)?.set(userMap, SetOptions.merge())?.await()
            NotificationHelper.syncFcmTokenToFirestore(uid)
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Failed to write user to Firestore: ${e.message}")
        }
    }

    fun blockUser(targetUserId: String) {
        val user = _currentUser.value ?: return
        if (targetUserId.isBlank() || targetUserId == user.id) return

        val updatedBlocked = (user.blockedUsers + targetUserId).distinct()
        _currentUser.value = user.copy(blockedUsers = updatedBlocked)

        viewModelScope.launch {
            try {
                firestore?.collection("users")?.document(user.id)?.update(
                    "blockedUsers", FieldValue.arrayUnion(targetUserId)
                )?.await()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to block user in Firestore: ${e.message}")
                // Fallback using set with merge if document or field didn't exist
                try {
                    firestore?.collection("users")?.document(user.id)?.set(
                        mapOf("blockedUsers" to updatedBlocked),
                        SetOptions.merge()
                    )?.await()
                } catch (_: Exception) {}
            }
        }
    }

    fun unblockUser(targetUserId: String) {
        val user = _currentUser.value ?: return
        if (targetUserId.isBlank()) return

        val updatedBlocked = user.blockedUsers.filter { it != targetUserId }
        _currentUser.value = user.copy(blockedUsers = updatedBlocked)

        viewModelScope.launch {
            try {
                firestore?.collection("users")?.document(user.id)?.update(
                    "blockedUsers", FieldValue.arrayRemove(targetUserId)
                )?.await()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to unblock user in Firestore: ${e.message}")
                // Fallback using set with merge
                try {
                    firestore?.collection("users")?.document(user.id)?.set(
                        mapOf("blockedUsers" to updatedBlocked),
                        SetOptions.merge()
                    )?.await()
                } catch (_: Exception) {}
            }
        }
    }

    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                val user = _currentUser.value
                if (user != null) {
                    try {
                        firestore?.collection("users")?.document(user.id)?.update(
                            mapOf(
                                "isOnline" to true,
                                "lastActive" to System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Heartbeat update error: ${e.message}")
                    }
                }
                kotlinx.coroutines.delay(45_000) // Heartbeat every 45 seconds while active
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun setUserOnlineStatus(isOnline: Boolean) {
        val user = _currentUser.value ?: return
        val now = System.currentTimeMillis()
        val updated = user.copy(isOnline = isOnline, lastActive = now)
        _currentUser.value = updated

        if (isOnline) {
            startHeartbeat()
        } else {
            stopHeartbeat()
        }

        firestore?.collection("users")?.document(user.id)?.update(
            mapOf(
                "isOnline" to isOnline,
                "lastActive" to now
            )
        )
    }

    fun signOut() {
        val uid = _currentUser.value?.id
        stopHeartbeat()
        currentUserListener?.remove()
        currentUserListener = null
        if (!uid.isNullOrBlank()) {
            firestore?.collection("users")?.document(uid)?.update(
                mapOf(
                    "isOnline" to false,
                    "lastActive" to System.currentTimeMillis()
                )
            )
        }
        try { auth?.signOut() } catch (_: Throwable) {}
        _currentUser.value = null
    }

    fun updateProfile(
        name: String,
        profilePic: String,
        onSuccess: () -> Unit = {}
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updated = user.copy(
                    name = name.trim(),
                    profilePic = profilePic
                )
                _currentUser.value = updated

                firestore?.collection("users")?.document(updated.id)?.update(
                    mapOf(
                        "name" to updated.name,
                        "profilePic" to updated.profilePic
                    )
                )?.await()

                onSuccess()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to update profile: ${e.message}")
                // Still update locally for smooth UX
                onSuccess()
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentUserListener?.remove()
    }
}

