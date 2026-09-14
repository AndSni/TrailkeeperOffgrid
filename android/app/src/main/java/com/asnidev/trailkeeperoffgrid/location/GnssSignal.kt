package com.asnidev.trailkeeperoffgrid.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.getSystemService

/**
 * 0 (no fix) .. 4 (strong fix) signal-strength bars, Nokia-coverage-style,
 * derived from how many GNSS satellites are actually contributing to the
 * current fix (not just visible). There's no direct "signal strength" API
 * on Android - satellite-used count is the closest honest proxy.
 */
@SuppressLint("MissingPermission")
class GnssSignalMonitor(context: Context, private val onBars: (Int) -> Unit) {
    private val manager = context.getSystemService<LocationManager>()
    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            onBars(
                when {
                    used >= 8 -> 4
                    used >= 5 -> 3
                    used >= 2 -> 2
                    used >= 1 -> 1
                    else -> 0
                }
            )
        }

        override fun onStopped() {
            onBars(0)
        }
    }

    fun start() {
        manager?.registerGnssStatusCallback(callback, null)
    }

    fun stop() {
        manager?.unregisterGnssStatusCallback(callback)
        onBars(0)
    }
}
