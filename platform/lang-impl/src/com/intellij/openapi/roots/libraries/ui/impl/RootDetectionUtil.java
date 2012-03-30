/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsDetector;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class RootDetectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.libraryEditor.RootDetectionUtil");

  private RootDetectionUtil() {
  }

  @NotNull
  public static List<OrderRoot> detectRoots(@NotNull final Collection<VirtualFile> rootCandidates,
                                            @Nullable Component parentComponent,
                                            @Nullable Project project,
                                            @NotNull final LibraryRootsComponentDescriptor rootsComponentDescriptor) {
    return detectRoots(rootCandidates, parentComponent, project, rootsComponentDescriptor.getRootsDetector(),
                       rootsComponentDescriptor.getRootTypes());
  }

  @NotNull
  public static List<OrderRoot> detectRoots(@NotNull final Collection<VirtualFile> rootCandidates, @Nullable Component parentComponent,
                                            @Nullable Project project, @NotNull final LibraryRootsDetector detector,
                                            @NotNull OrderRootType[] rootTypesAllowedToBeSelectedByUserIfNothingIsDetected) {
    final List<OrderRoot> result = new ArrayList<OrderRoot>();
    final List<SuggestedChildRootInfo> suggestedRoots = new ArrayList<SuggestedChildRootInfo>();
    new Task.Modal(project, "Scanning for Roots", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          for (VirtualFile rootCandidate : rootCandidates) {
            final Collection<OrderRoot> roots = detector.detectRoots(rootCandidate, indicator);
            if (!roots.isEmpty() && allRootsEqualTo(roots, rootCandidate)) {
              result.addAll(roots);
            }
            else {
              for (OrderRoot root : roots) {
                final String typeName = detector.getRootTypeName(root.getType(), root.isJarDirectory());
                LOG.assertTrue(typeName != null, "Unexpected root type " + root.getType().name() + (root.isJarDirectory() ? " (jar directory)" : "") + ", detectors: " + detector);
                suggestedRoots.add(new SuggestedChildRootInfo(rootCandidate, root, typeName));
              }
            }
          }
        }
        catch (ProcessCanceledException ignored) {
        }
      }
    }.queue();

    if (!suggestedRoots.isEmpty()) {
      final DetectedRootsChooserDialog dialog = parentComponent != null
                                                ? new DetectedRootsChooserDialog(parentComponent, suggestedRoots)
                                                : new DetectedRootsChooserDialog(project, suggestedRoots);
      dialog.show();
      if (!dialog.isOK()) {
        return Collections.emptyList();
      }
      for (SuggestedChildRootInfo rootInfo : dialog.getChosenRoots()) {
        result.add(rootInfo.getSuggestedRoot());
      }
    }

    if (result.isEmpty() && rootTypesAllowedToBeSelectedByUserIfNothingIsDetected.length > 0) {
      Map<String, Pair<OrderRootType, Boolean>> types = new HashMap<String, Pair<OrderRootType, Boolean>>();
      for (OrderRootType type : rootTypesAllowedToBeSelectedByUserIfNothingIsDetected) {
        for (boolean isDirectory : new boolean[]{false, true}) {
          final String typeName = detector.getRootTypeName(type, isDirectory);
          if (typeName != null) {
            types.put(typeName, Pair.create(type, isDirectory));
          }
        }
      }
      LOG.assertTrue(!types.isEmpty(), "No allowed root types found for " + detector);
      List<String> sortedNames = new ArrayList<String>(types.keySet());
      Collections.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
      final int i = Messages.showChooseDialog("Choose category for selected files:", "Attach Files",
                                              ArrayUtil.toStringArray(sortedNames), sortedNames.get(0), null);
      if (i != -1) {
        final Pair<OrderRootType, Boolean> pair = types.get(sortedNames.get(i));
        for (VirtualFile candidate : rootCandidates) {
          result.add(new OrderRoot(candidate, pair.getFirst(), pair.getSecond()));
        }
      }
    }

    return result;
  }

  private static boolean allRootsEqualTo(Collection<OrderRoot> roots, VirtualFile candidate) {
    for (OrderRoot root : roots) {
      if (!root.getFile().equals(candidate)) {
        return false;
      }
    }
    return true;
  }
}
