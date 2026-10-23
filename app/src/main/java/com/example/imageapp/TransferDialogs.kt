package com.example.imageapp

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.FileProvider
import com.example.imageapp.model.FileItem
import com.example.imageapp.model.TransferStatus
import com.example.imageapp.model.TransferTransport
import java.io.File

@Composable
fun LeftBreakoutSharingItem(
    onSelectTransport: (TransferTransport) -> Unit,
    onCloseParent: () -> Unit
) {
    var showLeftBreakout by remember { mutableStateOf(false) }

    Box {
        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Left Breakout",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sharing", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            onClick = { showLeftBreakout = !showLeftBreakout }
        )

        if (showLeftBreakout) {
            val leftPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        val x = (anchorBounds.left - popupContentSize.width - 8).coerceAtLeast(8)
                        val y = anchorBounds.top.coerceAtLeast(16)
                        return IntOffset(x, y)
                    }
                }
            }

            Popup(
                popupPositionProvider = leftPositionProvider,
                onDismissRequest = { showLeftBreakout = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "Sharing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        DropdownMenuItem(
                            text = { Text("NFC") },
                            leadingIcon = { Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showLeftBreakout = false
                                onCloseParent()
                                onSelectTransport(TransferTransport.NFC)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bluetooth") },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showLeftBreakout = false
                                onCloseParent()
                                onSelectTransport(TransferTransport.BLUETOOTH_ONLY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bluetooth + Wifi") },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showLeftBreakout = false
                                onCloseParent()
                                onSelectTransport(TransferTransport.BLUETOOTH_AND_WIFI)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShareOptionsDialog(
    itemsToShare: List<FileItem>,
    onDismiss: () -> Unit,
    onStartP2PShare: (TransferTransport) -> Unit,
    onSystemShare: () -> Unit
) {
    var selectedTransport by remember { mutableStateOf(TransferTransport.BLUETOOTH_AND_WIFI) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Share Files", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sharing ${itemsToShare.size} item(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Peer-to-Peer Transfer Method:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTransport = TransferTransport.NFC },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTransport == TransferTransport.NFC) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTransport == TransferTransport.NFC, onClick = { selectedTransport = TransferTransport.NFC })
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("NFC Sharing", fontWeight = FontWeight.SemiBold)
                            Text("Tap phone to start transfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTransport = TransferTransport.BLUETOOTH_ONLY },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTransport == TransferTransport.BLUETOOTH_ONLY) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTransport == TransferTransport.BLUETOOTH_ONLY, onClick = { selectedTransport = TransferTransport.BLUETOOTH_ONLY })
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Bluetooth Only", fontWeight = FontWeight.SemiBold)
                            Text("Standard Bluetooth transfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTransport = TransferTransport.BLUETOOTH_AND_WIFI },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTransport == TransferTransport.BLUETOOTH_AND_WIFI) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTransport == TransferTransport.BLUETOOTH_AND_WIFI, onClick = { selectedTransport = TransferTransport.BLUETOOTH_AND_WIFI })
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Bluetooth + Wi-Fi", fontWeight = FontWeight.SemiBold)
                            Text("High speed peer-to-peer connection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSystemShare()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("More Share Options (System)")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onStartP2PShare(selectedTransport)
            }) {
                Text("Start Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ActiveTransferDialog(
    onDismiss: () -> Unit
) {
    val state by P2PTransferManager.transferState.collectAsState()

    AlertDialog(
        onDismissRequest = {
            if (state.status == TransferStatus.COMPLETED || state.status == TransferStatus.ERROR) {
                P2PTransferManager.resetState()
                onDismiss()
            }
        },
        title = {
            Text(
                text = when (state.status) {
                    TransferStatus.WAITING_FOR_NFC -> "Touch Phones (NFC)"
                    TransferStatus.DISCOVERING -> "Discovering..."
                    TransferStatus.CONNECTING -> "Connecting..."
                    TransferStatus.TRANSFERRING -> if (state.isReceiver) "Receiving Files" else "Sending Files"
                    TransferStatus.UPGRADING_TO_WIFI -> "Speeding Up..."
                    TransferStatus.COMPLETED -> "Transfer Complete!"
                    TransferStatus.ERROR -> "Transfer Failed"
                    else -> "Transfer"
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.status == TransferStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                if (state.status == TransferStatus.TRANSFERRING || state.status == TransferStatus.UPGRADING_TO_WIFI) {
                    if (state.currentFileName.isNotEmpty()) {
                        Text(
                            text = "File ${state.currentFileIndex}/${state.totalFiles}: ${state.currentFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${StorageScanner.formatFileSize(state.transferredBytes)} / ${StorageScanner.formatFileSize(state.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${StorageScanner.formatFileSize(state.speedBytesPerSec)}/s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.isWifiActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isWifiActive) Icons.Default.Wifi else Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.isWifiActive) "High-Speed Wi-Fi Direct" else "Bluetooth Mode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (!state.isWifiActive && !state.isReceiver) {
                        Button(
                            onClick = { P2PTransferManager.requestSpeedUpToWifi() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("⚡ Speed Up (Bluetooth + Wi-Fi)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.status == TransferStatus.COMPLETED || state.status == TransferStatus.ERROR) {
                Button(onClick = {
                    P2PTransferManager.resetState()
                    onDismiss()
                }) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (state.status != TransferStatus.COMPLETED && state.status != TransferStatus.ERROR) {
                TextButton(onClick = {
                    P2PTransferManager.cancelTransfer()
                    onDismiss()
                }) {
                    Text("Cancel Transfer")
                }
            }
        }
    )
}

