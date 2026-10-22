package de.sonora.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable

@Immutable
data class AudioOutputInfo(
    val id: Int,
    val type: Int,
    val typeName: String,
    val productName: String,
    val address: String,
    val isUsb: Boolean
)

@Immutable
data class AudioInputInfo(
    val id: Int,
    val type: Int,
    val typeName: String,
    val productName: String,
    val address: String
)

sealed interface HeadphoneModelStatus {
    data object None : HeadphoneModelStatus
    data object BuiltInSpeaker : HeadphoneModelStatus
    data class VerifiedModel(val modelName: String, val details: String) : HeadphoneModelStatus
    data class GenericUsbOutput(val message: String) : HeadphoneModelStatus
    data class OtherDevice(val name: String) : HeadphoneModelStatus
}

@Immutable
data class AudioDeviceSnapshot(
    val detectedOutputs: List<AudioOutputInfo> = emptyList(),
    val activeOutput: AudioOutputInfo? = null,
    val detectedInputs: List<AudioInputInfo> = emptyList(),
    val headphoneModel: HeadphoneModelStatus = HeadphoneModelStatus.None,
    val lastEvent: String? = null
)

/**
 * Manages live audio routing inspection and lifecycle-safe AudioDeviceCallback.
 * Tracks physical device detection, active routing, identified headphone models,
 * and available input devices without requiring microphone permissions.
 */
class AudioRoutingManager(
    private val context: Context,
    private val onSnapshotChanged: (AudioDeviceSnapshot) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val names = addedDevices?.joinToString { it.safeProductName() } ?: "keine"
            refreshSnapshot("Gerät verbunden: $names")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val names = removedDevices?.joinToString { it.safeProductName() } ?: "keine"
            refreshSnapshot("Gerät getrennt: $names")
        }
    }

    var currentSnapshot: AudioDeviceSnapshot = AudioDeviceSnapshot()
        private set

    fun register() {
        if (isRegistered || audioManager == null) return
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            isRegistered = true
            refreshSnapshot("Routing-Überwachung gestartet")
        } catch (_: Throwable) {
            refreshSnapshot("Registrierung des DeviceCallback fehlgeschlagen")
        }
    }

    fun unregister() {
        if (!isRegistered || audioManager == null) return
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Throwable) {
        } finally {
            isRegistered = false
        }
    }

    fun refresh(trigger: String = "Manuelle Aktualisierung") {
        refreshSnapshot(trigger)
    }

    private fun refreshSnapshot(eventTrigger: String) {
        if (audioManager == null) {
            val empty = AudioDeviceSnapshot(lastEvent = "AudioManager nicht verfügbar ($eventTrigger)")
            currentSnapshot = empty
            onSnapshotChanged(empty)
            return
        }

        val outputDevices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Throwable) {
            emptyArray()
        }

        val inputDevices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        } catch (_: Throwable) {
            emptyArray()
        }

        val outputs = outputDevices.map { it.toOutputInfo() }
        val inputs = inputDevices.map { it.toInputInfo() }

        // Find active output: prioritize USB headset / headphones if present, otherwise speaker
        val usbOutput = outputs.firstOrNull { it.isUsb }
        val wiredOutput = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
        val speakerOutput = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val activeOut = usbOutput ?: wiredOutput ?: speakerOutput ?: outputs.firstOrNull()

        val modelStatus = determineHeadphoneModel(usbOutput, wiredOutput, activeOut)

        val snapshot = AudioDeviceSnapshot(
            detectedOutputs = outputs,
            activeOutput = activeOut,
            detectedInputs = inputs,
            headphoneModel = modelStatus,
            lastEvent = eventTrigger
        )

        currentSnapshot = snapshot
        onSnapshotChanged(snapshot)
    }

    companion object {
        fun determineHeadphoneModel(
            usbOutput: AudioOutputInfo?,
            wiredOutput: AudioOutputInfo?,
            activeOut: AudioOutputInfo?
        ): HeadphoneModelStatus {
            if (usbOutput != null) {
                val prod = usbOutput.productName.trim()
                val addr = usbOutput.address.trim()
                val isSamsungAkg = prod.contains("EO-IC100", ignoreCase = true) ||
                        (prod.contains("Samsung", ignoreCase = true) && prod.contains("Type-C", ignoreCase = true)) ||
                        (prod.contains("AKG", ignoreCase = true) && prod.contains("Type-C", ignoreCase = true))

                return if (isSamsungAkg) {
                    HeadphoneModelStatus.VerifiedModel(
                        modelName = "Samsung EO-IC100",
                        details = "USB-C Audio Device [${prod.ifBlank { "Samsung AKG" }}]"
                    )
                } else {
                    HeadphoneModelStatus.GenericUsbOutput(
                        message = if (prod.isNotBlank()) "USB-Audioausgang erkannt ($prod)" else "USB-Audioausgang erkannt, Modell unbekannt"
                    )
                }
            }

            if (wiredOutput != null) {
                return HeadphoneModelStatus.OtherDevice(
                    name = if (wiredOutput.productName.isNotBlank()) wiredOutput.productName else "Kabelgebundener Kopfhörer"
                )
            }

            if (activeOut?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                return HeadphoneModelStatus.BuiltInSpeaker
            }

            if (activeOut != null) {
                return HeadphoneModelStatus.OtherDevice(
                    name = if (activeOut.productName.isNotBlank()) activeOut.productName else activeOut.typeName
                )
            }

            return HeadphoneModelStatus.None
        }

        fun AudioDeviceInfo.safeProductName(): String {
            val name = try { productName?.toString()?.trim() } catch (_: Throwable) { null }
            return if (name.isNullOrBlank()) "Unbekannt" else name
        }

        fun AudioDeviceInfo.safeAddress(): String {
            val addr = try { address?.trim() } catch (_: Throwable) { null }
            return if (addr.isNullOrBlank()) "–" else addr
        }

        fun AudioDeviceInfo.toOutputInfo(): AudioOutputInfo {
            val isUsbDevice = type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    type == AudioDeviceInfo.TYPE_USB_ACCESSORY

            return AudioOutputInfo(
                id = id,
                type = type,
                typeName = typeToString(type),
                productName = safeProductName(),
                address = safeAddress(),
                isUsb = isUsbDevice
            )
        }

        fun AudioDeviceInfo.toInputInfo(): AudioInputInfo {
            return AudioInputInfo(
                id = id,
                type = type,
                typeName = typeToString(type),
                productName = safeProductName(),
                address = safeAddress()
            )
        }

        fun typeToString(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Lautsprecher"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Hörmuschel"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Mikrofon (Intern)"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kabel-Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Kabel-Kopfhörer"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-Audioausgang"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-Zubehör"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio (A2DP)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset (SCO)"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line-Out Analog"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line-Out Digital"
            AudioDeviceInfo.TYPE_AUX_LINE -> "AUX"
            else -> "Audio-Gerät ($type)"
        }
    }
}
