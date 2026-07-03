package com.example.helloworldkotlinandroid

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object CelestialObjectsCalculator {
    data class TargetBody(
        val name: String,
        val azimuth: Double,
        val altitude: Double,
    )

    /**
     * Solves the topocentric horizontal coordinates for the requested objects.
     */
    fun getCalibratedObjects(
        lat: Double,
        lon: Double,
    ): List<TargetBody> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        // Julian Date relative to J2000.0 baseline
        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        // Local Sidereal Time Calculation
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0
        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

        val list = mutableListOf<TargetBody>()

        // 1. Sirius (Fixed deep-space coordinates adjusted for J2000)
        val siriusCoords = computeAltAz(101.287, -16.716, lat, lst)
        list.add(TargetBody("Sirius", siriusCoords.azimuth, siriusCoords.altitude))

        // 2. Arcturus (Fixed deep-space coordinates)
        val arcturusCoords = computeAltAz(213.915, 19.182, lat, lst)
        list.add(TargetBody("Arcturus", arcturusCoords.azimuth, arcturusCoords.altitude))

        // 3. Venus (Approximated Mean Keplerian Elements relative to Earth)
        val venusRa = (181.98 + 585.178 * (d / 365.25)) % 360
        val venusDec = 20.0 * sin(Math.toRadians(venusRa))
        val venusCoords = computeAltAz(venusRa, venusDec, lat, lst)
        list.add(TargetBody("Venus", venusCoords.azimuth, venusCoords.altitude))

        // 4. Jupiter
        val jupiterRa = (34.35 + 30.349 * (d / 365.25)) % 360
        val jupiterDec = 12.0 * sin(Math.toRadians(jupiterRa))
        val jupiterCoords = computeAltAz(jupiterRa, jupiterDec, lat, lst)
        list.add(TargetBody("Jupiter", jupiterCoords.azimuth, jupiterCoords.altitude))

        // 5. Mars
        val marsRa = (355.43 + 191.399 * (d / 365.25)) % 360
        val marsDec = 24.0 * sin(Math.toRadians(marsRa))
        val marsCoords = computeAltAz(marsRa, marsDec, lat, lst)
        list.add(TargetBody("Mars", marsCoords.azimuth, marsCoords.altitude))

        // 6. Saturn
        val saturnRa = (49.95 + 12.221 * (d / 365.25)) % 360
        val saturnDec = 2.5 * sin(Math.toRadians(saturnRa))
        val saturnCoords = computeAltAz(saturnRa, saturnDec, lat, lst)
        list.add(TargetBody("Saturn", saturnCoords.azimuth, saturnCoords.altitude))

        return list
    }

    private fun computeAltAz(
        ra: Double,
        dec: Double,
        lat: Double,
        lst: Double,
    ): MoonCalculator.Position {
        var ha = lst - ra
        ha = (ha % 360 + 360) % 360

        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val haRad = Math.toRadians(ha)

        val sinAlt = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val alt = Math.toDegrees(asin(sinAlt))

        val yAz = sin(haRad)
        val xAz = cos(haRad) * sin(latRad) - tan(decRad) * cos(latRad)
        var az = Math.toDegrees(atan2(yAz, xAz))
        az = (az % 360 + 360) % 360

        return MoonCalculator.Position(azimuth = az, altitude = alt)
    }
}
