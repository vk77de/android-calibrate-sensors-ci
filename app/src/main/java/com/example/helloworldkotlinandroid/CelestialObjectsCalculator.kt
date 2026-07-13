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

        list.add(
            TargetBody(
                "Sirius",
                computeAltAz(101.287, -16.716, lat, lst).azimuth,
                computeAltAz(101.287, -16.716, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Arcturus",
                computeAltAz(213.915, 19.182, lat, lst).azimuth,
                computeAltAz(213.915, 19.182, lat, lst).altitude
            )
        )

        val venusRa = (244.19 + 0.9856 * d) % 360
        val venusDec = -22.0 * cos(Math.toRadians(venusRa))
        list.add(
            TargetBody(
                "Venus",
                computeAltAz(venusRa, venusDec, lat, lst).azimuth,
                computeAltAz(venusRa, venusDec, lat, lst).altitude
            )
        )

        val jupiterRa = (304.02 + 0.0831 * d) % 360
        val jupiterDec = -19.5 * cos(Math.toRadians(jupiterRa))
        list.add(
            TargetBody(
                "Jupiter",
                computeAltAz(jupiterRa, jupiterDec, lat, lst).azimuth,
                computeAltAz(jupiterRa, jupiterDec, lat, lst).altitude
            )
        )

        val marsRa = (355.43 + 191.399 * (d / 365.25)) % 360
        val marsDec = 24.0 * sin(Math.toRadians(marsRa))
        list.add(
            TargetBody(
                "Mars",
                computeAltAz(marsRa, marsDec, lat, lst).azimuth,
                computeAltAz(marsRa, marsDec, lat, lst).altitude
            )
        )

        val saturnRa = (49.95 + 12.221 * (d / 365.25)) % 360
        val saturnDec = 2.5 * sin(Math.toRadians(saturnRa))
        list.add(
            TargetBody(
                "Saturn",
                computeAltAz(saturnRa, saturnDec, lat, lst).azimuth,
                computeAltAz(saturnRa, saturnDec, lat, lst).altitude
            )
        )

        list.add(
            TargetBody(
                "Canopus",
                computeAltAz(95.987, -52.697, lat, lst).azimuth,
                computeAltAz(95.987, -52.697, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Alpha Centauri",
                computeAltAz(219.902, -60.833, lat, lst).azimuth,
                computeAltAz(219.902, -60.833, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Vega",
                computeAltAz(279.234, 38.783, lat, lst).azimuth,
                computeAltAz(279.234, 38.783, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Capella",
                computeAltAz(79.172, 45.998, lat, lst).azimuth,
                computeAltAz(79.172, 45.998, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Rigel",
                computeAltAz(78.634, -8.201, lat, lst).azimuth,
                computeAltAz(78.634, -8.201, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Procyon",
                computeAltAz(114.825, 5.224, lat, lst).azimuth,
                computeAltAz(114.825, 5.224, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Achernar",
                computeAltAz(24.428, -57.236, lat, lst).azimuth,
                computeAltAz(24.428, -57.236, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Betelgeuse",
                computeAltAz(88.792, 7.407, lat, lst).azimuth,
                computeAltAz(88.792, 7.407, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Altair",
                computeAltAz(297.695, 8.868, lat, lst).azimuth,
                computeAltAz(297.695, 8.868, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Aldebaran",
                computeAltAz(68.980, 16.509, lat, lst).azimuth,
                computeAltAz(68.980, 16.509, lat, lst).altitude
            )
        )

        list.add(
            TargetBody(
                "Sagittarius A*",
                computeAltAz(266.417, -29.008, lat, lst).azimuth,
                computeAltAz(266.417, -29.008, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Great Attractor",
                computeAltAz(200.000, -44.000, lat, lst).azimuth,
                computeAltAz(200.000, -44.000, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Shapley Attractor",
                computeAltAz(201.250, -31.000, lat, lst).azimuth,
                computeAltAz(201.250, -31.000, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Dipole Repeller",
                computeAltAz(318.500, 17.000, lat, lst).azimuth,
                computeAltAz(318.500, 17.000, lat, lst).altitude
            )
        )
        list.add(
            TargetBody(
                "Cold Spot Repeller",
                computeAltAz(48.750, -19.500, lat, lst).azimuth,
                computeAltAz(48.750, -19.500, lat, lst).altitude
            )
        )

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

        return MoonCalculator.Position(az, alt)
    }
}

object MoonCalculator {
    data class Position(val azimuth: Double, val altitude: Double)

    fun getPosition(lat: Double, lon: Double): Position {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val timeMs = cal.timeInMillis

        val d = (timeMs / 86400000.0) + 2440587.5 - 2451545.0
        var l = 218.316 + 13.176396 * d
        val m = 134.963 + 13.064993 * d
        val f = 93.272 + 13.229350 * d

        l += 6.289 * sin(Math.toRadians(m))
        val b = 5.128 * sin(Math.toRadians(f))
        val ecl = Math.toRadians(23.439 - 0.0000004 * d)

        val cosB = cos(Math.toRadians(b))
        val sinB = sin(Math.toRadians(b))
        val cosL = cos(Math.toRadians(l))
        val sinL = sin(Math.toRadians(l))

        val x = cosB * cosL
        val y = cosB * sinL * cos(ecl) - sinB * sin(ecl)
        val z = cosB * sinL * sin(ecl) + sinB * cos(ecl)

        val ra = Math.toDegrees(atan2(y, x))
        val dec = Math.toDegrees(asin(z))

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val utcHours = hour + min / 60.0 + sec / 3600.0

        var lst = 100.46 + 0.98564736 * d + lon + 15.0 * utcHours
        lst = (lst % 360 + 360) % 360

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

        return Position(azimuth = az, altitude = alt)
    }
}
