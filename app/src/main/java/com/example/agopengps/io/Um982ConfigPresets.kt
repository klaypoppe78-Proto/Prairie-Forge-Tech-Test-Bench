package com.example.agopengps.io

/**
 * Standard configuration command presets for the Unicore UM982 dual-antenna RTK receiver.
 * Commands can be transmitted over USB OTG Serial or UDP socket directly to the receiver.
 */
object Um982ConfigPresets {

    data class Um982Command(
        val label: String,
        val description: String,
        val commandString: String
    )

    /**
     * Recommended 10Hz/20Hz dual-antenna navigation sentences:
     * - $KSXT at 0.1s (10Hz) or 0.05s (20Hz): Dual heading, pitch, roll, baseline length, RTK quality.
     * - $GNGGA at 0.1s: Standard NMEA position and altitude.
     * - $GNVTG at 0.1s: Speed over ground and track angle.
     */
    val COMMAND_PRESETS = listOf(
        Um982Command(
            label = "10Hz AgOpenGPS Dual Mode",
            description = "Enables \$KSXT, \$GNGGA, \$GNVTG at 10Hz (0.1s) for standard Teensy AIO guidance.",
            commandString = "CONFIG NMEA KSXT 0.1\r\nCONFIG NMEA GNGGA 0.1\r\nCONFIG NMEA GNVTG 0.1\r\n"
        ),
        Um982Command(
            label = "20Hz Ultra-Fast Dual Mode",
            description = "Enables \$KSXT and \$GNGGA at 20Hz (0.05s) for zero-latency high-speed row-crop tracking.",
            commandString = "CONFIG NMEA KSXT 0.05\r\nCONFIG NMEA GNGGA 0.05\r\n"
        ),
        Um982Command(
            label = "Set Baud to 460800",
            description = "Sets COM port baud rate to 460,800 bps for AIO high-speed multi-constellation RTK.",
            commandString = "CONFIG COM1 460800 8 N 1\r\nCONFIG COM2 460800 8 N 1\r\n"
        ),
        Um982Command(
            label = "Set Baud to 115200",
            description = "Sets COM port baud rate to standard 115,200 bps.",
            commandString = "CONFIG COM1 115200 8 N 1\r\nCONFIG COM2 115200 8 N 1\r\n"
        ),
        Um982Command(
            label = "Save Config to NVRAM",
            description = "Permanently saves active configuration settings to UM982 internal flash memory.",
            commandString = "SAVECONFIG\r\n"
        ),
        Um982Command(
            label = "Heading Roll Baseline Alignment",
            description = "Configures antenna baseline parameters (Antenna 1 Master, Antenna 2 Slave).",
            commandString = "CONFIG HEADING ANT1 ANT2\r\nCONFIG RTK TIMEOUT 60\r\n"
        )
    )

    /**
     * Unicore UM982 Detailed Status from $KSXT:
     * $KSXT,time,lon,lat,alt,yaw,pitch,roll_or_baseline,qual,status*hh
     */
    data class Um982Status(
        val isDualLocked: Boolean = false,
        val baselineLengthMeters: Double = 0.0,
        val trueHeadingDeg: Double = 0.0,
        val rollDeg: Double = 0.0,
        val pitchDeg: Double = 0.0,
        val rtkQuality: String = "No Fix",
        val satellites: Int = 0,
        val lastSentence: String = ""
    )
}
