package com.waqas028.watch_faces_wear_os_sample

import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import com.waqas028.watch_faces_wear_os_sample.utils.createComplicationSlotManager
import com.waqas028.watch_faces_wear_os_sample.utils.createUserStyleSchema
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.waqas028.watch_faces_wear_os_sample.utils.Constant

/**
 * Handles much of the boilerplate needed to implement a watch face (minus rendering code; see
 * [AnalogWatchCanvasRenderer]) including the complications and settings (styles user can change on
 * the watch face).
 */
class AnalogWatchFaceService : WatchFaceService(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    private lateinit var renderer: AnalogWatchCanvasRenderer

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()

        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
    }

    // Used by Watch Face APIs to construct user setting options and repository.
    override fun createUserStyleSchema(): UserStyleSchema =
        createUserStyleSchema(context = applicationContext)

    // Creates all complication user settings and adds them to the existing user settings
    // repository.
    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager = createComplicationSlotManager(
        context = applicationContext,
        currentUserStyleRepository = currentUserStyleRepository
    )

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        Log.d(TAG, "createWatchFace()")
        // Creates class that renders the watch face.
        renderer = AnalogWatchCanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE
        )

        // Creates the watch face.
        return WatchFace(
            watchFaceType = WatchFaceType.ANALOG,
            renderer = renderer
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
    }

    companion object {
        const val TAG = "AnalogWatchFaceServiceInfo"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.i(TAG, "onDataChanged: $dataEvents")
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path == "/image_path") {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val imageBytes = dataMap.getByteArray("image_key")
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes!!.size)
                    Log.i(TAG, "onDataChanged: $bitmap")
                    //renderer.updateWatchFaces(bitmap)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.i(TAG, "onMessageReceived: ${messageEvent.path}")
        if (messageEvent.path == Constant.IMAGE_BITMAP) {
            // Handle the command, update the UI, or change settings
            val imageBytes = messageEvent.data
            val backgroundBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val command = String(messageEvent.data)
            renderer.updateWatchFaces(backgroundBitmap)
            // Apply changes based on the command
            Log.i(TAG, "onMessageReceived: $command   $backgroundBitmap")
        }
    }
}