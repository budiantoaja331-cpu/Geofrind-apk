package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.User
import com.example.util.HaversineCalculator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class UserFilterType {
    ALL,
    ONLINE_ONLY,
    COUNTRY
}

class UserListViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private var firestoreListener: ListenerRegistration? = null

    private val _rawUsers = MutableStateFlow<List<User>>(emptyList())
    private val _filteredUsers = MutableStateFlow<List<User>>(emptyList())
    val filteredUsers: StateFlow<List<User>> = _filteredUsers.asStateFlow()

    private val _countryList = MutableStateFlow<List<String>>(listOf("Semua Pengguna", "Online Sekarang"))
    val countryList: StateFlow<List<String>> = _countryList.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Semua Pengguna")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRealtimeConnected = MutableStateFlow(false)
    val isRealtimeConnected: StateFlow<Boolean> = _isRealtimeConnected.asStateFlow()

    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    private val _isDistanceFilterEnabled = MutableStateFlow(false)
    val isDistanceFilterEnabled: StateFlow<Boolean> = _isDistanceFilterEnabled.asStateFlow()

    private val _sliderDistanceKm = MutableStateFlow(25f)
    val sliderDistanceKm: StateFlow<Float> = _sliderDistanceKm.asStateFlow()

    private var currentUser: User? = null

    init {
        seedInitialDemoUsersIfNeeded()
    }

    fun listenToUsers(currentUser: User?) {
        this.currentUser = currentUser
        _isLoading.value = true

        // Clean up previous listener if any
        firestoreListener?.remove()

        // Attach Realtime Firestore Snapshot Listener
        firestoreListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("UserListViewModel", "Firestore Realtime listener failed: ${error.message}")
                    _isRealtimeConnected.value = false
                    applyFiltering()
                    return@addSnapshotListener
                }

                _isRealtimeConnected.value = true

                if (snapshot != null) {
                    val usersFromDb = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val name = doc.getString("name") ?: ""
                        val profilePic = doc.getString("profilePic") ?: ""
                        val latitude = doc.getDouble("latitude") ?: 0.0
                        val longitude = doc.getDouble("longitude") ?: 0.0
                        val country = doc.getString("country") ?: "Indonesia"
                        val isOnlineRaw = doc.getBoolean("isOnline") ?: false
                        val lastActive = doc.getLong("lastActive") ?: 0L
                        val now = System.currentTimeMillis()
                        // User is online if marked online and lastActive within 3 mins (or if newly registered/demo)
                        val isOnline = isOnlineRaw && (lastActive == 0L || (now - lastActive) < 180_000)
                        val blocked = (doc.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                        User(
                            id = id,
                            name = name,
                            profilePic = profilePic,
                            latitude = latitude,
                            longitude = longitude,
                            country = country,
                            isOnline = isOnline,
                            lastActive = if (lastActive > 0) lastActive else now,
                            blockedUsers = blocked
                        )
                    }

                    // Combined with fallback demo records if Firestore is new
                    val combinedList = (usersFromDb + getSampleGlobalUsers()).distinctBy { it.id }
                    _rawUsers.value = combinedList
                    _onlineCount.value = combinedList.filter { currentUser == null || it.id != currentUser?.id }.count { it.isOnline }

                    // Aggregate unique countries for quick filter chips
                    val uniqueCountries = combinedList.map { it.country }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    _countryList.value = listOf("Semua Pengguna", "Online Sekarang") + uniqueCountries
                    applyFiltering()
                }
            }
    }

    fun setSelectedFilter(filter: String) {
        _selectedFilter.value = filter
        applyFiltering()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFiltering()
    }

    fun setDistanceFilterEnabled(enabled: Boolean) {
        _isDistanceFilterEnabled.value = enabled
        applyFiltering()
    }

    fun setSliderDistanceKm(distanceKm: Float) {
        _sliderDistanceKm.value = distanceKm.coerceIn(1f, 500f)
        if (_isDistanceFilterEnabled.value) {
            applyFiltering()
        }
    }

    fun applyDistancePreset(distanceKm: Float) {
        _sliderDistanceKm.value = distanceKm.coerceIn(1f, 500f)
        _isDistanceFilterEnabled.value = true
        applyFiltering()
    }

    fun clearDistanceFilter() {
        _isDistanceFilterEnabled.value = false
        applyFiltering()
    }

    fun updateCurrentUser(user: User?) {
        this.currentUser = user
        applyFiltering()
    }

    fun getBlockedUsers(): List<User> {
        val currUser = currentUser ?: return emptyList()
        val blockedIds = currUser.blockedUsers.toSet()
        if (blockedIds.isEmpty()) return emptyList()
        return _rawUsers.value.filter { it.id in blockedIds }
    }

    private fun applyFiltering() {
        val currUser = currentUser
        val query = _searchQuery.value.trim().lowercase()
        val currentFilter = _selectedFilter.value
        val myBlockedIds = (currUser?.blockedUsers ?: emptyList()).toSet()

        // Exclude self, users blocked by currentUser, and users who have blocked currentUser
        var list = _rawUsers.value.filter { otherUser ->
            val isNotSelf = currUser == null || otherUser.id != currUser.id
            val isNotBlockedByMe = otherUser.id !in myBlockedIds
            val hasNotBlockedMe = currUser == null || currUser.id !in otherUser.blockedUsers
            isNotSelf && isNotBlockedByMe && hasNotBlockedMe
        }

        // Filter by chip selection
        when (currentFilter) {
            "Semua Pengguna" -> {
                // No specific status or country filter
            }
            "Online Sekarang" -> {
                list = list.filter { it.isOnline }
            }
            else -> {
                // Filter by country name
                list = list.filter { it.country.equals(currentFilter, ignoreCase = true) }
            }
        }

        // Realtime search query matching (Name or Country or ID)
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase().contains(query) ||
                        it.country.lowercase().contains(query) ||
                        it.id.lowercase().contains(query)
            }
        }

        // Calculate distance if coordinates exist
        if (currUser != null && currUser.latitude != 0.0 && currUser.longitude != 0.0) {
            list = list.map { user ->
                val dist = if (user.latitude != 0.0 && user.longitude != 0.0) {
                    HaversineCalculator.calculateDistanceMeters(
                        currUser.latitude, currUser.longitude,
                        user.latitude, user.longitude
                    )
                } else {
                    0.0
                }
                user.copy(distanceMeters = dist)
            }
        }

        // Apply radius filter if enabled
        if (_isDistanceFilterEnabled.value) {
            val maxMeters = _sliderDistanceKm.value * 1000.0
            list = list.filter { user ->
                // Keep users who have a calculated distance within radius
                user.distanceMeters in 0.1..maxMeters
            }
        }

        // Sort: Online users first, then alphabetically by name
        _filteredUsers.value = list.sortedWith(
            compareByDescending<User> { it.isOnline }
                .thenBy { it.name.lowercase() }
        )

        _onlineCount.value = _rawUsers.value.filter { currUser == null || it.id != currUser.id }.count { it.isOnline }
    }

    private fun seedInitialDemoUsersIfNeeded() {
        val sampleUsers = getSampleGlobalUsers()
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users").get().await()
                if (snapshot.isEmpty) {
                    for (u in sampleUsers) {
                        val map = hashMapOf(
                            "id" to u.id,
                            "name" to u.name,
                            "profilePic" to u.profilePic,
                            "latitude" to u.latitude,
                            "longitude" to u.longitude,
                            "country" to u.country,
                            "isOnline" to u.isOnline,
                            "lastActive" to u.lastActive
                        )
                        firestore.collection("users").document(u.id).set(map)
                    }
                }
            } catch (e: Exception) {
                Log.w("UserListViewModel", "Firestore seed skipped: ${e.message}")
            }
        }
    }

    private fun getSampleGlobalUsers(): List<User> {
        return listOf(
            User(
                id = "sample_user_1",
                name = "Ayu Kartika",
                profilePic = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
                latitude = -6.1754,
                longitude = 106.8272,
                country = "Indonesia",
                isOnline = true
            ),
            User(
                id = "sample_user_2",
                name = "Kenji Sato",
                profilePic = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                latitude = 35.6762,
                longitude = 139.6503,
                country = "Jepang",
                isOnline = true
            ),
            User(
                id = "sample_user_3",
                name = "Emily Watson",
                profilePic = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
                latitude = 40.7128,
                longitude = -74.0060,
                country = "Amerika Serikat",
                isOnline = false
            ),
            User(
                id = "sample_user_4",
                name = "Lucas Müller",
                profilePic = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150",
                latitude = 52.5200,
                longitude = 13.4050,
                country = "Jerman",
                isOnline = true
            ),
            User(
                id = "sample_user_5",
                name = "Isabella Silva",
                profilePic = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150",
                latitude = -23.5505,
                longitude = -46.6333,
                country = "Brasil",
                isOnline = false
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        firestoreListener?.remove()
    }
}
