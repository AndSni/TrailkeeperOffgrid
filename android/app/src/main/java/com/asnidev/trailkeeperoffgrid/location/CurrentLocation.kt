package com.asnidev.trailkeeperoffgrid.location

import android.content.Context
import android.location.Location

/**
 * A fresh GPS fix for one-shot "where am I right now" uses (marking a
 * task/structure, picking a point) - the next real fix from the GPS chip,
 * not a cached value, so marking on the ground lands where you're
 * actually standing. See [RawGps] for why this is pure GNSS rather than
 * the fused/network-capable location APIs.
 */
suspend fun freshLocation(context: Context): Location? = RawGps.currentFix(context)
