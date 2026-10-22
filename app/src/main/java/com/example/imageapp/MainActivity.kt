package com.example.imageapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ImageViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(PermissionHelper.hasManageStoragePermission()) }
    val images by viewModel.images.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = PermissionHelper.hasManageStoragePermission()
        if (hasPermission) viewModel.scanImages(context)
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.scanImages(context)
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!hasPermission) {
                Button(onClick = {
                    val intent = PermissionHelper.getManageStorageIntent()
                    if (intent != null) permissionLauncher.launch(intent)
                }) {
                    Text("Grant File Access")
                }
            } else if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    items(images) { image ->
                        AsyncImage(
                            model = image.file,
                            contentDescription = image.name,
                            modifier = Modifier.size(120.dp).padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}
