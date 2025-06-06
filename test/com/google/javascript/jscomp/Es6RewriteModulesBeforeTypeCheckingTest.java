/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link Es6RewriteModules} as it should work when run before type checking.
 *
 * <p>TODO(b/144593112): Remove this file when module rewriting is permanently moved after type
 * checking.
 */
@RunWith(JUnit4.class)
public final class Es6RewriteModulesBeforeTypeCheckingTest extends CompilerTestCase {
  private @Nullable ImmutableList<String> moduleRoots = null;

  private static final SourceFile other =
      SourceFile.fromCode(
          "other.js",
          """
          export default 0;
          export let name, x, a, b, c;
          export {x as class};
          export class Parent {}
          """);

  private static final SourceFile otherExpected =
      SourceFile.fromCode(
          "other.js",
          """
          var $jscompDefaultExport$$module$other = 0;
          let name$$module$other, x$$module$other, a$$module$other, b$$module$other,
            c$$module$other;
          class Parent$$module$other {}
          /** @const */ var module$other = {};
          /** @const */ module$other.Parent = Parent$$module$other;
          /** @const */ module$other.a = a$$module$other;
          /** @const */ module$other.b = b$$module$other;
          /** @const */ module$other.c = c$$module$other;
          /** @const */ module$other.class = x$$module$other;
          /** @const */ module$other.default = $jscompDefaultExport$$module$other;
          /** @const */ module$other.name = name$$module$other;
          /** @const */ module$other.x = x$$module$other;
          """);

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    enableCreateModuleMap();
    enableTypeCheck();
    enableRunTypeCheckAfterProcessing();
    enableTypeInfoValidation();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);

    if (moduleRoots != null) {
      options.setModuleRoots(moduleRoots);
    }

    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER)
          .process(externs, root);
      new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()).process(externs, root);
      new Es6RewriteModules(
              compiler,
              compiler.getModuleMetadataMap(),
              compiler.getModuleMap(),
              /* preprocessorSymbolTable= */ null,
              /* globalTypedScope= */ null)
          .process(externs, root);
    };
  }

  void testModules(String input, String expected) {
    test(
        srcs(other, SourceFile.fromCode("testcode", input)),
        expected(otherExpected, SourceFile.fromCode("testcode", expected)));
  }

  @Test
  public void testImport() {
    testModules(
        """
        import name from './other.js';
        use(name);
        """,
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules("import {a as name} from './other.js';", "/** @const */ var module$testcode = {};");

    testModules(
        """
        import x, {a as foo, b as bar} from './other.js';
        use(x);
        """,
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules(
        """
        import {default as name} from './other.js';
        use(name);
        """,
        "use($jscompDefaultExport$$module$other); /** @const */ var module$testcode = {};");

    testModules(
        """
        import {class as name} from './other.js';
        use(name);
        """,
        "use(x$$module$other); /** @const */ var module$testcode = {};");
  }

  @Test
  public void testImport_missing() {
    ModulesTestUtils.testModulesError(
        this, "import name from './does_not_exist';\n use(name);", ModuleLoader.LOAD_WARNING);

    ignoreWarnings(ModuleLoader.LOAD_WARNING);

    // These are different as a side effect of the way that the fake bindings are made. The first
    // makes a binding for a fake variable in the fake module. The second creates a fake binding
    // for the fake module. When "dne.name" is resolved, the module does not have key "name", so
    // it chooses to rewrite to "module$does_not_exist.name", thinking that this could've been a
    // reference to an export that doesn't exist.
    testModules(
        """
        import {name} from './does_not_exist';
        use(name);
        """,
        """
        use(name$$module$does_not_exist);
        /** @const */ var module$testcode = {};
        """);

    testModules(
        """
        import * as dne from './does_not_exist';
        use(dne.name);
        """,
        """
        use(module$does_not_exist.name);
        /** @const */ var module$testcode = {};
        """);
  }

  @Test
  public void testImportStar() {
    testModules(
        """
        import * as name from './other.js';
        use(name.a);
        """,
        "use(a$$module$other); /** @const */ var module$testcode = {};");
  }

  @Test
  public void testTypeNodeRewriting() {
    //
    test(
        srcs(
            SourceFile.fromCode(
                "other.js",
                """
                export default 0;
                export let name = 'George';
                export let a = class {};
                """),
            SourceFile.fromCode(
                "testcode",
                """
                import * as name from './other.js';
                /** @type {name.a} */ var x;
                """)),
        expected(
            SourceFile.fromCode(
                "other.js",
                """
                var $jscompDefaultExport$$module$other = 0;
                let name$$module$other = 'George';
                let a$$module$other = class {};
                /** @const */ var module$other = {};
                /** @const */ module$other.a = a$$module$other;
                /** @const */ module$other.default = $jscompDefaultExport$$module$other;
                /** @const */ module$other.name = name$$module$other;
                """),
            SourceFile.fromCode(
                "testcode",
                """
                /** @type {a$$module$other} */ var x$$module$testcode;
                /** @const */ var module$testcode = {};
                """)));
  }

  @Test
  public void testExport() {
    testModules(
        "export var a = 1, b = 2;",
        """
        var a$$module$testcode = 1, b$$module$testcode = 2;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$testcode;
        """);

    testModules(
        "export var a;\nexport var b;",
        """
        var a$$module$testcode; var b$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$testcode;
        """);

    testModules(
        "export function f() {};",
        """
        function f$$module$testcode() {}
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.f = f$$module$testcode;
        """);

    testModules(
        "export function f() {};\nfunction g() { f(); }",
        """
        function f$$module$testcode() {}
        function g$$module$testcode() { f$$module$testcode(); }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.f = f$$module$testcode;
        """);

    testModules(
        """
        export function MyClass() {};
        MyClass.prototype.foo = function() {};
        """,
        """
        function MyClass$$module$testcode() {}
        MyClass$$module$testcode.prototype.foo = function() {};
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.MyClass = MyClass$$module$testcode;
        """);

    testModules(
        "var f = 1;\nvar b = 2;\nexport {f as foo, b as bar};",
        """
        var f$$module$testcode = 1;
        var b$$module$testcode = 2;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.bar = b$$module$testcode;
        /** @const */ module$testcode.foo = f$$module$testcode;
        """);

    testModules(
        "var f = 1;\nexport {f as default};",
        """
        var f$$module$testcode = 1;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = f$$module$testcode;
        """);

    testModules(
        "var f = 1;\nexport {f as class};",
        """
        var f$$module$testcode = 1;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.class = f$$module$testcode;
        """);
  }

  @Test
  public void testModulesInExterns() {
    testError(
        srcs(
            SourceFile.fromCode(
                "externsMod.js",
                """
                /** @fileoverview @externs */
                export let /** !number */ externalName;
                """)),
        error(TranspilationUtil.CANNOT_CONVERT_YET));
  }

  @Test
  public void testModulesInTypeSummary() {
    allowExternsChanges();
    test(
        srcs(
            SourceFile.fromCode(
                "mod1.js",
                """
                /** @fileoverview @typeSummary */
                export let /** !number */ externalName;
                """),
            SourceFile.fromCode(
                "mod2.js",
                """
                import {externalName as localName} from './mod1.js'
                alert(localName);
                """)),
        expected(
            SourceFile.fromCode(
                "mod2.js",
                """
                alert(externalName$$module$mod1);
                /** @const */ var module$mod2 = {};
                """)));
  }

  @Test
  public void testMutableExport() {
    testModules(
        """
        export var a = 1, b = 2;
        function f() {
          a++;
          b++
        }
        """,
        """
        var a$$module$testcode = 1, b$$module$testcode = 2;
        function f$$module$testcode() {
          a$$module$testcode++;
          b$$module$testcode++
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get a() { return a$$module$testcode; },
          /** @return {?} */ get b() { return b$$module$testcode; },
        };
        """);

    testModules(
        """
        var a = 1, b = 2; export {a as A, b as B};
        const f = () => {
          a++;
          b++
        };
        """,
        """
        var a$$module$testcode = 1, b$$module$testcode = 2;
        const f$$module$testcode = () => {
          a$$module$testcode++;
          b$$module$testcode++
        };
        /** @const */ var module$testcode = {
          /** @return {?} */ get A() { return a$$module$testcode; },
          /** @return {?} */ get B() { return b$$module$testcode; },
        };
        """);

    testModules(
        """
        export function f() {};
        function g() {
          f = function() {};
        }
        """,
        """
        function f$$module$testcode() {}
        function g$$module$testcode() {
          f$$module$testcode = function() {};
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get f() { return f$$module$testcode; },
        };
        """);

    testModules(
        """
        export default function f() {};
        function g() {
          f = function() {};
        }
        """,
        """
        function f$$module$testcode() {}
        function g$$module$testcode() {
          f$$module$testcode = function() {};
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get default() { return f$$module$testcode; },
        };
        """);

    testModules(
        """
        export class C {};
        function g() {
          C = class {};
        }
        """,
        """
        class C$$module$testcode {}
        function g$$module$testcode() {
          C$$module$testcode = class {};
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get C() { return C$$module$testcode; },
        };
        """);

    testModules(
        """
        export default class C {};
        function g() {
          C = class {};
        }
        """,
        """
        class C$$module$testcode {}
        function g$$module$testcode() {
          C$$module$testcode = class {};
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get default() { return C$$module$testcode; },
        };
        """);

    testModules(
        """
        export var IN, OF;
        function f() {
          for (IN in {});
          for (OF of []);
        }
        """,
        """
        var IN$$module$testcode, OF$$module$testcode;
        function f$$module$testcode() {
          for (IN$$module$testcode in {});
          for (OF$$module$testcode of []);
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get IN() { return IN$$module$testcode; },
          /** @return {?} */ get OF() { return OF$$module$testcode; },
        };
        """);

    testModules(
        """
        export var ARRAY, OBJ, UNCHANGED;
        function f() {
          ({OBJ} = {OBJ: {}});
          [ARRAY] = [];
          var x = {UNCHANGED: 0};
        }
        """,
        """
        var ARRAY$$module$testcode, OBJ$$module$testcode, UNCHANGED$$module$testcode;
        function f$$module$testcode() {
          ({OBJ:OBJ$$module$testcode} = {OBJ: {}});
          [ARRAY$$module$testcode] = [];
          var x = {UNCHANGED: 0};
        }
        /** @const */ var module$testcode = {
          /** @return {?} */ get ARRAY() { return ARRAY$$module$testcode; },
          /** @return {?} */ get OBJ() { return OBJ$$module$testcode; },
        };
        /** @const */ module$testcode.UNCHANGED = UNCHANGED$$module$testcode
        """);
  }

  @Test
  public void testConstClassExportIsConstant() {
    testModules(
        "export const Class = class {}",
        """
        const Class$$module$testcode = class {}
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Class = Class$$module$testcode;
        """);
  }

  @Test
  public void testTopLevelMutationIsNotMutable() {
    testModules(
        """
        export var a = 1, b = 2;
        a++;
        b++
        """,
        """
        var a$$module$testcode = 1, b$$module$testcode = 2;
        a$$module$testcode++;
        b$$module$testcode++
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$testcode;
        """);

    testModules(
        """
        var a = 1, b = 2; export {a as A, b as B};
        if (change) {
          a++;
          b++
        }
        """,
        """
        var a$$module$testcode = 1, b$$module$testcode = 2;
        if (change) {
          a$$module$testcode++;
          b$$module$testcode++
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.A = a$$module$testcode;
        /** @const */ module$testcode.B = b$$module$testcode;
        """);

    testModules(
        """
        export function f() {};
        if (change) {
          f = function() {};
        }
        """,
        """
        function f$$module$testcode() {}
        if (change) {
          f$$module$testcode = function() {};
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.f = f$$module$testcode;
        """);

    testModules(
        """
        export default function f() {};
        try { f = function() {}; } catch (e) { f = function() {}; }
        """,
        """
        function f$$module$testcode() {}
        try { f$$module$testcode = function() {}; }
        catch (e) { f$$module$testcode = function() {}; }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = f$$module$testcode;
        """);

    testModules(
        """
        export class C {};
        if (change) {
          C = class {};
        }
        """,
        """
        class C$$module$testcode {}
        if (change) {
          C$$module$testcode = class {};
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.C = C$$module$testcode;
        """);

    testModules(
        """
        export default class C {};
        {
          C = class {};
        }
        """,
        """
        class C$$module$testcode {}
        {
          C$$module$testcode = class {};
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = C$$module$testcode;
        """);
  }

  @Test
  public void testExportWithJsDoc() {
    testModules(
        "/** @constructor */\nexport function F() { return '';}",
        """
        /** @constructor */
        function F$$module$testcode() { return ''; }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.F = F$$module$testcode;
        """);

    testModules(
        "/** @return {string} */\nexport function f() { return '';}",
        """
        /** @return {string} */
        function f$$module$testcode() { return ''; }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.f = f$$module$testcode;
        """);

    testModules(
        "/** @return {string} */\nexport var f = function() { return '';}",
        """
        /** @return {string} */
        var f$$module$testcode = function() { return ''; }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.f = f$$module$testcode;
        """);

    testModules(
        "/** @type {number} */\nexport var x = 3",
        """
        /** @type {number} */
        var x$$module$testcode = 3;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.x = x$$module$testcode;
        """);
  }

  @Test
  public void testImportAndExport() {
    testModules(
        """
        import {name as n} from './other.js';
        use(n);
        export {n as name};
        """,
        """
        use(name$$module$other);
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.name = name$$module$other;
        """);
  }

  @Test
  public void testExportFrom() {
    test(
        srcs(
            other,
            SourceFile.fromCode(
                "testcode",
                """
                export {name} from './other.js';
                export {default} from './other.js';
                export {class} from './other.js';
                """)),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                """
                /** @const */ var module$testcode = {};
                /** @const */ module$testcode.class = x$$module$other;
                /** @const */ module$testcode.default = $jscompDefaultExport$$module$other;
                /** @const */ module$testcode.name = name$$module$other;
                """)));

    test(
        srcs(other, SourceFile.fromCode("testcode", "export {a, b as c, x} from './other.js';")),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                """
                /** @const */ var module$testcode = {}
                /** @const */ module$testcode.a = a$$module$other;
                /** @const */ module$testcode.c = b$$module$other;
                /** @const */ module$testcode.x = x$$module$other;
                """)));

    test(
        srcs(other, SourceFile.fromCode("testcode", "export {a as b, b as a} from './other.js';")),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                """
                /** @const */ var module$testcode = {}
                /** @const */ module$testcode.a = b$$module$other;
                /** @const */ module$testcode.b = a$$module$other;
                """)));

    test(
        srcs(
            other,
            SourceFile.fromCode(
                "testcode",
                """
                export {default as a} from './other.js';
                export {a as a2, default as b} from './other.js';
                export {class as switch} from './other.js';
                """)),
        expected(
            otherExpected,
            SourceFile.fromCode(
                "testcode",
                """
                /** @const */ var module$testcode = {}
                /** @const */ module$testcode.a = $jscompDefaultExport$$module$other;
                /** @const */ module$testcode.a2 = a$$module$other;
                /** @const */ module$testcode.b = $jscompDefaultExport$$module$other;
                /** @const */ module$testcode.switch = x$$module$other;
                """)));
  }

  @Test
  public void testExportDefault() {
    testModules(
        "export default 'someString';",
        """
        var $jscompDefaultExport$$module$testcode = 'someString';
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;
        """);

    testModules(
        "var x = 5;\nexport default x;",
        """
        var x$$module$testcode = 5;
        var $jscompDefaultExport$$module$testcode = x$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;
        """);

    testModules(
        "export default function f(){};\n var x = f();",
        """
        function f$$module$testcode() {}
        var x$$module$testcode = f$$module$testcode();
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = f$$module$testcode;
        """);

    testModules(
        "export default class Foo {};\n var x = new Foo;",
        """
        class Foo$$module$testcode {}
        var x$$module$testcode = new Foo$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = Foo$$module$testcode;
        """);
  }

  @Test
  public void testExportDefault_anonymous() {
    testModules(
        "export default class {};",
        """
        var $jscompDefaultExport$$module$testcode = class {};
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;
        """);

    testModules(
        "export default function() {}",
        """
        var $jscompDefaultExport$$module$testcode = function() {}
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.default = $jscompDefaultExport$$module$testcode;
        """);
  }

  @Test
  public void testExportDestructureDeclaration() {
    testModules(
        "export let {a, c:b} = obj;",
        """
        let {a:a$$module$testcode, c:b$$module$testcode} = obj;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$testcode;
        """);

    testModules(
        "export let [a, b] = obj;",
        """
        let [a$$module$testcode, b$$module$testcode] = obj;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$testcode;
        """);

    testModules(
        "export let {a, b:[c,d]} = obj;",
        """
        let {a:a$$module$testcode, b:[c$$module$testcode, d$$module$testcode]} = obj;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.c = c$$module$testcode;
        /** @const */ module$testcode.d = d$$module$testcode;
        """);
  }

  @Test
  public void testExtendImportedClass() {
    testModules(
        """
        import {Parent} from './other.js';
        class Child extends Parent {
          /** @param {Parent} parent */
          useParent(parent) {}
        }
        """,
        """
        class Child$$module$testcode extends Parent$$module$other {
          /** @param {Parent$$module$other} parent */
          useParent(parent) {}
        }
        /** @const */ var module$testcode = {};
        """);

    testModules(
        """
        import {Parent} from './other.js';
        export class Child extends Parent {
          /** @param {Parent} parent */
          useParent(parent) {}
        }
        """,
        """
        class Child$$module$testcode extends Parent$$module$other {
          /** @param {Parent$$module$other} parent */
          useParent(parent) {}
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Child = Child$$module$testcode;
        """);
  }

  @Test
  public void testFixTypeNode() {
    testModules(
        """
        export class Child {
          /** @param {Child} child */
          useChild(child) {}
        }
        """,
        """
        class Child$$module$testcode {
          /** @param {Child$$module$testcode} child */
          useChild(child) {}
        }
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Child = Child$$module$testcode;
        """);

    testModules(
        """
        export class Child {
          /** @param {Child.Foo.Bar.Baz} baz */
          useBaz(baz) {}
        }

        Child.Foo = class {};

        Child.Foo.Bar = class {};

        Child.Foo.Bar.Baz = class {};
        """,
        """
        class Child$$module$testcode {
          /** @param {Child$$module$testcode.Foo.Bar.Baz} baz */
          useBaz(baz) {}
        }
        Child$$module$testcode.Foo=class{};
        Child$$module$testcode.Foo.Bar=class{};
        Child$$module$testcode.Foo.Bar.Baz=class{};
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Child = Child$$module$testcode;
        """);
  }

  @Test
  public void testRenameTypedef() {
    testModules(
        """
        import './other.js';
        /** @typedef {string|!Object} */
        export var UnionType;
        """,
        """
        /** @typedef {string|!Object} */
        var UnionType$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @typedef {UnionType$$module$testcode} */
        module$testcode.UnionType;
        """);
  }

  @Test
  public void testNoInnerChange() {
    testModules(
        """
        var Foo = (function () {
            /**  @param bar */
            function Foo(bar) {}
            return Foo;
        }());
        export { Foo };
        """,
        """
        var Foo$$module$testcode = function() {
            /**  @param bar */
            function Foo(bar) {}
            return Foo;
        }();
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Foo = Foo$$module$testcode;
        """);
  }

  @Test
  public void testRenameImportedReference() {
    testModules(
        """
        import {a} from './other.js';
        import {b as bar} from './other.js';
        a();
        function g() {
          a();
          bar++;
          function h() {
            var a = 3;
            { let a = 4; }
          }
        }
        """,
        """
        a$$module$other();
        function g$$module$testcode() {
          a$$module$other();
          b$$module$other++;
          function h() {
            var a = 3;
            { let a = 4; }
          }
        }
        /** @const */ var module$testcode = {};
        """);
  }

  @Test
  public void testObjectDestructuringAndObjLitShorthand() {
    testModules(
        """
        import {c} from './other.js';
        const foo = 1;
        const {a, b} = c({foo});
        use(a, b);
        """,
        """
        const foo$$module$testcode = 1;
        const {
          a: a$$module$testcode,
          b: b$$module$testcode,
        } = c$$module$other({foo: foo$$module$testcode});
        use(a$$module$testcode, b$$module$testcode);
        /** @const */ var module$testcode = {};
        """);
  }

  @Test
  public void testObjectDestructuringAndObjLitShorthandWithDefaultValue() {
    testModules(
        """
        import {c} from './other.js';
        const foo = 1;
        const {a = 'A', b = 'B'} = c({foo});
        use(a, b);
        """,
        """
        const foo$$module$testcode = 1;
        const {
          a: a$$module$testcode = 'A',
          b: b$$module$testcode = 'B',
        } = c$$module$other({foo: foo$$module$testcode});
        use(a$$module$testcode, b$$module$testcode);
        /** @const */ var module$testcode = {};
        """);
  }

  @Test
  public void testImportWithoutReferences() {
    testModules(
        "import './other.js';", //
        "/** @const */ var module$testcode = {};");
  }

  @Test
  public void testUselessUseStrict() {
    ModulesTestUtils.testModulesError(
        this,
        """
        'use strict';
        export default undefined;
        """,
        ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }

  @Test
  public void testUseStrict_noWarning() {
    testSame(
        """
        'use strict';
        var x;
        """);
  }

  @Test
  public void testAbsoluteImportsWithModuleRoots() {
    moduleRoots = ImmutableList.of("/base");
    test(
        srcs(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                "import * as foo from '/mod/name.js';")),
        expected(
            SourceFile.fromCode(Compiler.joinPathParts("base", "mod", "name.js"), ""),
            SourceFile.fromCode(
                Compiler.joinPathParts("base", "test", "sub.js"),
                "/** @const */ var module$test$sub = {};")));
  }

  @Test
  public void testUseImportInEs6ObjectLiteralShorthand() {
    testModules(
        """
        import {b} from './other.js';
        var bar = {a: 1, b};
        """,
        """
        var bar$$module$testcode={a: 1, b: b$$module$other};
        /** @const */ var module$testcode = {};
        """);

    testModules(
        """
        import {a as foo} from './other.js';
        var bar = {a: 1, foo};
        """,
        """
        var bar$$module$testcode={a: 1, foo: a$$module$other};
        /** @const */ var module$testcode = {};
        """);

    testModules(
        """
        import f from './other.js';
        var bar = {a: 1, f};
        """,
        """
        var bar$$module$testcode={a: 1, f: $jscompDefaultExport$$module$other};
        /** @const */ var module$testcode = {};
        """);

    testModules(
        "import * as f from './other.js';\nvar bar = {a: 1, f};",
        """
        var bar$$module$testcode={a: 1, f: module$other};
        /** @const */ var module$testcode = {};
        """);
  }

  @Test
  public void testImportAliasInTypeNode() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode(
                "b.js",
                """
                import {A as B} from './a.js';
                const /** !B */ b = new B();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                class A$$module$a {}
                /** @const */ var module$a = {};
                /** @const */ module$a.A = A$$module$a;
                """),
            SourceFile.fromCode(
                "b.js",
                """
                const /** !A$$module$a*/ b$$module$b = new A$$module$a();
                /** @const */ var module$b = {};
                """)));
  }

  @Test
  public void testExportStar() {
    testModules(
        "export * from './other.js';",
        """
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Parent = Parent$$module$other;
        /** @const */ module$testcode.a = a$$module$other;
        /** @const */ module$testcode.b = b$$module$other;
        /** @const */ module$testcode.c = c$$module$other;
        /** @const */ module$testcode.class = x$$module$other;
        // no default
        /** @const */ module$testcode.name = name$$module$other;
        /** @const */ module$testcode.x = x$$module$other;
        """);
  }

  @Test
  public void testExportStarWithLocalExport() {
    testModules(
        """
        export * from './other.js';
        export let baz, zed;
        """,
        """
        let baz$$module$testcode, zed$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Parent = Parent$$module$other;
        /** @const */ module$testcode.a = a$$module$other;
        /** @const */ module$testcode.b = b$$module$other;
        /** @const */ module$testcode.baz = baz$$module$testcode;
        /** @const */ module$testcode.c = c$$module$other;
        /** @const */ module$testcode.class = x$$module$other;
        /** @const */ module$testcode.name = name$$module$other;
        /** @const */ module$testcode.x = x$$module$other;
        /** @const */ module$testcode.zed = zed$$module$testcode;
        """);
  }

  @Test
  public void testExportStarWithLocalExportOverride() {
    testModules(
        """
        export * from './other.js';
        export let a, zed;
        """,
        """
        let a$$module$testcode, zed$$module$testcode;
        /** @const */ var module$testcode = {};
        /** @const */ module$testcode.Parent = Parent$$module$other;
        /** @const */ module$testcode.a = a$$module$testcode;
        /** @const */ module$testcode.b = b$$module$other;
        /** @const */ module$testcode.c = c$$module$other;
        /** @const */ module$testcode.class = x$$module$other;
        /** @const */ module$testcode.name = name$$module$other;
        /** @const */ module$testcode.x = x$$module$other;
        /** @const */ module$testcode.zed = zed$$module$testcode;
        """);
  }

  @Test
  public void testTransitiveImport() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode("b.js", "export {A} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                """
                import {A} from './b.js';
                let /** !A */ a = new A();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                class A$$module$a {}
                /** @const */ var module$a = {};
                /** @const */ module$a.A = A$$module$a;
                """),
            SourceFile.fromCode(
                "b.js",
                """
                /** @const */ var module$b = {};
                /** @const */ module$b.A = A$$module$a;
                """),
            SourceFile.fromCode(
                "c.js",
                """
                let /** !A$$module$a*/ a$$module$c = new A$$module$a();
                /** @const */ var module$c = {};
                """)));
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode("b.js", "export {A as B} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                """
                import {B as C} from './b.js';
                let /** !C */ a = new C();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                class A$$module$a {}
                /** @const */ var module$a = {};
                /** @const */ module$a.A = A$$module$a;
                """),
            SourceFile.fromCode(
                "b.js",
                """
                /** @const */ var module$b = {};
                /** @const */ module$b.B = A$$module$a;
                """),
            SourceFile.fromCode(
                "c.js",
                """
                let /** !A$$module$a*/ a$$module$c = new A$$module$a();
                /** @const */ var module$c = {};
                """)));
  }

  @Test
  public void testMutableTransitiveImport() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export let A = class {}; () => (A = class {});"),
            SourceFile.fromCode("b.js", "export {A} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                """
                import {A} from './b.js';
                let /** !A */ a = new A();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                let A$$module$a = class {};
                ()=>A$$module$a = class {};
                /** @const */ var module$a = {
                  /** @return {?} */ get A() { return A$$module$a; },
                };
                """),
            SourceFile.fromCode(
                "b.js",
                """
                /** @const */ var module$b = {
                  /** @return {?} */ get A() { return A$$module$a; },
                };
                """),
            SourceFile.fromCode(
                "c.js",
                """
                let /** !A$$module$a*/ a$$module$c = new A$$module$a();
                /** @const */ var module$c = {};
                """)));
    test(
        srcs(
            SourceFile.fromCode("a.js", "export let A = class {}; () => (A = class {});"),
            SourceFile.fromCode("b.js", "export {A as B} from './a.js';"),
            SourceFile.fromCode(
                "c.js",
                """
                import {B as C} from './b.js';
                let /** !C */ a = new C();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                let A$$module$a = class {};
                ()=>A$$module$a = class {};
                /** @const */ var module$a = {
                  /** @return {?} */ get A() { return A$$module$a; },
                };
                """),
            SourceFile.fromCode(
                "b.js",
                """
                /** @const */ var module$b = {
                  /** @return {?} */ get B() { return A$$module$a; },
                };
                """),
            SourceFile.fromCode(
                "c.js",
                """
                let /** !A$$module$a*/ a$$module$c = new A$$module$a();
                /** @const */ var module$c = {};
                """)));
  }

  @Test
  public void testRewriteGetPropsWhileModuleReference() {
    test(
        srcs(
            SourceFile.fromCode("a.js", "export class A {}"),
            SourceFile.fromCode(
                "b.js",
                """
                import * as a from './a.js';
                export {a};
                """),
            SourceFile.fromCode(
                "c.js",
                """
                import * as b from './b.js';
                let /** !b.a.A */ a = new b.a.A();
                """)),
        expected(
            SourceFile.fromCode(
                "a.js",
                """
                class A$$module$a {}
                /** @const */ var module$a = {};
                /** @const */ module$a.A = A$$module$a;
                """),
            SourceFile.fromCode(
                "b.js",
                """
                /** @const */ var module$b = {};
                /** @const */ module$b.a = module$a;
                """),
            SourceFile.fromCode(
                "c.js",
                """
                let /** !A$$module$a*/ a$$module$c = new A$$module$a();
                /** @const */ var module$c = {};
                """)));
  }

  @Test
  public void testRewritePropsWhenNotModuleReference() {
    //
    test(
        srcs(
            SourceFile.fromCode("other.js", "export let name = {}, a = { Type: class {} };"),
            SourceFile.fromCode(
                "testcode",
                """
                import * as name from './other.js';
                let /** !name.a.Type */ t = new name.a.Type();
                """)),
        expected(
            SourceFile.fromCode(
                "other.js",
                """
                let name$$module$other = {}, a$$module$other = { Type: class {} };
                /** @const */ var module$other = {};
                /** @const */ module$other.a = a$$module$other;
                /** @const */ module$other.name = name$$module$other;
                """),
            SourceFile.fromCode(
                "testcode",
                """
                let /** !a$$module$other.Type */ t$$module$testcode =
                   new a$$module$other.Type();
                /** @const */ var module$testcode = {};
                """)));
  }

  @Test
  public void testExportsNotImplicitlyLocallyDeclared() {
    test(
        externs("var exports;"),
        srcs("typeof exports; export {};"),
        // Regression test; compiler used to rewrite `exports` to `exports$$module$testcode`.
        expected("typeof exports; /** @const */ var module$testcode = {};"));
  }

  @Test
  public void testImportMeta() {

    testError("import.meta", TranspilationUtil.CANNOT_CONVERT);
  }
}
