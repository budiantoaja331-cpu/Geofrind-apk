package com.example

import com.example.util.HaversineCalculator
import com.example.util.JitsiHelper
import com.example.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun haversineCalculator_calculatesAndFormatsDistanceCorrectly() {
        // Jakarta Monas (-6.1754, 106.8272) to Bundaran HI (-6.1950, 106.8230) ~ 2.2 km
        val distance = HaversineCalculator.calculateDistanceMeters(-6.1754, 106.8272, -6.1950, 106.8230)
        assertTrue("Distance should be around 2.2 km", distance in 2000.0..2500.0)

        val formattedMeters = HaversineCalculator.formatDistance(450.0)
        assertEquals("450 m jauhnya", formattedMeters)

        val formattedKm = HaversineCalculator.formatDistance(2300.0)
        assertEquals("2.3 km jauhnya", formattedKm)
    }

    @Test
    fun jitsiHelper_generatesDeterministicRoomId() {
        val room1 = JitsiHelper.generateSecureRoomId("userA", "userB")
        val room2 = JitsiHelper.generateSecureRoomId("userB", "userA")
        assertEquals(room1, room2)
        assertTrue(room1.startsWith("SecureCall_"))
    }

    @Test
    fun timeUtils_formatsPresenceCorrectly() {
        assertEquals("Online", TimeUtils.formatPresenceStatus(true, System.currentTimeMillis()))
        assertEquals("Offline", TimeUtils.formatPresenceStatus(false, 0L))
        
        val justNow = System.currentTimeMillis() - 30_000 // 30s ago
        assertEquals("Aktif baru saja", TimeUtils.formatPresenceStatus(false, justNow))
    }

    @Test
    fun distanceRadius_filtersWithinRadiusCorrectly() {
        // Monas coordinates
        val myLat = -6.1754
        val myLng = 106.8272

        // User 1: Bundaran HI ~2.2 km away
        val dist1 = HaversineCalculator.calculateDistanceMeters(myLat, myLng, -6.1950, 106.8230)
        // User 2: Bandung ~120 km away
        val dist2 = HaversineCalculator.calculateDistanceMeters(myLat, myLng, -6.9175, 107.6191)

        val radiusKm = 10f
        val maxMeters = radiusKm * 1000.0

        assertTrue("Bundaran HI should be within 10 km", dist1 <= maxMeters)
        assertTrue("Bandung should be outside 10 km", dist2 > maxMeters)
    }

    @Test
    fun notificationHelper_channelConstantsValid() {
        assertEquals("geofriends_chat_channel", com.example.util.NotificationHelper.CHANNEL_CHAT_ID)
        assertEquals("geofriends_call_channel", com.example.util.NotificationHelper.CHANNEL_CALL_ID)
    }

    @Test
    fun chatMessage_readStatusDefaultsAndUpdates() {
        val unreadMsg = com.example.model.ChatMessage(id = "msg1", text = "Hello", read = false)
        assertEquals(false, unreadMsg.read)

        val readMsg = unreadMsg.copy(read = true)
        assertEquals(true, readMsg.read)
    }

    @Test
    fun userPresence_onlineAndOfflineState() {
        val onlineUser = com.example.model.User(
            id = "user1",
            name = "Sarah",
            isOnline = true,
            lastActive = System.currentTimeMillis()
        )
        assertTrue(onlineUser.isOnline)
        assertEquals("Online", TimeUtils.formatPresenceStatus(onlineUser.isOnline, onlineUser.lastActive))

        val offlineUser = onlineUser.copy(isOnline = false, lastActive = System.currentTimeMillis() - 600_000)
        assertEquals(false, offlineUser.isOnline)
        assertEquals("Aktif 10 mnt lalu", TimeUtils.formatPresenceStatus(offlineUser.isOnline, offlineUser.lastActive))
    }
}
