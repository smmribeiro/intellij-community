// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.diff;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DiffElement<T> {
  public static final DiffElement<?>[] EMPTY_ARRAY = new DiffElement[0];

  public abstract @NlsSafe String getPath();

  public abstract @NlsSafe @NotNull String getName();

  /**
   * Returns name to be used for displaying this item in UI
   */
  public @NlsSafe String getPresentableName() {
    return getName();
  }

  /**
   * Returns path to be used for displaying this item in UI and logs
   */
  public @NlsSafe String getPresentablePath() {
    return getPresentableName();
  }

  /**
   * Returns the path of the element that is subjected to filtering.
   * Filter operations will be executed based on the returned path.
   */
  public @NlsSafe String getFilterablePath() {
    return getPath();
  }

  public abstract long getSize();

  public abstract long getTimeStamp();

  public FileType getFileType() {
    return FileTypeManager.getInstance().getFileTypeByFileName(getName());
  }

  public abstract boolean isContainer();

  public abstract DiffElement[] getChildren() throws IOException;

  public @Nullable Navigatable getNavigatable(@Nullable Project project) {
    return null;
  }

  /**
   * Returns content data as byte array. Can be null, if element for example is a container
   * @return content byte array
   * @throws IOException when reading
   */
  public abstract byte @Nullable [] getContent() throws IOException;

  public @Nullable InputStream getContentStream() throws IOException {
    byte[] bytes = getContent();
    return bytes != null ? new ByteArrayInputStream(bytes) : null;
  }

  public @NotNull Charset getCharset() {
    return EncodingManager.getInstance().getDefaultCharset();
  }

  public abstract T getValue();

  public static String getSeparator() {
    return "/";
  }

  public @Nullable Icon getIcon() {
    return null;
  }

  /**
   * Called in background thread without ReadLock OR in EDT
   *
   * @see com.intellij.diff.chains.DiffRequestProducer#process
   */
  public @NotNull DiffContent createDiffContent(@Nullable Project project, @NotNull ProgressIndicator indicator)
    throws DiffRequestProducerException, ProcessCanceledException {
    try {
      final T src = getValue();
      if (src instanceof VirtualFile) {
        return DiffContentFactory.getInstance().create(project, (VirtualFile)src);
      }

      byte[] content = getContent();
      if (content == null) throw new DiffRequestProducerException(DiffBundle.message("error.cant.show.dirdiff.preview.cant.load.content"));

      return DiffContentFactory.getInstance().create(project, new String(content, getCharset()), getFileType());
    }
    catch (IOException e) {
      throw new DiffRequestProducerException(e);
    }
  }

  public @Nullable Callable<DiffElement<T>> getElementChooser(@Nullable Project project) {
    return null;
  }

  /**
   * Defines is it possible to perform such operations as copy or delete through Diff Panel
   *
   * @return {@code true} if copy, delete, etc operations are allowed,
   *        {@code false} otherwise
   */
  public boolean isOperationsEnabled() {
    return false;
  }

  /**
   * Copies element to the container.
   *
   * @param container file directory or other container
   * @param relativePath relative path from root
   * @return {@code true} if coping was completed successfully,
   *        {@code false} otherwise
   */
  public @Nullable DiffElement<?> copyTo(DiffElement<T> container, String relativePath) {
    return null;
  }

  /**
   * Deletes element
   * @return {@code true} if deletion was completed successfully,
   *        {@code false} otherwise
   */
  public boolean delete() {
    return false;
  }

  public void refresh(boolean userInitiated) throws IOException{
  }
}
