package com.example.pathreport

import org.junit.Assert.*
import org.junit.Test

class DeviceAccessTest {
    @Test fun unlockedPhoneEntersWithoutAnotherPrompt() {
        assertTrue(canEnterCareApp(secure = true, deviceLocked = false, manuallyLocked = false))
    }
    @Test fun phoneWithoutCredentialsCanEnter() {
        assertTrue(canEnterCareApp(secure = false, deviceLocked = false, manuallyLocked = false))
        assertTrue(canEnterCareApp(secure = false, deviceLocked = true, manuallyLocked = false))
    }
    @Test fun lockedSecurePhoneCannotEnter() {
        assertFalse(canEnterCareApp(secure = true, deviceLocked = true, manuallyLocked = false))
    }
    @Test fun manualLockRemainsUntilExplicitEntry() {
        assertFalse(canEnterCareApp(secure = true, deviceLocked = false, manuallyLocked = true))
        assertFalse(canEnterCareApp(secure = false, deviceLocked = false, manuallyLocked = true))
    }
}
