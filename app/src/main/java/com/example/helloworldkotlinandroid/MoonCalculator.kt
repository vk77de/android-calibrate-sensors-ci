// File: ./app/src/main/java/com/example/helloworldkotlinandroid/MoonCalculator.kt
package com.example.helloworldkotlinandroid

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object MoonCalculator {
    data class Position(val azimuth: Double, val altitude: Double, val ra: Double = Double.NaN)

    /**
     * Calculates the topocentric position of the Moon using Meeus-based lunar perturbations.
     * @param lat Device latitude in degrees
     * @param lon Device longitude in degrees
     * @return Position object containing Azimuth, Altitude, and RA in degrees
     */
    fun getPosition(lat: Double, lon: Double): Position {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        // Julian Date relative to J2000.0
        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        // Fundamental Arguments (Meeus Astronomical Algorithms)
        val l0 = (218.316 + 13.176396 * d) % 360 // Mean longitude
        val m = (134.963 + 13.064993 * d) % 360 // Moon's mean anomaly
        val f = (93.272 + 13.229350 * d) % 360 // Argument of latitude
        val dElong = (297.850 + 12.190749 * d) % 360 // Mean elongation
        val mSolar = (357.529 + 0.985600 * d) % 360 // Sun's mean anomaly

        val mRad = Math.toRadians(m)
        val fRad = Math.toRadians(f)
        val dRad = Math.toRadians(dElong)
        val mSolarRad = Math.toRadians(mSolar)

        // Ecliptic longitude with major perturbation terms (Equation of Center, Evection, Variation)
        var l = l0 + 6.289 * sin(mRad) +
            1.274 * sin(2 * dRad - mRad) +
            0.658 * sin(2 * dRad) +
            0.214 * sin(2 * mRad) -
            0.186 * sin(mSolarRad) -
            0.114 * sin(2 * fRad)

        // Ecliptic latitude with major perturbation terms
        val b = 5.128 * sin(fRad) +
            0.280 * sin(mRad + fRad) +
            0.277 * sin(mRad - fRad) -
            0.173 * sin(2 * dRad - fRad)

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

        var ra = Math.toDegrees(atan2(y, x))
        ra = (ra % 360 + 360) % 360
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

        // East (yAz) and North (xAz) components
        val yAz = -sin(haRad)
        val xAz = tan(decRad) * sin(latRad) - cos(haRad) * cos(latRad) // Fixed trig formula
        var az = Math.toDegrees(atan2(yAz, xAz))
        az = (az % 360 + 360) % 360 // Normalize 0-360 (North = 0, East = 90)

        return Position(azimuth = az, altitude = alt, ra = ra)
    }
}
