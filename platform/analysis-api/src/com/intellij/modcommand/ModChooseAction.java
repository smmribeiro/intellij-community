// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A command that displays a UI and allows users to select a subsequent action from the list.
 * Intention preview assumes that the first available action is selected by default.
 * In batch mode, the first option is also selected automatically.
 * 
 * @param title title to display to the user
 * @param actions actions to select from. If there's only one action, then it could be executed right away without asking the user. 
 */
public record ModChooseAction(@NotNull @NlsContexts.PopupTitle String title, 
                              @NotNull List<? extends @NotNull ModCommandAction> actions) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return actions.isEmpty();
  }

  @Override
  public @NotNull ModCommand andThen(@NotNull ModCommand next) {
    if (next.isEmpty()) return this;
    return new ModChooseAction(title, ContainerUtil.map(
      actions, action -> action instanceof ActionDelegate delegate ? 
                         new ActionDelegate(delegate.myAction, delegate.myAdditionalCommand.andThen(next)) : 
                         new ActionDelegate(action, next)));
  }
  
  private static class ActionDelegate implements ModCommandAction, ReportingClassSubstitutor {
    private final @NotNull ModCommandAction myAction;
    private final @NotNull ModCommand myAdditionalCommand;

    ActionDelegate(@NotNull ModCommandAction action, @NotNull ModCommand command) {
      myAction = action;
      myAdditionalCommand = command;
    }

    @Override
    public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
      return myAction.getPresentation(context);
    }

    @Override
    public @NotNull ModCommand perform(@NotNull ActionContext context) {
      return myAction.perform(context).andThen(myAdditionalCommand);
    }

    @Override
    public @NotNull String getFamilyName() {
      return myAction.getFamilyName();
    }
    
    @Override
    public @NotNull Class<?> getSubstitutedClass() {
      return ReportingClassSubstitutor.getClassToReport(myAction);
    }
  }
}
