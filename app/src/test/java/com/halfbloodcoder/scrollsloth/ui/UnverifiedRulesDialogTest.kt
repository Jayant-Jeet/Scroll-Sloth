package com.halfbloodcoder.scrollsloth.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.halfbloodcoder.scrollsloth.ui.theme.ScrollSlothTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnverifiedRulesDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noRulesAppliedUntilExplicitConfirmation() {
        var applied = 0
        compose.setContent {
            ScrollSlothTheme { UnverifiedRulesDialog(onConfirm = { applied++ }, onDismiss = {}) }
        }
        compose.runOnIdle { assertEquals(0, applied) }
        compose.onNodeWithText("Apply unverified rules").performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun cancelDoesNotApplyRules() {
        var applied = 0
        var dismissed = 0
        compose.setContent {
            ScrollSlothTheme { UnverifiedRulesDialog(onConfirm = { applied++ }, onDismiss = { dismissed++ }) }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, applied)
            assertEquals(1, dismissed)
        }
    }
}
