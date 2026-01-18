package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkPermission(context)) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkPermission(context)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasPermission) {
            ModelViewer()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Permission required to read GLB file")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(String.format("package:%s", context.packageName))
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

fun checkPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun getStackTrace(t: Throwable): String {
    val sw = StringWriter()
    t.printStackTrace(PrintWriter(sw))
    return sw.toString()
}

@Composable
fun ModelViewer() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    
    val childNodes = rememberNodes()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var stackTraceStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            Log.d("ModelViewer", "Attempting to load from assets: test2.glb")
            
            // Загрузка модели из assets
            val modelInstance = modelLoader.createModelInstance(
                assetFileLocation = "test2.glb"
            )

            if (modelInstance != null) {
                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 1.0f
                )
                childNodes.add(modelNode)
                Log.d("ModelViewer", "Asset loaded successfully")
            } else {
                errorMsg = "Failed to load asset: returned null"
            }
        } catch (e: Exception) {
            Log.e("ModelViewer", "Error loading asset", e)
            errorMsg = "Exception: ${e.message}"
            stackTraceStr = getStackTrace(e)
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            view = rememberView(engine),
            renderer = rememberRenderer(engine),
            scene = rememberScene(engine),
            mainLightNode = rememberMainLightNode(engine),
            cameraNode = rememberCameraNode(engine).apply {
                position = Position(z = 4.0f)
            },
            childNodes = childNodes
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (errorMsg != null) {
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "ERROR", color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMsg!!, color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                if (stackTraceStr.isNotEmpty()) {
                    Text(text = "Stack Trace:", color = Color.Black)
                    Text(
                        text = stackTraceStr,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}