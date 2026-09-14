package com.asnidev.trailkeeperoffgrid.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pure-GNSS location: no Play Services, no network-based positioning. This
 * app is meant to work flawlessly off-grid, so it talks to the GPS chip
 * directly via [LocationManager]'s `GPS_PROVIDER` - the same category of
 * source a dedicated handheld GPS unit uses (satellite signal only, no
 * "fused"/network-provider ambiguity to be dragged off by a stale WiFi/cell
 * fix). See `docs/` / the v0.3.3 changelog entry for the investigation that
 * led here: MapLibre's own default location engine mixes in the coarse
 * `NETWORK_PROVIDER` and applies whichever fires, which is what was
 * producing wildly worse accuracy on the map than everywhere else in the
 * app.
 */
object RawGps {

    /** A one-shot fix for "mark here" uses (a task, a structure, a point).
     * Waits for the GPS chip's next real fix - not a cached value - up to
     * [timeoutMs], since a cold GPS start can take a while. */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context, timeoutMs: Long = 20_000L): Location? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    cont.resumeWith(Result.success(null))
                    return@suspendCancellableCoroutine
                }
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resumeWith(Result.success(location))
                    }

                    @Deprecated("Deprecated in Java, still the interface method to override")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resumeWith(Result.success(null))
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()
                )
            }
        }

    /**
     * Continuous fixes for the map puck / track recording. [minTimeMs] and
     * [minDistanceM] throttle how often the chip is asked to report; `0`/`0`
     * asks for every fix it produces.
     */
    @SuppressLint("MissingPermission")
    class Monitor(
        private val minTimeMs: Long = 1_000L,
        private val minDistanceM: Float = 0f,
        private val onLocation: (Location) -> Unit,
    ) {
        private var manager: LocationManager? = null
        private val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location)

            @Deprecated("Deprecated in Java, still the interface method to override")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        fun start(context: Context) {
            val m = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            manager = m
            if (!m.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
            m.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, listener, Looper.getMainLooper()
            )
            m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(onLocation)
        }

        fun stop() {
            manager?.removeUpdates(listener)
            manager = null
        }
    }
}
