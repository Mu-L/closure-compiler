/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Scope;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import java.util.Set;

/** Checks that exports of ES6 modules are not mutated outside of module initialization. */
public final class CheckNoMutatedEs6Exports implements NodeTraversal.Callback, CompilerPass {

  public static final DiagnosticType MUTATED_EXPORT =
      DiagnosticType.warning(
          "JSC_MUTATED_EXPORT",
          "The name \"{0}\" is exported and should not be mutated outside of module "
              + "initialization. Mutable exports are generally difficult to reason about. You "
              + "can work around this by exporting getter/setter functions, or an object with "
              + "mutable properties instead.");

  private final AbstractCompiler compiler;
  private final Multimap<String, Node> mutatedNames =
      MultimapBuilder.hashKeys().hashSetValues().build();
  private final Set<String> exportedLocalNames = new LinkedHashSet<>();

  public CheckNoMutatedEs6Exports(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private void checkNoMutations() {
    Set<String> mutatedExports = Sets.intersection(mutatedNames.keySet(), exportedLocalNames);

    for (String mutatedExport : mutatedExports) {
      for (Node mutation : mutatedNames.get(mutatedExport)) {
        compiler.report(JSError.make(mutation, MUTATED_EXPORT, mutatedExport));
      }
    }

    mutatedNames.clear();
    exportedLocalNames.clear();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isModuleBody()) {
      checkNoMutations();
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return switch (n.getToken()) {
      case SCRIPT -> n.getBooleanProp(Node.ES6_MODULE);
      case EXPORT -> {
        visitExport(n);
        yield true;
      }
      case NAME -> {
        visitName(t, n);
        yield true;
      }
      default -> true;
    };
  }

  private void visitExport(Node export) {
    if (export.hasOneChild() && export.getFirstChild().isExportSpecs()) {
      // export {a, b as c};
      for (Node exportSpec = export.getFirstFirstChild();
          exportSpec != null;
          exportSpec = exportSpec.getNext()) {
        checkState(exportSpec.hasTwoChildren());
        exportedLocalNames.add(exportSpec.getFirstChild().getString());
      }
    } else if (export.hasOneChild() && !export.getBooleanProp(Node.EXPORT_ALL_FROM)) {
      Node declaration = export.getFirstChild();

      if (NodeUtil.isNameDeclaration(declaration)) {
        // export const x = 0;
        // export let a, b, c;
        NodeUtil.visitLhsNodesInNode(
            declaration,
            (lhs) -> {
              checkState(lhs.isName());
              exportedLocalNames.add(lhs.getString());
            });
      } else if (export.getBooleanProp(Node.EXPORT_DEFAULT)) {
        // export default function() {}
        // export default function foo() {}
        // export default 0;
        // Note: default exports may or may not be declarations.
        if (!declaration.isClass() && !declaration.isFunction()) {
          return;
        }
        Node nameNode = declaration.getFirstChild();
        if (!nameNode.isEmpty() && !nameNode.getString().isEmpty()) {
          exportedLocalNames.add(nameNode.getString());
        }
      } else {
        // export function foo() {}
        // export class Bar {}
        checkState(declaration.isClass() || declaration.isFunction());
        Node nameNode = declaration.getFirstChild();
        exportedLocalNames.add(nameNode.getString());
      }
    }
  }

  private void visitName(NodeTraversal t, Node name) {
    Scope scope = t.getScope();
    if (NodeUtil.isLValue(name) && !scope.getClosestHoistScope().isModuleScope()) {
      Var var = scope.getVar(name.getString());
      if (var != null && var.getScope().isModuleScope()) {
        mutatedNames.put(name.getString(), name);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
}
