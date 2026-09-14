package com.asnidev.trailkeeperoffgrid.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import androidx.core.content.getSystemService

data class DevSatellite(
    val svid: Int,
    val constellation: String,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
)

data class DevGpsState(
    val location: Location? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val satellites: List<DevSatellite> = emptyList(),
)

/**
 * Live GPS diagnostics for Settings → Developer options: raw position and
 * per-satellite signal (constellation, CN0, used-in-fix) - for debugging
 * poor-connection field reports. Started/stopped with the screen's own
 * lifecycle (`start`/`stop`), never run in the background.
 */
@SuppressLint("MissingPermission")
class DeveloperGpsMonitor(private val context: Context, private val onUpdate: (DevGpsState) -> Unit) {
    private var state = DevGpsState()
    private val locationManager = context.getSystemService<LocationManager>()

    private val locationMonitor = RawGps.Monitor(minTimeMs = 1_000L) { loc ->
        state = state.copy(location = loc)
        onUpdate(state)
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val sats = ArrayList<DevSatellite>(status.satelliteCount)
            for (i in 0 until status.satelliteCount) {
                val inFix = status.usedInFix(i)
                if (inFix) used++
                sats.add(
                    DevSatellite(
                        svid = status.getSvid(i),
                        constellation = constellationName(status.getConstellationType(i)),
                        cn0DbHz = status.getCn0DbHz(i),
                        usedInFix = inFix,
                    )
                )
            }
            sats.sortByDescending { it.cn0DbHz }
            state = state.copy(
                satellitesUsed = used,
                satellitesVisible = status.satelliteCount,
                satellites = sats,
            )
            onUpdate(state)
        }
    }

    fun start() {
        locationMonitor.start(context)
        locationManager?.registerGnssStatusCallback(gnssCallback, null)
    }

    fun stop() {
        locationMonitor.stop()
        locationManager?.unregisterGnssStatusCallback(gnssCallback)
    }

    companion object {
        private fun constellationName(type: Int): String = when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
            GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
            else -> "Unknown"
        }
    }
}
