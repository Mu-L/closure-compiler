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

import static com.google.javascript.jscomp.CheckAccessControls.BAD_PACKAGE_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.CONST_PROPERTY_DELETED;
import static com.google.javascript.jscomp.CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.EXTEND_FINAL_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.FINAL_PROPERTY_OVERRIDDEN;
import static com.google.javascript.jscomp.CheckAccessControls.PRIVATE_OVERRIDE;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckAccessControls}.
 *
 * <p>This file is forked from {@link CheckAccessControlsOldSyntaxTest} because nearly all cases
 * there use the class syntax from before the `class` keyword was added to the language. Test cases
 * using ES5-style classes with the `@constructor`, `@interface`, or `@record` annotations should
 * remain in that file.
 */
@RunWith(JUnit4.class)
public final class CheckAccessControlsTest extends CompilerTestCase {

  public CheckAccessControlsTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableParseTypeInfo();
    enableClosurePass();
    enableRewriteClosureCode();
    enableCreateModuleMap();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckAccessControls(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.ERROR);
    return options;
  }

  @Test
  public void testOverrideAllowed() {
    testSame(
        """
        class Foo {
          /** @final*/ x;
          constructor() {
            this.x = 5;
          }
        }
        """);
  }

  @Test
  public void testMultipleFieldsOverrideAllowed() {
    testSame(
        """
        class Bar {
          /** @final*/ a;
          /** @final */ b;
          constructor() {
            this.a = 1;
            this.b = 2;
          }
        }
        """);
  }

  @Test
  public void testFieldOverrideNotAllowed() {
    testError(
        """
        class Bar {
          /** @final*/ a;
          constructor() {
            this.a = 1;
            this.a = 2;
          }
        }
        """,
        FINAL_PROPERTY_OVERRIDDEN);

    testError(
        """
        class Bar {
          /** @final*/ a = 5;
          constructor() {
            this.a = 1;
          }
        }
        """,
        FINAL_PROPERTY_OVERRIDDEN);
  }

  @Test
  public void testThisAssignmentInConstructor() {
    testSame(
        """
        class Foo {
          constructor() {
            /** @const {number} */ this.x;
            this.x = 4;
          }
        }
        """);
  }

  @Test
  public void testWarningInNormalClass() {
    test(
        srcs(
            """
            /** @deprecated FooBar */ function f() {}

            var Foo = class {
              bar() { f(); }
            };
            """),
        deprecatedName("Variable f has been deprecated: FooBar"));
  }

  @Test
  public void testWarningForProperty1() {
    test(
        srcs(
            """
            class Foo {
              baz() { alert((new Foo()).bar); }
            }
            /** @deprecated A property is bad */ Foo.prototype.bar = 3;
            """),
        deprecatedProp("Property bar of type Foo has been deprecated: A property is bad"));
  }

  @Test
  public void testWarningForProperty2() {
    test(
        srcs(
            """
            class Foo {
              baz() { alert(this.bar); }
            }
            /** @deprecated Zee prop, it is deprecated! */ Foo.prototype.bar = 3;
            """),
        deprecatedProp(
            "Property bar of type Foo has been deprecated: Zee prop, it is deprecated!"));
  }

  @Test
  public void testWarningForDeprecatedClass() {
    test(
        srcs(
            """
            /** @deprecated Use the class 'Bar' */
            class Foo {}

            function f() { new Foo(); }
            """),
        deprecatedClass("Class Foo has been deprecated: Use the class 'Bar'"));
  }

  @Test
  public void testWarningForDeprecatedClassNoReason() {
    test(
        srcs(
            """
            /** @deprecated */
            class Foo {}

            function f() { new Foo(); }
            """),
        error(DEPRECATED_CLASS));
  }

  @Test
  public void testNoWarningForDeprecatedClassInstance() {
    test(
        srcs(
            """
            /** @deprecated */
            class Foo {}

            /** @param {Foo} x */
            function f(x) { return x; }
            """));
  }

  @Test
  public void testNoWarningForDeprecatedSuperClass() {
    testNoWarning(
        srcs(
            """
            /** @deprecated Superclass to the rescue! */
            class Foo {}

            class SubFoo extends Foo {}

            function f() { new SubFoo(); }
            """));
  }

  @Test
  public void testNoWarningForDeprecatedSuperClassOnNamespace() {
    testNoWarning(
        srcs(
            """
            /** @deprecated Its only weakness is Kryptoclass */
            class Foo {}

            /** @const */
            var namespace = {};

            namespace.SubFoo = class extends Foo {};

            function f() { new namespace.SubFoo(); }
            """));
  }

  @Test
  public void testWarningForPrototypeProperty() {
    test(
        srcs(
            """
            class Foo {
              baz() { alert(Foo.prototype.bar); }
            }

            /** @deprecated It is now in production, use that one */ Foo.prototype.bar = 3;
            """),
        deprecatedProp(
            "Property bar of type Foo.prototype has been deprecated:"
                + " It is now in production, use that one"));
  }

  @Test
  public void testNoWarningForNumbers() {
    test(
        srcs(
            """
            class Foo {
              baz() { alert(3); }
            }

            /** @deprecated */ Foo.prototype.bar = 3;
            """));
  }

  @Test
  public void testWarningForMethod1() {
    test(
        srcs(
            """
            class Foo {
              /** @deprecated There is a madness to this method */
              bar() {}

              baz() { this.bar(); }
            }
            """),
        deprecatedProp(
            "Property bar of type Foo has been deprecated: There is a madness to this method"));
  }

  @Test
  public void testWarningForMethod2() {
    test(
        srcs(
            """
            class Foo {
              baz() { this.bar(); }
            }

            /** @deprecated Stop the ringing! */
            Foo.prototype.bar;
            """),
        deprecatedProp("Property bar of type Foo has been deprecated: Stop the ringing!"));
  }

  @Test
  public void testWarningOnClassField_deprecated() {
    test(
        srcs(
            """
            class Foo {
              /** @deprecated There is a madness to this method */
              bar = 2;
              baz() { return this.bar; }
            }
            """),
        deprecatedProp(
            "Property bar of type Foo has been deprecated: There is a madness to this method"));
  }

  @Test
  public void testNoWarningOnClassField_deprecated() {
    test(
        srcs(
            """
            class Foo {
              /**
               * @type {?}
               * @deprecated Use something else.
               */
              x = 2;
            }
            """));
  }

  @Test
  public void testNoWarningOnClassField_deprecated1() {
    // TODO(b/239747805): Should throw deprecatedProp warning
    test(
        srcs(
            """
            class Foo {
              /**
               * @type {number}
               * @deprecated No.
               */
              static x=1;
            }
            Foo.x;
            """));
  }

  @Test
  public void testNoWarningOnClassField_deprecated2() {
    // TODO(b/239747805): Should say "Property x of type number" not "super"
    test(
        srcs(
            """
            class Foo {
              /**
               * @type {number}
               * @deprecated No.
               */
              static x = 1;
            }
            class Bar extends Foo {
              static y = super.x;
            }
            """),
        deprecatedProp("Property x of type super has been deprecated: No."));
  }

  @Test
  public void testNoWarningInDeprecatedClass() {
    test(
        srcs(
            """
            /** @deprecated */
            function f() {}

            /** @deprecated */
            var Foo = class {
              bar() { f(); }
            };
            """));
  }

  @Test
  public void testNoWarningOnDeclaration() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /**
                 * @type {number}
                 * @deprecated Use something else.
                 */
                this.code;
              }
            }
            """));
  }

  @Test
  public void testNoWarningInDeprecatedClass2() {
    test(
        srcs(
            """
            /** @deprecated */
            function f() {}

            /** @deprecated */
            var Foo = class {
              static bar() { f(); }
            };
            """));
  }

  @Test
  public void testNoWarningInDeprecatedStaticMethod() {
    test(
        srcs(
            """
            /** @deprecated */
            function f() {}

            var Foo = class {
              /** @deprecated */
              static bar() { f(); }
            };
            """));
  }

  @Test
  public void testWarningInStaticMethod() {
    test(
        srcs(
            """
            /** @deprecated crazy! */
            function f() {}

            var Foo = class {
              static bar() { f(); }
            };
            """),
        deprecatedName("Variable f has been deprecated: crazy!"));
  }

  @Test
  public void testWarningInClassStaticBlock() {
    test(
        srcs(
            """
            /** @deprecated Oh No! */
            function f() {}

            class Foo {
              static { f(); }
            }
            """),
        deprecatedName("Variable f has been deprecated: Oh No!"));
  }

  @Test
  public void testWarningInClassStaticBlock1() {
    test(
        srcs(
            """
            /** @deprecated Woah! */
            var x;

            class Foo {
              static { x; }
            }
            """),
        deprecatedName("Variable x has been deprecated: Woah!"));
  }

  @Test
  public void testWarningInClassStaticBlock2() {
    test(
        srcs(
            """
            class Foo {
              /** @deprecated Bad :D */
               static x = 1;
               static {
                  var y = this.x;
               }
            }
            """),
        deprecatedProp("Property x of type this has been deprecated: Bad :D"));
  }

  @Test
  public void testNoWarningInClassStaticBlock() {
    // TODO(b/235871861): Compiler should throw warning as it doesn't make sense to deprecate a
    // static block
    test(
        srcs(
            """
            class Foo {
              /** @deprecated D: */
              static {
              }
            }
            """));
  }

  @Test
  public void testNoWarningInClassStaticBlock1() {
    // TODO(b/239747805): Should say "Property x of type number" not "super"
    test(
        srcs(
            """
            class A {
              /** @deprecated Meow! */
              static x = 1;
            }
            class B extends A {
              static {
                this.y = super.x;
              }
            }
            """),
        deprecatedProp("Property x of type super has been deprecated: Meow!"));
  }

  @Test
  public void testWarningForSubclassMethod() {
    test(
        srcs(
            """
            class Foo {
              bar() {}
            }

            class SubFoo extends Foo {
              /**
               * @override
               * @deprecated I have a parent class!
               */
              bar() {}
            }

            function f() { (new SubFoo()).bar(); }
            """),
        deprecatedProp("Property bar of type SubFoo has been deprecated: I have a parent class!"));
  }

  @Test
  public void testWarningForSuperClassWithDeprecatedSubclassMethod() {
    test(
        srcs(
            """
            class Foo {
              bar() {}
            }

            class SubFoo extends Foo {
              /**
               * @override
               * @deprecated I have a parent class!
               */
              bar() {}
            }

            function f() { (new Foo()).bar(); };
            """));
  }

  @Test
  public void testWarningForSuperclassMethod() {
    test(
        srcs(
            """
            class Foo {
              /** @deprecated I have a child class! */
              bar() {}
            }

            class SubFoo extends Foo {
              /** @override */
              bar() {}
            }

            function f() { (new SubFoo()).bar(); };
            """),
        deprecatedProp("Property bar of type SubFoo has been deprecated: I have a child class!"));
  }

  @Test
  public void testWarningForSuperclassMethod2() {
    test(
        srcs(
            """
            class Foo {
              /**
               * @protected
               * @deprecated I have another child class...
               */
              bar() {}
            }

            class SubFoo extends Foo {
              /**
               * @protected
               * @override
               */
              bar() {}
            }

            function f() { (new SubFoo()).bar(); };
            """),
        deprecatedProp(
            "Property bar of type SubFoo has been deprecated: I have another child class..."));
  }

  @Test
  public void testWarningForDeprecatedClassInGlobalScope() {
    test(
        srcs(
            """
            /** @deprecated I'm a very worldly object! */
            var Foo = class {};

            new Foo();
            """),
        deprecatedClass("Class Foo has been deprecated: I'm a very worldly object!"));
  }

  @Test
  public void testNoWarningForPrototypeCopying() {
    test(
        srcs(
            """
            var Foo = class {
              bar() {}
            };

            /** @deprecated */
            Foo.prototype.baz = Foo.prototype.bar;

            (new Foo()).bar();
            """));
  }

  @Test
  public void testNoWarningOnDeprecatedPrototype() {
    test(
        srcs(
            """
            var Foo = class {};

            /** @deprecated */
            Foo.prototype;
            """));
  }

  @Test
  public void testPrivateAccessForProperties1() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}

              baz() { this.bar_(); }
            }

            (new Foo).bar_();
            """));
  }

  @Test
  public void testPrivateAccessForProperties2() {
    test(
        srcs(
            "class Foo {}",
            """
            Foo.prototype.bar_ = function() {};
            Foo.prototype.baz = function() { this.bar_(); };

            (new Foo).bar_();
            """));
  }

  @Test
  public void testPrivateAccessForProperties3() {
    // Even though baz is "part of the Foo class" the access is disallowed since it's
    // not in the same file.
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }
            """,
            "Foo.prototype.baz = function() { this.bar_(); };"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testPrivateAccessForProperties4() {
    test(
        srcs(
            """
            /** @unrestricted */
            class Foo {
              /** @private */
              bar_() {}

              ['baz']() { (new Foo()).bar_(); }
            }
            """));
  }

  @Test
  public void testPrivateAccessForProperties5() {
    test(
        srcs(
            """
            class Parent {
              constructor() {
                /** @private */
                this.prop = 'foo';
              }
            };
            """,
            """
            class Child extends Parent {
              constructor() {
                this.prop = 'asdf';
              }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of Parent not allowed here."));
  }

  @Test
  public void testPrivatePropAccess_inSameFile_throughDestructuring() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() { }
            }

            function f(/** !Foo */ x) {
              const {bar_: bar} = x;
            }
            """));
  }

  @Test
  public void testPrivateAccessForProperties6() {
    test(
        srcs(
            """
            goog.provide('x.y.z.Parent');

            x.y.z.Parent = class {
              constructor() {
                /** @private */
                this.prop = 'foo';
              }
            };
            """,
            """
            goog.require('x.y.z.Parent');

            class Child extends x.y.z.Parent {
              constructor() {
                this.prop = 'asdf';
              }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of x.y.z.Parent not allowed here."));
  }

  @Test
  public void testPrivateAccessToConstructorThroughNew() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }

            new Foo();
            """));
  }

  @Test
  public void testPrivateAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }

            class SubFoo extends Foo { }
            """));
  }

  @Test
  public void testPrivateAccessToClass() {
    test(
        srcs(
            """
            /** @private */
            class Foo { }

            Foo;
            """));
  }

  @Test
  public void testPrivateAccess_googModule() {
    test(
        srcs(
            """
            goog.module('example.One');

            class One {
              /** @private */
              m() {}
            }

            exports = One;
            """,
            """
            goog.module('example.two');

            const One = goog.require('example.One');

            (new One()).m();
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property m of One not allowed here."));
  }

  @Test
  public void testNoPrivateAccessForProperties1() {
    test(
        srcs(
            """
            class Foo {};
            (new Foo).bar_();
            """,
            """
            /** @private */
            Foo.prototype.bar_ = function() {};

            Foo.prototype.baz = function() { this.bar_(); };
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties2() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}

              baz() { this.bar_(); }
            }
            """,
            "(new Foo).bar_();"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties3() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }
            """,
            """
            class OtherFoo {
              constructor() {
                (new Foo).bar_();
              }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties4() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }
            """,
            """
            class SubFoo extends Foo {
              constructor() {
                this.bar_();
              }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties5() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }
            """,
            """
            class SubFoo extends Foo {
              baz() { this.bar_(); }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties6() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }
            """,
            """
            class SubFoo extends Foo {
              bar_() {}
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties6a() {
    // Same as above, except with namespaced constructors
    test(
        srcs(
            """
            /** @const */ var ns = {};

            ns.Foo = class {
              /** @private */
              bar_() {}
            };
            """,
            """
            ns.SubFoo = class extends ns.Foo {
              bar_() {}
            };
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() {}
            }

            class SubFoo extends Foo {
              bar_() {}
            }
            """,
            "SubFoo.prototype.baz = function() { this.bar_(); }"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties8() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @private */
                this.bar_ = 3;
              }
            }
            """,
            """
            class SubFoo extends Foo {
              constructor() {
                /** @private */
                this.bar_ = 3;
              }
            }
            """),
        error(PRIVATE_OVERRIDE));
  }

  @Test
  public void testNoPrivateAccessForProperties9() {
    test(
        srcs(
            """
            class Foo {}

            /** @private */
            Foo.prototype.bar_ = 3;
            """,
            "new Foo().bar_;"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties10() {
    test(
        srcs(
            """
            class Foo {}

            /** @private */
            Foo.prototype.bar_ = function() {};
            """,
            "new Foo().bar_;"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties11() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              get bar_() { return 1; }
            }
            """,
            "var a = new Foo().bar_;"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties12() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              set bar_(x) { this.barValue = x; }
            }
            """,
            "new Foo().bar_ = 1;"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties13() {
    // Visibility of getter and setter differ.
    var fooFileSrc =
        """
        class Foo {
          constructor() {
            /** @private */
            this.barValue_ = 1;
          }

          /**
           * @param {number} x
           * @private
           */
          set bar(x) {
            this.barValue_ = x;
          }

          /** @return {number} */
          get bar() {
            return this.barValue_;
          }
        }
        """;

    // TODO: b/416023147 - This should not be an error.
    test(srcs(fooFileSrc, "const barCopy = new Foo().bar;"), error(BAD_PRIVATE_PROPERTY_ACCESS));

    // This is fine.
    test(srcs(fooFileSrc, "new Foo().bar = 2;"), error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivatePropAccess_inDifferentFile_throughDestructuring() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar_() { }
            }
            """,
            """
            function f(/** !Foo */ x) {
              const {bar_: bar} = x;
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToConstructorThroughNew() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }
            """,
            "new Foo();"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }
            """,
            "class SubFoo extends Foo { }"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToClass() {
    test(
        srcs(
            """
            /** @private */
            class Foo { }
            """,
            "Foo"),
        error(BAD_PRIVATE_GLOBAL_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForFields() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar
            }
            """,
            """
            class SubFoo extends Foo {
              bar
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForFields2() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar
            }
            """,
            "new Foo().bar"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForFields3() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar = 2
            }
            """,
            "new Foo().bar = 4"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testPrivateAccessForStaticBlock() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              static bar = 2
              static {
                this.bar = 4;
              }
            }
            """));
  }

  @Test
  public void testNoPrivateAccessForStaticBlock() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              static bar = 2;
            }
            """,
            """
            class Bar extends Foo{
              static {
                this.bar = 4;
              }
            }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testProtectedAccessForProperties1() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }

            (new Foo).bar();
            """,
            "Foo.prototype.baz = function() { this.bar(); };"));
  }

  @Test
  public void testProtectedAccessForProperties2() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }

            (new Foo).bar();
            """,
            """
            class SubFoo extends Foo {
              constructor() {
                this.bar();
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties3() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }

            (new Foo).bar();
            """,
            """
            class SubFoo extends Foo {
              static baz() { (new Foo()).bar(); }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties4() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              static foo() { super.bar(); }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties5() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ bar() {}
            }

            (new Foo).bar();
            """,
            """
            var SubFoo = class extends Foo {
              constructor() {
                super();
                this.bar();
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties6() {
    test(
        srcs(
            """
            goog.Foo = class {
              /** @protected */
              bar() {}
            }
            """,
            """
            goog.SubFoo = class extends goog.Foo {
              constructor() {
                super();
                this.bar();
              }
            };
            """));
  }

  @Test
  public void testProtectedAccessForProperties7() {
    test(
        srcs(
            """
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            var SubFoo = class extends Foo {
              constructor() {
                super();
                this.bar();
              }
            };

            SubFoo.prototype.moo = function() { this.bar(); };
            """));
  }

  @Test
  public void testProtectedAccessForProperties8() {
    test(
        srcs(
            """
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            var SubFoo = class extends Foo {
              get moo() { this.bar(); }
            };
            """));
  }

  @Test
  public void testProtectedAccessForProperties9() {
    test(
        srcs(
            """
            /** @unrestricted */
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            /** @unrestricted */
            var SubFoo = class extends Foo {
              set moo(val) { this.x = this.bar(); }
            };
            """));
  }

  @Test
  public void testProtectedAccessForProperties10() {
    test(
        srcs(
            SourceFile.fromCode(
                "foo.js",
                """
                var Foo = class {
                  /** @protected */
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                "sub_foo.js",
                """
                var SubFoo = class extends Foo {};

                (function() {
                  SubFoo.prototype.baz = function() { this.bar(); }
                })();
                """)));
  }

  @Test
  public void testProtectedAccessForProperties11() {
    test(
        srcs(
            SourceFile.fromCode(
                "foo.js",
                """
                goog.provide('Foo');

                /** @interface */
                Foo = class {};

                /** @protected */
                Foo.prop = {};
                """),
            SourceFile.fromCode(
                "bar.js",
                """
                goog.require('Foo');

                /** @implements {Foo} */
                class Bar {
                  constructor() {
                    Foo.prop;
                  }
                };
                """)));
  }

  @Test
  public void testProtectedAccessForProperties12() {
    test(
        srcs(
            SourceFile.fromCode(
                "a.js",
                """
                goog.provide('A');

                var A = class {
                  constructor() {
                    /** @protected {string} */
                    this.prop;
                  }
                };
                """),
            SourceFile.fromCode(
                "b.js",
                """
                goog.require('A');

                var B = class extends A {
                  constructor() {
                    super();

                    this.prop.length;
                  }
                };
                """)));
  }

  // FYI: Java warns for the b1.method access in c.js.
  // Instead of following that in NTI, we chose to follow the behavior of
  // the old JSCompiler type checker, to make migration easier.
  @Test
  public void testProtectedAccessForProperties13() {
    test(
        srcs(
            SourceFile.fromCode(
                "a.js",
                """
                goog.provide('A');

                var A = class {
                  /** @protected */
                  method() {}
                }
                """),
            SourceFile.fromCode(
                "b1.js",
                """
                goog.require('A');

                goog.provide('B1');

                var B1 = class extends A {
                  /** @override */
                  method() {}
                }
                """),
            SourceFile.fromCode(
                "b2.js",
                """
                goog.require('A');

                goog.provide('B2');

                var B2 = class extends A {
                  /** @override */
                  method() {}
                };
                """),
            SourceFile.fromCode(
                "c.js",
                """
                goog.require('B1');
                goog.require('B2');

                var C = class extends B2 {
                  /** @param {!B1} b1 */
                  constructor(b1) {
                    var x = b1.method();
                  }
                };
                """)));
  }

  @Test
  public void testProtectedAccessForProperties14() {
    // access in member function
    test(
        srcs(
            """
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            var OtherFoo = class extends Foo {
              constructor() {
                super();

                this.bar();
              }
            };

            OtherFoo.prototype.moo = function() { new Foo().bar(); };
            """));
  }

  @Test
  public void testProtectedAccessForProperties15() {
    // access in computed member function
    test(
        srcs(
            """
            /** @unrestricted */
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            /** @unrestricted */
            var OtherFoo = class extends Foo {
              constructor() {
                super();
                this['bar']();
              };
            }

            OtherFoo.prototype['bar'] = function() { new Foo().bar(); };
            """));
  }

  @Test
  public void testProtectedAccessForProperties16() {
    // access in nested arrow function
    test(
        srcs(
            """
            class Foo {
              /** @protected */ bar() {}
            }
            """,
            """
            class OtherFoo extends Foo {
              constructor() { super(); var f = () => this.bar(); }
              baz() { return () => this.bar(); }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties17() {
    // Access in subclass field initializer where Subclass parent is SCRIPT.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            class Subclass extends SuperClass {
              numField = this.getNum();
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties18() {
    // Access in subclass field initializer where Subclass parent is BLOCK.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            {
              class Subclass extends SuperClass {
                numField = this.getNum();
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties19() {
    // Access in subclass field initializer where class parent is ASSIGN.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            const Subclass = class extends SuperClass {
              numField = this.getNum();
            }
            """));
  }

  @Test
  public void testProtectedAccessForProperties20() {
    // Access in subclass field initializer where class parent is FUNCTION.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            const Subclass = (() => class extends SuperClass {
              numField = this.getNum();
            })();
            """));
  }

  @Test
  public void testProtectedAccessForProperties21() {
    // Access in subclass field initializer where class parent is RETURN.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            function classGenerator() {
              return class extends SuperClass {
                numField = this.getNum();
              };
            }
            const Subclass = classGenerator();
            """));
  }

  @Test
  public void testProtectedAccessForProperties22() {
    // Access in subclass field initializer where class parent is GETPROP.
    test(
        srcs(
            """
            class SuperClass {
              /** @protected @return {number} */
              getNum() { return 1; }
            }
            """,
            """
            function getValue() {
              return class extends SuperClass {
                numField = this.getNum();
                static staticField = 1;
              }.staticField;
            }
            const value = getValue();
            """));
  }

  @Test
  public void testProtectedPropAccess_inDifferentFile_inSubclass_throughDestructuring() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() { }
            }
            """,
            """
            class SubFoo extends Foo {
              method(/** !Foo */ x) {
                const {bar: bar} = x;
              }
            }
            """));
  }

  @Test
  public void testNoProtectedAccess_forOverriddenProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() { }
            }
            """,
            """
            class Bar extends Foo {
              /** @override */
              bar() { }
            }

            // TODO(b/113705099): This should be legal.
            (new Bar()).bar();
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testProtectedAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ constructor() {}
            }
            """,
            "class OtherFoo extends Foo { }"));
  }

  @Test
  public void testProtectedAccessToConstructorThroughSubclassInstanceMethod() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ constructor() {}
            }
            """,
            """
            class OtherFoo extends Foo {
              bar() { new Foo(); }
            }
            """));
  }

  @Test
  public void testProtectedAccessToClassThroughSubclassInstanceMethod() {
    test(
        srcs(
            """
            /** @protected */
            class Foo {}
            """,
            """
            class OtherFoo extends Foo {
              bar() { Foo; }
            }
            """));
  }

  @Test
  public void testProtectedAccessThroughNestedFunction() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ bar() {}
            }
            """,
            """
            class Bar extends Foo {
              constructor(/** Foo */ foo) {
                function f() {
                  foo.bar();
                }
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessThroughNestedEs5Class() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ bar() {}
            }
            """,
            """
            class Bar extends Foo {
              constructor() {
                /** @constructor */
                const Nested = function() {}

                /** @param {!Foo} foo */
                Nested.prototype.qux = function(foo) {
                  foo.bar();
                }
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessThroughNestedEs6Class() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */ bar() {}
            }
            """,
            """
            class Bar extends Foo {
              constructor() {
                class Nested {
                  qux(/** Foo */ foo) {
                    foo.bar();
                  }
                }
              }
            }
            """));
  }

  @Test
  public void testNoProtectedAccessForProperties1() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }
            """,
            "(new Foo).bar();"),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties2() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }
            """,
            """
            class OtherFoo {
              constructor() {
                (new Foo).bar();
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties3() {
    test(
        srcs(
            """
            class Foo {}

            class SubFoo extends Foo {
              /** @protected */
              bar() {}
            }
            """,
            """
            class SubberFoo extends Foo {
              constructor() {
                (new SubFoo).bar();
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties4() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                (new SubFoo).bar();
              }
            }
            """,
            """
            class SubFoo extends Foo {
              /** @protected */
              bar() {}
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties5() {
    test(
        srcs(
            """
            goog.Foo = class {
              /** @protected */
              bar() {}
            }
            """,
            """
            goog.NotASubFoo = class {
              constructor() {
                (new goog.Foo).bar();
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties6() {
    test(
        srcs(
            """
            class Foo {}

            /** @protected */
            Foo.prototype.bar = 3;
            """,
            "new Foo().bar;"),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties7() {
    test(
        srcs(
            """
            class Foo {}

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            "new Foo().bar();"),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties8() {
    test(
        srcs(
            """
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            var OtherFoo = class {
              constructor() {
                this.bar();
              }
            }

            OtherFoo.prototype.moo = function() { new Foo().bar(); };
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties9() {
    test(
        srcs(
            """
            var Foo = class {};

            /** @protected */
            Foo.prototype.bar = function() {};
            """,
            """
            var OtherFoo = class {
              constructor() { this['bar'](); }
            };

            OtherFoo.prototype['bar'] = function() { new Foo().bar(); };
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedPropAccess_inDifferentFile_throughDestructuring() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() { }
            }
            """,
            """
            function f(/** !Foo */ x) {
              const {bar: bar} = x;
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccess_forInheritedProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() { }
            }
            """,
            """
            class Bar extends Foo { }

            (new Bar()).bar();
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessToConstructorFromUnrelatedClass() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              constructor() {}
            }
            """,
            """
            class OtherFoo {
              bar() { new Foo(); }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForPropertiesWithNoRhs() {
    test(
        srcs(
            """
            class Foo {}

            /** @protected */
            Foo.prototype.x;
            """,
            """
            class Bar extends Foo {}

            /** @protected */
            Bar.prototype.x;
            """));
  }

  @Test
  public void testNoProtectedAccessForFields1() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar
            }
            """,
            "(new Foo).bar;"),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForFields2() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar
            }
            """,
            """
            class OtherFoo {
              constructor() {
                (new Foo).bar;
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForFields3() {
    test(
        srcs(
            """
            class Foo {}

            class SubFoo extends Foo {
              /** @protected */
              bar
            }
            """,
            """
            class SubberFoo extends Foo {
              constructor() {
                (new SubFoo).bar;
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForFields4() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                (new SubFoo).bar;
              }
            }
            """,
            """
            class SubFoo extends Foo {
              /** @protected */
              bar
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForFields5() {
    test(
        srcs(
            """
            goog.Foo = class {
              /** @protected */
              bar
            }
            """,
            """
            goog.NotASubFoo = class {
              constructor() {
                (new goog.Foo).bar;
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testProtectedAccessForStaticBlocks() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar
              static {
                this.bar;
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForStaticBlocks_sameFile() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar
            }
            class SubFoo extends Foo {
              static {
                this.bar;
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForStaticBlocks_sameFile1() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar
            }
            class Bar {
              static {
                Foo.bar;
              }
            }
            """));
  }

  @Test
  public void testProtectedAccessForStatic_differentFile() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar
            }
            """,
            """
            class SubFoo extends Foo {
              static {
                this.bar;
              }
            }
            """));
  }

  @Test
  public void testNoProtectedAccessForStaticBlocks_differentFile() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              static bar
            }
            """,
            """
            class Bar {
              static {
                Foo.bar;
              }
            }
            """),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testPackagePrivateAccessForProperties1() {
    test(
        srcs(
            """
            class Foo {
              /** @package */
              bar() {}

              baz() { this.bar(); }
            }

            (new Foo).bar();
            """));
  }

  @Test
  public void testPackagePrivateAccessForProperties2() {
    test(
        srcs(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"), "class Foo {}"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                /** @package */
                Foo.prototype.bar = function() {};

                Foo.prototype.baz = function() { this.bar(); };

                (new Foo).bar();
                """)));
  }

  @Test
  public void testPackagePrivateAccessForProperties3() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }

                (new Foo).bar();
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "baz.js"),
                "Foo.prototype.baz = function() { this.bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties4() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "baz.js"),
                "Foo.prototype['baz'] = function() { (new Foo()).bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties5() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Parent {
                  constructor() {
                    /** @package */
                    this.prop = 'foo';
                  }
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                class Child extends Parent {
                  constructor() {
                    this.prop = 'asdf';
                  }
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackagePropAccess_inSamePackage_throughDestructuring() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() { }
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"),
                """
                function f(/** !Foo */ x) {
                  const {bar: bar} = x;
                }
                """)));
  }

  @Test
  public void testNoPackagePropAccess_inDifferentPackage_throughDestructuring() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() { }
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                function f(/** !Foo */ x) {
                  const {bar: bar} = x;
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageAccessToConstructorThroughNew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "new Foo();")));
  }

  @Test
  public void testPackageAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "class SubFoo extends Foo { }")));
  }

  @Test
  public void testPackageAccessToClass() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @package */
                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testPackageAccessToConstructorThroughNew_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                class Foo {
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "new Foo();")));
  }

  @Test
  public void testPackageAccessToConstructorThroughExtendsClause_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                class Foo {
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "class SubFoo extends Foo { }")));
  }

  @Test
  public void testPackageAccessToClass_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties1() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {}

                (new Foo).bar();
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                /** @package */
                Foo.prototype.bar = function() {};

                Foo.prototype.baz = function() { this.bar(); };
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties2() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}

                  baz() { this.bar(); };
                }
                """),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "(new Foo).bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties3() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {};
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                class OtherFoo {
                  constructor() {
                    (new Foo).bar();
                  }
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties4() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                class SubFoo extends Foo {
                  constructor() {
                    this.bar();
                  }
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties5() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                class SubFoo extends Foo {
                  baz() { this.bar(); }
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                class SubFoo extends Foo {
                  bar() {}
                }
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  bar() {}
                }

                class SubFoo extends Foo {
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "SubFoo.prototype.baz = function() { this.bar(); }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackgeAccessToConstructorThroughNew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                class Foo {
                  /** @package */
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "class SubFoo extends Foo { }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToClass() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @package */
                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackgeAccessToConstructorThroughNew_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                /** @public */
                class Foo {
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToConstructorThroughExtendsClause_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                /** @public */
                class Foo {
                  constructor() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "class SubFoo extends Foo { }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToClass_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /** @fileoverview @package */

                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_OneFile() {
    test(
        srcs(
            """
            /**
             * @fileoverview
             * @package
             */

            Foo = class {
              /** @private */
              privateMethod_() {}
            }

            Bar = class extends Foo {
              /** @override */
              privateMethod_() {}
            }
            """),
        error(BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY));
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    test(
        srcs(
            """
            Foo = class {
              /** @protected */
              protectedMethod() {}
            };
            """,
            """
            /**
             * @fileoverview
             * @package
             */

            Bar = class extends Foo {
              /** @override */
              protectedMethod() {}
            };
            """),
        error(BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY));
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewOk() {
    test(
        srcs(
            """
            Foo = class {
              /** @private */
              privateMethod_() {}
            };

            Bar = class extends Foo {
              /** @override */
              privateMethod_() {}
            };
            """));
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewVisibilityOk() {
    test(
        srcs(
            """
            /**
             * @fileoverview
             */

            Foo = class {
              /** @private */
              privateMethod_() {}
            };

            Bar = class extends Foo {
              /** @override */
              privateMethod_() {}
            };
            """));
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_OneFile() {
    test(
        srcs(
            """
            /**
             * @fileoverview
             * @package
             */

            Foo = class {
              /** @private */
              privateMethod_() {};
            };

            Bar = class extends Foo {
              /** @override @private */
              privateMethod_() {};
            };
            """));
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_TwoFiles() {
    test(
        srcs(
            """
            Foo = class {
              /** @protected */
              protectedMethod() {}
            };
            """,
            """
            /**
             * @fileoverview
             * @package
             */

            Bar = class extends Foo {
              /** @override @protected */
              protectedMethod() {}
            };
            """));
  }

  @Test
  public void testConstructorVisibility_canBeNarrowed() {
    test(
        srcs(
            """
            class Foo {
              /** @public */
              constructor() {}
            };
            """,
            """
            class Bar extends Foo {
              /** @private */
              constructor() {}
            };
            """));
  }

  @Test
  public void testConstructorVisibility_canBeExpanded() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              constructor() {}
            };
            """,
            """
            class Bar extends Foo {
              /** @public */
              constructor() {}
            };
            """));
  }

  @Test
  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @public
                 */

                /** @package */
                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityDoesNotApplyToNameWithExplicitPublicVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */
                /** @public */
                class Foo {}
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                var Foo = class {};
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility_googModule() {
    disableRewriteClosureCode();
    testError(
        srcs(
            ImmutableList.of(
                SourceFile.fromCode(
                    Compiler.joinPathParts("foo", "bar.js"),
                    """
                    /**
                     * @fileoverview
                     * @package
                     */
                    goog.module('Foo');
                    class Foo {}
                    exports = Foo;
                    """),
                SourceFile.fromCode(
                    Compiler.joinPathParts("baz", "quux.js"),
                    "goog.module('client'); const Foo = goog.require('Foo'); new Foo();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility_esModule() {
    testError(
        srcs(
            ImmutableList.of(
                SourceFile.fromCode(
                    Compiler.joinPathParts("foo", "bar.js"),
                    """
                    /**
                     * @fileoverview
                     * @package
                     */
                    class Foo {}
                    export {Foo};
                    """),
                SourceFile.fromCode(
                    Compiler.joinPathParts("baz", "quux.js"),
                    "import {Foo} from '/foo/bar.js'; new Foo();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void
      testPackageFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPublicVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                /** @public */ // The class must be visible.
                Foo = class {
                  /** @public */ // The constructor must be visible.
                  constructor() { }

                  /** @public */
                  bar() {}
                };
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                var foo = new Foo();
                foo.bar();
                """)));
  }

  @Test
  public void
      testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @public
                 */

                Foo = class {
                  /** @package */
                  bar() {}
                };
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                var foo = new Foo();
                foo.bar();
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPublicFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @public
                 */

                Foo = class {
                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                var foo = new Foo();
                foo.bar();
                """)));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                /** @public */ // The class must be visible.
                Foo = class {
                  /** @public */ // The constructor must be visible.
                  constructor() { }

                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                var foo = new Foo();
                foo.bar();
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                /** @public */ // The class must be visible.
                Foo = class {
                  /** @public */ // The constructor must be visible.
                  constructor() { }

                  bar() {}
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                """
                /**
                 * @fileoverview
                 * @public
                 */

                var foo = new Foo();
                foo.bar();
                """)),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testImplicitSubclassConstructor_doesNotInheritVisibility_andIsPublic() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }

            // The implict constructor for `SubFoo` should be treated as public.
            class SubFoo extends Foo { }
            """,
            // So we get no warning for using it here.
            "new SubFoo();"));
  }

  @Test
  public void testImplicitSubclassConstructor_doesNotInheritVisibility_andUsesFileOverview() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                class Foo {
                  /** @private */
                  constructor() { }
                }

                // The implict constructor for `SubFoo` should be trated as package.
                /** @public */ // The class must be visible.
                class SubFoo extends Foo { }
                """),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new SubFoo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testUnannotatedSubclassConstructor_doesNotInheritVisibility_andIsPublic() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              constructor() { }
            }

            class SubFoo extends Foo {
            // The unannotated constructor for `SubFoo` should be treated as public.
              constructor() {
                super();
              }
            }
            """,
            // So we get no warning for using it here.
            "new SubFoo();"));
  }

  @Test
  public void testUnannotatedSubclassConstructor_doesNotInheritVisibility_andUsesFileOverview() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                """
                /**
                 * @fileoverview
                 * @package
                 */

                class Foo {
                  /** @private */
                  constructor() { }
                }

                /** @public */ // The class must be visible.
                class SubFoo extends Foo {
                // The unannotated constructor for `SubFoo` should be treated as package.
                  constructor() {
                    super();
                  }
                }
                """),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "new SubFoo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoExceptionsWithBadConstructors1() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                (new Bar).bar();
              }
            }

            class Bar {
              /** @protected */
              bar() {}
            }
            """));
  }

  @Test
  public void testNoExceptionsWithBadConstructors2() {
    test(
        srcs(
            """
            class Foo {
              bar() {}
            }

            class Bar {
              /** @protected */
              bar() { (new Foo).bar(); }
            }
            """));
  }

  @Test
  public void testGoodOverrideOfProtectedProperty() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              /** @inheritDoc */
              bar() {}
            }
            """));
  }

  @Test
  public void testPublicOverrideOfProtectedProperty() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              /** @public */
              bar() {}
            }
            """));
  }

  @Test
  public void testBadOverrideOfProtectedProperty() {
    test(
        srcs(
            """
            class Foo {
              /** @protected */
              bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              /** @private */
              bar() {}
            }
            """),
        error(VISIBILITY_MISMATCH));
  }

  @Test
  public void testProtectedOverrideOfPackageProperty() {
    test(
        srcs(
            """
            class Foo {
              /** @package */
              bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              /** @protected */
              bar() {}
            }
            """));
  }

  @Test
  public void testBadOverrideOfPrivateProperty() {
    test(
        srcs(
            """
            class Foo {
              /** @private */
              bar() {}
            }
            """,
            """
            class SubFoo extends Foo {
              /** @protected */
              bar() {}
            }
            """),
        error(PRIVATE_OVERRIDE));
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateClass() {
    test(
        srcs(
            """
            /** @private */
            class Foo {
              static create() { return new Foo(); }
            }
            """,
            "Foo.create()"),
        error(BAD_PRIVATE_GLOBAL_ACCESS));
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    test(
        srcs(
            """
            /** @private */
            goog.Foo = class {
              static create() { return new goog.Foo(); }
            }
            """,
            "goog.Foo.create()"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testInstanceofOfPrivateConstructor() {
    test(
        srcs(
            """
            goog.Foo = class {
              /** @private */
              constructor() {}
            };
            """,
            "goog instanceof goog.Foo"));
  }

  @Test
  public void testOkAssignmentOfDeprecatedProperty() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @deprecated */
                this.bar = 3;
              }
            }
            """));
  }

  @Test
  public void testBadReadOfDeprecatedProperty() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @deprecated GRR */
                this.bar = 3;

                this.baz = this.bar;
              }
            }
            """),
        deprecatedProp("Property bar of type Foo has been deprecated: GRR"));
  }

  @Test
  public void testNullableDeprecatedProperty() {
    test(
        srcs(
            """
            class Foo {}

            /** @deprecated */
            Foo.prototype.length;

            /** @param {?Foo} x */
            function f(x) { return x.length; }
            """),
        error(DEPRECATED_PROP));
  }

  @Test
  public void testNullablePrivateProperty() {
    test(
        srcs(
            """
            class Foo {}

            /** @private */
            Foo.prototype.length;
            """,
            """
            /** @param {?Foo} x */
            function f(x) { return x.length; }
            """),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivatePropertyByConvention1() {
    test(
        srcs(
            """
            class Foo {}

            /** @type {number} */
            Foo.prototype.length_;
            """,
            """
            /** @param {?Foo} x */
            function f(x) { return x.length_; }
            """));
  }

  @Test
  public void testNoPrivatePropertyByConvention2() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @type {number} */
                this.length_ = 1;
              }
            }

            /** @type {number} */
             Foo.prototype.length_;
            """,
            """
            /** @param {Foo} x */
            function f(x) { return x.length_; }
            """));
  }

  @Test
  public void testNoDeclarationAndConventionNoConflict1() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @protected */
                this.length_ = 1;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty1a() {
    test(
        srcs(
            """
            class A {
              constructor() {
                /** @const */
                this.bar = 3;
              }
            }

            class B {
              constructor() {
                /** @const */
                this.bar = 3;

                this.bar += 4;
              }
            }
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty1aLogicalAssignment() {
    test(
        srcs(
            """
            class A {
              constructor() {
                /** @const */
                this.bar = null;

                this.bar ??= 4;
              }
            }
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantPropertyReassigned_crossModuleWithCollidingNames() {
    disableRewriteClosureCode();
    testNoWarning(
        srcs(
            """
            goog.module('mod1');
            class A {
              constructor() {
                /** @const */
                this.bar = 3;
              }
            }
            """,
            """
            goog.module('mod2');
            class A {
              constructor() {
                /** @const */
                this.bar = 3;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced1() {
    test(
        srcs(
            """
            class A {
              constructor() {
                this.BAR = 3;
              }
            }

            class B {
              constructor() {
                this.BAR = 3;

                this.BAR += 4;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty_conventionNotEnforced1LogicalAssignment() {
    test(
        srcs(
            """
            class A {
              constructor() {
                this.BAR = null;

                this.BAR ??= 4;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty2() {
    test(
        srcs(
            """
            class  Foo {}

            /** @const */
            Foo.prototype.prop = 2;

            var foo = new Foo();
            foo.prop = 3;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced2() {
    test(
        srcs(
            """
            class  Foo {}

            Foo.prototype.PROP = 2;

            var foo = new Foo();
            foo.PROP = 3;
            """));
  }

  @Test
  public void testConstantProperty4() {
    test(
        srcs(
            """
            class Cat {}

            /** @const */
            Cat.test = 1;

            Cat.test *= 2;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced3() {
    test(
        srcs(
            """
            class Cat { }

            Cat.TEST = 1;
            Cat.TEST *= 2;
            """));
  }

  @Test
  public void testConstantProperty5() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                this.prop = 1;
              }
            }

            /** @const */
            Foo.prototype.prop;

            Foo.prototype.prop = 2
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty6() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                this.prop = 1;
              }
            }

            /** @const */
            Foo.prototype.prop = 2;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty7() {
    test(
        srcs(
            """
            class Foo {
              bar_() {}
            }

            class SubFoo extends Foo {
              /**
               * @const
               * @override
               */
              bar_() {}

              baz() { this.bar_(); }
            }
            """));
  }

  @Test
  public void testConstantProperty9() {
    test(
        srcs(
            """
            class A {
              constructor() {
                /** @const */
                this.bar = 3;
              }
            }

            class B {
              constructor() {
                this.bar = 4;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty10a() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                this.prop = 1;
              }
            }

            /** @const */
            Foo.prototype.prop;
            """));
  }

  @Test
  public void testConstantProperty10b() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                this.PROP = 1;
              }
            }

            Foo.prototype.PROP;
            """));
  }

  @Test
  public void testConstantProperty11() {
    test(
        srcs(
            """
            class Foo {}

            /** @const */
            Foo.prototype.bar;

            class SubFoo extends Foo {
              constructor() {
                this.bar = 5;
                this.bar = 6;
              }
            }
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty12() {
    test(
        srcs(
            """
            class Foo {}

            /** @const */
            Foo.prototype.bar;

            class SubFoo extends Foo {
              constructor() {
                this.bar = 5;
              }
            }

            class SubFoo2 extends Foo {
              constructor() {
                this.bar = 5;
              }
            }
            """));
  }

  @Test
  public void testConstantProperty13() {
    test(
        srcs(
            """
            class Foo {}

            /** @const */
            Foo.prototype.bar;

            class SubFoo extends Foo {
              constructor() {
                this.bar = 5;
              }
            }

            class SubSubFoo extends SubFoo {
              constructor() {
                this.bar = 5;
              }
            }
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty14() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                /** @const */
                this.bar = 3;

                delete this.bar;
              }
            }
            """),
        error(CONST_PROPERTY_DELETED));
  }

  @Test
  public void testConstantProperty_recordType() {
    test(
        srcs(
            """
            /** @record */
            class Foo {
              constructor() {
                /** @const {number} */
                this.bar;
              }
            }

            const /** !Foo */ x = {
              bar: 9,
            };
            x.bar = 0;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE)
            .withMessageContaining("unknown location due to structural typing"));
  }

  @Test
  public void testConstantProperty_fromExternsOrIjs_duplicateExternOk() {
    testSame(
        externs(
            """
            class Foo {}
            /** @const */ Foo.prototype.PROP;
            /** @const */ Foo.prototype.PROP;
            """),
        srcs(""));
  }

  @Test
  public void testConstantProperty_fromExternsOrIjs() {
    test(
        externs(
            """
            class Foo {}
            /** @const */ Foo.prototype.PROP;
            """),
        srcs(
            """
            var f = new Foo();
            f.PROP = 1;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE).withMessageContaining("at externs:2:"));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced15a() {
    test(
        srcs(
            """
            class Foo {
              constructor() {
                this.CONST = 100;
              }
            }

            /** @type {Foo} */
            var foo = new Foo();

            /** @type {number} */
            foo.CONST = 0;
            """));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced15b() {
    test(
        srcs(
            """
            class Foo {}

            Foo.prototype.CONST = 100;

            /** @type {Foo} */
            var foo = new Foo();

            /** @type {number} */
            foo.CONST = 0;
            """));
  }

  @Test
  public void testConstantProperty_ConventionNotEnforced15c() {
    test(
        srcs(
            """
            class Bar {
              constructor() {
                this.CONST = 100;
              }
            }

            class Foo extends Bar {};

            /** @type {Foo} */
            var foo = new Foo();

            /** @type {number} */
            foo.CONST = 0;
            """));
  }

  @Test
  public void testConstantProperty16() {
    test(
        srcs(
            """
            class Foo {}

            Foo.CONST = 100;

            class Bar {}

            Bar.CONST = 100;
            """));
  }

  @Test
  public void testConstantProperty17() {
    test(
        srcs(
            """
            class Foo {
            /** @const */
              x = 2;
            }

            /** @const */
            new Foo().x = 2;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantPropertyReassignmentInClassStaticBlock() {
    test(
        srcs(
            """
            class Foo {
            /** @const */
              static x = 2;
              static {
                this.x = 3;
              }
            }
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testFinalClassCannotBeSubclassed() {
    test(
        srcs(
            """
            /** @final */
            var Foo = class {};

            var Bar = class extends Foo {};
            """),
        error(EXTEND_FINAL_CLASS));

    test(
        srcs(
            """
            /** @final */
            class Foo {};

            class Bar extends Foo {};
            """),
        error(EXTEND_FINAL_CLASS));

    test(
        srcs(
            """
            /** @final */
            class Foo {};

            class Bar extends Foo { constructor() {} };
            """),
        error(EXTEND_FINAL_CLASS));

    test(
        srcs(
            """
            /** @const */
            var Foo = class {};

            var Bar = class extends Foo {};
            """));
  }

  @Test
  public void testFinalAndConstTreatedAsFinal() {
    test(
        srcs(
            """
            class Foo {}
            /** @final @const */
            Foo.prop = 0;
            Foo.prop = 1;
            """),
        error(FINAL_PROPERTY_OVERRIDDEN));
  }

  @Test
  public void testFinalMethodCanBeCalled() {
    testNoWarning(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            class Bar extends Foo {}
            new Foo().method();
            new Bar().method();
            """));
  }

  @Test
  public void testFinalMethodCannotBeOverridden() {
    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            class Bar extends Foo {
              method() {}
            }
            """),
        error(FINAL_PROPERTY_OVERRIDDEN));

    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            class Bar extends Foo {
              /** @override */
              method() {}
            }
            """),
        error(FINAL_PROPERTY_OVERRIDDEN));

    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            class Bar extends Foo {}
            class Baz extends Bar {
              /** @override */
              method() {}
            }
            """),
        error(FINAL_PROPERTY_OVERRIDDEN));

    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            Foo.prototype.method = () => '';
            """),
        error(FINAL_PROPERTY_OVERRIDDEN));
  }

  @Test
  public void testFinalMethodCannotBeDeleted() {
    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            delete Foo.prototype.method;
            """),
        error(CONST_PROPERTY_DELETED));

    test(
        srcs(
            """
            class Foo {
              /** @final */
              method() {}
            }
            class Bar extends Foo {}
            delete Bar.prototype.method;
            """),
        error(CONST_PROPERTY_DELETED));
  }

  @Test
  public void testCircularPrototypeLink() {
    // NOTE: this does yield a useful warning, except we don't check for it in this test:
    //      WARNING - Cycle detected in inheritance chain of type Foo
    // This warning already has a test: TypeCheckTest::testPrototypeLoop.
    test(
        srcs(
            """
            class Foo extends Foo {}

            /** @const */
            Foo.prop = 1;

            Foo.prop = 2;
            """),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  private static Diagnostic deprecatedName(String errorMessage) {
    return error(DEPRECATED_NAME_REASON).withMessage(errorMessage);
  }

  private static Diagnostic deprecatedProp(String errorMessage) {
    return error(DEPRECATED_PROP_REASON).withMessage(errorMessage);
  }

  private static Diagnostic deprecatedClass(String errorMessage) {
    return error(DEPRECATED_CLASS_REASON).withMessage(errorMessage);
  }
}
