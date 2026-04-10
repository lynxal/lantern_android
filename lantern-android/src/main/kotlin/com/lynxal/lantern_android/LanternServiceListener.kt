package com.lynxal.lantern_android

import com.lynxal.lantern_android.model.ServiceInfo

interface LanternServiceListener {
    fun onServiceFound(service: ServiceInfo)
    fun onServiceLost(instanceName: String)
    fun onError(cause: Exception)
}
