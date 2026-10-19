package com.example.pathreport

import android.app.KeyguardManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertSame
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

class RotationAccessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun rotationKeepsOpenEditorAndUnsavedName() {
        val original = compose.activity
        val keyguard = original.getSystemService(KeyguardManager::class.java)
        assumeFalse(keyguard.isDeviceSecure && keyguard.isDeviceLocked)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("어르신명단").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("어르신명단").onFirst().performClick()
        compose.onNodeWithText("어르신 추가").performClick()
        compose.onNodeWithText("어르신 이름").performTextInput("회전확인")
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { original.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity { assertSame(original, it) }
            compose.onNodeWithText("회전확인").assertExists()
            compose.onNodeWithText("기기 인증으로 잠금 해제").assertDoesNotExist()
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { original.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithText("회전확인").assertExists()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun manualLockCanBeDismissedOnUnlockedPhone() {
        val keyguard = compose.activity.getSystemService(KeyguardManager::class.java)
        assumeFalse(keyguard.isDeviceSecure && keyguard.isDeviceLocked)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("잠금 화면").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("잠금 화면").performClick()
        compose.onNodeWithText("기기 인증으로 잠금 해제").performClick()
        compose.onNodeWithText("잠금 화면").assertExists()
    }
    @Test fun manualLockStaysLockedThroughRotationUntilExplicitEntry() {
        val original = compose.activity
        val keyguard = original.getSystemService(KeyguardManager::class.java)
        assumeFalse(keyguard.isDeviceSecure && keyguard.isDeviceLocked)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("잠금 화면").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("잠금 화면").performClick()
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { original.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("기기 인증으로 잠금 해제").assertExists()
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { original.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithText("기기 인증으로 잠금 해제").assertExists().performClick()
            compose.onNodeWithText("잠금 화면").assertExists()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
