package sa.factory.multaqahubcoworkin.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    private val peripheral = "13.56 MHz RFID Turnstile Access + TOTP Pass"
    private val rfidTotpDriver = RfidTurnstileTotpPassDriver()

    fun getDriverInstance(): RfidTurnstileTotpPassDriver = rfidTotpDriver

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "13.56 MHz RFID Turnstile Access + TOTP Pass: ONLINE | READY"))

        val mockSecret = "MultaqaSecret2026".toByteArray(Charsets.US_ASCII)
        var sequence = 1000

        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 10)
            sequence++

            // Produce real TOTP access evaluation telemetry
            val epochSeconds = System.currentTimeMillis() / 1000L
            val currentTotp = rfidTotpDriver.generateTotpCode(mockSecret, epochSeconds / 30L)
            val event = rfidTotpDriver.evaluateTotpAccess(
                memberId = "MEM-${sequence % 50}",
                sharedSecret = mockSecret,
                submittedToken = currentTotp,
                direction = if (sequence % 2 == 0) TurnstileDirection.ENTRY else TurnstileDirection.EXIT,
                gateId = 0x01,
                evaluationEpochSeconds = epochSeconds
            )
            val encodedFrame = rfidTotpDriver.encodeTelemetryFrame(event)
            val crcHex = Integer.toHexString(rfidTotpDriver.calculateCrc16Ccitt(encodedFrame).toInt() and 0xFFFF).toUpperCase()

            emit(HardwareTelemetryFrame(
                peripheral,
                "ACTIVE_STREAMING",
                "Gate 0x${event.gateId} | ${event.direction.name} | TOTP: $currentTotp | Decision: ${event.decision.name} | CRC16: 0x$crcHex (Jitter: +${jitter}ms)"
            ))
        }
    }
}
