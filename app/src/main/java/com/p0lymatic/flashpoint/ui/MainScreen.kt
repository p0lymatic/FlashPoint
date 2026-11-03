package com.p0lymatic.flashpoint.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p0lymatic.flashpoint.core.BurnPhase
import com.p0lymatic.flashpoint.core.PartitionScheme
import com.p0lymatic.flashpoint.core.VentoyPhase
import com.p0lymatic.flashpoint.usb.UsbConnectionState

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FlashPointViewModel) {
    val usbState by viewModel.usbState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val showCancelDialog by viewModel.showCancelDialog.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FlashPoint ⚡",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    val isConnected = usbState is UsbConnectionState.Connected
                    val dotColor = if (isConnected) Color(0xFF00E676) else Color(0xFFFF1744)
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = dotColor)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectedTab.value = 0 },
                    text = { Text("ISO Burner") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectedTab.value = 1 },
                    text = { Text("Ventoy Setup") },
                )
            }

            when (selectedTab) {
                0 -> IsoBurnerTab(viewModel, usbState)
                1 -> VentoySetupTab(viewModel, usbState)
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog.value = false },
            title = { Text("Cancel Burn?") },
            text = { Text("Are you sure you want to cancel the burn operation? The USB drive may be left in an unusable state.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelBurn() }) {
                    Text("Cancel Burn", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog.value = false }) {
                    Text("Continue")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IsoBurnerTab(
    viewModel: FlashPointViewModel,
    usbState: UsbConnectionState,
) {
    val context = LocalContext.current
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val selectedImageName by viewModel.selectedImageName.collectAsStateWithLifecycle()
    val isBurning by viewModel.isBurning.collectAsStateWithLifecycle()
    val burnProgress by viewModel.burnProgress.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
            viewModel.onImageSelected(uri, displayName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = { launcher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBurning,
        ) {
            Text("Select ISO Image")
        }

        if (selectedImageName != null) {
            Text(
                text = "Selected: ${selectedImageName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        when (usbState) {
            is UsbConnectionState.Connected -> {
                val connected = usbState as UsbConnectionState.Connected
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text(
                        text = "USB Device Connected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vendor: ${connected.vendor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Model: ${connected.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Capacity: ${formatBytes(connected.capacityBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            is UsbConnectionState.NoDevice -> {
                Text(
                    text = "No USB drive connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UsbConnectionState.PermissionRequested -> {
                Text(
                    text = "Requesting USB permission…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            is UsbConnectionState.Error -> {
                val err = usbState as UsbConnectionState.Error
                Text(
                    text = "USB Error: ${err.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Button(
            onClick = { viewModel.startBurn() },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedImageUri != null
                    && usbState is UsbConnectionState.Connected
                    && !isBurning,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Start Burn")
        }
    }

    if (isBurning && burnProgress != null) {
        val progress = burnProgress!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { /* prevent dismissing while burning */ },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Burning Image…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                val fraction = if (progress.totalBytes > 0) {
                    (progress.bytesWritten.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Text(
                    text = "%.1f MB/s".format(progress.bytesPerSecond / 1_000_000.0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val elapsedSeconds = if (progress.bytesPerSecond > 0) {
                    (progress.bytesWritten / progress.bytesPerSecond).toLong()
                } else 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Elapsed: ${formatTime(elapsedSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "ETA: ${formatTime(progress.etaSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = "${formatBytes(progress.bytesWritten)} / ${formatBytes(progress.totalBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val sha256Display = if (progress.sha256Hex.length > 16) {
                    progress.sha256Hex.substring(0, 16) + "…"
                } else {
                    progress.sha256Hex
                }
                Text(
                    text = "SHA256: $sha256Display",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { viewModel.showCancelDialog.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun VentoySetupTab(
    viewModel: FlashPointViewModel,
    usbState: UsbConnectionState,
) {
    val ventoyRelease by viewModel.ventoyRelease.collectAsStateWithLifecycle()
    val ventoyInstallProgress by viewModel.ventoyInstallProgress.collectAsStateWithLifecycle()
    val selectedScheme by viewModel.selectedPartitionScheme.collectAsStateWithLifecycle()

    val isInstalling = ventoyInstallProgress != null
            && ventoyInstallProgress!!.phase != VentoyPhase.COMPLETED
            && ventoyInstallProgress!!.phase != VentoyPhase.FAILED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = { viewModel.fetchVentoyRelease() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isInstalling,
        ) {
            Text("Check Latest Version")
        }

        if (ventoyRelease != null) {
            val release = ventoyRelease!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Text(
                    text = "Ventoy ${release.version}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Published: ${release.publishedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Download size: ${formatBytes(release.assetSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Text(
            text = "Partition Scheme",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedScheme == PartitionScheme.MBR,
                onClick = { viewModel.selectedPartitionScheme.value = PartitionScheme.MBR },
                enabled = !isInstalling,
            )
            Text(
                text = "MBR",
                modifier = Modifier.padding(end = 24.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            RadioButton(
                selected = selectedScheme == PartitionScheme.GPT,
                onClick = { viewModel.selectedPartitionScheme.value = PartitionScheme.GPT },
                enabled = !isInstalling,
            )
            Text(
                text = "GPT",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Button(
            onClick = { viewModel.installVentoy() },
            modifier = Modifier.fillMaxWidth(),
            enabled = ventoyRelease != null
                    && usbState is UsbConnectionState.Connected
                    && !isInstalling,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Install Ventoy")
        }

        if (ventoyInstallProgress != null) {
            val progress = ventoyInstallProgress!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (progress.phase) {
                        VentoyPhase.COMPLETED -> MaterialTheme.colorScheme.primary
                        VentoyPhase.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )

                if (progress.phase != VentoyPhase.COMPLETED && progress.phase != VentoyPhase.FAILED) {
                    LinearProgressIndicator(
                        progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    OutlinedButton(
                        onClick = { viewModel.cancelVentoyInstall() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
