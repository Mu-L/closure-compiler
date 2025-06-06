/*
 * Copyright 2025 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.TranspilationUtil.cannotConvertYet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;

/** Transpiles away usages of private properties in ES6 classes. */
final class RewritePrivateClassProperties extends AbstractPeepholeTranspilation {

  private final AbstractCompiler compiler;

  RewritePrivateClassProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  FeatureSet getTranspiledAwayFeatures() {
    return FeatureSet.BARE_MINIMUM.with(Feature.PRIVATE_CLASS_PROPERTIES);
  }

  @Override
  @CanIgnoreReturnValue
  Node transpileSubtree(Node n) {
    if (n.isPrivateIdentifier()) {
      cannotConvertYet(compiler, n, "private class properties");
    }
    return n;
  }
}
