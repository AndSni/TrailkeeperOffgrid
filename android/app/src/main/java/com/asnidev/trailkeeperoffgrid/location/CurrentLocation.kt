package com.asnidev.trailkeeperoffgrid.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A fresh, high-accuracy GPS fix for one-shot "where am I right now" uses
 * (marking a task/structure, picking a point). The fused provider's
 * `lastLocation` is a *cached* fix — it can be minutes old and/or a coarse
 * network/wifi position with an accuracy circle tens of meters wide, which
 * is what made on-the-ground marking imprecise. `getCurrentLocation` with
 * PRIORITY_HIGH_ACCURACY forces a real GPS-backed fix instead.
 */
@SuppressLint("MissingPermission")
suspend fun freshLocation(context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
