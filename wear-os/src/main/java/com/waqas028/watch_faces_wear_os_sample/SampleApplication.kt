package com.waqas028.watch_faces_wear_os_sample

import android.app.Application
import com.google.android.gms.wearable.Wearable

class SampleApplication : Application() {

    val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
}
