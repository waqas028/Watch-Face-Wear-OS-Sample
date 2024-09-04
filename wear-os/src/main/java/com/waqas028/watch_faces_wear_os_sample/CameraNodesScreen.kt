package com.waqas028.watch_faces_wear_os_sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CameraNodesScreen(
    viewModel: NodesViewModel = viewModel(factory = NodesViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NodesScreen(nodes = state.cameraNodes, modifier = Modifier)
}
