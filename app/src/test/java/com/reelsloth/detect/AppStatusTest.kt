package com.reelsloth.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStatusTest {
    private val pager = NodeMatcher(viewIdContains = "id/pager")
    private val todo = NodeMatcher(viewIdContains = DetectionRules.TODO_MARKER)

    private val rules = DetectionRulesLoader.validate(
        DetectionRules(
            schemaVersion = 1,
            rulesVersion = 3,
            apps = mapOf(
                Platform.INSTAGRAM.packageName to
                    AppRule(Platform.INSTAGRAM, listOf(pager), DetectionStrategy.ITEM_CHANGED, pager),
                Platform.YOUTUBE.packageName to
                    AppRule(Platform.YOUTUBE, listOf(todo), DetectionStrategy.ITEM_CHANGED, todo),
            ),
        ),
        RulesSource.OVERRIDE,
    )

    @Test
    fun calibratedAppsWorkWhenThePermissionIsOn() {
        assertEquals(
            mapOf(
                Platform.INSTAGRAM to AppStatus.WORKING,
                Platform.YOUTUBE to AppStatus.NEEDS_CALIBRATION,
                Platform.FACEBOOK to AppStatus.NEEDS_CALIBRATION,
            ),
            AppStatuses.of(rules, serviceEnabled = true),
        )
    }

    @Test
    fun calibratedAppsNeedThePermissionWhenItIsOff() {
        assertEquals(
            mapOf(
                Platform.INSTAGRAM to AppStatus.PERMISSION_MISSING,
                Platform.YOUTUBE to AppStatus.NEEDS_CALIBRATION,
                Platform.FACEBOOK to AppStatus.NEEDS_CALIBRATION,
            ),
            AppStatuses.of(rules, serviceEnabled = false),
        )
    }
}
