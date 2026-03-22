package com.example.mementoandroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Last known fused location when fine or coarse location permission is granted.
 * Used when photo EXIF has no GPS so uploads can still include coordinates.
 */
object DeviceLocationHelper {

    suspend fun getLastKnownOrNull(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (!cont.isCompleted) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (!cont.isCompleted) cont.resume(null)
                }
        }
    }
}
