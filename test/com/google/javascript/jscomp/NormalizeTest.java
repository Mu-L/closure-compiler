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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class NormalizeTest extends CompilerTestCase {

  private static final String EXTERNS = "var window; var Arguments;";

  public NormalizeTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return Normalize.createNormalizeForOptimizations(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    // The normalize pass is only run once.
    return 1;
  }

  @Before
  public void customSetUp() throws Exception {
    // Validate that Normalize copies colors onto any nodes it synthesizes
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
  }

  @Test
  public void testMultipleForOfLoopsWithSameNameInductionVariable() {
    Sources srcs =
        srcs(
            """
            function* inorder1(t) {
                for (var x of []) {
                  yield x;
                }
                for (var x of []) {
                  yield x;
                }
            }
            """);

    Expected expected =
        expected(
            """
            function* inorder1(t) {
                var x;
                for (x of []) {
                  yield x;
                }
                for (x of []) {
                  yield x;
                }
            }
            """);

    test(srcs, expected); //
  }

  @Test
  @SuppressWarnings("RhinoNodeGetFirstFirstChild") // to allow adding separate comments per-child
  public void testConstAnnotationPropagation() {
    test(
        "const x = 3; var a,     b; var y = x + 2;", //
        "const x = 3; var a; var b; var y = x + 2;");
    Node root = getLastCompiler().getRoot();
    Node scriptNode =
        root.getLastChild() // ROOT of input sources
            .getLastChild();

    // `const x = 3;`
    Node constNode = scriptNode.getFirstChild();

    // `x`
    Node constName = constNode.getOnlyChild();
    assertThat(constName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    // `var y = x + 2;`
    Node lastStatement = scriptNode.getLastChild();
    // `y = x + 2`
    Node yVar = lastStatement.getOnlyChild();
    Node secondNameNodeOfX =
        yVar.getFirstChild() // `x + 2`
            .getFirstChild();

    assertThat(secondNameNodeOfX.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  @SuppressWarnings("RhinoNodeGetFirstFirstChild") // to allow adding separate comments per-child
  public void testRestConstAnnotationPropagation() {
    testSame(
        """
        const {...x} = {a: 3};
        var y = x;
        """);
    Node root = getLastCompiler().getRoot();
    Node scriptNode =
        root.getLastChild() // ROOT of input sources
            .getLastChild();

    // `const {...x} = {a: 3};`
    Node constNode = scriptNode.getFirstChild();

    // `{...x}`
    Node objectPattern =
        constNode
            .getFirstChild() // DESTRUCTURING_LHS
            .getFirstChild();

    // `x`
    Node constName =
        objectPattern
            .getFirstChild() // OBJECT_REST
            .getFirstChild();
    assertThat(constName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    // `var y = x`
    Node lastStatement = scriptNode.getLastChild();
    // `y = x`
    Node yVar = lastStatement.getOnlyChild();
    Node secondNameNodeOfX = yVar.getFirstChild();
    assertThat(secondNameNodeOfX.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  @SuppressWarnings("RhinoNodeGetFirstFirstChild") // to allow adding separate comments per-child
  public void testRestConstAnnotationPropagation_onlyConstVars() {
    testSame(
        """
        const obj = {a: 3, b: 'string', c: null};
        const {...rest} = obj;
        const y = rest;
        """);
    Node root = getLastCompiler().getRoot();
    Node scriptNode =
        root.getLastChild() // ROOT of input sources
            .getLastChild();
    Node firstStatement = scriptNode.getFirstChild();

    // `obj` from `const obj = {a: 3, b: 'string', c: null}`
    Node objName = firstStatement.getFirstChild();
    assertThat(objName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    // `const {...rest} = obj`
    Node constNode = firstStatement.getNext();

    // `{...rest} = obj
    Node destructuringNode = constNode.getFirstChild();

    // `obj` from previous comment
    Node secondObjName = destructuringNode.getLastChild();
    assertThat(secondObjName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    // `{...rest}
    Node objectPattern = destructuringNode.getFirstChild();

    // `rest`
    Node constName =
        objectPattern
            .getFirstChild() // OBJECT_REST
            .getFirstChild();
    assertThat(constName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    // `const y = rest`
    Node secondConstNode = constNode.getNext();

    // `y = rest`
    Node yVar = secondConstNode.getOnlyChild();
    assertThat(yVar.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node secondConstName = yVar.getFirstChild();
    assertThat(secondConstName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testConstRHSPropagation() {
    testSame("const obj = function inner() {inner();};");
    Node root = getLastCompiler().getRoot();
    Node scriptNode =
        root.getLastChild() // ROOT of input sources
            .getLastChild();
    Node firstStatement = scriptNode.getFirstChild();

    // `obj` from `const obj = function inner() {inner();};`
    Node objName = firstStatement.getFirstChild();
    assertThat(objName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node functionName = objName.getOnlyChild().getFirstChild();
    assertThat(functionName.isName()).isTrue();
    assertThat(functionName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testConstRHSPropagation2() {
    testSame("/** @const */ var obj = function inner() {inner();};");
    Node root = getLastCompiler().getRoot();
    Node scriptNode =
        root.getLastChild() // ROOT of input sources
            .getLastChild();
    Node firstStatement = scriptNode.getFirstChild();

    // `obj` from `var obj = function inner() {inner();};`
    Node objName = firstStatement.getFirstChild();
    assertThat(objName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();

    Node functionName = objName.getOnlyChild().getFirstChild();
    assertThat(functionName.isName()).isTrue();
    assertThat(functionName.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testNullishCoalesce() {
    test("var a = x ?? y, b = foo()", "var a = x ?? y; var b = foo()");
    test(
        """
        let x = a ?? b;
        { let x = a ?? b; }
        { let x = a ?? b; }
        { let x = a ?? b; }
        { let x = a ?? b; }
        """,
        """
        let x = a ?? b;
        { let x$jscomp$1 = a ?? b; }
        { let x$jscomp$2 = a ?? b; }
        { let x$jscomp$3 = a ?? b; }
        { let x$jscomp$4 = a ?? b; }
        """);
  }

  @Test
  public void testSplitVar() {
    testSame("var a");
    test("var a, b", "var a; var b");
    test("var a, b, c", "var a; var b; var c");
    testSame("var a = 0 ");
    test("var a = 0 , b = foo()", "var a = 0; var b = foo()");
    test("var a = 0, b = 1, c = 2", "var a = 0; var b = 1; var c = 2");
    test(
        "var a = foo(1), b = foo(2), c = foo(3)", "var a = foo(1); var b = foo(2); var c = foo(3)");

    test(
        "try{var b = foo(1), c = foo(2);} finally { foo(3) }",
        "try{var b = foo(1); var c = foo(2)} finally { foo(3); }");
    test(
        "try{var b = foo(1),c = foo(2);} finally {}",
        "try{var b = foo(1); var c = foo(2)} finally {}");
    test(
        "try{foo(0);} finally { var b = foo(1), c = foo(2); }",
        "try{foo(0);} finally {var b = foo(1); var c = foo(2)}");

    test(
        "switch(a) {default: var b = foo(1), c = foo(2); break;}",
        "switch(a) {default: var b = foo(1); var c = foo(2); break;}");

    test("do var a = foo(1), b; while(false);", "do{var a = foo(1); var b} while(false);");
    test("a:var a,b,c;", "a:{ var a;var b; var c; }");
    test("if (true) a:var a,b;", "if (true)a:{ var a; var b; }");
  }

  @Test
  public void testSplitVar_forLoop() {
    // Verify vars extracted from FOR nodes are split.
    test(
        "for(var a = 0, b = foo(1), c = 1; c < b; c++) foo(2)",
        "var a = 0; var b = foo(1); var c = 1; for(; c < b; c++) foo(2)");

    // Verify split vars properly introduce blocks when needed.
    test("for(;;) var b = foo(1), c = foo(2);", "for(;;){var b = foo(1); var c = foo(2)}");
    test("for(;;){var b = foo(1), c = foo(2);}", "for(;;){var b = foo(1); var c = foo(2)}");

    test("a:for(var a,b,c;;);", "var a;var b; var c;a:for(;;);");
  }

  @Test
  public void testSplitVar_forLoopCrash() {
    // Verify b/174247914
    test(
        """
        for (let j;;);
        var i;
        for(var i=0;;);
        """,
        """
        for (let j;;);
        var i;
        i = 0;
        for(;;);
        """);
  }

  @Test
  public void testSplitLet() {
    testSame("let a");
    test("let a, b", "let a; let b");
    test("let a, b, c", "let a; let b; let c");
    testSame("let a = 0 ");
    test("let a = 0 , b = foo()", "let a = 0; let b = foo()");
    test("let a = 0, b = 1, c = 2", "let a = 0; let b = 1; let c = 2");
    test(
        "let a = foo(1), b = foo(2), c = foo(3)", "let a = foo(1); let b = foo(2); let c = foo(3)");
    testSame("for (let a = 0, b = 1;;) {}");
  }

  @Test
  public void testLetManyBlocks() {
    test(
        """
        let a = 'outer';
        { let a = 'inner1'; }
        { let a = 'inner2'; }
        { let a = 'inner3'; }
        { let a = 'inner4'; }
        """,
        """
        let a = 'outer';
        { let a$jscomp$1 = 'inner1'; }
        { let a$jscomp$2 = 'inner2'; }
        { let a$jscomp$3 = 'inner3'; }
        { let a$jscomp$4 = 'inner4'; }
        """);
  }

  @Test
  public void testLetOutsideAndInsideForLoop() {
    test(
        """
        let a = 'outer';
        for (let a = 'inner';;) {
          break;
        }
        alert(a);
        """,
        """
        let a = 'outer';
        for (let a$jscomp$1 = 'inner';;) {
          break;
        }
        alert(a);
        """);
  }

  @Test
  public void testLetOutsideAndInsideBlock() {
    test(
        """
        let a = 'outer';
        {
          let a = 'inner';
        }
        alert(a);
        """,
        """
        let a = 'outer';
        {
          let a$jscomp$1 = 'inner';
        }
        alert(a);
        """);
  }

  @Test
  public void testLetOutsideAndInsideFn() {
    test(
        """
        let a = 'outer';
        function f() {
          let a = 'inner';
        }
        alert(a);
        """,
        """
        let a = 'outer';
        function f() {
          let a$jscomp$1 = 'inner';
        }
        alert(a);
        """);
  }

  @Test
  public void testRemoveEmptiesFromClass() {
    test(
        """
        class Foo {
          m1() {};
          m2() {};
        }
        """,
        """
        class Foo {
          m1() {}
          m2() {}
        }
        """);
  }

  @Test
  public void testClassField() {
    test(
        """
        /** @unrestricted */
        class Foo {
          f1;
          ['f2'] = 1;
          static f3;
          static 'f4' = 'hi';
        }
        """,
        """
        class Foo {
          f1
          ['f2'] = 1
          static f3
          static 'f4' = 'hi'
        }
        """);
  }

  @Test
  public void testClassStaticBlock() {
    test(
        """
        var x;
        class Foo {
          static {
            var x;
            let y;
            this.x;
          }
          static {
            var x;
            let y;
          }
        }
        class Bar {
          static {
            var x;
            let y;
            this.x;
          }
        }
        """,
        """
        var x;
        class Foo {
          static {
            var x$jscomp$1;
            let y;
            this.x;
          }
          static {
            var x$jscomp$2;
            let y$jscomp$1;
          }
        }
        class Bar {
          static {
            var x$jscomp$3;
            let y$jscomp$2;
            this.x;
          }
        }
        """);
  }

  @Test
  public void testClassStaticBlock_innerFunctionHoisted() {
    test(
        """
        var x;
        class Foo {
          static {
            this.x;
            function f1() {}
          }
          static {
            let y;
            function f2() {}
          }
        }
        class Bar {
          static {
            var z;
            function f3() {}
          }
        }
        """,
        """
        var x;
        class Foo {
          static {
            function f1() {}
            this.x;
          }
          static {
            function f2() {}
            let y;
          }
        }
        class Bar {
          static {
            function f3() {}
            var z;
          }
        }
        """);
  }

  @Test
  public void testClassInForLoop() {
    testSame("for (class a {};;) { break; }");
  }

  @Test
  public void testFunctionInForLoop() {
    testSame("for (function a() {};;) { break; }");
  }

  @Test
  public void testLetInGlobalHoistScope() {
    testSame(
        """
        if (true) {
          let x = 1; alert(x);
        }
        """);

    test(
        """
        if (true) {
          let x = 1; alert(x);
        } else {
          let x = 1; alert(x);
        }
        """,
        """
        if (true) {
          let x = 1; alert(x);
        } else {
          let x$jscomp$1 = 1; alert(x$jscomp$1);
        }
        """);
  }

  @Test
  public void testConstInGlobalHoistScope() {
    testSame(
        """
        if (true) {
          const x = 1; alert(x);
        }
        """);

    test(
        """
        if (true) {
          const x = 1; alert(x);
        } else {
          const x = 1; alert(x);
        }
        """,
        """
        if (true) {
          const x = 1; alert(x);
        } else {
          const x$jscomp$1 = 1; alert(x$jscomp$1);
        }
        """);
  }

  @Test
  public void testVarReferencedInHoistedFunction() {
    test(
        """
        var f1 = function() {
          var x;
        };

        (function () {
          {
            var x = 0;
          }
          function f2() {
            alert(x);
          }
          f2();
        })();
        """,
        """
        var f1 = function() {
          var x;
        };

        (function () {
          function f2() {
            alert(x$jscomp$1);
          }
          {
            var x$jscomp$1 = 0;
          }
          f2();
        })();
        """);
  }

  @Test
  public void testFunctionDeclInBlockScope() {
    testSame("var x; { function g() {} }");
    test("var g; { function g() {} }", "var g; { function g$jscomp$1() {} }");

    testSameInFunction("var x; { function g() {} }");
    testInFunction("var g; { function g() {} }", "var g; { function g$jscomp$1() {} }");
  }

  @Test
  public void testAssignShorthand() {
    test("x |= 1;", "x = x | 1;");
    test("x ^= 1;", "x = x ^ 1;");
    test("x &= 1;", "x = x & 1;");
    test("x <<= 1;", "x = x << 1;");
    test("x >>= 1;", "x = x >> 1;");
    test("x >>>= 1;", "x = x >>> 1;");
    test("x += 1;", "x = x + 1;");
    test("x -= 1;", "x = x - 1;");
    test("x *= 1;", "x = x * 1;");
    test("x /= 1;", "x = x / 1;");
    test("x %= 1;", "x = x % 1;");

    test("/** @suppress {const} */ x += 1;", "x = x + 1;");
  }

  @Test
  public void testAssignShorthandDontNormalizeWhenLHSNotName() {
    testSame("obj.x += 1;");
  }

  @Test
  public void testLogicalAssignShorthand() {
    test("x ||= 1;", "x || (x = 1);");
    test("x &&= 1;", "x && (x = 1);");
    test("x ??= 1;", "x ?? (x = 1);");
  }

  @Test
  public void testLogicalAssignPropertyReferenceShorthand() {
    test(
        srcs("a.x ||= b"),
        expected(
            """
            let $jscomp$logical$assign$tmpm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = a).x
               ||
            ($jscomp$logical$assign$tmpm1146332801$0.x = b);
            """));
    test(
        srcs("a.foo &&= null"),
        expected(
            """
            let $jscomp$logical$assign$tmpm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = a).foo
               &&
            ($jscomp$logical$assign$tmpm1146332801$0.foo = null);
            """));
    test(
        srcs(
            """
            foo().x = null;
            foo().x ??= y
            """),
        expected(
            """
            foo().x = null;
            let $jscomp$logical$assign$tmpm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = foo()).x
               ??
            ($jscomp$logical$assign$tmpm1146332801$0.x = y);
            """));
  }

  @Test
  public void testLogicalAssignPropertyReferenceElementShorthand() {
    test(
        srcs("a[x] ||= b"),
        expected(
            """
            let $jscomp$logical$assign$tmpm1146332801$0;
            let $jscomp$logical$assign$tmpindexm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = a)
            [$jscomp$logical$assign$tmpindexm1146332801$0 = x]
               ||
            ($jscomp$logical$assign$tmpm1146332801$0
            [$jscomp$logical$assign$tmpindexm1146332801$0] = b);
            """));
    test(
        srcs("a[x + 5 + 's'] &&= b"),
        expected(
            """
            let $jscomp$logical$assign$tmpm1146332801$0;
            let $jscomp$logical$assign$tmpindexm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = a)
            [$jscomp$logical$assign$tmpindexm1146332801$0 = (x + 5 + 's')]
               &&
            ($jscomp$logical$assign$tmpm1146332801$0
            [$jscomp$logical$assign$tmpindexm1146332801$0] = b);
            """));
    test(
        srcs("foo[x] ??= bar[y]"),
        expected(
            """
            let $jscomp$logical$assign$tmpm1146332801$0;
            let $jscomp$logical$assign$tmpindexm1146332801$0;
            ($jscomp$logical$assign$tmpm1146332801$0 = foo)
            [$jscomp$logical$assign$tmpindexm1146332801$0 = x]
               ??
            ($jscomp$logical$assign$tmpm1146332801$0
            [$jscomp$logical$assign$tmpindexm1146332801$0] = bar[y]);
            """));
  }

  @Test
  public void testLogicalAssignmentNestedName() {
    test(
        srcs("a ||= (b &&= (c ??= d));"), //
        expected("a || (a = (b && (b = (c ?? (c = d)))));"));
  }

  @Test
  public void logicalAssignmentNestedPropertyReference() {
    test(
        """
        const foo = {}, bar = {};
        foo.x ||= (foo.y &&= (bar.z ??= 'something'));
        """,
        """
        const foo = {}; const bar = {};
        let $jscomp$logical$assign$tmpm1146332801$0;
        let $jscomp$logical$assign$tmpm1146332801$1;
        let $jscomp$logical$assign$tmpm1146332801$2;
        ($jscomp$logical$assign$tmpm1146332801$2 = foo).x
           ||
        ($jscomp$logical$assign$tmpm1146332801$2.x
         = ($jscomp$logical$assign$tmpm1146332801$1 = foo).y
           &&
        ($jscomp$logical$assign$tmpm1146332801$1.y
         = ($jscomp$logical$assign$tmpm1146332801$0 = bar).z
           ?? ($jscomp$logical$assign$tmpm1146332801$0.z = 'something')));
        """);
  }

  @Test
  public void testDuplicateVarInExterns() {
    test(
        externs("var extern;"),
        srcs("/** @suppress {duplicate} */ var extern = 3;"),
        expected("var extern = 3;"));
  }

  @Test
  public void testUnhandled() {
    testSame("var x = y = 1");
  }

  @Test
  public void testFor() {
    // Verify assignments are extracted from the FOR init node.
    test("for(a = 0; a < 2 ; a++) foo();", "a = 0; for(; a < 2 ; a++) foo()");
    // Verify vars are extracted from the FOR init node.
    test("for(var a = 0; c < b ; c++) foo()", "var a = 0; for(; c < b ; c++) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for(var a = 0; c < b ; c++) foo()", "var a = 0; a:for(; c < b ; c++) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a:b:for(var a = 0; c < b ; c++) foo()", "var a = 0; a:b:for(; c < b ; c++) foo()");

    // Verify block are properly introduced for ifs.
    test("if(x) for(var a = 0; c < b ; c++) foo()", "if(x){var a = 0; for(; c < b ; c++) foo()}");

    // Any other expression.
    test("for(init(); a < 2 ; a++) foo();", "init(); for(; a < 2 ; a++) foo()");

    // Verify destructuring var declarations are extracted.
    test(
        "for (var [a, b] = [1, 2]; a < 2; a = b++) foo();",
        "var a; var b; [a, b] = [1, 2]; for (; a < 2; a = b++) foo();");
  }

  @Test
  public void testForIn1() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);

    // Verify nothing happens with simple for-in
    testSame("for(a in b) foo();");

    // Verify vars are extracted from the FOR-IN node.
    test("for(var a in b) foo()", "var a; for(a in b) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for(var a in b) foo()", "var a; a:for(a in b) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a:b:for(var a in b) foo()", "var a; a:b:for(a in b) foo()");

    // Verify block are properly introduced for ifs.
    test("if (x) for(var a in b) foo()", "if (x) { var a; for(a in b) foo() }");

    // Verify names in destructuring declarations are individually declared.
    test(
        externs(new TestExternsBuilder().addIterable().addString().build()),
        srcs("for (var [a, b] in c) foo();"),
        expected("var a; var b; for ([a, b] in c) foo();"));

    test("for (var {a, b} in c) foo();", "var a; var b; for ({a: a, b: b} in c) foo();");
  }

  @Test
  public void testForIn2() {
    setExpectParseWarningsInThisTest();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    // Verify vars are extracted from the FOR-IN node.
    test("for(var a = foo() in b) foo()", "var a = foo(); for(a in b) foo()");
  }

  @Test
  public void testForOf() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    // Verify nothing happens with simple for-of
    testSame("for (a of b) foo();");

    // Verify vars are extracted from the FOR-OF node.
    test("for (var a of b) foo()", "var a; for (a of b) foo()");

    // Verify vars are extracted from the FOR init before the label node.
    test("a:for (var a of b) foo()", "var a; a: for (a of b) foo()");
    // Verify vars are extracted from the FOR init before the labels node.
    test("a: b: for (var a of b) foo()", "var a; a: b: for (a of b) foo()");

    // Verify block are properly introduced for ifs.
    test("if (x) for (var a of b) foo()", "if (x) { var a; for (a of b) foo() }");

    // Verify names in destructuring declarations are individually declared.
    test("for (var [a, b] of c) foo();", "var a; var b; for ([a, b] of c) foo();");

    test("for (var {a, b} of c) foo();", "var a; var b; for ({a: a, b: b} of c) foo();");
  }

  @Test
  public void testForAwaitOf() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    // Verify nothing happens with simple for-await-of
    testSame("async () => { for await (a of b) foo(); }");

    // Verify vars are extracted from the FOR-AWAIT-OF node.
    test(
        "async () => { for await (var a of b) foo() }",
        "async () => { var a; for await (a of b) foo() }");

    // Verify vars are extracted from the FOR init before the label node.
    test(
        "async () => { a:for await (var a of b) foo() }",
        "async () => { var a; a: for await (a of b) foo() }");
    // Verify vars are extracted from the FOR init before the labels node.
    test(
        "async () => { a: b: for await (var a of b) foo() }",
        "async () => { var a; a: b: for await (a of b) foo() }");

    // Verify block are properly introduced for ifs.
    test(
        "async () => { if (x) for await (var a of b) foo() }",
        "async () => { if (x) { var a; for await (a of b) foo() } }");

    // Verify names in destructuring declarations are individually declared.
    test(
        "async () => { for await (var [a, b] of c) foo(); }",
        "async () => { var a; var b; for await ([a, b] of c) foo(); }");

    test(
        "async () => { for await (var {a, b} of c) foo(); }",
        "async () => { var a; var b; for await ({a: a, b: b} of c) foo(); }");
  }

  @Test
  public void testWhile() {
    // Verify while loops are converted to FOR loops.
    test("while(c < b) foo()", "for(; c < b;) foo()");
  }

  @Test
  public void testMoveFunctions1() {
    test(
        "function f() { if (x) return; foo(); function foo() {} }",
        "function f() {function foo() {} if (x) return; foo(); }");
    test(
        """
        function f() {
          function foo() {}
          if (x) return;
          foo();
          function bar() {}
        }
        """,
        """
        function f() {
          function foo() {}
          function bar() {}
          if (x) return;
          foo();
        }
        """);
  }

  @Test
  public void testMoveFunctions2() {
    testSame("function f() { function foo() {} }");
    testSame("function f() { f(); {function bar() {}}}");
    testSame("function f() { f(); if (true) {function bar() {}}}");
  }

  private static String inFunction(String code) {
    return "(function(){" + code + "})";
  }

  private void testSameInFunction(String code) {
    testSame(inFunction(code));
  }

  private void testInFunction(String code, String expected) {
    test(inFunction(code), inFunction(expected));
  }

  @Test
  public void testNormalizeFunctionDeclarations() {
    testSame("function f() {}");
    testSame("function f() {}; var g = f;");
    testSame("var f = function () {}");
    test("var f = function f() {}", "var f = function f$jscomp$1() {}");
    testSame("var f = function g() {}");
    testSame("var f = function(){}; var g = f;");
    testSame("{function g() {}}");
    testSame("if (function g() {}) {}");
    testSame("if (true) {function g() {}}");
    testSame("if (true) {} else {function g() {}}");
    testSame("switch (function g() {}) {}");
    testSame("switch (1) { case 1: function g() {}}");
    testSame("if (true) {function g() {} function h() {}}");

    testSameInFunction("function f() {}");
    testSameInFunction("f(); {function g() {}}");
    testSameInFunction("f(); if (true) {function g() {}}");
    testSameInFunction("if (true) {} else {function g() {}}");
  }

  @Test
  public void testMakeLocalNamesUnique() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);
    disableCompareJsDoc();

    // Verify global names are untouched.
    testSame("var a;");

    // Verify global names are untouched.
    testSame("a;");

    // Local names are made unique.
    test("var a;function foo(a){var b;a}", "var a;function foo(a$jscomp$1){var b;a$jscomp$1}");
    test(
        "var a;function foo(){var b;a}function boo(){var b;a}",
        "var a;function foo(){var b;a}function boo(){var b$jscomp$1;a}");
    test(
        "function foo(a){var b} function boo(a){var b}",
        "function foo(a){var b} function boo(a$jscomp$1){var b$jscomp$1}");

    // Verify function expressions are renamed.
    test(
        "var a = function foo(){foo()};var b = function foo(){foo()};",
        "var a = function foo(){foo()};var b = function foo$jscomp$1(){foo$jscomp$1()};");

    // Verify catch exceptions names are made unique
    testSame("try { } catch(e) {e;}");
    test(
        "try { } catch(e) {e;}; try { } catch(e) {e;}",
        "try { } catch(e) {e;}; try { } catch(e$jscomp$1) {e$jscomp$1;}");
    test(
        "try { } catch(e) {e; try { } catch(e) {e;}};",
        "try { } catch(e) {e; try { } catch(e$jscomp$1) {e$jscomp$1;} }; ");

    // Verify the 1st global redefinition of extern definition is not removed.
    testSame("/** @suppress {duplicate} */ var window;");

    // Verify the 2nd global redefinition of extern definition is removed.
    test(
        "/** @suppress {duplicate} */ var window; /** @suppress {duplicate} */ var window;",
        "/** @suppress {duplicate} */ var window;");

    // Verify local masking extern made unique.
    test("function f() {var window}", "function f() {var window$jscomp$1}");

    // Verify import * as <alias> is renamed.
    test(
        srcs("let a = 5;", "import * as a from './a.js'; const TAU = 2 * a.PI;"),
        expected(
            "let a = 5;", "import * as a$jscomp$1 from './a.js'; const TAU = 2 * a$jscomp$1.PI"));

    // Verify exported and imported names are untouched.
    test(
        srcs("var a;", "let a; export {a as a};"),
        expected("var a;", "let a$jscomp$1; export {a$jscomp$1 as a};"));

    // Verify same local names in 2 different files get different new names
    test(
        srcs(
            "function foo() {var a; a;}",
            "function bar() {let a; let a$jscomp$1; a + a$jscomp$1;}"),
        expected(
            "function foo() {var a; a;}",
            """
            function bar() {let a$jscomp$1; let a$jscomp$1$jscomp$1; a$jscomp$1 +
             a$jscomp$1$jscomp$1;}
            """));

    test(
        srcs("var a;", "import {a as a} from './foo.js'; let b = a;"),
        expected("var a;", "import {a as a$jscomp$1} from './foo.js'; let b = a$jscomp$1;"));
  }

  @Test
  public void testMakeParamNamesUnique() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test(
        "function f(x) { x; }\nfunction g(x) { x; }",
        "function f(x) { x; }\nfunction g(x$jscomp$1) { x$jscomp$1; }");

    test(
        "function f(x) { x; }\nfunction g(...x) { x; }",
        "function f(x) { x; }\nfunction g(...x$jscomp$1) { x$jscomp$1; }");

    test(
        "function f(x) { x; }\nfunction g({x: x}) { x; }",
        "function f(x) { x; }\nfunction g({x: x$jscomp$1}) { x$jscomp$1; }");

    test(
        "function f(x) { x; }\nfunction g({x}) { x; }",
        "function f(x) { x; }\nfunction g({x: x$jscomp$1}) { x$jscomp$1; }");

    test(
        "function f(x) { x; }\nfunction g({y: {x}}) { x; }",
        "function f(x) { x; }\nfunction g({y: {x: x$jscomp$1}}) { x$jscomp$1; }");
  }

  @Test
  public void testNormalize_createsUniqueNamesInFunctionBody_avoidsShadowingWithDefaultParams() {
    test(
        """
        function x() {}
        var y = 1;
        function f(z=x, w=y) {
          let x = y;
          var y = 3;
          return w;
        }
        """,
        """
        function x() {}
        var y = 1;
        function f(z=x, w=y) {
          let x$jscomp$1 = y$jscomp$1;
          var y$jscomp$1 = 3;
          return w;
        }
        """);
  }

  @Test
  public void testNoRenameParamNames() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);
    testSame("function f(x) { x; }");

    testSame("function f(...x) { x; }");

    testSame("function f({x: x}) { x; }");

    test("function f({x}) { x; }", "function f({x: x}) { x; }");

    test("function f({y: {x}}) { x; }", "function f({y: {x: x}}) { x; }");
  }

  @Test
  public void testRemoveDuplicateVarDeclarations_destructuringArrayDeclaration() {
    test(
        "function f() { var a = 3; var [a] = [4]; }", //
        "function f() { var a = 3; [a]= [4]; }");
  }

  @Test
  public void testRemoveDuplicateVarDeclarations_destructuringObjectDeclaration() {
    test(
        "function f() { var a = 3; var {a} = {a: 4}; }", //
        "function f() { var a = 3; ({a}= {a: 4});}");
  }

  @Test
  public void testRemoveDuplicateVarDeclarations1() {
    test("function f() { var a; var a }", "function f() { var a; }");

    test("function f() { var a = 1; var a = 2 }", "function f() { var a = 1; a = 2 }");
    // second declaration not assigned to any rhs, empty reference removed
    test("function f() { var a = 1; var a; }", "function f() { var a = 1;}");

    // second declaration is a destructuring declaration; converted to assignment
    test("function f() { var a = 1; var [a] = [2]; }", "function f() { var a = 1; [a]= [2]; }");

    // this should be an error in the parser but isn't
    test("function f() { var a = 1; const a =2; }", "function f() { var a = 1; const a = 2 }");
    test("var a = 1; function f(){ var a = 2 }", "var a = 1; function f(){ var a$jscomp$1 = 2 }");
    test(
        "function f() { var a = 1; label1:var a = 2 }",
        "function f() { var a = 1; label1:{a = 2}}");
    test("function f() { var a = 1; label1:var a }", "function f() { var a = 1; label1:{} }");
    test("function f() { var a = 1; for(var a in b); }", "function f() { var a = 1; for(a in b);}");
  }

  @Test
  public void testRemoveDuplicateVarDeclarations2() {
    test(
        "var e = 1; function f(){ try {} catch (e) {} var e = 2 }",
        "var e = 1; function f(){ try {} catch (e$jscomp$2) {} var e$jscomp$1 = 2 }");
  }

  @Test
  public void testRemoveDuplicateVarDeclarations3() {
    ignoreWarnings(
        TypeCheck.FUNCTION_MASKS_VARIABLE,
        TypeValidator.TYPE_MISMATCH_WARNING,
        TypeValidator.DUP_VAR_DECLARATION);
    test("var f = 1; function f(){}", "f = 1; function f(){}");
    test("var f; function f(){}", "function f(){}");

    test("function f(){} var f = 1;", "function f(){} f = 1;");
    test("function f(){} var f;", "function f(){}");

    // TODO(johnlenz): Do we need to handle this differently for "third_party"
    // mode? Remove the previous function definitions?
    testSame("function f(){} function f(){}");
  }

  // It's important that we not remove this var completely. See
  // http://blickly.github.io/closure-compiler-issues/#290
  @Test
  public void testRemoveDuplicateVarDeclarations4() {
    disableCompareJsDoc();
    testSame("if (!Arguments) { /** @suppress {duplicate} */ var Arguments = {}; }");
  }

  // If there are multiple duplicates, it's okay to remove all but the first.
  @Test
  public void testRemoveDuplicateVarDeclarations5() {
    test("var Arguments = {}; var Arguments = {};", "var Arguments = {}; Arguments = {};");
  }

  @Test
  public void testRemoveVarDeclarationDuplicatesParam1() {
    test(
        "function f(x) { alert(x); var x = 0; alert(x); }",
        "function f(x) { alert(x);     x = 0; alert(x); }");
  }

  @Test
  public void testRemoveVarDeclarationDuplicatesParam2() {
    test(
        "function f(x) { alert(x); var x; alert(x); }",
        "function f(x) { alert(x);        alert(x); }");
  }

  @Test
  public void testRenamingConstants() {
    testSame("var ACONST = 4; var b = ACONST;");

    test("var a, ACONST = 4;var b = ACONST;", "var a; var ACONST = 4; var b = ACONST;");

    testSame("var ACONST; ACONST = 4; var b = ACONST;");

    testSame("var ACONST = new Foo(); var b = ACONST;");

    testSame("/** @const */ var aa; aa = 1;");
  }

  @Test
  public void testSkipRenamingExterns() {
    test(
        externs("var EXTERN; var ext; ext.FOO;"),
        srcs("var b = EXTERN; var c = ext.FOO"),
        expected("var b = EXTERN; var c = ext.FOO"));
  }

  @Test
  public void testIssue166e() {
    test(
        "var e = 2; try { throw 1 } catch(e) {}",
        "var e = 2; try { throw 1 } catch(e$jscomp$1) {}");
  }

  @Test
  public void testIssue166f() {
    test(
        """
        function a() {
          var e = 2;
          try { throw 1 } catch(e) {}
        }
        """,
        """
        function a() {
          var e = 2;
          try { throw 1 } catch(e$jscomp$1) {}
        }
        """);
  }

  @Test
  public void testIssue166g() {
    test(
        """
        function a() {
          try { throw 1 } catch(e) {}
          var e = 2;
        }
        """,
        """
        function a() {
          try { throw 1 } catch(e$jscomp$1) {}
          var e = 2;
        }
        """);
  }

  @Test
  public void testLetsInSeparateBlocks() {
    test(
        """
        if (x) {
          let e;
          alert(e);
        }
        if (y) {
          let e;
          alert(e);
        }
        """,
        """
        if (x) {
          let e;
          alert(e);
        }
        if (y) {
          let e$jscomp$1;
          alert(e$jscomp$1);
        }
        """);
  }

  @Test
  public void testCatchesInSeparateBlocks() {
    test(
        """
        if (x) {
          try {
            throw 1;
          } catch (e) {
            alert(e);
          }
        }
        if (y) {
          try {
            throw 2;
          } catch (e) {
            alert(e);
          }
        }
        """,
        """
        if (x) {
          try {
            throw 1;
          } catch (e) {
            alert(e);
          }
        }
        if (y) {
          try {
            throw 2;
          } catch (e$jscomp$1) {
            alert(e$jscomp$1);
          }
        }
        """);
  }

  @Test
  public void testDeclInCatchBlock() {
    test(
        """
        var x;
        try {
        } catch (e) {
          let x;
        }
        """,
        """
        var x;
        try {
        } catch (e) {
          let x$jscomp$1
        }
        """);
  }

  @Test
  public void testIssue() {
    allowExternsChanges();
    test(externs("var a,b,c; var a,b"), srcs("a(), b()"), expected("a(), b()"));
  }

  @Test
  public void testNormalizeSyntheticCode() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    compiler.init(new ArrayList<SourceFile>(), new ArrayList<SourceFile>(), options);
    String code = "function f(x) {} function g(x) {}";
    Node ast = compiler.parseSyntheticCode("testNormalizeSyntheticCode", code);
    Normalize.normalizeSyntheticCode(compiler, ast, "prefix_");
    assertThat(compiler.toSource(ast))
        .isEqualTo("function f(x$jscomp$prefix_0){}function g(x$jscomp$prefix_1){}");
  }

  @Test
  public void testIsConstant() {
    testSame("var CONST = 3; var b = CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertThat(hasProp.getString()).isEqualTo("CONST");
    }
  }

  // Test that the name nodes of function expressions are unconditionally marked as const even if
  // the LHS declaration is a var
  @Test
  public void testRHSFunctionExpressionNameNodeIsConstant() {
    testSame("var someNonConstVar = function foo() {};");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(1);
    assertThat(constantNodes.iterator().next().getString()).isEqualTo("foo");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const if the LHS
  // is a const
  @Test
  public void testRHSFunctionExpressionNameNodeIsConstant1() {
    testSame("const someConstVar = function foo() {};");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2); // {someConstVar, foo}
    Iterator<Node> itr = constantNodes.iterator();
    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("someConstVar");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const if the LHS
  // is a const
  @Test
  public void testRHSFunctionExpressionNameNodeIsConstant2() {
    testSame("const someConstVar = function foo() { foo(); };");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(3); // {someConstVar, foo, foo}
    Iterator<Node> itr = constantNodes.iterator();
    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("someConstVar");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const
  // irrespective of whether or not the LHS is a const
  @Test
  public void testRHSFunctionExpressionNameNodeIsConstant3() {
    testSame(
        """
        const someConstVar = function foo() { foo(); };
        // this call to something undefined but having the same name foo is not marked const
        foo();
        """);
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(3); // {someConstVar, foo, foo}
    Iterator<Node> itr = constantNodes.iterator();

    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("someConstVar");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const
  // irrespective of whether or not the LHS is a const
  @Test
  public void testRHSFunctionExpressionNameNodeIsConstant4() {
    testSame(
        """
        let someNonConstVar = function foo() { foo(); };
        // this call to something undefined but having the same name foo is not marked const
        foo();
        """);
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2); // {foo, foo}
    Iterator<Node> itr = constantNodes.iterator();

    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("foo");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const
  // irrespective of whether or not the LHS is a const
  @Test
  public void testFunctionExpressionNameNodeIsConstant() {
    testSame("use(function foo(i) { foo(i-1);});");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2); // {foo, foo}
    Iterator<Node> itr = constantNodes.iterator();

    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("foo");
  }

  // Test that the name nodes of function expressions are unconditionally marked as const
  // irrespective of whether or not the LHS is a const
  @Test
  public void testFunctionExpressionNameNodeIsConstant2() {
    testSame(
        """
        use(function foo(i) { foo(i-1);});
        // this call to something undefined but having the same name foo is not marked const
        foo();
        """);
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2); // {foo, foo}
    Iterator<Node> itr = constantNodes.iterator();

    assertThat(itr.next().getString()).isEqualTo("foo");
    assertThat(itr.next().getString()).isEqualTo("foo");
  }

  @Test
  public void testIsConstantByDestructuring() {
    test(
        "const {CONST} = {CONST:3}; let b = CONST;",
        "const {CONST: CONST} = {CONST:3}; let b = CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertThat(hasProp.getString()).isEqualTo("CONST");
    }
  }

  @Test
  public void testIsConstantByDestructuringWithDefault() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);

    test("const {CONST = 3} = {}; var b = CONST;", "const {CONST: CONST = 3} = {}; var b = CONST;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(2);
    for (Node hasProp : constantNodes) {
      assertThat(hasProp.getString()).isEqualTo("CONST");
    }
  }

  @Test
  public void testPropertyIsConstantIfMatchesConstantName() {
    // verify that the /** @const */ 'other' doesn't accidentally cause the string key in
    // {'other: 4'} to be marked const
    testSame("var a = {other: 4}; /** @const */ var other = 5;");
    Node n = getLastCompiler().getRoot();

    Set<Node> constantNodes = findNodesWithProperty(n, NormalizeTest::isConstantName);
    assertThat(constantNodes).hasSize(1);
    for (Node hasProp : constantNodes) {
      assertThat(hasProp.getString()).isEqualTo("other");
    }
  }

  @Test
  public void testShadowFunctionName() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test(
        """
        function f() {
          var f = 'test';
          console.log(f);
        }
        """,
        """
        function f() {
          var f$jscomp$1 = 'test';
          console.log(f$jscomp$1);
        }
        """);
  }

  private static boolean isConstantName(Node n) {
    return n.getBooleanProp(Node.IS_CONSTANT_NAME);
  }

  private Set<Node> findNodesWithProperty(Node root, Predicate<Node> prop) {
    final Set<Node> set = new LinkedHashSet<>();

    NodeTraversal.builder()
        .setCompiler(getLastCompiler())
        .setCallback(
            (NodeTraversal t, Node node, Node parent) -> {
              if (prop.test(node)) {
                set.add(node);
              }
            })
        .traverse(root);
    return set;
  }

  @Test
  public void testRenamingConstantProperties() throws Exception {
    // In order to detect that foo.BAR is a constant, we need collapse
    // properties to run first so that we can tell if the initial value is
    // non-null and immutable. The Normalize pass doesn't modify the code
    // in these examples, it just infers const-ness of some variables, so
    // we call enableNormalize to make the Normalize.VerifyConstants pass run.

    // TODO(johnlenz): fix this so it is just another test case.
    CompilerTestCase tester =
        new CompilerTestCase() {
          @Override
          protected int getNumRepetitions() {
            // The normalize pass is only run once.
            return 1;
          }

          @Override
          protected CompilerPass getProcessor(Compiler compiler) {
            return InlineAndCollapseProperties.builder(compiler)
                .setPropertyCollapseLevel(PropertyCollapseLevel.ALL)
                .setChunkOutputType(ChunkOutputType.GLOBAL_NAMESPACE)
                .setHaveModulesBeenRewritten(false)
                .setModuleResolutionMode(ResolutionMode.BROWSER)
                .build();
          }
        };

    tester.setUp();
    tester.enableNormalize();

    tester.test(
        "var a={}; a.ACONST = 4;var b = 1; b = a.ACONST;",
        "var a$ACONST = 4; var b = 1; b = a$ACONST;");

    tester.test(
        "var a={b:{}}; a.b.ACONST = 4;var b = 1; b = a.b.ACONST;",
        "var a$b$ACONST = 4;var b = 1; b = a$b$ACONST;");

    tester.test("var a = {FOO: 1};var b = 1; b = a.FOO;", "var a$FOO = 1; var b = 1; b = a$FOO;");

    tester.testSame(
        externs("var EXTERN; var ext; ext.FOO;"), srcs("var b = EXTERN; var c = ext.FOO"));

    tester.test(
        "var a={}; a.ACONST = 4; var b = 1; b = a.ACONST;",
        "var a$ACONST = 4; var b = 1; b = a$ACONST;");

    tester.test(
        "var a = {}; function foo() { var d = a.CONST; }; (function(){a.CONST=4})();",
        "var a$CONST;function foo(){var d = a$CONST;}; (function(){a$CONST = 4})();");

    tester.test(
        "var a = {}; a.ACONST = new Foo(); var b = 1; b = a.ACONST;",
        "var a$ACONST = new Foo(); var b = 1; b = a$ACONST;");

    tester.tearDown();
  }

  @Test
  public void testFunctionBlock1() {
    test("() => 1;", "() => { return 1; }");
  }

  @Test
  public void testFunctionBlock2() {
    test("var args = 1; var foo = () => args;", "var args = 1; var foo = () => { return args; }");
  }

  @Test
  public void testBlocklessArrowFunction_withinArgs_getBlocks() {
    // disable type checking to prevent `property map never defined` errors.
    disableTypeCheck();
    disableTypeInfoValidation();
    test(
        """
        function sortAndConcatParams(params) { // arrow fn body missing block
          return [...params].map(((k) => `k`));}
        """,
        """
        function sortAndConcatParams(params) { // gets block {}
          return [...params].map(((k) => { return `k`; }));}
        """);
  }

  @Test
  public void testArrowFunctionInFunction() {
    test(
        """
        function foo() {
          var x = () => 1;
          return x();
        }
        """,
        """
        function foo() {
          var x = () => { return 1; };
          return x();
        }
        """);
  }

  @Test
  public void testES6ShorthandPropertySyntax01() {
    test("obj = {x, y};", "obj = {x: x, y: y}");
  }

  @Test
  public void testES6ShorthandPropertySyntax02() {
    test("var foo = {x, y};", "var foo = {x: x, y: y}");
  }

  @Test
  public void testES6ShorthandPropertySyntax03() {
    test(
        """
        function foo(a, b, c) {
          return {
            a,
            b,
            c
          };
        }
        """,
        """
        function foo(a, b, c) {
          return {
            a: a,
            b: b,
            c: c
          };
        }
        """);
  }

  @Test
  public void testES6ShorthandPropertySyntax04() {
    test("var foo = {x};", "var foo = {x: x}");
  }

  @Test
  public void testES6ShorthandPropertySyntax05() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test("var {a = 5} = obj;", "var a; ({a = 5} = obj);");
  }

  @Test
  public void testES6ShorthandPropertySyntax06() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test("var {a = 5, b = 3} = obj;", "var a; var b; ({a = 5, b = 3} = obj);");
  }

  @Test
  public void testES6ShorthandPropertySyntax07() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test("var {a: a = 5, b = 3} = obj;", "var a; var b; ({a: a = 5, b: b = 3} = obj);");
  }

  @Test
  public void testES6ShorthandPropertySyntax08() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test("var {a, b} = obj;", "var a; var b; ({a, b} = obj);");
  }

  @Test
  public void testES6ShorthandPropertySyntax09() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    test("({a = 5} = obj);", "({a: a = 5} = obj);");
  }

  @Test
  public void testES6ShorthandPropertySyntax10() {
    testSame("function f(a = 5) {}");
  }

  @Test
  public void testES6ShorthandPropertySyntax11() {
    testSame("[a = 5] = obj;");
  }

  @Test
  public void testES6ShorthandPropertySyntax12() {
    ignoreWarnings(DiagnosticGroups.GLOBALLY_MISSING_PROPERTIES);

    testSame("({a: a = 5} = obj)");
  }

  @Test
  public void testES6ShorthandPropertySyntax13() {
    testSame("({['a']: a = 5} = obj);");
  }

  @Test
  public void testRewriteExportSpecShorthand1() {
    test("var a; export {a};", "var a; export {a as a};");
  }

  @Test
  public void testRewriteExportSpecShorthand2() {
    test(
        "let a, b, d; export {a, b as c, d};",
        "let a; let b; let d;export {a as a, b as c, d as d};");
  }

  @Test
  public void testSplitExportDeclarationWithVar() {
    test("export var a;", "var a; export {a as a};");
    test("export var a = 4;", "var a = 4; export {a as a};");
    test(
        "export var a, b;",
        """
        var a;
        var b;
        export {a as a, b as b};
        """);
  }

  @Test
  public void testSplitExportDeclarationWithShorthandProperty() {
    test("export var a = {b};", "var a = {b: b}; export {a as a};");
  }

  @Test
  public void testSplitExportDeclarationWithDestructuring() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);

    test("export var {} = {};", "({} = {}); export {};");
    test(
        """
        let obj = {a: 3, b: 2};
        export var {a, b: d, e: f = 2} = obj;
        """,
        """
        let obj = {a: 3, b: 2};
        var a; var d; var f;
        ({a: a, b: d, e: f = 2} = obj);
        export {a as a, d as d, f as f};
        """);
  }

  @Test
  public void testSplitExportDeclarationWithLet() {
    test("export let a;", "let a; export {a as a};");
  }

  @Test
  public void testSplitExportDeclarationWithConst() {
    test("export const a = 17;", "const a = 17; export {a as a};");
  }

  @Test
  public void testSplitExportDeclarationOfFunction() {
    test(
        "export function bar() {};",
        """
        function bar() {}
        export {bar as bar};
        """);

    // Don't need to split declarations in default exports since they are either unnamed, or the
    // name is declared in the module scope only.
    testSame("export default function() {};");
    testSame("export default function foo() {};");
  }

  @Test
  public void testSplitExportDeclarationOfClass() {
    test(
        "export class Foo {};",
        """
        class Foo {}
        export {Foo as Foo};
        """);
    testSame("export default class Bar {}");
    testSame("export default class {}");
  }
}
