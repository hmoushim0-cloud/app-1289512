package sa.factory.multaqahubcoworkin

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.multaqahubcoworkin.hardware.*
import sa.factory.multaqahubcoworkin.space.*
import java.util.Arrays

class MultaqaHubFeatureTest {

    private lateinit var spaceEngine: CoworkingSpaceAllocationEngine
    private lateinit var turnstileDriver: RfidTurnstileTotpPassDriver
    private val testSecret = "MultaqaSharedKey2026".toByteArray(Charsets.US_ASCII)

    @Before
    fun setUp() {
        spaceEngine = CoworkingSpaceAllocationEngine()
        turnstileDriver = RfidTurnstileTotpPassDriver()
    }

    @Test
    fun testHotDeskAndMeetingRoomCostCalculation() {
        val now = 1730000000L
        val slot = TimeSlot(startEpochSeconds = now, endEpochSeconds = now + 7200L) // 2.0 hours
        val request = CoworkingBookingRequest(
            memberId = "MEM-001",
            spaceType = CoworkingSpaceType.HOT_DESK, // 35 SAR/hr
            memberTier = CoworkingMemberTier.GUEST,
            slot = slot,
            isPeakTime = false,
            includeHighSpeedFibreSla = true, // 10 SAR/hr
            additionalCoffeeCredits = 1 // 12 SAR
        )

        val quote = spaceEngine.calculateReservationCost(request)

        // 2 hrs * (35 + 10) = 90 + 12 = 102 SAR gross
        assertEquals(102.0, quote.grossSubtotalSar, 0.01)
        assertEquals(0.0, quote.membershipDiscountSar, 0.01)
        assertEquals(102.0, quote.netSubtotalSar, 0.01)
        // VAT 15% of 102 = 15.30 SAR
        assertEquals(15.30, quote.vatAmountSar, 0.01)
        // Grand total: 117.30 SAR
        assertEquals(117.30, quote.grandTotalSar, 0.01)
    }

    @Test
    fun testMembershipTierDiscount() {
        val now = 1730000000L
        val slot = TimeSlot(startEpochSeconds = now, endEpochSeconds = now + 14400L) // 4.0 hours
        val request = CoworkingBookingRequest(
            memberId = "MEM-002",
            spaceType = CoworkingSpaceType.BOARDROOM_EXECUTIVE, // 260 SAR/hr
            memberTier = CoworkingMemberTier.GOLD_ENTERPRISE, // 20% discount
            slot = slot,
            isPeakTime = false,
            includeHighSpeedFibreSla = false,
            additionalCoffeeCredits = 0
        )

        val quote = spaceEngine.calculateReservationCost(request)
        // 4 hrs * 260 = 1040 SAR gross
        assertEquals(1040.0, quote.grossSubtotalSar, 0.01)
        // 20% discount = 208.0 SAR
        assertEquals(208.0, quote.membershipDiscountSar, 0.01)
        // Net subtotal: 832.0 SAR
        assertEquals(832.0, quote.netSubtotalSar, 0.01)
        // VAT 15% of 832 = 124.80 SAR
        assertEquals(124.80, quote.vatAmountSar, 0.01)
        // Grand total: 956.80 SAR
        assertEquals(956.80, quote.grandTotalSar, 0.01)
    }

    @Test
    fun testTimeSlotOverlapAndCollisionAvoidance() {
        val baseStart = 1730000000L
        val bookedSlot = TimeSlot(baseStart, baseStart + 3600L) // 10:00 - 11:00

        // Exact match overlap
        val exactOverlap = TimeSlot(baseStart, baseStart + 3600L)
        assertFalse(spaceEngine.checkSlotAvailability(listOf(bookedSlot), exactOverlap))

        // Partial internal overlap (10:15 - 10:45)
        val insideOverlap = TimeSlot(baseStart + 900L, baseStart + 2700L)
        assertFalse(spaceEngine.checkSlotAvailability(listOf(bookedSlot), insideOverlap))

        // Non-overlapping subsequent slot (11:00 - 12:00)
        val validNextSlot = TimeSlot(baseStart + 3600L, baseStart + 7200L)
        assertTrue(spaceEngine.checkSlotAvailability(listOf(bookedSlot), validNextSlot))

        // Non-overlapping prior slot (09:00 - 10:00)
        val validPriorSlot = TimeSlot(baseStart - 3600L, baseStart)
        assertTrue(spaceEngine.checkSlotAvailability(listOf(bookedSlot), validPriorSlot))
    }

    @Test
    fun testZoneOccupancyTelemetryAndWarningLimit() {
        // Under threshold: 30 / 50 seats = 60.0%
        val normalOccupancy = spaceEngine.computeZoneOccupancy(
            zoneId = "Z-OPEN-01",
            zoneNameArabic = "منطقة العمل المشترك - الطابق الأول",
            totalSeats = 50,
            activeAttendees = 30
        )
        assertEquals(60.0, normalOccupancy.utilizationPercentage, 0.01)
        assertFalse(normalOccupancy.isNearCapacityLimit)

        // Over 85% threshold: 45 / 50 seats = 90.0%
        val warningOccupancy = spaceEngine.computeZoneOccupancy(
            zoneId = "Z-OPEN-01",
            zoneNameArabic = "منطقة العمل المشترك - الطابق الأول",
            totalSeats = 50,
            activeAttendees = 45
        )
        assertEquals(90.0, warningOccupancy.utilizationPercentage, 0.01)
        assertTrue(warningOccupancy.isNearCapacityLimit)
    }

    @Test
    fun testRfidAccessGrantedAndAntiPassback() {
        val cardUid = "04A1B2C3"
        val memberId = "MEM-SAUDI-10"

        // 1. Initial entry granted
        val entry1 = turnstileDriver.evaluateRfidAccess(
            cardUidHex = cardUid,
            memberId = memberId,
            isMembershipActive = true,
            direction = TurnstileDirection.ENTRY
        )
        assertEquals(AccessDecision.ACCESS_GRANTED, entry1.decision)
        assertTrue(entry1.relayUnlockCommandEmitted)

        // 2. Immediate secondary entry without exit -> DENIED_ANTI_PASSBACK
        val entry2 = turnstileDriver.evaluateRfidAccess(
            cardUidHex = cardUid,
            memberId = memberId,
            isMembershipActive = true,
            direction = TurnstileDirection.ENTRY
        )
        assertEquals(AccessDecision.DENIED_ANTI_PASSBACK, entry2.decision)
        assertFalse(entry2.relayUnlockCommandEmitted)

        // 3. Exit -> ACCESS_GRANTED
        val exit1 = turnstileDriver.evaluateRfidAccess(
            cardUidHex = cardUid,
            memberId = memberId,
            isMembershipActive = true,
            direction = TurnstileDirection.EXIT
        )
        assertEquals(AccessDecision.ACCESS_GRANTED, exit1.decision)
        assertTrue(exit1.relayUnlockCommandEmitted)

        // 4. Inactive membership -> DENIED_EXPIRED
        val expiredEntry = turnstileDriver.evaluateRfidAccess(
            cardUidHex = cardUid,
            memberId = memberId,
            isMembershipActive = false,
            direction = TurnstileDirection.ENTRY
        )
        assertEquals(AccessDecision.DENIED_EXPIRED, expiredEntry.decision)
        assertFalse(expiredEntry.relayUnlockCommandEmitted)
    }

    @Test
    fun testTotpPassGenerationAndDriftValidation() {
        val memberId = "MEM-TOTP-77"
        val epochSeconds = 1730000000L
        val step = epochSeconds / 30L

        // Generate valid 6-digit TOTP
        val validTotp = turnstileDriver.generateTotpCode(testSecret, step)
        assertEquals(6, validTotp.length)
        assertTrue(validTotp.all { it.isDigit() })

        // Evaluate entry with valid TOTP -> Granted
        val event = turnstileDriver.evaluateTotpAccess(
            memberId = memberId,
            sharedSecret = testSecret,
            submittedToken = validTotp,
            direction = TurnstileDirection.ENTRY,
            evaluationEpochSeconds = epochSeconds
        )
        assertEquals(AccessDecision.ACCESS_GRANTED, event.decision)
        assertTrue(event.relayUnlockCommandEmitted)

        // Replay attack with same consumed token -> DENIED_INVALID_TOKEN
        val replayEvent = turnstileDriver.evaluateTotpAccess(
            memberId = memberId,
            sharedSecret = testSecret,
            submittedToken = validTotp,
            direction = TurnstileDirection.EXIT,
            evaluationEpochSeconds = epochSeconds
        )
        assertEquals(AccessDecision.DENIED_INVALID_TOKEN, replayEvent.decision)
        assertFalse(replayEvent.relayUnlockCommandEmitted)
    }

    @Test
    fun testTelemetryPacketEncodingAndDecodingCrcValidation() {
        val originalEvent = TurnstileAccessEvent(
            gateId = 0x01,
            credentialType = TurnstileCredentialType.MIFARE_RFID_1356MHZ,
            direction = TurnstileDirection.ENTRY,
            credentialIdHex = "04A1B2C3",
            timestampSeconds = 1730000000L,
            decision = AccessDecision.ACCESS_GRANTED,
            relayUnlockCommandEmitted = true
        )

        // Encode to 24-byte packet
        val packet = turnstileDriver.encodeTelemetryFrame(originalEvent)
        assertEquals(24, packet.size)

        // Check magic preamble 0x544E ('TN')
        assertEquals(0x54.toByte(), packet[0])
        assertEquals(0x4E.toByte(), packet[1])

        // Verify relay unlock pulse template
        assertTrue(Arrays.equals(RfidTurnstileTotpPassDriver.RELAY_UNLOCK_PULSE, byteArrayOf(
            0x02.toByte(), 0x55.toByte(), 0x4E.toByte(), 0x4C.toByte(),
            0x4F.toByte(), 0x43.toByte(), 0x4B.toByte(), 0x03.toByte()
        )))

        // Decode packet and verify round-trip
        val decoded = turnstileDriver.decodeTelemetryFrame(packet)
        assertEquals(originalEvent.gateId, decoded.gateId)
        assertEquals(originalEvent.credentialType, decoded.credentialType)
        assertEquals(originalEvent.direction, decoded.direction)
        assertEquals(originalEvent.credentialIdHex, decoded.credentialIdHex)
        assertEquals(originalEvent.decision, decoded.decision)
        assertTrue(decoded.relayUnlockCommandEmitted)
    }
}
