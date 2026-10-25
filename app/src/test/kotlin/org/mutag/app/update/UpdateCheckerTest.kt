package org.mutag.app.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerTest {

    @Test
    fun comparesSemanticVersionsCorrectly() {
        assertTrue(UpdateChecker.compareVersion("1.0.1", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersion("v1.0.1", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersion("v1.10.0", "1.9.0") > 0)
        assertTrue(UpdateChecker.compareVersion("2.0.0", "1.99.99") > 0)

        assertTrue(UpdateChecker.compareVersion("1.0.0", "1.0.1") < 0)
        assertTrue(UpdateChecker.compareVersion("0.9.9", "1.0.0") < 0)

        assertEquals(0, UpdateChecker.compareVersion("1.0.0", "v1.0.0"))
        assertEquals(0, UpdateChecker.compareVersion("1.2.3", "1.2.3"))
    }
}
