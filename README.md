# تطبيق ملتقى الأعمال — مطلوب 3 مساعدين لإدخال البيا (`multaqa-hub-coworking-1289512`)
> منظومة coworking المتكاملة مع ربط 13.56 MHz RFID Turnstile Access + TOTP Pass

- **Project ID**: `#1289512`
- **Commercial Archetype**: `coworking`
- **Bound Hardware Peripheral**: `13.56 MHz RFID Turnstile Access + TOTP Pass`
- **Package Name**: `sa.factory.multaqahubcoworkin`
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)


## [Sidekick Autonomous Update]

### Architecture & Smart Coworking Enhancements
- **Coworking Space Allocation Engine (`CoworkingSpaceAllocationEngine.kt`)**:
  - Multi-tier workspace provisioning: Hot Desk (flexible shared zone), Dedicated Desk (permanent workstation), Acoustic Podcast Studio, Private Team Suite, and Executive Boardroom.
  - Dynamic reservation quote calculation with peak hour surcharges (+15%), dedicated gigabit fibre SLA add-ons, and barista coffee credit allocations.
  - Tiered membership discount matrix: Guest (0%), Bronze Startup (5%), Silver Scaleup (12%), and Gold Enterprise (20%).
  - ZATCA Phase 2 15% VAT calculation and certified tax breakdown.
  - Multi-slot collision and overlap avoidance algorithms `(requestedStart < existingEnd && requestedEnd > existingStart)`.
  - Zone occupancy analytics and capacity limit warning thresholds (>= 85% utilization).

- **13.56 MHz RFID Turnstile & TOTP Pass Driver (`RfidTurnstileTotpPassDriver.kt`)**:
  - ISO/IEC 14443 Type A MIFARE RFID access protocol over magic preamble `0x544E` ('TN').
  - Anti-passback (APBL) security enforcement prohibiting double entry or exit without sequential transition.
  - RFC 6238 Time-Based One-Time Password (TOTP) pass generator and drift validation engine (+-30s window absorption).
  - Single-use consumed-token cache preventing token replay attacks.
  - 8-byte turnstile relay unlock motor pulse command (`0x02, 0x55, 0x4E, 0x4C, 0x4F, 0x43, 0x4B, 0x03` = `STX UNLOCK ETX`).
  - 24-byte binary telemetry framing with CRC-16-CCITT (polynomial `0x1021`) integrity validation.

- **Hardware Driver Integration (`HardwareSensorDriver.kt`)**:
  - Bound directly to `RfidTurnstileTotpPassDriver` for real-time telemetry streaming and access event dispatch.

- **Automated Verification Harness**:
  - Comprehensive unit test suite in `sa.factory.multaqahubcoworkin.MultaqaHubFeatureTest` covering 7 test scenarios (cost calculation, membership tier discounts, time slot collision avoidance, zone occupancy thresholds, RFID anti-passback transitions, TOTP token drift and replay defense, and binary telemetry CRC integrity).
  - 100% test pass rate verified on OpenJDK 17 and Kotlin compiler.

### Build and Verification Instructions
```bash
export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH
CP="/usr/share/java/junit4-4.13.2.jar:/usr/share/java/hamcrest-2.2.jar:/usr/share/java/kotlin-stdlib.jar"
mkdir -p /tmp/iter27_bin
kotlinc -Xskip-metadata-version-check -cp "$CP" -d /tmp/iter27_bin \
  app/src/main/java/sa/factory/multaqahubcoworkin/space/CoworkingSpaceAllocationEngine.kt \
  app/src/main/java/sa/factory/multaqahubcoworkin/hardware/RfidTurnstileTotpPassDriver.kt \
  app/src/test/java/sa/factory/multaqahubcoworkin/MultaqaHubFeatureTest.kt
java -cp "/tmp/iter27_bin:$CP" org.junit.runner.JUnitCore sa.factory.multaqahubcoworkin.MultaqaHubFeatureTest
```
