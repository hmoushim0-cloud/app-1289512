package sa.factory.multaqahubcoworkin.space

import java.util.UUID
import kotlin.math.round

enum class CoworkingSpaceType(val baseRateHourlySar: Double, val capacityPersons: Int, val titleArabic: String) {
    HOT_DESK(35.0, 1, "مساحة عمل مرنة"),
    DEDICATED_DESK(55.0, 1, "مكتب مخصص دائم"),
    PODCAST_ACOUSTIC_STUDIO(120.0, 3, "استوديو صوتي وبودكاست"),
    PRIVATE_TEAM_SUITE(180.0, 6, "مكتب تنفيذي خاص للفرق"),
    BOARDROOM_EXECUTIVE(260.0, 16, "قاعة اجتماعات كبرى ذكية")
}

enum class CoworkingMemberTier(val discountPercent: Double, val tierNameArabic: String) {
    GUEST(0.0, "زائر يومي"),
    BRONZE_STARTUP(5.0, "عضوية برونزية - رواد الأعمال"),
    SILVER_SCALEUP(12.0, "عضوية فضية - الشركات النامية"),
    GOLD_ENTERPRISE(20.0, "عضوية ذهبية - المنشآت الكبرى")
}

data class TimeSlot(
    val startEpochSeconds: Long,
    val endEpochSeconds: Long
) {
    init {
        require(endEpochSeconds > startEpochSeconds) { "End time must be after start time" }
    }

    fun overlaps(other: TimeSlot): Boolean {
        return this.startEpochSeconds < other.endEpochSeconds && this.endEpochSeconds > other.startEpochSeconds
    }

    val durationHours: Double
        get() = (endEpochSeconds - startEpochSeconds).toDouble() / 3600.0
}

data class CoworkingBookingRequest(
    val bookingId: String = UUID.randomUUID().toString().take(8),
    val memberId: String,
    val spaceType: CoworkingSpaceType,
    val memberTier: CoworkingMemberTier,
    val slot: TimeSlot,
    val isPeakTime: Boolean = false,
    val includeHighSpeedFibreSla: Boolean = true,
    val additionalCoffeeCredits: Int = 0
)

data class ReservationQuote(
    val bookingId: String,
    val spaceType: CoworkingSpaceType,
    val durationHours: Double,
    val baseHourlyRateSar: Double,
    val grossSubtotalSar: Double,
    val peakSurplusSar: Double,
    val membershipDiscountSar: Double,
    val netSubtotalSar: Double,
    val vatAmountSar: Double,
    val grandTotalSar: Double
)

data class ZoneOccupancyTelemetry(
    val zoneId: String,
    val zoneNameArabic: String,
    val totalWorkstations: Int,
    val occupiedWorkstations: Int,
    val utilizationPercentage: Double,
    val isNearCapacityLimit: Boolean
)

class CoworkingSpaceAllocationEngine {

    companion object {
        const val VAT_RATE = 0.15
        const val PEAK_SURCHARGE_MULTIPLIER = 0.15 // +15% peak rate surcharge
        const val FIBRE_SLA_SURCHARGE_PER_HOUR = 10.0 // SAR per hour for guaranteed SLA
        const val COFFEE_CREDIT_RATE_SAR = 12.0 // SAR per barista credit
        const val CAPACITY_WARNING_THRESHOLD_PCT = 85.0
    }

    fun calculateReservationCost(request: CoworkingBookingRequest): ReservationQuote {
        val duration = request.slot.durationHours
        require(duration > 0.0) { "Duration must be greater than zero hours" }

        val rawHourlyRate = request.spaceType.baseRateHourlySar
        var gross = duration * rawHourlyRate

        if (request.includeHighSpeedFibreSla) {
            gross += duration * FIBRE_SLA_SURCHARGE_PER_HOUR
        }
        gross += request.additionalCoffeeCredits * COFFEE_CREDIT_RATE_SAR

        val peakSurplus = if (request.isPeakTime) {
            roundToCents(gross * PEAK_SURCHARGE_MULTIPLIER)
        } else {
            0.0
        }

        val intermediateSubtotal = gross + peakSurplus
        val discountRate = request.memberTier.discountPercent / 100.0
        val discountAmount = roundToCents(intermediateSubtotal * discountRate)
        val netSubtotal = roundToCents(intermediateSubtotal - discountAmount)
        val vat = roundToCents(netSubtotal * VAT_RATE)
        val grandTotal = roundToCents(netSubtotal + vat)

        return ReservationQuote(
            bookingId = request.bookingId,
            spaceType = request.spaceType,
            durationHours = round(duration * 100.0) / 100.0,
            baseHourlyRateSar = rawHourlyRate,
            grossSubtotalSar = roundToCents(gross),
            peakSurplusSar = peakSurplus,
            membershipDiscountSar = discountAmount,
            netSubtotalSar = netSubtotal,
            vatAmountSar = vat,
            grandTotalSar = grandTotal
        )
    }

    fun checkSlotAvailability(existingBookings: List<TimeSlot>, candidateSlot: TimeSlot): Boolean {
        for (booked in existingBookings) {
            if (booked.overlaps(candidateSlot)) {
                return false
            }
        }
        return true
    }

    fun computeZoneOccupancy(
        zoneId: String,
        zoneNameArabic: String,
        totalSeats: Int,
        activeAttendees: Int
    ): ZoneOccupancyTelemetry {
        require(totalSeats > 0) { "Total workstations must be greater than 0" }
        require(activeAttendees >= 0) { "Active attendees cannot be negative" }

        val clampedOccupancy = if (activeAttendees > totalSeats) totalSeats else activeAttendees
        val percentage = round((clampedOccupancy.toDouble() / totalSeats.toDouble()) * 10000.0) / 100.0
        val isWarning = percentage >= CAPACITY_WARNING_THRESHOLD_PCT

        return ZoneOccupancyTelemetry(
            zoneId = zoneId,
            zoneNameArabic = zoneNameArabic,
            totalWorkstations = totalSeats,
            occupiedWorkstations = clampedOccupancy,
            utilizationPercentage = percentage,
            isNearCapacityLimit = isWarning
        )
    }

    private fun roundToCents(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}
