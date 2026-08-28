package com.example.ui.screens

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.User
import com.example.util.HaversineCalculator
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.UserListViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun UserListScreen(
    authViewModel: AuthViewModel,
    userListViewModel: UserListViewModel,
    onUserClick: (User) -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val users by userListViewModel.filteredUsers.collectAsState()
    val countryList by userListViewModel.countryList.collectAsState()
    val selectedFilter by userListViewModel.selectedFilter.collectAsState()
    val searchQuery by userListViewModel.searchQuery.collectAsState()
    val isLoading by userListViewModel.isLoading.collectAsState()
    val isRealtimeConnected by userListViewModel.isRealtimeConnected.collectAsState()
    val onlineCount by userListViewModel.onlineCount.collectAsState()
    val isDistanceFilterEnabled by userListViewModel.isDistanceFilterEnabled.collectAsState()
    val sliderDistanceKm by userListViewModel.sliderDistanceKm.collectAsState()

    var isDistancePanelExpanded by remember { mutableStateOf(false) }
    var userToBlock by remember { mutableStateOf<User?>(null) }
    var showBlockedUsersSheet by remember { mutableStateOf(false) }
    var isMapView by remember { mutableStateOf(false) }

    // Accompanist Location Permissions Flow
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var showRationaleDialog by remember { mutableStateOf(false) }

    // Automatic location refresh when permission is granted
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            authViewModel.refreshUserLocation(context)
        }
    }

    LaunchedEffect(currentUser) {
        userListViewModel.listenToUsers(currentUser)
        userListViewModel.updateCurrentUser(currentUser)
    }

    // Block User Confirmation Dialog
    if (userToBlock != null) {
        val target = userToBlock
        AlertDialog(
            onDismissRequest = { userToBlock = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.PersonOff,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Blokir ${target?.name}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    text = "Pengguna ini tidak akan muncul lagi di daftar teman Anda. Anda dapat membuka blokir kapan saja melalui menu Keamanan.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = target?.id
                        if (!uid.isNullOrBlank()) {
                            authViewModel.blockUser(uid)
                        }
                        userToBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Blokir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToBlock = null }) {
                    Text("Batal", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Blocked Users Management Sheet
    if (showBlockedUsersSheet) {
        val blockedIds = currentUser?.blockedUsers ?: emptyList()
        BlockedUsersBottomSheet(
            blockedUserIds = blockedIds,
            onUnblock = { unblockId ->
                authViewModel.unblockUser(unblockId)
            },
            onDismiss = { showBlockedUsersSheet = false }
        )
    }

    // Permission Rationale Explanation Dialog
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Akses Lokasi Presisi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    text = "Aplikasi membutuhkan izin lokasi presisi (GPS) agar dapat menghitung jarak antara Anda dan pengguna lain secara akurat dengan rumus Haversine.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationaleDialog = false
                        locationPermissionsState.launchMultiplePermissionRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Izinkan Sekarang", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Nanti Saja", color = Color(0xFF64748B))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onProfileClick() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUser?.profilePic?.isNotBlank() == true) {
                                AsyncImage(
                                    model = currentUser?.profilePic,
                                    contentDescription = "Profil",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            currentUser?.let { user ->
                                Text(
                                    text = user.name,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isMapView = !isMapView },
                        modifier = Modifier.testTag("toggle_map_view_button")
                    ) {
                        Icon(
                            imageVector = if (isMapView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Map,
                            contentDescription = if (isMapView) "Tampilkan Tampilan Daftar" else "Tampilkan Peta Pengguna",
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    IconButton(
                        onClick = { showBlockedUsersSheet = true },
                        modifier = Modifier.testTag("manage_blocked_users_button")
                    ) {
                        val blockedCount = currentUser?.blockedUsers?.size ?: 0
                        if (blockedCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White
                                    ) {
                                        Text("$blockedCount")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Kelola Pengguna Diblokir",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Kelola Pengguna Diblokir",
                                tint = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profil",
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!locationPermissionsState.allPermissionsGranted) {
                                locationPermissionsState.launchMultiplePermissionRequest()
                            } else {
                                authViewModel.refreshUserLocation(context)
                            }
                            userListViewModel.listenToUsers(currentUser)
                        },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang Realtime",
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    IconButton(
                        onClick = { authViewModel.signOut() },
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Keluar",
                            tint = Color(0xFFEF4444)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E293B)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(paddingValues)
        ) {
            // Location Permission Banner (Accompanist Permission Flow)
            AnimatedVisibility(
                visible = !locationPermissionsState.allPermissionsGranted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izin Lokasi Presisi Dibutuhkan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Aktifkan GPS agar jarak dengan pengguna lain dapat dihitung akurat.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                lineHeight = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (locationPermissionsState.shouldShowRationale) {
                                    showRationaleDialog = true
                                } else {
                                    locationPermissionsState.launchMultiplePermissionRequest()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("request_location_permission_button")
                        ) {
                            Text(
                                text = "Izinkan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Real-time Firestore Status Banner
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isRealtimeConnected) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRealtimeConnected) "Jarak Anda dengan Pengguna Lain" else "Menghitung Jarak Pengguna...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRealtimeConnected) Color(0xFF047857) else Color(0xFFB45309)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEEF2FF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF4F46E5)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.online_count_format, onlineCount),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real-time Search Text Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { userListViewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "Cari pengguna secara realtime...",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari Pengguna",
                                tint = Color(0xFF4F46E5)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { userListViewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Hapus Pencarian",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_field"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filter Chips (Semua, Online, Negara)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(countryList) { filterItem ->
                            val isSelected = selectedFilter == filterItem
                            FilterChip(
                                selected = isSelected,
                                onClick = { userListViewModel.setSelectedFilter(filterItem) },
                                label = {
                                    val displayName = when (filterItem) {
                                        "Semua Pengguna" -> stringResource(com.example.R.string.all_users)
                                        "Online Sekarang" -> stringResource(com.example.R.string.online_now)
                                        else -> filterItem
                                    }
                                    Text(
                                        text = displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4F46E5),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = Color(0xFFF8FAFC),
                                    labelColor = Color(0xFF475569)
                                ),
                                border = null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Distance Radius Filter Slider Card
                    DistanceRadiusFilterCard(
                        isDistanceFilterEnabled = isDistanceFilterEnabled,
                        sliderDistanceKm = sliderDistanceKm,
                        isLocationAvailable = currentUser != null && currentUser?.latitude != 0.0,
                        isExpanded = isDistancePanelExpanded,
                        onToggleExpanded = { isDistancePanelExpanded = !isDistancePanelExpanded },
                        onFilterToggle = { enabled ->
                            userListViewModel.setDistanceFilterEnabled(enabled)
                            if (enabled) isDistancePanelExpanded = true
                        },
                        onRadiusChange = { radius ->
                            userListViewModel.setSliderDistanceKm(radius)
                        },
                        onPresetClick = { preset ->
                            userListViewModel.applyDistancePreset(preset)
                            isDistancePanelExpanded = true
                        },
                        onClearFilter = {
                            userListViewModel.clearDistanceFilter()
                        }
                    )
                }
            }

            // Real-time Users Area (Map or List View)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isMapView) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp, vertical = 8.dp))
            ) {
                if (isMapView) {
                    NearbyUsersMapView(
                        currentUser = currentUser,
                        users = users,
                        onUserClick = onUserClick,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isLoading && users.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4F46E5),
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Menyinkronkan data pengguna Firestore...",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else if (users.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            color = if (isDistanceFilterEnabled) Color(0xFFEFF6FF) else Color(0xFFEEF2FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDistanceFilterEnabled) Icons.Default.NearMe else Icons.Default.People,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                isDistanceFilterEnabled -> "Tidak ada teman dalam radius ${sliderDistanceKm.toInt()} km"
                                searchQuery.isNotEmpty() -> "Pengguna tidak ditemukan"
                                else -> "Belum ada pengguna lain"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                isDistanceFilterEnabled -> "Tidak ditemukan pengguna yang berada dalam jarak ${sliderDistanceKm.toInt()} km dari lokasi Anda saat ini. Coba geser slider radius ke jarak yang lebih besar."
                                searchQuery.isNotEmpty() -> "Tidak ada hasil untuk \"$searchQuery\". Coba cari nama atau negara lain."
                                else -> "Pengguna yang mendaftar di Firestore akan otomatis muncul di sini secara real-time."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        if (isDistanceFilterEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { userListViewModel.applyDistancePreset(100f) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Perbesar ke 100 km", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { userListViewModel.clearDistanceFilter() },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Text("Lihat Semua Jarak", fontSize = 12.sp, color = Color(0xFF475569))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = users,
                            key = { it.id }
                        ) { user ->
                            RealtimeUserCard(
                                user = user,
                                onClick = { onUserClick(user) },
                                onBlockClick = { userToBlock = user }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingOnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    if (isOnline) {
        val infiniteTransition = rememberInfiniteTransition(label = "presencePulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha"
        )
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 2.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale"
        )

        Box(
            modifier = modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated breathing radar ripple
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale,
                        alpha = pulseAlpha
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            // Solid green dot with crisp white border
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
        }
    } else {
        // Offline subtle slate dot
        Box(
            modifier = modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF94A3B8))
            )
        }
    }
}

@Composable
fun RealtimeUserCard(
    user: User,
    onClick: () -> Unit,
    onBlockClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(
                elevation = if (user.isOnline) 3.dp else 1.5.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (user.isOnline) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF0F172A).copy(alpha = 0.06f)
            )
            .testTag("user_card_${user.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = if (user.isOnline) BorderStroke(1.dp, Color(0xFFD1FAE5)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture with Real-Time Pulsing Status Indicator
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFEEF2F6)),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.profilePic.isNotEmpty()) {
                        AsyncImage(
                            model = user.profilePic,
                            contentDescription = "Foto profil ${user.name}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // Real-time Pulsing Online/Offline status dot
                PulsingOnlineIndicator(isOnline = user.isOnline)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.isOnline) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFD1FAE5)
                        ) {
                            Text(
                                text = "LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (user.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = com.example.util.TimeUtils.formatPresenceStatus(context, user.isOnline, user.lastActive),
                        fontSize = 11.sp,
                        fontWeight = if (user.isOnline) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (user.isOnline) Color(0xFF059669) else Color(0xFF64748B)
                    )

                    Text(
                        text = " • ",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (user.country.isNotEmpty()) user.country else "Indonesia",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (user.distanceMeters > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Jarak: ${HaversineCalculator.formatDistance(user.distanceMeters)}",
                            fontSize = 11.sp,
                            color = Color(0xFF4F46E5),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Block Option Icon Button
            IconButton(
                onClick = onBlockClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("block_button_${user.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Blokir ${user.name}",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Action Button (Kirim Pesan)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (user.isOnline) Color(0xFF4F46E5) else Color(0xFFEEF2FF),
                    contentColor = if (user.isOnline) Color.White else Color(0xFF4F46E5)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Chat",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DistanceRadiusFilterCard(
    isDistanceFilterEnabled: Boolean,
    sliderDistanceKm: Float,
    isLocationAvailable: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFilterToggle: (Boolean) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onPresetClick: (Float) -> Unit,
    onClearFilter: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("distance_radius_filter_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDistanceFilterEnabled) Color(0xFFF5F3FF) else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDistanceFilterEnabled) Color(0xFFC7D2FE) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Icon, Title, Radius status pill, Switch, Expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDistanceFilterEnabled) Color(0xFF4F46E5) else Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = if (isDistanceFilterEnabled) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Filter Radius Jarak",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDistanceFilterEnabled) Color(0xFF4F46E5) else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = if (isDistanceFilterEnabled) "≤ ${sliderDistanceKm.toInt()} km" else "Semua",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDistanceFilterEnabled) Color.White else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isDistanceFilterEnabled)
                            "Hanya menampilkan teman dalam ${sliderDistanceKm.toInt()} km"
                        else
                            "Filter teman berdasarkan jarak maksimum",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Switch(
                    checked = isDistanceFilterEnabled,
                    onCheckedChange = onFilterToggle,
                    modifier = Modifier.testTag("distance_filter_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4F46E5),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD5E1)
                    )
                )

                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Tutup Panel Radius" else "Buka Panel Radius",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expandable Slider & Preset Area
            AnimatedVisibility(
                visible = isExpanded || isDistanceFilterEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    if (!isLocationAvailable) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lokasi Anda belum aktif. Izinkan GPS agar perhitungan jarak akurat.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    // Value Readout Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jarak Radius Maksimum:",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "${sliderDistanceKm.toInt()} km",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4F46E5)
                        )
                    }

                    // The Slider UI Component
                    Slider(
                        value = sliderDistanceKm,
                        onValueChange = onRadiusChange,
                        valueRange = 1f..100f,
                        steps = 98,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("distance_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4F46E5),
                            activeTrackColor = Color(0xFF4F46E5),
                            inactiveTrackColor = Color(0xFFE2E8F0),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )

                    // Min and Max Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "1 km", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(text = "25 km", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(text = "50 km", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        Text(text = "100 km", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Buttons (5km, 10km, 25km, 50km, 100km)
                    Text(
                        text = "Pilih Cepat:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val presets = listOf(5f, 10f, 25f, 50f, 100f)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presets) { preset ->
                            val isPresetActive = isDistanceFilterEnabled && sliderDistanceKm.toInt() == preset.toInt()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPresetActive) Color(0xFF4F46E5) else Color(0xFFF1F5F9),
                                border = if (isPresetActive) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .clickable { onPresetClick(preset) }
                                    .testTag("preset_${preset.toInt()}km")
                            ) {
                                Text(
                                    text = "${preset.toInt()} km",
                                    fontSize = 11.sp,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPresetActive) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (isDistanceFilterEnabled) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEE2E2),
                                    modifier = Modifier
                                        .clickable { onClearFilter() }
                                        .testTag("clear_distance_filter")
                                ) {
                                    Text(
                                        text = "Hapus Batasan",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFDC2626),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersBottomSheet(
    blockedUserIds: List<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("blocked_users_bottom_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pengguna Diblokir",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "${blockedUserIds.size} pengguna dalam daftar blokir",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Tutup",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (blockedUserIds.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tidak Ada Pengguna yang Diblokir",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Anda dapat memblokir pengguna dari tombol blokir di daftar teman.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedUserIds) { blockedId ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("blocked_user_row_$blockedId"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonOff,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ID: $blockedId",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Pengguna diblokir dari daftar teman",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onUnblock(blockedId) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF1F5F9),
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.testTag("unblock_button_$blockedId")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF059669)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Buka Blokir",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyUsersMapView(
    currentUser: User?,
    users: List<User>,
    onUserClick: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Initialize osmdroid configuration
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
    }
    
    val defaultLat = currentUser?.latitude?.takeIf { it != 0.0 } ?: -6.2088
    val defaultLng = currentUser?.longitude?.takeIf { it != 0.0 } ?: 106.8456

    var selectedUserOnMap by remember { mutableStateOf<User?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    controller.setZoom(11.0)
                    controller.setCenter(GeoPoint(defaultLat, defaultLng))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // Marker for current user
                if (currentUser != null && currentUser.latitude != 0.0) {
                    val myMarker = Marker(mapView).apply {
                        position = GeoPoint(currentUser.latitude, currentUser.longitude)
                        title = context.getString(com.example.R.string.my_location_title, currentUser.name)
                        snippet = context.getString(com.example.R.string.my_location_snippet)
                        setOnMarkerClickListener { _, _ ->
                            selectedUserOnMap = null
                            true
                        }
                    }
                    mapView.overlays.add(myMarker)
                    mapView.controller.animateTo(GeoPoint(currentUser.latitude, currentUser.longitude))
                }

                // Markers for nearby friends
                users.forEach { friend ->
                    if (friend.latitude != 0.0 || friend.longitude != 0.0) {
                        val friendMarker = Marker(mapView).apply {
                            position = GeoPoint(friend.latitude, friend.longitude)
                            title = friend.name
                            snippet = "${friend.country} • ${if (friend.isOnline) context.getString(com.example.R.string.online) else context.getString(com.example.R.string.offline)}"
                            setOnMarkerClickListener { _, _ ->
                                selectedUserOnMap = friend
                                true
                            }
                        }
                        mapView.overlays.add(friendMarker)
                    }
                }
                mapView.invalidate()
            }
        )

        // Overlay Card for selected user on map
        selectedUserOnMap?.let { selected ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            if (selected.profilePic.isNotEmpty()) {
                                AsyncImage(
                                    model = selected.profilePic,
                                    contentDescription = selected.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                            PulsingOnlineIndicator(isOnline = selected.isOnline)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = selected.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                            val distanceText = if (currentUser != null && currentUser.latitude != 0.0 && selected.latitude != 0.0) {
                                val distMeters = HaversineCalculator.calculateDistanceMeters(
                                    currentUser.latitude,
                                    currentUser.longitude,
                                    selected.latitude,
                                    selected.longitude
                                )
                                "${HaversineCalculator.formatDistance(distMeters)} • ${selected.country}"
                            } else {
                                selected.country
                            }
                            Text(
                                text = distanceText,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Button(
                        onClick = { onUserClick(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

