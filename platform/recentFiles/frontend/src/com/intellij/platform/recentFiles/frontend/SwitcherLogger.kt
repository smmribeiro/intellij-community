// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields

internal object SwitcherLogger {
  val GROUP = EventLogGroup("recent.files.dialog", 2)

  val NAVIGATED = EventFields.Boolean("navigated")
  val NAVIGATED_INDEXES = EventFields.IntList("navigated_indexes")
  val NAVIGATED_ORIGINAL_INDEXES = EventFields.IntList("navigated_indexes_original")

  val SHOWN_TIME_ACTIVITY = GROUP.registerIdeActivity("dialog.time", finishEventAdditionalFields =
  arrayOf(NAVIGATED, NAVIGATED_ORIGINAL_INDEXES, NAVIGATED_INDEXES))

  data class NavigationData(val navigationOriginalIndexes: List<Int>, val navigationIndexes: List<Int>)
}