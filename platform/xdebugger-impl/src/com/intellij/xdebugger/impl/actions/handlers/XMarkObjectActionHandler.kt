// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModelsManager
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.ValueMarkerPresentationDialog
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.awt.Component
import java.util.function.Consumer
import javax.swing.JDialog
import javax.swing.JFrame

@ApiStatus.Internal
class XMarkObjectActionHandler : MarkObjectActionHandler() {
  override fun perform(project: Project, event: AnActionEvent) {
    val session = DebuggerUIUtil.getSession(event)
    if (session == null) return

    val markers = (session as XDebugSessionImpl).getValueMarkers()
    val node = XDebuggerTreeActionBase.getSelectedNode(event.dataContext)
    if (markers == null || node == null) return
    val detachedView = DebuggerUIUtil.isInDetachedTree(event)
    val treeState = XDebuggerTreeState.saveState(node.tree)

    performMarkObject(event, node, markers, onSuccess = {
      updateMarkersForAllXValueModels(markers, session)
      UIUtil.invokeLaterIfNeeded(Runnable {
        if (detachedView) {
          node.tree.rebuildAndRestore(treeState)
        }
        session.rebuildViews()
      })
    })
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    return isEnabled(event)
  }

  override fun isMarked(project: Project, event: AnActionEvent): Boolean {
    val markers: XValueMarkers<*, *>? = getValueMarkers(event)
    if (markers == null) return false

    val value = XDebuggerTreeActionBase.getSelectedValue(event.getDataContext())
    return value != null && markers.getMarkup(value) != null
  }

  override fun isHidden(project: Project, event: AnActionEvent): Boolean {
    return getValueMarkers(event) == null
  }

  companion object {
    private fun getValueMarkers(event: AnActionEvent): XValueMarkers<*, *>? {
      val session = DebuggerUIUtil.getSession(event)
      return if (session != null) (session as XDebugSessionImpl).getValueMarkers() else null
    }

    @ApiStatus.Internal
    fun isEnabled(event: AnActionEvent): Boolean {
      val markers: XValueMarkers<*, *>? = getValueMarkers(event)
      if (markers == null) return false

      val value = XDebuggerTreeActionBase.getSelectedValue(event.dataContext)
      return value != null && markers.canMarkValue(value)
    }

    @ApiStatus.Internal
    fun updateMarkersForAllXValueModels(markers: XValueMarkers<*, *>, session: XDebugSessionImpl) {
      val sessionXValueModels = BackendXValueModelsManager.getInstance(session.project).getXValueModelsForSession(session)
      // TODO[IJPL-160146]: Don't update all the xValues, since some markers may not be changed
      for (sessionXValueModel in sessionXValueModels) {
        val marker = markers.getMarkup(sessionXValueModel.xValue)
        sessionXValueModel.setMarker(marker)
      }
    }

    @ApiStatus.Internal
    fun performMarkObject(
      event: AnActionEvent,
      node: XValueNodeImpl,
      markers: XValueMarkers<*, *>,
      onSuccess: () -> Unit,
    ) {
      val value = node.valueContainer

      val existing = markers.getMarkup(value)
      val markPromise: Promise<Any?>
      if (existing != null) {
        markPromise = markers.unmarkValue(value)
      }
      else {
        var component = event.getData<Component?>(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val window = ComponentUtil.getWindow(component)
        if (window !is JFrame && window !is JDialog) {
          component = window!!.owner
        }
        val dialog = ValueMarkerPresentationDialog(
          component, node.name, markers.getAllMarkers().values)
        dialog.show()
        val markup = dialog.getConfiguredMarkup()
        if (dialog.isOK && markup != null) {
          markPromise = markers.markValue(value, markup)
        }
        else {
          return
        }
      }
      markPromise.onSuccess(Consumer { _: Any? ->
        onSuccess()
      })
    }
  }
}