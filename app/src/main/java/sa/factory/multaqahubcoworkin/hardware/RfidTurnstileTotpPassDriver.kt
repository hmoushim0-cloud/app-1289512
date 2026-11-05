package sa.factory.multaqahubcoworkin.hardware

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class TurnstileDirection(val code: Byte) {
    ENTRY(0x01),
    EXIT(0x02);

    companion object {
        fun fromCode(code: Byte): TurnstileDirection {
            return if (code == 0x01.toByte()) ENTRY else EXIT
        }
    }
}

enum class TurnstileCredentialType(val code: Byte) {
    MIFARE_RFID_1356MHZ(0x01),
    DYNAMIC_TOTP_PASS(0x02);

    companion object {
        fun fromCode(code: Byte): TurnstileCredentialType {
            return if (code == 0x01.toByte()) MIFARE_RFID_1356MHZ else DYNAMIC_TOTP_PASS
        }
    }
}

enum class AccessDecision(val statusCode: Short, val descriptionArabic: String) {
    ACCESS_GRANTED(0x0000.toShort(), "تم فتح البوابة - دخول مصرح"),
    DENIED_EXPIRED(0x0001.toShort(), "مرفوض - اشتراك العضوية منتهي"),
    DENIED_ANTI_PASSBACK(0x0002.toShort(), "مرفوض - مخالفة نظام منع التمرير المزدوج"),
    DENIED_INVALID_TOKEN(0x0003.toShort(), "مرفوض - رمز التحقق غير صالح أو مستخدم سابقاً")
}

data class TurnstileAccessEvent(
    val gateId: Byte,
    val credentialType: TurnstileCredentialType,
    val direction: TurnstileDirection,
    val credentialIdHex: String,
    val timestampSeconds: Long,
    val decision: AccessDecision,
    val relayUnlockCommandEmitted: Boolean
)

class RfidTurnstileTotpPassDriver {

    companion object {
        const val MAGIC_PREAMBLE: Short = 0x544E // 'TN'
        const val PACKET_LENGTH = 24
        const val TOTP_STEP_SECONDS = 30L
        const val TOTP_DIGITS = 6

        // 8-byte relay unlock command: STX 'UNLOCK' ETX
        val RELAY_UNLOCK_PULSE = byteArrayOf(
            0x02.toByte(), 0x55.toByte(), 0x4E.toByte(), 0x4C.toByte(),
            0x4F.toByte(), 0x43.toByte(), 0x4B.toByte(), 0x03.toByte()
        )
    }

    // In-memory anti-passback state tracking: MemberId -> Current Direction (ENTRY / EXIT)
    private val memberPresenceMap = mutableMapOf<String, TurnstileDirection>()

    // Used TOTP cache to prevent replay attacks: Token -> Expire Epoch Seconds
    private val consumedTotpTokens = mutableSetOf<String>()

    /**
     * Evaluates RFID card access through the 13.56 MHz turnstile with anti-passback enforcement.
     */
    fun evaluateRfidAccess(
        cardUidHex: String,
        memberId: String,
        isMembershipActive: Boolean,
        direction: TurnstileDirection,
        gateId: Byte = 0x01
    ): TurnstileAccessEvent {
        val now = System.currentTimeMillis() / 1000L

        if (!isMembershipActive) {
            return TurnstileAccessEvent(
                gateId = gateId,
                credentialType = TurnstileCredentialType.MIFARE_RFID_1356MHZ,
                direction = direction,
                credentialIdHex = cardUidHex,
                timestampSeconds = now,
                decision = AccessDecision.DENIED_EXPIRED,
                relayUnlockCommandEmitted = false
            )
        }

        // Anti-passback check: entry must alternate with exit
        val lastDirection = memberPresenceMap[memberId]
        if (lastDirection == direction) {
            return TurnstileAccessEvent(
                gateId = gateId,
                credentialType = TurnstileCredentialType.MIFARE_RFID_1356MHZ,
                direction = direction,
                credentialIdHex = cardUidHex,
                timestampSeconds = now,
                decision = AccessDecision.DENIED_ANTI_PASSBACK,
                relayUnlockCommandEmitted = false
            )
        }

        // Access granted: update state and emit relay unlock
        memberPresenceMap[memberId] = direction
        return TurnstileAccessEvent(
            gateId = gateId,
            credentialType = TurnstileCredentialType.MIFARE_RFID_1356MHZ,
            direction = direction,
            credentialIdHex = cardUidHex,
            timestampSeconds = now,
            decision = AccessDecision.ACCESS_GRANTED,
            relayUnlockCommandEmitted = true
        )
    }

    /**
     * Evaluates dynamic TOTP pass access (RFC 6238) with window drift (+-30s) and replay rejection.
     */
    fun evaluateTotpAccess(
        memberId: String,
        sharedSecret: ByteArray,
        submittedToken: String,
        direction: TurnstileDirection,
        gateId: Byte = 0x01,
        evaluationEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): TurnstileAccessEvent {
        // Prevent replay attacks
        val tokenKey = "$memberId:$submittedToken"
        if (consumedTotpTokens.contains(tokenKey)) {
            return TurnstileAccessEvent(
                gateId = gateId,
                credentialType = TurnstileCredentialType.DYNAMIC_TOTP_PASS,
                direction = direction,
                credentialIdHex = submittedToken,
                timestampSeconds = evaluationEpochSeconds,
                decision = AccessDecision.DENIED_INVALID_TOKEN,
                relayUnlockCommandEmitted = false
            )
        }

        // Verify TOTP across drift window: -1, 0, +1 interval
        val currentStep = evaluationEpochSeconds / TOTP_STEP_SECONDS
        var tokenValid = false

        for (stepOffset in -1L..1L) {
            val step = currentStep + stepOffset
            val expectedCode = generateTotpCode(sharedSecret, step)
            if (expectedCode == submittedToken) {
                tokenValid = true
                break
            }
        }

        if (!tokenValid) {
            return TurnstileAccessEvent(
                gateId = gateId,
                credentialType = TurnstileCredentialType.DYNAMIC_TOTP_PASS,
                direction = direction,
                credentialIdHex = submittedToken,
                timestampSeconds = evaluationEpochSeconds,
                decision = AccessDecision.DENIED_INVALID_TOKEN,
                relayUnlockCommandEmitted = false
            )
        }

        // Anti-passback check
        val lastDirection = memberPresenceMap[memberId]
        if (lastDirection == direction) {
            return TurnstileAccessEvent(
                gateId = gateId,
                credentialType = TurnstileCredentialType.DYNAMIC_TOTP_PASS,
                direction = direction,
                credentialIdHex = submittedToken,
                timestampSeconds = evaluationEpochSeconds,
                decision = AccessDecision.DENIED_ANTI_PASSBACK,
                relayUnlockCommandEmitted = false
            )
        }

        // Grant access, mark token as consumed, and record presence
        consumedTotpTokens.add(tokenKey)
        memberPresenceMap[memberId] = direction

        return TurnstileAccessEvent(
            gateId = gateId,
            credentialType = TurnstileCredentialType.DYNAMIC_TOTP_PASS,
            direction = direction,
            credentialIdHex = submittedToken,
            timestampSeconds = evaluationEpochSeconds,
            decision = AccessDecision.ACCESS_GRANTED,
            relayUnlockCommandEmitted = true
        )
    }

    /**
     * Generates a 6-digit TOTP code for a given secret and counter step using HMAC-SHA1.
     */
    fun generateTotpCode(secret: ByteArray, counterStep: Long): String {
        val counterBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counterStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "RAW"))
        val hash = mac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binaryCode = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = 10.0.pow(TOTP_DIGITS.toDouble()).toInt()
        val otp = binaryCode % modulo
        return String.format("%0${TOTP_DIGITS}d", otp)
    }

    /**
     * Builds a 24-byte binary telemetry frame with CRC-16-CCITT for IoT turnstile logs.
     */
    fun encodeTelemetryFrame(event: TurnstileAccessEvent): ByteArray {
        val buffer = ByteBuffer.allocate(PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(MAGIC_PREAMBLE)
        buffer.put(event.gateId)
        buffer.put(event.credentialType.code)
        buffer.put(event.direction.code)

        // 8 bytes credential identifier (padded or truncated)
        val idBytes = event.credentialIdHex.toByteArray(Charsets.US_ASCII)
        val credArray = ByteArray(8)
        System.arraycopy(idBytes, 0, credArray, 0, minOf(idBytes.size, 8))
        buffer.put(credArray)

        // 4 bytes timestamp
        buffer.putInt((event.timestampSeconds and 0xFFFFFFFFL).toInt())

        // 2 bytes status code
        buffer.putShort(event.decision.statusCode)

        // 3 bytes reserved flags
        val relayFlag: Byte = if (event.relayUnlockCommandEmitted) 0x01 else 0x00
        buffer.put(relayFlag)
        buffer.put(0x00.toByte())
        buffer.put(0x00.toByte())

        // Calculate CRC-16-CCITT over first 22 bytes
        val bodyBytes = ByteArray(22)
        System.arraycopy(buffer.array(), 0, bodyBytes, 0, 22)
        val crc = calculateCrc16Ccitt(bodyBytes)
        buffer.putShort(crc)

        return buffer.array()
    }

    /**
     * Validates and decodes a 24-byte turnstile telemetry packet.
     */
    fun decodeTelemetryFrame(packet: ByteArray): TurnstileAccessEvent {
        require(packet.size == PACKET_LENGTH) { "Telemetry frame must be exactly $PACKET_LENGTH bytes" }

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.short
        require(magic == MAGIC_PREAMBLE) { "Invalid magic preamble: 0x" + Integer.toHexString(magic.toInt() and 0xFFFF) }

        val bodyBytes = ByteArray(22)
        System.arraycopy(packet, 0, bodyBytes, 0, 22)
        val expectedCrc = buffer.getShort(22)
        val calculatedCrc = calculateCrc16Ccitt(bodyBytes)
        require(expectedCrc == calculatedCrc) { "CRC mismatch in turnstile packet" }

        val gateId = buffer.get(2)
        val credType = TurnstileCredentialType.fromCode(buffer.get(3))
        val direction = TurnstileDirection.fromCode(buffer.get(4))

        val credBytes = ByteArray(8)
        buffer.position(5)
        buffer.get(credBytes)
        val credId = String(credBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

        val timestamp = buffer.getInt(13).toLong() and 0xFFFFFFFFL
        val statusCode = buffer.getShort(17)
        val relayFlag = buffer.get(19)

        val decision = when (statusCode) {
            0x0000.toShort() -> AccessDecision.ACCESS_GRANTED
            0x0001.toShort() -> AccessDecision.DENIED_EXPIRED
            0x0002.toShort() -> AccessDecision.DENIED_ANTI_PASSBACK
            else -> AccessDecision.DENIED_INVALID_TOKEN
        }

        return TurnstileAccessEvent(
            gateId = gateId,
            credentialType = credType,
            direction = direction,
            credentialIdHex = credId,
            timestampSeconds = timestamp,
            decision = decision,
            relayUnlockCommandEmitted = relayFlag == 0x01.toByte()
        )
    }

    /**
     * CRC-16-CCITT (Polynomial 0x1021, Initial 0xFFFF)
     */
    fun calculateCrc16Ccitt(data: ByteArray): Short {
        var crc = 0xFFFF
        for (b in data) {
            for (i in 0..7) {
                val bit = ((b.toInt() shr (7 - i)) and 1) == 1
                val c15 = ((crc shr 15) and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) {
                    crc = crc xor 0x1021
                }
            }
        }
        return (crc and 0xFFFF).toShort()
    }
}
