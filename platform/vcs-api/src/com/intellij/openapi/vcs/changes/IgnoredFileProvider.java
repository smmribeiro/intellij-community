// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Provides content, masks for VCS native ignore files (e.g., {@code .gitignore}, {@code .hgignore}).
 * Every plugin which has "ignore" files should implement it to contribute own ignores to VCS.
 */
public interface IgnoredFileProvider {
  ExtensionPointName<IgnoredFileProvider> IGNORE_FILE = new ExtensionPointName<>("com.intellij.ignoredFileProvider");

  /**
   * @see VcsIgnoreManager#isPotentiallyIgnoredFile(FilePath)
   */
  boolean isIgnoredFile(@NotNull Project project, @NotNull FilePath filePath);

  @NotNull
  Set<IgnoredFileDescriptor> getIgnoredFiles(@NotNull Project project);

  @NotNull
  @NlsContexts.DetailedDescription
  String getIgnoredGroupDescription();
}
