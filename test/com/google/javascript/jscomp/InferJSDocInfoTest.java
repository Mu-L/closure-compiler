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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link InferJSDocInfo}. */
// TODO(nicksantos): A lot of this code is duplicated from
// TypedScopeCreatorTest. We should create a common test harness for
// assertions about type information.
@RunWith(JUnit4.class)
public final class InferJSDocInfoTest extends CompilerTestCase {

  private static enum DeclarationKeyword {
    VAR("var"),
    LET("let"),
    CONST("const");

    private final String string;

    private DeclarationKeyword(String string) {
      this.string = string;
    }

    public String toJs() {
      return string;
    }
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);

        new TypeInferencePass(compiler, compiler.getReverseAbstractInterpreter(), scopeCreator)
            .inferAllScopes(root.getParent());

        new InferJSDocInfo(compiler).process(externs, root);
      }
    };
  }

  @Test
  public void testJSDocFromExternTypesIsPreserved() {
    // Given
    testSame(
        externs(
            """
            /**
             * I'm an Object.
             * @param {*=} x
             * @return {!Object}
             * @constructor
             */
            function Object(x) {};
            """),
        srcs("var x = new Object();"));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Object");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm an Object.");
  }

  @Test
  public void testJSDocFromInstanceNodesIsIgnored() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user type.
             * @constructor
             */
            function Foo() {};

            /** I'm a Foo instance */ // This should not be attached to a type.
            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocIsNotPropagatedToNativeFunctionType() {
    // Given
    testSame(
        srcs(
            """
            /**
            * I'm some function.
             * @type {!Function}
             */
            var x;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Function");

    // Then
    assertThat(xType.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocFromNamedFunctionPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user type.
             * @constructor
             */
            function Foo() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromNamedEs6ClassPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            """
            /** I'm a user type. */
            class Foo { };

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromGlobalAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user type.
             * @constructor
             */
            Foo = function() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromNamespacedNameAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            """
            var namespace = {}

            /**
             * I'm a user type.
             * @constructor
             */
            namespace.Foo = function() {};

            var x = new namespace.Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("namespace.Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromVarAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.VAR);
  }

  @Test
  public void testJSDocFromLetAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.LET);
  }

  @Test
  public void testJSDocFromConstAssignmentPropagatesToDefinedType() {
    testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword.CONST);
  }

  private void testJSDocFromVariableAssignmentPropagatesToDefinedType(DeclarationKeyword keyword) {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user type.
             * @constructor
             */
            KEYWORD Foo = function() {};

            // `var x` is just a hook to access type "Foo".
            var x = new Foo();
            """
                .replace("KEYWORD", keyword.toJs())));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromVariableNameAssignmentPropagatesToDefinedType() {
    // Given
    testSame(
        srcs(
            """
            var /**
             * I'm a user type.
             * @constructor
             */
            Foo = function() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocIsPropagatedToClasses_Es5() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user class.
             * @constructor
             */
            var Foo = function() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user class.");
  }

  @Test
  public void testJSDocIsPropagatedToClasses_Es6() {
    // Given
    testSame(
        srcs(
            """
            /** I'm a user class. */
            var Foo = class { };

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user class.");
  }

  @Test
  public void testJSDocIsPropagatedToCtors() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user class.
             * @constructor
             */
            var Foo = function() {};

            var x = Foo;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("(typeof Foo)");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user class.");
  }

  @Test
  public void testJSDocIsPropagatedToInterfaces() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user interface.
             * @interface
             */
            var Foo = function() {};

            var x = /** @type {!Foo} */ ({});
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user interface.");
  }

  @Test
  public void testJSDocIsPropagatedToRecords() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user record.
             * @record
             */
            var Foo = function() {};

            var x = /** @type {!Foo} */ ({});
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user record.");
  }

  @Test
  public void testJSDocIsPropagatedToEnums() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user enum.
             * @enum {number}
             */
            var Foo = {BAR: 0};

            var x = Foo;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("enum{Foo}");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user enum.");
  }

  @Test
  public void testJSDocIsPropagatedToEnumElements() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user enum.
             * @enum {number}
             */
            var Foo = {BAR: 0};

            var x = Foo.BAR;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo<number>");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user enum.");
  }

  @Test
  public void testJSDocIsPropagatedToFunctionTypes() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a custom function.
             * @param {*} a
             * @return {string}
             */
            function test(a) {};

            var x = test;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("function(*): string");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a custom function.");
  }

  @Test
  public void testJSDocIsPropagatedToScopedTypes() {
    // Given
    testSame(
        srcs(
            """
            (() => {
              /**
               * I'm a scoped user class.
               * @constructor
               */
              var Foo = function() {};

              var x = new Foo(); // Just a hook to access type "Foo".
            })();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a scoped user class.");
  }

  @Test
  public void testJSDocIsPropagatedToFunctionTypesFromMethodAssigments() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            /**
             * I'm a method.
             * @return {number} a
             */
            Foo.prototype.method = function() { };

            var x = Foo.prototype.method;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("function(this:Foo): number");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocIsNotPropagatedToFunctionTypesFromConstAliases() {
    // Given
    testSame(
        srcs(
            """
            var foo = function() {};

            /**
             * I'm an alias
             * @const
             */
            var x = foo;
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("function(): undefined");

    // Then
    assertThat(xType.getJSDocInfo()).isNull();
  }

  @Test
  public void testJSDocIsNotPropagatedToFunctionTypesFromPropertyAliases() {
    // Given
    testSame(
        srcs(
            """
            /**
             * @constructor
             * @param {function(*): string} callback
             */
            function Foo(callback) {

              /**
               * CB1
               * @private @const
               */
              this.callback1 = callback;
              /**
               * CB2
               * @private @const
               */
              this.callback2 = callback;
              /**
               * CB3
               * @private @const
               */
              this.callback3 = callback;

            };
            """));

    JSType callbackType = inferredTypeOfName("callback");
    ObjectType fooInstanceType = inferredTypeOfName("Foo").toMaybeFunctionType().getInstanceType();
    assertThat(callbackType.toString()).isEqualTo("function(*): string");

    // Then
    assertThat(callbackType.getJSDocInfo()).isNull();
    assertThat(fooInstanceType.getPropertyJSDocInfo("callback1").getBlockDescription())
        .isEqualTo("CB1");
    assertThat(fooInstanceType.getPropertyJSDocInfo("callback2").getBlockDescription())
        .isEqualTo("CB2");
    assertThat(fooInstanceType.getPropertyJSDocInfo("callback3").getBlockDescription())
        .isEqualTo("CB3");
  }

  @Test
  public void testJSDocItPropagatedToFunctionTypesFromPropertyAliases_withUniqueTypeDeclarations() {
    // Given
    testSame(
        srcs(
            """
            /**
             * @constructor
             * @param {function(*): string} callback
             */
            function Foo(callback) {

              /**
               * CB1
               * @private @const {function(string): string}
               */
              this.callback = callback;

            };
            """));

    JSType callbackType = inferredTypeOfName("callback");
    ObjectType fooInstanceType = inferredTypeOfName("Foo").toMaybeFunctionType().getInstanceType();
    FunctionType thisDotCallbackType =
        fooInstanceType.getPropertyType("callback").toMaybeFunctionType();

    assertThat(callbackType.toString()).isEqualTo("function(*): string");
    assertThat(thisDotCallbackType.toString()).isEqualTo("function(string): string");

    // Then
    // While `this.callback = callback` doesn't attach any JSDocInfo to the parameter
    // type `function(*): string`, it does attach JSDocInfo to the narrower function type
    // `function(string): string`.
    assertThat(callbackType.getJSDocInfo()).isNull();
    assertThat(fooInstanceType.getPropertyJSDocInfo("callback").getBlockDescription())
        .isEqualTo("CB1");
    assertThat(thisDotCallbackType.getJSDocInfo().getBlockDescription()).isEqualTo("CB1");
  }

  @Test
  public void testJSDocIsPropagatedDistinctlyToMatchingStructuralTypes_ObjectLiteralTypes() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm test0.
             */
            var test0 = {a: 4, b: 5};

            // The type of this object *looks* is the same, but it is different since the type
            // may get more properties later. Therefore, it should get a different JSDoc.
            /**
             * I'm test1.
             */
            var test1 = {a: 4, b: 5};
            """));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    // For some reason `test0Type` and `test1Type` aren't equal. This is good, but unexpected.
    assertThat(test1Type).isNotSameInstanceAs(test0Type);

    // Then
    assertThat(test0Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test0.");
    assertThat(test1Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test1.");
  }

  @Test
  public void testJSDocIsPropagatedDistinctlyToMatchingStructuralTypes_FunctionTypes() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm test0.
             * @param {*} a
             * @return {string}
             */
            function test0(a) {};

            // The type of this function *looks* is the same, but it is different since the type
            // may get more properties later. Therefore, it should get a different JSDoc.
            /**
             * I'm test1.
             * @param {*} a
             * @return {string}
             */
            function test1(a) {};
            """));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    // We really only care that they match, not about equality.
    // TODO(b/111070482): That fact that these are equal yet have different JSDocs is bad.
    assertThat(test1Type).isEqualTo(test0Type);
    assertThat(test1Type).isNotSameInstanceAs(test0Type);

    // Then
    assertThat(test0Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test0.");
    assertThat(test1Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test1.");
  }

  @Test
  public void testJSDocIsNotOverriddenByStructuralTypeAssignments_ObjectLiteralTypes() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm test0.
             */
            var test0 = {a: 4, b: 5};

            // The type of these objects should be identical.
            /**
             * I'm test1.
            // We need this so inferrence understands thes two objects/types are stuck together.
             * @const
             */
            var test1 = test0;
            """));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertThat(test1Type).isSameInstanceAs(test0Type);

    // Then
    assertThat(test0Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test0.");
  }

  @Test
  public void testJSDocIsNotOverriddenByStructuralTypeAssignments_FunctionTypes() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm test0.
             * @param {*} a
             * @return {string}
             */
            function test0(a) {};

            // The type of these objects should be identical.
            /**
             * I'm test1.
             * @param {string} a
             * @return {*}
            // We need this so inferrence understands thes two objects/types are stuck together.
             * @const
             */
            var test1 = test0;
            """));

    JSType test0Type = inferredTypeOfName("test0");
    JSType test1Type = inferredTypeOfName("test1");
    assertThat(test1Type).isSameInstanceAs(test0Type);

    // Then
    assertThat(test0Type.getJSDocInfo().getBlockDescription()).isEqualTo("I'm test0.");
  }

  @Test
  public void testJSDocIsPropagatedToFieldProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {
              /**
               * I'm a field.
               * @const
               */
               this.field = 5;
            };

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("field").getBlockDescription()).isEqualTo("I'm a field.");
  }

  @Test
  public void testJSDocIsPropagatedToFieldProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              constructor() {
                /**
                 * I'm a field.
                 * @const
                 */
                 this.field = 5;
              }
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("field").getBlockDescription()).isEqualTo("I'm a field.");
  }

  @Test
  public void testJSDocIsPropagatedToClassField() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a field.
               * @const
               */
               field = 5;
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("field").getBlockDescription()).isEqualTo("I'm a field.");
  }

  @Test
  public void testJSDocIsNotPropagatedFromSuppressionsOnFieldProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {
              /**
               * I'm a field.
               * @const
               */
               this.field = 5;
            };

            var x = new Foo(); // Just a hook to access type "Foo".
            /** @suppress {checkTypes} */
            x.field = 'test';
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("field").getBlockDescription()).isEqualTo("I'm a field.");
  }

  @Test
  public void testJSDocIsNotPropagatedFromSuppressionsOnEarlyRefToFieldProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @param {!Foo} x */
            function f(x) {
              /** @suppress {checkTypes} */
              x.field = 'test';
            }
            /** @constructor */
            function Foo() {
              /**
               * I'm a field.
               * @const
               */
               this.field = 5;
            };
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("field").getBlockDescription()).isEqualTo("I'm a field.");
  }

  @Test
  public void testJSDocIsPropagatedToGetterProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            Foo.prototype = {
              /**
               * I'm a getter.
               * @return {number}
               */
              get getter() {}
            };

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("getter").getBlockDescription())
        .isEqualTo("I'm a getter.");
  }

  @Test
  public void testJSDocIsPropagatedToGetterProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a getter.
               * @return {number}
               */
              get getter() {}
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("getter").getBlockDescription())
        .isEqualTo("I'm a getter.");
  }

  @Test
  public void testJSDocIsPropagatedToSetterProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            Foo.prototype = {
              /**
               * I'm a setter.
               * @param {number} a
               */
              set setter(a) {}
            };

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("setter").getBlockDescription())
        .isEqualTo("I'm a setter.");
  }

  @Test
  public void testJSDocIsPropagatedToSetterProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a setter.
               * @param {number} a
               */
              set setter(a) {}
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("setter").getBlockDescription())
        .isEqualTo("I'm a setter.");
  }

  @Test
  public void testJSDocIsPropagatedToMethodProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            /**
             * I'm a method.
             * @return {number} a
             */
            Foo.prototype.method = function() { };

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("method").getBlockDescription())
        .isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocIsPropagatedToMethodProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a method.
               * @return {number} a
               */
              method() { }
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("method").getBlockDescription())
        .isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocIsPropagatedToAbstractMethodProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            /**
             * I'm a method.
             * @abstract
             * @return {number} a
             */
            Foo.prototype.method = goog.abstractMethod;

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("method").getBlockDescription())
        .isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocIsPropagatedToAbstractMethodProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a method.
               * @return {number} a
               * @abstract
               */
              method() { }
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getPropertyJSDocInfo("method").getBlockDescription())
        .isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocIsPropagatedToStaticProperties_Es5() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            /**
             * I'm a static.
             * @return {number} a
             */
            Foo.static = function() { };

            var x = Foo;
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("(typeof Foo)");

    // Then
    assertThat(xType.getPropertyJSDocInfo("static").getBlockDescription())
        .isEqualTo("I'm a static.");
  }

  @Test
  public void testJSDocIsPropagatedToStaticProperties_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            class Foo {
              /**
               * I'm a static.
               * @return {number} a
               */
               static static() { }
            }

            var x = Foo;
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("(typeof Foo)");

    // Then
    assertThat(xType.getPropertyJSDocInfo("static").getBlockDescription())
        .isEqualTo("I'm a static.");
  }

  // TODO(b/30710701): Constructor docs should be used in some way. This is probably similar to how
  // access control is being differentiated between constructor invocation and constructors as
  // namespaces. The decision for both of these cases should be made together.
  @Test
  public void testJSDocFromConstructorsIsIgnored_Es6Class() {
    // Given
    testSame(
        srcs(
            """
            /** I'm a class. */
            class Foo {
              /** I'm a constructor. */
              constructor() { }
            }

            var x = new Foo();
            """));

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a class.");
    assertThat(xType.getPropertyJSDocInfo("constructor")).isNotNull();
  }

  @Test
  public void testJSDocDoesNotPropagateFromStructuralTypesToClassProperties() {
    // Given
    testSame(
        srcs(
            """
            /** @constructor */
            function Foo() {};

            /**
             * I'm a free function.
             * @return {number}
             */
            function free() { return 0; }

            /**
             * I'm a method.
             * @return {number} a
             */
            Foo.prototype.method = free;

            var x = new Foo();
            """));

    JSType freeType = inferredTypeOfName("free");
    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType).isNotSameInstanceAs(freeType);

    // Then
    assertThat(freeType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a free function.");
    assertThat(xType.getPropertyJSDocInfo("method").getBlockDescription())
        .isEqualTo("I'm a method.");
  }

  @Test
  public void testJSDocDoesNotPropagateBackwardFromInstancesToTypes() {
    // Given
    testSame(
        """
        /** @constructor */
        function Foo() {}

        var x = new Foo();

        /** @type {number} */
        x.bar = 4;
        """);

    ObjectType xType = (ObjectType) inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.hasProperty("bar")).isFalse();
    assertThat(xType.getOwnPropertyJSDocInfo("bar")).isNull();
  }

  @Test
  public void testJSDocFromDuplicateDefinitionDoesNotOverrideJSDocFromOriginal() {
    // Given
    testSame(
        srcs(
            """
            /**
             * I'm a user type.
             * @constructor
             */
            var Foo = function() {};

            /**
             * I'm a different type.
             * @constructor
             */
            Foo = function() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a user type.");
  }

  @Test
  public void testJSDocFromDuplicateDefinitionIsUsedIfThereWasNoOriginalJSDocFromOriginal() {
    // Given
    testSame(
        srcs(
            """
            var Foo = function() {};

            /**
             * I'm a different type.
             * @constructor
             */
            Foo = function() {};

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.getJSDocInfo().getBlockDescription()).isEqualTo("I'm a different type.");
  }

  @Test
  public void testJSDocFromDuplicatePropertyDefinitionDoesNotOverrideJSDoc_es6Style() {
    // Given
    testSame(
        srcs(
            """
            class Foo {

              /**
               * I'm the first definition
               * @param {string} s
               */
              x(s) {}

              /**
               * I'm the second definition
               * @param {string} s
               */
              x(s) {}
            }

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.assertObjectType().getPropertyJSDocInfo("x").getBlockDescription())
        .isEqualTo("I'm the first definition");
  }

  @Test
  public void testJSDocFromDuplicatePropertyDefinitionDoesNotOverrideJSDoc_es5Style() {
    // Given
    testSame(
        srcs(
            """
            /**
             * @constructor
             */
            var Foo = function() {};

            /**
             * I'm the first definition
             * @type {string}
             */
            Foo.prototype.x;

            /**
             * I'm the second definition
             * @type {string}
             */
            Foo.prototype.x;

            var x = new Foo();
            """));

    JSType xType = inferredTypeOfName("x");
    assertThat(xType.toString()).isEqualTo("Foo");

    // Then
    assertThat(xType.assertObjectType().getPropertyJSDocInfo("x").getBlockDescription())
        .isEqualTo("I'm the first definition");
  }

  @Test
  public void testJSDocIsPropagatedToTypeFromObjectLiteralPrototype() {
    testSame(
        """
        /** @constructor */
        function Foo() {}

        Foo.prototype = {
          /** Property a. */ a: function() {},
          /** Property b. */ get b() {},
          /** Property c. */ set c(x) {},
          /** Property d. */ d() {}
        };
        """);

    FunctionType ctor = inferredTypeOfName("Foo").toMaybeFunctionType();
    ObjectType prototype = ctor.getInstanceType().getImplicitPrototype();

    assertThat(prototype.getOwnPropertyJSDocInfo("a").getBlockDescription())
        .isEqualTo("Property a.");
    assertThat(prototype.getOwnPropertyJSDocInfo("b").getBlockDescription())
        .isEqualTo("Property b.");
    assertThat(prototype.getOwnPropertyJSDocInfo("c").getBlockDescription())
        .isEqualTo("Property c.");
    assertThat(prototype.getOwnPropertyJSDocInfo("d").getBlockDescription())
        .isEqualTo("Property d.");
  }

  @Test
  public void testClassWithUnknownTypeDoesNotCrash() {
    // Code that looks like this and @suppresses {checkTypes} is generated by tsickle.
    ignoreWarnings(TypeCheck.CONFLICTING_EXTENDED_TYPE);
    enableTypeCheck();
    testSame(
        """
        /** @param {?} base */
        function mixin(base) {
          return (/** @type {?} */ ((class extends base {
            /** @param {...?} args */ constructor(...args) {}
          })));
        }
        """);
  }

  /** Returns the inferred type of the reference {@code name} anywhere in the AST. */
  private JSType inferredTypeOfName(String name) {
    return inferredTypeOfLocalName(name, getLastCompiler().getRoot());
  }

  /** Returns the inferred type of the reference {@code name} under {@code root} AST node.. */
  private JSType inferredTypeOfLocalName(String name, Node root) {
    Deque<Node> queue = new ArrayDeque<>();
    queue.push(root);

    while (!queue.isEmpty()) {
      Node current = queue.pop();
      if (current.matchesQualifiedName(name) && current.getJSType() != null) {
        return current.getJSType();
      }

      for (Node child = current.getFirstChild(); child != null; child = child.getNext()) {
        queue.push(child);
      }
    }
    return null;
  }
}
