# Watch Face Wear OS Sample
This repository contains an Android and Wear OS app that allows users to send data or images from a mobile device to a smartwatch and set that image as the current watch face.

## Demo


https://github.com/user-attachments/assets/845b2848-5dc3-45dd-b742-af9b0dfd983f



## Features

- **Send Data from Mobile to Watch**: Easily transfer data, including images, from your Android smartphone to your Wear OS watch.
- **Dynamic Watch Face**: The image sent from the mobile device can be set as the current watch face on the Wear OS watch.
- **Seamless Integration**: The app is designed to work smoothly between Android mobile devices and Wear OS watches.

### How It Works

1. **Select Your Watch Face**:
   - On your Wear OS watch, open the watch face selector.
   - Choose the Wear OS watch face that you want to use. This watch face will be the one that can be dynamically updated from your mobile device.

2. **Take a Photo on Your Mobile Device**:
   - Open the app on your Android device.
   - Select the option to take a new photo or choose an existing image from your gallery.

3. **Send the Image to Your Watch**:
   - The app will send the selected image to your Wear OS watch using the Data Layer API.
   - Once received, the image will automatically be set as the current watch face on your Wear OS watch.

### Data Layer API
- **Data Layer API** is used to send and sync data between the mobile device and the Wear OS watch. This includes sending images or other data that will be used to update the watch face.

```kotlin
val imageAsset = image.toAsset()
                 val request = PutDataMapRequest.create(IMAGE_BITMAP).apply {
                     dataMap.putAsset(IMAGE_KEY, imageAsset)
                     dataMap.putLong(TIME_KEY, Instant.now().epochSecond)
                 }
                     .asPutDataRequest()
                     .setUrgent()

                 val result = dataClient.putDataItem(request).await()
```

### MessageClient API
- **MessageClient API** is used to send short messages or commands from the mobile device to the watch, enabling quick updates or requests for data.

```kotlin
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
```

### Wearable API
- **Wearable API** is utilized to find connected nodes (devices) that are part of the Wear OS network. This helps in identifying the connected watch to which the data or image should be sent.

```kotlin
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
```

### Prerequisites

- Android Studio
- Android SDK
- A Wear OS-compatible smartwatch
- A connected Android smartphone

### Installation

1. **Clone the Repository**
   ```bash
   git clone https://github.com/waqas028/Watch-Face-Wear-OS-Sample.git
   cd Watch-Face-Wear-OS-Sample

## Contributing

Contributions are welcome! Please follow these steps:

- Fork the repository.
- Create a new branch (git checkout -b feature-branch).
- Commit your changes (git commit -m 'Add some feature').
- Push to the branch (git push origin feature-branch).
- Open a pull request.

## Contact

For any inquiries, please contact waqaswaseem679@gmail.com.
