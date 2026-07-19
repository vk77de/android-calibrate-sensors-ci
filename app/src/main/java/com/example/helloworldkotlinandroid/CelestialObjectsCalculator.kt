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
        val altitude: Double
    )

    private data class StarData(
        val name: String,
        val ra: Double,
        val dec: Double
    )

    private val StarCatalog = listOf(
        StarData("Sirius", 101.287, -16.716),
        StarData("Canopus", 95.987, -52.697),
        StarData("Alpha Centauri", 219.902, -60.833),
        StarData("Arcturus", 213.915, 19.182),
        StarData("Vega", 279.234, 38.783),
        StarData("Capella", 79.172, 45.998),
        StarData("Rigel", 78.634, -8.201),
        StarData("Procyon", 114.825, 5.224),
        StarData("Achernar", 24.428, -57.236),
        StarData("Betelgeuse", 88.792, 7.407),
        StarData("Altair", 297.695, 8.868),
        StarData("Aldebaran", 68.980, 16.509),
        StarData("Spica", 201.298, -11.161),
        StarData("Antares", 247.351, -26.432),
        StarData("Pollux", 116.328, 28.026),
        StarData("Fomalhaut", 344.412, -29.622),
        StarData("Deneb", 310.357, 45.280),
        StarData("Regulus", 152.093, 11.967),
        StarData("Castor", 113.650, 31.888),
        StarData("Bellatrix", 81.282, 6.349),
        StarData("Elnath", 81.572, 28.602),
        StarData("Alnilam", 84.053, -1.201),
        StarData("Polaris", 37.950, 89.264),
        StarData("Dubhe", 165.930, 61.751),
        StarData("Merak", 165.460, 56.382),
        StarData("Phecda", 178.450, 53.698),
        StarData("Megrez", 183.040, 57.032),
        StarData("Alioth", 194.270, 55.960),
        StarData("Mizar", 200.980, 54.918),
        StarData("Alkaid", 206.880, 49.314)
    )

    private val DeepSpaceCatalog = listOf(
        StarData("Sagittarius A*", 266.417, -29.008),
        StarData("Great Attractor", 200.000, -44.000),
        StarData("Shapley Attractor", 201.250, -31.000),
        StarData("Dipole Repeller", 318.500, 17.000),
        StarData("Cold Spot Repeller", 48.750, -19.500)
    )

    fun getVenusPosition(lat: Double, lon: Double): MoonCalculator.Position {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0
        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

        var ra = (244.19 + 0.9856 * d) % 360
        ra = (ra % 360 + 360) % 360
        val dec = -22.0 * cos(Math.toRadians(ra))

        val pos = computeAltAz(ra, dec, lat, lst)
        return MoonCalculator.Position(pos.azimuth, pos.altitude, ra)
    }

    fun getSunPosition(lat: Double, lon: Double): MoonCalculator.Position {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0
        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

        val l = (280.46 + 0.9856474 * d) % 360
        val g = Math.toRadians((357.528 + 0.9856003 * d) % 360)
        val lambda = l + 1.915 * sin(g) + 0.020 * sin(2 * g)
        val lambdaRad = Math.toRadians(lambda)
        val ecl = Math.toRadians(23.439 - 0.0000004 * d)

        var ra = Math.toDegrees(atan2(cos(ecl) * sin(lambdaRad), cos(lambdaRad)))
        ra = (ra % 360 + 360) % 360
        val dec = Math.toDegrees(asin(sin(ecl) * sin(lambdaRad)))

        val pos = computeAltAz(ra, dec, lat, lst)
        return MoonCalculator.Position(pos.azimuth, pos.altitude, ra)
    }

    fun getCalibratedObjects(lat: Double, lon: Double): List<TargetBody> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0
        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

        val list = mutableListOf<TargetBody>()

        val moonPos = MoonCalculator.getPosition(lat, lon)
        list.add(TargetBody("Moon", moonPos.azimuth, moonPos.altitude))

        val sunPos = getSunPosition(lat, lon)
        list.add(TargetBody("Sun", sunPos.azimuth, sunPos.altitude))

        val planets = listOf(
            Triple(
                "Venus",
                (244.19 + 0.9856 * d) % 360,
                -22.0 * cos(Math.toRadians((244.19 + 0.9856 * d) % 360))
            ),
            Triple(
                "Jupiter",
                (304.02 + 0.0831 * d) % 360,
                -19.5 * cos(Math.toRadians((304.02 + 0.0831 * d) % 360))
            ),
            Triple(
                "Mars",
                (355.43 + 191.399 * (d / 365.25)) % 360,
                24.0 * sin(Math.toRadians((355.43 + 191.399 * (d / 365.25)) % 360))
            ),
            Triple(
                "Saturn",
                (49.95 + 12.221 * (d / 365.25)) % 360,
                2.5 * sin(Math.toRadians((49.95 + 12.221 * (d / 365.25)) % 360))
            )
        )

        for ((name, ra, dec) in planets) {
            val pos = computeAltAz(ra, dec, lat, lst)
            list.add(TargetBody(name, pos.azimuth, pos.altitude))
        }

        for (star in StarCatalog) {
            val pos = computeAltAz(star.ra, star.dec, lat, lst)
            list.add(TargetBody(star.name, pos.azimuth, pos.altitude))
        }

        for (anomaly in DeepSpaceCatalog) {
            val pos = computeAltAz(anomaly.ra, anomaly.dec, lat, lst)
            list.add(TargetBody(anomaly.name, pos.azimuth, pos.altitude))
        }

        return list
    }

    private fun computeAltAz(
        ra: Double,
        dec: Double,
        lat: Double,
        lst: Double
    ): MoonCalculator.Position {
        var ha = lst - ra
        ha = (ha % 360 + 360) % 360

        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val haRad = Math.toRadians(ha)

        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
        val altRad = asin(sinAlt)
        val alt = Math.toDegrees(altRad)

        val azRad = atan2(sin(haRad), (sin(latRad) * cos(haRad) - tan(decRad) * cos(latRad)))
        var az = Math.toDegrees(azRad)
        az = (az + 180) % 360

        return MoonCalculator.Position(az, alt, ra)
    }
}
