/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class FunctionToBlockMutatorTest {

  private boolean needsDefaultResult;
  private boolean isCallInLoop;

  @Before
  public void setUp() {
    needsDefaultResult = false;
    isCallInLoop = false;
  }

  @Test
  public void testMutateNoReturnWithoutResultAssignment() {
    helperMutate("function foo(){}; foo();", "{}", "foo");
  }

  @Test
  public void testMutateNoReturnWithResultAssignment() {
    needsDefaultResult = true;
    helperMutate("function foo(){}; var result = foo();", "{result = void 0}", "foo");
  }

  @Test
  public void testMutateNoValueReturnWithoutResultAssignment() {
    helperMutate("function foo(){return;}; foo();", "{}", "foo", null);
  }

  @Test
  public void testMutateNoValueReturnWithResultAssignment() {
    helperMutate("function foo(){return;}; var result = foo();", "{result = void 0}", "foo");
  }

  @Test
  public void testMutateValueReturnWithoutResultAssignment() {
    helperMutate("function foo(){return true;}; foo();", "{true;}", "foo", null);
  }

  @Test
  public void testMutateValueReturnWithResultAssignment() {
    needsDefaultResult = true;
    helperMutate("function foo(){return true;}; var x=foo();", "{x=true}", "foo", "x");
  }

  @Test
  public void testMutateWithMultipleReturns() {
    needsDefaultResult = true;
    helperMutate(
        "function foo(){ if (0) {return 0} else {return 1} }; var result=foo();",
        """
        {
          JSCompiler_inline_label_foo_0: {
            if (0) {
              result = 0;
              break JSCompiler_inline_label_foo_0
            } else {
              result = 1;
              break JSCompiler_inline_label_foo_0
            }
            result=void 0
          }
        }
        """,
        "foo");
  }

  @Test
  public void testMutateWithParameters1() {
    // Simple call with useless parameter
    helperMutate("function foo(a){return true;}; foo(x);", "{true}", "foo", null);
  }

  @Test
  public void testMutateWithParameters2() {
    // Simple call with parameter
    helperMutate("function foo(a){return x;}; foo(x);", "{x}", "foo", null);
  }

  @Test
  public void testMutateWithParameters3() {
    // Parameter has side-effects.
    helperMutate(
        "function foo(a){return a;}; function x() { foo(x++); }", //
        "{ x++ }",
        "foo",
        null);
  }

  @Test
  public void testMutate8() {
    // Parameter has side-effects.
    helperMutate(
        "function foo(a){return a+a;}; foo(x++);",
        "{var a$jscomp$inline_0 = x++; a$jscomp$inline_0 + a$jscomp$inline_0;}",
        "foo",
        null);
  }

  @Test
  public void testMutateInitializeUninitializedVars1() {
    isCallInLoop = true;
    helperMutate(
        "function foo(a){var b;return a;}; foo(1);",
        "{var b$jscomp$inline_1 = void 0; 1;}",
        "foo",
        null);
  }

  @Test
  public void testMutateInitializeUninitializedVars2() {
    helperMutate(
        "function foo(a) {var b; for(b in c)return a;}; foo(1);",
        """
        {
          JSCompiler_inline_label_foo_2:
          {
            var b$jscomp$inline_1;
            for (b$jscomp$inline_1 in c) {
              1;
              break JSCompiler_inline_label_foo_2;
            }
          }
        }
        """,
        "foo",
        null);
  }

  @Test
  public void testMutateInitializeUninitializedLets1() {
    isCallInLoop = true;
    helperMutate(
        "function foo(a){let b;return a;}; foo(1);",
        "{let b$jscomp$inline_1 = void 0; 1;}",
        "foo",
        null);
  }

  @Test
  public void testMutateInitializeUninitializedLets2() {
    helperMutate(
        "function foo(a) {for(let b in c)return a;}; foo(1);",
        """
        {
          JSCompiler_inline_label_foo_2:
          {
            for (let b$jscomp$inline_1 in c) {
              1;
              break JSCompiler_inline_label_foo_2;
            }
          }
        }
        """,
        "foo",
        null);
  }

  @Test
  public void testMutateCallInLoopVars1() {
    String src =
        """
        function foo(a) {
          var B = bar();
          a;
        };
        foo(1);
        """;

    // baseline: outside a loop, the constant remains constant.
    isCallInLoop = false;
    helperMutate(src, "{var B$jscomp$inline_1 = bar(); 1;}", "foo", null);
    // ... in a loop, the constant-ness is removed.
    // TODO(johnlenz): update this test to look for the const annotation.
    isCallInLoop = true;
    helperMutate(src, "{var B$jscomp$inline_1 = bar(); 1;}", "foo", null);
  }

  @Test
  public void testMutateFunctionDefinition() {
    // Function declarations are rewritten as function expressions.
    helperMutate(
        "function foo(a){function g(){}}; foo(1);",
        "{var g$jscomp$inline_1 = function() {};}",
        "foo",
        null);
  }

  @Test
  public void testMutateFunctionDefinitionHoisting() {
    helperMutate(
        """
        function foo(a){
          var b = g(a);
          function g(c){ return c; }
          var c = i();
          function h(){}
          function i(){}
        }
        foo(1);
        """,
        """
        {
          var g$jscomp$inline_1 = function(c$jscomp$inline_6) {return c$jscomp$inline_6};
          var h$jscomp$inline_2 = function(){};
          var i$jscomp$inline_3 = function(){};
          var b$jscomp$inline_4 = g$jscomp$inline_1(1);
          var c$jscomp$inline_5 = i$jscomp$inline_3();
        }
        """,
        "foo",
        null);
  }

  private void validateSourceInfo(Compiler compiler, Node subtree) {
    new SourceInfoCheck(compiler).setCheckSubTree(subtree);
    assertThat(compiler.getErrors()).isEmpty();
  }

  public void helperMutate(String code, String expectedResult, String fnName) {
    helperMutate(code, expectedResult, fnName, "result");
  }

  public void helperMutate(
      String code, String expectedResult, String fnName, @Nullable String resultName) {
    final Compiler compiler = new Compiler();
    compiler.initCompilerOptionsIfTesting();
    final FunctionToBlockMutator mutator =
        new FunctionToBlockMutator(compiler, compiler.getUniqueNameIdSupplier());

    compiler.init(
        ImmutableList.of(),
        ImmutableList.of(SourceFile.fromCode("[testcode]", code)),
        compiler.getOptions());
    compiler.parse();
    Node script = compiler.getRoot().getSecondChild().getFirstChild();

    Normalize.createNormalizeForOptimizations(compiler)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    GatherGetterAndSetterProperties.update(
        compiler, compiler.getExternsRoot(), compiler.getJsRoot());
    new PureFunctionIdentifier.Driver(compiler)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());

    final Node fnNode = findFunction(script, fnName);

    Node expectedRoot = compiler.parseTestCode(expectedResult);
    checkState(compiler.getErrorCount() == 0);
    final Node expected = expectedRoot.getFirstChild();

    // inline tester
    Method tester =
        (NodeTraversal t, Node n, Node parent) -> {
          Node result =
              mutator.mutate(fnName, fnNode, n, resultName, needsDefaultResult, isCallInLoop);
          validateSourceInfo(compiler, result);
          assertNode(result).usingSerializer(compiler::toSource).isEqualTo(expected);
          return true;
        };

    compiler.resetUniqueNameId();
    TestCallback test = new TestCallback(fnName, tester);
    NodeTraversal.traverse(compiler, script, test);
  }

  @FunctionalInterface
  private interface Method {
    boolean call(NodeTraversal t, Node n, Node parent);
  }

  static class TestCallback implements NodeTraversal.Callback {

    private final String callname;
    private final Method method;
    private boolean complete = false;

    TestCallback(String callname, Method method) {
      this.callname = callname;
      this.method = method;
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return !complete;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node first = n.getFirstChild();
        if (first.isName() && first.getString().equals(callname)) {
          complete = method.call(t, n, parent);
        }
      }

      if (parent == null) {
        assertThat(complete).isTrue();
      }
    }
  }

  private static Node findFunction(Node n, String name) {
    if (n.isFunction()) {
      if (n.getFirstChild().getString().equals(name)) {
        return n;
      }
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      Node result = findFunction(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }
}
