package com.waqas028.watch_faces_wear_os_sample

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent

class MainViewModelViewModel : ViewModel(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {
    var image by mutableStateOf<Bitmap?>(null)
        private set

    var nodeName by mutableStateOf("")
        private set

    fun onPictureTaken(bitmap: Bitmap?) {
        image = bitmap ?: return
    }

    fun onGetNode(nodeName: String) {
        this.nodeName = nodeName
    }

    override fun onDataChanged(p0: DataEventBuffer) {
        Log.i(TAG, "onDataChanged: $p0")
    }

    override fun onMessageReceived(p0: MessageEvent) {
        Log.i(TAG, "onMessageReceived: $p0")
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        Log.i(TAG, "onCapabilityChanged: $p0")
    }

    companion object{
        const val TAG = "MainViewModelInfo"
    }
}