package com.example.pathreport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Address
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.Executors

// Legacy geocoder calls may ignore interruption. A single daemon worker bounds outstanding work.
private val legacyGeocoderWorker = Executors.newSingleThreadExecutor { task -> Thread(task, "address-lookup").apply { isDaemon = true } }

data class AddressChoice(val address: String, val lat: Double, val lon: Double)
class EntryFailure(val guidance: String) : Exception()

@Suppress("DEPRECATION")
suspend fun searchAddress(context: Context, addressOnly: String): List<AddressChoice> = withContext(Dispatchers.IO) {
    val network = context.getSystemService(ConnectivityManager::class.java)
    val caps = network.getNetworkCapabilities(network.activeNetwork)
    if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true)
        throw EntryFailure("인터넷 연결을 확인한 뒤 다시 검색해 주세요. 좌표 직접 입력도 가능합니다.")
    if (!Geocoder.isPresent()) throw EntryFailure("이 기기는 주소 검색을 지원하지 않습니다. 현재 위치 또는 좌표 직접 입력을 이용해 주세요.")
    try {
        // Only the address is supplied to the geocoder, never a person/place name or stored dataset.
        val geocoder = Geocoder(context, Locale.KOREA)
        val addresses: List<Address> = suspendCancellableCoroutine { continuation ->
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocationName(addressOnly, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses)
                    }
                    override fun onError(errorMessage: String?) {
                        // Provider error text is deliberately neither displayed nor logged.
                        if (continuation.isActive) continuation.resumeWithException(java.io.IOException())
                    }
                })
            } else {
                val future = legacyGeocoderWorker.submit {
                    try {
                        val found = geocoder.getFromLocationName(addressOnly, 5).orEmpty()
                        if (continuation.isActive) continuation.resume(found)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        }
        addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { AddressChoice(it.getAddressLine(0) ?: addressOnly, it.latitude, it.longitude) }.distinct()
    } catch (e: java.io.IOException) {
        throw EntryFailure("주소 검색 서비스에 연결하지 못했습니다. 인터넷을 확인하거나 좌표를 직접 입력해 주세요.")
    } catch (e: IllegalArgumentException) { throw EntryFailure("주소를 확인해 주세요.") }
}

suspend fun currentCoordinates(context: Context): Location = withTimeout(25_000) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        throw EntryFailure("정확한 위치 권한을 허용해 주세요. 좌표를 직접 입력할 수도 있습니다.")
    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) throw EntryFailure("기기의 GPS 위치 설정을 켠 뒤 다시 시도해 주세요.")
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.cancel(EntryFailure("GPS가 꺼졌습니다. 다시 켜 주세요."))
                }
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper()) }
            catch (e: SecurityException) { continuation.cancel(EntryFailure("위치 권한이 필요합니다.")) }
        }
    }
}
