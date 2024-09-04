package com.waqas028.watch_faces_wear_os_sample

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.AvailabilityException
import com.google.android.gms.common.api.GoogleApi
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.waqas028.watch_faces_wear_os_sample.ui.theme.WatchFacesWearOSSampleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity(){
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val isCameraSupported by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    private val mainViewModel by viewModels<MainViewModelViewModel>()
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            mainViewModel.onPictureTaken(it)
        }
    }
    private var nodeClient: NodeClient? = null

     @RequiresApi(Build.VERSION_CODES.O)
     override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchFacesWearOSSampleTheme {
                var apiAvailable by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    apiAvailable = isAvailable(capabilityClient)
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        nodeName = mainViewModel.nodeName,
                        image = mainViewModel.image,
                        isCameraSupported = isCameraSupported,
                        apiAvailable = apiAvailable,
                        onTakePhotoClick = ::takePhoto,
                        onSendPhotoClick = { mainViewModel.image?.let { sendMessage(it) } },
                        onStartWearableActivityClick = ::startWearableActivity
                    )
                }
            }
        }

         nodeClient = Wearable.getNodeClient(this)
         nodeClient?.connectedNodes?.addOnCompleteListener {task->
             if (task.isSuccessful) {
                 val nodes: List<Node> = task.result
                 for (node in nodes) {
                     val nodeName = node.displayName
                     mainViewModel.onGetNode(nodeName)
                 }
             } else {
                 mainViewModel.onGetNode("Failed to get connected nodes")
             }
         }
    }

     companion object {
         private const val TAG = "MainActivityInfo"
         private const val START_ACTIVITY_PATH = "/start-activity"
         private const val IMAGE_BITMAP = "/image-bitmap"
         private const val IMAGE_KEY = "photo"
         private const val TIME_KEY = "time"
         private const val CAMERA_CAPABILITY = "camera"
         private const val WEAR_CAPABILITY = "wear"
     }

     override fun onResume() {
         super.onResume()

         dataClient.addListener(mainViewModel)
         messageClient.addListener(mainViewModel)
         capabilityClient.addListener(
             mainViewModel,
             Uri.parse("wear://"),
             CapabilityClient.FILTER_REACHABLE
         )

         if (isCameraSupported) {
             lifecycleScope.launch {
                 try {
                     capabilityClient.addLocalCapability(CAMERA_CAPABILITY).await()
                 } catch (cancellationException: CancellationException) {
                     throw cancellationException
                 } catch (exception: Exception) {
                     Log.e(TAG, "Could not add capability: $exception")
                 }
             }
         }
     }

     override fun onPause() {
         super.onPause()
         dataClient.removeListener(mainViewModel)
         messageClient.removeListener(mainViewModel)
         capabilityClient.removeListener(mainViewModel)
         lifecycleScope.launch {
             withContext(NonCancellable) {
                 try {
                     capabilityClient.removeLocalCapability(CAMERA_CAPABILITY).await()
                 } catch (exception: Exception) {
                     Log.e(TAG, "Could not remove capability: $exception")
                 }
             }
         }
     }

     private suspend fun isAvailable(api: GoogleApi<*>): Boolean {
         return try {
             GoogleApiAvailability.getInstance()
                 .checkApiAvailability(api)
                 .await()

             true
         } catch (e: AvailabilityException) {
             Log.d(TAG, "${api.javaClass.simpleName} API is not available in this device.")
             false
         }
     }

     private fun takePhoto() {
         if (!isCameraSupported) return
         takePhotoLauncher.launch(null)
     }

     @RequiresApi(Build.VERSION_CODES.O)
     private fun sendPhoto(image: Bitmap) {
         lifecycleScope.launch {
             try {
                 val imageAsset = image.toAsset()
                 val request = PutDataMapRequest.create(IMAGE_BITMAP).apply {
                     dataMap.putAsset(IMAGE_KEY, imageAsset)
                     dataMap.putLong(TIME_KEY, Instant.now().epochSecond)
                 }
                     .asPutDataRequest()
                     .setUrgent()

                 val result = dataClient.putDataItem(request).await()

                 Log.d(TAG, "DataItem saved: $result")
             } catch (cancellationException: CancellationException) {
                 throw cancellationException
             } catch (exception: Exception) {
                 Log.d(TAG, "Saving DataItem failed: $exception")
             }
         }
     }

     private fun sendMessage(imageBitmap: Bitmap){
         lifecycleScope.launch {
             try {
                 val imageByteArray = convertImageToByteArray(imageBitmap)
                 val nodes = capabilityClient
                     .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                     .await()
                     .nodes
                 nodes.map { node ->
                     async {
                         messageClient.sendMessage(node.id, IMAGE_BITMAP, imageByteArray)
                             .addOnCompleteListener {
                                 if (it.isSuccessful) {
                                     Log.d(TAG, "Image sent successfully. $imageBitmap   //  $imageByteArray")
                                 } else {
                                     Log.e(TAG, "Failed to send image.")
                                 }
                             }
                             .addOnFailureListener {
                                 Log.e(TAG, "Failed: ${it.message}")
                             }
                             .await()
                     }
                 }.awaitAll()

                 Log.d(TAG, "Send Message requests sent successfully")
             } catch (cancellationException: CancellationException) {
                 throw cancellationException
             } catch (exception: Exception) {
                 Log.d(TAG, "Send Message failed: $exception")
             }
         }
     }

    private fun startWearableActivity() {
        lifecycleScope.launch {
            try {
                val nodes = capabilityClient
                    .getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                nodes.map { node ->
                    async {
                        messageClient.sendMessage(node.id, START_ACTIVITY_PATH, byteArrayOf())
                            .await()
                    }
                }.awaitAll()

                Log.d(TAG, "Starting activity requests sent successfully")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(TAG, "Starting activity failed: $exception")
            }
        }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    nodeName: String,
    image: Bitmap?,
    isCameraSupported: Boolean,
    apiAvailable: Boolean,
    onTakePhotoClick: () -> Unit,
    onSendPhotoClick: () -> Unit,
    onStartWearableActivityClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        if (!apiAvailable) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.wearable_api_unavailable),
                color = Color.Red,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        if(image == null)Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = "",
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.CenterHorizontally)
        )else Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "",
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row {
            Text(
                text = "Connected Watch: ",
                fontWeight = FontWeight.Medium
            )
            Text(
                text = nodeName,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ){
            Button(
                enabled = isCameraSupported,
                onClick = onTakePhotoClick
            ){
                Text(text = "Take Picture")
            }
            Button(
                enabled = nodeName.isNotEmpty() && image != null,
                onClick = onSendPhotoClick
            ){
                Text(text = "Send Pic to Wear OS")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            enabled = nodeName.isNotEmpty() && image != null,
            onClick = onStartWearableActivityClick
        ){
            Text(text = "Set Image as a Current Watch Faces")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WatchFacesWearOSSampleTheme {
        HomeScreen(
            nodeName = "Watch",
            image = null,
            isCameraSupported = true,
            onTakePhotoClick = {},
            onSendPhotoClick = {},
            onStartWearableActivityClick = {},
            apiAvailable = true
        )
    }
}

suspend fun Bitmap.toAsset(): Asset =
    withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { byteStream ->
            compress(Bitmap.CompressFormat.PNG, 100, byteStream)
            Asset.createFromBytes(byteStream.toByteArray())
        }
    }

fun convertImageToByteArray(image: Bitmap): ByteArray {
    val byteStream = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
    return byteStream.toByteArray()
}