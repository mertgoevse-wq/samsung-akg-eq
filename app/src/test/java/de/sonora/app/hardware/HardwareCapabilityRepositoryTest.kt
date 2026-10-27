package de.sonora.app.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun allBackendsIncludeThree() {
        assertEquals(3, HardwareCapabilityRepository().all().size)
    }
}
