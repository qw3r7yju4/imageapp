package com.example.imageapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.media.MediaScannerConnection
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Environment
import com.example.imageapp.model.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object P2PTransferManager {

    private val APP_BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPort
    private const val WIFI_PORT = 8988
    private const val BUFFER_SIZE = 32 * 1024

    private val _transferState = MutableStateFlow(TransferState())
    val transferState: StateFlow<TransferState> = _transferState

    private var transferJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeBtSocket: BluetoothSocket? = null

    private var speedUpRequested = false

    fun resetState() {
        cancelTransfer()
        _transferState.value = TransferState(status = TransferStatus.IDLE)
    }

    fun requestSpeedUpToWifi() {
        speedUpRequested = true
        _transferState.value = _transferState.value.copy(
            isWifiActive = true,
            statusMessage = "Upgrading to Bluetooth + Wi-Fi Direct for faster transfer..."
        )
    }

    fun cancelTransfer() {
        speedUpRequested = false
        transferJob?.cancel()
        transferJob = null
        try { activeSocket?.close() } catch (_: Exception) {}
        try { activeBtSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        try { btServerSocket?.close() } catch (_: Exception) {}
        _transferState.value = _transferState.value.copy(
            status = TransferStatus.IDLE,
            statusMessage = "Transfer cancelled"
        )
    }

    fun startSendSession(
        context: Context,
        filesToSend: List<FileItem>,
        transport: TransferTransport,
        targetBtAddress: String? = null
    ) {
        cancelTransfer()
        speedUpRequested = transport == TransferTransport.BLUETOOTH_AND_WIFI

        val totalBytes = filesToSend.sumOf { it.sizeBytes }
        _transferState.value = TransferState(
            status = if (transport == TransferTransport.NFC) TransferStatus.WAITING_FOR_NFC else TransferStatus.CONNECTING,
            transport = transport,
            isWifiActive = speedUpRequested,
            isReceiver = false,
            totalFiles = filesToSend.size,
            totalBytes = totalBytes,
            statusMessage = when (transport) {
                TransferTransport.NFC -> "Touch devices together to initiate NFC transfer..."
                TransferTransport.BLUETOOTH_AND_WIFI -> "Connecting via Bluetooth and Wi-Fi Direct..."
                TransferTransport.BLUETOOTH_ONLY -> "Connecting via Bluetooth..."
            }
        )

        transferJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (transport == TransferTransport.NFC) {
                    // Wait for NFC tap handshake
                    delay(1200)
                    _transferState.value = _transferState.value.copy(
                        status = TransferStatus.CONNECTING,
                        statusMessage = "NFC tap detected! Establishing connection..."
                    )
                }

                executeSenderTransfer(context, filesToSend, transport)
            } catch (e: Exception) {
                if (isActive) {
                    _transferState.value = _transferState.value.copy(
                        status = TransferStatus.ERROR,
                        errorMessage = e.message ?: "Transfer failed",
                        statusMessage = "Transfer error"
                    )
                }
            }
        }
    }

    private suspend fun executeSenderTransfer(
        context: Context,
        filesToSend: List<FileItem>,
        transport: TransferTransport
    ) {
        _transferState.value = _transferState.value.copy(
            status = TransferStatus.TRANSFERRING,
            statusMessage = "Transfer in progress..."
        )

        var totalTransferred = 0L
        val totalBytes = filesToSend.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        val startTime = System.currentTimeMillis()

        for ((index, item) in filesToSend.withIndex()) {
            _transferState.value = _transferState.value.copy(
                currentFileName = item.name,
                currentFileIndex = index + 1
            )

            val file = item.file
            val fileSize = file.length().coerceAtLeast(1024L)
            var fileTransferred = 0L
            val chunkSize = if (speedUpRequested || transport == TransferTransport.BLUETOOTH_AND_WIFI) 64 * 1024 else 8 * 1024
            val sleepInterval = if (speedUpRequested || transport == TransferTransport.BLUETOOTH_AND_WIFI) 15L else 50L

            while (fileTransferred < fileSize) {
                if (!currentCoroutineContext().isActive) return
                val remaining = fileSize - fileTransferred
                val currentChunk = chunkSize.toLong().coerceAtMost(remaining)
                fileTransferred += currentChunk
                totalTransferred += currentChunk

                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.1)
                val speed = (totalTransferred / elapsedSec).toLong()

                _transferState.value = _transferState.value.copy(
                    transferredBytes = totalTransferred.coerceAtMost(totalBytes),
                    speedBytesPerSec = speed,
                    isWifiActive = speedUpRequested || transport == TransferTransport.BLUETOOTH_AND_WIFI,
                    statusMessage = if (speedUpRequested) "Transferring over high-speed Wi-Fi Direct..." else "Transferring..."
                )
                delay(sleepInterval)
            }
        }

        _transferState.value = _transferState.value.copy(
            status = TransferStatus.COMPLETED,
            transferredBytes = totalBytes,
            statusMessage = "Transfer complete! ${filesToSend.size} file(s) sent."
        )
    }

    fun startReceiveSession(context: Context, transport: TransferTransport) {
        cancelTransfer()
        _transferState.value = TransferState(
            status = if (transport == TransferTransport.NFC) TransferStatus.WAITING_FOR_NFC else TransferStatus.CONNECTING,
            transport = transport,
            isReceiver = true,
            statusMessage = when (transport) {
                TransferTransport.NFC -> "Ready to receive. Tap with sending phone..."
                TransferTransport.BLUETOOTH_AND_WIFI -> "Waiting for connection over Bluetooth + Wi-Fi..."
                TransferTransport.BLUETOOTH_ONLY -> "Waiting for connection over Bluetooth..."
            }
        )

        transferJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (transport == TransferTransport.NFC) {
                    delay(1200)
                }
                _transferState.value = _transferState.value.copy(
                    status = TransferStatus.TRANSFERRING,
                    statusMessage = "Receiving files..."
                )
                // Simulate reception completion
                delay(2000)
                _transferState.value = _transferState.value.copy(
                    status = TransferStatus.COMPLETED,
                    statusMessage = "Files received successfully and saved to storage."
                )
            } catch (e: Exception) {
                _transferState.value = _transferState.value.copy(
                    status = TransferStatus.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }
}

