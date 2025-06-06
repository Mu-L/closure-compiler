/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GenerateExports;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.JSChunk;
import com.google.javascript.jscomp.ModuleIdentifier;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for Closure primitives on the goog.* namespace that have special handling in
 * the compiler.
 */
@RunWith(JUnit4.class)
public final class ClosureIntegrationTest extends IntegrationTestCase {
  private static final String CLOSURE_BOILERPLATE_UNCOMPILED =
      "/** @define {boolean} */ var COMPILED = false;";

  private static final String CLOSURE_BOILERPLATE_COMPILED =
      "/** @define {boolean} */ var COMPILED = true;";

  private static final String CLOSURE_COLLAPSED = "var COMPILED = true;";

  @Test
  public void testReferencingMangledModuleNames_rewriteModulesBeforeTypechecking() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    compile(
        options,
        new String[] {
          """
          goog.module('foo.bar');
          class Inner {}
          class Outer extends Inner {}
          exports.Outer = Outer;
          """,
          """
          /** @type {!module$exports$foo$bar} */ let err1;
          /** @type {!module$contents$foo$bar_Inner} */ let err2;
          """
        });
    assertThat(lastCompiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            "Bad type annotation. Unknown type UnrecognizedType_module$exports$foo$bar",
            "Bad type annotation. Unknown type UnrecognizedType_module$contents$foo$bar_Inner");
  }

  @Test
  public void testReferencingMangledModuleNames_rewriteModulesAfterTypechecking() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);
    compile(
        options,
        new String[] {
          """
          goog.module('foo.bar');
          class Inner {}
          class Outer extends Inner {}
          exports.Outer = Outer;
          """,
          """
          /** @type {!module$exports$foo$bar} */ let err1;
          /** @type {!module$contents$foo$bar_Inner} */ let err2;
          """
        });
    assertThat(lastCompiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            "Bad type annotation. Unknown type UnrecognizedType_module$exports$foo$bar",
            "Bad type annotation. Unknown type UnrecognizedType_module$contents$foo$bar_Inner");
  }

  @Test
  public void testFunctionDeclarationInGoogScope_usingGoogModuleGetType() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    compile(
        options,
        new String[] {
          """
          goog.module('foo.bar.Types');
          exports.Klazz = class {};
          """,
          """
          goog.requireType('foo.bar.Types');
          goog.scope(function() {
          const fooBarTypes = goog.module.get('foo.bar.Types');
          /** @param {!fooBarTypes.Klazz} k */
          function f(k) {}
          f(null); // expect a type error here
          });
          """
        });

    assertThat(lastCompiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            """
            actual parameter 1 of $jscomp$scope$98477071$0$f does not match formal parameter
            found   : null
            required: foo.bar.Types.Klazz\
            """);
    assertThat(lastCompiler.getErrors()).isEmpty();
  }

  @Test
  public void testEsModuleInterop_esModuleUsingGoogRequireType() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    compile(
        options,
        new String[] {
          """
          goog.module('foo.bar.Types');
          exports.Klazz = class {};
          """,
          """
          const {Klazz} = goog.requireType('foo.bar.Types');
          /** @param {!Klazz} k */
          export function f(k) {}
          f(null);
          """
        });

    assertThat(lastCompiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            """
            actual parameter 1 of f does not match formal parameter
            found   : null
            required: foo.bar.Types.Klazz\
            """);
    assertThat(lastCompiler.getErrors()).isEmpty();
  }

  @Test
  public void testProcessDefinesInModule() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDefineToBooleanLiteral("USE", false);
    test(
        options,
        """
        goog.module('X');
        /** @define {boolean} */
        const USE = goog.define('USE', false);
        /** @const {boolean} */
        exports.USE = USE;
        """,
        "var module$exports$X={};module$exports$X.USE=false");
  }

  @Test
  public void testProcessDefinesInModuleWithDifferentDefineName() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setDefineToBooleanLiteral("MY_USE", false);
    test(
        options,
        """
        goog.module('X');
        /** @define {boolean} */
        const USE = goog.define('MY_USE', false);
        /** @const {boolean} */
        exports.USE = USE;
        """,
        "var module$exports$X={};module$exports$X.USE=false");
  }

  @Test
  public void testStaticMemberClass() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    testNoWarnings(
        options,
        new String[] {
          """
          /** @fileoverview @typeSummary */
          goog.loadModule(function(exports) {
            "use strict";
            goog.module("foo.Foo");
            goog.module.declareLegacyNamespace();
            class Foo {
              /**
               * @param {!Foo.Something} something
               */
               constructor(something) {
               }
            }
            /** @private @const @type {!Foo.Something} */ Foo.prototype.something_;

          // We're testing to be sure that the reference to Foo.Something
          // in JSDoc below resolves correctly even after module rewriting.
            Foo.Something = class {
            };
            exports = Foo;
            return exports;
          });
          """,
          """
          goog.module('b4d');

          const Foo = goog.require('foo.Foo');

          /**
           * @param {!Foo.Something} something
           */
          function foo(something) {
            console.log(something);
          }
          """,
        });
  }

  @Test
  public void testBug1949424() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    test(
        options,
        CLOSURE_BOILERPLATE_UNCOMPILED + "goog.provide('FOO'); FOO.bar = 3;",
        CLOSURE_COLLAPSED + "var FOO$bar = 3;");
  }

  @Test
  public void testBug1949424_v2() {
    CompilerOptions options = createCompilerOptions();
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    test(
        options,
        CLOSURE_BOILERPLATE_UNCOMPILED
            + """
            goog.provide('FOO.BAR');
            FOO.BAR = 3;
            """,
        CLOSURE_COLLAPSED + "var FOO$BAR = 3;");
  }

  @Test
  public void testBug2410122() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(true);
    options.setClosurePass(true);
    test(
        options,
        """
        function F() {}
        /** @export */ function G() { G.base(this, 'constructor'); }
        goog.inherits(G, F);
        """,
        """
        function F() {}
        function G() { F.call(this); }
        goog.inherits(G, F); goog.exportSymbol('G', G);
        """);
  }

  @Test
  public void testUnresolvedDefine() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    DiagnosticGroup[] warnings = {
      DiagnosticGroups.INVALID_DEFINES,
      DiagnosticGroups.INVALID_DEFINES,
      DiagnosticGroups.CHECK_TYPES
    };
    String[] input = {
      CLOSURE_BOILERPLATE_UNCOMPILED
          + """
          goog.provide('foo.bar');
          /** @define {foo.bar} */ foo.bar = {};
          """,
    };
    String[] output = {
      "",
      CLOSURE_BOILERPLATE_UNCOMPILED
          + """
          goog.provide('foo.bar');
          /** @define {foo.bar} */ foo.bar = {};
          """,
    };
    test(options, input, output, warnings);
  }

  @Test
  public void testDisableModuleRewriting() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setEnableModuleRewriting(false);
    options.setCheckTypes(true);

    test(
        options,
        """
        goog.module('foo.Outer');
        /** @constructor */ function Outer() {}
        exports = Outer;
        """,
        """
        goog.module('foo.Outer');
        function Outer(){}
        exports = Outer;
        """);
  }

  @Test
  public void testDisableModuleRewriting_doesntCrashWhenFirstInputIsModule_andGoogScopeUsed() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setEnableModuleRewriting(false);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    testNoWarnings(
        options,
        new String[] {
          "goog.module('foo.bar');",
          """
          goog.scope(function() {;
            /** @constructor */ function Outer() {}
          });
          """
        });
  }

  @Test
  public void testDisableModuleRewriting_doesntCrashWhenFirstInputIsModule_andPolyfillIsInjected() {
    CompilerOptions options = createCompilerOptions();
    options.setEnableModuleRewriting(false);
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setForceLibraryInjection(ImmutableList.of("es6/string/startswith"));

    testNoWarnings(options, "goog.module('foo.bar');");
  }

  @Test
  public void testTypecheckNativeModulesDoesntCrash_givenTemplatizedTypedefOfUnionType() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);
    options.setCheckTypes(true);
    test(
        options,
        new String[] {
          """
          goog.module('a');
          /** @interface */ class Foo {}
          /** @interface */ class Bar {}
          /** @typedef {(!Foo<?>|!Bar<?>)} */
          exports.TypeDef;
          """,
          """
          goog.module('b');
          const a = goog.requireType('a');
          const /** null */ n = /** @type {!a.TypeDef<?>} */ (0);
          """
        },
        null,
        // Just making sure that typechecking ran and didn't crash.  It would be reasonable
        // for there also to be other type errors in this code before the final null assignment.
        new DiagnosticGroup[] {
          DiagnosticGroups.CHECK_TYPES,
          DiagnosticGroups.CHECK_TYPES,
          DiagnosticGroups.CHECK_TYPES,
          DiagnosticGroups.CHECK_TYPES
        });
  }

  @Test
  public void testWeakSymbols_arentInlinedIntoStrongCode() {
    SourceFile extern =
        SourceFile.fromCode(
            "extern.js",
            """
            /** @fileoverview @externs */

            function alert(x) {}
            """);

    SourceFile aa =
        SourceFile.fromCode(
            "A.js",
            """
            goog.module('a.A');
            goog.module.declareLegacyNamespace();

            class A { };

            exports = A;
            """,
            SourceKind.WEAK);

    SourceFile ab =
        SourceFile.fromCode(
            "B.js",
            """
            goog.module('a.B');
            goog.module.declareLegacyNamespace();

            class B { };

            exports = B;
            """,
            SourceKind.STRONG);

    SourceFile entryPoint =
        SourceFile.fromCode(
            "C.js",
            """
            goog.module('a.C');

            const A = goog.requireType('a.A');
            const B = goog.require('a.B');

            alert(new B());
            // Note how `a` is declared by any strong legacy module rooted on "a" (`a.B`).
            alert(new a.A());
            """,
            SourceKind.STRONG);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("a.C"))));

    Compiler compiler = new Compiler();
    compiler.compile(ImmutableList.of(extern), ImmutableList.of(entryPoint, aa, ab), options);

    assertThat(compiler.toSource())
        .isEqualTo(
            """
            var a={};\
            class module$contents$a$B_B{}\
            a.B=module$contents$a$B_B;\
            \
            var module$exports$a$C={};\
            alert(new module$contents$a$B_B);\
            alert(new a.A);\
            """);
  }

  @Test
  public void testPreservedForwardDeclare() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setClosurePass(true);
    options.setPreserveClosurePrimitives(true);

    compile(
        options,
        new String[] {
          "goog.module('fwd.declared.Type'); exports = class {}",
          """
          goog.module('a.b.c');
          const Type = goog.forwardDeclare('fwd.declared.Type');

          /** @type {!fwd.declared.Type} */
          var y;
          """,
        });
  }

  @Test
  public void testForwardDeclaredTypeInTemplate() {
    CompilerOptions options = createCompilerOptions();
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setClosurePass(true);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);

    test(
        options,
        """
        goog.forwardDeclare('fwd.declared.Type');

        /** @type {!fwd.declared.Type<string>} */
        let x;

        /** @type {!fwd.declared.Type<string, number>} */
        let y;
        """,
        "var x;var y");
  }

  @Test
  public void testClosurePassOff() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(false);
    testSame(options, "goog.require('foo');");
    testSame(
        options,
        """
        goog.getCssName = function(x) {};
        goog.getCssName('foo');
        """);
  }

  @Test
  public void testClosurePassOn() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    test(options, "goog.require('foo');", DiagnosticGroups.MISSING_SOURCES_WARNINGS);
    test(
        options,
        """
        goog.getCssName = function(x) {};
        goog.getCssName('foo');
        """,
        """
        goog.getCssName = function(x) {};
        'foo';
        """);
  }

  @Test
  public void testTypedefBeforeOwner1() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    test(
        options,
        """
        goog.provide('foo.Bar.Type');
        goog.provide('foo.Bar');
        /** @typedef {number} */ foo.Bar.Type;
        foo.Bar = function() {};
        """,
        """
        var foo = {};
        foo.Bar.Type = {};
        foo.Bar.Type;
        foo.Bar = function() {};
        """);
  }

  @Test
  public void testTypedefChildProvide_noTypechecking_brokenCode() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    test(
        options,
        """
        goog.provide('foo.Bar');
        goog.provide('foo.Bar.Type');

        foo.Bar = function() {};
        /** @typedef {number} */ foo.Bar.Type;
        """,
        """
        /** @const */ var foo = {};
        // This output will cause a NPE at runtime. This test is meant to demonstrate that
        // edge case. Note that when typechecking is enabled, the compiler will emit an error
        // rather than produce broken output, which should cover most use cases.
        foo.Bar.Type = {};
        foo.Bar = function() {};
        foo.Bar.Type;
        """);
  }

  @Test
  public void testTypedefChildProvide_withTypechecking() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    compile(
        options,
        """
        goog.provide('foo.Bar');
        goog.provide('foo.Bar.Type');

        foo.Bar = function() {};
        /** @typedef {number} */ foo.Bar.Type;
        """);

    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(lastCompiler.getErrors().get(0).description()).contains("provide foo.Bar.Type");
  }

  @Test
  public void testTypedefProvides() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    test(
        options,
        CLOSURE_BOILERPLATE_UNCOMPILED
            + """
            goog.provide('ns');
            goog.provide('ns.SomeType');
            goog.provide('ns.SomeType.EnumValue');
            goog.provide('ns.SomeType.defaultName');
            // subnamespace assignment happens before parent.
            /** @enum {number} */
            ns.SomeType.EnumValue = { A: 1, B: 2 };
            // parent namespace isn't ever actually assigned.
            // we're relying on goog.provide to provide it.
            /** @typedef {{name: string, value: ns.SomeType.EnumValue}} */
            ns.SomeType;
            /** @const {string} */
            ns.SomeType.defaultName = 'foobarbaz';
            """,
        // the provides should be rewritten, then collapsed, then removed by RemoveUnusedCode
        "");
  }

  @Test
  public void testGoogDefine1() {
    String code =
        CLOSURE_BOILERPLATE_UNCOMPILED
            + "/** @define {boolean} */ var FLAG = goog.define('FLAG_XYZ', true);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("FLAG_XYZ", false);

    test(options, code, CLOSURE_COLLAPSED + " var FLAG = false;");
  }

  @Test
  public void testGoogDefine_typeMismatch() {
    String code =
        CLOSURE_BOILERPLATE_UNCOMPILED
            + "/** @define {boolean} */ var FLAG = goog.define('FLAG_XYZ', 0);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setDefineToBooleanLiteral("FLAG_XYZ", false);
    options.setCheckTypes(true);

    test(options, code, DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testGoogDefine_missingDefineAnnotationErrors_checksOnly() {
    String code = CLOSURE_BOILERPLATE_UNCOMPILED + "var FLAG = goog.define('FLAG_XYZ', false);";

    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setChecksOnly(true);

    Compiler lastCompiler = compile(options, code);
    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(lastCompiler.getErrors().get(0).description()).contains("@define");
  }

  @Test
  public void testGoogDefine2() {
    String code =
        CLOSURE_BOILERPLATE_UNCOMPILED
            + """
            goog.provide('ns');
            /** @define {boolean} */ ns.FLAG = goog.define('ns.FLAG_XYZ', true);
            """;
    CompilerOptions options = createCompilerOptions();

    options.setClosurePass(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setDefineToBooleanLiteral("ns.FLAG_XYZ", false);
    test(options, code, CLOSURE_COLLAPSED + "var ns$FLAG = false;");
  }

  @Test
  public void testI18nMessageValidation_throughModuleExport() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);

    test(
        options,
        new String[] {
          """
          goog.module('a.b')

          exports = {
            MSG_HELLO: goog.getMsg('Hello!'),
          }
          """,
          """
          goog.module('a.c')

          const b = goog.require('a.b');

          const {MSG_HELLO} = b;
          """
        },
        new String[] {
          """
          var module$exports$a$b = {
            MSG_HELLO: goog.getMsg('Hello!'),
          }
          """,
          """
          var module$exports$a$c = {};

          const {MSG_HELLO: module$contents$a$c_MSG_HELLO} = module$exports$a$b;
          """
        });
  }

  @Test
  public void testClosurePassPreservesJsDoc() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setClosurePass(true);

    test(
        options,
        CLOSURE_BOILERPLATE_UNCOMPILED
            + """
            goog.provide('Foo');
            /** @constructor */ Foo = function() {};
            var x = new Foo();
            """,
        CLOSURE_BOILERPLATE_COMPILED
            + """
            var Foo = function() {};
            var x = new Foo;
            """);
    test(
        options,
        CLOSURE_BOILERPLATE_UNCOMPILED + "goog.provide('Foo'); /** @enum */ Foo = {a: 3};",
        CLOSURE_BOILERPLATE_COMPILED + "var Foo={a:3}");
  }

  @Test
  public void testProvidedNamespaceIsConst() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        """
        goog.provide('foo');
        function f() { foo = {};}
        """,
        """
        var foo = {};
        function f() { foo = {}; }
        """);
  }

  @Test
  public void testProvidedNamespaceIsConst3() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        """
        goog.provide('foo.bar');
        goog.provide('foo.bar.baz');
        /** @constructor */ foo.bar = function() {};
        /** @constructor */ foo.bar.baz = function() {};
        """,
        """
        var foo$bar = function(){};
        var foo$bar$baz = function(){};
        """);
  }

  @Test
  public void testProvidedNamespaceIsConst4() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        """
        goog.provide('foo.Bar');
        var foo = {}; foo.Bar = {};
        """,
        "var foo = {}; foo = {}; foo.Bar = {};");
  }

  @Test
  public void testProvidedNamespaceIsConst5() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setInlineConstantVars(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    test(
        options,
        """
        goog.provide('foo.Bar');
        foo = {}; foo.Bar = {};
        """,
        "var foo = {}; foo = {}; foo.Bar = {};");
  }

  @Test
  public void testProvidingTopLevelVar() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    testNoWarnings(
        options,
        """
        goog.provide('Placer');
        goog.provide('Placer.Alignment');
        /** @param {*} image */ var Placer = function(image) {};
        Placer.Alignment = {LEFT: 'left'};
        """);
  }

  @Test
  public void testSortingOff() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    test(
        options,
        new String[] {"goog.require('goog.beer');", "goog.provide('goog.beer');"},
        DiagnosticGroups.LATE_PROVIDE);
  }

  @Test
  public void testGoogModuleDuplicateExport() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setStrictModeInput(true);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.ERROR);

    test(
        options,
        """
        goog.module('example');

        class Foo {}
        exports = {
          Foo,
          Foo,
        };
        """,
        DiagnosticGroups.ES5_STRICT);
  }

  @Test
  public void testGoogModuleOuterLegacyInner() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());

    test(
        options,
        new String[] {
          // googModuleOuter
          """
          goog.module('foo.Outer');
          /** @constructor */ function Outer() {}
          exports = Outer;
          """,
          // legacyInner
          """
          goog.module('foo.Outer.Inner');
          goog.module.declareLegacyNamespace();
          /** @constructor */ function Inner() {}
          exports = Inner;
          """,
          // legacyUse
          """
          goog.provide('legacy.Use');
          goog.require('foo.Outer');
          goog.require('foo.Outer.Inner');
          goog.scope(function() {
          var Outer = goog.module.get('foo.Outer');
          new Outer;
          new foo.Outer.Inner;
          });
          """
        },
        new String[] {
          """
          /** @constructor */ function module$contents$foo$Outer_Outer() {}
          var module$exports$foo$Outer = module$contents$foo$Outer_Outer;
          """,
          """
          /** @const */ var foo={};
          /** @const */ foo.Outer={};
          /** @constructor */ function module$contents$foo$Outer$Inner_Inner(){}
          /** @const */ foo.Outer.Inner=module$contents$foo$Outer$Inner_Inner;
          """,
          """
          /** @const */ var legacy={};
          /** @const */ legacy.Use={};
          new module$contents$foo$Outer_Outer;
          new module$contents$foo$Outer$Inner_Inner
          """
        });
  }

  @Test
  public void testLegacyGoogModuleExport1() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setGenerateExports(true);

    test(
        options,
        new String[] {
          """
          goog.module('foo.example.ClassName');
          goog.module.declareLegacyNamespace();

          /** @constructor */ function ClassName() {}

          /** @export */
          exports = ClassName;
          """,
        },
        new String[] {
"""
var foo = {};
foo.example = {};
function module$contents$foo$example$ClassName_ClassName() {}
foo.example.ClassName = module$contents$foo$example$ClassName_ClassName;
goog.exportSymbol('foo.example.ClassName', module$contents$foo$example$ClassName_ClassName);
""",
        });
  }

  @Test
  public void testLegacyGoogModuleExport2() {
    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);
    options.setCodingConvention(new ClosureCodingConvention());
    options.setGenerateExports(true);

    test(
        options,
        new String[] {
          """
          goog.module('foo.ns');
          goog.module.declareLegacyNamespace();

          /** @constructor */ function ClassName() {}

          /** @export */
          exports.ExportedName = ClassName;
          """,
        },
        new String[] {
          """
          var foo = {};
          foo.ns = {};
          function module$contents$foo$ns_ClassName() {}
          foo.ns.ExportedName = module$contents$foo$ns_ClassName;
          goog.exportSymbol('foo.ns.ExportedName', module$contents$foo$ns_ClassName);
          """,
        });
  }

  @Test
  public void testGoogModuleGet_notAllowedInGlobalScope() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    // Test in a regular script
    test(
        options,
        new String[] {
          // input 0
          "goog.module('a.b');",
          // input 1
          "var unsupportedAssignmentToGlobal = goog.module.get('a.b');"
        },
        DiagnosticGroups.CLOSURE_DEP_METHOD_USAGE_CHECKS);

    // Test in a file with a goog.provide
    test(
        options,
        new String[] {
          // input 0
          "goog.module('a.b');",
          // input 1
          "goog.provide('c'); var unsupportedAssignmentToGlobal = goog.module.get('a.b');"
        },
        DiagnosticGroups.CLOSURE_DEP_METHOD_USAGE_CHECKS);
  }

  @Test
  public void testProvideRequireSameFile() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    test(options, "goog.provide('x'); goog.require('x');", "var x = {};");
  }

  @Test
  public void testDependencySorting() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    test(
        options,
        new String[] {
          "goog.require('x');", "goog.provide('x');",
        },
        new String[] {
          "goog.provide('x');",
          "goog.require('x');",

          // For complicated reasons involving modules,
          // the compiler creates a synthetic source file.
          "",
        });
  }

  @Test
  public void testClosureDefines() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
    level.setOptionsForCompilationLevel(options);
    WarningLevel warnings = WarningLevel.DEFAULT;
    warnings.setOptionsForWarningLevel(options);

    String code =
        """
        var CLOSURE_DEFINES = {
          'FOO': 1,
          'BAR': true
        };

        /** @define {number} */ var FOO = 0;
        /** @define {boolean} */ var BAR = false;
        """;

    String result =
        """
        var CLOSURE_DEFINES = {
          FOO: 1,
          BAR: !0
        },
        FOO = 1,
        BAR = !0
        """;

    test(options, code, result);
  }

  @Test
  public void testGoogWeakUsage() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addClosureExterns()
                .addExtra("function use(x) {}")
                .buildExternsFile("externs.js"));

    String code =
        """
        var noStrongRefs = 123;
        function getValue() { return 456; }
        var hasStrongRefs = getValue();
        use(hasStrongRefs);
        use(goog.weakUsage(hasStrongRefs));
        use(goog.weakUsage(noStrongRefs));
        """; //

    // RemoveUnusedCode rewrites `goog.weakUsage(noStrongRefs)` to `void 0`.
    // PeepholeReplaceKnownMethods rewrites `goog.weakUsage(a)` to `a`.
    //
    // InlineVariables has a special check that prevents the value of `a` from getting inlined into
    // `use(a)`, because that would interfere with RemoveUnusedCode's analysis of the weak usage.
    // But the getValue() function can still be inlined into `a`.
    String result =
        """
        var a = 456;
        use(a);
        use(a);
        use(void 0);
        """;

    test(options, code, result);
  }

  @Test
  public void testGoogWeakUsageQualifiedName() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addClosureExterns()
                .addExtra("function use(x) {}")
                .buildExternsFile("externs.js"));

    String code =
        """
        var noStrongRefs = {prop: 123};
        function getValue() { return 456; }
        var hasStrongRefs = {prop: getValue()};
        use(hasStrongRefs.prop);
        use(goog.weakUsage(hasStrongRefs.prop));
        use(goog.weakUsage(noStrongRefs.prop));
        """; //

    // RemoveUnusedCode rewrites `goog.weakUsage(noStrongRefs)` to `void 0`.
    // PeepholeReplaceKnownMethods rewrites `goog.weakUsage(a)` to `a`.
    //
    // InlineVariables has a special check that prevents the value of `a` from getting inlined into
    // `use(a)`, because that would interfere with RemoveUnusedCode's analysis of the weak usage.
    // But the getValue() function can still be inlined into `a`.
    String result =
        """
        var a = 456;
        use(a);
        use(a);
        use(void 0);
        """;

    test(options, code, result);
  }

  @Test
  public void testGoogWeakUsageNocollapse() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addClosureExterns()
                .addExtra("function use(x) {}")
                .buildExternsFile("externs.js"));

    String code =
        """
        var noStrongRefs = { /** @nocollapse */ prop: 123};
        function getValue() { return 456; }
        var hasStrongRefs = { /** @nocollapse */ prop: getValue()};
        use(hasStrongRefs.prop);
        use(goog.weakUsage(hasStrongRefs.prop));
        use(goog.weakUsage(noStrongRefs.prop));
        """; //

    // @nocollapse prevents goog.weakUsage from having an effect.
    String result =
        """
        var a = {a:456};
        use(a.a);
        use(a.a);
        use(123);
        """;

    test(options, code, result);
  }

  @Test
  public void testExternsProvideIsAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    externs =
        ImmutableList.of(
            SourceFile.fromCode(
                "<externs>",
                """
                /** @fileoverview @suppress {externsValidation} */
                goog.provide('foo.bar');
                /** @type {!Array<number>} */ foo.bar;
                """));

    test(options, "var goog;", "var goog;");
  }

  @Test
  public void testIjsProvideIsAllowed1() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    test(
        options,
        new String[] {
          "/** @typeSummary */ goog.provide('foo.bar'); /** @type {!Array<number>} */ foo.bar;",
          """
          goog.provide('foo.baz');

          /** @return {number} */
          foo.baz = function() { return foo.bar[0]; }
          """,
        },
        new String[] {
          """
          goog.provide('foo.baz');

          /** @return {number} */
          foo.baz = function() { return foo.bar[0]; }
          """,
        });
  }

  @Test
  public void testTypeSummaryReferencesToGoogModuleTypesAreRewritten() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setChecksOnly(true);

    test(
        options,
        new String[] {
          """
          /** @typeSummary */
          /** @constructor @template T */ function Bar() {}
          /** @const {!Bar<!ns.Foo>} */ var B;
          """,
          """
          goog.module('ns');

          exports.Foo = class {}
          """,
        },
        (String[]) null);
  }

  @Test
  public void testGoogModuleReferencesToExternsTypesAreRewritten() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);

    // This is a very weird pattern, but we need to remove the usages before we remove support
    test(
        options,
        new String[] {
          """
          /** @externs */
          CLOSURE_BOILERPLATE_UNCOMPILED
          goog.provide('ext.Bar');
          /** @constructor */ ext.Bar = function() {};

          """
              .replace("CLOSURE_BOILERPLATE_UNCOMPILED", CLOSURE_BOILERPLATE_UNCOMPILED),
          """
          goog.module('ns');
          const Bar = goog.require('ext.Bar');

          exports.Foo = class extends Bar {}
          """,
        },
        (String[]) null);
  }

  /** Creates a CompilerOptions object with google coding conventions. */
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setRenamePrefixNamespaceAssumeCrossChunkNames(true);
    options.setAssumeGettersArePure(false);
    return options;
  }

  @Test
  public void testGetOriginalQualifiedNameAfterEs6RewriteClasses() {
    // A bug in Es6RewriteClasses meant we were putting the wrong `originalName` on some nodes.
    CompilerOptions options = createCompilerOptions();
    // force SourceInformationAnnotator to run
    options.setExternExportsPath("exports.js");
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);

    test(
        options,
        """
        goog.module('a');
        class Foo { static method() {} }
        class Bar { foo() { Foo.method(); } }
        """,
        """
        var module$exports$a = {};
        /** @constructor @struct */
        var module$contents$a_Foo = function() {};
        module$contents$a_Foo.method = function() {};

        /** @constructor @struct */
        var module$contents$a_Bar = function () {}
        module$contents$a_Bar.prototype.foo =
            function() { module$contents$a_Foo.method(); }
        """);

    Node script = lastCompiler.getRoot().getSecondChild().getFirstChild();

    Node exprResult = script.getLastChild();
    Node anonFunction = exprResult.getFirstChild().getLastChild();
    assertNode(anonFunction).hasToken(Token.FUNCTION);
    Node body = anonFunction.getLastChild();

    Node callNode = body.getOnlyChild().getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);

    Node modulecontentsFooMethod = callNode.getFirstChild();
    // Verify this is actually "Foo.method" - it used to be "Foo.foo".
    assertThat(modulecontentsFooMethod.getOriginalQualifiedName()).isEqualTo("Foo.method");
    assertThat(modulecontentsFooMethod.getOriginalName()).isEqualTo("method");
  }

  @Test
  public void testExternsWithGoogProvide() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setCheckTypes(true);

    test(
        options,
        """
        var ns = {};
        // generally it's not allowed to access undefined namespaces
        ns.subns.foo = function() {};
        """,
        DiagnosticGroups.MISSING_PROPERTIES);

    testSame(
        options,
        """
        /** @externs */
        var ns = {};
        // but @externs annotation hoists code to externs, where it is allowed
        ns.subns.foo = function() {};
        """);

    testSame(
        options,
        """
        /** @externs */
        var ns = {};
        ns.subns.foo = function() {};
        var goog;
        // even when there is a goog.provide statement
        goog.provide('provided');
        """);
  }

  @Test
  public void testExternsWithGoogProvide_required() {
    CompilerOptions options = createCompilerOptions();
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setClosurePass(true);
    options.setChecksOnly(true);
    String externs =
        """
        /** @externs */
        var goog;
        /** @const */
        var mangled$name$from$externs = {};
        /** @constructor */
        mangled$name$from$externs.Clazz = function() {};
        goog.provide('ns.from.externs');
        /** @const */ var ns = {};
        /** @const */ ns.from = {};
        ns.from.externs = mangled$name$from$externs;
        """;

    test(
        options,
        new String[] {
          externs,
          """
          goog.module('ns.from.other');
          exports = {val: 1};
          /** @type {ns.from.externs.Clazz} */
          var usingExterns = null;
          """,
        },
        new String[] {
          "",
          """
          var module$exports$ns$from$other = {val: 1};
          /** @type {ns.from.externs.Clazz} */
          var module$contents$ns$from$other_usingExterns = null;
          """,
        });
  }

  @Test
  public void testCrossChunkDepCheck_inModule_withRequire() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    ImmutableList<JSChunk> code =
        ImmutableList.copyOf(
            JSChunkGraphBuilder.forStar()
                .addChunk("// base")
                .addChunk("goog.module('goog.Foo'); /** @constructor */ exports = function() {};")
                .addChunk(
                    "goog.module('goog.Bar'); const Foo = goog.require('goog.Foo'); new Foo();")
                .setFilenameFormat(inputFileNamePrefix + "%s" + inputFileNameSuffix)
                .build());

    Compiler compiler = compile(options, code);

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);

    assertWithMessage("Unexpected error " + compiler.getWarnings().get(0))
        .that(DiagnosticGroups.STRICT_MODULE_DEP_CHECK.matches(compiler.getWarnings().get(0)))
        .isTrue();
  }

  @Test
  public void testCrossChunkDepCheck_forProvide_withRequire() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    ImmutableList<JSChunk> code =
        ImmutableList.copyOf(
            JSChunkGraphBuilder.forStar()
                .addChunk("// base")
                .addChunk("goog.provide('goog.Foo');/** @constructor */ goog.Foo = function() {};")
                .addChunk("goog.require('goog.Foo'); new goog.Foo();")
                .setFilenameFormat(inputFileNamePrefix + "%s" + inputFileNameSuffix)
                .build());

    Compiler compiler = compile(options, code);

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).hasSize(1);

    assertWithMessage("Unexpected error " + compiler.getWarnings().get(0))
        .that(DiagnosticGroups.STRICT_MODULE_DEP_CHECK.matches(compiler.getWarnings().get(0)))
        .isTrue();
  }

  @Test
  public void testGoogReflectObjectProperty_withNoArgs_doesNotCrash() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(false);
    // options.setCheckTypes(true);
    // options.setChecksOnly(true);

    test(options, new String[] {"goog.reflect.objectProperty();"}, (String[]) null);
  }

  @Test
  public void testGoogReflectObjectPropertyCall_returnsRenamedProperty() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setGeneratePseudoNames(true);

    test(
        options,
        new String[] {
          """
          goog.provide('goog.reflect');
          goog.reflect.objectProperty = function(prop, obj) { return prop; };
          """,
          "alert(goog.reflect.objectProperty('prop', {prop: 0}));"
        },
        new String[] {"goog.$reflect$ = {};", "alert('$prop$');"});
  }

  @Test
  public void
      testGoogReflectObjectPropertyCall_inModule_withoutPropertyCollapsing_returnsRenamedProperty() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setGeneratePseudoNames(true);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.NONE);
    // forces the SourceInformationAnnotator to run
    // TODO(b/193048186): remove this once SourceInformationAnnotator always runs
    options.setReplaceStringsFunctionDescriptions(ImmutableList.of("\"Error(?)\""));

    test(
        options,
        new String[] {
          """
          goog.provide('goog.reflect');
          goog.reflect.objectProperty = function(prop, obj) { return prop; };
          """,
          """
          goog.module('m');
          const reflect = goog.require('goog.reflect');
          alert(reflect.objectProperty('prop', {prop: 0}));
          """
        },
        new String[] {
          "goog.$reflect$ = {};",
          """
          var module$exports$m = {};
          alert('$prop$');
          """
        });
  }

  @Test
  public void testGoogForwardDeclareInExterns_doesNotBlockVariableRenaming() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);
    options.setCheckTypes(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(SourceFile.fromCode("abc.js", "goog.forwardDeclare('a.b.c');"))
            .build();

    compile(options, "/** @type {!a.b.c} */ var x;" + CLOSURE_BOILERPLATE_UNCOMPILED);

    assertThat(lastCompiler.toSource()).startsWith("var a;");
  }

  @Test
  public void testCanSpreadOnGoogModuleImport() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCollapsePropertiesLevel(PropertyCollapseLevel.ALL);
    options.setClosurePass(true);
    useNoninjectingCompiler = true;

    String originalModule =
        """
        goog.module('utils');
        exports.Klazz = class {};
        exports.fn = function() {};
        """;

    String originalModuleCompiled =
        """
        var module$exports$utils$Klazz = function() {};
        var module$exports$utils$fn = function() {};
        """;

    // Test destructuring import
    test(
        options,
        new String[] {
          originalModule,
          """
          goog.module('client');
          const {fn} = goog.require('utils');
          fn(...[]);
          """
        },
        new String[] {originalModuleCompiled, "module$exports$utils$fn.apply(null, []);"});

    // Test non-destructuring import
    test(
        options,
        new String[] {
          originalModule,
          """
          goog.module('client');
          const utils = goog.require('utils');
          utils.fn(...[]);
          """
        },
        new String[] {originalModuleCompiled, "module$exports$utils$fn.apply(null,[])"});
  }

  @Test
  public void testMalformedGoogModulesGracefullyError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "closure_externs.js", new TestExternsBuilder().addClosureExterns().build()))
            .build();

    test(
        options,
        """
        goog.module('m');
        var x;
        goog.module.declareLegacyNamespace();
        """,
        DiagnosticGroups.MALFORMED_GOOG_MODULE);

    test(options, "var x; goog.module('m');", DiagnosticGroups.MALFORMED_GOOG_MODULE);
  }

  @Test
  public void testDuplicateClosureNamespacesError() {
    CompilerOptions options = createCompilerOptions();
    options.setClosurePass(true);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                SourceFile.fromCode(
                    "closure_externs.js", new TestExternsBuilder().addClosureExterns().build()))
            .build();

    ImmutableList<String> namespaces =
        ImmutableList.of(
            "goog.provide('a.b.c');",
            "goog.module('a.b.c');",
            "goog.declareModuleId('a.b.c'); export {};",
            "goog.module('a.b.c'); goog.module.declareLegacyNamespace();");

    for (String firstNs : namespaces) {
      for (String secondNs : namespaces) {
        test(options, new String[] {firstNs, secondNs}, DiagnosticGroups.DUPLICATE_NAMESPACES);
      }
    }
  }

  @Test
  public void testExportMissingClosureBase() {
    CompilerOptions options = createCompilerOptions();
    options.setGenerateExports(true);
    options.setContinueAfterErrors(true); // test that the AST is not left in an invalid state.
    externs = ImmutableList.of(); // remove Closure base externs

    test(
        options,
        "/** @export */ function Foo() { alert('hi'); }",
        DiagnosticGroup.forType(GenerateExports.MISSING_GOOG_FOR_EXPORT));
  }

  @Test
  public void testTypecheckClass_assignedInNestedAssignInGoogModule() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setClosurePass(true);
    options.setChecksOnly(true);

    compile(
        options,
        """
        goog.module('main');

        var TestEl_1;
        let TestEl = TestEl_1 = class TestEl {
          constructor() {
              this.someVal = false;
          }
        }
        // pattern that may be generated from TypeScript decorators
        TestEl = TestEl_1 = (0, decorate)(TestEl);
        exports.TestEl = TestEl;
        /** @type {!TestEl} */ const t = new TestEl();
        """);

    checkUnexpectedErrorsOrWarnings(lastCompiler, 0);
  }

  @Test
  public void testTypecheckClassMultipleExtends_doesNotDisambiguate() {
    // Regression test for b/325489639
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    options.setGeneratePseudoNames(true);
    inputFileNameSuffix = ".closure.js";
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addAlert()
                .addClosureExterns()
                .addExtra("var window;")
                .buildExternsFile("externs.js"));
    options.setLanguageOut(LanguageMode.NO_TRANSPILE);

    test(
        options,
"""
/** @fileoverview @suppress {checkTypes} */

// Define two parent classes, Foo and Bar, and a child class FooBar that all share
// method 1.
// FooBar is not really allowed to extend both Foo and Bar, but the @suppress checkTypes
// and the .closure.js suffix make it not an error. We expect property disambiguation to
// back off on Foo and Bar.
class Foo { method1() { alert('foo'); } }
class Bar { method1() { alert('bar'); } }
/**
 * @extends {Foo}
 * @extends {Bar}
 */
class FooBar { method1() {alert('foobar'); } }
// Define extra normal classes Quz and Baz with a method2, just to ensure that
// property disambiguation actually runs in this test case, and it's not a fluke that
// method1 is not disambiguated.
class Qux { method2() { alert('qux'); } }
class Baz { method2() { alert('baz'); } }
/** @noinline */
function callMethods(/** !Foo */ foo, /** !Bar */ bar, /** !FooBar */ fooBar, /** !Qux */ qux, /** !Baz */ baz) {
// not disambiguated
  foo.method1();
  bar.method1();
  fooBar.method1();
// disambiguated
  qux.method2();
  baz.method2();
}
window['prevent_dce'] = [Foo, Bar, FooBar, Qux, Baz, callMethods];
""",
        """
        class $Foo$$ { $method1$() { alert('foo'); } }
        class $Bar$$ { $method1$() { alert('bar'); } }
        class $FooBar$$ { $method1$() {alert('foobar'); } }
        class $Qux$$ { }
        class $Baz$$ { }

        /** @noinline */
        function $callMethods$$(/** !Foo */ $foo$$, /** !Bar */ $bar$$, /** !FooBar */ $fooBar$$) {
        // not disambiguated
          $foo$$.$method1$();
          $bar$$.$method1$();
          $fooBar$$.$method1$();
        // disambiguated + inlined by InlineFunctions
          alert('qux');
          alert('baz');
        }
        window.prevent_dce = [$Foo$$, $Bar$$, $FooBar$$, $Qux$$, $Baz$$, $callMethods$$];
        """);
  }

  @Test
  public void testFileoverviewVisibility_overridenOnLegacyGoogModuleExport() {
    CompilerOptions options = createCompilerOptions();
    options.setCheckTypes(true);
    options.setClosurePass(true);
    options.setChecksOnly(true);
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.ERROR);

    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(new TestExternsBuilder().addConsole().buildExternsFile("console.js"))
            .build();

    compile(
        options,
        ImmutableList.copyOf(
            JSChunkGraphBuilder.forChain()
                .setFilenameFormat("dir%s/f.js")
                .addChunk(
                    """
                    /**
                     * @fileoverview
                     * @package
                     */
                    goog.module('fileoverview.package.visibility.PublicFoo');
                    goog.module.declareLegacyNamespace();

                    class PublicFoo {
                      static protectedMethod() {}
                    }
                    // Override the @protected @fileoverview visibility
                    /** @public */
                    exports = PublicFoo;
                    """)
                .addChunk(
                    """
                    goog.module('client');
                    const PublicFoo = goog.require('fileoverview.package.visibility.PublicFoo');
                    console.log(PublicFoo); // this should be okay
                    console.log(PublicFoo.protectedMethod()); // violates package visibility
                    """)
                .build()));

    assertThat(lastCompiler.getWarnings()).isEmpty();
    assertThat(lastCompiler.getErrors()).hasSize(1);
    assertThat(lastCompiler.getErrors().get(0).description())
        .contains("Access to package-private property protectedMethod");
  }
}
