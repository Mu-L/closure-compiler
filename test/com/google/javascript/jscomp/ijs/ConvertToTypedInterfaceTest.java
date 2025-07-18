/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.ijs;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ConvertToTypedInterface}. */
@RunWith(JUnit4.class)
public final class ConvertToTypedInterfaceTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ConvertToTypedInterface(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Test
  public void testInferAnnotatedTypeFromTypeInference() {
    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");
  }

  @Test
  public void testExternsDefinitionsRespected() {
    test(externs("/** @type {number} */ var x;"), srcs("x = 7;"), expected(""));
  }

  @Test
  public void testUnannotatedDeclaration() {
    test("var x;", "/** @const {UnusableType} */ var x;");
  }

  @Test
  public void testEmptyClass() {
    test("class x {;}", "class x {}");
  }

  @Test
  public void testComputedFieldNoRHS() {
    test(
        """
        const s = Symbol();
        class Foo {
          [s]
        }
        """,
        """
        const s = Symbol();
        class Foo {
        }
        """);
    test(
        """
        class Foo {
          [Symbol.iterator]
        }
        """,
        """
        class Foo {
          [Symbol.iterator]
        }
        """);
  }

  @Test
  public void testDoubleAssignmentField() {
    test(
        """
        let Snackbar = Snackbar1 = class Snackbar {
          x = 5
        }
        """,
        "/** @const @type {UnusableType} */ var Snackbar;");
  }

  @Test
  public void testDoubleAssignmentStaticField() {
    test(
        """
        let A = B = class C {
          static y = true
        }
        """,
        "/** @const @type {UnusableType} */ var A;");
  }

  @Test
  public void testSuperClassFields() {
    test(
        """
        class First{
          /** @const {number} */ fromFirst;
          constructor() {
            this.fromFirst = 1;
          };
        };
        class Second extends First {
           /** @const {number} */ fromSecond;
          constructor() {
            super();
            /** @override */this.fromFirst = 7;
            this.fromSecond = 5;
          };
        };
        """,
        """
        class First {
          /** @const {number} */ fromFirst;
          constructor() {
          }
        }
        class Second extends First {
           /** @const {number} */ fromSecond;
          constructor() {
          }
        }
         /** @override */ Second.prototype.fromFirst;
        """);
  }

  @Test
  public void testThisAssignment() {
    // When a public field is created and assigned with a constructor, we only care about the
    // declaration.
    test(
        """
        class Foo {
          /** @const {number} */ unique;
          constructor() {
            this.unique = 5;
           }
        }
        """,
        """
        class Foo {
          /** @const {number}*/ unique;
          constructor() {
          }
        }
        """);
    test(
        """
        class Foo {
          /** @const {number} */ fieldOne;
          /** @const {boolean} */ fieldTwo;
          /** @const {string} */ fieldThree;
          constructor() {
            this.fieldOne = 1;
            this.fieldTwo = true
            this.fieldThree = 'three'
           }
        }
        """,
        """
        class Foo {
          /** @const {number} */ fieldOne;
          /** @const {boolean} */ fieldTwo;
          /** @const {string} */ fieldThree;
          constructor() {
          }
        }
        """);

    test(
        """
        class Foo {
          /** @const {number} */ fieldOne;
          /** @const {boolean} */ fieldTwo;
          /** @const {string} */ fieldThree;
          constructor(x,y,z) {
            this.fieldOne = x;
            this.fieldTwo = y
            this.fieldThree = z
           }
        }
        """,
        """
        class Foo {
          /** @const {number} */ fieldOne;
          /** @const {boolean} */ fieldTwo;
          /** @const {string} */ fieldThree;
          constructor(x,y,z) {
          }
        }
        """);
  }

  @Test
  public void testPrototypeDeclared() {
    // When the prototype is declared, we only care about the initial declaration for public fields.
    test(
        """
        class Foo {
          /** @const {number} */sameField = 10;
        }
        Foo.prototype.sameField = 8;
        """,
        """
        class Foo {
          /** @const {number} */ sameField;
        }
        """);
  }

  @Test
  public void testThisAndPrototype() {
    test(
        """
        class Foo {
          /** @const @type {number} */sameField = 10;
          constructor() {
            this.sameField = 5;
           }
        }
        Foo.prototype.sameField = 8;
        """,
        """
        class Foo {
          /** @const @type {number} */ sameField;
          constructor() {
          }
        }
        """);
  }

  @Test
  public void testComputedFieldDef() {
    // Computed Field Def's are unanalyzable to the compiler so they will be dropped.
    test(
        """
        const PREFIX = 'prefix';
        /** @unrestricted*/ class Foo {
          [`${PREFIX}Field`] = 'prefixed field';
        }
        """,
        """
         /** @const @type {string} */
        var PREFIX;
        /** @unrestricted*/ class Foo {
        }
        """);
    test(
        """
        /** @unrestricted*/ class Foo {
          [Math.random()] = 'gone';
        }
        """,
        """
        /** @unrestricted*/ class Foo {
        }
        """);
  }

  @Test
  public void testJSDocMemberFieldDef() {
    // Add type if not present
    test(
        """
        class Foo {
          /** @const  */ myField = 5;
        }
        """,
        """
        class Foo {
          /** @const {number} */ myField;
        }
        """);
    // If no JSdoc present, set to UnusableType
    test(
        """
        class Foo {
          myField = 5;
        }
        """,
        """
        class Foo {
          /** @const @type {UnusableType} */ myField;
        }
        """);
  }

  @Test
  public void testStaticMemberFieldDef() {
    // check that static remains in the declaration even when the assignment is removed
    test(
        """
        class Foo {
          /** @const @type {number}*/ static myField = 5;
        }
        """,
        """
        class Foo {
          /** @const @type {number}*/ static myField;
        }
        """);
  }

  @Test
  public void testUpdatingStaticField() {
    // Updating the static field should not be included in i.js
    test(
        """
        class Foo {
          /** @const {number}*/static myField = 1;
        }
        Foo.myField = 2;
        """,
        """
        class Foo {
          /** @const {number}*/static myField;
        }
        """);
  }

  @Test
  public void testThisInFieldInitializer() {
    test(
        """
        class Base {
          /** @const @type {string}*/ baseField = 'base field';
          /** @const @type {string}*/anotherBaseField = this.baseField;
        }
        """,
        """
        class Base {
          /** @const @type {string}*/ baseField;
          /** @const @type {string}*/ anotherBaseField;
        }
        """);
    test(
        """
        class Base {
          /** @const @type {string}*/ static fieldOne = 'I am static';
          /** @const @type {string}*/ static fieldTwo = this.fieldOne;
        }
        """,
        """
        class Base {
          /** @const @type {string}*/ static fieldOne;
          /** @const @type {string}*/ static fieldTwo;
        }
        """);

    test(
        """
        class Base {
          /** @const @type {string}*/ static fieldStatic = 'I am static';
          /** @const @type {string}*/ instanceField = this.fieldStatic;
        }
        """,
        """
        class Base {
          /** @const @type {string}*/ static fieldStatic;
          /** @const @type {string}*/ instanceField;
        }
        """);
  }

  @Test
  public void testSameFieldNameInDifferentClass() {
    test(
        """
        class Base {
          /** @const @type {string}*/ sameField;
        }
        class Foo {
          constructor() {
            /** @const {number}*/ this.sameField;
          }
        }
        """,
        """
        class Base {
           /** @const @type {string}*/ sameField;
        }
        class Foo {
          constructor(){
          }
        }
        /** @const {number}*/ Foo.prototype.sameField
        """);
  }

  @Test
  public void testSameFieldDeclared() {
    // If the one of the duplicated fields contains type annotation, we will keep that one.
    test(
        """
        class Base {
          sameField;
          /** @const @type {string}*/ sameField = 'first';
        }
        """,
        """
        class Base {
          /** @const @type {string}*/ sameField
        }
        """);

    test(
        """
        class Base {
          /** @const @type {string}*/ sameField = 'first';
        }
        class Foo {
          /** @const {number}*/ sameField = 3;
        }
        """,
        """
        class Base {
          /** @const @type {string}*/ sameField
        }
        class Foo {
         /** @const @type {number}*/ sameField
        }
        """);
  }

  @Test
  public void testSimpleConstJsdocPropagation() {
    test("/** @const */ var x = 5;", "/** @const {number} */ var x;");
    test("/** @const */ var x = 5n;", "/** @const {bigint} */ var x;");
    test("/** @const */ var x = true;", "/** @const {boolean} */ var x;");
    test("/** @const */ var x = 'str';", "/** @const {string} */ var x;");
    test("/** @const */ var x = `str`;", "/** @const {string} */ var x;");
    test("/** @const */ var x = null;", "/** @const {null} */ var x;");
    test("/** @const */ var x = void 0;", "/** @const {void} */ var x;");
    test("/** @const */ var x = /a/;", "/** @const {!RegExp} */ var x;");

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = 5; }",
        "/** @constructor */ function Foo() {} \n /** @const {number} */ Foo.prototype.x;");

    test(
        "/** @const */ var x = cond ? true : 5;",
        "/** @const {UnusableType} */ var x;",
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  @Test
  public void testConstKeywordWithAnnotatedType() {
    test("/** @type {number} */ const x = 5;", "/** @const {number} */ var x;");
    test("/** @type {!Foo} */ const f = new Foo;", "/** @const {!Foo} */ var f;");
  }

  @Test
  public void testConstKeywordJsdocPropagation() {
    test("const x = 5;", "/** @const {number} */ var x;");

    test(
        "const x = 5, y = 'str', z = /abc/;",
        """
        /** @const {number} */ var x;
        /** @const {string} */ var y;
        /** @const {!RegExp} */ var z;
        """);

    test(
        "const x = cond ? true : 5;",
        "/** @const {UnusableType} */ var x;",
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  @Test
  public void testPropagateConstCast() {
    test("const x = /** @type {!Array<number>} */ ([]);", "/** @const {!Array<number>} */ var x;");

    test(
        "let /** (string|number) */ x = /** @type {number} */ (5);",
        "/** @type {(string|number)} */ var x;");
  }

  @Test
  public void testSplitMultiDeclarations() {
    test(
        "var /** number */ x = 4, /** string */ y = 'str';",
        "/** @type {number} */ var x; /** @type {string} */ var y;");

    test(
        "var /** number */ x, /** string */ y;",
        "/** @type {number} */ var x; /** @type {string} */ var y;");

    test(
        "let /** number */ x = 4, /** string */ y = 'str';",
        "/** @type {number} */ var x; /** @type {string} */ var y;");

    test(
        "let /** number */ x, /** string */ y;",
        "/** @type {number} */ let x; /** @type {string} */ let y;");
  }

  @Test
  public void testThisPropertiesInConstructors() {
    test(
        "/** @constructor */ function Foo() { /** @const {number} */ this.x; }",
        """
        /** @constructor */ function Foo() {}
        /** @const {number} */ Foo.prototype.x
        """);

    test(
        "/** @constructor */ function Foo() { this.x = undefined; }",
        """
        /** @constructor */ function Foo() {}
        /** @const {UnusableType} */ Foo.prototype.x;
        """);

    test(
        "/** @constructor */ function Foo() { /** @type {?number} */ this.x = null; this.x = 5; }",
        """
        /** @constructor */ function Foo() {}
        /** @type {?number} */ Foo.prototype.x;
        """);

    test(
        "/** @constructor */ function Foo() { /** @const */ this.x = cond ? true : 5; }",
        """
        /** @constructor */ function Foo() {} /** @const {UnusableType} */ Foo.prototype.x;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  @Test
  public void testNonThisPropertiesInConstructors() {
    test(
        "/** @constructor */ function Foo() { const obj = {}; obj.name = () => 5; alert(obj); }",
        "/** @constructor */ function Foo() {}");
  }

  @Test
  public void testThisPropertiesInConstructorsAndPrototype() {
    test(
        """
        /** @constructor */ function Foo() { this.x = null; }
        /** @type {?number} */ Foo.prototype.x = 5;
        """,
        """
        /** @constructor */ function Foo() {}
        /** @type {?number} */ Foo.prototype.x;
        """);

    test(
        """
        /** @constructor */ function Foo() { this.x = null; }
        /** @type {?number} */ Foo.prototype.x;
        """,
        """
        /** @constructor */ function Foo() {}
        /** @type {?number} */ Foo.prototype.x;
        """);

    test(
        """
        /** @constructor */ function Foo() { this.x = null; }
        Foo.prototype.x = 5;
        """,
        """
        /** @constructor */ function Foo() {}
        /** @const {UnusableType} */ Foo.prototype.x;
        """);
  }

  @Test
  public void testConstJsdocPropagationForGlobalNames() {
    test(
        "/** @type {!Array<string>} */ var x = []; /** @const */ var y = x;",
        "/** @type {!Array<string>} */ var x; /** @const */ var y = x;");

    test(
        "/** @type {Object} */ var o = {}; /** @type {number} */ o.p = 5; /** @const */ var y = o;",
        "/** @type {Object} */ var o; /** @type {number} */ o.p; /** @const */ var y = o;");
  }

  @Test
  public void testConstJsdocPropagationForConstructorNames() {
    test(
        """
        /** @constructor */
        function Foo(/** number */ x) {
          /** @const */ this.x = x;
        }
        """,
        """
        /** @constructor */ function Foo(/** number */ x) {}
        /** @const {number} */ Foo.prototype.x;
        """);

    test(
        """
        /** @constructor @param {!Array<string>} arr */
        function Foo(arr) {
          /** @const */ this.arr = arr;
        }
        """,
        """
        /** @constructor @param {!Array<string>} arr */ function Foo(arr) {}
        /** @const {!Array<string>} */ Foo.prototype.arr;
        """);

    test(
        """
        class Foo {
          constructor(/** number */ x) {
            /** @const */ this.x = x;
          }
        }
        """,
        """
        class Foo {
          constructor(/** number */ x) {}
        }
        /** @const {number} */ Foo.prototype.x;
        """);

    test(
        """
        class Foo {
          /** @param {number} x */
          constructor(x) {
            /** @const */ this.x = x;
          }
        }
        """,
        """
        class Foo {
          /** @param {number} x */
          constructor(x) {}
        }
        /** @const {number} */ Foo.prototype.x;
        """);
  }

  @Test
  public void testClassMethodsConflictWithOtherAssignment() {
    test(
        """
        class Foo {
          /** @return {number} */
          method() {}
        }
        Foo.prototype.method = wrap(Foo.prototype.method);
        """,
        """
        class Foo {
          /** @return {number} */
          method() {}
        }
        """);

    test(
        """
        class Foo {
          constructor() {
            this.method = wrap(Foo.prototype.method);
          }
          /** @return {number} */
          method() {}
        }
        """,
        """
        class Foo {
          constructor() {}
          /** @return {number} */
          method() {}
        }
        """);
  }

  @Test
  public void testMultipleSameNamedThisProperties() {
    test(
        """
        class Foo {
          constructor(/** number */ x) {
            /** @const */ this.x = x;
          }
        }
        /** @template T */
        class Bar {
          constructor(/** T */ x) {
            /** @const */ this.x = x;
          }
        }
        """,
        """
        class Foo {
          constructor(/** number */ x) {}
        }
        /** @const {number} */ Foo.prototype.x;
        /** @template T */
        class Bar {
          constructor(/** T */ x) {}
        }
        /** @const {T} */ Bar.prototype.x;
        """);
  }

  @Test
  public void testGlobalThis() {
    testSame("/** @const */ this.globalNamespace = {};");
    test(
        "/** @const */ this.globalNamespace = this.globalNamespace || {};",
        "/** @const */ this.globalNamespace = {};");
  }

  @Test
  public void testGoogAddSingletonGetter() {
    testSame("class Foo {}  goog.addSingletonGetter(Foo);");
  }

  @Test
  public void testLegacyGoogModule() {
    testSame(
        """
        goog.module('a.b.c');
        goog.module.declareLegacyNamespace();

        exports = class {};
        """);
  }

  @Test
  public void testExternsAlias() {
    testSame("const winAlias = window;");
    testSame("const winAlias = window; const locationAlias = winAlias.location;");
  }

  @Test
  public void testConstructorAlias1() {
    testSame(
        """
        /** @constructor */
        function Foo() {}
        /** @const */ var FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias2() {
    testSame(
        """
        goog.module('a.b.c');

        /** @constructor */
        function Foo() {}
        /** @const */ var FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias3() {
    testSame(
        """
        class Foo {}
        /** @const */ var FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias4() {
    testSame(
        """
        goog.module('a.b.c');

        class Foo {}
        /** @constructor */ var FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias5() {
    testSame(
        """
        /** @constructor */
        function Foo() {}
        /** @constructor */ var FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias6() {
    testSame(
        """
        goog.provide('a.b.c.Foo');
        goog.provide('FooAlias');

        /** @constructor */
        a.b.c.Foo = function() {};

        /** @const */ var FooAlias = a.b.c.Foo;
        """);
  }

  @Test
  public void testConstructorAlias7() {
    testSame(
        """
        class Foo {}
        const FooAlias = Foo;
        """);
  }

  @Test
  public void testConstructorAlias8() {
    testSame(
        """
        goog.module('a.b.c');

        class Foo {}
        const FooAlias = Foo;
        """);
  }

  @Test
  public void testRequireAlias1() {
    testSame(
        """
        goog.provide('FooAlias');

        goog.require('a.b.c.Foo');

        /** @const */ var FooAlias = a.b.c.Foo;
        """);
  }

  @Test
  public void testRequireAlias2() {
    testSame(
        """
        goog.provide('FooAlias');
        goog.provide('BarAlias');

        goog.require('a.b.c');

        /** @const */ var FooAlias = a.b.c.Foo;
        /** @const */ var BarAlias = a.b.c.Bar;
        """);
  }

  @Test
  public void testRequireAlias3() {
    testSame(
        """
        goog.module('FooAlias');

        const Foo = goog.require('a.b.c.Foo');

        exports = Foo;
        """);
  }

  @Test
  public void testRequireAlias4() {
    testSame(
        """
        goog.module('FooAlias');

        const {Foo} = goog.require('a.b.c');

        exports = Foo;
        """);
  }

  @Test
  public void testRequireAlias5() {
    test(
        """
        goog.module('FooAlias');

        const {Foo} = goog.require('a.b.c');
        /** @const {number} */
        Foo = 1;

        exports = Foo;
        """,
        """
        goog.module('FooAlias');

        /** @const @type {number} */ var Foo;

        exports = Foo;
        """);
  }

  @Test
  public void testRequireTypeAlias1() {
    testSame(
        """
        goog.provide('FooAlias');

        goog.requireType('a.b.c.Foo');

        /** @const */ var FooAlias = a.b.c.Foo;
        """);
  }

  @Test
  public void testRequireTypeAlias2() {
    testSame(
        """
        goog.provide('FooAlias');
        goog.provide('BarAlias');

        goog.requireType('a.b.c');

        /** @const */ var FooAlias = a.b.c.Foo;
        /** @const */ var BarAlias = a.b.c.Bar;
        """);
  }

  @Test
  public void testRequireTypeAlias3() {
    testSame(
        """
        goog.module('FooAlias');

        const Foo = goog.requireType('a.b.c.Foo');

        exports = Foo;
        """);
  }

  @Test
  public void testRequireTypeAlias4() {
    testSame(
        """
        goog.module('FooAlias');

        const {Foo} = goog.requireType('a.b.c');

        exports = Foo;
        """);
  }

  @Test
  public void testRequireTypeAlias5() {
    test(
        """
        goog.module('FooAlias');

        const {Foo, Bar} = goog.requireType('a.b.c');
        /** @const {string} */
        Foo = 'foo';

        exports = Foo;
        """,
        """
        goog.module('FooAlias');

        const {Bar} = goog.requireType('a.b.c');
        /** @const @type {string} */ var Foo;

        exports = Foo;
        """);
  }

  @Test
  public void testDestructuredAlias1() {
    testSame("const {Foo} = a.b.c; const {Bar} = x; exports = Foo;");
  }

  @Test
  public void testDestructuredAlias2() {
    testSame("const {Foo, Bar} = a.b.c;  exports = Foo;");
  }

  @Test
  public void testDestructuredAlias3() {
    test(
        """
        const {Foo} = a.b.c, bar = 1;
        exports = Foo;
        """,
        """
        const {Foo} = a.b.c;

        /** @const @type {number} */ var bar;

        exports = Foo;
        """);
  }

  @Test
  public void testDestructuredAlias4() {
    test(
        "const {Foo} = a.b.c, Baz = p.q.Baz; exports = Foo;",
        "const {Foo} = a.b.c; const Baz = p.q.Baz; exports = Foo;");
  }

  @Test
  public void testDuplicateDeclarationWAliasRemoved1() {
    test(
        """
        const {Foo, Bar} = x.y;
        /** @const {number} */
        Bar = 1;

        exports = Foo;
        """,
        """
        const {Foo} = x.y

        /** @const @type {number} */ var Bar;

        exports = Foo;
        """);
  }

  @Test
  public void testDuplicateDeclarationWAliasRemoved2() {
    test(
        """
        /** @const {string} */
        Foo = 'hello';

        const {Foo, Bar} = x.y;

        /** @const {number} */
        Bar = 1;

        exports = Foo;
        """,
        """
        /** @const @type {string} */ var Foo;

        /** @const @type {number} */ var Bar;

        exports = Foo;
        """);
  }

  @Test
  public void testDuplicateDeclarationWAliasRemoved3() {
    test(
        """
        /** @const {string} */
        Foo = 'hello';

        const {Foo, Bar, Baz} = x.y;

        /** @const {number} */
        Baz = 1;

        exports = Foo;
        """,
        """
        /** @const @type {string} */ var Foo;

        const {Bar} = x.y;

        /** @const @type {number} */ var Baz;

        exports = Foo;
        """);
  }

  @Test
  public void testBreakDownDestructuringForConst() {

    test(
        """
        goog.module('m');
        const {x,y}=fn();
        exports.x=x;
        exports.y=y;
        """,
        """
        goog.module("m");
        /** @const @type {UnusableType} */
        var x;
        /** @const @type {UnusableType} */
        var y;
        exports.x=x;
        exports.y = y;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));

    test(
        """
        goog.module('m');
        const {x=0} = fn();
        exports.x = x;
        """,
        """
        goog.module("m");
        /** @const @type {UnusableType} */
        var x;
        exports.x = x;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));

    test(
        """
        goog.module('m');
        const {x:{y}} = fn();
        exports.x = x;
        """,
        """
        goog.module("m");
        /** @const @type {UnusableType} */
        var y;
        exports.x = x;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));

    test(
        """
        goog.module('m');
        const {[x()]:x} = fn();
        exports.x = x;
        """,
        """
        goog.module("m");
        /** @const @type {UnusableType} */
        var x;
        exports.x = x;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  @Test
  public void testBreakDownDestructuringWarning() {
    testWarning(
        """
        goog.module('m');
        const x = ({y: obj.y} = fn());
        exports.x = x;
        """,
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  @Test
  public void testDuplicateDeclarationWAliasNotRemoved() {
    test(
        """
        const {Foo, Bar} = x.y;

        Bar = z;

        exports = Foo;
        """,
        """
        const {Foo, Bar} = x.y;

        exports = Foo;
        """);
  }

  @Test
  public void testBreakDownDestructuringForLet() {
    test(
        """
        let {Foo, Bar} = a.b.c;
        exports = Foo;
        """,
        """
        /** @const @type {UnusableType} */
        var Foo
        /** @const @type {UnusableType} */
        var Bar;
        exports = Foo;
        """,
        warning(ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
  }

  @Test
  public void testAtConstAnnotationAlias() {
    test(
        "/** @const */ var x = a.b.c; var y = x;",
        "/** @const */ var x = a.b.c; /** @const @type {UnusableType} */ var y;");
  }

  @Test
  public void testConstPropagationPrivateProperties1() {
    test(
        """
        /** @constructor */
        function Foo() {
          /** @const @private */ this.x = someComplicatedExpression();
        }
        """,
        """
        /** @constructor */ function Foo() {}
        /** @const @private {UnusableType} */ Foo.prototype.x;
        """);
  }

  @Test
  public void testConstPropagationPrivateProperties2() {
    test(
        """
        goog.provide('a.b.c');

        /** @private @const */
        a.b.c.helper_ = someComplicatedExpression();
        """,
        "goog.provide('a.b.c');   /** @private @const {UnusableType} */ a.b.c.helper_;");
  }

  @Test
  public void testOverrideAnnotationCountsAsDeclaration() {
    testSame(
        """
        goog.provide('x.y.z.Bar');

        goog.require('a.b.c.Foo');

        /** @constructor @extends {a.b.c.Foo} */
        x.y.z.Bar = function() {};

        /** @override */
        x.y.z.Bar.prototype.method = function(a, b, c) {};
        """);

    testSame(
        """
        goog.module('x.y.z');

        const {Foo} = goog.require('a.b.c');

        /** @constructor @extends {Foo} */
        const Bar = class {
           /** @override */
           method(a, b, c) {}
        }
        exports.Bar = Bar;
        """);
  }

  @Test
  public void testConstJsdocPropagationForNames_optional() {
    test(
        """
        /** @constructor */
        function Foo(/** number= */ opt_x) {
          /** @const */ this.x = opt_x;
        }
        """,
        """
        /** @constructor */ function Foo(/** number= */ opt_x) {}
        /** @const {number|undefined} */ Foo.prototype.x;
        """);
  }

  @Test
  public void testNotConfusedByOutOfOrderDeclarations() {
    test(
        """
        /** @constructor */
        function Foo(/** boolean= */ opt_tag) {
          if (opt_tag) {
            Foo.tag = opt_tag;
          }
        }
        /** @type {boolean} */ Foo.tag = true;
        """,
        """
        /** @constructor */
        function Foo(/** boolean= */ opt_tag) {}
        /** @type {boolean} */ Foo.tag;
        """);
  }

  @Test
  public void testConstJsdocPropagationForNames_rest() {
    test(
        """
        /**
         * @constructor
         * @param {...number} nums
         */
        function Foo(...nums) {
          /** @const */ this.nums = nums;
        }
        """,
        """
        /**
         * @constructor
         * @param {...number} nums
         */
        function Foo(...nums) {}
        /** @const {!Array<number>} */ Foo.prototype.nums;
        """);
  }

  @Test
  public void testOptionalRestParamFunction() {
    test(
        """
        /**
         * @param {?Object} o
         * @param {string=} str
         * @param {number=} num
         * @param {...!Object} rest
         */
        function foo(o, str = '', num = 5, ...rest) {}
        """,
        """
        /**
         * @param {?Object} o
         * @param {string=} str
         * @param {number=} num
         * @param {...!Object} rest
         */
        function foo(o, str=void 0, num=void 0, ...rest) {}
        """);
  }

  @Test
  public void testDefaultValuesRemain() {
    test("function f(x = 0) {}", "function f(x = void 0) {}");
    test("function f(x = window.foobar()) {}", "function f(x = void 0) {}");
  }

  @Test
  public void testGoogCallerLocationDefaultValueIsPreserved() {
    testSame("function f(a = goog.callerLocation()) {}");
    test(
        "function f(a = 1, b = goog.callerLocation()) {}",
        "function f(a = void 0, b = goog.callerLocation()) {}");
  }

  @Test
  public void testConstJsdocPropagationForNames_defaultValue() {
    test(
        """
        /**
         * @constructor
         * @param {string=} str
         */
        function Foo(str = '') {
          /** @const */ this.s = str;
        }
        """,
        """
        /**
         * @constructor
         * @param {string=} str
         */
        function Foo(str = void 0) {}
        /** @const {string} */ Foo.prototype.s;
        """);

    test(
        """
        class Foo {
          /** @param {string=} str */
          constructor(str = '') {
            /** @const */ this.s = str;
          }
        }
        """,
        """
        class Foo {
          /** @param {string=} str */
          constructor(str = void 0) {}
        }
        /** @const {string} */ Foo.prototype.s;
        """);
  }

  @Test
  public void testConstWithDeclaredTypes() {
    test("/** @const @type {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const {number} */ var n = compute();", "/** @const @type {number} */ var n;");
    test("/** @const @return {void} */ var f = compute();", "/** @const @return {void} */ var f;");
    test("/** @const @this {Array} */ var f = compute();", "/** @const @this {Array} x */ var f;");

    test(
        "/** @const @param {number} x */ var f = compute();",
        "/** @const @param {number} x */ var f;");

    test(
        "/** @const @constructor x */ var Foo = createConstructor();",
        "/** @const @constructor x */ var Foo;");
  }

  @Test
  public void testRemoveUselessStatements() {
    test("34", "");
    test("'str'", "");
    test("({x:4})", "");
    test("debugger;", "");
    test("throw 'error';", "");
    test("label: debugger;", "");
  }

  @Test
  public void testRemoveUnnecessaryBodies() {
    test("function f(x,y) { /** @type {number} */ z = x + y; return z; }", "function f(x,y) {}");

    test(
        "/** @return {number} */ function f(/** number */ x, /** number */ y) { return x + y; }",
        "/** @return {number} */ function f(/** number */ x, /** number */ y) {}");

    test(
        "class Foo { method(/** string */ s) { return s.split(','); } }",
        "class Foo { method(/** string */ s) {} }");
  }

  @Test
  public void testRemoveEmptyMembers() {
    test(
        "class Foo { ;; method(/** string */ s) {};; }",
        "class Foo { method(/** string */ s) {} }");
  }

  @Test
  public void testEs6Modules() {
    testSame("export default class {}");

    testSame("import Foo from '/foo';");

    testSame("export class Foo {}");

    testSame("import {Foo} from '/foo';");

    testSame(
        """
        import {Baz} from '/baz';

        export /** @constructor */ function Foo() {}
        /** @type {!Baz} */ Foo.prototype.baz
        """);

    testSame(
        """
        import {Bar, Baz} from '/a/b/c';

        class Foo extends Bar {
          /** @return {!Baz} */ getBaz() {}
        }

        export {Foo};
        """);

    test(
        """
        export class Foo {
          /** @return {number} */ getTime() { return Date.now(); }
        }
        export default /** @return {number} */ () => 6
        const BLAH = 'foobar';
        export {BLAH};
        """,
        """
        export class Foo {
          /** @return {number} */ getTime() {}
        }
        export default /** @return {number} */ () => {}
        /** @const {string} */ var BLAH;
        export {BLAH};
        """);
  }

  @Test
  public void testEs6ModulesExportedNameDeclarations() {
    testSame("/** @type {number} */ export let x;");
    testSame("/** @type {number} */ export var x;");

    test("/** @type {number} */ export var x = 5;", "/** @type {number} */ export var x;");
    test("/** @type {number} */ export const X = 5;", "/** @const {number} */ export var X;");
    // TODO(blickly): Ideally, we would leave let declarations alone
    test("/** @type {number} */ export let x = 5;", "/** @type {number} */ export var x;");

    test(
        "/** @type {number} */ export const X = 5, Y = z;",
        "/** @const {number} */ export var X; /** @const {number} */ export var Y;");
  }

  @Test
  public void testEs6ModulesDeclareModuleId() {
    testSame(
        """
        goog.declareModuleId('foo');
        /** @type {number} */ export var x;
        """);
  }

  @Test
  public void testGoogModules() {
    testSame(
        """
        goog.module('x.y.z');

        /** @constructor */ function Foo() {}

        exports = Foo;
        """);

    testSame(
        """
        goog.module('x.y.z');

        const Baz = goog.require('a.b.c');

        /** @constructor */ function Foo() {}
        /** @type {!Baz} */ Foo.prototype.baz

        exports = Foo;
        """);

    testSame(
        """
        goog.module('x.y.z');

        const {Bar, Baz} = goog.require('a.b.c');

        /** @constructor */ function Foo() {}
        /** @type {!Baz} */ Foo.prototype.baz

        exports = Foo;
        """);

    testSame(
        srcs(
            """
            goog.module('a.b.c');
            /** @constructor */ function Foo() {}
            Foo.prototype.display = function() {};
            exports = Foo;
            """,
            """
            goog.module('x.y.z');
            /** @constructor */ function Foo() {}
            Foo.prototype.display = function() {};
            exports = Foo;
            """));

    testSame(
        srcs(
            """
            /** @constructor */ function Foo() {}
            Foo.prototype.display = function() {};
            """,
            """
            goog.module('x.y.z');
            /** @constructor */ function Foo() {}
            Foo.prototype.display = function() {};
            exports = Foo;
            """));

    test(
        srcs(
            """
            goog.module('a.b.c');
            /** @constructor */ function Foo() {
              /** @type {number} */ this.x = 5;
            }
            exports = Foo;
            """,
            """
            goog.module('x.y.z');
            /** @constructor */ function Foo() {
              /** @type {number} */ this.x = 99;
            }
            exports = Foo;
            """),
        expected(
            """
            goog.module('a.b.c');
            /** @constructor */ function Foo() {}
            /** @type {number} */ Foo.prototype.x;
            exports = Foo;
            """,
            """
            goog.module('x.y.z');
            /** @constructor */ function Foo() {}
            /** @type {number} */ Foo.prototype.x;
            exports = Foo;
            """));
  }

  @Test
  public void testGoogModulesWithTypedefExports() {
    testSame(
        """
        goog.module('x.y.z');

        /** @typedef {number} */
        exports.Foo;
        """);
  }

  @Test
  public void testGoogModulesWithUndefinedExports() {
    testWarning(
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        exports = (new Baz).getFoobar();
        """,
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testWarning(
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        exports.foobar = (new Baz).getFoobar();
        """,
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  @Test
  public void testGoogModulesWithAnnotatedUninferrableExports() {
    test(
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        /** @type {Foobar} */
        exports.foobar = (new Baz).getFoobar();
        """,
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        /** @type {Foobar} */
        exports.foobar;
        """);

    test(
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        /** @type {Foobar} */
        exports = (new Baz).getFoobar();
        """,
        """
        goog.module('x.y.z');

        const Baz = goog.require('x.y.z.Baz');
        const Foobar = goog.require('f.b.Foobar');

        /** @type {Foobar} */
        exports = /** @type {?} */ (0);
        """);
  }

  @Test
  public void testCrossFileModifications() {
    test(
        """
        goog.module('a.b.c');
        othermodule.modify.something = othermodule.modify.something + 1;
        """,
        "goog.module('a.b.c');");

    test(
        """
        goog.module('a.b.c');
        class Foo {}
        Foo.something = othermodule.modify.something + 1;
        exports = Foo;
        """,
        """
        goog.module('a.b.c');
        class Foo {}
        /** @const {UnusableType} */ Foo.something
        exports = Foo;
        """);

    test(
        """
        goog.provide('a.b.c');
        otherfile.modify.something = otherfile.modify.something + 1;
        """,
        "goog.provide('a.b.c');");

    test(
        """
        goog.provide('a.b.c');
        a.b.c.something = otherfile.modify.something + 1;
        """,
        """
        goog.provide('a.b.c');
        /** @const {UnusableType} */ a.b.c.something;
        """);
  }

  @Test
  public void testRemoveCalls() {
    test("alert('hello'); window.clearTimeout();", "");

    testSame("goog.provide('a.b.c');");

    testSame("goog.provide('a.b.c'); goog.require('x.y.z');");
  }

  @Test
  public void testEnums() {
    test(
        "/** @const @enum {number} */ var E = { A: 1, B: 2, C: 3};",
        "/** @const @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    test(
        "/** @enum {number} */ var E = { A: foo(), B: bar(), C: baz()};",
        "/** @enum {number} */ var E = { A: 0, B: 0, C: 0};");

    // No corrosion into 0 for string enums. This allows conformance to emit fewer false positives.
    // Values longer than 10 gets truncated.
    test(
        "/** @enum {string} */ var E = { A: 'hello', B: 'world', C: 'some_string_longer_than_10'};",
        "/** @enum {string} */ var E = { A: 'hello', B: 'world', C: 'some_str..'};");

    test(
        "/** @enum {Object} */ var E = { A: {b: 'c'}, D: {e: 'f'} };",
        "/** @enum {Object} */ var E = { A: 0, D: 0};");

    test(
        "var x = 7; /** @enum {number} */ var E = { A: x };",
        "/** @const {UnusableType} */ var x; /** @enum {number} */ var E = { A: 0 };");
  }

  @Test
  public void testEnumInsideNamespace() {
    test(
        "const ns = { /** @enum {number} */ ENUM: { A: 1, B: 2, C: 3} };",
        "const ns = { /** @enum {number} */ ENUM: { A: 0, B: 0, C: 0} };");
  }

  @Test
  public void testTryCatch() {
    test(
        "try { /** @type {number} */ var n = foo(); } catch (e) { console.log(e); }",
        "/** @type {number} */ var n;");

    test(
        """
        try {
          /** @type {number} */ var start = Date.now();
          doStuff();
        } finally {
          /** @type {number} */ var end = Date.now();
        }
        """,
        "/** @type {number} */ var start; /** @type {number} */ var end;");
  }

  @Test
  public void testTemplatedClass() {

    test(
        """
        /** @template T */
        class Foo {
          /** @param {T} x */
          constructor(x) { /** @const */ this.x = x;}
        }
        """,
        """
        /** @template T */
        class Foo {
          /** @param {T} x */
          constructor(x) {}
        }
        /** @const {T} */ Foo.prototype.x;
        """);
  }

  @Test
  public void testConstructorBodyWithThisDeclaration() {
    test(
        "/** @constructor */ function Foo() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ function Foo() {} /** @type {number} */ Foo.prototype.num;");

    test(
        "/** @constructor */ function Foo(b) { if (b) { /** @type {number} */ this.num = 5; } }",
        "/** @constructor */ function Foo(b) {} /** @type {number} */ Foo.prototype.num;");

    test(
        "/** @constructor */ let Foo = function() { /** @type {number} */ this.num = 5;}",
        "/** @constructor */ let Foo = function() {}; /** @type {number} */ Foo.prototype.num;");

    test(
        "class Foo { constructor() { /** @type {number} */ this.num = 5;} }",
        "class Foo { constructor() {} } /** @type {number} */ Foo.prototype.num;");
  }

  @Test
  public void testConstructorBodyWithoutThisDeclaration() {
    test(
        "/** @constructor */ function Foo(o) { o.num = 8; var x = 'str'; }",
        "/** @constructor */ function Foo(o) {}");
  }

  @Test
  public void testIIFE() {
    test("(function(){ /** @type {number} */ var n = 99; })();", "");
  }

  @Test
  public void testConstants() {
    test("/** @const {number} */ var x = 5;", "/** @const {number} */ var x;");
  }

  @Test
  public void testDefines() {
    // NOTE: This is another pattern that is only allowed in externs.
    test(
        "/** @define {number} */ var x = 5;", //
        "/** @define {number} */ var x;");

    test(
        "/** @define {number} */ goog.define('goog.BLAH', 5);",
        "/** @define {number} */ goog.define('goog.BLAH', 0);");

    test(
        "/** @define {string} */ const BLAH = goog.define('goog.BLAH', 'blah');",
        "/** @define {string} */ const BLAH = goog.define('goog.BLAH', '');");

    test(
        "/** @define {boolean} */ goog.BLECH = goog.define('goog.BLAH', true);",
        "/** @define {boolean} */ goog.BLECH = goog.define('goog.BLAH', false);");

    test(
        "/** @define {number|boolean} */ const X = goog.define('goog.XYZ', true);",
        "/** @define {number|boolean} */ const X = goog.define('goog.XYZ', 0);");
  }

  @Test
  public void testNestedBlocks() {
    test("{ const x = foobar(); }", "");

    test("{ /** @const */ let x = foobar(); }", "");

    test("{ /** @const */ let x = foobar(); x = foobaz(); }", "");

    testWarning(
        "{ /** @const */ var x = foobar(); }",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  @Test
  public void testGoogProvidedTopLevelSymbol() {
    testSame("goog.provide('Foo');  /** @constructor */ Foo = function() {};");
  }

  @Test
  public void testIfs() {
    test("if (true) { var /** number */ x = 5; }", "/** @type {number} */ var x;");

    test(
        "if (true) { var /** number */ x = 5; } else { var /** string */ y = 'str'; }",
        "/** @type {number} */ var x; /** @type {string} */ var  y;");

    test("if (true) { if (false) { var /** number */ x = 5; } }", "/** @type {number} */ var x;");

    test(
        "if (true) {} else { if (false) {} else { var /** number */ x = 5; } }",
        "/** @type {number} */ var x;");
  }

  @Test
  public void testLoops() {
    test("while (true) { foo(); break; }", "");

    test(
        "for (var i = 0; i < 10; i++) { var field = 88; }",
        "/** @const {UnusableType} */ var i; /** @const {UnusableType} */ var field;");

    test(
        "for (var i = 0, arraySize = getSize(); i < arraySize; i++) { foo(arr[i]); }",
        "/** @const {UnusableType} */ var i; /** @const {UnusableType} */ var arraySize;");

    test("while (i++ < 10) { var /** number */ field = i; }", "/** @type {number} */ var field;");

    test(
        "label: while (i++ < 10) { var /** number */ field = i; }",
        "/** @type {number} */ var field;");

    test(
        "do { var /** number */ field = i; } while (i++ < 10);",
        "/** @type {number} */ var field;");

    test(
        "for (var /** number */ i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {number} */ var i; /** @type {number} */ var field;");

    test(
        "for (i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @type {number} */ var field;");

    test(
        "for (var i = 0; i < 10; i++) { var /** number */ field = i; }",
        "/** @const {UnusableType} */ var i; /** @type {number} */ var field;");
  }

  @Test
  public void testSymbols() {
    testSame("const sym = Symbol();");

    testSame("/** @const */ var sym = Symbol();");

    test("const sym = Symbol(computeDescription());", "const sym = Symbol();");

    test(
        "/** @type {symbol} */ var sym = Symbol.for(computeDescription());",
        "/** @type {symbol} */ var sym;");
  }

  @Test
  public void testNamespaces() {
    testSame("/** @const */ var ns = {}; /** @return {number} */ ns.fun = function(x,y,z) {}");

    testSame("/** @const */ var ns = {}; ns.fun = function(x,y,z) {}");

    test(
        "/** @const */ var ns = ns || {}; ns.fun = function(x,y,z) {}",
        "/** @const */ var ns = {}; ns.fun = function(x,y,z) {}");
  }

  @Test
  public void testNonemptyNamespaces() {
    testSame("/** @const */ var ns = {fun: function(x,y,z) {}}");

    test(
        "/** @const */ var ns = {/** @type {number} */ n: 5};",
        "/** @const */ var ns = {/** @type {number} */ n: 0};");

    // NOTE: This pattern typechecks when found in externs, but not for code.
    // Since the goal of this pass is intended to be used as externs, this is acceptable.
    test(
        "/** @const */ var ns = {/** @type {string} */ s: 'str'};",
        "/** @const */ var ns = {/** @type {string} */ s: 0};");

    test(
        "/** @const */ var ns = {/** @const */ s: 'blahblahblah'};",
        "/** @const */ var ns = {/** @const {string} */ s: 0};");

    test(
        "/** @const */ var ns = {untyped: foo()};",
        "/** @const */ var ns = {/** @const {UnusableType} */ untyped: 0};");
  }

  @Test
  public void testConstKeywordNamespaces() {
    testSame("const ns = {}; /** @return {number} */ ns.fun = function(x,y,z) {}");
    testSame("const ns = { /** @return {number} */ fun : goog.abstractMethod };");
    testSame("const ns = {fun: function(x,y,z) {}}");
    testSame("const ns = { /** @return {number} */ fun(x,y,z) {}}");
  }

  @Test
  public void testRemoveIgnoredProperties() {
    test(
        "/** @const */ var ns = {}; /** @return {number} */ ns['fun'] = function(x,y,z) {}",
        "/** @const */ var ns = {};");

    test(
        "/** @constructor */ function Foo() {} Foo.prototype['fun'] = function(x,y,z) {}",
        "/** @constructor */ function Foo() {}");

    test(
        "/** @constructor */ function Foo() {} /** @type {str} */ Foo['prototype'].method;",
        "/** @constructor */ function Foo() {}");
  }

  @Test
  public void testRemoveRepeatedProperties() {
    test(
        "/** @const */ var ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @type {number} */ ns.x;");

    test(
        "/** @const */ var ns = {}; ns.x = 5; ns.x = 7;",
        "/** @const */ var ns = {}; /** @const {UnusableType} */ ns.x;");

    test(
        "const ns = {}; /** @type {number} */ ns.x = 5; ns.x = 7;",
        "const ns = {}; /** @type {number} */ ns.x;");
  }

  @Test
  public void testRemoveRepeatedDeclarations() {
    test("/** @type {number} */ var x = 4; var x = 7;", "/** @type {number} */ var x;");

    test("/** @type {number} */ var x = 4; x = 7;", "/** @type {number} */ var x;");

    test("var x = 4; var x = 7;", "/** @const {UnusableType} */ var x;");

    test("var x = 4; x = 7;", "/** @const {UnusableType} */ var x;");
  }

  @Test
  public void testArrowFunctions() {
    testSame("/** @return {void} */ const f = () => {}");

    test("/** @return {number} */ const f = () => 5", "/** @return {number} */ const f = () => {}");

    test(
        "/** @return {string} */ const f = () => { return 'str' }",
        "/** @return {string} */ const f = () => {}");
  }

  @Test
  public void testDontRemoveGoogModuleContents() {
    testWarning(
        "goog.module('x.y.z'); var C = goog.require('a.b.C'); exports = new C;",
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testSame("goog.module('x.y.z.Foo'); exports = class {};");

    testSame("goog.module('x.y.z'); exports.Foo = class {};");

    testSame("goog.module('x.y.z.Foo'); class Foo {} exports = Foo;");

    testSame("goog.module('x.y.z'); class Foo {} exports.Foo = Foo;");

    testSame("goog.module('x.y.z'); class Foo {} exports = {Foo};");

    testSame(
        """
        goog.module('x.y.z');
        const C = goog.require('a.b.C');
        class Foo extends C {}
        exports = Foo;
        """);
  }

  @Test
  public void testDontPreserveUnknownTypeDeclarations() {
    testSame("goog.forwardDeclare('MyType'); /** @type {MyType} */ var x;");

    test(
        "goog.addDependency('zzz.js', ['MyType'], []); /** @type {MyType} */ var x;",
        "/** @type {MyType} */ var x;");

    // This is OK, because short-import goog.forwardDeclares don't declare a type.
    testSame("goog.module('x.y.z'); var C = goog.forwardDeclare('a.b.C'); /** @type {C} */ var c;");
  }

  @Test
  public void testAliasOfRequirePreserved() {
    testSame(
        """
        goog.provide('a.b.c');

        goog.require('ns.Foo');

        /** @const */
        a.b.c.FooAlias = ns.Foo;
        """);

    testSame(
        """
        goog.provide('a.b.c');

        goog.require('ns.Foo');

        /** @constructor */
        a.b.c.FooAlias = ns.Foo;
        """);

    testSame(
        """
        goog.module('mymod');

        const {Foo} = goog.require('ns.Foo');

        /** @const */
        var FooAlias = Foo;

        /** @param {!FooAlias} f */
        exports = function (f) {};
        """);

    testSame(
        """
        goog.module('mymod');

        var Foo = goog.require('ns.Foo');

        /** @constructor */
        var FooAlias = Foo;

        /** @param {!FooAlias} f */
        exports = function (f) {};
        """);
  }

  @Test
  public void testAliasOfNonRequiredName() {
    testSame(
        """
        goog.provide('a.b.c');

        /** @const */
        a.b.c.FooAlias = ns.Foo;
        """);

    testWarning(
        """
        goog.provide('a.b.c');

        /** @constructor */
        a.b.c.Bar = function() {
          /** @const */
          this.FooAlias = ns.Foo;
        };
        """,
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);

    testWarning(
        """
        goog.module('a.b.c');

        class FooAlias {
          constructor() {
            /** @const */
            this.FooAlias = window.Foo;
          }
        };
        """,
        ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE);
  }

  @Test
  public void testDuplicateClassMethods() {
    test(
        """
        /** @constructor */ var Foo = function() {};
        Foo.prototype.method = function() {};

        Foo = class {
          method() {}
        };
        """,
        """
        /** @constructor */ var Foo = function() {};
        Foo.prototype.method = function() {};
        """);
  }

  @Test
  public void testGoogScopeLeftoversAreRemoved() {
    test(
        """
        goog.provide('a.b.c.d.e.f.g');

        /** @const */ var $jscomp = $jscomp || {};
        /** @const */ $jscomp.scope = {};

        $jscomp.scope.strayVariable = function() {};

        a.b.c.d.e.f.g.Foo = class {};
        """,
        """
        goog.provide('a.b.c.d.e.f.g');

        a.b.c.d.e.f.g.Foo = class {};
        """);

    test(
        """
        goog.provide('a.b.c.d.e.f.g');

        /** @const */ var $jscomp = $jscomp || {};
        /** @const */ $jscomp.scope = {};

        /** @constructor */
        $jscomp.scope.strayCtor = function() { this.x = 5; };

        a.b.c.d.e.f.g.Foo = class {};
        """,
        """
        goog.provide('a.b.c.d.e.f.g');

        a.b.c.d.e.f.g.Foo = class {};
        """,
        warning(ConvertToTypedInterface.GOOG_SCOPE_HIDDEN_TYPE));

    test(
        """
        goog.provide('a.b.c.d.e.f.g');

        /** @const */ var $jscomp = $jscomp || {};
        /** @const */ $jscomp.scope = {};

        $jscomp.scope.strayClass = class {
          constructor() { this.x = 5 };
          method() {};
        };

        a.b.c.d.e.f.g.Foo = class {};
        """,
        """
        goog.provide('a.b.c.d.e.f.g');

        a.b.c.d.e.f.g.Foo = class {};
        """,
        warning(ConvertToTypedInterface.GOOG_SCOPE_HIDDEN_TYPE));

    test(
        """
        /** @const */ var $jscomp = $jscomp || {};
        /** @const */ $jscomp.scope = {};

        $jscomp.scope.strayClass = class {
          constructor() {
            this.Foo = class {};
          };
        };
        """,
        "",
        warning(ConvertToTypedInterface.GOOG_SCOPE_HIDDEN_TYPE));
  }

  @Test
  public void testDestructuringDoesntCrash() {
    test(
        """
        goog.module('a.b.c');

        const Enum = goog.require('Enum');
        const Foo = goog.require('Foo');

        const {A, B} = Enum;

        /** @type {Foo} */
        exports.foo = use(A, B);
        """,
        """
        goog.module('a.b.c');

        const Enum = goog.require('Enum');
        const Foo = goog.require('Foo');

        const {A, B} = Enum;

        /** @type {Foo} */
        exports.foo;
        """);
  }

  @Test
  public void testSameNamedStaticAndNonstaticMethodsDontCrash() {
    testSame(
        """
        const Foo = class {
          static method() {}
          method() {}
        }
        """);
  }

  @Test
  public void testRedeclarationOfClassMethodDoesntCrash() {
    test(
        """
        class Foo {
          constructor() {
            /** @private */
            this.handleEvent_ = this.handleEvent_.bind(this);
          }
          /** @private @param {Event} e */
          handleEvent_(e) {}
        }
        """,
        """
        class Foo {
          constructor() {}
          /** @private @param {Event} e */
          handleEvent_(e) {}
        }
        """);

    test(
        """
        class Foo {
          constructor() {
            /** @param {Event} e */
            this.handleEvent_ = function (e) {};
          }
          handleEvent_(e) {}
        }
        """,
        """
        class Foo {
          constructor() {
            /** @param {Event} e */
            this.handleEvent_ = function (e) {};
          }
        }
        """);
  }

  @Test
  public void testGoogGlobalTyped() {
    testSame("/** @const */ var goog = {}; /** @const */ goog.global = this;");
  }

  @Test
  public void testAnonymousClassDoesntCrash() {
    test("fooFactory(class { x = 5; });", "");

    test("fooFactory(class { x; constructor() { this.x = 5} });", "");

    test(
        "let Foo = fooFactory(class { x; constructor() { this.x = 5;} });", //
        "/** @const {UnusableType} */ var Foo;");

    test(
        "let Foo = fooFactory(class { constructor() {} });",
        "/** @const {UnusableType} */ var Foo;");

    test(
        """
        let Foo = fooFactory(class {
          constructor() {
            /** @type {number} */
            this.n = 5;
          }
        });
        """,
        "/** @const {UnusableType} */ var Foo;");

    test(
        """
        /** @type {function(new:Int)} */
        let Foo = fooFactory(class {
          constructor() {
            /** @type {number} */
            this.n = 5;
          }
        });
        """,
        "/** @type {function(new:Int)} */ var Foo;");
  }

  @Test
  public void testPolymerCallsDeclareType() {
    testSame("Polymer({is: 'exampleApp'});");
    testSame("const ExampleApp = Polymer({is: 'exampleApp'});");
  }

  @Test
  public void testWellKnownSymbolComputedPropsPreserved() {
    test(
        """
        class Foo {
          /** @return {?} */
          [Symbol.iterator]() { return []; }
          static [Symbol.dispose]() { return; }
        }
        """,
        """
        class Foo {
          /** @return {?} */
          [Symbol.iterator]() {}
          static [Symbol.dispose]() {}
        }
        """);
  }

  @Test
  public void testSymbolProperties_nonClass() {
    testSame(
        "/** @constructor */ function Foo() {} Foo.prototype[Symbol.iterator] = function(x,y,z)"
            + " {}");

    testSame("/** @constructor */ function Foo() {} /** @type {str} */ Foo[Symbol.iterator];");

    test("weirdDynamicThing()[Symbol.iterator] = function() {};", "");
  }

  @Test
  public void testComputedPropertyInferenceDoesntCrash() {
    test("const SomeMap = { [foo()]: 5 };", "const SomeMap = {};");

    test(
        "const SomeBagOfMethods = { /** @return {number} */ method() { return 5; } };",
        "const SomeBagOfMethods = { /** @return {number} */ method() {} };");

    test(
        "const SomeBagOfMethods = { /** @return {number} */ get x() { return 5; } };",
        "const SomeBagOfMethods = { /** @return {number} */ get x() {} };");

    test(
        "const RandomStuff = { [foo()]: 4, method() {}, 9.4: 'bar', set y(x) {} };",
        "const RandomStuff = { method() {}, /** @const {UnusableType} */ '9.4': 0, set y(x) {} };");
  }

  @Test
  public void testDescAnnotationCountsAsTyped() {
    test(
        """
        /** @desc Some description */
        var MSG_DESCRIPTION = goog.getMsg('Text');
        """,
        """
        /** @const {string} @desc Some description */
        var MSG_DESCRIPTION;
        """);

    test(
        """
        goog.module('a.b.c');

        /** @desc Some description */
        exports.MSG_DESCRIPTION = goog.getMsg('Text');
        """,
        """
        goog.module('a.b.c');

        /** @const {string} @desc Some description */
        exports.MSG_DESCRIPTION;
        """);
  }

  @Test
  public void testCommonJsModules() {
    testSame("const foo = require('./foo.js');");

    testSame(
        """
        const {Baz} = require('./baz.js');

        exports.Foo = class {
          /** @return {!Baz} */ getBaz() {}
        };
        """);

    test(
        """
        module.exports = class Foo {
          /** @return {number} */ get42() { return 40 + 2; }
        };
        """,
        """
        module.exports = class Foo {
          /** @return {number} */ get42() {}
        };
        """);

    testSame(
        """
        const {Bar, Baz} = require('./a/b/c.js');

        class Foo extends Bar {
          /** @return {!Baz} */ getBaz() {}
        }

        exports = {Foo};
        module.exports = {Foo};
        """);
  }

  @Test
  public void testEmptyFile() {
    test(srcs("const x = 42;", ""), expected("/** @const {number} */ var x;", ""));
  }

  @Test
  public void testEs5RecordWithCtorThisDefinitions() {
    test(
        "/** @record */ function Foo() {  /** @type {number} */ this.x = 42;}",
        "/** @record */ function Foo() {}  /** @type {number} */ Foo.prototype.x;");
  }

  @Test
  public void testEs5InterfaceWithCtorThisDefinitions() {
    test(
        "/** @interface */ function Foo() {  /** @type {number} */ this.x = 42;}",
        "/** @interface */ function Foo() {}  /** @type {number} */ Foo.prototype.x;");
  }

  @Test
  public void testPropertyAssignment() {
    // Test for b/123413988 which used to crash the ijs generator
    // These tests also exposes a bug tracked in b/124946590
    // TODO(b/124946590): Modify these tests when fixing the bug.
    test(
        """
        goog.module('fooo');
        var Foo;
        Foo = class {
            constructor() {
                this.boundOnMouseMove = null;
            }
        };
        """,
        """
        goog.module('fooo');
        /** @const @type {UnusableType} */ var Foo;
        """);

    test(
        """
        goog.module('fooo');
        var Foo;
        let Bar = Foo = class {
            constructor() {
                /** @type {null} */
                this.boundOnMouseMove = null;
            }
        };
        """,
        """
        goog.module('fooo');
        /** @const @type {UnusableType} */ var Foo;
        /** @const @type {UnusableType} */ var Bar
        """);
  }

  @Test
  public void testPolymerBehavior() {
    test(
        """
        /** @polymerBehavior */
        export const MyBehavior = {
          properties: {
            foo: String,
            /** @type {string|number} */
            bar: Number,
            /** @type {string|number} */
            baz: {
              type: String,
              value: function() {
                return "foo";
              },
              reflectToAttribute: true,
              observer: "bazChanged",
              readOnly: true
            }
          },
          observers: [
            "abc(foo)",
          ],
          /** @return {boolean} */
          abc() { return true; },
          /** @return {boolean} */
          xyz: function() { return false; }
        };
        /** @polymerBehavior */
        export const MyBehaviorAlias = MyBehavior;
        /** @polymerBehavior */
        export const MyBehaviorArray = [MyBehavior];
        /** @polymerBehavior */
        exports.MyBehaviorAlias = MyBehavior;
        /** @polymerBehavior */
        exports.MyBehaviorArray = [MyBehavior];
        /** @polymerBehavior */
        export let InvalidBehavior1 = "foo";
        /** @polymerBehavior */
        exports.InvalidBehavior2 = function() { return "foo" };
        export const NotABehavior = {
          properties: {
            foo: String
          }
        };
        """,
        """
        /** @polymerBehavior */
        export const MyBehavior = {
        // The "properties" configuration matters.
          properties: {
            foo: String,
        // @type annotations on the properties matter.
            /** @type {string|number} */
            bar: Number,
            /** @type {string|number} */
            baz: {
        // If the property definition is an object, only the "type" and "readOnly"
        // sub-properties matter.
              type: String,
              readOnly: true
            }
          },
        // The "observers" configuration doesn't matter.
          /** @const @type {UnusableType} */
          observers: 0,
        // Methods matter, but only their signatures.
          /** @return {boolean} */
          abc() {},
          /** @return {boolean} */
          xyz: function() {}
        };
        // Behaviors can also be aliased or combined into arrays, and the RHS values matter.
        /** @polymerBehavior */
        export const MyBehaviorAlias = MyBehavior;
        /** @polymerBehavior */
        export const MyBehaviorArray = [MyBehavior];
        /** @polymerBehavior */
        exports.MyBehaviorAlias = MyBehavior;
        /** @polymerBehavior */
        exports.MyBehaviorArray = [MyBehavior];
        // Not valid behavior types, can be simplified.
        /**
         * @const
         * @polymerBehavior
         * @type {UnusableType}
         */
        export var InvalidBehavior1
        /** @polymerBehavior */
        exports.InvalidBehavior2 = function() {};
        // There's no @polymerBehavior annotation here, so don't preserve "properties".
        export const NotABehavior = {
          /** @const @type {UnusableType} */
          properties: 0
        };
        """);
  }

  @Test
  public void testGoogModuleGet() {
    test(
        """
        goog.provide('a.b.c');
        goog.provide('a.b.c.d');
        goog.require('x.y.z');

        /** @const */
        a.b.c = {};
        /** @const */
        a.b.c.d = goog.module.get('x.y.z').d;
        """,
        """
        goog.provide('a.b.c');
        goog.provide('a.b.c.d');
        goog.require('x.y.z');

        /** @const */
        a.b.c = {};
        /** @const */
        a.b.c.d = goog.module.get('x.y.z').d;
        """);
  }

  @Test
  public void testRemovesClosureUnawareCodeAnnotationIfPresent() {
    test(
        """
        /** @fileoverview @closureUnaware */
        goog.module('a.b.c');
        exports.foo = 10;
        """,
        """
        /**@fileoverview */
        goog.module('a.b.c');
        /** @const @type {number} */
        exports.foo;
        """);
  }
}
