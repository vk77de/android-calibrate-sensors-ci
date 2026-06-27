package com.example.helloworldkotlinandroid

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object MoonCalculator {
    data class Position(val azimuth: Double, val altitude: Double)

    /**
     * Calculates the approximate topocentric position of the Moon.
     * @param lat Device latitude in degrees
     * @param lon Device longitude in degrees
     * @return Position object containing Azimuth and Altitude in degrees
     */
    fun getPosition(
        lat: Double,
        lon: Double,
    ): Position {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        // Julian Date relative to J2000.0
        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        // Mean longitude of the Moon
        var l = 218.316 + 13.176396 * d
        // Mean anomaly of the Moon
        val m = 134.963 + 13.064993 * d
        // Mean distance of the Moon from ascending node
        val f = 93.272 + 13.229350 * d

        // Ecliptic longitude approximation
        l += 6.289 * sin(Math.toRadians(m))

        // Ecliptic latitude approximation
        val b = 5.128 * sin(Math.toRadians(f))

        // Obliquity of the ecliptic
        val ecl = Math.toRadians(23.439 - 0.0000004 * d)

        val cosB = cos(Math.toRadians(b))
        val sinB = sin(Math.toRadians(b))
        val cosL = cos(Math.toRadians(l))
        val sinL = sin(Math.toRadians(l))

        // Convert to Equatorial Coordinates (Right Ascension / Declination)
        val x = cosB * cosL
        val y = cosB * sinL * cos(ecl) - sinB * sin(ecl)
        val z = cosB * sinL * sin(ecl) + sinB * cos(ecl)

        val ra = Math.toDegrees(atan2(y, x))
        val dec = Math.toDegrees(asin(z))

        // Calculate Local Sidereal Time (LST)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0

        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

        // Hour Angle
        var ha = lst - ra
        ha = (ha % 360 + 360) % 360

        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val haRad = Math.toRadians(ha)

        // Convert to Horizontal Coordinates (Altitude / Azimuth)
        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val alt = Math.toDegrees(asin(sinAlt))

        val yAz = sin(haRad)
        val xAz = cos(haRad) * sin(latRad) - tan(decRad) * cos(latRad)
        var az = Math.toDegrees(atan2(yAz, xAz))
        az = (az % 360 + 360) % 360 // Normalize 0-360 (North = 0, East = 90)

        return Position(azimuth = az, altitude = alt)
    }
}
