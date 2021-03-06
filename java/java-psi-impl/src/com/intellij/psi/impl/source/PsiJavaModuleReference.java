/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

public class PsiJavaModuleReference extends PsiReferenceBase.Poly<PsiJavaModuleReferenceElement> {
  public PsiJavaModuleReference(@NotNull PsiJavaModuleReferenceElement element) {
    super(element, new TextRange(0, element.getTextLength()), false);
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getElement().getReferenceText();
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(getProject()).resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
    PsiJavaModuleReferenceElement element = getElement();
    if (element instanceof PsiCompiledElement) {
      throw new IncorrectOperationException(JavaCoreBundle.message("psi.error.attempt.to.edit.class.file", element.getContainingFile()));
    }
    PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(element.getProject());
    PsiJavaModuleReferenceElement newElement = factory.createModuleFromText("module " + newName + " {}").getNameIdentifier();
    return element.replace(newElement);
  }

  private Project getProject() {
    return getElement().getProject();
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver<PsiJavaModuleReference> {
    private static final ResolveCache.PolyVariantResolver<PsiJavaModuleReference> INSTANCE = new Resolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaModuleReference reference, boolean incompleteCode) {
      PsiFile file = reference.getElement().getContainingFile();
      String moduleName = reference.getCanonicalText();
      Collection<PsiJavaModule> modules = findModules(file, moduleName, incompleteCode);
      if (!modules.isEmpty()) {
        ResolveResult[] result = new ResolveResult[modules.size()];
        int i = 0;
        for (PsiJavaModule module : modules) result[i++] = new PsiElementResolveResult(module);
        return result;
      }
      else {
        return ResolveResult.EMPTY_ARRAY;
      }
    }

    private static Collection<PsiJavaModule> findModules(PsiFile file, String moduleName, boolean incompleteCode) {
      Project project = file.getProject();
      GlobalSearchScope scope = incompleteCode ? GlobalSearchScope.allScope(project) : file.getResolveScope();
      return JavaFileManager.SERVICE.getInstance(project).findModules(moduleName, scope);
    }
  }

  private static final Key<CachedValue<Collection<PsiJavaModule>>> K_COMPLETE = Key.create("java.module.ref.text.resolve.complete");
  private static final Key<CachedValue<Collection<PsiJavaModule>>> K_INCOMPLETE = Key.create("java.module.ref.text.resolve.incomplete");

  @Nullable
  public static PsiJavaModule resolve(@NotNull PsiElement refOwner, String refText, boolean incompleteCode) {
    Collection<PsiJavaModule> modules = multiResolve(refOwner, refText, incompleteCode);
    return modules.size() == 1 ? modules.iterator().next() : null;
  }

  @NotNull
  public static Collection<PsiJavaModule> multiResolve(@NotNull final PsiElement refOwner, final String refText, final boolean incompleteCode) {
    if (StringUtil.isEmpty(refText)) return Collections.emptyList();
    CachedValuesManager manager = CachedValuesManager.getManager(refOwner.getProject());
    Key<CachedValue<Collection<PsiJavaModule>>> key = incompleteCode ? K_INCOMPLETE : K_COMPLETE;
    return manager.getCachedValue(refOwner, key, () -> {
      Collection<PsiJavaModule> modules = Resolver.findModules(refOwner.getContainingFile(), refText, incompleteCode);
      return CachedValueProvider.Result.create(modules, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }, false);
  }
}