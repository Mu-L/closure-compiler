/*
 * Copyright 2017 The Closure Compiler Authors.
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

/**
 * Tests for {@link RemoveUnusedCode} that cover removal of instance properties and properties
 * defined directly on constructors.
 *
 * <p>Note that removal of variables is actually disabled for these test cases to make it easier to
 * construct cases where only parts of a class will be removed.
 */
@RunWith(JUnit4.class)
public final class RemoveUnusedCodeClassPropertiesTest extends CompilerTestCase {

  private boolean keepLocals = true;
  private boolean keepGlobals = true;
  private static final String EXTERNS =
      """
      /**
       * @constructor
       * @param {*=} opt_value
       * @return {!Object}
       */
      function Object(opt_value) {}
      /**
       * @constructor
       * @param {...*} var_args
       */
      function Function(var_args) {}
      /**
       * @constructor
       * @param {*=} arg
       * @return {string}
       */
      function String(arg) {}
      /**
       * @record
       * @template VALUE
       */
      /**
       * @template T
       * @constructor
       * @param {...*} var_args
       * @return {!Array<?>}
       */
      function Array(var_args) {}
      var window;
      function alert(a) {}
      function use(x) {}
      var EXT = {};
      EXT.ext;
      var externVar;
      function externFunction() {}
      /** @type {Function} */
      Object.defineProperties = function() {};
      /** @type {Function} */
      Object.prototype.constructor = function() {};
      // NOTE: The following are needed to prevent NTI inexistent property warnings.
      var $jscomp = {};
      $jscomp.global = {}
      /** @type {?} */
      $jscomp.global.Object
      function JSCompiler_renameProperty(p) {}
      var goog = {};
      goog.reflect = {};
      goog.reflect.object = function(a, b) {};
      """;

  public RemoveUnusedCodeClassPropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveUnusedCode.Builder(compiler)
        .removeLocalVars(!keepLocals)
        .removeGlobals(!keepGlobals)
        .removeUnusedPrototypeProperties(true)
        .removeUnusedThisProperties(true)
        .removeUnusedObjectDefinePropertiesDefinitions(true)
        .build();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // Allow testing of features that aren't fully supported for output yet.
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    disableCompareJsDoc();
    enableGatherExternProperties();
    disableTypeCheck();
  }

  @Test
  public void testSimple1() {
    // A property defined on "this" can be removed
    test("this.a = 2", "");
    test("let x = (this.a = 2)", "let x = 2");
    testSame("this.a = 2; let x = this.a;");
  }

  @Test
  public void testSimple2() {
    // A property defined on "this" can be removed, even when defined
    // as part of an expression
    test("this.a = 2, alert(1);", "alert(1);");
    test("const x = (this.a = 2, alert(1));", "const x = alert(1);");
    test("const x = (alert(1), this.a = 2);", "const x = (alert(1), 2);");
  }

  @Test
  public void testSimple3() {
    // A property defined on an object other than "this" can not be removed.
    testSame("var y = {}; y.a = 2");
    // and prevents the removal of the definition on 'this'.
    testSame("var y = {}; y.a = 2; this.a = 2");
    // Some use of the property "a" prevents the removal.
    testSame("var x; var y = {}; y.a = 2; this.a = 1; alert(x.a)");
  }

  @Test
  public void testObjLit() {
    // A property defined on an object other than "this" can not be removed.
    testSame("({a:2})");
    // and prevent the removal of the definition on 'this'.
    testSame("({a:0}); this.a = 1;");
    // ... even if it's quoted
    testSame("({'a':0}); this.a = 1;");
    // Some use of the property "a" prevents the removal.
    testSame("var x = ({a:0}); this.a = 1; alert(x.a)");
  }

  @Test
  public void testExtern() {
    // A property defined in the externs is can not be removed.
    testSame("this.ext = 2");
  }

  @Test
  public void testExport() {
    // An exported property can not be removed.
    testSame("this.ext = 2; window['export'] = this.ext;");
    testSame("function f() { this.ext = 2; } window['export'] = this.ext;");
  }

  @Test
  public void testAssignOp1() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.x += 2", "");
    testSame("const x = (this.x += 2)");
    testSame("this.x += 2; const x = this.x;");
    // But, of course, a later use prevents its removal.
    testSame("this.x += 2; let x = {}; x.x;");
  }

  @Test
  public void testAssignOp2() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    test("this.a += 2, alert(1)", "alert(1)");
    test("const x = (this.a += 2, alert(1))", "const x = alert(1)");
    testSame("const x = (alert(1), this.a += 2)");
  }

  @Test
  public void testInc1() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.x++", "");
    testSame("let x = (this.x++)");
    testSame("this.x++; let x = this.x;");

    test("--this.x", "");
    testSame("let x = (--this.x)");
    testSame("--this.x; let x = this.x;");
  }

  @Test
  public void testInc2() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    test("this.a++, alert()", "alert()");
    test("let x = (this.a++, alert())", "let x = alert()");
    testSame("let x = (alert(), this.a++)");

    test("--this.a, alert()", "alert()");
    test("let x = (--this.a, alert())", "let x = alert()");
    testSame("let x = (alert(), --this.a)");
  }

  @Test
  public void testDestructuringRest() {
    testSame(
        """
        function Foo() {}
        Foo.a = function() {};
        ({ ...Foo.a.b } = 0);
        """);
  }

  @Test
  public void testExprResult() {
    test("this.x", "");
    test("externFunction().prototype.x", "externFunction()");
    // It doesn't make much sense to use optional chaining in these cases, but if you do,
    // it shouldn't prevent unused property removal
    test("this?.x", "");
    test("externFunction()?.prototype.x", "externFunction()");
  }

  @Test
  public void testJSCompiler_renameProperty() {
    // JSCompiler_renameProperty introduces a use of the property
    testSame("var x; this.a = 2; x[JSCompiler_renameProperty('a')]");
    testSame("this.a = 2; JSCompiler_renameProperty('a')");
  }

  @Test
  public void testForIn() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object is used in a FOR-IN loop
    test(
        "let x = {}; this.y = 1;for (var a in x) { alert(x[a]) }",
        "let x = {};            for (var a in x) { alert(x[a]) }");
  }

  @Test
  public void testObjectKeys() {
    // This is the basic assumption that this pass makes:
    // it can remove properties even when the object are referenced
    test(
        "this.y = 1;alert(Object.keys(this))", // preserve format
        "           alert(Object.keys(this))");
  }

  @Test
  public void testObjectReflection1() {
    // Verify reflection prevents removal.
    testSame(
        """
        /** @constructor */ // preserve newlines
        function A() { this.foo = 1; }
        use(goog.reflect.object(A, {foo: 'foo'}));
        """);
  }

  @Test
  public void testObjectReflection2() {
    // Any object literal definition prevents removal.
    // Type based removal would allow this to be removed.
    testSame(
        """
        /** @constructor */ // preserve newlines
        function A() {this.foo = 1;}
        use({foo: 'foo'});
        """);
  }

  @Test
  public void testIssue730() {
    // Partial removal of properties can causes problems if the object is
    // sealed.
    testSame(
        """
        function A() {this.foo = 0;}
        function B() {this.a = new A();}
        B.prototype.dostuff = function() { this.a.foo++; alert('hi'); }
        new B().dostuff();
        """);
  }

  @Test
  public void testPrototypeProps1() {
    test(
        """
        function A() {this.foo = 1;}
        A.prototype.foo = 0;
        A.prototype.method = function() {this.foo++};
        new A().method()
        """,
        """
        function A() {             }

        A.prototype.method = function() {          };
        new A().method()
        """);
  }

  @Test
  public void testPrototypeProps2() {
    // don't remove properties that are exported by convention
    testSame(
        """
        function A() {this._foo = 1;}
        A.prototype._foo = 0;
        A.prototype.method = function() {this._foo++};
        new A().method()
        """);
  }

  @Test
  public void testConstructorProperty1() {
    enableTypeCheck();

    test(
        "/** @constructor */ function C() {} C.prop = 1;",
        "/** @constructor */ function C() {}            ");
  }

  @Test
  public void testConstructorProperty2() {
    enableTypeCheck();

    testSame(
        """
        /** @constructor */ function C() {}
        C.prop = 1;
        function foo(a) { alert(a.prop) };
        foo(C)
        """);
  }

  @Test
  public void testES6StaticProperty() {
    test(
        "class C { static prop() {} }", // preserve newline
        "class C {                  }");
  }

  @Test
  public void testES6StaticProperty2() {
    test("class C {} C.prop = 1;", "class C {}");
  }

  @Test
  public void testObjectDefineProperties1() {
    enableTypeCheck();

    testSame(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{value:1}});
        function foo(a) { alert(a.prop) };
        foo(C)
        """);
  }

  @Test
  public void testObjectDefineProperties2() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{value:1}});
        """,
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {});
        """);
  }

  @Test
  public void testObjectDefineProperties3() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C,
          {prop:{
            get:function(){},
            set:function(a){},
        }});
        """,
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {});
        """);
  }

  // side-effect in definition retains property definition, but doesn't count as a reference
  @Test
  public void testObjectDefineProperties4() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() { this.prop = 3; }
        Object.defineProperties(C, {prop:alert('')});
        """,
        """
        /** @constructor */ function C() {                }
        Object.defineProperties(C, {prop:alert('')});
        """);
  }

  // quoted properties retains property
  @Test
  public void testObjectDefineProperties5() {
    enableTypeCheck();

    testSame(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {'prop': {value: 1}});
        """);
  }

  @Test
  public void testObjectDefineProperties6() {
    enableTypeCheck();

    // an unknown destination object doesn't prevent removal.
    test(
        "Object.defineProperties(externVar(), {prop:{value:1}});",
        "Object.defineProperties(externVar(), {              });");
  }

  @Test
  public void testObjectDefineProperties7() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{get:function () {return new C}}});
        """,
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {});
        """);
  }

  @Test
  public void testObjectDefineProperties8() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{set:function (a) {return alert(a)}}});
        """,
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {});
        """);
  }

  @Test
  public void testObjectDefinePropertiesQuotesPreventRemoval() {
    enableTypeCheck();

    testSame(
        """
        /** @constructor */ function C() { this.prop = 1; }
        Object.defineProperties(C, {'prop':{set:function (a) {return alert(a.prop)}}});
        """);
  }

  @Test
  public void testObjectDefineProperties_usedSetter_notRemoved() {
    enableTypeCheck();

    testSame(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{set:function (a) {alert(2)}}});
        C.prop = 2;
        """);
  }

  @Test
  public void testPrototypeMethodDef_notConsideredSetterUse() {
    enableTypeCheck();

    test(
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {prop:{set:function (a) {alert(2)}}});
        /** @constructor */ function D () {}
        D.prototype.prop = function() {};
        """,
        """
        /** @constructor */ function C() {}
        Object.defineProperties(C, {});
        /** @constructor */ function D () {}
        """);
  }

  @Test
  public void testEs6GettersWithoutTranspilation() {
    test(
        "class C { get value() { return 0; } }", // preserve newline
        "class C {                           }");
    testSame("class C { get value() { return 0; } } const x = (new C()).value");
  }

  @Test
  public void testES6ClassComputedProperty() {
    testSame("class C { ['test' + 3]() { return 0; } }");
  }

  @Test
  public void testEs6SettersWithoutTranspilation() {
    test(
        "class C { set value(val) { this.internalVal = val; } }", // preserve newline
        "class C {                                            }");

    test(
        "class C { set value(val) { this.internalVal = val; } } (new C()).value = 3;",
        "class C { set value(val) {                         } } (new C()).value = 3;");
    testSame(
        """
        class C {
          set value(val) {
            this.internalVal = val;
          }
          get value() {
            return this.internalVal;
          }
        }
        const y = new C();
        y.value = 3;
        const x = y.value;
        """);
  }

  // All object literal fields are not removed, but the following
  // tests assert that the pass does not fail.
  @Test
  public void testEs6EnhancedObjLiteralsComputedValuesNotRemoved() {
    testSame(
        """
        function getCar(make, model, value) {
          return {
            ['make' + make] : true
          };
        }
        """);
  }

  @Test
  public void testEs6EnhancedObjLiteralsMethodShortHandNotRemoved() {
    testSame(
        """
        function getCar(make, model, value) {
          return {
            getModel() {
              return model;
            }
          };
        }
        """);
  }

  @Test
  public void testEs6EnhancedObjLiteralsPropertyShorthand() {
    testSame("function getCar(make, model, value) { return {model}; }");
  }

  @Test
  public void testTranspiledEs6GettersRemoval() {
    enableTypeCheck();
    test(
        // This is the output of ES6->ES5 class getter converter.
        // See Es6TranspilationIntegrationTest.testEs5GettersAndSettersClasses test method.
        """
        /** @constructor @struct */
        var C = function() {};
        /** @type {?} */
        C.prototype.value = 0;
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            /** @this {C} */
            get: function() {
              return 0;
            }
          }
        });
        """,
        """
        /** @constructor @struct */var C=function(){};
        $jscomp.global.Object.defineProperties(C.prototype, {});
        """);
  }

  @Test
  public void testTranspiledEs6SettersRemoval() {
    enableTypeCheck();
    test(
        // This is the output of ES6->ES5 class setter converter.
        // See Es6TranspilationIntegrationTest.testEs5GettersAndSettersClasses test method.
        """
        /** @constructor @struct */
        var C = function() {};
        /** @type {?} */
        C.prototype.value;
        /** @type {?} */
        C.prototype.internalVal;
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            /** @this {C} */
            set: function(val) {
              this.internalVal = val;
            }
          }
        });
        """,
        """
        /** @constructor @struct */var C=function(){};
        $jscomp.global.Object.defineProperties(C.prototype, {});
        """);
  }

  @Test
  public void testEs6ArrowFunction() {
    test(
        "const arrow = () => this.a = 1;", // preserve newline
        "const arrow = () =>          1;");
    testSame("const arrow = () => ({a: 2})");
    testSame("var y = {}; const arrow = () => {y.a = 2; this.a = 2;}");
    test(
        """
        function A() {
          this.foo = 1;
        }
        A.prototype.foo = 0;
        A.prototype.getIncr = function() {
          return () => { this.foo++; };
        };
        new A().getIncr()
        """,
        """
        function A() {

        }

        A.prototype.getIncr = function() {
          return () => {             };
        };
        new A().getIncr()
        """);
  }

  @Test
  public void testEs6Generator() {
    test(
        "function* gen() { yield this.a = 1; }", // preserve newline
        "function* gen() { yield          1; }");
    testSame("function* gen() { yield this.a = 1; yield this.a; }");
  }

  @Test
  public void testEs6Destructuring() {
    // Test normal destructuring removal
    test(
        "[this.x, this.y] = [1, 2]", // preserve newline
        "[              ] = [1, 2]");

    // Test normal destructuring, assignment prevent removal
    test(
        """
        [this.x, this.y] = [1, 2] // preserve newline
        var p = this.x;
        """,
        """
        [this.x        ] = [1, 2] // preserve newline
        var p = this.x;
        """);

    // Test rest destructuring, `this` property
    test(
        "[this.x, ...this.z] = [1, 2, 3]", // preserve newline
        "[      , ...this.z] = [1, 2, 3]");

    // Test rest destructuring with normal variable
    test(
        "let z; [this.x, ...z] = [1, 2]", // preserve newline
        "let z; [      , ...z] = [1, 2]");

    // Test rest destructuring, assignment prevent removal
    test(
        """
        [this.x, ...this.y] = [1, 2]; // preserve newline
        var p = this.y;
        """,
        """
        [      , ...this.y] = [1, 2]; // preserve newline
        var p = this.y;
        """);

    // Test destructuring rhs prevent removal
    testSame(
        """
        let a;
        this.x = 1; // preserve newline
        this.y = 2;
        [...a] = [this.x, this.y];
        """);

    // Test nested destructuring
    test(
        "let z; [this.x, [this.y, ...z]] = [1, [2]]", // preserve newline
        "let z; [      , [      , ...z]] = [1, [2]]");

    // Test normal object destructuring full removal
    test("({a: this.x, b: this.y} = {a: 1, b: 2})", "({} = {a: 1, b: 2})");

    // Test normal object destructuring partial removal
    test("let y; ({a: this.x, b: y} = {a: 1, b: 2})", "let y; ({           b: y} = {a: 1, b: 2})");

    // Test obj destructuring prevent removal
    test(
        """
        ({a: this.x, b: this.y} = {a: 1, b: 2});
        var p = this.x;
        """,
        """
        ({a: this.x} = {a: 1, b: 2});
        var p = this.x;
        """);

    // Test obj destructuring with old style class
    testSame(
        """
        /** @constructor */ function C () {
          this.a = 1;
        }
        let x;
        ({a: x} = new C());
        """);

    // Test obj destructuring with new style class
    testSame(
        """
        class C {
          constructor() {
             this.a = 1;
          }
        }
        let x;
        ({a: x} = new C());
        """);

    // Test let destructuring
    testSame(
        """
        class C {
          constructor() {
             this.a = 1;
          }
        }
        let {a: x} = new C();
        """);

    // Test obj created at a different location and later used in destructuring
    testSame(
        """
        class C {
          constructor() {
             this.a = 1;
          }
        }
        var obj = new C()
        let x;
        ({a: x} = obj);
        """);

    // Test obj destructuring with default value
    testSame(
        """
        class C {
          constructor() {
             this.a = 1;
          }
        }
        let a;
        ({a = 2} = new C());
        """);

    // Test obj nested destructuring
    testSame(
        """
        class C {
          constructor() {
             this.a = 1;
          }
        }
        var obj = new C()
        let a;
        ({x: {a}} = {x: obj});
        """);

    // Computed Property string expression doesn't prevent removal.
    test(
        "({['a']:0}); this.a = 1;", // preserve newline
        "({['a']:0});            ");
  }

  @Test
  public void testDestrucuturing_assginmentToProperty_consideredUse() {
    testSame(
        """
        class Foo {
          constructor() {
            this.x = 0;
          }
        }

        ({a: new Foo().x} = {a: 0});
        """);
  }

  @Test
  public void testEs6DefaultParameter() {
    test(
        "function foo(x, y = this.a = 1) {}", // preserve newline
        "function foo(x, y =          1) {}");
    testSame("this.a = 1; function foo(x, y = this.a) {}");
  }

  @Test
  public void testEs8AsyncFunction() {
    test(
        """
        async function foo(promise) { // preserve newlines
           this.x = 1;
           return await promise;
        }
        """,
        """
        async function foo(promise) { // preserve newlines

           return await promise;
        }
        """);

    testSame(
        """
        async function foo() {
           this.x = 1;
           return await this.x;
        }
        """);

    testSame(
        """
        this.x = 1;
        async function foo() {
           return await this.x;
        }
        """);
  }

  @Test
  public void testField() {
    test(
        """
        class C {
          x = 1;
          y;
          z = 'hi';
          static x = 1;
          static y;
          static z = 'hi';
        }
        """,
        """
        class C {

        }
        """);
  }

  @Test
  public void testStaticFieldReferencingClassName() {
    this.keepGlobals = false;
    this.keepLocals = false;

    test(
        """
        const C = class {
          constructor() {
          }
          static x = new C();
          static method() {
            return C.x;
          }
        }
        """,
        """
        const C = class {
          constructor() {
          }
          static x = new C();
        }
        """);

    test(
        """
        const C = class {
          constructor() {
          }
          static x = new C();
          static y = alert();
          static method() {
            return C.x;
          }
        }
        """,
        """
        const C = class {
          constructor() {
          }
          static x = new C();
          static y = alert();
        }
        """);
  }

  @Test
  public void testComputedField() {
    testSame(
        """
        class C {
          ['x'] = 1;
          'y';
          1 = 'hi';
          static ['x'] = 1;
          static 'y';
          static 1 = 'hi';
        }
        """);
  }

  @Test
  public void testMixedField() {
    // Computed properties cannot be removed, so only non-computed properties are removed
    test(
        """
        class C {
          x = 1;
          y;
          z = 'hi';
          static x = 1;
          static y;
          static z = 'hi';
          ['x'] = 1;
          'y';
          1 = 'hi';
          static ['x'] = 1;
          static 'y';
          static 1 = 'hi';
        }
        """,
        """
        class C {
          ['x'] = 1;
          'y';
          1 = 'hi';
          static ['x'] = 1;
          static 'y';
          static 1 = 'hi';
        }
        """);

    testSame(
        """
        class C {
          [alert()] = 5;
        }
        """);

    testSame(
        """
        class C {
          static x = alert();
        }
        """);

    testSame(
        """
        class C {
          x = alert();
        }
        """);
  }

  @Test
  public void testPureOrBreakMyCode() {
    test(
        """
        class C {
          constructor() {
           /** @const */this.used = /** @pureOrBreakMyCode */(alert());
           /** @const */this.unused = /** @pureOrBreakMyCode */(alert());
          }
        };
        /** @const */C.used = /** @pureOrBreakMyCode */(alert());
        /** @const */C.unused = /** @pureOrBreakMyCode */(alert());
        function foo() {
          return C.used;
        }
        foo();
        function bar() {
          return new C().used;
        }
        bar();
        """,
        """
        class C {
          constructor() {
            /** @const */this.used = /** @pureOrBreakMyCode */(alert());
          }
        };
        /** @const */C.used = /** @pureOrBreakMyCode */(alert());
        function foo() {
          return C.used;
        }
        foo();
        function bar() {
          return new C().used;
        }
        bar();
        """);
  }

  @Test
  public void testUnusedCodeInStaticInitializationBlock() {
    this.keepLocals = false;

    test(
        """
        class C {
          static {
            let x = 1;
          }
        }
        """,
        """
        class C {
          static {
          }
        }
        """);
  }

  @Test
  public void testClassReferencedInFieldInitializer() {
    this.keepLocals = false;
    this.keepGlobals = false;

    testSame(
        """
        class One {
          field = 1;
        }
        class Two {
          field = new One();
        }
        const two = new Two();
        alert(two);
        alert(two.field);
        alert(two.field.field);
        """);
  }
}
