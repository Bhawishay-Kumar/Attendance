package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:org.junit.Rule
  val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BHTT Attendance", appName)
  }

  @Test
  fun `create AttendanceViewModel without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    org.junit.Assert.assertNotNull(vm)
  }

  @Test
  fun `render MainAppContent without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = true) {
        MainAppContent(viewModel = vm)
      }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun `render MainAppContent with light theme`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = false) {
        MainAppContent(viewModel = vm)
      }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun `render all dock tabs without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = true) {
        MainAppContent(viewModel = vm)
      }
    }
    composeRule.waitForIdle()

    // Test switching to each tab
    for (tab in com.example.ui.components.DockTab.entries) {
      vm.selectTab(tab)
      composeRule.waitForIdle()
    }
  }

  @Test
  fun `render BunkPlannerScreen without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = true) {
        com.example.ui.bunk.BunkPlannerScreen(viewModel = vm)
      }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun `render WhatIfPredictorHost without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = true) {
        com.example.ui.whatif.WhatIfPredictorHost(vm.whatIf)
      }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun `render OnboardingFlow without crash`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.AttendanceViewModel(app)
    composeRule.setContent {
      com.example.ui.theme.BHTTAttendanceTheme(darkTheme = true) {
        com.example.ui.screens.OnboardingFlow(
          viewModel = vm,
          onFinishOnboarding = {}
        )
      }
    }
    composeRule.waitForIdle()
  }
}

