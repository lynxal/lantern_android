package com.lynxal.lantern.android.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.lynxal.lantern_android.LanternConfig
import com.lynxal.lantern_android.LanternDiscovery
import com.lynxal.lantern_android.LanternServiceListener
import com.lynxal.lantern_android.model.ServiceInfo

/**
 * Minimal Lantern sample: discover network printers on the local network.
 *
 * All output goes to logcat. To watch:
 *
 *     adb logcat -s LanternSample
 */
class MainActivity : Activity() {

    private lateinit var lantern: LanternDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Starting printer discovery on $PRINTER_SERVICE_TYPE")

        lantern = LanternDiscovery(
            context = this,
            config = LanternConfig(
                serviceType = PRINTER_SERVICE_TYPE,
                queryIntervalMs = 10_000L, // re-query every 10s in case answers were missed
            ),
            listener = object : LanternServiceListener {
                override fun onServiceFound(service: ServiceInfo) {
                    Log.i(TAG, "Printer found: ${service.instanceName}")
                    Log.i(TAG, "  host: ${service.host}:${service.port}")
                    if (service.txtRecords.isNotEmpty()) {
                        Log.i(TAG, "  TXT:  ${service.txtRecords}")
                    }
                }

                override fun onServiceLost(instanceName: String) {
                    Log.i(TAG, "Printer lost: $instanceName")
                }

                override fun onError(cause: Exception) {
                    Log.e(TAG, "Discovery error", cause)
                }
            },
        )
        lantern.start()
    }

    override fun onDestroy() {
        Log.i(TAG, "Stopping printer discovery")
        lantern.stop()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LanternSample"

        // _ipp._tcp.local. — Internet Printing Protocol (RFC 8011, AirPrint).
        // Other common printer service types you might want to try:
        //   _ipps._tcp.local.            IPP over TLS
        //   _printer._tcp.local.         LPR/LPD (RFC 1179)
        //   _pdl-datastream._tcp.local.  Raw PDL on port 9100
        const val PRINTER_SERVICE_TYPE = "_ipp._tcp.local."
    }
}
