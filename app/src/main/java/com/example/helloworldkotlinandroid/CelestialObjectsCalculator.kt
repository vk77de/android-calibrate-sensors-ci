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

        // ==========================================
        // 0. DYNAMIC OBJECTS
        // ==========================================
        val moonPos = MoonCalculator.getPosition(lat, lon)
        list.add(TargetBody("Moon", moonPos.azimuth, moonPos.altitude))

        // ==========================================
        // 1. ORIGINAL FIXED STARS & PLANETS
        // ==========================================
        // Sirius
        val siriusCoords = computeAltAz(101.287, -16.716, lat, lst)
        list.add(TargetBody("Sirius", siriusCoords.azimuth, siriusCoords.altitude))

        // Arcturus
        val arcturusCoords = computeAltAz(213.915, 19.182, lat, lst)
        list.add(TargetBody("Arcturus", arcturusCoords.azimuth, arcturusCoords.altitude))

        // Venus
        val venusRa = (244.19 + 0.9856 * d) % 360
        val venusDec = -22.0 * cos(Math.toRadians(venusRa))
        val venusCoords = computeAltAz(venusRa, venusDec, lat, lst)
        list.add(TargetBody("Venus", venusCoords.azimuth, venusCoords.altitude))

        // Jupiter
        val jupiterRa = (304.02 + 0.0831 * d) % 360
        val jupiterDec = -19.5 * cos(Math.toRadians(jupiterRa))
        val jupiterCoords = computeAltAz(jupiterRa, jupiterDec, lat, lst)
        list.add(TargetBody("Jupiter", jupiterCoords.azimuth, jupiterCoords.altitude))

        // Mars
        val marsRa = (355.43 + 191.399 * (d / 365.25)) % 360
        val marsDec = 24.0 * sin(Math.toRadians(marsRa))
        val marsCoords = computeAltAz(marsRa, marsDec, lat, lst)
        list.add(TargetBody("Mars", marsCoords.azimuth, marsCoords.altitude))

        // Saturn
        val saturnRa = (49.95 + 12.221 * (d / 365.25)) % 360
        val saturnDec = 2.5 * sin(Math.toRadians(saturnRa))
        val saturnCoords = computeAltAz(saturnRa, saturnDec, lat, lst)
        list.add(TargetBody("Saturn", saturnCoords.azimuth, saturnCoords.altitude))

        // ==========================================
        // 2. NEW REQUESTED FIRST-MAGNITUDE STARS
        // ==========================================
        // Canopus (Alpha Carinae)
        val canopusCoords = computeAltAz(95.987, -52.697, lat, lst)
        list.add(TargetBody("Canopus", canopusCoords.azimuth, canopusCoords.altitude))

        // Alpha Centauri (Rigil Kentaurus)
        val alphaCentauriCoords = computeAltAz(219.902, -60.833, lat, lst)
        list.add(TargetBody("Alpha Centauri", alphaCentauriCoords.azimuth, alphaCentauriCoords.altitude))

        // Vega (Alpha Lyrae)
        val vegaCoords = computeAltAz(279.234, 38.783, lat, lst)
        list.add(TargetBody("Vega", vegaCoords.azimuth, vegaCoords.altitude))

        // Capella (Alpha Aurigae)
        val capellaCoords = computeAltAz(79.172, 45.998, lat, lst)
        list.add(TargetBody("Capella", capellaCoords.azimuth, capellaCoords.altitude))

        // Rigel (Beta Orionis)
        val rigelCoords = computeAltAz(78.634, -8.201, lat, lst)
        list.add(TargetBody("Rigel", rigelCoords.azimuth, rigelCoords.altitude))

        // Procyon (Alpha Canis Minoris)
        val procyonCoords = computeAltAz(114.825, 5.224, lat, lst)
        list.add(TargetBody("Procyon", procyonCoords.azimuth, procyonCoords.altitude))

        // Achernar (Alpha Eridani)
        val achernarCoords = computeAltAz(24.428, -57.236, lat, lst)
        list.add(TargetBody("Achernar", achernarCoords.azimuth, achernarCoords.altitude))

        // Betelgeuse (Alpha Orionis)
        val betelgeuseCoords = computeAltAz(88.792, 7.407, lat, lst)
        list.add(TargetBody("Betelgeuse", betelgeuseCoords.azimuth, betelgeuseCoords.altitude))

        // Altair (Alpha Aquilae)
        val altairCoords = computeAltAz(297.695, 8.868, lat, lst)
        list.add(TargetBody("Altair", altairCoords.azimuth, altairCoords.altitude))

        // Aldebaran (Alpha Tauri)
        val aldebaranCoords = computeAltAz(68.980, 16.509, lat, lst)
        list.add(TargetBody("Aldebaran", aldebaranCoords.azimuth, aldebaranCoords.altitude))

        // ==========================================
        // 3. GALACTIC CENTER & LARGE COSMIC STRUCTURES
        // ==========================================
        // Galaxy center Sagittarius A*
        val sagAStarCoords = computeAltAz(266.417, -29.008, lat, lst)
        list.add(TargetBody("Sagittarius A*", sagAStarCoords.azimuth, sagAStarCoords.altitude))

        // Great Attractor (Center of Laniakea Supercluster / Norma Cluster)
        val greatAttractorCoords = computeAltAz(200.000, -44.000, lat, lst)
        list.add(TargetBody("Great Attractor", greatAttractorCoords.azimuth, greatAttractorCoords.altitude))

        // Shapley Attractor (Shapley Supercluster Core)
        val shapleyAttractorCoords = computeAltAz(201.250, -31.000, lat, lst)
        list.add(TargetBody("Shapley Attractor", shapleyAttractorCoords.azimuth, shapleyAttractorCoords.altitude))

        // Dipole Repeller
        val dipoleRepellerCoords = computeAltAz(318.500, 17.000, lat, lst)
        list.add(TargetBody("Dipole Repeller", dipoleRepellerCoords.azimuth, dipoleRepellerCoords.altitude))

        // The Cold Spot Repeller
        val coldSpotRepellerCoords = computeAltAz(48.750, -19.500, lat, lst)
        list.add(TargetBody("Cold Spot Repeller", coldSpotRepellerCoords.azimuth, coldSpotRepellerCoords.altitude))

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

        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
        val altRad = asin(sinAlt)
        val alt = Math.toDegrees(altRad)

        val cosAzNum = sin(decRad) - sin(altRad) * sin(latRad)
        val cosAzDen = cos(altRad) * cos(latRad)
        var azRad = atan2(sin(haRad), (sin(latRad) * cos(haRad) - tan(decRad) * cos(latRad)))
        var az = Math.toDegrees(azRad)
        az = (az + 180) % 360

        return MoonCalculator.Position(az, alt)
    }
}
