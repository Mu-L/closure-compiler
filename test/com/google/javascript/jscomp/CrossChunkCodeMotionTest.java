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

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CrossChunkCodeMotion}. */
@RunWith(JUnit4.class)
public final class CrossChunkCodeMotionTest extends CompilerTestCase {

  private static final String EXTERNS = "alert";
  private boolean parentModuleCanSeeSymbolsDeclaredInChildren = false;

  public CrossChunkCodeMotionTest() {
    super(EXTERNS);
  }

  @Override
  protected int getNumRepetitions() {
    // A single run should be sufficient to move all definitions to their final destinations.
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    disableScriptFeatureValidation();
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CrossChunkCodeMotion(
        compiler, compiler.getChunkGraph(), parentModuleCanSeeSymbolsDeclaredInChildren);
  }

  @Test
  public void testFunctionMovement1() {
    // This tests lots of things:
    // 1) f1 is declared in m1, and used in m2. Move it to m2
    // 2) f2 is declared in m1, and used in m3 twice. Move it to m3
    // 3) f3 is declared in m1, and used in m2+m3. It stays put
    // 4) g declared in m1 and never used. It stays put
    // 5) h declared in m2 and never used. It stays put
    // 6) f4 declared in m1 and used in m2 as var. It moves to m2

    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk(
                """
                function f1(a) { alert(a); }
                function f2(a) { alert(a); }
                function f3(a) { alert(a); }
                function f4() { alert(1); }
                function g() { alert('ciao'); }
                """)
            // m2
            .addChunk("f1('hi'); f3('bye'); var a = f4; function h(a) { alert('h:' + a); }")
            // m3
            .addChunk("f2('hi'); f2('hi'); f3('bye');")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "function f3(a) { alert(a); } function g() { alert('ciao'); }",
            // m2
            """
            function f1(a) { alert(a); }
            function f4() { alert(1); }
            f1('hi'); f3('bye'); var a = f4;
            function h(a) { alert('h:' + a); }
            """,
            // m3
            "function f2(a) { alert(a); } f2('hi'); f2('hi'); f3('bye');"));
  }

  @Test
  public void testFunctionMovement2() {
    // having f declared as a local variable should block the migration to m2
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("function f(a) { alert(a); } function g() {var f = 1; f++}")
            // m2
            .addChunk("f(1);")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "function g() {var f = 1; f++}",
            // m2
            "function f(a) { alert(a); } f(1);"));
  }

  @Test
  public void testFunctionMovement3() {
    // having f declared as a arg should block the migration to m2
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("function f(a) { alert(a); } function g(f) {f++}")
            // m2
            .addChunk("f(1);")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "function g(f) {f++}",
            // m2
            "function f(a) { alert(a); } f(1);"));
  }

  @Test
  public void testFunctionMovement4() {
    // Try out moving a function which returns a closure
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("function f(){return function(a){}}")
            // m2
            .addChunk("var a = f();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function f(){return function(a){}} var a = f();"));
  }

  @Test
  public void testFunctionMovement5() {
    // Try moving a recursive function [using factorials for kicks]
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("function f(n){return (n<1)?1:f(n-1)}")
            // m2
            .addChunk("var a = f(4);")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function f(n){return (n<1)?1:f(n-1)} var a = f(4);"));
  }

  @Test
  public void testFunctionMovement5b() {
    // Try moving a recursive function declared differently.
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var f = function(n){return (n<1)?1:f(n-1)};")
            // m2
            .addChunk("var a = f(4);")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var f = function(n){return (n<1)?1:f(n-1)}; var a = f(4);"));
  }

  @Test
  public void testFunctionMovement5c() {
    // Try moving a recursive function declared differently, in a nested block scope.
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};")
            // m2
            .addChunk("var a = f(4);")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            """
            var f = function(n){if(true){if(true){return (n<1)?1:f(n-1)}}};
            var a = f(4);
            """));
  }

  @Test
  public void testFunctionMovement6() {
    // Try out moving to the common ancestor
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("function f(){return 1}")
            // m2
            .addChunk("var a = f();")
            // m3
            .addChunk("var b = f();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function f(){return 1} var a = f();",
            // m3
            "var b = f();"));
  }

  @Test
  public void testFunctionMovement7() {
    // Try out moving to the common ancestor with deeper ancestry chain
    JSChunk[] chunks =
        JSChunkGraphBuilder.forUnordered()
            // m1
            .addChunk("function f(){return 1}")
            // m2
            .addChunk("")
            // m3
            .addChunk("var a = f();")
            // m4
            .addChunk("var b = f();")
            // m5
            .addChunk("var c = f();")
            .build();

    chunks[1].addDependency(chunks[0]);
    chunks[2].addDependency(chunks[1]);
    chunks[3].addDependency(chunks[1]);
    chunks[4].addDependency(chunks[1]);

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function f(){return 1}",
            // m3
            "var a = f();",
            // m4
            "var b = f();",
            // m5
            "var c = f();"));
  }

  @Test
  public void testFunctionMovement8() {
    // Check what happens with named functions
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("var v = function f(){return 1}")
            // m2
            .addChunk("v();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var v = function f(){return 1}; v();"));
  }

  @Test
  public void testFunctionNonMovement1() {
    // This tests lots of things:
    // 1) we can't move it if it is a class with non-const attributes accessed
    // 2) if it's in an if statement, we can't move it
    // 3) if it's in an while statement, we can't move it [with some extra
    // block elements]
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk(
                    """
                    function f(){};f.prototype.bar=new f;
                    if(a)function f2(){}
                    {{while(a)function f3(){}}}
                    """)
                .addChunk("var a = new f();f2();f3();")
                .build()));
  }

  @Test
  public void testFunctionNonMovement2() {
    // A generic case where 2 modules depend on the first one. But it's the
    // common ancestor, so we can't move.
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){return 1}")
                .addChunk("var a = f();")
                .addChunk("var b = f();")
                .build()));
  }

  @Test
  public void testEs6ClassMovement1() {
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("class f { bar() {} }")
                .addChunk("var a = new f();")
                .build()),
        expected("", "class f { bar() {} } var a = new f();"));
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("var f = class { bar() {} };")
                .addChunk("var a = new f();")
                .build()),
        expected("", "var f = class { bar() {} }; var a = new f();"));
  }

  @Test
  public void testClassMovement1() {
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){};")
                .addChunk("var a = new f();")
                .build()),
        expected("", "function f(){} f.prototype.bar=function (){}; var a = new f();"));
  }

  @Test
  public void testEs6ClassMovement_instanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("class f { bar(){} } 1 instanceof f;")
                .addChunk("var a = new f();")
                .build()),
        expected(
            "'function' == typeof f && 1 instanceof f;", "class f { bar(){} } var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){}; 1 instanceof f;")
                .addChunk("var a = new f();")
                .build()),
        expected(
            "'function' == typeof f && 1 instanceof f;",
            "function f(){} f.prototype.bar=function (){}; var a = new f();"));
  }

  @Test
  public void testEs6ClassMovement_instanceofTurnedOff() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("class f { bar(){} } 1 instanceof f;")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testClassMovement_instanceofTurnedOff() {
    parentModuleCanSeeSymbolsDeclaredInChildren = false;
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){}; 1 instanceof f;")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testEs6ClassMovement_instanceof2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("class f { bar(){} } (true && 1 instanceof f);")
                .addChunk("var a = new f();")
                .build()),
        expected(
            "(true && ('function' == typeof f && 1 instanceof f));",
            "class f { bar(){} } var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceof2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){}; (true && 1 instanceof f);")
                .addChunk("var a = new f();")
                .build()),
        expected(
            "(true && ('function' == typeof f && 1 instanceof f));",
            "function f(){} f.prototype.bar=function (){}; var a = new f();"));
  }

  @Test
  public void testClassMovement_alreadyGuardedInstanceof() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk(
                    """
                    function f(){} f.prototype.bar=function (){};
                    (true && ('undefined' != typeof f && 1 instanceof f));
                    """)
                .addChunk("var a = new f();")
                .build()),
        expected(
            "(true && ('undefined' != typeof f && 1 instanceof f));",
            "function f(){} f.prototype.bar=function (){}; var a = new f();"));
  }

  @Test
  public void testClassMovement_alreadyGuardedInstanceof_functionGuard() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk(
                    """
                    function f(){} f.prototype.bar=function (){};
                    (true && ('function' == typeof f && 1 instanceof f));
                    """)
                .addChunk("var a = new f();")
                .build()),
        expected(
            "(true && ('function' == typeof f && 1 instanceof f));",
            "function f(){} f.prototype.bar=function (){}; var a = new f();"));
  }

  @Test
  public void testClassMovement_instanceof3() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){}; f instanceof 1")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testClassMovement_instanceof_noRewriteRequired() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=function (){}; 1 instanceof f; new f;")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testClassMovement_instanceof_noRewriteRequired2() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("function f(){} f.prototype.bar=function (){}; new f;")
                .addChunk("1 instanceof f;")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testEs6ClassMovement2() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("class f {} f.prototype.bar=3; f.prototype.baz=5;")
                // m2
                .addChunk("f.prototype.baq = 7;")
                // m3
                .addChunk("f.prototype.baz = 9;")
                // m4
                .addChunk("var a = new f();")
                .build()),
        expected(
            // m1
            "",
            // m2
            "",
            // m3
            "",
            // m4
            """
            class f {}
            f.prototype.bar = 3;
            f.prototype.baz = 5;
            f.prototype.baq = 7;
            f.prototype.baz = 9;
            var a = new f();
            """));
  }

  @Test
  public void testClassMovement2() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function f(){} f.prototype.bar=3; f.prototype.baz=5;")
                // m2
                .addChunk("f.prototype.baq = 7;")
                // m3
                .addChunk("f.prototype.baz = 9;")
                // m4
                .addChunk("var a = new f();")
                .build()),
        expected(
            // m1
            "",
            // m2
            "",
            // m3
            "",
            // m4
            """
            function f(){}
            f.prototype.bar=3;
            f.prototype.baz=5;
            f.prototype.baq = 7;
            f.prototype.baz = 9;
            var a = new f();
            """));
  }

  @Test
  public void testClassMovement3() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var f = function() {}; f.prototype.bar=3; f.prototype.baz=5;")
                // m2
                .addChunk("f = 7;")
                // m3
                .addChunk("f = 9;")
                // m4
                .addChunk("f = 11;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "",
            // m3
            "",
            // m4
            """
            var f = function() {};
            f.prototype.bar=3;
            f.prototype.baz=5;
            f = 7;
            f = 9;
            f = 11;
            """));
  }

  @Test
  public void testClassMovement4() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f(){} f.prototype.bar=3; f.prototype.baz=5;")
                .addChunk("f.prototype.baq = 7;")
                .addChunk("var a = new f();")
                .build()));
  }

  @Test
  public void testClassMovement5() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forUnordered()
            // m1
            .addChunk("function f(){} f.prototype.bar=3; f.prototype.baz=5;")
            // m2
            .addChunk("")
            // m3
            .addChunk("f.prototype.baq = 7;")
            // m4
            .addChunk("var a = new f();")
            .build();

    chunks[1].addDependency(chunks[0]);
    chunks[2].addDependency(chunks[1]);
    chunks[3].addDependency(chunks[1]);

    // m4 +
    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function f(){} f.prototype.bar=3; f.prototype.baz=5;",
            // m3
            "f.prototype.baq = 7;",
            // m4 +
            "var a = new f();"));
  }

  @Test
  public void testClassMovement6() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function Foo(){} function Bar(){} goog.inherits(Bar, Foo); new Foo();")
                // m2
                .addChunk("new Bar();")
                .build()),
        expected(
            // m1
            "function Foo(){} new Foo();",
            // m2
            "function Bar(){} goog.inherits(Bar, Foo); new Bar();"));
  }

  @Test
  public void testEs6ClassMovement6() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("class Foo{} class Bar extends Foo {} new Foo();")
                // m2
                .addChunk("new Bar();")
                .build()),
        expected(
            // m1
            "class Foo {} new Foo();",
            // m2
            "class Bar extends Foo {} new Bar();"));
  }

  @Test
  public void testClassMovement7() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function Foo(){} function Bar(){} goog.inherits(Bar, Foo); new Bar();")
                // m2
                .addChunk("new Foo();")
                .build()));
  }

  @Test
  public void testEs6ClassMovement7() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()

                // m1
                .addChunk("class Foo {} class Bar extends Foo {} new Bar();")

                // m2
                .addChunk("new Foo();")
                .build()));
  }

  @Test
  public void testClassMovement8() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk(
                    """
                    function Foo(){}
                    Object.defineProperties(Foo.prototype, {a: {get:function(){return 0;}}});
                    """)

                // m2
                .addChunk("new Foo();")
                .build()),
        expected(
            "", // m1
            """
            function Foo(){}
            Object.defineProperties(Foo.prototype, {a: {get:function(){return 0;}}});
            new Foo();
            """ // m2
            ));
  }

  @Test
  public void testEs6ClassMovement8() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("class Foo { get test() { return 0; }}")
                .addChunk("new Foo();")
                .build()),
        expected(
            "", // m1
            "class Foo { get test() { return 0; }} new Foo();" // m2
            ));
  }

  @Test
  public void testClassMovement_classFields() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("class Foo { a; static b = 2; ['c'] = 3; static 'd' = 'hi'; 1 = 2;}")
                .addChunk("new Foo();")
                .build()),
        expected(
            "", "class Foo { a; static b = 2; ['c'] = 3; static 'd' = 'hi'; 1 = 2; } new Foo();"));
  }

  @Test
  public void testClassMovement_classStaticBlock1() {
    // TODO(bradfordcsmith):Ideally the class would move
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("class Foo { static { } }")
                .addChunk("new Foo();")
                .build()),
        expected("class Foo { static { } }", "new Foo();"));
  }

  @Test
  public void testClassMovement_classStaticBlock2() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var x = 1;")
                .addChunk("class Foo { static { x; } } new Foo();")
                .build()),
        expected("", "var x = 1; class Foo { static { x; } } new Foo();"));
  }

  @Test
  public void testClassMovement_classStaticBlock3() {
    // TODO(bradfordcsmith):Ideally the class and var would move to m3
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("const x = 1; var y = 2;")
            // m2
            .addChunk("class Foo { static { y = 3; } }")
            // m3
            .addChunk("new Foo();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "const x = 1;",
            // m2
            "var y =2; class Foo { static { y = 3; } } ",
            // m3
            "new Foo();"));
  }

  @Test
  public void testClassMovement_classStaticBlock4() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("var x =1;")
            // m2
            .addChunk(
                """
                class Foo {
                  static {
                    x = 2;
                  }
                }
                use(x);
                """)
            // m3
            .addChunk("new Foo();")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            """
            var x =1;
            class Foo {
              static {
                x = 2
              }
            }
            use(x);
            """,
            // m3
            "new Foo();"));
  }

  @Test
  public void testClassMovement_mixins() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    class ValueType {}
                    class Foo {}
                    ValueType.mixin(Foo, ValueType, 5, goog.reflect.objectProperty('foo', Foo))
                    """)
                .addChunk("new Foo();")
                .build()),
        expected(
            "", // m1
            """
            class ValueType {}
            class Foo {}
            ValueType.mixin(Foo, ValueType, 5, goog.reflect.objectProperty('foo', Foo))
            new Foo();
            """ // m2
            ));
  }

  @Test
  public void testPureOrBreakMyCodedStaticClassFieldIsMovable() {
    //
    //
    //
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    function wrapIt(val) { return [val]; }
                    class ImmovableClass { static x = wrapIt(123); }
                    class MovableClass { static y = /** @pureOrBreakMyCode */ wrapIt(123); }
                    """)
                .addChunk("new ImmovableClass(); new MovableClass();")
                .build()),
        expected(
            """
            function wrapIt(val) { return [val] }
            class ImmovableClass { static x = wrapIt(123);}
            """,
            """
            class MovableClass { static y = /** @pureOrBreakMyCode */ wrapIt(123); }
            new ImmovableClass(); new MovableClass();
            """));
  }

  @Test
  public void testStubMethodMovement() {
    // The method stub can move, but the unstub definition cannot, because
    // CrossChunkCodeMotion doesn't know where individual methods are used.
    // CrossChunkMethodMotion is responsible for putting the unstub definitions
    // in the right places.
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function Foo(){} Foo.prototype.bar = JSCompiler_stubMethod(x);")
                // m1
                .addChunk("Foo.prototype.bar = JSCompiler_unstubMethod(x);")
                // m2
                .addChunk("new Foo();")
                .build()),
        expected(
            // m0
            "",
            // m1
            """
            function Foo(){} Foo.prototype.bar = JSCompiler_stubMethod(x);
            Foo.prototype.bar = JSCompiler_unstubMethod(x);
            """,
            // m2
            "new Foo();"));
  }

  @Test
  public void testNoMoveSideEffectProperty() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function Foo(){}  Foo.prototype.bar = createSomething();")
                // m2
                .addChunk("new Foo();")
                .build()));
  }

  @Test
  public void testNoMoveSideEffectDefineProperties() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk(
                    """
                    function Foo(){}
                    Object.defineProperties(Foo.prototype, {a: {get: createSomething()}})
                    """)
                // m2
                .addChunk("new Foo();")
                .build()));
  }

  @Test
  public void testNoMoveSideEffectDefinePropertiesComputed() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    function Foo(){}
                    Object.defineProperties(Foo.prototype,{[test()]:{get: function() {return 10;}}})
                    """)
                .addChunk("new Foo();")
                .build()));
  }

  @Test
  public void testAssignMovement() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var f = 3; f = 5;")
                // m2
                .addChunk("var h = f;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "var f = 3; f = 5; var h = f;"));

    // don't move nested assigns
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var f = 3; var g = f = 5;")
                .addChunk("var h = f;")
                .build()));
  }

  @Test
  public void testNoClassMovement2() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var f = {}; f.h = 5;")
                // m2
                .addChunk("var h = f;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "var f = {}; f.h = 5; var h = f;"));

    // don't move nested getprop assigns
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var f = {}; var g = f.h = 5;")
                .addChunk("var h = f;")
                .build()));
  }

  @Test
  public void testLiteralMovement1() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var f = {'hi': 'mom', 'bye': function() {}};")
                // m2
                .addChunk("var h = f;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "var f = {'hi': 'mom', 'bye': function() {}}; var h = f;"));
  }

  @Test
  public void testLiteralMovement2() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var f = {'hi': 'mom', 'bye': shared};")
                .addChunk("var h = f;")
                .build()));
  }

  @Test
  public void testLiteralMovement3() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var f = ['hi', function() {}];")
                // m2
                .addChunk("var h = f;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "var f = ['hi', function() {}]; var h = f;"));
  }

  @Test
  public void testLiteralMovement4() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var f = ['hi', shared];")
                .addChunk("var h = f;")
                .build()));
  }

  @Test
  public void testStringTemplateLiteralMovement1() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("var s = 'world'; var f = `hi ${s}`;")
                // m2
                .addChunk("var h = f;")
                .build()),
        expected(
            // m1
            "",
            // m2
            "var s = 'world'; var f = `hi ${s}`; var h = f;"));
  }

  @Test
  public void testStringTemplateLiteralMovement2() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("var f = `hi ${shared()}`;")
                .addChunk("var h = f;")
                .build()));
  }

  @Test
  public void testVarMovement1() {
    // test moving a variable
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var a = 0;")
            // m2
            .addChunk("var x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var a = 0; var x = a;"));
  }

  @Test
  public void testLetConstMovement() {
    // test moving a variable
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("const a = 0;")
            // m2
            .addChunk("let x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "const a = 0; let x = a;"));
  }

  @Test
  public void testVarMovement2() {
    // Test moving 1 variable out of the block
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var a = 0; var b = 1; var c = 2;")
            // m2
            .addChunk("var x = b;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "var a = 0; var c = 2;",
            // m2
            "var b = 1; var x = b;"));
  }

  @Test
  public void testLetConstMovement2() {
    // Test moving 1 variable out of the block
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("const a = 0; const b = 1; const c = 2;")
            // m2
            .addChunk("let x = b;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "const a = 0; const c = 2;",
            // m2
            "const b = 1; let x = b;"));
  }

  @Test
  public void testVarMovement3() {
    // Test moving all variables out of the block
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var a = 0; var b = 1;")
            // m2
            .addChunk("var x = a + b;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var a = 0; var b = 1; var x = a + b;"));
  }

  @Test
  public void testLetConstMovement3() {
    // Test moving all variables out of the block
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("const a = 0; const b = 1;")
            // m2
            .addChunk("let x = a + b;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "const a = 0; const b = 1; let x = a + b;"));
  }

  @Test
  public void testVarMovement4() {
    // Test moving a function
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var a = function(){alert(1)};")
            // m2
            .addChunk("var x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var a = function(){alert(1)}; var x = a;"));
  }

  @Test
  public void testLetConstMovement4() {
    // Test moving a function
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("const a = function(){alert(1)};")
            // m2
            .addChunk("let x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "const a = function(){alert(1)}; let x = a;"));
  }

  @Test
  public void testVarMovement5() {
    // Don't move a function outside of scope
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("var a = alert;")
                .addChunk("var x = a;")
                .build()));
  }

  @Test
  public void testLetConstMovement5() {
    // Don't move a function outside of scope
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("const a = alert;")
                .addChunk("let x = a;")
                .build()));
  }

  @Test
  public void testVarMovement6() {
    // Test moving a var with no assigned value
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("var a;")
            // m2
            .addChunk("var x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var a; var x = a;"));
  }

  @Test
  public void testLetMovement6() {
    // Test moving a let with no assigned value
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            // m1
            .addChunk("let a;")
            // m2
            .addChunk("let x = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "let a; let x = a;"));
  }

  @Test
  public void testVarMovement7() {
    // Don't move a variable higher in the dependency tree
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function f() {g();} f();")
                .addChunk("function g(){};")
                .build()));
  }

  @Test
  public void testVarMovement8() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            // m1
            .addChunk("var a = 0;")
            // m2 -> m1
            .addChunk("")
            // m3 -> m2
            .addChunk("var x = a;")
            // m4 -> m2
            .addChunk("var y = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var a = 0;",
            // m3
            "var x = a;",
            // m4
            "var y = a;"));
  }

  @Test
  public void testLetConstMovement8() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            // m1
            .addChunk("const a = 0;")
            // m2 -> m1
            .addChunk("")
            // m3 -> m2
            .addChunk("let x = a;")
            // m4 -> m2
            .addChunk("let y = a;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "const a = 0;",
            // m3
            "let x = a;",
            // m4
            "let y = a;"));
  }

  @Test
  public void testVarMovement9() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forTree()
            // m1
            .addChunk("var a = 0; var b = 1; var c = 3;")
            // m2 -> m1
            .addChunk("")
            // m3 -> m1
            .addChunk("")
            // m4 -> m2
            .addChunk("a;")
            // m5 -> m2
            .addChunk("a;c;")
            // m6 -> m3
            .addChunk("b;")
            // m7 -> m4
            .addChunk("b;c;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "var c = 3;",
            // m2
            "var a = 0;",
            // m3
            "var b = 1;",
            // m4
            "a;",
            // m5
            "a;c;",
            // m6
            "b;",
            // m7
            "b;c;"));
  }

  @Test
  public void testConstMovement9() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forTree()
            // m1
            .addChunk("const a = 0; const b = 1; const c = 3;")
            // m2 -> m1
            .addChunk("")
            // m3 -> m1
            .addChunk("")
            // m4 -> m2
            .addChunk("a;")
            // m5 -> m2
            .addChunk("a;c;")
            // m6 -> m3
            .addChunk("b;")
            // m7 -> m4
            .addChunk("b;c;")
            .build();

    test(
        srcs(chunks),
        expected(
            // m1
            "const c = 3;",
            // m2
            "const a = 0;",
            // m3
            "const b = 1;",
            // m4
            "a;",
            // m5
            "a;c;",
            // m6
            "b;",
            // m7
            "b;c;"));
  }

  @Test
  public void testClinit1() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("function Foo$clinit() { Foo$clinit = function() {}; }")
            // m2
            .addChunk("Foo$clinit();")
            .build();
    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "function Foo$clinit() { Foo$clinit = function() {}; } Foo$clinit();"));
  }

  @Test
  public void testClinit2() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1
            .addChunk("var Foo$clinit = function() { Foo$clinit = function() {}; };")
            // m2
            .addChunk("Foo$clinit();")
            .build();
    test(
        srcs(chunks),
        expected(
            // m1
            "",
            // m2
            "var Foo$clinit = function() { Foo$clinit = function() {}; }; Foo$clinit();"));
  }

  @Test
  public void testClone1() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk("function f(){} f.prototype.clone = function() { return new f };")
                // m2
                .addChunk("var a = (new f).clone();")
                .build()),
        expected(
            // m1
            "",
            // m2
            """
            function f(){}
            f.prototype.clone = function() { return new f() };
            var a = (new f).clone();
            """));
  }

  @Test
  public void testClone2() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    function f(){}
                    f.prototype.cloneFun = function() {
                      return function() {new f}
                    };
                    """)
                .addChunk("var a = (new f).cloneFun();")
                .build()),
        expected(
            // m1
            "",
            """
            function f(){}
            f.prototype.cloneFun = function() {
              return function() {new f}
            };
            var a = (new f).cloneFun();
            """));
  }

  @Test
  public void testBug4118005() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    var m = 1;
                    (function () {
                     var x = 1;
                     m = function() { return x };
                    })();
                    """)
                .addChunk("m();")
                .build()));
  }

  @Test
  public void testEmptyModule() {
    // When the dest module is empty, it might try to move the code to the
    // one of the modules that the empty module depends on. In some cases
    // this might ended up to be the same module as the definition of the code.
    // When that happens, CrossChunkCodeMotion might report a code change
    // while nothing is moved. This should not be a problem if we know all
    // modules are non-empty.
    JSChunk m1 = new JSChunk("m1");
    m1.add(SourceFile.fromCode("m1", "function x() {}"));

    JSChunk empty = new JSChunk("empty");
    empty.addDependency(m1);

    JSChunk m2 = new JSChunk("m2");
    m2.add(SourceFile.fromCode("m2", "x()"));
    m2.addDependency(empty);

    JSChunk m3 = new JSChunk("m3");
    m3.add(SourceFile.fromCode("m3", "x()"));
    m3.addDependency(empty);

    test(srcs(new JSChunk[] {m1, empty, m2, m3}), expected("", "function x() {}", "x()", "x()"));
  }

  @Test
  public void testAbstractMethod() {
    // m2 -> m1
    // m3 -> m1
    test(
        srcs(
            JSChunkGraphBuilder.forStar()
                // m1
                .addChunk(
                    """
                    var abstractMethod = function () {};
                    function F(){} F.prototype.bar=abstractMethod;
                    function G(){} G.prototype.bar=abstractMethod;
                    """)
                // m2 -> m1
                .addChunk("var f = new F();")
                // m3 -> m1
                .addChunk("var g = new G();")
                .build()),
        expected(
            "var abstractMethod = function () {};",
            "function F(){} F.prototype.bar=abstractMethod; var f = new F();",
            "function G(){} G.prototype.bar=abstractMethod; var g = new G();"));
  }

  @Test
  public void testMovableUseBeforeDeclaration() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function f() { g(); } function g() {}")
                // m1
                .addChunk("f();")
                .build()),
        expected(
            // m0
            "",
            // m1
            "function g() {} function f() { g(); } f();"));
  }

  @Test
  public void testImmovableUseBeforeDeclaration() {
    // must recognize this as a reference to the following declaration
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    g(); // must recognize this as a reference to the following declaration
                    function g() {}
                    """)
                .addChunk("g();")
                .build()));

    // f() cannot move, so neither can g()
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m1
                .addChunk(
                    """
                    function f() { g(); }
                    function g() {}
                    // f() cannot move, so neither can g()
                    f();
                    """)
                // m2
                .addChunk("g();")
                .build()));
  }

  @Test
  public void testSplitDeclaration() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk(
                    """
                    function a() { b(); }
                    function b() {}
                    function c() {}
                    a.prototype.x = function() { c(); };
                    """)
                // m1
                .addChunk("a();")
                .build()),
        expected(
            // m0
            "",
            // m1
            """
            function b() {}
            function c() {}
            function a() { b(); }
            a.prototype.x = function() { c(); };
            a();
            """));
  }

  @Test
  public void testOutOfOrderAfterSplitDeclaration() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk(
                    """
                    function a() { c(); }
                    function b() {}
                    a.prototype.x = function() { c(); };
                    function c() {}
                    """)
                // m1
                .addChunk("a();")
                .build()),
        expected(
            // m0
            "function b() {}",
            // m1
            """
            function c() {}
            function a() { c(); }
            a.prototype.x = function() { c(); };
            a();
            """));
  }

  @Test
  public void testOutOfOrderWithInterveningReferrer() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function a() { c(); } function b() { a(); } function c() {}")
                // m1
                .addChunk("b();")
                .build()),
        expected(
            // m0
            "",
            // m1
            "function c() {} function a() { c(); } function b() { a(); } b();"));
  }

  @Test
  public void testOutOfOrderWithDifferentReferrers() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function a() { b(); } function b() {}")
                // m1
                .addChunk("b();")
                // m2
                .addChunk("a();")
                .build()),
        expected(
            // m0
            "",
            // m1
            "function b() { } b();",
            "function a() { b(); } a();"));
  }

  @Test
  public void testCircularWithDifferentReferrers() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function a() { b(); } function b() { a(); }")
                // m1
                .addChunk("b();")
                // m2
                .addChunk("a();")
                .build()),
        expected(
            // m0
            "",
            // m1
            "function a() { b(); } function b() { a(); } b();",
            "a();"));
  }

  @Test
  public void testSmallestCoveringDependencyDoesNotDependOnDeclarationModule() {
    //       m0
    //      /  \
    //    m1   m2  // declaration in m1
    //    |    /|  // smallest common dep is m2
    //    m3_ | |  // best place for declaration is m3
    //   / | X  |
    //  /  |/ \ /
    // m4  m5  m6  // references in m5 and m6
    JSChunk[] m =
        JSChunkGraphBuilder.forUnordered()
            .addChunk("") // m0
            .addChunk("function f() {}") // m1
            .addChunk("") // m2
            .addChunk("") // m3
            .addChunk("") // m4
            .addChunk("f();") // m5
            .addChunk("f();") // m6
            .build();

    m[1].addDependency(m[0]);
    m[2].addDependency(m[0]);
    m[3].addDependency(m[1]);
    m[4].addDependency(m[3]);
    m[5].addDependency(m[2]);
    m[5].addDependency(m[3]);
    m[6].addDependency(m[2]);
    m[6].addDependency(m[3]);

    test(
        srcs(m),
        expected(
            // m0
            "",
            // m1
            "",
            // m2
            "",
            // m3
            "function f() {}",
            // m4
            "",
            // m5
            "f();",
            // m6
            "f();"));
  }

  @Test
  public void testEarlyReferencesPinLateDeclarations() {
    // globalC.x == 2 - not safe to move declaration
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("function C() {} C.prototype.x = 1; var globalC = new C();")
                // globalC.x == 2 - not safe to move declaration
                .addChunk("C.prototype.x = 2; globalC.x;")
                .addChunk("new C().x;")
                .build()));
  }

  @Test
  public void testMovedInstanceofIsHandledCorrectly() {
    parentModuleCanSeeSymbolsDeclaredInChildren = true;
    // no need to guard instanceof
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk(
                    """
                    function C() {}
                    function X() {}
                    X.prototype.a = function(x) { return x instanceof C; }
                    """)
                // m1
                .addChunk("new C();")
                // m2
                .addChunk("new X();")
                .build()),
        expected(
            // m0
            "",
            // m1
            "function C() {} new C();",
            // m2
            """
            function X() {}
            // no need to guard instanceof
            X.prototype.a = function(x) { return x instanceof C; }
            new X();
            """));
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk(
                    """
                    function C() {}
                    function X() {}
                    X.prototype.a = function(x) { return x instanceof C; }
                    """)
                // m1
                .addChunk("new X();")
                // m2
                .addChunk("new C();")
                .build()),
        expected(
            // m0
            "",
            // m1
            """
            function X() {}
            X.prototype.a = function(x) { return 'function' == typeof C && x instanceof C; }
            new X();
            """,
            // m2
            "function C() {} new C();"));
  }

  @Test
  public void testValueNotWellDefined() {
    // TODO(bradfordcsmith): This prevents us from guaranteeing that all moves are made in a single
    //     pass. Code movement in the first pass may cause some variables to become "well defined"
    //     that weren't before, unblocking movement of some statements.
    // B is blocked from moving because A is used before it is defined.
    // See ReferenceCollection#isWellDefined and CrossChunkReferenceCollector#canMoveValue
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                // m0
                .addChunk("function f() { return A; } var A = 1; var B = A;")
                // m1
                .addChunk("f(); function f2() { return B; }")
                .build()),
        expected(
            // m0
            "var A = 1; var B = A;",
            // m1
            "function f() { return A; } f(); function f2() { return B; }"));
  }

  @Test
  public void testDestructuringDeclarationsNotMovable() {
    testSame(
        srcs(JSChunkGraphBuilder.forChain().addChunk("const [a] = [];").addChunk("a;").build()));
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("const {a} = { a: 1 };")
                .addChunk("a;")
                .build()));
  }

  @Test
  public void testDestructuringAssignmentsAreReferences() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1; [a] = [5];")
                .addChunk("a;")
                .build()));
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1; ({x: a} = {x: 5});")
                .addChunk("a;")
                .build()));
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1;")
                .addChunk("[a] = [5];")
                .addChunk("a;")
                .build()),
        expected("", "let a = 1; [a] = [5];", "a;"));
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1;")
                .addChunk("({x: a} = {x: 5});")
                .addChunk("a;")
                .build()),
        expected("", "let a = 1; ({x: a} = {x: 5});", "a;"));
  }

  @Test
  public void testDefaultParamValuesAreReferences() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1; function f(x = a) {} f();")
                .addChunk("a;")
                .build()));
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1; function f(x = a) {}")
                .addChunk("f();")
                .build()),
        expected("", "let a = 1; function f(x = a) {} f();"));
  }

  @Test
  public void testSpreadCountsAsAReference() {
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = [];")
                .addChunk("function f(...args) {} f(...a);")
                .addChunk("a;")
                .build()),
        expected("", "let a = []; function f(...args) {} f(...a);", "a;"));
  }

  @Test
  public void testObjectLiteralMethods() {
    // Object literal methods, getters, and setters are movable and references within them are
    // handled correctly.
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("const a = 1;")
                .addChunk("const o = { foo() {return a;}, get x() {}, set x(v) {a = v;} };")
                .addChunk("o;")
                .addChunk("a;")
                .build()),
        expected(
            "",
            "",
            "const a = 1; const o = { foo() {return a;}, get x() {}, set x(v) {a = v;} }; o;",
            "a;"));
  }

  @Test
  public void testComputedProperties() {
    // Computed properties are movable if the key expression is a literal.
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = { ['something']: 1};")
                .addChunk("a;")
                .build()),
        expected("", "let a = { ['something']: 1}; a;"));

    // Computed properties are movable if the key is a well defined variable
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("const x = 1; let a = { [x]: 1};")
                .addChunk("a;")
                .build()),
        expected("", "const x = 1; let a = { [x]: 1}; a;"));
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("const x = 1; let a = class { [x]() {} };")
                .addChunk("a;")
                .build()),
        expected("", "const x = 1; let a = class { [x]() {} }; a;"));

    // Computed properties are not movable if the key is an unknown variable
    testSame(
        srcs(JSChunkGraphBuilder.forChain().addChunk("let a = { [x]: 1};").addChunk("a;").build()));
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = class { [x]() {} };")
                .addChunk("a;")
                .build()));

    // Computed properties are not movable if the key is a
    testSame(
        srcs(JSChunkGraphBuilder.forChain().addChunk("let a = { [x]: 1};").addChunk("a;").build()));

    // references in computed properties are honored
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("let a = 1;")
                .addChunk("let b = { [a + 1]: 2 };")
                .addChunk("a;")
                .build()),
        expected("", "let a = 1; let b = { [a + 1]: 2 };", "a;"));
  }

  @Test
  public void testClassComputedFieldUnmovable() {
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("class C { ['x'] = 1; }")
                .addChunk("a;")
                .build()));
    testSame(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk("class C { static ['x'] = 1; }")
                .addChunk("a;")
                .build()));
  }

  @Test
  public void testPureOrBreakMyCode() {
    // should move a function if its top level invocation was annotated with @pureOrBreakMyCode
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    function wrapIt(val) { return [val]; }
                    const value = /** @pureOrBreakMyCode */ wrapIt(123);
                    """)
                .addChunk("const unwrappedValue = value[0];")
                .build()),
        expected(
            "",
            """
            function wrapIt(val) { return [val]; }
            const value = /** @pureOrBreakMyCode */ wrapIt(123);
            const unwrappedValue = value[0];
            """));

    // should move a class if its static initializer containing a function call was annotated with
    // @pureOrBreakMyCode
    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
                    """
                    function wrapIt(val) { return [val]; }
                    class ImmovableClass {}
                    ImmovableClass.prop = wrapIt(123);
                    class MovableClass {}
                    MovableClass.prop = /** @pureOrBreakMyCode */ wrapIt(123);
                    """)
                .addChunk("new ImmovableClass(); new MovableClass();")
                .build()),
        expected(
            """
            function wrapIt(val) { return [val] }
            class ImmovableClass {}
            ImmovableClass.prop = wrapIt(123);
            """,
            """
            class MovableClass {}
            MovableClass.prop = /** @pureOrBreakMyCode */ wrapIt(123);
            new ImmovableClass(); new MovableClass();
            """));

    test(
        srcs(
            JSChunkGraphBuilder.forChain()
                .addChunk(
"""
class LowerCasePipe {}
/** @nocollapse */ LowerCasePipe.\u0275pipe = /** @pureOrBreakMyCode*/ i0.\u0275\u0275definePipe({ name: "lowercase", type: LowerCasePipe, pure: true });
""")
                .addChunk("new LowerCasePipe();")
                .build()),
        expected(
            "",
"""
class LowerCasePipe {}
/** @nocollapse */ LowerCasePipe.\u0275pipe = /** @pureOrBreakMyCode*/ i0.\u0275\u0275definePipe({ name: "lowercase", type: LowerCasePipe, pure: true });
new LowerCasePipe();
"""));
  }
}
