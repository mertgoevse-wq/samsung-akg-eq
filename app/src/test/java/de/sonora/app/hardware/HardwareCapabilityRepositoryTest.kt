package de.sonora.app.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCapabilityRepositoryTest {
    @Test
    fun npuIsNotClaimedAvailable() {
        val repo = HardwareCapabilityRepository()
        val npu = repo.npu()
        assertFalse(npu.supported)
        assertFalse(npu.initializationSucceeded)
        assertFalse(npu.executionVerified)
    }

    @Test
    fun cpuIsVerified() {
        val repo = HardwareCapabilityRepository()
        val cpu = repo.cpu()
        assertEquals(true, cpu.executionVerified)
        assertEquals(true, cpu.supported)
        assertEquals(true, cpu.initializationSucceeded)
    }

    @Test
    fun allBackendsIncludeThree() {
        assertEquals(3, HardwareCapabilityRepository().all().size)
    }

    @Test
    fun gpuStatus_defaultsToUnverified() {
        val repo = HardwareCapabilityRepository()
        val gpu = repo.gpu()
        assertEquals("GPU (LiteRT GPU delegate)", gpu.name)
        assertTrue(gpu.supported) // hardware capability exists, just not verified
        assertFalse(gpu.executionVerified)
    }

    @Test
    fun npuStatus_documentsExynos1580() {
        val repo = HardwareCapabilityRepository()
        val status = repo.npuStatus()
        assertTrue(status.contains("Exynos 1580"))
        assertTrue(status.contains("NPU"))
        assertFalse(status.contains("available")) // No NPU available on this chipset
    }
}