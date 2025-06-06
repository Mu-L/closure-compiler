/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.jscomp.graph.CheckPathsBetweenNodes;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import org.jspecify.annotations.Nullable;

/**
 * Checks functions for missing return statements. Return statements are only expected for functions
 * with return type information. Functions with empty bodies are ignored.
 */
class CheckMissingReturn extends NodeTraversal.AbstractCfgCallback {

  static final DiagnosticType MISSING_RETURN_STATEMENT =
      DiagnosticType.warning(
          "JSC_MISSING_RETURN_STATEMENT",
          "Missing return statement. Function expected to return {0}.");

  private final AbstractCompiler compiler;
  private final CodingConvention convention;

  /* Skips all exception edges and impossible edges. */
  private static boolean goesThroughTrueCondition(
      DiGraphEdge<Node, ControlFlowGraph.Branch> input) {
    // First skill all exceptions.
    Branch branch = input.getValue();
    if (branch == Branch.ON_EX) {
      return false;
    } else if (branch.isConditional()) {
      Node condition = NodeUtil.getConditionExpression(input.getSource().getValue());
      // TODO(user): We CAN make this bit smarter just looking at
      // constants. We DO have a full blown ReverseAbstractInterupter and
      // type system that can evaluate some impressions' boolean value but
      // for now we will keep this pass lightweight.
      if (condition != null) {
        Tri val = NodeUtil.getBooleanValue(condition);
        if (val != Tri.UNKNOWN) {
          return val.toBoolean(true) == (branch == Branch.ON_TRUE);
        }
      }
    }
    return true;
  }

  CheckMissingReturn(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
  }

  @Override
  public void enterScopeWithCfg(NodeTraversal t) {
    Node n = t.getScopeRoot();
    JSType returnType = getExplicitReturnTypeIfExpected(n);

    if (returnType == null) {
      // No return value is expected, so nothing to check.
      return;
    }

    if (n.isArrowFunction()) {
      Node functionBody = NodeUtil.getFunctionBody(n);
      if (!functionBody.isBlock()) {
        // Body is an expression, which is the implicit return value.
        return;
      }
    }

    ControlFlowGraph<Node> cfg = getControlFlowGraph(compiler);
    if (fastAllPathsReturnCheck(cfg)) {
      return;
    }

    CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test =
        new CheckPathsBetweenNodes<>(
            cfg,
            cfg.getEntry(),
            cfg.getImplicitReturn(),
            (Node input) -> input != null && input.isReturn(),
            CheckMissingReturn::goesThroughTrueCondition);

    if (!test.allPathsSatisfyPredicate()) {
      compiler.report(
          JSError.make(t.getScopeRoot(), MISSING_RETURN_STATEMENT, returnType.toString()));
    }
  }

  /**
   * Fast check to see if all execution paths contain a return statement. May spuriously report that
   * a return statement is missing.
   *
   * @return true if all paths return, converse not necessarily true
   */
  private boolean fastAllPathsReturnCheck(ControlFlowGraph<Node> cfg) {
    for (DiGraphEdge<Node, Branch> s : cfg.getImplicitReturn().getInEdges()) {
      Node n = s.getSource().getValue();
      // NOTE(dimvar): it is possible to change ControlFlowAnalysis.java, so
      // that the calls that always throw are treated in the same way as THROW
      // in the CFG. Then, we would not need to use the coding convention here.
      if (!n.isReturn() && !convention.isFunctionCallThatAlwaysThrows(n)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
  }

  /**
   * Determines if the given scope should explicitly return. All functions with non-void or
   * non-unknown return types must have explicit returns.
   *
   * <p>Exception: Constructors which specifically specify a return type are used to allow
   * invocation without requiring the "new" keyword. They have an implicit return type. See unit
   * tests.
   *
   * @return If a return type is expected, returns it. Otherwise, returns null.
   */
  private @Nullable JSType getExplicitReturnTypeIfExpected(Node scopeRoot) {
    if (!scopeRoot.isFunction()) {
      // Nothing to do in a global/module/block scope.
      return null;
    }
    FunctionType scopeType = JSType.toMaybeFunctionType(scopeRoot.getJSType());

    if (scopeType == null) {
      return null;
    }

    if (isEmptyFunction(scopeRoot)) {
      return null;
    }

    if (scopeType.isConstructor()) {
      return null;
    }

    JSType returnType = scopeType.getReturnType();

    if (returnType == null) {
      return null;
    }

    if (scopeRoot.isAsyncFunction()) {
      // Unwrap the declared return type (e.g. "!Promise<number>" becomes "number")
      returnType = Promises.getTemplateTypeOfThenable(compiler.getTypeRegistry(), returnType);
    } else if (scopeRoot.isGeneratorFunction()) {
      // Unwrap the declared return type (e.g. "!Generator<string, number>" becomes "number")
      returnType = JsIterables.getReturnElementType(returnType, compiler.getTypeRegistry());
    }

    if (!isVoidOrUnknown(returnType)) {
      return returnType;
    }

    return null;
  }

  /**
   * @return {@code true} if function represents a JavaScript function
   *     with an empty body
   */
  private static boolean isEmptyFunction(Node function) {
    return function.hasXChildren(3) && !function.getSecondChild().getNext().hasChildren();
  }

  /**
   * @return {@code true} if returnType is void, unknown, or a union
   *     containing void or unknown
   */
  private boolean isVoidOrUnknown(JSType returnType) {
    final JSType voidType = compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE);
    return voidType.isSubtypeOf(returnType);
  }
}
