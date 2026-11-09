package com.p0lymatic.flashpoint.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.p0lymatic.flashpoint.core.BurnPhase
import com.p0lymatic.flashpoint.core.PartitionScheme
import com.p0lymatic.flashpoint.core.VentoyPhase
import com.p0lymatic.flashpoint.usb.UsbConnectionState
import com.p0lymatic.flashpoint.usb.VirtualDriveMode


private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FlashPointViewModel) {
    var showAboutScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showAboutScreen) {
        showAboutScreen = false
    }

    if (showAboutScreen) {
        AboutScreen(viewModel = viewModel, onBack = { showAboutScreen = false })
        return
    }
    val doneMetrics by viewModel.doneMetrics.collectAsStateWithLifecycle()
    if (doneMetrics != null) {
        DoneScreen(
            metrics = doneMetrics!!,
            onFinish = { viewModel.resetDoneState() }
        )
        return
    }

    val failedDetails by viewModel.failedDetails.collectAsStateWithLifecycle()
    if (failedDetails != null) {
        FailedScreen(
            details = failedDetails!!,
            onDismiss = { viewModel.resetFailedState() }
        )
        return
    }
    val context = LocalContext.current
    val usbState by viewModel.usbState.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val deviceCapacities by viewModel.deviceCapacities.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val selectedImageName by viewModel.selectedImageName.collectAsStateWithLifecycle()
    val isBurning by viewModel.isBurning.collectAsStateWithLifecycle()
    val burnProgress by viewModel.burnProgress.collectAsStateWithLifecycle()
    val ventoyRelease by viewModel.ventoyRelease.collectAsStateWithLifecycle()
    val ventoyProgress by viewModel.ventoyInstallProgress.collectAsStateWithLifecycle()
    val selectedScheme by viewModel.selectedPartitionScheme.collectAsStateWithLifecycle()
    val selectedArch by viewModel.selectedArchitecture.collectAsStateWithLifecycle()
    val isChangelogExploded by viewModel.isChangelogExploded.collectAsStateWithLifecycle()
    val isCheckingVentoy by viewModel.isCheckingVentoy.collectAsStateWithLifecycle()
    val isDownloadingOffline by viewModel.isDownloadingOfflineVentoy.collectAsStateWithLifecycle()
    val offlineReady by viewModel.offlineVentoyFileExists.collectAsStateWithLifecycle()
    val showCancelDialog by viewModel.showCancelDialog.collectAsStateWithLifecycle()
    val isDemoModeEnabled by viewModel.isDemoModeEnabled.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            var name: String? = null
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
            viewModel.onImageSelected(it, name ?: it.lastPathSegment)
        }
    }

    val isConnected = usbState is UsbConnectionState.Connected
    val isInstalling = ventoyProgress != null &&
            ventoyProgress?.phase != VentoyPhase.COMPLETED &&
            ventoyProgress?.phase != VentoyPhase.FAILED
    val isBusy = isBurning || isInstalling

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FlashPoint",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isDemoModeEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "DEMO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(color = if (isConnected) Color(0xFF00E599) else Color(0xFF757575))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isConnected) "Connected" else "No Drive",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAboutScreen = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Drive Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header row: "Target Drive" label + dedicated Refresh Icon Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Target Drive",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val totalDrives = availableDevices.size + (if (isDemoModeEnabled) 3 else 0)
                            if (totalDrives > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$totalDrives drives",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshUsbDevices() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh devices",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Device Selector Dropdown (if multiple devices or dropdown trigger is clicked)
                    var deviceDropdownExpanded by remember { mutableStateOf(false) }

                    if (availableDevices.isNotEmpty() || isDemoModeEnabled) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { deviceDropdownExpanded = true },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val currentConnected = (usbState as? UsbConnectionState.Connected)
                                    val activeName = if (currentConnected != null) {
                                        if (currentConnected.isVirtual) {
                                            "${currentConnected.vendor} ${currentConnected.model}".trim()
                                        } else {
                                            val dev = currentConnected.device
                                            dev?.let {
                                                listOfNotNull(it.manufacturerName, it.productName)
                                                    .filter { n -> n.isNotBlank() }
                                                    .joinToString(" ")
                                                    .ifEmpty { it.deviceName }
                                            } ?: "Select Device"
                                        }
                                    } else {
                                        availableDevices.firstOrNull()?.let { dev ->
                                            listOfNotNull(dev.manufacturerName, dev.productName)
                                                .filter { n -> n.isNotBlank() }
                                                .joinToString(" ")
                                                .ifEmpty { dev.deviceName }
                                        } ?: "Select Device"
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Selected Device",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = activeName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select target drive",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = deviceDropdownExpanded,
                                onDismissRequest = { deviceDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                val currentConnected = (usbState as? UsbConnectionState.Connected)
                                if (isDemoModeEnabled) {
                                    val virtualDrives = listOf(
                                        Triple(
                                            VirtualDriveMode.SUCCESS,
                                            "Virtual RAM Disk (Success)",
                                            "1.0 GB"
                                        ),
                                        Triple(
                                            VirtualDriveMode.IO_ERROR,
                                            "Virtual RAM Disk (I/O Error @ 60%)",
                                            "1.0 GB"
                                        ),
                                        Triple(
                                            VirtualDriveMode.VERIFY_FAILURE,
                                            "Virtual RAM Disk (Corrupt MBR on Verify)",
                                            "1.0 GB"
                                        )
                                    )
                                    virtualDrives.forEach { (mode, title, cap) ->
                                        val isSelected = currentConnected?.isVirtual == true && currentConnected.virtualMode == mode
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = "$title - $cap",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Emulated RAM Disk (${mode.name})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            trailingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                viewModel.selectVirtualDevice(mode)
                                                deviceDropdownExpanded = false
                                            }
                                        )
                                    }
                                    if (availableDevices.isNotEmpty()) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }

                                availableDevices.forEach { dev ->
                                    val isSelected = currentConnected?.isVirtual != true && currentConnected?.device?.deviceId == dev.deviceId
                                    val devName = listOfNotNull(dev.manufacturerName, dev.productName)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                        .ifEmpty { "USB Storage (${dev.deviceId})" }

                                    val devCapacity = deviceCapacities[dev.deviceId]?.let { formatBytes(it) }
                                        ?: if (isSelected && usbState is UsbConnectionState.Connected) {
                                            formatBytes((usbState as UsbConnectionState.Connected).capacityBytes)
                                        } else {
                                            "Capacity on connect"
                                        }

                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = devName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = devCapacity,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            viewModel.selectDevice(dev)
                                            deviceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    when (val state = usbState) {
                        is UsbConnectionState.Connected -> {
                            val vendor = state.vendor.trim()
                            val model = state.model.trim()
                            val driveTitle = listOf(vendor, model)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifEmpty { "USB Storage Device" }

                            Text(
                                text = driveTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatBytes(state.capacityBytes),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = if (state.isVirtual) "Virtual RAM Disk (Emulated SCSI)" else "USB Mass Storage (SCSI BOT)",
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        is UsbConnectionState.PermissionRequested -> {
                            Text(
                                text = "Permission required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Allow USB host access when prompted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is UsbConnectionState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        is UsbConnectionState.NoDevice -> {
                            Text(
                                text = "No drive connected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Attach USB drive via OTG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Selector
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val pillWidth = maxWidth / 2
                    val pillOffset by animateDpAsState(
                        targetValue = if (selectedTab == 0) 0.dp else pillWidth,
                        animationSpec = spring(
                            dampingRatio = 0.76f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "pillSpringAnimation"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = pillOffset)
                            .width(pillWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable { viewModel.selectedTab.value = 0 }
                        ) {
                            Text(
                                text = "Image",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable { viewModel.selectedTab.value = 1 }
                        ) {
                            Text(
                                text = "Ventoy Setup",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Options Container with Smooth Directional Slide Animations
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        // Moving Image (0) -> Ventoy (1): slide out left, slide in from right
                        (slideInHorizontally(animationSpec = tween(240)) { width -> width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> -width } + fadeOut(animationSpec = tween(240)))
                    } else {
                        // Moving Ventoy (1) -> Image (0): slide out right, slide in from left
                        (slideInHorizontally(animationSpec = tween(240)) { width -> -width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> width } + fadeOut(animationSpec = tween(240)))
                    }.using(SizeTransform(clip = false))
                },
                label = "tabAnimatedContent",
                modifier = Modifier.fillMaxWidth()
            ) { tab ->
                if (tab == 0) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Source Image",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (selectedImageUri != null) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = selectedImageName ?: "Selected Image",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = selectedImageUri.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change File")
                                }
                            } else {
                                Button(
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text(
                                        text = "Select Image File",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Ventoy Version",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ventoyRelease?.let { release ->
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = release.version,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { viewModel.fetchVentoyRelease() },
                                    enabled = !isCheckingVentoy,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    if (isCheckingVentoy) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Checking...")
                                    } else {
                                        Text("Check Latest")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            ventoyRelease?.let { release ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "Ventoy ${release.version}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = formatBytes(release.assetSize),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            when {
                                                offlineReady -> {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Offline ready",
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                isDownloadingOffline -> {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            CircularProgressIndicator(
                                                                strokeWidth = 2.5.dp,
                                                                modifier = Modifier.size(20.dp),
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    FilledTonalIconButton(
                                                        onClick = { viewModel.downloadOfflineVentoy() },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDownward,
                                                            contentDescription = "Download Ventoy offline",
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Explode Changelog Card
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.isChangelogExploded.value = !isChangelogExploded }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Release Changelog",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isChangelogExploded) "Collapse" else "Explode",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (isChangelogExploded) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = release.changelog.ifBlank { "Support for dynamic multiboot, Secure Boot bypass, and live persistence updates." },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Text(
                                text = "Partition Scheme",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val isGpt = selectedScheme == PartitionScheme.GPT
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (isGpt) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { viewModel.selectedPartitionScheme.value = PartitionScheme.GPT }
                                ) {
                                    Text(
                                        text = "GPT (UEFI)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isGpt) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isGpt) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val isMbr = selectedScheme == PartitionScheme.MBR
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (isMbr) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable { viewModel.selectedPartitionScheme.value = PartitionScheme.MBR }
                                ) {
                                    Text(
                                        text = "MBR (Legacy)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isMbr) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isMbr) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Target Architecture",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    com.p0lymatic.flashpoint.core.TargetArchitecture.X86_64 to "x86_64",
                                    com.p0lymatic.flashpoint.core.TargetArchitecture.IA32 to "IA32",
                                    com.p0lymatic.flashpoint.core.TargetArchitecture.ARM64 to "ARM64"
                                ).forEach { (arch, label) ->
                                    val isSel = selectedArch == arch
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                            .clickable { viewModel.selectedArchitecture.value = arch }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Action Button
            val isVirtual = (usbState as? UsbConnectionState.Connected)?.isVirtual == true
            val canStartRaw = isConnected && (selectedImageUri != null || (isDemoModeEnabled && isVirtual)) && !isBusy
            val canStartVentoy = isConnected && (ventoyRelease != null || (isDemoModeEnabled && isVirtual)) && !isBusy

            Button(
                onClick = {
                    if (selectedTab == 0) viewModel.startBurn()
                    else viewModel.installVentoy()
                },
                enabled = if (selectedTab == 0) canStartRaw else canStartVentoy,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (selectedTab == 0) "Write Image" else "Install Ventoy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Modal Progress Sheet
    val showProgress = isBurning || isInstalling
    if (showProgress) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showCancelDialog.value = true },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedTab == 0) {
                    val p = burnProgress
                    val fraction = if (p != null && p.totalBytes > 0) (p.bytesWritten.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f
                    val percent = (fraction * 100).toInt()

                    Text(
                        text = when (p?.phase) {
                            BurnPhase.PREPARING -> "Preparing"
                            BurnPhase.WRITING -> "Writing Image"
                            BurnPhase.VERIFYING -> "Verifying"
                            BurnPhase.COMPLETED -> "Complete"
                            BurnPhase.CANCELLED -> "Cancelled"
                            BurnPhase.FAILED -> "Failed"
                            null -> "Starting"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    p?.let {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "%.1f MB/s".format(it.bytesPerSecond / 1_000_000.0),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ETA ${formatTime(it.etaSeconds)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val vp = ventoyProgress
                    val rawFraction = if (vp != null) {
                        if (vp.progressPercent <= 1.0f) vp.progressPercent else (vp.progressPercent / 100f)
                    } else {
                        0f
                    }
                    val fraction = rawFraction.coerceIn(0f, 1f)
                    val percent = (fraction * 100).toInt()

                    Text(
                        text = when (vp?.phase) {
                            VentoyPhase.FETCHING_RELEASE -> "Checking Release"
                            VentoyPhase.DOWNLOADING -> "Downloading Package"
                            VentoyPhase.PARTITIONING -> "Creating Partitions"
                            VentoyPhase.WRITING_BOOTLOADER -> "Installing Bootloader"
                            VentoyPhase.VERIFYING -> "Verifying Integrity"
                            VentoyPhase.COMPLETED -> "Installed"
                            VentoyPhase.FAILED -> "Failed"
                            null -> "Starting"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { viewModel.showCancelDialog.value = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog.value = false },
            title = { Text("Abort Operation") },
            text = { Text("Interrupting the write process may leave the USB drive in an unbootable state. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedTab == 0) viewModel.cancelBurn()
                        else viewModel.cancelVentoyInstall()
                    }
                ) {
                    Text("Abort", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog.value = false }) {
                    Text("Keep Writing")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = {
                Text(
                    text = "Operation Failed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}
