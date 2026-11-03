package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.bunk.BunkOverview
import com.example.data.bunk.BunkPlannerEngine
import com.example.data.bunk.BunkPlannerResult
import com.example.data.bunk.NoSafeReason
import com.example.data.model.PeriodStatus
import com.example.data.model.StudentProfile
import com.example.data.timetable.TimetableMatrix
import com.example.ui.bunk.BunkPlannerContent
import com.example.ui.theme.GlassThemeTokens
import com.example.ui.theme.LocalGlassTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * UI tests for the upgraded Bunk Planner. Endless decorative animations are switched off
 * (liveMotion = false) so the Compose test clock can settle; screens scroll to what they assert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BunkPlannerUiTest {
    @get:Rule val rule = createComposeRule()

    private val profile = StudentProfile()

    private fun ready(): BunkPlannerResult.Ready {
        val rec = mutableMapOf<String, MutableMap<Int, PeriodStatus>>()
        listOf("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05").forEach { ds ->
            val date = LocalDate.parse(ds)
            TimetableMatrix.getScheduleForDay(date.dayOfWeek.value, profile.batchEnum, profile.courseSelectionEnum)
                .filter { !it.subject.isAutoPresent }
                .forEach { rec.getOrPut(ds) { mutableMapOf() }[it.periodNumber] = PeriodStatus.PRESENT }
        }
        val r = BunkPlannerEngine.plan(profile, rec, { it == "2026-11-09" }, LocalDate.parse("2026-11-05"))
        assertTrue(r is BunkPlannerResult.Ready)
        return r as BunkPlannerResult.Ready
    }

    private fun setContent(dark: Boolean, result: BunkPlannerResult?, detail: Int? = null, onBack: () -> Unit = {}) {
        rule.setContent {
            val base = LocalGlassTheme.current
            val tokens: GlassThemeTokens = base.copy(isDark = dark)
            CompositionLocalProvider(LocalGlassTheme provides tokens) {
                var d by remember { mutableStateOf(detail) }
                BunkPlannerContent(result, d, { d = it }, { d = null }, onBack, {}, liveMotion = false)
            }
        }
    }

    private fun scrollList(tag: String) {
        rule.onNodeWithTag("bunk_planner_list").performScrollToNode(hasTestTag(tag))
        try {
            rule.onNodeWithTag(tag).performScrollTo()
        } catch (_: Throwable) {}
    }

    @Test fun darkTheme_showsOverviewAndFirstCard() {
        setContent(true, ready())
        rule.onNodeWithTag("bunk_overview").assertIsDisplayed()
        scrollList("bunk_card_1")
        rule.onNodeWithTag("bunk_card_1").assertIsDisplayed()
    }

    @Test fun lightTheme_rendersCardsAndAssumption() {
        setContent(false, ready())
        scrollList("bunk_assumption")
        rule.onNodeWithTag("bunk_assumption").assertIsDisplayed()
        scrollList("bunk_card_1")
        rule.onNodeWithTag("bunk_card_1").assertIsDisplayed()
    }

    @Test fun neverMoreThanThreeCards() {
        setContent(true, ready())
        scrollList("bunk_card_1")
        rule.onNodeWithTag("bunk_card_1").assertIsDisplayed()
        rule.onAllNodesWithTagCount("bunk_card_4").let { assertTrue(it == 0) }
    }

    @Test fun loadingState() {
        setContent(true, null)
        rule.onNodeWithTag("bunk_loading").assertIsDisplayed()
    }

    @Test fun noSafeOptionState() {
        val ov: BunkOverview = ready().overview
        setContent(true, BunkPlannerResult.NoSafeOption(ov, NoSafeReason.EVERY_LEAVE_TOO_COSTLY, 74.0, emptyList(), emptyList()))
        scrollList("bunk_no_safe")
        rule.onNodeWithTag("bunk_no_safe").assertIsDisplayed()
    }

    @Test fun insufficientDataState() {
        setContent(false, BunkPlannerResult.InsufficientData(null, "No classes marked yet."))
        scrollList("bunk_insufficient")
        rule.onNodeWithTag("bunk_insufficient").assertIsDisplayed()
    }

    @Test fun backButtonInvokesCallback() {
        var backed = false
        setContent(true, ready(), onBack = { backed = true })
        rule.onNodeWithTag("bunk_back").performClick()
        assertTrue(backed)
    }

    @Test fun detailsOpenAndCloseWithoutLeavingPlanner() {
        setContent(true, ready())
        scrollList("bunk_details_1")
        rule.onNodeWithTag("bunk_details_1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("bunk_details").assertIsDisplayed()
        rule.onNodeWithTag("bunk_details").performScrollToNode(hasTestTag("bunk_explanation"))
        rule.onNodeWithTag("bunk_explanation").assertIsDisplayed()
        rule.onNodeWithTag("bunk_details").performScrollToNode(hasTestTag("bunk_details_back"))
        rule.onNodeWithTag("bunk_details_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("bunk_planner_list").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        this.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
}
