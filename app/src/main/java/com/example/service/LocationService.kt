package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationResultData(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val mapUrl: String
)

class LocationService(private val context: Context) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<LocationResultData> = withContext(Dispatchers.IO) {
        try {
            if (!isLocationEnabled()) {
                return@withContext Result.failure(Exception("خدمة الموقع (GPS) مغلقة على الهاتف. يرجى تفعيلها والمحاولة مرة أخرى."))
            }

            var loc: Location? = null

            // Try FusedLocationClient high accuracy first
            try {
                val cts = CancellationTokenSource()
                loc = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()
            } catch (e: Exception) {
                Log.w("LocationService", "FusedLocationClient error, trying lastLocation: ${e.message}")
            }

            if (loc == null) {
                try {
                    loc = fusedLocationClient.lastLocation.await()
                } catch (e: Exception) {
                    Log.w("LocationService", "LastLocation error: ${e.message}")
                }
            }

            // Fallback to system LocationManager if Google Play Services location was null
            if (loc == null) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsLoc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                loc = gpsLoc ?: netLoc
            }

            if (loc == null) {
                return@withContext Result.failure(Exception("تعذر الحصول على إحداثيات الموقع الحالي من الجهاز."))
            }

            val lat = loc.latitude
            val lng = loc.longitude
            val address = reverseGeocode(lat, lng)
            val mapUrl = "https://maps.google.com/?q=$lat,$lng"

            Result.success(
                LocationResultData(
                    latitude = lat,
                    longitude = lng,
                    address = address,
                    mapUrl = mapUrl
                )
            )
        } catch (e: SecurityException) {
            Result.failure(Exception("صلاحية الموقع غير ممنوحة. يرجى إعطاء الإذن لتحديد موقعك."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val first = addresses.firstOrNull()
                        val res = if (first != null) {
                            val locality = first.locality ?: first.subAdminArea ?: ""
                            val street = first.thoroughfare ?: first.featureName ?: ""
                            val country = first.countryName ?: ""
                            listOf(street, locality, country).filter { it.isNotBlank() }.joinToString(", ")
                        } else {
                            "الموقع: $lat, $lng"
                        }
                        cont.resume(res)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val first = addresses?.firstOrNull()
                if (first != null) {
                    val locality = first.locality ?: first.subAdminArea ?: ""
                    val street = first.thoroughfare ?: first.featureName ?: ""
                    val country = first.countryName ?: ""
                    listOf(street, locality, country).filter { it.isNotBlank() }.joinToString(", ")
                } else {
                    "الموقع: $lat, $lng"
                }
            }
        } catch (e: Exception) {
            "الموقع: $lat, $lng"
        }
    }
}
