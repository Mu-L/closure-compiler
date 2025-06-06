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

package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.refactoring.testing.SuggestedFixes.assertChanges;
import static com.google.javascript.refactoring.testing.SuggestedFixes.assertReplacement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.refactoring.SuggestedFix.MatchedNodeInfo;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for JsFlume {@link SuggestedFix}. */
@RunWith(JUnit4.class)
public class SuggestedFixTest {
  @Test
  public void testInsertBefore() {
    String before = "var someRandomCode = {};";
    String after = "/** some comment */\ngoog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode =
        IR.exprResult(IR.call(IR.getprop(IR.name("goog2"), "get"), IR.string("service")));
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .insertBefore(root.getLastChild().getFirstChild(), newNode, compiler)
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), 0, "goog2.get('service');\n");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertBeforeWithNonJSDocBlockComment() {
    String before = "var someRandomCode = {};\n";
    String after = "/* some comment */\ngoog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode =
        IR.exprResult(IR.call(IR.getprop(IR.name("goog2"), "get"), IR.string("service")));
    SuggestedFix fix =
        new SuggestedFix.Builder().insertBefore(root.getLastChild(), newNode, compiler).build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), 0, "goog2.get('service');\n");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertBeforeWithNonJSDocLineComment() {
    String before = "var someRandomCode = {};\n";
    String after = "// some comment \ngoog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode =
        IR.exprResult(IR.call(IR.getprop(IR.name("goog2"), "get"), IR.string("service")));
    SuggestedFix fix =
        new SuggestedFix.Builder().insertBefore(root.getLastChild(), newNode, compiler).build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), 0, "goog2.get('service');\n");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplaceText() {
    String input = "var foo = new Bar();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().replaceText(root.getFirstFirstChild(), 3, "quux").build();
    CodeReplacement replacement = CodeReplacement.create(4, 3, "quux");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete() {
    String input = "var foo = new Bar();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstChild()).build();
    CodeReplacement replacement = CodeReplacement.create(0, input.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_spaceBeforeNode() {
    String before = "var foo = new Bar();";
    String after = "\n\nvar baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getLastChild()).build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_NonJSDocLineCommentBeforeNode() {
    String before = "var foo = new Bar();";
    String after = "// blah \n var baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getLastChild()).build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_NonJSDocBlockCommentBeforeNode() {
    String before = "var foo = new Bar();";
    String after = "/* blah */ var baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getLastChild()).build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_dontDeleteSpaceBeforeNode() {
    String before = "var foo = new Bar();\n\n";
    String after = "var baz = new Baz();";
    String input = before + after;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .deleteWithoutRemovingWhitespaceBefore(root.getLastChild())
            .build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), after.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleVarDeclaration() {
    String input = "var foo = 3, bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();
    CodeReplacement replacement = CodeReplacement.create(4, "foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(13, "bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(16, ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleVarDeclarationWithJSDocComments() {
    String input = "var /** fooComment*/foo = 3, bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();
    CodeReplacement replacement =
        CodeReplacement.create(4, "/** fooComment*/foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(input.indexOf("bar, "), "bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(input.indexOf(", baz"), ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleVarDeclarationWithNonJSDocComments() {
    String input = "var /* fooComment*/ foo = 3, bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();

    CodeReplacement replacement =
        CodeReplacement.create(4, "/* fooComment*/ foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(input.indexOf("bar, "), "bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(input.indexOf(", baz"), ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleLetDeclaration() {
    String input = "let foo = 3, bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();
    CodeReplacement replacement = CodeReplacement.create(4, "foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(13, "bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(16, ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleLetDeclarationWithNonJSDocComment() {
    String input = "let foo = 3, /* blah */ bar, baz;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();
    CodeReplacement replacement = CodeReplacement.create(4, "foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(13, "/* blah */ bar, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(input.indexOf(", baz"), ", baz".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testDelete_multipleConstDeclaration() {
    String input = "const foo = 3, bar = 4, baz = 5;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);

    // Delete the 1st variable on the line. Make sure the deletion includes the assignment and the
    // trailing comma.
    SuggestedFix fix = new SuggestedFix.Builder().delete(root.getFirstFirstChild()).build();
    CodeReplacement replacement = CodeReplacement.create(6, "foo = 3, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the 2nd variable.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getSecondChild()).build();
    replacement = CodeReplacement.create(input.indexOf("bar = 4, "), "bar = 4, ".length(), "");
    assertReplacement(fix, replacement);

    // Delete the last variable. Make sure it removes the leading comma.
    fix = new SuggestedFix.Builder().delete(root.getFirstChild().getLastChild()).build();
    replacement = CodeReplacement.create(input.indexOf(", baz = 5"), ", baz = 5".length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameStringKey() {
    String input = "var obj = {foo: 'bar'};";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node node = root.getFirstFirstChild().getFirstFirstChild();
    SuggestedFix fix = new SuggestedFix.Builder().rename(node, "fooBar").build();
    CodeReplacement replacement = CodeReplacement.create(11, "foo".length(), "fooBar");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameProperty_justPropertyName() {
    String input = "obj.test.property";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().rename(root.getFirstFirstChild(), "renamedProperty").build();
    CodeReplacement replacement = CodeReplacement.create(9, "property".length(), "renamedProperty");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameProperty_entireName() {
    String input = "obj.test.property";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .rename(root.getFirstFirstChild(), "renamedProperty", true)
            .build();
    CodeReplacement replacement = CodeReplacement.create(0, input.length(), "renamedProperty");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameFunction_justFunctionName() {
    String input = "obj.fnCall();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().rename(root.getFirstFirstChild(), "renamedFnCall").build();
    CodeReplacement replacement = CodeReplacement.create(4, "fnCall".length(), "renamedFnCall");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameFunction_entireName() {
    String fnName = "goog.dom.classes.add";
    String newFnName = "goog.dom.classlist.add";
    String input = fnName + "(foo, bar);";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().rename(root.getFirstFirstChild(), newFnName, true).build();
    CodeReplacement replacement = CodeReplacement.create(0, fnName.length(), newFnName);
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameTaggedTemplate_justFunctionName() {
    String prefix = "prt.";
    String fnName = "oldTaggedTemp";
    String newFnName = "newTaggedTemp";
    String input = prefix + fnName + "`${Foo}Bar`;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().rename(root.getFirstFirstChild(), newFnName).build();
    CodeReplacement replacement =
        CodeReplacement.create(prefix.length(), fnName.length(), newFnName);
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRenameTaggedTemplate_entireName() {
    String fnName = "goog.dom.classes.oldTaggedTemp";
    String newFnName = "goog.dom.classesList.newTaggedTemp";
    String input = fnName + "`${Foo}Bar`;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().rename(root.getFirstFirstChild(), newFnName, true).build();
    CodeReplacement replacement = CodeReplacement.create(0, fnName.length(), newFnName);
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace() {
    String before = "var someRandomCode = {};\n/** some comment */\n";
    String after = "goog.foo();";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode =
        IR.exprResult(IR.call(IR.getprop(IR.name("goog2"), "get"), IR.string("service")));
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .replace(root.getLastChild().getFirstChild(), newNode, compiler)
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), after.length(), "goog2.get('service');");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_functionArgument() {
    String before =
        """
        var MyClass = function() {};
        MyClass.prototype.foo = function() {};
        MyClass.prototype.bar = function() {};
        var clazz = new MyClass();
        """;
    String after = "alert(clazz.foo());";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.call(IR.getprop(IR.name("clazz"), "bar"));
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .replace(root.getLastChild().getFirstChild().getLastChild(), newNode, compiler)
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(
            before.length() + "alert(".length(), "clazz.foo()".length(), "clazz.bar()");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_leftHandSideAssignment() {
    String before = "var MyClass = function() {};\n";
    String after = "MyClass.prototype.foo = function() {};\n";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.getprop(IR.name("MyClass"), "prototype", "bar");
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .replace(root.getLastChild().getFirstFirstChild(), newNode, compiler)
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(
            before.length(), "MyClass.prototype.foo".length(), "MyClass.prototype.bar");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_lowerPrecedence() {
    String before = "2 * ";
    String after = "3";
    Compiler compiler = getCompiler(before + after + ";\n");
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.exprResult(IR.sub(IR.number(4), IR.number(1)));
    Node toReplace = root.getOnlyChild().getOnlyChild().getLastChild();
    SuggestedFix fix = new SuggestedFix.Builder().replace(toReplace, newNode, compiler).build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), after.length(), "(4 - 1)");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_samePrecedence() {
    String before = "5 - ";
    String after = "1";
    Compiler compiler = getCompiler(before + after + ";\n");
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.exprResult(IR.sub(IR.number(3), IR.number(2)));
    Node toReplace = root.getOnlyChild().getOnlyChild().getLastChild();
    SuggestedFix fix = new SuggestedFix.Builder().replace(toReplace, newNode, compiler).build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), after.length(), "(3 - 2)");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testReplace_higherPrecedence() {
    String before = "3 << ";
    String after = "2";
    Compiler compiler = getCompiler(before + after + ";\n");
    Node root = compileToScriptRoot(compiler);
    Node newNode = IR.exprResult(IR.add(IR.number(1), IR.number(1)));
    Node toReplace = root.getOnlyChild().getOnlyChild().getLastChild();
    SuggestedFix fix = new SuggestedFix.Builder().replace(toReplace, newNode, compiler).build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), after.length(), "1 + 1");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testAddCast() {
    String input = "obj.fnCall();";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node objNode = root.getFirstFirstChild().getFirstFirstChild();
    SuggestedFix fix = new SuggestedFix.Builder().addCast(objNode, compiler, "FooBar").build();
    CodeReplacement replacement =
        CodeReplacement.create(0, "obj".length(), "/** @type {FooBar} */ (obj)");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveCast() {
    String input = "var x = /** @type {string} */ (y);";
    String expectedCode = "var x = y;";
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node castNode = root.getFirstFirstChild().getFirstChild();
    assertTrue(castNode.isCast());

    SuggestedFix fix = new SuggestedFix.Builder().removeCast(castNode, compiler).build();
    assertChanges(fix, "", input, expectedCode);
  }

  @Test
  public void testRemoveCast_complexStatement() {
    String input =
        """
        var x = /** @type {string} */ (function() {
          // Inline comment that should be preserved.
          var blah = bleh;
        });
        """;
    String expectedCode =
        """
        var x = function() {
          // Inline comment that should be preserved.
          var blah = bleh;
        };
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node castNode = root.getFirstFirstChild().getFirstChild();
    assertTrue(castNode.isCast());

    SuggestedFix fix = new SuggestedFix.Builder().removeCast(castNode, compiler).build();
    assertChanges(fix, "", input, expectedCode);
  }

  @Test
  public void testRemoveCast_return() {
    String input =
        """
        function f() {
          return /** @type {string} */ (
              'I am obviously a string. Why are you casting me?');
        }
        """;
    String expectedCode =
        """
        function f() {
          return 'I am obviously a string. Why are you casting me?';
        }
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Node castNode = root.getFirstChild().getLastChild().getFirstChild().getLastChild();
    assertTrue(castNode.isCast());

    SuggestedFix fix = new SuggestedFix.Builder().removeCast(castNode, compiler).build();
    assertChanges(fix, "", input, expectedCode);
  }

  @Test
  public void testChangeJsDocType() {
    String before = "/** ";
    String after = "@type {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@type {Foo}".length(), "@type {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType2() {
    String code = "/** @type {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    Node varNode = root.getFirstChild();
    Node jsdocRoot = Iterables.getOnlyElement(varNode.getJSDocInfo().getTypeNodes());
    SuggestedFix fix = new SuggestedFix.Builder().insertBefore(jsdocRoot, "!").build();
    CodeReplacement replacement = CodeReplacement.create("/** @type {".length(), 0, "!");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_packageType1() {
    String before = "/** ";
    String after = "@package {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@package {Foo}".length(), "@package {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_privateType1() {
    String before = "/** ";
    String after = "@private {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@private {Foo}".length(), "@private {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_privateType2() {
    String before = "/** @private ";
    String after = "@const {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@const {Foo}".length(), "@const {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_privateType3() {
    String before = "/** @private ";
    String after = "@const {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@const {Foo}".length(), "@const {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testChangeJsDocType_privateType4() {
    String before = "/** ";
    String after = "@const {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder()
            .changeJsDocType(root.getFirstChild(), compiler, "Object")
            .build();
    CodeReplacement replacement =
        CodeReplacement.create(before.length(), "@const {Foo}".length(), "@const {Object}");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments() {
    String before = "goog.dom.classes.add(";
    String after = "foo, bar);";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 0, "baz").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), 0, "baz, ");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_emptyArguments() {
    String before = "goog.dom.classes.add(";
    String after = ");";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 0, "baz").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), 0, "baz");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_notFirstArgument() {
    String before = "goog.dom.classes.add(foo, ";
    String after = "bar);";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 1, "baz").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), 0, "baz, ");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_lastArgument() {
    String before = "goog.dom.classes.add(foo, bar";
    String after = ");";
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 2, "baz").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), 0, ", baz");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testInsertArguments_beforeCastInArguments() {
    String originalCode = "goog.dom.classes.add(foo, /** @type {String} */ (bar));";
    String expectedCode = "goog.dom.classes.add(foo, baz, /** @type {String} */ (bar));";
    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 1, "baz").build();
    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testInsertArguments_afterCastInArguments() {
    String originalCode = "goog.dom.classes.add(foo, /** @type {String} */ (bar));";
    String expectedCode = "goog.dom.classes.add(foo, /** @type {String} */ (bar), baz);";
    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().insertArguments(root.getFirstFirstChild(), 2, "baz").build();
    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentFirst() {
    String originalCode = "f(a, b, c);";
    String expectedCode = "f(b, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentFirstWithBlockComment() {
    String originalCode = "f(/* blah */ a, b, c);";
    String expectedCode = "f(b, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentFirstWithLineComment() {
    String originalCode = "f(// blah \n a, b, c);";
    String expectedCode = "f(b, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentMiddle() {
    String originalCode = "f(a, b, c);";
    String expectedCode = "f(a, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 1).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentMiddleWithBlockComment() {
    String originalCode = "f(a, /* blah */ b, c);";
    String expectedCode = "f(a, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 1).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentMiddleWithLineComment() {
    String originalCode = "f(a, // blah \n b, c);";
    String expectedCode = "f(a, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 1).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentLast() {
    String originalCode = "f(a, b, c);";
    String expectedCode = "f(a, b);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 2).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentLastBlockComment() {
    String originalCode = "f(a, b, /* blah */ c);";
    String expectedCode = "f(a, b);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 2).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentLastLineComment() {
    String originalCode = "f(a, b, // blah \n c);";
    String expectedCode = "f(a, b);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 2).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteFirstArgumentWithPrefixComment() {
    String originalCode = "f(/** @type {number} */ (a), b, c);";
    String expectedCode = "f(b, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteFirstArgumentWithPostfixComment() {
    String originalCode = "f(a /** foo */, b, c);";
    String expectedCode = "f(b, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentWithPrefixComment() {
    String originalCode = "f(a, /** @type {number} */ (b), c);";
    String expectedCode = "f(a, c);";

    Compiler compiler = getCompiler(originalCode);
    Node root = compileToScriptRoot(compiler);
    SuggestedFix fix =
        new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 1).build();

    assertChanges(fix, "", originalCode, expectedCode);
  }

  @Test
  public void testDeleteArgumentIndexTooLarge() {
    String fnCall = "f(a, b, c);";
    Compiler compiler = getCompiler(fnCall);
    Node root = compileToScriptRoot(compiler);
    assertThrows(
        IllegalArgumentException.class,
        () -> new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 3).build());
  }

  @Test
  public void testDeleteArgumentIndexTooSmall() {
    String fnCall = "f(a);";
    Compiler compiler = getCompiler(fnCall);
    Node root = compileToScriptRoot(compiler);
    assertThrows(
        IllegalArgumentException.class,
        () -> new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), -1).build());
  }

  @Test
  public void testDeleteArgumentWithNoArguments() {
    String fnCall = "f();";
    Compiler compiler = getCompiler(fnCall);
    Node root = compileToScriptRoot(compiler);
    assertThrows(
        IllegalStateException.class,
        () -> new SuggestedFix.Builder().deleteArgument(root.getFirstFirstChild(), 0).build());
  }

  @Test
  public void testAddGoogRequire_var() {
    String before = "goog.provide('js.Foo');\n";
    String after =
        """
        goog.require('js.Bar');

        var x;
        /** @private */
        function foo_() {};
        """;
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire() {
    String before = "goog.provide('js.Foo');\n";
    String after =
        """
        goog.require('js.Bar');

        /** @private */
        function foo_() {};
        """;
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_afterAllOtherGoogRequires() {
    String before =
        """
        goog.provide('js.Foo');
        goog.require('js.Bar');
        """;
    String after =
        """
        /** @private */
        function foo_() {};
        """;
    assertAddGoogRequire(before, after, "zyx.w");
  }

  @Test
  public void testAddGoogRequire_noGoogRequire() {
    String before = "goog.provide('js.Foo');\n";
    String after =
        """
        /** @private */
        function foo_() {};
        """;
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_noGoogRequireOrGoogProvide() {
    String before = "";
    String after =
        """
        /** @private */
        function foo_() {};
        """;
    assertAddGoogRequire(before, after, "abc.def");
  }

  @Test
  public void testAddGoogRequire_alreadyExists() {
    String input =
        """
        goog.provide('js.Foo');
        goog.require('abc.def');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "abc.def", scriptMetadata).build();
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertThat(replacementMap).isEmpty();
  }

  @Test
  public void testAddGoogRequire_requireTypeAlreadyExists() {
    String input =
        """
        goog.provide('js.Foo');
        goog.requireType('abc.def');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "abc.def", scriptMetadata).build();
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertThat(replacementMap).isEmpty();
  }

  @Test
  public void testAddRequireModule() {
    String input =
        """
        goog.module('js.Foo');

        /** @private */
        function foo_() {
        }
        """;
    String expected =
        """
        goog.module('js.Foo');
        const safe = goog.require('goog.safe');

        /** @private */
        function foo_() {
        }
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata).build();
    assertChanges(fix, "", input, expected);
  }

  @Test
  public void testAddRequireTypeModule() {
    String input =
        """
        goog.module('js.Foo');

        /** @private */
        function foo_() {
        }
        """;
    String expected =
        """
        goog.module('js.Foo');
        const Bar = goog.requireType('b.Bar');

        /** @private */
        function foo_() {
        }
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequireType(match, "b.Bar", scriptMetadata).build();
    assertChanges(fix, "", input, expected);
  }

  @Test
  public void testAddRequireModule_requireTypeAlreadyExists() {
    String input =
        """
        goog.module('js.Foo');
        goog.requireType('goog.safe');

        /** @private */
        function foo_() {
        }
        """;
    String expected =
        """
        goog.module('js.Foo');
        const safe = goog.requireType('goog.safe');

        /** @private */
        function foo_() {
        }
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata).build();
    assertChanges(fix, "", input, expected);
  }

  @Test
  public void testAddRequireConst() {
    String input =
        """
        const bar = goog.require('goog.bar');

        /** @private */
        function foo_() {};
        """;
    String expected =
        """
        const bar = goog.require('goog.bar');
        const safe = goog.require('goog.safe');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata).build();
    assertChanges(fix, "", input, expected);
  }

  @Test
  public void testAddRequireVar() {
    String input =
        """
        var bar = goog.require('goog.bar');

        /** @private */
        function foo_() {};
        """;
    String expected =
        // We add new imports as const per the Google Style Guide;
        // TODO(bangert): we could add complexity to add new imports as var if we want to.
        """
        var bar = goog.require('goog.bar');
        const safe = goog.require('goog.safe');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata).build();
    assertChanges(fix, "", input, expected);
  }

  @Test
  public void testAddRequireModuleUnchanged() {
    String input =
        """
        goog.module('js.Foo');
        const safe = goog.require('goog.safe');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata).build();
    assertThat(fix.getReplacements()).isEmpty();
  }

  @Test
  public void testAddRequireModuleDifferentNameUnchanged() {
    String input =
        """
        goog.module('js.Foo');
        const googSafe = goog.require('goog.safe');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix.Builder fixBuilder =
        new SuggestedFix.Builder().addGoogRequire(match, "goog.safe", scriptMetadata);
    assertThat(fixBuilder.build().getReplacements()).isEmpty();
  }

  private static void assertAddGoogRequire(String before, String after, String namespace) {
    Compiler compiler = getCompiler(before + after);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    ScriptMetadata scriptMetadata = ScriptMetadata.create(root, compiler);

    SuggestedFix fix =
        new SuggestedFix.Builder().addGoogRequire(match, namespace, scriptMetadata).build();
    CodeReplacement replacement =
        CodeReplacement.create(
            before.length(), 0, "goog.require('" + namespace + "');\n", namespace);
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveGoogRequire() {
    String before =
        """
        /** @fileoverview blah */

        goog.provide('js.Foo');
        """;
    String googRequire = "goog.require('abc.def');";
    String input =
        before
            + googRequire
            + """
            /** @private */
            function foo_() {};
            """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder().removeGoogRequire(match, "abc.def").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), googRequire.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveGoogRequireAmongSeveralGoogRequires() {
    String before =
        """
        /** @fileoverview blah */

        goog.provide('js.Foo');

        goog.require('abc.abc');
        """;
    String googRequire = "goog.require('abc.def');\n";
    String input =
        before
            + googRequire
            + """
            goog.require('def');

            /** @private */
            function foo_() {};
            """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder().removeGoogRequire(match, "abc.def").build();
    CodeReplacement replacement = CodeReplacement.create(before.length(), googRequire.length(), "");
    assertReplacement(fix, replacement);
  }

  @Test
  public void testRemoveGoogRequire_doesNotExist() {
    String input =
        """
        goog.require('abc.def');

        /** @private */
        function foo_() {};
        """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix = new SuggestedFix.Builder().removeGoogRequire(match, "fakefake").build();
    SetMultimap<String, CodeReplacement> replacementMap = fix.getReplacements();
    assertThat(replacementMap).isEmpty();
  }

  @Test
  public void testGenerateCode_doesNotJsEscapeStrings() {
    String code = "someFunc('foo<>=%&\"bar');\n";
    Compiler compiler = getCompiler(code);
    Node node = compileToScriptRoot(compiler);
    String generated = new SuggestedFix.Builder().generateCode(compiler, node);
    assertEquals(code, generated);
  }

  @Test
  public void testGenerateCode_var1() {
    String code = "var x;\nvar y;\n";
    Compiler compiler = getCompiler(code);
    Node node = compileToScriptRoot(compiler);
    String generated = new SuggestedFix.Builder().generateCode(compiler, node);
    assertEquals(code, generated);
  }

  @Test
  public void testGenerateCode_var2() {
    String code = "var x = 0;\nvar y = 1;\n";
    Compiler compiler = getCompiler(code);
    Node node = compileToScriptRoot(compiler);
    String generated = new SuggestedFix.Builder().generateCode(compiler, node);
    assertEquals(code, generated);
  }

  @Test
  public void testAttachMatchedNodeInfo() {
    String before =
        """
        /** @fileoverview blah */

        goog.provide('js.Foo');
        """;
    String googRequire = "goog.require('abc.def');";
    String input =
        before
            + googRequire
            + """
            /** @private */
            function foo_() {};
            """;
    Compiler compiler = getCompiler(input);
    Node root = compileToScriptRoot(compiler);
    Match match = new Match(root.getFirstChild(), new NodeMetadata(compiler));
    SuggestedFix fix =
        new SuggestedFix.Builder()
            // Corresponds to the goog.require node.
            .attachMatchedNodeInfo(root.getFirstChild().getNext().getFirstChild(), compiler)
            .removeGoogRequire(match, "abc.def")
            .build();
    MatchedNodeInfo info = fix.getMatchedNodeInfo();
    assertThat(info.sourceFilename()).isEqualTo("test");
    assertThat(info.lineno()).isEqualTo(4);
    assertThat(info.charno()).isEqualTo(0);
    assertThat(info.inClosurizedFile()).isTrue();
  }

  /** Returns the root script node produced from the compiled JS input. */
  private static Node compileToScriptRoot(Compiler compiler) {
    Node root = compiler.getRoot();
    // The last child of the compiler root is a Block node, and the first child
    // of that is the Script node.
    return root.getLastChild().getFirstChild();
  }

  private static Compiler getCompiler(String jsInput) {
    Compiler compiler = new Compiler();
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", jsInput)),
        options);
    compiler.parse();
    return compiler;
  }
}
