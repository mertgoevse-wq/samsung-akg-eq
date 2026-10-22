package de.sonora.app.core

import android.media.AudioDeviceInfo
import de.sonora.app.audio.AudioOutputInfo
import de.sonora.app.audio.AudioRoutingManager
import de.sonora.app.audio.HeadphoneModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingManagerTest {

    @Test
    fun `EO-IC100 wird nur bei belegbaren Geratedaten als verifiziert erkannt`() {
        val samsungAkgUsb = AudioOutputInfo(
            id = 1,
            type = AudioDeviceInfo.TYPE_USB_HEADSET,
            typeName = "USB-C Headset",
            productName = "Samsung Type-C Earphones (EO-IC100)",
            address = "card=1;device=0",
            isUsb = true
        )

        val status = AudioRoutingManager.determineHeadphoneModel(
            usbOutput = samsungAkgUsb,
            wiredOutput = null,
            activeOut = samsungAkgUsb
        )

        assertTrue("Sollte VerifiedModel sein", status is HeadphoneModelStatus.VerifiedModel)
        val verified = status as HeadphoneModelStatus.VerifiedModel
        assertEquals("Samsung EO-IC100", verified.modelName)
    }

    @Test
    fun `generischer USB-Ausgang ohne Modellnachweis wird als Modell unbekannt klassifiziert`() {
        val genericUsb = AudioOutputInfo(
            id = 2,
            type = AudioDeviceInfo.TYPE_USB_DEVICE,
            typeName = "USB-Audioausgang",
            productName = "",
            address = "",
            isUsb = true
        )

        val status = AudioRoutingManager.determineHeadphoneModel(
            usbOutput = genericUsb,
            wiredOutput = null,
            activeOut = genericUsb
        )

        assertTrue("Sollte GenericUsbOutput sein", status is HeadphoneModelStatus.GenericUsbOutput)
        val generic = status as HeadphoneModelStatus.GenericUsbOutput
        assertEquals("USB-Audioausgang erkannt, Modell unbekannt", generic.message)
    }

    @Test
    fun `fremder USB DAC zeigt seinen Produktnamen aber behauptet kein Samsung Modell`() {
        val sonyDac = AudioOutputInfo(
            id = 3,
            type = AudioDeviceInfo.TYPE_USB_DEVICE,
            typeName = "USB-Audioausgang",
            productName = "Sony Hi-Res Audio DAC",
            address = "bus/001/002",
            isUsb = true
        )

        val status = AudioRoutingManager.determineHeadphoneModel(
            usbOutput = sonyDac,
            wiredOutput = null,
            activeOut = sonyDac
        )

        assertTrue(status is HeadphoneModelStatus.GenericUsbOutput)
        val generic = status as HeadphoneModelStatus.GenericUsbOutput
        assertTrue(generic.message.contains("Sony Hi-Res Audio DAC"))
    }

    @Test
    fun `interner Lautsprecher wird korrekt als BuiltInSpeaker erkannt`() {
        val speaker = AudioOutputInfo(
            id = 4,
            type = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            typeName = "Lautsprecher",
            productName = "Speaker",
            address = "",
            isUsb = false
        )

        val status = AudioRoutingManager.determineHeadphoneModel(
            usbOutput = null,
            wiredOutput = null,
            activeOut = speaker
        )

        assertEquals(HeadphoneModelStatus.BuiltInSpeaker, status)
    }

    @Test
    fun `kein Ausgang aktiv liefert None`() {
        val status = AudioRoutingManager.determineHeadphoneModel(
            usbOutput = null,
            wiredOutput = null,
            activeOut = null
        )

        assertEquals(HeadphoneModelStatus.None, status)
    }
}
