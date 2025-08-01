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

import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Inline function tests. */
@RunWith(JUnit4.class)
public class InlineFunctionsTest extends CompilerTestCase {
  private Reach inliningReach;
  private boolean assumeStrictThis;
  private boolean assumeMinimumCapture;
  private int maxSizeAfterInlining;

  static final String EXTERNS = "/** @nosideeffects */ function nochg(){}\nfunction chg(){}\n";

  public InlineFunctionsTest() {
    super(EXTERNS);
  }

  // Overridden by one test method that needs to disable this.
  void maybeEnableInferConsts() {
    enableInferConsts();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    maybeEnableInferConsts();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    enableComputeSideEffects();
    inliningReach = Reach.ALL;
    assumeStrictThis = false;
    assumeMinimumCapture = false;
    maxSizeAfterInlining = CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    compiler.resetUniqueNameId();

    return new InlineFunctions(
        compiler,
        compiler.getUniqueNameIdSupplier(),
        inliningReach,
        assumeStrictThis,
        assumeMinimumCapture,
        maxSizeAfterInlining);
  }

  /** Returns the number of times the pass should be run before results are verified. */
  @Override
  protected int getNumRepetitions() {
    // Some inlining can only be done in multiple passes.
    return 3;
  }

  @Test
  public void testNoInline() {
    testSame("/** @noinline */ function foo(){} foo();foo();foo();");
    testSame("/** @noinline */ var foo = function(){}; foo();foo();foo();");
    testSame("/** @noinline */ var foo = function(){}; foo?.();foo?.();foo?.();");
  }

  @Test
  public void testRequireInlining() {
    test(
        """
        /** @requireInlining */ function foo() {console.log('hi');console.log('bye');}
        foo();foo();foo();
        """,
        "{console.log('hi');console.log('bye');}".repeat(3));
  }

  @Test
  public void testEncourageInliningCascades() {
    disableCompareAsTree();
    test(
        """
        const a = class {constructor() {} setA(value) {this.a = value;} setB(value) {this.b =\
         value;}}; /** @requireInlining */ function fromRecord(rec) { return new\
         a().setA(rec.a).setB(rec.b); }; console.log(fromRecord({a: 1, b: fromRecord({a: 2,\
         b: fromRecord({a: 4, b: 5}) }) }) );\
        """,
        """
        const\
         a=class{constructor(){}setA(value){this.a=value}setB(value$jscomp$1){this.b=value$jscomp$1}};var\
         JSCompiler_temp_const$jscomp$1=console;var\
         JSCompiler_temp_const$jscomp$0=JSCompiler_temp_const$jscomp$1.log;var\
         JSCompiler_inline_result$jscomp$2;{var\
         rec$jscomp$inline_5={a:4,b:5};JSCompiler_inline_result$jscomp$2=(new\
         a).setA(rec$jscomp$inline_5.a).setB(rec$jscomp$inline_5.b)}var\
         JSCompiler_inline_result$jscomp$3;
        {var rec$jscomp$inline_7={a:2,b:JSCompiler_inline_result$jscomp$2};JSCompiler_inline_result$jscomp$3=(new\
         a).setA(rec$jscomp$inline_7.a).setB(rec$jscomp$inline_7.b)}var\
         JSCompiler_inline_result$jscomp$4;{var\
         rec$jscomp$inline_9={a:1,b:JSCompiler_inline_result$jscomp$3};JSCompiler_inline_result$jscomp$4=(new\
         a).setA(rec$jscomp$inline_9.a).setB(rec$jscomp$inline_9.b)}JSCompiler_temp_const$jscomp$0.call(\
        JSCompiler_temp_const$jscomp$1,JSCompiler_inline_result$jscomp$4)\
        """);
  }

  @Test
  public void testNullableFunctionsNotInlined() {
    testSame("var foo = null; if(x) { foo = function(){}; }; foo();foo();foo();");
    testSame("var foo = null; if(x) { foo = function(){}; }; foo?.();foo?.();foo?.();");

    // Even when non-null, we don't inline if there are multiple definitions of a function (need
    // Dataflow Analysis for that).
    testSame(
        """
        var foo = function() {return 1;}; if(x) { foo = function(){}; };
         foo?.();foo?.();foo?.();
        """);
  }

  @Test
  public void testInlinedAtRegularAndOptChainCallsBoth() {
    // both regular and optional references are found and inlined
    test("function foo() {return 1;} foo(); foo?.();", "1;1;");
  }

  @Test
  public void testQualifiedNamesNotInlined() {
    testSame(
        """
        var a = { b :  function() {return 0;} };
        var c = a.b();
        """);

    testSame(
        """
        var a = { b :  function() {return 0;} };
        var c = a.b?.();
        """);

    testSame(
        """
        var a = { b :  function() {return 0;} };
        var c = a?.b();
        """);
  }

  @Test
  public void testInlineEmptyFunction1() {
    // Empty function, no params.
    test(
        """
        function foo(){}
        foo();
        """,
        "void 0;");
  }

  @Test
  public void testInlineEmptyFunction1_optChain() {
    // Empty function, no params.
    test("function foo(){} foo?.();", "void 0;");
  }

  @Test
  public void testInline_optChain_1() {
    // Empty function, no params.
    test("(function(){}).call?.(this)", "void 0;");
    test("(function(){})?.call(this)", "void 0;");
  }

  @Test
  public void testInlineEmptyFunction2() {
    // Empty function, params with no side-effects.
    test("function foo(){}\n foo(1, new Date, function(){});", "void 0;");
  }

  @Test
  public void testInlineEmptyFunction2_optChain() {
    test("function foo(){}\n foo?.(1, new Date, function(){});", "void 0;");
  }

  @Test
  public void testInlineEmptyFunction3() {
    // Empty function, multiple references.
    test("function foo(){}\n foo();foo();foo();", "void 0;void 0;void 0");
  }

  @Test
  public void testInlineEmptyFunction4() {
    // Empty function, params with side-effects forces block inlining.
    test("function foo(){}\n foo(x());", "{var JSCompiler_inline_anon_param_0 = x();}");
  }

  @Test
  public void testInlineEmptyFunction4_optChainCall() {
    // Empty function, params with side-effects forces block inlining.
    test(
        "function foo(){}\n foo?.(x());",
        """
        var JSCompiler_inline_result$jscomp$0;
        {
          var JSCompiler_inline_anon_param_1 = x();
          JSCompiler_inline_result$jscomp$0 = void 0;
        }
        JSCompiler_inline_result$jscomp$0;
        """);
  }

  @Test
  public void testInlineEmptyFunction6() {
    test("if (window) { f(); function f() {} }", "if (window) { void 0; }");
  }

  @Test
  public void testInlineEmptyFunction6_optChain() {
    test("if (window) { f?.(); function f() {} }", "if (window) { void 0; }");
  }

  @Test
  public void testInlineFunctions1() {
    // As simple a test as we can get.
    test(
        """
        function foo(){ return 4 }
        foo();
        """,
        "4");
  }

  @Test
  public void testInlineFunctions1_optChain() {
    // As simple a test as we can get.
    test(
        """
        function foo(){ return 4 }
        foo?.();
        """,
        "4");
  }

  @Test
  public void testInlineFunctions1_arrowFn() {
    test(
        """
        const foo = () => 4;
        foo();
        """,
        "4");
  }

  @Test
  public void testInlineFunctions1_arrowFn_block() {
    test(
        """
        const foo = () => { return 4; };
        foo();
        """,
        "4");
  }

  @Test
  public void testInlineFunctions1_inArrowFn() {
    test(
        """
        const foo = () => 4;
        (() => foo())();
        """,
        "4");
  }

  @Test
  public void testInlineFunctions1_inArrowFn_block() {
    test(
        """
        const foo = () => 4;
        (() => {
          let a = foo();
          [].forEach(() => a += 1);
          return a;
        })();
        """,
        // Why is the IIFE not inlined here?
        // This happens whether or not the function references
        // a local.
        """
        (() => {
          let a = 4;
          [].forEach(() => {
            return a = a + 1;
          });
          return a;
        })();
        """);
  }

  @Test
  public void testInlineFunctions1_arrowFnArg() {
    test(
        """
        const bar = (f) => f();
        bar(() => 4);
        """,
        "4");
  }

  @Test
  public void testInlineFunctions2() {
    // inline simple constants
    // NOTE: CD is not inlined.
    test(
        """
        var t;var AB=function(){return 4};
        function BC(){return 6;}
        CD=function(x){return x + 5};x=CD(3);y=AB();z=BC();
        """,
        "var t;CD=function(x){return x+5};x=CD(3);y=4;z=6");
  }

  // calls present in the args list of an optional chaining call get inlined.
  @Test
  public void testInline_simpleConstantsUnderOptionalChaining() {
    // inline simple constants
    test(
        """
        var AB=function(){return 4};
        function BC(x){ return x;}
        y=BC?.(AB());
        """,
        "y = 4;");
  }

  @Test
  public void testInlineFunctions3() {
    // inline simple constants
    test(
        """
        var t;var AB=function(){return 4};
        function BC(){return 6;}
        var CD=function(x){return x + 5};x=CD(3);y=AB();z=BC();
        """,
        "var t;x=3+5;y=4;z=6");
  }

  @Test
  public void testInlineFunctions3_optChain() {
    // inline simple constants
    test(
        """
        var t;var AB=function(){return 4};
        function BC(){return 6;}
        var CD=function(x){return x + 5};x=CD?.(3);y=AB?.();z=BC?.();
        """,
        "var t;x=3+5;y=4;z=6");
  }

  @Test
  public void testInlineFunctions4() {
    // don't inline if there are multiple definitions (need DFA for that).
    test(
        """
        var t; var AB = function() { return 4 };
        function BC() { return 6; }
        CD = 0;
        CD = function(x) { return x + 5 }; x = CD(3); y = AB(); z = BC();
        """,
        "var t;CD=0;CD=function(x){return x+5};x=CD(3);y=4;z=6");
  }

  @Test
  public void testInlineFunctions4_optChain() {
    // don't inline if there are multiple definitions (need DFA for that).
    test(
        """
        var t; var AB = function() { return 4 };
        function BC() { return 6; }
        CD = 0;
        CD = function(x) { return x + 5 }; x = CD?.(3); y = AB?.(); z = BC?.();
        """,
        "var t;CD=0;CD=function(x){return x+5};x=CD?.(3);y=4;z=6");
  }

  @Test
  public void testInlineFunctions5() {
    // inline additions
    test(
        """
        var FOO_FN=function(x,y) { return "de" + x + "nu" + y };
        var a = FOO_FN("ez", "ts")
        """,
        "var a=\"de\"+\"ez\"+\"nu\"+\"ts\"");
  }

  @Test
  public void testInlineFunctions5_optChain() {
    // inline additions
    test(
        """
        var FOO_FN=function(x,y) { return "de" + x + "nu" + y };
        var a = FOO_FN?.("ez", "ts")
        """,
        "var a=\"de\"+\"ez\"+\"nu\"+\"ts\"");
  }

  @Test
  public void testInlineFunctions6() {
    // more complex inlines
    test(
        """
        function BAR_FN(x, y, z) { return z(nochg(x + y)) }
        alert(BAR_FN(1, 2, baz))
        """,
        "alert(baz(nochg(1+2)))");
  }

  @Test
  public void testInlineFunctions6_optChain() {
    // more complex inlines
    test(
        """
        function BAR_FN(x, y, z) { return z?.(nochg?.(x + y)) }
        alert(BAR_FN?.(1, 2, baz))
        """,
        "alert(baz?.(nochg?.(1+2)))");
  }

  @Test
  public void testInlineFunctions7() {
    // inlines appearing multiple times
    test(
        """
        function FN(x,y,z){return x+x+y}
        var b=FN(1,2,3)
        """,
        "var b=1+1+2");
  }

  @Test
  public void testInlineFunctions7_optChain() {
    // inlines appearing multiple times
    test(
        """
        function FN(x,y,z){return x+x+y}
        var b=FN?.(1,2,3)
        """,
        "var b=1+1+2");
  }

  @Test
  public void testInlineFunctions8() {
    // check correct parenthesization
    test(
        """
        function MUL(x,y){return x*y}function ADD(x,y){return x+y}
        var a=1+MUL(2,3);var b=2*ADD(3,4)
        """,
        "var a=1+2*3;var b=2*(3+4)");
  }

  @Test
  public void testInlineFunctions9() {
    // don't inline if the input parameter is modified.
    test(
        """
        function INC(x){return x++}
        var y=INC(i)
        """,
        """
        var y;{var x$jscomp$inline_0=i;
        y=x$jscomp$inline_0++}
        """);
  }

  @Test
  public void testInlineFunctions9_optChain() {
    // don't inline if the input parameter is modified.
    test(
        "function INC(x){return x++} var y=INC?.(i)",
        "var y;{var x$jscomp$inline_0=i; y=x$jscomp$inline_0++}");
  }

  @Test
  public void testInlineFunctions10() {
    test(
        """
        function INC(x){return x++}
        var y = INC(i);
        y = INC(i);
        """,
        """
        var y;
        {
          var x$jscomp$inline_0 = i;
          y = x$jscomp$inline_0++;
        }
        {
          var x$jscomp$inline_2 = i;
          y = x$jscomp$inline_2++;
        }
        """);
  }

  @Test
  public void testInlineFunctions11() {
    test(
        """
        function f(x){return x}
        var y=f(i)
        """,
        "var y=i");
  }

  @Test
  public void testInlineFunctions13() {
    // inline as block if the input parameter has side-effects.
    test(
        """
        function f(x){return x}
        var y=f(i++)
        """,
        // TODO: b/407603216 - Inline using DIRECT mode when return expressions don't have
        // references that can be side-effected. The output code can be `var y=i++`
        """
        var y = i++;
        """);
  }

  @Test
  public void testInlineFunctions13a() {
    // inline as block if the input parameter has side-effects.
    test(
        """
        function f(x){return random() || x}
        var y=f(i++)
        """,
        "var y;{var x$jscomp$inline_0=i++;y=random() || x$jscomp$inline_0}");
  }

  @Test
  public void testInlineFunctions14() {
    // don't remove functions that are referenced on other ways
    test(
        """
        function FOO(x){return x}
        var BAR=function(y){return y};
        b=FOO;a(BAR);x=FOO(1);y=BAR(2)
        """,
        """
        function FOO(x){return x}
        var BAR=function(y){return y};
        b=FOO;a(BAR);x=1;y=2
        """);
  }

  @Test
  public void testInlineFunctions15a() {
    // closure factories: do inline into global scope.
    test(
        """
        function foo(){return function(a){return a+1}}
        var b=function(){return c};
        var d=b()+foo()
        """,
        "var d=c+function(a){return a+1}");
  }

  @Test
  public void testInlineFunctions15b() {
    assumeMinimumCapture = false;

    // closure factories: don't inline closure with locals into global scope.
    test(
        """
        function foo(){var x; return function(a){ return a+1; };}
        var b = function() { return c; };
        var d = b() + foo()
        """,
        "function foo() { var x; return function(a){return a+1}; } var d = c+foo();");

    assumeMinimumCapture = true;

    test(
        """
        function foo() { var x; return function(a) { return a + 1; }; }
        var b = function() { return c; };
        var d = b() + foo()
        """,
        """
        var JSCompiler_inline_result$jscomp$0;
        {
          var x$jscomp$inline_1;
          JSCompiler_inline_result$jscomp$0 = function(a$jscomp$inline_2) {
            return a$jscomp$inline_2 + 1;
          };
        }
        var d = c+JSCompiler_inline_result$jscomp$0;
        """);
  }

  @Test
  public void testInlineFunctions15c() {
    assumeMinimumCapture = false;

    // closure factories: don't inline into non-global scope.
    test(
        """
        function foo(){return function(a){return a+1}}
        var b=function(){return c};
        function _x(){ var d=b()+foo() }
        """,
        """
        function foo(){return function(a){return a+1}}
        function _x(){ var d=c+foo() }
        """);

    assumeMinimumCapture = true;

    // closure factories: don't inline into non-global scope.
    test(
        """
        function foo(){return function(a){return a+1}}
        var b=function(){return c};
        function _x(){ var d=b()+foo() }
        """,
        "function _x(){var d=c+function(a){return a+1}}");
  }

  @Test
  public void testInlineFunctions15d() {
    assumeMinimumCapture = false;

    // closure factories: don't inline functions with vars.
    test(
        """
        function foo() { var x; return function(a) { return a+1; }; }
        var b = function() { return c; };
        function _x(){ var d = b()+foo() }
        """,
        """
        function foo() {
          var x;
          return function(a){ return a+1; };
        }
        function _x() { var d = c + foo(); }
        """);

    assumeMinimumCapture = true;

    // closure factories: inline functions with vars.
    test(
        """
        function foo(){var x; return function(a){return a+1}}
        var b = function(){return c};
        function _x(){ var d=b()+foo() }
        """,
        """
        function _x() {
          var JSCompiler_inline_result$jscomp$0;
          {
            var x$jscomp$inline_1;
            JSCompiler_inline_result$jscomp$0 = function(a$jscomp$inline_2) {
              return a$jscomp$inline_2+1
            };
          }
          var d = c + JSCompiler_inline_result$jscomp$0;
        }
        """);
  }

  @Test
  public void testInlineFunctions16a() {
    assumeMinimumCapture = false;

    testSame("function foo(b){return window.bar(function(){c(b)})} var d=foo(e)");

    assumeMinimumCapture = true;

    test(
        """
        function foo(b){return window.bar(function(){c(b)})}
        var d=foo(e)
        """,
        """
        var d;{var b$jscomp$inline_0=e;
        d=window.bar(function(){c(b$jscomp$inline_0)})}
        """);
  }

  @Test
  public void testInlineFunctions16b() {
    test(
        """
        function foo(){return window.bar(function(){c()})}
        var d=foo(e)
        """,
        "var d=window.bar(function(){c()})");
  }

  @Test
  public void testInlineFunctions17() {
    // don't inline recursive functions
    testSame("function foo(x){return x*x+foo(3)}var bar=foo(4)");
  }

  @Test
  public void testInlineFunctions19() {
    // TRICKY ... test nested inlines
    // with block inlining possible
    test(
        """
        function foo(a, b){return a+b}
        function bar(d){return c}
        var d=foo(bar(1),e)
        """,
        "var d=c+e;");
  }

  @Test
  public void testInlineFunctions19_optChain() {
    // TRICKY ... test nested inlines
    // with block inlining possible
    test(
        """
        function foo(a, b){return a+b}
        function bar(d){return c}
        var d=foo?.(bar?.(1),e)
        """,
        "var d=c+e;");
  }

  @Test
  public void testInlineFunctions21() {
    // with block inlining possible
    test(
        """
        function foo(a, b){return a+b}
        function bar(d){return c}
        var d=bar(foo(1,e))
        """,
        "var d=c");
  }

  @Test
  public void testInlineFunctions22() {
    // Another tricky case ... test nested compiler inlines
    test(
        """
        function plex(a){if(a) return 0;else return 1;}
        function foo(a, b){return bar(a+b)}
        function bar(d){return plex(d)}
        var d=foo(1,2)
        """,
        """
        var d;
        {
          JSCompiler_inline_label_plex_1:{
            if(1+2) {
              d = 0;
              break JSCompiler_inline_label_plex_1;
            } else {
              d = 1;
              break JSCompiler_inline_label_plex_1;
            }
            d=void 0;
          }
        }
        """);
  }

  @Test
  public void testInlineFunctions23() {
    // Test both orderings again
    test(
        """
        function complex(a){if(a) return 0;else return 1;}
        function bar(d){return complex(d)}
        function foo(a, b){return bar(a+b)}
        var d=foo(1,2)
        """,
        """
        var d;
        {
          JSCompiler_inline_label_complex_1:{
            if (1+2) {
              d=0;
              break JSCompiler_inline_label_complex_1;
            } else {
              d = 1;
              break JSCompiler_inline_label_complex_1;
            }
            d=void 0
          }
        }
        """);
  }

  @Test
  public void testInlineFunctions24() {
    // Don't inline functions with 'arguments' or 'this'
    testSame("function foo(x){return this}foo(1)");
  }

  @Test
  public void testInlineFunctions26() {
    // Don't inline external functions
    testSame("function _foo(x){return x}_foo(1)");
  }

  @Test
  public void testInlineFunctions27() {
    test(
        """
        var window = {}; function foo(){window.bar++; return 3;}
        var x = {y: 1, z: foo(2)};
        """,
        """
        var window={};
        var JSCompiler_inline_result$jscomp$0;
        {
          window.bar++;
          JSCompiler_inline_result$jscomp$0 = 3;
        }
        var x = {y: 1, z: JSCompiler_inline_result$jscomp$0};
        """);
  }

  @Test
  public void testInlineFunctions28() {
    test(
        """
        var window = {}; function foo(){window.bar++; return 3;}
        var x = {y: alert(), z: foo(2)};
        """,
        """
        var window = {};
        var JSCompiler_temp_const$jscomp$0 = alert();
        var JSCompiler_inline_result$jscomp$1;
        {
         window.bar++;
         JSCompiler_inline_result$jscomp$1 = 3;}
        var x = {
          y: JSCompiler_temp_const$jscomp$0,
          z: JSCompiler_inline_result$jscomp$1
        };
        """);
  }

  @Test
  public void testInlineFunctions29() {
    test(
        """
        var window = {}; function foo(){window.bar++; return 3;}
        var x = {a: alert(), b: alert2(), c: foo(2)};
        """,
        """
        var window = {};
        var JSCompiler_temp_const$jscomp$1 = alert();
        var JSCompiler_temp_const$jscomp$0 = alert2();
        var JSCompiler_inline_result$jscomp$2;
        {
         window.bar++;
         JSCompiler_inline_result$jscomp$2 = 3;}
        var x = {
          a: JSCompiler_temp_const$jscomp$1,
          b: JSCompiler_temp_const$jscomp$0,
          c: JSCompiler_inline_result$jscomp$2
        };
        """);
  }

  @Test
  public void testInlineFunctions30() {
    // As simple a test as we can get.
    testSame("function foo(){ return eval() } foo();");
  }

  @Test
  public void testInlineFunctions31() {
    // Don't introduce a duplicate label in the same scope
    test("function foo(){ lab:{4;} } lab:{foo();}", "lab:{{JSCompiler_inline_label_0:{4}}}");
  }

  @Test
  public void testInlineFunctions32() {
    test("let f = function() { return 5; }; f();", "5;");
  }

  @Test
  public void testInlineFunctions33() {
    test("const f = function() { return 5; }; f();", "5;");
  }

  @Test
  public void testInlineFunctions34() {
    test(
        """
        class X {}
        (function(e) {
          for (var el = f(e.target); el != null; el = el.parent) {}
          function f(x) { return x instanceof X ? x : null; }
        })({target:{}});
        """,
        """
        class X {}
        {
          var e$jscomp$inline_0 = {target:{}};
          var el$jscomp$inline_1;
          {
            var x$jscomp$inline_2 = e$jscomp$inline_0.target;
            el$jscomp$inline_1 = x$jscomp$inline_2 instanceof X ? x$jscomp$inline_2 : null;
          }
          for(;el$jscomp$inline_1 != null; el$jscomp$inline_1 = el$jscomp$inline_1.parent);
        }
        """);
  }

  @Test
  public void testInlineFunctions34_optChain() {
    test(
        """
        class X {}
        (function(e) {
          for (var el = f?.(e.target); el != null; el = el.parent) {}
          function f(x) { return x instanceof X ? x : null; }
        })({target:{}});
        """,
        """
        class X {}
        {
          var e$jscomp$inline_0 = {target:{}};
          var el$jscomp$inline_1;
          {
            var x$jscomp$inline_2 = e$jscomp$inline_0.target;
            el$jscomp$inline_1 = x$jscomp$inline_2 instanceof X ? x$jscomp$inline_2 : null;
          }
          for(;el$jscomp$inline_1 != null; el$jscomp$inline_1 = el$jscomp$inline_1.parent);
        }
        """);
  }

  // Same as above, except the for loop uses a let instead of a var. See b/73373371.
  @Test
  public void testInlineFunctions35() {
    test(
        """
        class X {}
        (function(e) {
          for (let el = f(e.target); el != null; el = el.parent) {}
          function f(x) { return x instanceof X ? x : null; }
        })({target:{}});
        """,
        """
        class X {}
        {
          var e$jscomp$inline_0 = {target: {}};
          var JSCompiler_inline_result$jscomp$inline_1;
          {
            var x$jscomp$inline_2 = e$jscomp$inline_0.target;
            JSCompiler_inline_result$jscomp$inline_1 =
                x$jscomp$inline_2 instanceof X ? x$jscomp$inline_2 : null
          }
          for (let el$jscomp$inline_3 = JSCompiler_inline_result$jscomp$inline_1;
               el$jscomp$inline_3 != null;
               el$jscomp$inline_3 = el$jscomp$inline_3.parent) {}
        }
        """);
  }

  @Test
  public void testInlineFunctions35_optChain() {
    test(
        """
        class X {}
        (function(e) {
        // The for loop uses a `let` instead of a var.
          for (let el = f?.(e.target); el != null; el = el.parent) {}
          function f(x) { return x instanceof X ? x : null; }
        })({target:{}});
        """,
        """
        class X {}
        {
          var e$jscomp$inline_0 = {target: {}};
          var JSCompiler_inline_result$jscomp$inline_1;
          {
            var x$jscomp$inline_2 = e$jscomp$inline_0.target;
            JSCompiler_inline_result$jscomp$inline_1 =
                x$jscomp$inline_2 instanceof X ? x$jscomp$inline_2 : null
          }
          for (let el$jscomp$inline_3 = JSCompiler_inline_result$jscomp$inline_1;
               el$jscomp$inline_3 != null;
               el$jscomp$inline_3 = el$jscomp$inline_3.parent) {}
        }
        """);
  }

  @Test
  public void testDontInlineFunctionsWithDirectArgumentsReferences() {
    testSame("function foo() { return arguments[0]; } foo(1);");
  }

  @Test
  public void testInlineSwitchStatementWithReturns() {
    test(
        """
        function foo(x) {
          switch (x) {
            case 1: return 2;
            case 2: return 3;
            default: return 4;
          }
        }
        function bar() {
          const f = foo(x);
          console.log(f);
        }
        bar();
        """,
        """
        {
          var JSCompiler_inline_result$jscomp$0;
              {
                JSCompiler_inline_label_foo_2: {
                  switch(x) {
                    case 1:
                      JSCompiler_inline_result$jscomp$0 = 2;
                      break JSCompiler_inline_label_foo_2;
                    case 2:
                      JSCompiler_inline_result$jscomp$0 = 3;
                      break JSCompiler_inline_label_foo_2;
                    default:
                      JSCompiler_inline_result$jscomp$0 = 4;
                      break JSCompiler_inline_label_foo_2;
                  }
                  JSCompiler_inline_result$jscomp$0 = void 0;
                }
              }
          const f$jscomp$inline_0 = JSCompiler_inline_result$jscomp$0;
          console.log(f$jscomp$inline_0);
        }
        """);
  }

  @Test
  public void testDontInlineFunctionsWithArgumentsReferencesInArrowFunction() {
    testSame("function foo() { return () => arguments[0]; } foo(1);");
  }

  @Test
  public void testDoInlineFunctionsWithArgumentsReferencesInInnerVanillaFunction() {
    test(
        "function foo() { return function() { return arguments[0]; } } foo(1);",
        "(function() { return arguments[0]; })");
  }

  @Test
  public void testMixedModeInlining1() {
    // Base line tests, direct inlining
    test(
        """
        function foo(){return 1}
        foo();
        """,
        "1;");
  }

  @Test
  public void testMixedModeInlining2() {
    // Base line tests, block inlining. Block inlining is needed by possible-side-effect parameter.
    test("function foo(){return 1} foo(x());", "{var JSCompiler_inline_anon_param_0=x();1}");
  }

  @Test
  public void testMixedModeInlining2_optChain() {
    // Base line tests, block inlining. Block inlining is needed by possible-side-effect parameter.
    test(
        "function foo(){return 1} foo?.(x());",
        """
        var JSCompiler_inline_result$jscomp$0;
        {
          var JSCompiler_inline_anon_param_1 = x();
          JSCompiler_inline_result$jscomp$0 = 1;
        }
        JSCompiler_inline_result$jscomp$0;
        """);
  }

  @Test
  public void testMixedModeInlining3() {
    // Inline using both modes.
    test(
        "function foo(){return 1} foo();foo(x());", "1;{var JSCompiler_inline_anon_param_0=x();1}");
  }

  @Test
  public void testMixedModeInlining3_optChain() {
    // Inline using both modes.
    test(
        "function foo(){return 1} foo?.();foo?.(x?.());",
        """
        1;
        var JSCompiler_inline_result$jscomp$0;
        {
          var JSCompiler_inline_anon_param_1 = x?.();
          JSCompiler_inline_result$jscomp$0 = 1;
        }
        JSCompiler_inline_result$jscomp$0;
        """);
  }

  @Test
  public void testMixedModeInlining4() {
    // Inline using both modes. Alternating. Second call of each type has
    // side-effect-less parameter, this is thrown away.
    test(
        "function foo(){return 1} foo(); foo(x()); foo(1);foo(1,x());",
        """
        1;
        {var JSCompiler_inline_anon_param_0=x();1}
        1;
        {var JSCompiler_inline_anon_param_3 = 1; var JSCompiler_inline_anon_param_4=x();1}
        """);
  }

  @Test
  public void testMixedModeInliningCosting1() {
    // Inline using both modes. Costing estimates.

    // Base line.
    test(
        """
        function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}
        foo(1,2);
        foo(2,3)
        """,
        """
        1+2+1+2+4+5+6+7+8+9+1+2+3+4+5;
        2+3+2+3+4+5+6+7+8+9+1+2+3+4+5
        """);
  }

  @Test
  public void testMixedModeInliningCosting2() {
    // Don't inline here because the function definition can not be eliminated.
    // TODO(johnlenz): Should we add constant removing to the unit test?
    testSame(
        """
        function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}
        foo(1,2);
        foo(2,3,x())
        """);
  }

  @Test
  public void testMixedModeInliningCosting3() {
    // Do inline here because the function definition can be eliminated.
    test(
        """
        function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+10}
        foo(1,2);
        foo(2,3,x());
        """,
"""
1+2+1+2+4+5+6+7+8+9+1+2+3+10; // inlining of the foo(1,2); call
{
var a$jscomp$inline_0 = 2; // temp for arg 2
var b$jscomp$inline_1 = 3; // temp for arg 3
  var JSCompiler_inline_anon_param_2 = x();
  a$jscomp$inline_0 + b$jscomp$inline_1 + a$jscomp$inline_0 + b$jscomp$inline_1 + 4 + 5 + 6 + 7 + 8 + 9 + 1 + 2 + 3 + 10;
}
""");
  }

  @Test
  public void testMixedModeInliningCosting4() {
    // Threshold test.
    testSame(
        """
        function foo(a,b){return a+b+a+b+4+5+6+7+8+9+1+2+3+4+101}
        foo(1,2);
        foo(2,3,x())
        """);
  }

  /** See b/72513540 */
  @Test
  public void testDestructuringAssignInFunction() {
    test(
        """
        function foo(a) {
          [a] = [1];
        }
        foo(2);
        """,
        """
        {
          var a$jscomp$inline_0 = 2;
          [a$jscomp$inline_0] = [1];
        }
        """);
  }

  @Test
  public void testDestructuringAssignInFunction_withArrayPattern_doesNotCrash() {
    test(
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), [f] = CN();
          }
          function CN() {
            return [1];
          }
        });
        """,
        "var Di = I(() => {});");
  }

  @Test
  public void testDestructuringAssignInFunction_withObjectPattern_doesNotCrash() {
    test(
        """
        var Di = I(() => {
          function zv() {
            JSCOMPILER_PRESERVE(e), ({f: g} = CN());
          }
          function CN() {
            return {f: 1};
          }
        });
        """,
        "var Di = I(() => {});");
  }

  @Test
  public void testNoInlineIfParametersModified1() {
    // Assignment
    test(
        "function f(x){return x=1}f(undefined)",
        """
        {var x$jscomp$inline_0=undefined;
        x$jscomp$inline_0=1}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified2() {
    test(
        "function f(x){return (x)=1;}f(2)",
        """
        {var x$jscomp$inline_0=2;
        x$jscomp$inline_0=1}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified3() {
    // Assignment variant.
    test(
        "function f(x){return x*=2}f(2)",
        """
        {var x$jscomp$inline_0=2;
        x$jscomp$inline_0*=2}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified4() {
    // Assignment in if.
    test(
        "function f(x){return x?(x=2):0}f(2)",
        """
        {var x$jscomp$inline_0=2;
        x$jscomp$inline_0?(
        x$jscomp$inline_0=2):0}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified5() {
    // Assignment in if, multiple params
    test(
        "function f(x,y){return x?(y=2):0}f(2,undefined)",
        """
        {var x$jscomp$inline_0 = 2; var y$jscomp$inline_1=undefined;
        x$jscomp$inline_0 ? y$jscomp$inline_1 = 2 : 0;}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified6() {
    test(
        "function f(x,y){return x?(y=2):0}f(2)",
        """
        {var x$jscomp$inline_0 = 2; var y$jscomp$inline_1=void 0;
        x$jscomp$inline_0 ? y$jscomp$inline_1 = 2 : 0;}
        """);
  }

  @Test
  public void testNoInlineIfParametersModified7() {
    // Increment
    test(
        "function f(a){return++a<++a}f(1)",
        """
        {var a$jscomp$inline_0=1;
        ++a$jscomp$inline_0<
        ++a$jscomp$inline_0}
        """);
  }

  @Test
  public void testInlineIfParametersModified8() {
    // OK, object parameter modified.
    test("function f(a){return a.x=2}f(o)", "o.x=2");
  }

  @Test
  public void testInlineIfParametersModified9() {
    // OK, array parameter modified.
    test("function f(a){return a[2]=2}f(o)", "o[2]=2");
  }

  @Test
  public void testInlineNeverPartialSubtitution1() {
    test("function f(z){return x.y.z;}f(1)", "x.y.z");
  }

  @Test
  public void testInlineNeverPartialSubtitution2() {
    test("function f(z){return x.y[z];}f(a)", "x.y[a]");
  }

  @Test
  public void testInlineNeverMutateConstants() {
    test(
        "function f(x){return x=1}f(undefined)",
        """
        {var x$jscomp$inline_0=undefined;
        x$jscomp$inline_0=1}
        """);
  }

  @Test
  public void testInlineNeverOverrideNewValues() {
    test(
        "function f(a){return++a<++a}f(1)",
        """
        {var a$jscomp$inline_0=1;
        ++a$jscomp$inline_0<++a$jscomp$inline_0}
        """);
  }

  @Test
  public void testInlineMutableArgsReferencedOnce() {
    test("function foo(x){return x;}foo([])", "[]");
  }

  @Test
  public void testInlineMutableArgsReferencedOnce2() {
    this.assumeMinimumCapture = true;
    // Don't inline a mutable value that will be reused.
    test(
        "function foo(x){return function(){ return x; }} repeat(foo([]))",
        """
        var JSCompiler_inline_result$jscomp$0;
        {
        var x$jscomp$inline_1=[];
        JSCompiler_inline_result$jscomp$0=function(){return x$jscomp$inline_1};
        }
        repeat(JSCompiler_inline_result$jscomp$0)
        """);
  }

  @Test
  public void testInlineMutableArgsReferencedOnce3() {
    this.assumeMinimumCapture = true;
    // Don't inline a mutable value that will be reused.
    test(
        """
        function f(a) {
          for(var i=0; i<0; i++) {
            g(a);
          }
        }
        f([]);
        """,
        """
        {
          var a$jscomp$inline_0 = [];
          var i$jscomp$inline_1 = 0;
          for(; i$jscomp$inline_1 < 0; i$jscomp$inline_1++) {
            g(a$jscomp$inline_0)
          }
        }
        """);
  }

  @Test
  public void testInlineBlockMutableArgs1() {
    test(
        "function foo(x){x+x}foo([])",
        """
        {var x$jscomp$inline_0=[];
        x$jscomp$inline_0+x$jscomp$inline_0}
        """);
  }

  @Test
  public void testInlineBlockMutableArgs2() {
    test(
        "function foo(x){x+x}foo(new Date)",
        """
        {var x$jscomp$inline_0=new Date;
        x$jscomp$inline_0+x$jscomp$inline_0}
        """);
  }

  @Test
  public void testInlineBlockMutableArgs3() {
    test(
        "function foo(x){x+x}foo(true&&new Date)",
        """
        {var x$jscomp$inline_0=true&&new Date;
        x$jscomp$inline_0+x$jscomp$inline_0}
        """);
  }

  @Test
  public void testInlineBlockMutableArgs4() {
    test(
        "function foo(x){x+x}foo({})",
        """
        {var x$jscomp$inline_0={};
        x$jscomp$inline_0+x$jscomp$inline_0}
        """);
  }

  @Test
  public void testShadowVariables1() {
    // The Normalize pass now guarantees that that globals are never shadowed
    // by locals.

    // "foo" is inlined here as its parameter "a" doesn't conflict.
    // "bar" is assigned a new name.
    test(
        """
        var a = 0;
        function foo(a) { return 3+a; }
        function bar() { var a = foo(4); }
        bar();
        """,
        "var a=0; {var a$jscomp$inline_0 = 3+4; }");
  }

  @Test
  public void testShadowVariables2() {
    // "foo" is inlined here as its parameter "a" doesn't conflict.
    // "bar" is inlined as its uses global "a", and does introduce any new
    // globals.
    test(
        """
        var a=0;
        function foo(a){return 3+a}
        function bar(){a=foo(4)}
        bar()
        """,
        """
        var a=0;
        {a=3+4}
        """);
  }

  @Test
  public void testShadowVariables3() {
    // "foo" is inlined into exported "_bar", aliasing foo's "a".
    test(
        """
        var a = 0;
        function foo() { var a = 2; return 3+a; }
        function _bar() { a = foo(); }
        """,
        """
        var a=0;
        function _bar() {
          {
             var a$jscomp$inline_0 = 2;
             a = 3 + a$jscomp$inline_0;
          }
        }
        """);
  }

  @Test
  public void testShadowVariables4() {
    // "foo" is inlined.
    // block access to global "a".
    test(
        """
        var a=0;
        function foo(){return 3+a}
        function _bar(a){a=foo(4)+a}
        """,
        """
        var a=0;function _bar(a$jscomp$1){
        a$jscomp$1=
        3+a+a$jscomp$1}
        """);
  }

  @Test
  public void testShadowVariables6() {
    test(
        """
        var a=0;
        function foo() { var a = 4; return 3+a; }
        function _bar(a) { a = foo(4); }
        """,
        """
        var a=0;
        function _bar(a$jscomp$2) {
          {
            var a$jscomp$inline_0 = 4;
            a$jscomp$2 = 3 + a$jscomp$inline_0;
          }
        }
        """);
  }

  @Test
  public void testShadowVariables7() {
    assumeMinimumCapture = false;
    test(
        """
        var a=3;
        function foo(){return a}
        (function(){var a=5;(function(){foo()})()})()
        """,
        """
        var a=3;
        {var a$jscomp$inline_0=5;{a}}
        """);

    assumeMinimumCapture = true;
    test(
        """
        var a=3;
        function foo(){return a}
        (function(){var a=5;(function(){foo()})()})()
        """,
        """
        var a=3;
        {var a$jscomp$inline_1=5;{a}}
        """);
  }

  @Test
  public void testShadowVariables8() {
    // this should be inlined
    test(
        """
        var a=0;
        function foo(){return 3}
        function _bar(){var a=foo()}
        """,
        """
        var a=0;
        function _bar(){var a=3}
        """);
  }

  @Test
  public void testShadowVariables9() {
    // this should be inlined too [even if the global is not declared]
    test(
        """
        function foo(){return 3}
        function _bar(){var a=foo()}
        """,
        "function _bar(){var a=3}");
  }

  @Test
  public void testShadowVariables10() {
    // callee var must be renamed.
    test(
        """
        var a;function foo(){return a}
        function _bar(){var a=foo()}
        """,
        "var a;function _bar(){var a$jscomp$1=a}");
  }

  @Test
  public void testShadowVariables11() {
    // The call has a local variable
    // which collides with the function being inlined
    test(
        """
        var a=0;var b=1;
        function foo(){return a+a}
        function _bar(){var a=foo();alert(a)}
        """,
        """
        var a=0;var b=1;
        function _bar(){var a$jscomp$1=a+a;
        alert(a$jscomp$1)}
        """);
  }

  @Test
  public void testShadowVariables12() {
    // 2 globals colliding
    test(
        """
        var a=0;var b=1;
        function foo(){return a+b}
        function _bar(){var a=foo(),b;alert(a)}
        """,
        """
        var a=0;var b=1;
        function _bar(){var a$jscomp$1=a+b,
        b$jscomp$1;
        alert(a$jscomp$1)}
        """);
  }

  @Test
  public void testShadowVariables13() {
    // The only change is to remove the collision
    test(
        """
        var a=0;var b=1;
        function foo(){return a+a}
        function _bar(){var c=foo();alert(c)}
        """,
        """
        var a=0;var b=1;
        function _bar(){var c=a+a;alert(c)}
        """);
  }

  @Test
  public void testShadowVariables14() {
    // There is a collision even though it is not read.
    test(
        """
        var a=0;var b=1;
        function foo(){return a+b}
        function _bar(){var c=foo(),b;alert(c)}
        """,
        """
        var a=0;var b=1;
        function _bar(){var c=a+b,
        b$jscomp$1;alert(c)}
        """);
  }

  @Test
  public void testShadowVariables15() {
    // Both parent and child reference a global
    test(
        """
        var a=0;var b=1;
        function foo(){return a+a}
        function _bar(){var c=foo();alert(c+a)}
        """,
        """
        var a=0;var b=1;
        function _bar(){var c=a+a;alert(c+a)}
        """);
  }

  @Test
  public void testShadowVariables16() {
    assumeMinimumCapture = false;
    // Inline functions defined as a child of the CALL node.
    test(
        """
        var a=3;
        function foo(){return a}
        (function(){var a=5;(function(){foo()})()})()
        """,
        """
        var a=3;
        {var a$jscomp$inline_0=5;{a}}
        """);

    assumeMinimumCapture = true;
    // Inline functions defined as a child of the CALL node.
    test(
        """
        var a=3;
        function foo(){return a}
        (function(){var a=5;(function(){foo()})()})()
        """,
        """
        var a=3;
        {var a$jscomp$inline_1=5;{a}}
        """);
  }

  @Test
  public void testShadowVariables17() {
    test(
        """
        var a=0;
        function bar(){return a+a}
        function foo(){return bar()}
        function _goo(){var a=2;var x=foo();}
        """,
        """
        var a=0;
        function _goo(){var a$jscomp$1=2;var x=a+a}
        """);
  }

  @Test
  public void testShadowVariables18() {
    test(
        """
        var a=0;
        function bar() { return a + a; }
        function foo() { var a=3; return bar(); }
        function _goo() { var a=2; var x = foo(); }
        """,
        """
        var a=0;
        function _goo(){
          var a$jscomp$2 = 2;
          var x;
          {
            var a$jscomp$inline_0 = 3;
            x = a + a;
          }
        }
        """);
  }

  @Test
  public void testShadowVariables19() {
    test(
        """
        let a = 0;
        function bar() { return a + a; }
        function foo() { let a = 3; return bar(); }
        function _goo() { let a = 2; let x = foo(); }
        """,
        """
        let a = 0;
        function _goo() {
          let a$jscomp$2 = 2;
          var JSCompiler_inline_result$jscomp$0;
          {
             let a$jscomp$inline_1 = 3;
             JSCompiler_inline_result$jscomp$0 = a + a;
          }
          let x = JSCompiler_inline_result$jscomp$0;
        }
        """);
  }

  @Test
  public void testShadowVariables20() {
    test(
        """
        const a = 0;
        function bar() { return a + a; }
        function foo() { const a = 3; return bar(); }
        function _goo() { const a = 2; const x = foo(); }
        """,
        """
        const a = 0;
        function _goo() {
          const a$jscomp$2 = 2;
          var JSCompiler_inline_result$jscomp$0;
          {
            const a$jscomp$inline_1 = 3;
            JSCompiler_inline_result$jscomp$0 = a + a;
          }
          const x = JSCompiler_inline_result$jscomp$0;
        }
        """);
  }

  @Test
  public void testCostBasedInlining1() {
    testSame(
        """
        function foo(a){return a}
        foo=new Function("return 1");
        foo(1)
        """);
  }

  @Test
  public void testCostBasedInlining2() {
    // Baseline complexity tests.
    // Single call, function not removed.
    test(
        """
        function foo(a){return a}
        var b=foo;
        function _t1(){return foo(1)}
        """,
        """
        function foo(a){return a}
        var b=foo;
        function _t1(){return 1}
        """);
  }

  @Test
  public void testCostBasedInlining3() {
    // Two calls, function not removed.
    test(
        """
        function foo(a,b){return a+b}
        var b=foo;
        function _t1(){return foo(1,2)}
        function _t2(){return foo(2,3)}
        """,
        """
        function foo(a,b){return a+b}
        var b=foo;
        function _t1(){return 1+2}
        function _t2(){return 2+3}
        """);
  }

  @Test
  public void testCostBasedInlining4() {
    // Two calls, function not removed.
    // Here there isn't enough savings to justify inlining.
    testSame(
        """
        function foo(a,b){return a+b+a+b}
        var b=foo;
        function _t1(){return foo(1,2)}
        function _t2(){return foo(2,3)}
        """);
  }

  @Test
  public void testCostBasedInlining5() {
    // Here there is enough savings to justify inlining.
    test(
        """
        function foo(a,b){return a+b+a+b}
        function _t1(){return foo(1,2)}
        function _t2(){return foo(2,3)}
        """,
        """
        function _t1(){return 1+2+1+2}
        function _t2(){return 2+3+2+3}
        """);
  }

  @Test
  public void testCostBasedInlining6() {
    // Here we have a threshold test.
    // Do inline here:
    test(
        """
        function foo(a,b){return a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2+3+4+5}
        function _t1(){return foo(1,2)}
        function _t2(){return foo(2,3)}
        """,
        """
        function _t1(){return 1+2+1+2+1+2+1+2+4+5+6+7+8+9+1+2+3+4+5}
        function _t2(){return 2+3+2+3+2+3+2+3+4+5+6+7+8+9+1+2+3+4+5}
        """);
  }

  @Test
  public void testCostBasedInlining7() {
    // Don't inline here (not enough savings):
    testSame(
        """
        function foo(a,b){
            return a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2+3+4+5+6}
        function _t1(){return foo(1,2)}
        function _t2(){return foo(2,3)}
        """);
  }

  @Test
  public void testCostBasedInlining9() {
    // Here both direct and block inlining is used.  The call to f as a
    // parameter is inlined directly, which the call to f with f as a parameter
    // is inlined using block inlining.
    test(
        """
        function f(a){return chg() + a + a;}
        var a = f(f(1));
        """,
        """
        var a;
        {var a$jscomp$inline_0=chg()+1+1;
        a=chg()+a$jscomp$inline_0+a$jscomp$inline_0}
        """);
  }

  @Test
  public void testCostBasedInlining11() {
    // With block inlining
    test(
        """
        function f(a){return chg() + a + a;}
        var a = f(f(1))
        """,
        """
        var a;
        {var a$jscomp$inline_0=chg()+1+1;
        a=chg()+a$jscomp$inline_0+a$jscomp$inline_0}
        """);
  }

  @Test
  public void testCostBasedInlining12() {
    test(
        """
        function f(a){return 1 + a + a;}
        var a = f(1) + f(2);
        """,
        "var a=1+1+1+(1+2+2)");
  }

  @Test
  public void testCostBasedInlineForSimpleFunction() {
    int calls = 100;
    String src = "function f(a){return a;}\n";
    for (int i = 0; i < calls; i++) {
      src += "f(chg());\n";
    }
    String expected = "";
    for (int i = 0; i < calls; i++) {
      expected += "chg();\n";
    }
    test(src, expected);
  }

  @Test
  public void testCostBasedInliningComplex1() {
    testSame(
        """
        function foo(a){a()}
        foo=new Function("return 1");
        foo(1)
        """);
  }

  @Test
  public void testCostBasedInliningComplex2() {
    // Baseline complexity tests.
    // Single call, function not removed.
    test(
        """
        function foo(a){a()}
        var b=foo;
        function _t1(){foo(x)}
        """,
        """
        function foo(a){a()}
        var b=foo;
        function _t1(){{x()}}
        """);
  }

  @Test
  public void testCostBasedInliningComplex3() {
    // Two calls, function not removed.
    test(
        """
        function foo(a,b){a+b}
        var b=foo;
        function _t1(){foo(1,2)}
        function _t2(){foo(2,3)}
        """,
        """
        function foo(a,b){a+b}
        var b=foo;
        function _t1(){{1+2}}
        function _t2(){{2+3}}
        """);
  }

  @Test
  public void testCostBasedInliningComplex4() {
    // Two calls, function not removed.
    // Here there isn't enough savings to justify inlining.
    testSame(
        """
        function foo(a,b){a+b+a+b}
        var b=foo;
        function _t1(){foo(1,2)}
        function _t2(){foo(2,3)}
        """);
  }

  @Test
  public void testCostBasedInliningComplex5() {
    // Here there is enough savings to justify inlining.
    test(
        """
        function foo(a,b){a+b+a+b}
        function _t1(){foo(1,2)}
        function _t2(){foo(2,3)}
        """,
        """
        function _t1(){{1+2+1+2}}
        function _t2(){{2+3+2+3}}
        """);
  }

  @Test
  public void testCostBasedInliningComplex6() {
    // Here we have a threshold test.
    // Do inline here:
    test(
        """
        function foo(a,b){a+b+a+b+a+b+a+b+4+5+6+7+8+9+1}
        function _t1(){foo(1,2)}
        function _t2(){foo(2,3)}
        """,
        """
        function _t1(){{1+2+1+2+1+2+1+2+4+5+6+7+8+9+1}}
        function _t2(){{2+3+2+3+2+3+2+3+4+5+6+7+8+9+1}}
        """);
  }

  @Test
  public void testCostBasedInliningComplex7() {
    // Don't inline here (not enough savings):
    testSame(
        """
        function foo(a,b){a+b+a+b+a+b+a+b+4+5+6+7+8+9+1+2}
        function _t1(){foo(1,2)}
        function _t2(){foo(2,3)}
        """);
  }

  @Test
  public void testCostBasedInliningComplex8() {
    // Verify multiple references in the same statement.
    testSame(
        """
        function _f(a){1+a+a}
        a=_f(1)+_f(1)
        """);
  }

  @Test
  public void testCostBasedInliningComplex9() {
    test(
        """
        function f(a){1 + a + a;}
        f(1);f(2);
        """,
        """
        {1+1+1}
        {1+2+2}
        """);
  }

  @Test
  public void testDoubleInlining2() {
    test(
        """
        var foo = function(a) { return getWindow(a); };
        var bar = function(b) { return b; };
        foo(bar(x));
        """,
        "getWindow(x)");
  }

  @Test
  public void testNoInlineOfNonGlobalFunction1() {
    test(
        """
        var g;function _f(){function g(){return 0}}
        function _h(){return g()}
        """,
        """
        var g;function _f(){}
        function _h(){return g()}
        """);
  }

  @Test
  public void testNoInlineOfNonGlobalFunction2() {
    test(
        """
        var g;
        function _f() { var g = function() { return 0; }; }
        function _h() { return g(); }
        """,
        """
        var g;
        function _f(){}
        function _h(){ return g(); }
        """);
  }

  @Test
  public void testNoInlineOfNonGlobalFunction3() {
    test(
        """
        var g;function _f(){var g=function(){return 0}}
        function _h(){return g()}
        """,
        """
        var g;function _f(){}
        function _h(){return g()}
        """);
  }

  @Test
  public void testNoInlineOfNonGlobalFunction4() {
    test(
        """
        var g;function _f(){function g(){return 0}}
        function _h(){return g()}
        """,
        """
        var g;function _f(){}
        function _h(){return g()}
        """);
  }

  @Test
  public void testNoInlineMaskedFunction() {
    // Normalization makes this test of marginal value.
    // The unreferenced function is removed.
    test(
        """
        var g=function(){return 0};
        function _f(g){return g()}
        """,
        "function _f(g$jscomp$1){return g$jscomp$1()}");
  }

  @Test
  public void testNoInlineNonFunction() {
    testSame("var g=3;function _f(){return g()}");
  }

  @Test
  public void testInlineCall() {
    test("function f(g) { return g.h(); } f('x');", "\"x\".h()");
  }

  @Test
  public void testInlineFunctionWithArgsMismatch1() {
    test("function f(g) { return g; } f();", "void 0");
  }

  @Test
  public void testInlineFunctionWithArgsMismatch2() {
    test("function f() { return 0; } f(1);", "0");
  }

  @Test
  public void testInlineFunctionWithArgsMismatch3() {
    test("function f(one, two, three) { return one + two + three; } f(1);", "1+void 0+void 0");
  }

  @Test
  public void testInlineFunctionWithArgsMismatch4() {
    test(
        """
        function f(one, two, three) { return one + two + three; }
        f(1,2,3,4,5);
        """,
        "1+2+3");
  }

  @Test
  public void testComplexInlineNoResultNoParamCall1() {
    test("function f(){a()}f()", "{a()}");
  }

  @Test
  public void testComplexInlineNoResultNoParamCall2() {
    test(
        "function f() { if (true) { return; } else; } f();",
        "{JSCompiler_inline_label_f_0:{ if(true)break JSCompiler_inline_label_f_0;else; } }");
  }

  @Test
  public void testComplexInlineNoResultNoParamCall3() {
    // We now allow vars in the global space.
    //   Don't inline into vars into global scope.
    //   testSame("function f(){a();b();var z=1+1}f()");

    // But do inline into functions
    test(
        "function f(){a();b();var z=1+1}function _foo(){f()}",
        "function _foo(){{a();b();var z$jscomp$inline_0=1+1}}");
  }

  @Test
  public void testComplexInlineNoResultWithParamCall1() {
    test("function f(x){a(x)}f(1)", "{a(1)}");
  }

  @Test
  public void testComplexInlineNoResultWithParamCall2() {
    test("function f(x,y){a(x)}var b=1;f(1,b)", "var b=1;{a(1)}");
  }

  @Test
  public void testComplexInlineNoResultWithParamCall3() {
    test("function f(x,y){if (x) y(); return true;}var b=1;f(1,b)", "var b=1;{if(1)b();true}");
  }

  @Test
  public void testComplexInline1() {
    test(
        "function f(){if (true){return;}else;} z=f();",
        """
        {
          JSCompiler_inline_label_f_0: {
            if (true) {
              z = void 0;
              break JSCompiler_inline_label_f_0;
            }else;
            z = void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInline2() {
    test(
        "function f(){if (true){return;}else return;} z=f();",
        """
        {
          JSCompiler_inline_label_f_0: {
            if(true) {
              z = void 0;
              break JSCompiler_inline_label_f_0;
            } else {
              z=void 0;
              break JSCompiler_inline_label_f_0;
            }
            z=void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInline3() {
    test(
        "function f(){if (true){return 1;}else return 0;} z=f();",
        """
        {
        JSCompiler_inline_label_f_0:{
        if(true){z=1;
        break JSCompiler_inline_label_f_0;
        } else {
        z=0;
        break JSCompiler_inline_label_f_0;}
        z = void 0
        }
        }
        """);
  }

  @Test
  public void testComplexInline4() {
    test("function f(x){a(x)} z = f(1)", "{a(1);z=void 0}");
  }

  @Test
  public void testComplexInline5() {
    test("function f(x,y){a(x)}var b=1;z=f(1,b)", "var b=1;{a(1);z=void 0}");
  }

  @Test
  public void testComplexInline6() {
    test("function f(x,y){if (x) y(); return true;}var b=1;z=f(1,b)", "var b=1;{if(1)b();z=true}");
  }

  @Test
  public void testComplexInline7() {
    test(
        "function f(x,y){if (x) return y(); else return true;} var b=1;z=f(1,b)",
        """
        var b=1;
        {
          JSCompiler_inline_label_f_2: {
            if(1) {
              z = b();
              break JSCompiler_inline_label_f_2;
            } else {
              z = true;
              break JSCompiler_inline_label_f_2
            }
            z = void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInline8() {
    test("function f(x){a(x)}var z=f(1)", "var z;{a(1);z=void 0}");
  }

  @Test
  public void testInlineIntoLoopWithUninitializedVars1() {
    test(
        """
        function f(){var x;if (true){x=7} return x;}
        for(;;) {f();}
        """,
        """
        for(;;) {
          {
            var x$jscomp$inline_0 = void 0;
            if (true) { x$jscomp$inline_0 = 7; }
            x$jscomp$inline_0;
          }
        }
        """);
  }

  @Test
  public void testInlineIntoLoopWithUninitializedVars2() {
    test(
        """
        function f(){x = x||1; var x; return x;}
        for(;;) {f();}
        """,
        """
        for(;;) {
          {
            var x$jscomp$inline_0 = void 0;
            x$jscomp$inline_0 = x$jscomp$inline_0 || 1;
            x$jscomp$inline_0;
          }
        }
        """);
  }

  @Test
  public void testComplexInlineVars1() {
    test(
        "function f() { if (true) { return; } else; } var z = f();",
        """
        var z;
        {
          JSCompiler_inline_label_f_0:{
            if(true) {
              z = void 0;
              break JSCompiler_inline_label_f_0;
            } else;
            z = void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInlineVars2() {
    test(
        "function f(){if (true){return;}else return;}var z=f();",
        """
        var z;
        {
          JSCompiler_inline_label_f_0:{
            if (true) {
              z = void 0;
              break JSCompiler_inline_label_f_0;
            } else {
              z = void 0;
              break JSCompiler_inline_label_f_0;
            }
            z=void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInlineVars3() {
    test(
        "function f(){if (true){return 1;}else return 0;}var z=f();",
        """
        var z;
        {
          JSCompiler_inline_label_f_0:{
            if (true) {
              z = 1;
              break JSCompiler_inline_label_f_0;
            } else {
              z = 0;
              break JSCompiler_inline_label_f_0;
            }
            z = void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInlineVars4() {
    test("function f(x){a(x)}var z = f(1)", "var z;{a(1);z=void 0}");
  }

  @Test
  public void testComplexInlineVars5() {
    test("function f(x,y){a(x)}var b=1;var z=f(1,b)", "var b=1;var z;{a(1);z=void 0}");
  }

  @Test
  public void testComplexInlineVars6() {
    test(
        "function f(x,y){if (x) y(); return true;}var b=1;var z=f(1,b)",
        "var b=1;var z;{if(1)b();z=true}");
  }

  @Test
  public void testComplexInlineVars7() {
    test(
        """
        function f(x,y){if (x) return y(); else return true;}
        var b=1;var z=f(1,b)
        """,
        """
        var b=1;
        var z;
        {
          JSCompiler_inline_label_f_2:{
            if (1) {
              z = b();
              break JSCompiler_inline_label_f_2;
            } else {
              z = true;
              break JSCompiler_inline_label_f_2;
            }
            z = void 0;
          }
        }
        """);
  }

  @Test
  public void testComplexInlineVars8() {
    test("function f(x){a(x)}var x;var z=f(1)", "var x;var z;{a(1);z=void 0}");
  }

  @Test
  public void testComplexInlineVars9() {
    test("function f(x){a(x)}var x;var z=f(1);var y", "var x;var z;{a(1);z=void 0}var y");
  }

  @Test
  public void testComplexInlineVars10() {
    test(
        "function f(x){a(x)}var x=blah();var z=f(1);var y=blah();",
        "var x=blah();var z;{a(1);z=void 0}var y=blah()");
  }

  @Test
  public void testComplexInlineVars11() {
    test(
        "function f(x){a(x)}var x=blah();var z=f(1);var y;",
        "var x=blah();var z;{a(1);z=void 0}var y");
  }

  @Test
  public void testComplexInlineVars12() {
    test(
        "function f(x){a(x)}var x;var z=f(1);var y=blah();",
        "var x;var z;{a(1);z=void 0}var y=blah()");
  }

  @Test
  public void testComplexInlineInExpressionss1() {
    test("function f(){a()}var z=f()", "var z;{a();z=void 0}");
  }

  @Test
  public void testComplexInlineInExpressionss2() {
    test(
        "function f(){a()}c=z=f()",
        """
        var JSCompiler_inline_result$jscomp$0;
        {a();JSCompiler_inline_result$jscomp$0=void 0;}
        c=z=JSCompiler_inline_result$jscomp$0
        """);
  }

  @Test
  public void testComplexInlineInExpressionss3() {
    test(
        "function f(){a()}c=z=f()",
        """
        var JSCompiler_inline_result$jscomp$0;
        {a();JSCompiler_inline_result$jscomp$0=void 0;}
        c=z=JSCompiler_inline_result$jscomp$0
        """);
  }

  @Test
  public void testComplexInlineInExpressionss4() {
    test(
        "function f(){a()}if(z=f());",
        """
        var JSCompiler_inline_result$jscomp$0;
        {a();JSCompiler_inline_result$jscomp$0=void 0;}
        if(z=JSCompiler_inline_result$jscomp$0);
        """);
  }

  @Test
  public void testComplexInlineInExpressionss5() {
    test(
        "function f(){a()}if(z.y=f());",
        """
        var JSCompiler_temp_const$jscomp$0=z;
        var JSCompiler_inline_result$jscomp$1;
        {a();JSCompiler_inline_result$jscomp$1=void 0;}
        if(JSCompiler_temp_const$jscomp$0.y=JSCompiler_inline_result$jscomp$1);
        """);
  }

  @Test
  public void testComplexNoInline1() {
    testSame("function f(){a()}while(z=f())continue");
  }

  @Test
  public void testComplexNoInline2() {
    testSame("function f(){a()}do;while(z=f())");
  }

  @Test
  public void testComplexSample() {
    test(
        """
        var foo = function(stylesString, opt_element) {
          var styleSheet = null;
          if (goog$userAgent$IE)
            styleSheet = 0;
          else
            var head = 0;

          goo$zoo(styleSheet, stylesString);
          return styleSheet;
        };

        var goo$zoo = function(element, stylesString) {
          if (goog$userAgent$IE)
            element.cssText = stylesString;
          else {
            var propToSet = 'innerText';
            element[propToSet] = stylesString;
          }
        };
        (function(){foo(a,b);})();
        """,
        """
        {
          {
            var styleSheet$jscomp$inline_2 = null;
            if (goog$userAgent$IE)
              styleSheet$jscomp$inline_2 = 0;
            else
              var head$jscomp$inline_3 = 0;
            {
               var element$jscomp$inline_0 = styleSheet$jscomp$inline_2;
               var stylesString$jscomp$inline_1=a;
               if (goog$userAgent$IE)
                 element$jscomp$inline_0.cssText = stylesString$jscomp$inline_1;
               else {
                 var propToSet$jscomp$inline_2 = 'innerText';
                 element$jscomp$inline_0[propToSet$jscomp$inline_2] =
                     stylesString$jscomp$inline_1
               }
            }
            styleSheet$jscomp$inline_2;
          }
        }
        """);
  }

  @Test
  public void testComplexSampleNoInline() {
    testSame(
        """
        foo = function(stylesString, opt_element) {
          var styleSheet = null;
          if(goog$userAgent$IE)
            styleSheet=0;
          else
            var head=0;

          goo$zoo(styleSheet, stylesString);
          return styleSheet
        };

        goo$zoo = function(element, stylesString) {
          if (goog$userAgent$IE)
            element.cssText = stylesString;
          else{
            var propToSet = goog$userAgent$WEBKIT? 'innerText' : 'innerHTML';
            element[propToSet] = stylesString
          }
        }
        """);
  }

  // Test redefinition of parameter name.
  @Test
  public void testComplexNoVarSub() {
    test(
        """
        function foo(x){
        var x;
        y=x
        }
        foo(1)
        """,
        "{y=1}");
  }

  @Test
  public void testComplexFunctionWithFunctionDefinition1() {
    test("function f(){call(function(){return})}f()", "{call(function(){return})}");
  }

  @Test
  public void testComplexFunctionWithFunctionDefinition2() {
    assumeMinimumCapture = false;

    // Don't inline if local names might be captured.
    testSame(
        """
        function f(a) {
          call(function(){return});
        }
        f();
        """);

    assumeMinimumCapture = true;

    test(
        """
        (function() {
          var f = function(a) { call(function(){return a}); };
          f();
        })();
        """,
        """
        {
          {
            var a$jscomp$inline_0 = void 0;
            call(function() { return a$jscomp$inline_0; });
          }
        }
        """);
  }

  @Test
  public void testComplexFunctionWithFunctionDefinition2a() {
    assumeMinimumCapture = false;

    // Don't inline if local names might be captured.
    testSame(
        """
        (function() {
          var f = function(a) { call(function(){return a}); };
          f();
        })()
        """);

    assumeMinimumCapture = true;

    test(
        """
        (function() {
          var f = function(a) { call(function(){return a}); };
          f();
        })()
        """,
        """
        {
          {
            var a$jscomp$inline_0 = void 0;
            call(function() { return a$jscomp$inline_0; });
          }
        }
        """);
  }

  @Test
  public void testComplexFunctionWithFunctionDefinition3() {
    assumeMinimumCapture = false;

    // Don't inline if local names might need to be captured.
    testSame("function f(){var a; call(function(){return a})}f()");

    assumeMinimumCapture = true;

    test(
        "function f(){var a; call(function(){return a})}f()",
        "{var a$jscomp$inline_0;call(function(){return a$jscomp$inline_0})}");
  }

  @Test
  public void testDecomposePlusEquals() {
    test(
        "function f(){a=1;return 1} var x = 1; x += f()",
        """
        var x = 1;
        var JSCompiler_temp_const$jscomp$0 = x;
        var JSCompiler_inline_result$jscomp$1;
        {a=1;
         JSCompiler_inline_result$jscomp$1=1}
        x = JSCompiler_temp_const$jscomp$0 + JSCompiler_inline_result$jscomp$1;
        """);
  }

  @Test
  public void testDecomposeFunctionExpressionInCall() {
    test(
        """
        (function(map){descriptions_=map})(
        function(){
        var ret={};
        ret[ONE]='a';
        ret[TWO]='b';
        return ret
        }()
        );
        """,
        """
        var JSCompiler_inline_result$jscomp$0;
        {
        var ret$jscomp$inline_1={};
        ret$jscomp$inline_1[ONE]='a';
        ret$jscomp$inline_1[TWO]='b';
        JSCompiler_inline_result$jscomp$0 = ret$jscomp$inline_1;
        }
        {
        descriptions_=JSCompiler_inline_result$jscomp$0;
        }
        """);
  }

  @Test
  public void testInlineConstructor1() {
    test("function f() {} function _g() {f.call(this)}", "function _g() {void 0}");
  }

  @Test
  public void testInlineConstructor2() {
    test(
        "function f() {} f.prototype.a = 0; function _g() {f.call(this)}",
        "function f() {} f.prototype.a = 0; function _g() {void 0}");
  }

  @Test
  public void testInlineConstructor3() {
    test(
        """
        function f() {x.call(this)} f.prototype.a = 0;
        function _g() {f.call(this)}
        """,
        """
        function f() {x.call(this)} f.prototype.a = 0;
        function _g() {{x.call(this)}}
        """);
  }

  @Test
  public void testInlineConstructor4() {
    test(
        """
        function f() {x.call(this)} f.prototype.a = 0;
        function _g() {var t = f.call(this)}
        """,
        """
        function f() {x.call(this)} f.prototype.a = 0;
            function _g() {var t; {x.call(this); t = void 0}}
        """);
  }

  @Test
  public void testFunctionExpressionInlining1() {
    test("(function(){})()", "void 0");
  }

  @Test
  public void testFunctionExpressionInlining2() {
    test("(function(){foo()})()", "{foo()}");
  }

  @Test
  public void testFunctionExpressionInlining3() {
    test("var a = (function(){return foo()})()", "var a = foo()");
  }

  @Test
  public void testFunctionExpressionInlining4() {
    test("var a; a = 1 + (function(){return foo()})()", "var a; a = 1 + foo()");
  }

  @Test
  public void testFunctionExpressionCallInlining1() {
    test("(function(){}).call(this)", "void 0");
  }

  @Test
  public void testFunctionExpressionCallInlining2() {
    test("(function(){foo(this)}).call(this)", "{foo(this)}");
  }

  @Test
  public void testFunctionExpressionCallInlining3() {
    test("var a = (function(){return foo(this)}).call(this)", "var a = foo(this)");
  }

  @Test
  public void testFunctionExpressionCallInlining4() {
    test("var a; a = 1 + (function(){return foo(this)}).call(this)", "var a; a = 1 + foo(this)");
  }

  @Test
  public void testFunctionExpressionCallInlining5() {
    test("a:(function(){return foo()})()", "a:foo()");
  }

  @Test
  public void testFunctionExpressionCallInlining6() {
    test("a:(function(){return foo()}).call(this)", "a:foo()");
  }

  @Test
  public void testFunctionExpressionCallInlining7() {
    test("a:(function(){})()", "a:void 0");
  }

  @Test
  public void testFunctionExpressionCallInlining8() {
    test("a:(function(){}).call(this)", "a:void 0");
  }

  @Test
  public void testFunctionExpressionCallInlining9() {
    // ... with unused recursive name.
    test("(function foo(){})()", "void 0");
  }

  @Test
  public void testFunctionExpressionCallInlining10() {
    // ... with unused recursive name.
    test("(function foo(){}).call(this)", "void 0");
  }

  @Test
  public void testFunctionExpressionCallInlining11a() {
    // Inline functions that return inner functions.
    test("((function(){return function(){foo()}})())();", "{foo()}");
  }

  @Test
  public void testFunctionExpressionCallInlining11b() {
    assumeMinimumCapture = false;
    // Can't inline functions that return inner functions and have local names.
    testSame("((function(){var a; return function(){foo()}})())();");

    assumeMinimumCapture = true;
    test(
        "((function(){var a; return function(){foo()}})())();",
        """
        var JSCompiler_inline_result$jscomp$0;
        {var a$jscomp$inline_1;
        JSCompiler_inline_result$jscomp$0=function(){foo()};}
        JSCompiler_inline_result$jscomp$0()
        """);
  }

  @Test
  public void testFunctionExpressionCallInlining11c() {
    // TODO(johnlenz): Can inline, not temps needed.
    assumeMinimumCapture = false;
    testSame(
        """
        function _x() {
          ((function(){return function(){foo()}})())();
        }
        """);

    assumeMinimumCapture = true;
    test(
        """
        function _x() {
          ((function(){return function(){foo()}})())();
        }
        """,
        """
        function _x() {
          {foo()}
        }
        """);
  }

  @Test
  public void testFunctionExpressionCallInlining11d() {
    // TODO(johnlenz): Can inline into a function containing eval, if
    // no names are introduced.
    assumeMinimumCapture = false;
    testSame(
        """
        function _x() {
          eval();
          ((function(){return function(){foo()}})())();
        }
        """);

    assumeMinimumCapture = true;
    test(
        """
        function _x() {
          eval();
          ((function(){return function(){foo()}})())();
        }
        """,
        """
        function _x() {
          eval();
          {foo()}
        }
        """);
  }

  @Test
  public void testFunctionExpressionCallInlining11e() {
    // No, don't inline into a function containing eval,
    // if temps are introduced.
    assumeMinimumCapture = false;
    testSame(
        """
        function _x() {
          eval();
          ((function(a){return function(){foo()}})())();
        }
        """);

    assumeMinimumCapture = true;
    test(
        """
        function _x() {
          eval();
          ((function(a){return function(){foo()}})())();
        }
        """,
        """
        function _x() {
          eval();
          {foo();}
        }
        """);
  }

  @Test
  public void testFunctionExpressionCallInlining12() {
    // Can't inline functions that recurse.
    testSame("(function foo(){foo()})()");
  }

  @Test
  public void testFunctionExpressionOmega() {
    // ... with unused recursive name.
    test(
        "(function (f){f(f)})(function(f){f(f)})",
        """
        {
          var f$jscomp$inline_0 = function(f$jscomp$1) {
            f$jscomp$1(f$jscomp$1);
          };
          {
            {
               f$jscomp$inline_0(f$jscomp$inline_0);
            }
          }
        }
        """);
  }

  @Test
  public void testLocalFunctionInlining1() {
    test("function _f(){ function g() {} g() }", "function _f(){ void 0 }");
  }

  @Test
  public void testLocalFunctionInlining2() {
    test("function _f(){ function g() {foo(); bar();} g() }", "function _f(){ {foo(); bar();} }");
  }

  @Test
  public void testLocalFunctionInlining3() {
    test("function _f(){ function g() {foo(); bar();} g() }", "function _f(){ {foo(); bar();} }");
  }

  @Test
  public void testLocalFunctionInlining4() {
    test("function _f(){ function g() {return 1} return g() }", "function _f(){ return 1 }");
  }

  @Test
  public void testLocalFunctionInlining4_optChain() {
    test("function _f(){ function g() {return 1} return g?.() }", "function _f(){ return 1 }");
  }

  @Test
  public void testLocalFunctionInlining5() {
    testSame("function _f(){ function g() {this;} g() }");
  }

  @Test
  public void testLocalFunctionInlining6() {
    testSame("function _f(){ function g() {this;} return g; }");
  }

  @Test
  public void testLocalFunctionInliningOnly1() {
    this.inliningReach = Reach.ALL;
    test("function f(){} f()", "void 0;");
    this.inliningReach = Reach.LOCAL_ONLY;
    testSame("function f(){} f()");
  }

  @Test
  public void testLocalFunctionInliningOnly2() {
    this.inliningReach = Reach.LOCAL_ONLY;
    testSame("function f(){} f()");

    test(
        "function f(){ function g() {return 1} return g() }; f();",
        "function f(){ return 1 }; f();");
  }

  @Test
  public void testLocalFunctionInliningOnly3() {
    this.inliningReach = Reach.LOCAL_ONLY;
    testSame("function f(){} f()");

    test("(function(){ function g() {return 1} return g() })();", "(function(){ return 1 })();");
  }

  @Test
  public void testLocalFunctionInliningOnly4() {
    this.inliningReach = Reach.LOCAL_ONLY;
    testSame("function f(){} f()");

    test("(function(){ return (function() {return 1})() })();", "(function(){ return 1 })();");
  }

  @Test
  public void testInlineWithThis1() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call();");
    testSame("function f(){this} f.call();");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call();", "{}");
    test("function f(){this} f.call();", "{void 0;}");
  }

  @Test
  public void testInlineWithThis2() {
    // "this" can always be replaced with "this"
    assumeStrictThis = false;
    test("function f(){} f.call(this);", "void 0");

    assumeStrictThis = true;
    test("function f(){} f.call(this);", "void 0");
  }

  @Test
  public void testInlineWithThis3() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call([]);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call([]);", "{}");
  }

  @Test
  public void testInlineWithThis4() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call(new g);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call(new g);", "{var JSCompiler_inline_this_0=new g}");
  }

  @Test
  public void testInlineWithThis5() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){} f.call(g());");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test("function f(){} f.call(g());", "{var JSCompiler_inline_this_0=g()}");
  }

  @Test
  public void testInlineWithThis6() {
    assumeStrictThis = false;
    // If no "this" is provided it might need to be coerced to the global
    // "this".
    testSame("function f(){this} f.call(new g);");

    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test(
        "function f(){this} f.call(new g);",
        "{var JSCompiler_inline_this_0=new g;JSCompiler_inline_this_0}");
  }

  @Test
  public void testInlineWithThis7() {
    assumeStrictThis = true;
    // In strict mode, "this" is never coerced so we can use the provided value.
    test(
        "function f(a){a=1;this} f.call();",
        "{var a$jscomp$inline_0=void 0; a$jscomp$inline_0=1; void 0;}");
    test(
        "function f(a){a=1;this} f.call(x, x);",
        """
        {var JSCompiler_inline_this_1 = x; var a$jscomp$inline_0=x; a$jscomp$inline_0=1;
            JSCompiler_inline_this_1;}
        """);
  }

  // http://en.wikipedia.org/wiki/Fixed_point_combinator#Y_combinator
  @Test
  public void testFunctionExpressionYCombinator() {
    assumeMinimumCapture = false;
    testSame(
        """
        var factorial = ((function(M) {
              return ((function(f) {
                         return M(function(arg) {
                                    return (f(f))(arg);
                                    })
                       })
                      (function(f) {
                         return M(function(arg) {
                                    return (f(f))(arg);
                                   })
                         }));
             })
            (function(f) {
               return function(n) {
                if (n === 0)
                  return 1;
                else
                  return n * f(n - 1);
               };
             }));

        factorial(5)
        """);

    assumeMinimumCapture = true;
    test(
        """
        var factorial = ((function(M) {
              return ((function(f) {
                         return M(function(arg) {
                                    return (f(f))(arg);
                                    })
                       })
                      (function(f) {
                         return M(function(arg) {
                                    return (f(f))(arg);
                                   })
                         }));
             })
            (function(f) {
               return function(n) {
                if (n === 0)
                  return 1;
                else
                  return n * f(n - 1);
               };
             }));

        factorial(5)
        """,
        """
        var factorial;
        {
        var M$jscomp$inline_4 = function(f$jscomp$2) {
          return function(n){if(n===0)return 1;else return n*f$jscomp$2(n-1)}
        };
        {
        var f$jscomp$inline_0=function(f$jscomp$inline_7){
          return M$jscomp$inline_4(
            function(arg$jscomp$inline_8){
              return f$jscomp$inline_7(f$jscomp$inline_7)(arg$jscomp$inline_8)
             })
        };
        factorial=M$jscomp$inline_4(
          function(arg$jscomp$inline_1){
            return f$jscomp$inline_0(f$jscomp$inline_0)(arg$jscomp$inline_1)
        });
        }
        }
        factorial(5)
        """);
  }

  @Test
  public void testRenamePropertyFunction() {
    testSame(
        """
        function JSCompiler_renameProperty(x) {return x}
        JSCompiler_renameProperty('foo')
        """);
  }

  @Test
  public void testReplacePropertyFunction() {
    // baseline: an alias doesn't prevents declaration removal, but not
    // inlining.
    test(
        """
        function f(x) {return x}
        foo(window, f); f(1)
        """,
        """
        function f(x) {return x}
        foo(window, f); 1
        """);
  }

  @Test
  public void testInlineWithClosureContainingThis() {
    test("(function (){return f(function(){return this})})();", "f(function(){return this})");
  }

  @Test
  public void testIssue5159924a() {
    test(
        """
        function f() { if (x()) return y(); }
        while(1) { var m = f() || z() }
        """,
        """
        for(;1;) {
          var JSCompiler_inline_result$jscomp$0;
          {
            JSCompiler_inline_label_f_1: {
              if(x()) {
                JSCompiler_inline_result$jscomp$0 = y();
                break JSCompiler_inline_label_f_1;
              }
              JSCompiler_inline_result$jscomp$0 = void 0;
            }
          }
          var m = JSCompiler_inline_result$jscomp$0 || z()
        }
        """);
  }

  @Test
  public void testIssue5159924b() {
    test(
        """
        function f() { if (x()) return y(); }
        while(1) { var m = f(); }
        """,
        """
        for(;1;) {
          var m;
          {
            JSCompiler_inline_label_f_0: {
              if(x()) {
                m = y();
                break JSCompiler_inline_label_f_0;
              }
              m = void 0;
            }
          }
        }
        """);
  }

  @Test
  public void testInlineObject() {
    disableCompareAsTree();
    enableComputeSideEffects();

    this.inliningReach = Reach.LOCAL_ONLY;
    assumeStrictThis = true;
    assumeMinimumCapture = true;

    // TODO(johnlenz): normalize the AST so an AST comparison can be done.
    // As is, the expected AST does not match the actual correct result:
    // The AST matches "g.a()" with a FREE_CALL annotation, but this as
    // expected string would fail as it won't be mark as a free call.
    // "(0,g.a)()" matches the output, but not the resulting AST.
    test("function inner(){function f(){return g.a}(f())()}", "function inner(){(0,g.a)()}");
  }

  @Test
  public void testReproduceBug401582520() {
    // InlineFunctions is inlining reading a property before the side-effectful calls that
    // initializes it occurs.
    test(
        """
          var module$exports$foo$Foo$$0clinit = function() {
            module$exports$foo$Foo$$0clinit = function() {
            };
            module$exports$foo$Foo$options_ = ["a", "b", "c", "d"];
          };

                  var module$exports$foo$Foo$create = function(o$jscomp$1) {
                    module$exports$foo$Foo$$0clinit();
                    return new module$exports$foo$Foo(o$jscomp$1);
                  };

                  var module$exports$foo$Foo = class {
                    constructor(o) {
                      this.o_ = o;
                    }
                  };

                  var module$exports$foo$Foo$options_;

          console.log(function(JSCompiler_StaticMethods_isKnown$self) {
            return module$exports$foo$Foo$options_.includes(JSCompiler_StaticMethods_isKnown$self.o_);
          }(function(o$jscomp$2) {
            return module$exports$foo$Foo$create(o$jscomp$2);
          }("abc")));
        """,
        """
        var module$exports$foo$Foo$$0clinit = function() {
          module$exports$foo$Foo$$0clinit = function() {
          };
          module$exports$foo$Foo$options_ = ["a", "b", "c", "d"];
        };
        var module$exports$foo$Foo = class {
          constructor(o) {
            this.o_ = o;
          }
        };
        var module$exports$foo$Foo$options_;
        var JSCompiler_temp_const$jscomp$1 = console;
        var JSCompiler_temp_const$jscomp$0 = JSCompiler_temp_const$jscomp$1.log;
        var JSCompiler_inline_result$jscomp$2;
        {
          var JSCompiler_StaticMethods_isKnown$self$jscomp$inline_3;
          {
            module$exports$foo$Foo$$0clinit();
            JSCompiler_StaticMethods_isKnown$self$jscomp$inline_3 = new module$exports$foo$Foo("abc");
          }
          JSCompiler_inline_result$jscomp$2 = module$exports$foo$Foo$options_.includes(JSCompiler_StaticMethods_isKnown$self$jscomp$inline_3.o_);
        }
        JSCompiler_temp_const$jscomp$0.call(JSCompiler_temp_const$jscomp$1, JSCompiler_inline_result$jscomp$2);
        """);
  }

  @Test
  public void testReproduceBug401582520_simplified() {
    // InlineFunctions is inlining reading a property before the side-effectful calls that
    // initializes it occurs.
    test(
        """
        var options_;

        /** @noinline */
        var initAndReturnA = function() {
          options_ = ["a", "b", "c", "d"];
          return "a";
        };

        console.log(function(x) {
          return options_.includes(x);
        }(initAndReturnA()));
        """,
        """
        var options_;

        /** @noinline */
        var initAndReturnA = function() {
          options_ = ["a", "b", "c", "d"];
          return "a";
        };

        var JSCompiler_temp_const$jscomp$1 = console;
        var JSCompiler_temp_const$jscomp$0 = JSCompiler_temp_const$jscomp$1.log;
        var JSCompiler_inline_result$jscomp$2;
        {
          var x$jscomp$inline_3 = initAndReturnA();
          JSCompiler_inline_result$jscomp$2 = options_.includes(x$jscomp$inline_3);
        }
        JSCompiler_temp_const$jscomp$0.call(JSCompiler_temp_const$jscomp$1, JSCompiler_inline_result$jscomp$2);
        """);
  }

  @Test
  public void testBug4944818() {
    test(
        """
        var getDomServices_ = function(self) {
          if (!self.domServices_) {
            self.domServices_ = goog$component$DomServices.get(
                self.appContext_);
          }

          return self.domServices_;
        };

        var getOwnerWin_ = function(self) {
          return getDomServices_(self).getDomHelper().getWindow();
        };

        HangoutStarter.prototype.launchHangout = function() {
          var self = a.b;
          var myUrl = new goog.Uri(getOwnerWin_(self).location.href);
        };
        """,
        """
        HangoutStarter.prototype.launchHangout = function() {
          var self$jscomp$2 = a.b;
          var JSCompiler_temp_const$jscomp$0 = goog.Uri;
          var JSCompiler_inline_result$jscomp$1;
          {
          var self$jscomp$inline_2 = self$jscomp$2;
          if (!self$jscomp$inline_2.domServices_) {
            self$jscomp$inline_2.domServices_ = goog$component$DomServices.get(
                self$jscomp$inline_2.appContext_);
          }
          JSCompiler_inline_result$jscomp$1=self$jscomp$inline_2.domServices_;
          }
          var myUrl = new JSCompiler_temp_const$jscomp$0(
              JSCompiler_inline_result$jscomp$1.getDomHelper().
                  getWindow().location.href)
        }
        """);
  }

  // http://blickly.github.io/closure-compiler-issues/#423
  @Test
  public void testIssue423() {
    assumeMinimumCapture = false;
    test(
        """
        (function($) {
          $.fn.multicheck = function(options) {
            initialize.call(this, options);
          };

          function initialize(options) {
            options.checkboxes = $(this).siblings(':checkbox');
            preload_check_all.call(this);
          }

          function preload_check_all() {
            $(this).data('checkboxes');
          }
        })(jQuery)
        """,
        """
        (function($){
          $.fn.multicheck=function(options) {
            {
              options.checkboxes=$(this).siblings(':checkbox');
              {
                $(this).data('checkboxes');
              }
            }
          }
        })(jQuery)
        """);
  }

  // http://blickly.github.io/closure-compiler-issues/#423
  @Test
  public void testIssue423_minCap() {
    assumeMinimumCapture = true;
    test(
        """
        (function($) {
          $.fn.multicheck = function(options) {
            initialize.call(this, options);
          };

          function initialize(options) {
            options.checkboxes = $(this).siblings(':checkbox');
            preload_check_all.call(this);
          }

          function preload_check_all() {
            $(this).data('checkboxes');
          }
        })(jQuery)
        """,
        """
        {
          var $$jscomp$inline_0=jQuery;
          $$jscomp$inline_0.fn.multicheck=function(options$jscomp$inline_4) {
            {
              options$jscomp$inline_4.checkboxes =
                  $$jscomp$inline_0(this).siblings(':checkbox');
              {
                 $$jscomp$inline_0(this).data('checkboxes')
              }
            }
          }
        }
        """);
  }

  // http://blickly.github.io/closure-compiler-issues/#728
  @Test
  public void testIssue728() {
    String f = "var f = function() { return false; };";
    StringBuilder calls = new StringBuilder();
    StringBuilder folded = new StringBuilder();
    for (int i = 0; i < 30; i++) {
      calls.append("if (!f()) alert('x');");
      folded.append("if (!false) alert('x');");
    }

    test(f + calls, folded.toString());
  }

  @Test
  public void testAnonymous1() {
    assumeMinimumCapture = false;
    test(
        "(function(){var a=10;(function(){var b=a;a++;alert(b)})()})();",
        """
        {var a$jscomp$inline_0=10;
        {var b$jscomp$inline_1=a$jscomp$inline_0;
        a$jscomp$inline_0++;alert(b$jscomp$inline_1)}}
        """);

    assumeMinimumCapture = true;
    test(
        "(function(){var a=10;(function(){var b=a;a++;alert(b)})()})();",
        """
        {var a$jscomp$inline_2=10;
        {var b$jscomp$inline_0=a$jscomp$inline_2;
        a$jscomp$inline_2++;alert(b$jscomp$inline_0)}}
        """);
  }

  @Test
  public void testAnonymous2() {
    testSame(
        """
        (function(){
          eval();
          (function(){
            var b=a;
            a++;
            alert(b)
          })()
        })();
        """);
  }

  @Test
  public void testAnonymous3() {
    // Introducing a new value into is tricky
    assumeMinimumCapture = false;
    testSame("(function(){var a=10;(function(){arguments;})()})();");

    assumeMinimumCapture = true;
    test(
        "(function(){var a=10;(function(){arguments;})()})();",
        "{var a$jscomp$inline_0=10;(function(){arguments;})();}");

    test("(function(){(function(){arguments;})()})();", "{(function(){arguments;})()}");
  }

  @Test
  public void testLoopWithFunctionWithFunction() {
    assumeMinimumCapture = true;
    test(
        """
        function _testLocalVariableInLoop_() {
          var result = 0;
          function foo() {
            var arr = [1, 2, 3, 4, 5];
            for (var i = 0, l = arr.length; i < l; i++) {
              var j = arr[i];
              // Don't inline this function; the correct behavior depends on captured values.
              (function() {
                var k = j;
                setTimeout(function() { result += k; }, 5 * i);
              })();
            }
          }
          foo();
        }
        """,
        """
        function _testLocalVariableInLoop_() {
          var result = 0;
          {
            var arr$jscomp$inline_0 = [1,2,3,4,5];
            var i$jscomp$inline_1 = 0;
            var l$jscomp$inline_2 = arr$jscomp$inline_0.length;
            for(;i$jscomp$inline_1 < l$jscomp$inline_2; i$jscomp$inline_1++) {
              var j$jscomp$inline_3 = arr$jscomp$inline_0[i$jscomp$inline_1];
              (function(){
                var k$jscomp$inline_4 = j$jscomp$inline_3;
                setTimeout(function() {result += k$jscomp$inline_4; }, 5*i$jscomp$inline_1)
              })()
            }
          }
        }
        """);
  }

  @Test
  public void testMethodWithFunctionWithFunction() {
    assumeMinimumCapture = true;
    test(
        """
        function _testLocalVariable_() {
          var result = 0;
          function foo() {
              var j = [i];
              (function(j) {
                setTimeout(function() { result += j; }, 5 * i);
              })(j);
              j = null;
          }
          foo();
        }
        """,
        """
        function _testLocalVariable_(){
          var result = 0;
          {
            var j$jscomp$inline_2 = [i];
            {
              var j$jscomp$inline_0 = j$jscomp$inline_2; // this temp is needed.
              setTimeout(function(){ result += j$jscomp$inline_0; }, 5*i);
            }
            j$jscomp$inline_2 = null; // because this value can be modified later.
          }
        }
        """);
  }

  // Inline a single reference function into deeper modules
  @Test
  public void testCrossModuleInlining1() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function foo(){return f(1)+g(2)+h(3);}")
                // m2
                .addChunk("foo()")
                .build()),
        expected(
            // m1
            "",
            // m2
            "f(1)+g(2)+h(3);"));
  }

  // Inline a single reference function into shallow modules, only if it
  // is cheaper than the call itself.
  @Test
  public void testCrossModuleInlining2() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("foo()")
                .addChunk("function foo(){return f(1)+g(2)+h(3);}")
                .build()));

    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("foo()")
                // m2
                .addChunk("function foo(){return f();}")
                .build()),
        expected( // m1
            "f();",
            // m2
            ""));
  }

  // Inline a multi-reference functions into shallow modules, only if it
  // is cheaper than the call itself.
  @Test
  public void testCrossModuleInlining3() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("foo()")
                .addChunk("function foo(){return f(1)+g(2)+h(3);}")
                .addChunk("foo()")
                .build()));

    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("foo()")
                .addChunk("function foo(){return f();}")
                .addChunk("foo()")
                .build()),
        expected(
            // m1
            "f();",
            // m2
            "",
            // m3
            "f();"));
  }

  @Test
  public void test6671158() throws Exception {
    CompilerTestCase tester =
        new InlineFunctionsTest() {
          @Override
          void maybeEnableInferConsts() {}
        };

    tester.setUp();
    tester.test(
        """
        function f() {return g()}
        function Y(a){a.loader_()}
        function _Z(){}
        function _X() { new _Z(a,b, Y(singleton), f()) }
        """,
        """
        function _Z(){}
        function _X(){
          var JSCompiler_temp_const$jscomp$2=_Z;
          var JSCompiler_temp_const$jscomp$1=a;
          var JSCompiler_temp_const$jscomp$0=b;
          var JSCompiler_inline_result$jscomp$3;
          {
            singleton.loader_();
            JSCompiler_inline_result$jscomp$3=void 0;
          }
          new JSCompiler_temp_const$jscomp$2(
            JSCompiler_temp_const$jscomp$1,
            JSCompiler_temp_const$jscomp$0,
            JSCompiler_inline_result$jscomp$3,
            g())}
        """);
    tester.tearDown();
  }

  @Test
  public void test6671158b() {
    test(
        """
        function f() {return g()}
        function Y(a){a.loader_()}
        function _Z(){}
        function _X() { new _Z(a,b, Y(singleton), f()) }
        """,
        """
        function _Z(){}
        function _X(){
          var JSCompiler_temp_const$jscomp$1=a;
          var JSCompiler_temp_const$jscomp$0=b;
          var JSCompiler_inline_result$jscomp$2;
          {
            singleton.loader_();
            JSCompiler_inline_result$jscomp$2=void 0;
          }
          new _Z(
            JSCompiler_temp_const$jscomp$1,
            JSCompiler_temp_const$jscomp$0,
            JSCompiler_inline_result$jscomp$2,
            g())}
        """);
  }

  @Test
  public void test8609285a() {
    test(
        "function f(x){ for(x in y){} } f()",
        "{var x$jscomp$inline_0=void 0;for(x$jscomp$inline_0 in y);}");
  }

  @Test
  public void test8609285b() {
    test(
        "function f(x){ for(var x in y){} } f()",
        "{var x$jscomp$inline_0=void 0;for(x$jscomp$inline_0 in y);}");
  }

  // http://blickly.github.io/closure-compiler-issues/#1101
  @Test
  public void testIssue1101() {
    test(
        """
        var x = (function (saved) {
            return modifyObjProp(obj) + saved;
          })(obj["prop"]);
        """,
        """
        var x;
        {
          var saved$jscomp$inline_0=obj["prop"];x=modifyObjProp(obj)+
             saved$jscomp$inline_0
        }
        """);
  }

  @Test
  public void testMaxFunSizeAfterInlining() {
    this.maxSizeAfterInlining = 1;
    test( // Always inline single-statement functions
        """
        function g() { return 123; }
        function f() { g(); }
        """,
        "function f() { 123; }");

    this.maxSizeAfterInlining = 10;
    test( // Always inline at the top level
        """
        function g() { 123; return 123; }
        g();
        """,
        "{ 123; 123; }");

    this.maxSizeAfterInlining = 1;
    testSame( // g is too big to be inlined
        """
        function g() { 123; return 123; }
        g();
        """);

    this.maxSizeAfterInlining = 20;
    test(
        """
        function g() { 123; return 123; }
        function f() {
          g();
        }
        """,
        "");

    // g's size ends up exceeding the max size because all inlining decisions
    // were made in the same inlining round.
    this.maxSizeAfterInlining = 25;
    test(
        """
        function f1() { 1; return 1; }
        function f2() { 2; return 2; }
        function f3() { 3; return 3; }
        function f4() { 4; return 4; }
        function g() {
          f1(); f2(); f3(); f4();
        }
        g(); g(); g();
        """,
        "function g() { {1; 1;} {2; 2;} {3; 3;} {4; 4;} }\ng(); g(); g();");

    this.maxSizeAfterInlining = CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING;
  }

  @Test
  public void testArrowFunctionUndefined() {
    test("var empty = () => {}; empty();", "void 0;");
  }

  @Test
  public void testFunctionProperty() {
    testSame(
        """
        function Foo() {
          this.bar = 0;
        }
        var a = new Foo();
        a.bar;
        """);
  }

  @Test
  public void testFunctionRestParam() {
    test(
        """
        var f = function(...args) { return args[0]; }
        f(8);
        """,
        "[8][0]");

    test(
        """
        var f = function(...args) { return args.length; }
        f();
        """,
        "[].length;");

    test(
        """
        var f = function(...args) { return args.length; }
        f(3, 4);
        """,
        "[3, 4].length;");
  }

  @Test
  public void testArrowFunctionRestParam() {
    test(
        """
        function foo() {
          var f = (...args) => args[0];
          return f(8);
        }
        foo();
        """,
        "[8][0]");

    test(
        """
        function foo() {
          var f = (x, ...args) => x + args[0];
          return f(1, 8);
        }
        foo();
        """,
        """
        {
          var JSCompiler_inline_result$jscomp$inline_0;
          {
            var x$jscomp$inline_1 = 1;
            var args$jscomp$inline_2 = [8];
            JSCompiler_inline_result$jscomp$inline_0 = x$jscomp$inline_1 + args$jscomp$inline_2[0];
          }
          JSCompiler_inline_result$jscomp$inline_0
        }
        """);

    test(
        """
        function foo() {
          var f = (...args) => args[0].bar;
          return f({bar: 8});
        }
        foo();
        """,
        "[{bar: 8}][0].bar");
  }

  @Test
  public void testRestObjectPattern() {
    testSame(
        """
        function countArgs(...{length}) {
          return length;
        }
        countArgs(1, 1, 1, 1, 1);
        """);

    testSame(
        """
        function countArgs(x, ...{length}) {
          return length;
        }
        countArgs(1, 1, 1, 1, 1);
        """);

    testSame(
        """
        function countArgs(x, ...{length: length}) {
          return length;
        }
        countArgs(1, 1, 1, 1, 1);
        """);

    testSame(
        """
        function f(...{'a': x}) {
          return x;
        }
        f(null,null,null,3,null);
        """);

    testSame(
        """
        function f(...{3: x}) {
          return x;
        }
        f(null,null,null,3,null);
        """);

    testSame(
        """
        function f(...{x: y}) {
          return y;
        }
        f(null,null,null,3,null);
        """);

    testSame(
        """
        function f(...{p: x, 3:y}) {
          return y;
        }
        f(null, null, null, 3, null);
        """);
  }

  @Test
  public void testObjectPatternParam() {
    testSame(
        """
        function foo({x}) {
          return x+1;
        }
        foo({x:5});
        """);

    testSame(
        """
        function foo({x:y}) {
          return y+1;
        }
        foo({x:5});
        """);

    testSame(
        """
        function foo({'x':y}) {
          return y+1;
        }
        foo({x:5});
        """);

    testSame(
        """
        function foo({x}, {y}) {
          return x+y;
        }
        foo({x:5}, {y:6});
        """);

    testSame(
        """
        function foo({x}, {y}) {
          return x+1;
        }
        foo({x:5}, {y:6});
        """);

    testSame(
        """
        function foo({x: {y}}) {
          return y + 1;
        }
        foo(obj);
        """);

    testSame(
        """
        function foo({a: b, x: {y}}) {
          return y + 1;
        }
        foo(obj);
        """);

    testSame(
        """
        function f({x}) {
          return x;
        }
        class Foo {constructor() {this.x = 0;}}
        f(new Foo());
        """);

    testSame(
        """
        function f({x}) {
          alert(x);
          alert(x)
        }
        class Foo {constructor() {this.x = 0;}}
        f(new Foo());
        """);

    testSame(
        """
        function f({x}, {y}) {
          alert(x);
          alert(x);
          return y;
        }
        class Foo {constructor() {this.x = 0;}}
        f(new Foo(), {y:6});
        """);

    testSame(
        """
        function f({x, y}) {
          return x + y;
        }
        f(obj);
        """);

    testSame(
        """
        function f({x, y}, {z}) {
          alert(z);
          return x + y;
        }
        f(obj, new Foo());
        """);

    testSame(
        """
        function f({x, y}) {
          return x + y;
        }
        f(getArg());
        """);

    testSame(
        """
        function f({a, b, c}) {
          return b + c;
        }
        f(x);
        """);

    testSame(
        """
        function f({3:x}) {
          return x;
        }
        f({3:1});
        """);

    testSame(
        """
        function f({x:{3:y}}) {
          return y;
        }
        f({x:{3:1}});
        """);

    testSame(
        """
        function f({p: x, 3: y}) {
          return x;
        }
        f({p:1, 3:2});
        """);

    testSame(
        """
        function f({prop1, prop2}) {
          return prop1;
        }
        f({prop1:5, prop2:6});
        """);

    testSame(
        """
        function f({'foo bar':x}) {
          return x;
        }
        f({'foo bar': 1});
        """);

    testSame(
        """
        function f({'foo_bar':x}) {
          return x;
        }
        f({'foo_bar': 1});
        """);

    testSame(
        """
        function f({123: x}) {
         return x;
        }
        f({123: 1});
        """);

    testSame(
        """
        function f({'1foo': x}) {
          return x;
        }
        f({'1foo': 1});
        """);

    testSame(
        """
        function f({foo1 : x}) {
          return x;
        }
        f({foo1 : 1});
        """);

    testSame(
        """
        function f({'foo1': x}) {
          return x;
        }
        f({'foo1': 1});
        """);

    testSame(
        """
        function f({$foo : x}) {
          return x;
        }
        f({$foo : 1});
        """);

    testSame(
        """
        function f({_foo : x}) {
          return x;
        }
        f({_foo : 1});
        """);
  }

  @Test
  public void testDefaultObjectPatternParam() {
    testSame(
        """
        function foo({x} = {x:5}) {
          return x+1;
        }
        foo();
        """);

    testSame(
        """
        function foo({x} = {x:5}, {y} = {y:3}) {
          return x+y;
        }
        foo();
        """);

    testSame(
        """
        let defaultObj = {x: 5};
        function foo({x} = defaultObj) {
          return x;
        }
        foo();
        """);

    testSame(
        """
        function f({a, b, c} = {a:1, b:2, c:3}) {
         return b+c;
        }
        f();
        """);

    testSame(
        """
        function f({p:x, 3:y} = {p:1, 3:2}) {
         return x+y;
        }
        f();
        """);

    testSame(
        """
        function foo(a, { b = '', c = '' } = {}) {
          return a;
        }
        foo();
        """);
  }

  @Test
  public void testObjectPatternParamWithMultipleDestructuredNames() {
    testSame(
        """
        function f({x, y}) {
          return x + y;
        }
        alert(f({x: sideEffects1(), y: sideEffects2()}));
        """);
  }

  @Test
  public void testArrayPatternParam() {
    testSame("function f([x]) { return x; } alert(f([3]));");
  }

  @Test
  public void testObjectPatternParamWithDefaults() {
    testSame(
        """
        function foo({x = 3}) {
          return x;
        }
        alert(foo({}));
        """);

    testSame(
        """
        function foo({x = 3}) {
          return x;
        }
        alert(foo({x: 3}));
        """);

    testSame(
        """
        function foo({x = 3}, y) {
          return y;
        }
        alert(foo({x: 3}, 4));
        """);

    testSame(
        """
        function foo({x = 3} = {}) {
          return x;
        }
        alert(foo());
        """);

    testSame(
        """
        function foo(x, ...{length = 5}) {
          return x * length;
        }
        alert(foo(3));
        """);
  }

  @Test
  public void testDefaultParam() {
    testSame(
        """
        function foo(a, b = 1) {
          return a + b;
        }
        foo(1);
        """);

    testSame(
        """
        function foo(a, b = 1) {
          return a + b;
        }
        foo(1, 2);
        """);

    testSame(
        """
        function foo(a = 1, b = 2) {
          return a + b;
        }
        foo(3, 4);
        """);

    testSame(
        """
        function foo(a, b = {foo: 5}) {
          return a + b.foo;
        }
        foo(3, {foo: 9});
        """);

    testSame(
        """
        function foo(a, b = {'foo': 5}) {
          return a + b['foo'];
        }
        foo(3, {'foo': 9});
        """);

    testSame(
        """
        function foo(a, b = {foo: 5, bar: 6}) {
          return a + b.foo + b.bar;
        }
        foo(3, {foo: 1, bar: 2});
        """);

    testSame(
        """
        function foo(a, b = {foo: 5}) {
          return a + b.foo;
        }
        foo(3);
        """);

    testSame(
        """
        function foo(a, b = {foo: 5, bar: 6}) {
          return a + b.foo + b.bar;
        }
        foo(3, {foo: 1});
        """);

    testSame(
        """
        function foo(a, b = [1, 2]) {
          return a + b[1];
        }
        foo(3, [7, 8]);
        """);

    testSame(
        """
        function foo(a, b = []) {
          return a + b[1];
        }
        foo(3, [7, 8]);
        """);
  }

  @Test
  public void testDefaultParam_argIsUndefined() {
    testSame(
        """
        function foo(a, b = 1) {
          return a + b;
        }
        foo(1, undefined);
        """);
  }

  @Test
  public void testDefaultParam_argIsUnknown() {
    testSame(
        """
        function foo(a, b = 1) {
          return a + b;
        }
        foo(1, x);
        """);
  }

  @Test
  public void testDefaultParam_withAssign() {
    testSame(
        """
        function foo(x = undefined) {
          if (!x) {
            x = 2;
          }
          return x;
        }
        foo(4);
        """);
  }

  @Test
  public void testNestedDefaultParam() {
    testSame(
        """
        function foo(a = b = 1) {
          return a;
        }
        foo();
        """);

    testSame(
        """
        function foo(c = {a:(b = 1)}) {
          return c;
        }
        foo();
        """);
  }

  @Test
  public void testSpreadCall_withKnownArray_isNotInlined_andFunctionIsPreserved() {
    testSame(
        """
        function foo(x, y) {
          return x + y;
        }
        var args = [0, 1];
        foo(...args);
        """);
  }

  @Test
  public void testSpreadCall_withKnownArray_andLiteralArg_isNotInlined_andFunctionIsPreserved() {
    testSame(
        """
        function foo(x, y, z) {
          return x + y + z;
        }
        var args = [0, 1];
        foo(2, ...args);
        """);
  }

  @Test
  public void testSpreadCall_withArrayLiteral_isNotInlined_andFunctionIsPreserved() {
    testSame(
        """
        function foo(x, y) {
          return x + y;
        }
        foo(...[0, 1]);
        """);
  }

  @Test
  public void testSpreadCall_withArrayLiteral_isNotInlined_andFunctionIsPreserved_optChain() {
    testSame(
        """
        function foo(x, y) {
          return x + y;
        }
        foo?.(...[0, 1]);
        """);
  }

  @Test
  public void testSpreadCall_withMultipleSpreads_isNotInlined_andFunctionIsPreserved() {
    testSame(
        """
        function foo(x, y) {
          return x + y;
        }
        var args = [0];
        foo(...args, ...[1]);
        """);
  }

  @Test
  public void testSpreadCall_withArrayLiteral_forRestParam_isNotInlined_andFunctionIsPreserved() {
    testSame(
        """
        function foo(...args) {
          return args.length;
        }
        foo(...[0,1]);
        """);
  }

  @Test
  public void testSpreadCall_withKnownArray_intoIife_isNotInlined() {
    testSame(
        """
        var args = [0, 1];
        (function foo(x, y) { return x + y; })(...args);
        """);
  }

  @Test
  public void testSpreadCall_withKnownArray_andLiteralArg_intoIife_isNotInlined() {
    testSame(
        """
        var args = [0, 1];
        (function foo(x, y, z) {
          return x + y + z;
        })(2, ...args);
        """);
  }

  @Test
  public void testSpreadCall_withArrayLiteral_intoIife_isNotInlined() {
    testSame("(function (x, y) {  return x + y; })(...[0, 1]);");
  }

  @Test
  public void testSpreadCall_withSpreadAsIndirectDescendent_call_isInlined() {
    test(
        """
        function foo(x) {
          return x;
        }

        foo(bar(...arg));
        """,
        """
        bar(...arg);
        """);
  }

  @Test
  public void testSpreadCall_withSpreadAsIndirectDescendent_arrrayLit_isInlined() {
    test(
        """
        function foo(x) {
          return x;
        }

        foo([...arg]);
        """,
        """
        [...arg];
        """);
  }

  @Test
  public void testSpreadCall_withBlockInlinableFunction_asSpreadArg_isInlined() {
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        bar(...foo(arg));
        """,
        """
        var JSCompiler_temp_const$jscomp$0 = bar;
        var JSCompiler_inline_result$jscomp$1;
        {
          var x$jscomp$inline_2 = arg;
          qux();
          JSCompiler_inline_result$jscomp$1 = x$jscomp$inline_2;
        }

        JSCompiler_temp_const$jscomp$0(...JSCompiler_inline_result$jscomp$1);
        """);
  }

  @Test
  public void testSpreadCall_withBlockInlinableFunction_withPrecedingSpreadArg_isInlined() {
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        bar(...zap(), foo(arg));
        """,
        """
        var JSCompiler_temp_const$jscomp$1 = bar;
        var JSCompiler_temp_const$jscomp$0 = [...zap()];
        var JSCompiler_inline_result$jscomp$2;
        {
          var x$jscomp$inline_3 = arg;
          qux();
          JSCompiler_inline_result$jscomp$2 = x$jscomp$inline_3;
        }

        JSCompiler_temp_const$jscomp$1(
          ...JSCompiler_temp_const$jscomp$0,
          JSCompiler_inline_result$jscomp$2);
        """);
  }

  @Test
  public void
      testSpreadCall_withBlockInlinableFunction_withPrecedingSpreadArg_isInlined_optChain() {
    test(
        """
        function foo(x) {
          qux?.(); // Something with a side-effect.
          return x;
        }

        bar?.(...zap?.(), foo?.(arg));
        """,
        """
        let JSCompiler_temp_const$jscomp$0;
        // Optional call to `bar` gets decomposed to if- else.
        if ((JSCompiler_temp_const$jscomp$0 = bar) == null) {
          void 0;
        } else {
          var JSCompiler_temp_const$jscomp$2 = [...zap?.()];
          var JSCompiler_inline_result$jscomp$3;
          {
            qux?.();
            JSCompiler_inline_result$jscomp$3 = arg;
          }
          JSCompiler_temp_const$jscomp$0(...JSCompiler_temp_const$jscomp$2,
         JSCompiler_inline_result$jscomp$3);
        }
        """);
  }

  @Test
  public void methodCallIsDecomposed() {
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        bar.method(foo(arg));
        """,
        """
        var JSCompiler_temp_const$jscomp$1 = bar;
        var JSCompiler_temp_const$jscomp$0 = JSCompiler_temp_const$jscomp$1.method;
        var JSCompiler_inline_result$jscomp$2;
        {
          var x$jscomp$inline_3 = arg;
          qux();
          JSCompiler_inline_result$jscomp$2 = x$jscomp$inline_3;
         }
        JSCompiler_temp_const$jscomp$0.call(JSCompiler_temp_const$jscomp$1,
         JSCompiler_inline_result$jscomp$2);
        """);
  }

  @Test
  public void methodCallOnThisIsDecomposed() {
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        class C {
          method() {
            this.privateMethod(foo(arg));
          }
        }
        """,
        """
        class C {
          method() {
            var JSCompiler_temp_const$jscomp$0 = this.privateMethod
            var JSCompiler_inline_result$jscomp$1;
            {
              var x$jscomp$inline_2 = arg;
              qux();
              JSCompiler_inline_result$jscomp$1 = x$jscomp$inline_2;
            }
            JSCompiler_temp_const$jscomp$0.call(
                this,
                JSCompiler_inline_result$jscomp$1);
          }
        }
        """);
  }

  @Test
  public void methodCallOnSuperIsDecomposed() {
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        class B {
          privateMethod(y) {}
        }

        class C extends B {
          method() {
            super.privateMethod(foo(arg));
          }
        }
        """,
        """
        class B {
          privateMethod(y) {}
        }

        class C extends B {
          method() {
            var JSCompiler_temp_const$jscomp$0 = super.privateMethod;
            var JSCompiler_inline_result$jscomp$1
            {
              var x$jscomp$inline_2 = arg;
              qux();
              JSCompiler_inline_result$jscomp$1 = x$jscomp$inline_2;
            }
            JSCompiler_temp_const$jscomp$0.call(
                this,
                JSCompiler_inline_result$jscomp$1);
          }
        }
        """);
  }

  @Test
  public void methodCallWithNestedCallIsInlined() {
    this.assumeStrictThis = true;
    test(
        """
        function foo(x) {
          qux(); // Something with a side-effect.
          return x;
        }

        function bar(x) {
          return x;
        }
        bar.call(null, foo(arg));
        """,
        """
        var JSCompiler_inline_result$jscomp$0;
        {
          var x$jscomp$inline_1 = arg;
          qux();
          JSCompiler_inline_result$jscomp$0 = x$jscomp$inline_1;
        }
        {
          JSCompiler_inline_result$jscomp$0;
        }
        """);
  }

  @Test
  public void testGeneratorFunction() {
    testSame(
        """
        function* foo() {}
        var bar = foo();
        """);

    testSame(
        """
        function* foo() {
          yield 'X';
          return 'Y';
        }
        var bar = foo();
        """);

    testSame(
        """
        function* foo() {
          yield 'X';
        }
        foo();
        """);

    testSame(
        """
        function* foo() {
          return 1;
        }
        foo();
        """);
  }

  @Test
  public void testAsyncFunction() {
    testSame(
        """
        async function foo() {}
        foo().then(result => { alert(result); } );
        """);

    testSame(
        """
        async function foo() {
          return 'Y';
        }
        foo().then(result => { alert(result); } );
        """);

    // We could possibly inline here since the return statement already contains a promise.
    testSame(
        """
        async function foo() {
         return new Promise((resolve, reject) => { resolve('Success'); } );
        }
        foo().then(result => { alert(result); } );
        """);
  }

  @Test
  public void testFunctionReferencingLetInNonGlobalBlock() {
    testSame(
        """
        if (true) {
          let value = 1;
          var g = function() {
            return value + 1;
          }
        }
        alert(g(10));
        """);
  }

  @Test
  public void testNotInliningFunctionWithSuper() {
    // Super field accessor arrow functions like this one are used for transpilation of some
    // features such as async functions and async generators. If inlined, it becomes a syntax error
    // as the inner function has no super.
    testSame(
        """
        class A { m(){ return 1 } };
        class B extends A {
          m() {
            const super$m = () => super.m;
            const jscomp$this = this;
            return function*() {
              yield super$m().call(jscomp$this);
            };
          }
        }
        """);
  }

  @Test
  public void testClassField() {
    test(
        """
        class C {
          constructor() {
           /** @type {someType} */
            this.field = function f(){ function g() {} g() };
          }
        }
        C.staticField = function f(){ function g() {} g() };
        """,
        """
        class C {
          constructor() {
            this.field = function f(){ void 0 };
          }
        }
        C.staticField = function f$jscomp$1() { void 0};
        """);
    test(
        """
        class C {
          field = function f(){ function g() {} g() };
          static staticField = function f(){ function g() {} g() };
        }
        """,
        """
        class C {
          field = function f(){ void 0 };
          static staticField = function f$jscomp$1() { void 0};
        }
        """);

    test(
        """
        class C {
          constructor() {
           /** @return {number} */
            this.field = function() {return 1 };
          }
        }
        C.staticField = function() {return 1 };
        console.log(new C().field());
        console.log(C.staticField());
        """,
        """
        class C {
          constructor() {
            this.field = function() {return 1 };
          }
        }
        C.staticField = function() {return 1 };
        console.log(new C().field());
        console.log(C.staticField());
        """);

    testSame(
        """
        class C {
          field = function() {return 1 };
          static staticField = function() {return 1 };
        }
        console.log(new C().field());
        console.log(C.staticField());
        """);

    testSame(
        """
        function f({x}) {
          return x;
        }
        class Foo { x = 0; }
        f(new Foo());
        """);
  }

  @Test
  public void testClassField_doesNotInlineValueThatChanges() {
    testSame(
        """
        let nextVal = 0;
        function getNextVal() {
          nextVal++;
          return nextVal;
        }

        // Class expression.
        const Uid = class {
          // Should not inline `nextVal` because it changes.
          val = getNextVal();
        };
        """);

    testSame(
        """
        let nextVal = 0;
        function getNextVal() {
          nextVal++;
          return nextVal;
        }

        // Class declaration.
        class Uid {
          // Should not inline `nextVal` because it changes.
          val = getNextVal();
        }
        """);
  }

  @Test
  public void testVariableUsedAsArgumentAndReassignedInFollowingArgument() {
    // See https://github.com/google/closure-compiler/issues/4115.
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
          return f(a, b + 1, b = c);
        };
        """,
        """
        window.g = function(a, b, c) {
         var JSCompiler_inline_result$jscomp$0;
        {
          var value$jscomp$inline_1 = a;
          var begin$jscomp$inline_2 = b + 1;
          var end$jscomp$inline_3 = b = c;
          JSCompiler_inline_result$jscomp$0 =
           value$jscomp$inline_1.slice(begin$jscomp$inline_2, end$jscomp$inline_3);
        }
        return JSCompiler_inline_result$jscomp$0;
        };
        """);
  }

  @Test
  public void testVariableUsedAsArgumentAndModifiedInFollowingFunctionArgumentCall() {
    test(
        """
        let text = 'abc'
        let index = 1

        // This function is necessary. Inlining the slice call will make temps for both args
        function slice(begin, end) {
          return text.slice(begin, end);
        }

        function getEndIndex() {
          if (index)
            // This line is the only relevant line in this function - the index is being modified.
            // Everything else is artifical complexity to prevent the function from being inlined
            return ++index;

          return getEndIndex();
        }

        window.bug = slice(index - 1, getEndIndex());
        """,
"""
let text = 'abc';
let index = 1;
function getEndIndex() {
  if (index) {
    return ++index;
  }
  return getEndIndex();
}
var JSCompiler_temp_const$jscomp$0 = window;
var JSCompiler_inline_result$jscomp$1;
{
  var begin$jscomp$inline_2 = index - 1;
  var end$jscomp$inline_3 = getEndIndex();
  JSCompiler_inline_result$jscomp$1 = text.slice(begin$jscomp$inline_2, end$jscomp$inline_3);
}
JSCompiler_temp_const$jscomp$0.bug = JSCompiler_inline_result$jscomp$1;
""");
  }

  @Test
  public void testVariableUsedAsArgumentAndReassignedInFollowingArgument2() {
    // See https://github.com/google/closure-compiler/issues/4115.
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
          return f(a, b.x + 1, b.x = c);
        };
        """,
"""
window.g = function(a, b, c) {
    var JSCompiler_inline_result$jscomp$0;
{
  var value$jscomp$inline_1 = a;
  var begin$jscomp$inline_2 = b.x + 1;
  var end$jscomp$inline_3 = b.x = c;
  JSCompiler_inline_result$jscomp$0 = value$jscomp$inline_1.slice(begin$jscomp$inline_2, end$jscomp$inline_3);
}
return JSCompiler_inline_result$jscomp$0;
};
""");
  }

  @Test
  public void testDifferentPropertiesAsArguments() {
    // See https://github.com/google/closure-compiler/issues/4115.
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
          return f(a, b.x + 1, b.y = c);
        };
        """,
"""
window.g = function(a, b, c) {
  var JSCompiler_inline_result$jscomp$0;
  {
    var value$jscomp$inline_1 = a;
    var begin$jscomp$inline_2 = b.x + 1;
    var end$jscomp$inline_3 = b.y = c;
    JSCompiler_inline_result$jscomp$0 = value$jscomp$inline_1.slice(begin$jscomp$inline_2, end$jscomp$inline_3);
  }
  return JSCompiler_inline_result$jscomp$0;
};
""");
  }

  @Test
  public void testVariableUsedAsArgumentAndReassignedInPreviousArgument() {
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
          return f(a, b = c, b + 1);
        };
        """,
"""
window.g = function(a, b, c) {
  var JSCompiler_inline_result$jscomp$0;
  {
// Evaluates all args until `b=c` in order
    var value$jscomp$inline_1 = a;
    var begin$jscomp$inline_2 = b = c;
    JSCompiler_inline_result$jscomp$0 = value$jscomp$inline_1.slice(begin$jscomp$inline_2, b + 1);
  }
  return JSCompiler_inline_result$jscomp$0;
};
""");
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes() {
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
        // No temp needed as none of the arguments mutate state.
          return f(a, b, b + 1);
        };
        """,
        """
        window.g = function(a, b, c) {
          return a.slice(b, b + 1);
        };
        """);
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes2() {
    test(
        """
        function f(value, begin, end) {
         return value.slice(begin, end);
        }
        window.g = function(a, b, c) {
          return f(a, ++b, b++);
        };
        """,
"""
window.g = function(a, b, c) {
  var JSCompiler_inline_result$jscomp$0;
  {
    var value$jscomp$inline_1 = a;
    var begin$jscomp$inline_2 = ++b;
    var end$jscomp$inline_3 = b++;
    JSCompiler_inline_result$jscomp$0 = value$jscomp$inline_1.slice(begin$jscomp$inline_2, end$jscomp$inline_3);
  }
  return JSCompiler_inline_result$jscomp$0;
};
""");
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes3() {
    test(
        """
        function add(a,b) {
         return a+b;
        }
        window.g = function(value) {
          return add(++value, value++);
        };
        """,
        """
        window.g = function(value) {
          var JSCompiler_inline_result$jscomp$0;
          {
            var a$jscomp$inline_1 = ++value;
            var b$jscomp$inline_2 = value++;
            JSCompiler_inline_result$jscomp$0 = a$jscomp$inline_1 + b$jscomp$inline_2;
          }
          return JSCompiler_inline_result$jscomp$0;
        };
        """);
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes4() {
    test(
        """
        function add(a,b) {
         return a+b;
        }
        window.g = function(value) {
          return add(++value, ++value);
        };
        """,
        """
        window.g = function(value) {
          var JSCompiler_inline_result$jscomp$0;
          {
            var a$jscomp$inline_1 = ++value;
            var b$jscomp$inline_2 = ++value;
            JSCompiler_inline_result$jscomp$0 = a$jscomp$inline_1 + b$jscomp$inline_2;
          }
          return JSCompiler_inline_result$jscomp$0;
        };
        """);
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes5() {
    test(
        """
        function add(value, a,b) {
         return value+a+b;
        }
        window.g = function(value) {
          return add(value, ++value, ++value);
        };
        """,
"""
window.g = function(value$jscomp$1) {
  var JSCompiler_inline_result$jscomp$0;
  {
    var value$jscomp$inline_1 = value$jscomp$1;
    var a$jscomp$inline_2 = ++value$jscomp$1;
    var b$jscomp$inline_3 = ++value$jscomp$1;
    JSCompiler_inline_result$jscomp$0 = value$jscomp$inline_1 + a$jscomp$inline_2 + b$jscomp$inline_3;
  }
  return JSCompiler_inline_result$jscomp$0;
};
""");
  }

  @Test
  public void testVariableUsedAsArgumentMultipleTimes6() {
    test(
        """
        value = 10;
        function add(a,b) {
         return (value = 0)+a+b;
        }
        window.g = function() {
          return add(++value, ++value);
        };
        """,
"""
value = 10;
window.g = function() {
  var JSCompiler_inline_result$jscomp$0;
  {
    var a$jscomp$inline_1 = ++value;
    var b$jscomp$inline_2 = ++value;
    JSCompiler_inline_result$jscomp$0 = (value = 0 ) + a$jscomp$inline_1 + b$jscomp$inline_2;
  }
  return JSCompiler_inline_result$jscomp$0;
};
""");
  }
}
