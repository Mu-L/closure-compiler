/*
 * Copyright 2007 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InlineSimpleMethodsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InlineSimpleMethods(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
  }

  /**
   * Helper for tests that expects definitions to remain unchanged, such that {@code definitions+js}
   * is converted to {@code definitions+expected}.
   */
  private void testWithPrefix(String definitions, String js, String expected) {
    test(definitions + js, definitions + expected);
  }

  @Test
  public void testDoesNotInlineMethodOnBaseClass() {
    String baseClassJs =
        """
        class Base {
          constructor() {
            /** @const */
            this.prop_ =
                Math.random() > .5;
          }
          method() {
            return this.prop_;
          }
        }
        """;

    String derivedClassJS =
        """
        class Derived extends Base {
          constructor() {
            super();
          }
          derivedMethod() {
            super.method();
          }
        }

        (new Derived()).derivedMethod();
        """;

    String source = baseClassJs + derivedClassJS;
    testSame(source);
  }

  @Test
  public void testSimpleInline1() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        """,
        "var x=(new Foo).bar();var y=(new Foo).bar();",
        "var x=(new Foo).baz;var y=(new Foo).baz");
  }

  @Test
  public void testSimpleInline2() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype={bar:function(){return this.baz}};
        """,
        "var x=(new Foo).bar();var y=(new Foo).bar();",
        "var x=(new Foo).baz;var y=(new Foo).baz");
  }

  @Test
  public void testSimpleGetterInline1() {
    // TODO(johnlenz): Support this case.
    testSame(
        """
        function Foo(){}
        Foo.prototype={get bar(){return this.baz}};
        var x=(new Foo).bar;var y=(new Foo).bar
        """);
    // Verify we are not confusing calling the result of an ES5 getter
    // with call the getter.
    testSame(
        """
        function Foo(){}
        Foo.prototype={get bar(){return this.baz}};
        var x=(new Foo).bar();var y=(new Foo).bar()
        """);
  }

  @Test
  public void testSimpleSetterInline1() {
    // Verify 'get' and 'set' are not confused.
    testSame(
        """
        function Foo(){}
        Foo.prototype={set bar(a){return this.baz}};
        var x=(new Foo).bar;var y=(new Foo).bar
        """);
    testSame(
        """
        function Foo(){}
        Foo.prototype={set bar(a){return this.baz}};
        var x=(new Foo).bar();var y=(new Foo).bar()
        """);
  }

  @Test
  public void testSelfInline() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        """,
        "Foo.prototype.meth=function(){this.bar();}",
        "Foo.prototype.meth=function(){this.baz}");
  }

  @Test
  public void testCallWithArgs() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        """,
        "var x=(new Foo).bar(3,new Foo)",
        "var x=(new Foo).bar(3,new Foo)");
  }

  @Test
  public void testCallWithConstArgs() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(a){return this.baz};
        """,
        "var x=(new Foo).bar(3, 4)",
        "var x=(new Foo).baz");
  }

  @Test
  public void testNestedProperties() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz.ooka};
        """,
        "(new Foo).bar()",
        "(new Foo).baz.ooka");
  }

  @Test
  public void testSkipComplexMethods() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        Foo.prototype.condy=function(){return this.baz?this.baz:1};
        """,
        "var x=(new Foo).argy()",
        "var x=(new Foo).argy()");
  }

  @Test
  public void testSkipConflictingMethods() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        Foo.prototype.bar=function(){return this.bazz};
        """,
        "var x=(new Foo).bar()",
        "var x=(new Foo).bar()");
  }

  @Test
  public void testSameNamesDifferentDefinitions() {
    testWithPrefix(
        """
        function A(){}
        A.prototype.g=function(){return this.a};
        function B(){}
        B.prototype.g=function(){return this.b};
        """,
        """
        var x=(new A).g();
        var y=(new B).g();
        var a=new A;
        var ag=a.g();
        """,
        """
        var x=(new A).g();
        var y=(new B).g();
        var a=new A;
        var ag=a.g()
        """);
  }

  @Test
  public void testSameNamesSameDefinitions() {
    testWithPrefix(
        """
        function A(){}
        A.prototype.g=function(){return this.a};
        function B(){}
        B.prototype.g=function(){return this.a};
        """,
        """
        var x=(new A).g();
        var y=(new B).g();
        var a=new A;
        var ag=a.g();
        """,
        """
        var x=(new A).a;
        var y=(new B).a;
        var a=new A;
        var ag=a.a
        """);
  }

  @Test
  public void testConfusingNames() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return this.baz};
        """,
        "function bar(){var bar=function(){};bar()}",
        "function bar(){var bar=function(){};bar()}");
  }

  @Test
  public void testConstantInline() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return 3};
        """,
        "var f=new Foo;var x=f.bar()",
        "var f=new Foo;var x=3");
  }

  @Test
  public void testConstantArrayInline() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return[3,4]};
        """,
        "var f=new Foo;var x=f.bar()",
        "var f=new Foo;var x=[3,4]");
  }

  @Test
  public void testConstantInlineWithSideEffects() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){return 3};
        """,
        "var x=(new Foo).bar()",
        "var x=(new Foo).bar()");
  }

  @Test
  public void testEmptyMethodInline() {
    testWithPrefix(
        "function Foo(){} Foo.prototype.bar=function(a){};",
        "var x = new Foo(); x.bar();",
        "var x = new Foo();;");
  }

  @Test
  public void testEmptyMethodInlineWithSideEffects() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){};
        """,
        "(new Foo).bar();var y=new Foo;y.bar(new Foo)",
        "(new Foo).bar();var y=new Foo;y.bar(new Foo)");
  }

  @Test
  public void testEmptyMethodInlineInAssign1() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){};
        """,
        "var x=new Foo;var y=x.bar()",
        "var x=new Foo;var y=void 0");
  }

  @Test
  public void testEmptyMethodInlineInAssign2() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){};
        """,
        "var x=new Foo;var y=x.bar().toString()",
        "var x=new Foo;var y=(void 0).toString()");
  }

  @Test
  public void testNormalMethod() {
    testWithPrefix(
        """
        function Foo(){}
        Foo.prototype.bar=function(){var x=1};
        """,
        "var x=new Foo;x.bar()",
        "var x=new Foo;x.bar()");
  }

  @Test
  public void testNoInlineOfExternMethods1() {
    testSame(externs("var external={};external.charAt;"), srcs("external.charAt()"));
  }

  @Test
  public void testNoInlineOfExternMethods2() {
    testSame(externs("var external={};external.charAt=function(){};"), srcs("external.charAt()"));
  }

  @Test
  public void testNoInlineOfExternMethods3() {
    testSame(
        externs("var external={};external.bar=function(){};"),
        srcs("function Foo(){}Foo.prototype.bar=function(){};(new Foo).bar()"));
  }

  @Test
  public void testNoInlineOfDangerousProperty() {
    testSame(
        """
        function Foo(){this.bar=3}
        Foo.prototype.bar=function(){};
        var x=new Foo;var y=x.bar()
        """);
  }

  // Don't warn about argument naming conventions (this is done in another pass)
  //   opt_ parameters must not be followed by non-optional parameters.
  //   var_args must be last
  @Test
  public void testNoWarn() {
    testSame(
        """
        function Foo(){}
        Foo.prototype.bar=function(opt_a,b){var x=1};
        var x=new Foo;x.bar()
        """);

    testSame(
        """
        function Foo(){}
        Foo.prototype.bar=function(var_args,b){var x=1};
        var x=new Foo;x.bar()
        """);
  }

  @Test
  public void testObjectLit() {
    testSame(
        """
        Foo.prototype.bar=function(){return this.baz_};
        var blah={bar:function(){}};
        (new Foo).bar()
        """);
  }

  @Test
  public void testObjectLit2() {
    testSame(
        """
        var blah={bar:function(){}};
        (new Foo).bar()
        """);
  }

  @Test
  public void testObjectLit3() {
    testSame(
        """
        var blah={bar(){}};
        (new Foo).bar()
        """);
  }

  @Test
  public void testObjectLit4() {
    testSame(
        """
        var key='bar';
        var blah={[key]:a};
        (new Foo).bar
        """);
  }

  @Test
  public void testObjectLitExtern1() {
    String externs = "window.bridge={_sip:function(){}};";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  @Test
  public void testObjectLitExtern2() {
    String externs = "window.bridge={_sip(){}};";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  @Test
  public void testClassExtern() {
    String externs = "window.bridge= class { _sip() {} };";
    testSame(externs(externs), srcs("window.bridge._sip()"));
  }

  @Test
  public void testExternFunction() {
    String externs = "function emptyFunction() {}";
    testSame(
        externs(externs),
        srcs(
            """
            function Foo(){this.empty=emptyFunction}
            (new Foo).empty()
            """));
  }

  @Test
  public void testIssue2508576_1() {
    // Method defined by an extern should be left alone.
    String externs = "function alert(a) {}";
    testSame(externs(externs), srcs("({a:alert,b:alert}).a('a')"));
  }

  @Test
  public void testIssue2508576_2() {
    // Anonymous object definition with a side-effect should be left alone.
    testSame("({a:function(){},b:x()}).a('a')");
  }

  @Test
  public void testIssue2508576_3() {
    // Anonymous object definition without side-effect should be removed.
    test("({a:function(){},b:alert}).a('a')", ";");
  }

  // When there are two methods with the same name, one on an ES3-style class, and one on an
  // ES6-style class, make sure the right one gets inlined into the right place, if inlining happens
  // at all.
  @Test
  public void testEs6Issue1() {
    testSame(
        """
        /** @constructor */
        function OldClass() {}

        OldClass.prototype.foo = function() { return this.oldbar; };

        class NewClass {
          foo() { return this.newbar; }
        }

        var x = new OldClass;
        x.foo();
        x = new NewClass;
        x.foo();
        """);
  }

  @Test
  public void testEs6Issue2() {
    testSame(
        """
        /** @constructor */
        function OldClass() {}

        OldClass.prototype.foo = function() { return this.oldbar; };

        class NewClass {
          foo() { return this.newbar; }
        }

        var x = new OldClass;
        x.foo();
        var y = new NewClass;
        y.foo();
        """);
  }

  @Test
  public void testArrowFunction() {
    // Arrow functions cannot be trivially inlined.
    // This is only an issue if ES6+ is being targeted,
    // because otherwise the arrow function is removed before this pass.
    testSame(
        """
        class Holder {
            constructor() {
                this.val = {internal: true};
                this.context =  {getVal: () => this.val}
            }
        }

        console.log(new Holder().context.getVal().internal);
        """);
  }

  @Test
  public void testAnonymousGet() {
    // Anonymous object definition without side-effect should be removed.
    testSame("({get a(){return function(){}},b:alert}).a('a')");
    testSame("({get a(){},b:alert}).a('a')");
    testSame("({get a(){},b:alert}).a");
  }

  @Test
  public void testAnonymousSet() {
    // Anonymous object definition without side-effect should be removed.
    testSame("({set a(b){return function(){}},b:alert}).a('a')");
    testSame("({set a(b){},b:alert}).a('a')");
    testSame("({set a(b){},b:alert}).a");
  }

  @Test
  public void testInlinesEvenIfClassEscapes() {
    // The purpose of this test is to record an unsafe assumption made by the
    // pass. In practice, it's usually safe to inline even if the class
    // escapes, because method definitions aren't commonly mutated.
    test(
        externs("var esc;"),
        srcs(
            """
            /** @constructor */
            function Foo() {
              this.prop = 123;
            }
            Foo.prototype.m = function() {
              return this.prop;
            }
            (new Foo).m();
            esc(Foo);
            """),
        expected(
            """
            /** @constructor */
            function Foo(){this.prop=123}
            Foo.prototype.m=function(){return this.prop}
            (new Foo).m();
            esc(Foo)
            """));
  }

  @Test
  public void testClassFieldDoesntCrash() {
    testSame("class C { a; };");
    testSame("class C { a = 2; };");
    testSame("class C { ['a'] = 2; }");
    testSame("class C { a = function() { return 4; }; }");
    testSame("class C { static a; };");
    testSame("class C { static a = 2; };");
    testSame("class C { static ['a'] = 2; }");
    testSame("class C { static a = function() { return 4; }; }");
  }

  @Test
  public void testStaticInitializationBlockDoesntCrash() {
    testSame(
        """
        class C {
          static {
            alert('foo');
          }
        }
        """);
  }

  @Test
  public void testNonStaticClassFieldArrowFunction() {
    testSame(
        """
        class Foo {
          a = 5;
          b = () => this.a;
        }
        new Foo().b()
        """);
  }

  @Test
  public void testStaticClassFieldFunctionDoesInline() {
    test(
        """
        class Foo {
          static a = 5;
          static b = function() { return this.a; };
        }
        Foo.b();
        """,
        """
        class Foo {
          static a = 5;
          static b = function() { return this.a; };
        }
        Foo.a;
        """);
  }

  @Test
  public void testNoInline() {
    testWithPrefix(
        """
        class Foo {
         /** @noinline */ bar() { return 'hi'; }
        }
        """,
        "var x=new Foo;x.bar()",
        "var x=new Foo;x.bar()");
  }

  @Test
  public void testReflectObjectProperty() {
    testWithPrefix(
        """
        class Foo {
         bar() { return 'hi'; }
        }
        const c = goog.reflect.objectProperty('bar', Foo.prototype);
        """,
        "var x=new Foo;x.bar()",
        "var x=new Foo;x.bar()");
  }

  @Test
  public void testNoInlineOfAsyncMethod_es5Style() {
    // Don't inline: the bar() call returns a Promise wrapping this.baz.
    testSame(
        """
        function Foo(){}
        Foo.prototype.bar = async function(){return this.baz};
        var x = (new Foo).bar();
        var y = (new Foo).bar();
        """);
  }

  @Test
  public void testNoInlineOfAsyncMethod_es6Style() {
    // Don't inline: the bar() call returns a Promise wrapping this.baz.
    testSame(
        """
        class Foo { async bar(){return this.baz} }
        var x = (new Foo).bar();
        var y = (new Foo).bar();
        """);
  }

  @Test
  public void testNoInlineOfGeneratorMethod_es5Style() {
    // Don't inline: the bar() call returns a generator, not this.baz
    testSame(
        """
        function Foo(){}
        Foo.prototype.bar = function*(){return this.baz};
        var x = (new Foo).bar();
        var y = (new Foo).bar();
        """);
  }

  @Test
  public void testNoInlineOfGeneratorMethod_es6Style() {
    // Don't inline: the bar() call returns a generator, not this.baz
    testSame(
        """
        class Foo { *bar(){return this.baz} }
        var x = (new Foo).bar();
        var y = (new Foo).bar();
        """);
  }

  @Test
  public void testNoInlineOfAsynceneratorMethod_es5Style() {
    // Don't inline: the bar() call returns an async generator, not this.baz
    testSame(
        """
        function Foo(){}
        Foo.prototype.bar = async function*(){return this.baz};
        var x = (new Foo).bar();
        var y =(new Foo).bar();
        """);
  }

  @Test
  public void testNoInlineOfAsyncGeneratorMethod_es6Style() {
    // Don't inline: the bar() call returns an async generator, not this.baz
    testSame(
        """
        class Foo { async *bar(){return this.baz} }
        var x = (new Foo).bar();
        var y = (new Foo).bar();
        """);
  }
}
