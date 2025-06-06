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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.lint.CheckProvidesSorted;
import com.google.javascript.jscomp.lint.CheckRequiresSorted;
import com.google.javascript.refactoring.SuggestedFix.ImportType;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Maps a {@code JSError} to a list of {@code SuggestedFix}es, if possible.
 * TODO(tbreisacher): Move this into the compiler itself (i.e. into the jscomp package). This will
 *     make it easier for people adding new warnings to also add fixes for them.
 */
public final class ErrorToFixMapper {


  private static final Pattern DID_YOU_MEAN = Pattern.compile(".*Did you mean (.*)\\?");
  private static final Pattern EARLY_REF =
      Pattern.compile("Variable referenced before declaration: (.*)");
  private static final Pattern MISSING_REQUIRE =
      Pattern.compile("'([^']+)' references a .*", Pattern.DOTALL);
  private static final Pattern FULLY_QUALIFIED_NAME =
      Pattern.compile("Reference to fully qualified import name '([^']+)'.*");
  private static final Pattern USE_SHORT_NAME =
      Pattern.compile(".*Please use the short name '(.*)' instead.");

  private final AbstractCompiler compiler;
  private final LinkedHashMap<Node, ScriptMetadata> metadataForEachScript = new LinkedHashMap<>();

  public ErrorToFixMapper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public ImmutableList<SuggestedFix> getFixesForJsError(JSError error) {
    SuggestedFix fix = getFixForJsError(error);
    if (fix != null) {
      return ImmutableList.of(fix);
    }
    return switch (error.type().key) {
      case "JSC_IMPLICITLY_NONNULL_JSDOC",
          "JSC_IMPLICITLY_NULLABLE_JSDOC",
          "JSC_MISSING_NULLABILITY_MODIFIER_JSDOC",
          "JSC_NULL_MISSING_NULLABILITY_MODIFIER_JSDOC" ->
          getFixesForImplicitNullabilityErrors(error);
      default -> ImmutableList.of();
    };
  }

  /**
   * Creates a SuggestedFix for the given error. Note that some errors have multiple fixes so
   * getFixesForJsError should often be used instead of this.
   */
  public @Nullable SuggestedFix getFixForJsError(JSError error) {
    return switch (error.type().key) {
      case "JSC_REDECLARED_VARIABLE" -> getFixForRedeclaration(error);
      case "JSC_REFERENCE_BEFORE_DECLARE" -> getFixForEarlyReference(error);
      case "JSC_MISSING_SEMICOLON" -> getFixForMissingSemicolon(error);
      case "JSC_REQUIRES_NOT_SORTED" -> getFixForUnsortedRequires(error);
      case "JSC_PROVIDES_NOT_SORTED" -> getFixForUnsortedProvides(error);
      case "JSC_DEBUGGER_STATEMENT_PRESENT" ->
          new SuggestedFix.Builder()
              .attachMatchedNodeInfo(error.node(), compiler)
              .setDescription("Remove debugger statement")
              .delete(error.node())
              .build();
      case "JSC_USELESS_EMPTY_STATEMENT" -> removeEmptyStatement(error);
      case "JSC_INEXISTENT_PROPERTY_WITH_SUGGESTION",
          "JSC_STRICT_INEXISTENT_PROPERTY_WITH_SUGGESTION" ->
          getFixForInexistentProperty(error);
      case "JSC_MISSING_CALL_TO_SUPER" -> getFixForMissingSuper(error);
      case "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION" -> getFixForInvalidSuper(error);
      case "JSC_MISSING_REQUIRE", "JSC_MISSING_REQUIRE_IN_PROVIDES_FILE" ->
          getFixForMissingRequire(error, ImportType.REQUIRE);
      case "JSC_MISSING_REQUIRE_TYPE", "JSC_MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE" ->
          getFixForMissingRequire(error, ImportType.REQUIRE_TYPE);
      case "JSC_EXTRA_REQUIRE_WARNING" -> getFixForExtraRequire(error);
      case "JSC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME",
          "JSC_REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME" ->
          getFixForReferenceToShortImportByLongName(error);
      case "JSC_REDUNDANT_NULLABILITY_MODIFIER_JSDOC" ->
          getFixForRedundantNullabilityModifierJsDoc(error);
      case "JSC_MISSING_CONST_ON_CONSTANT_CASE" -> getFixForConstantCaseErrors(error);
      default -> null;
    };
  }

  private @Nullable SuggestedFix getFixForRedeclaration(JSError error) {
    Node name = error.node();
    checkState(name.isName(), name);
    Node parent = name.getParent();
    if (!NodeUtil.isNameDeclaration(parent)) {
      return null;
    }

    SuggestedFix.Builder fix = new SuggestedFix.Builder().attachMatchedNodeInfo(name, compiler);

    if (!name.hasChildren()) {
      Node nodeToDelete = parent.hasOneChild() ? parent : error.node();
      return fix.setDescription("Remove redundant declaration").delete(nodeToDelete).build();
    }

    fix.setDescription("Convert redundant declaration to assignment");

    Node assign = IR.exprResult(
        IR.assign(name.cloneNode(), name.getFirstChild().cloneTree()));
    if (parent.hasOneChild()) {
      return fix.replace(parent, assign, compiler).build();
    }

    // Split the var statement into an assignment and up to two var statements.
    // var a = 0,
    //     b = 1,  // This is the one we're removing.
    //     c = 2;
    //
    // becomes
    //
    // var a = 0;  // This is the "added" var statement.
    // b = 1;
    // var c = 2;  // This is the original var statement.
    List<Node> childrenOfAddedVarStatement = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
      if (n == name) {
        break;
      }
      childrenOfAddedVarStatement.add(n);
    }

    if (!childrenOfAddedVarStatement.isEmpty()) {
      Node var = new Node(parent.getToken());
      for (Node n : childrenOfAddedVarStatement) {
        var.addChildToBack(n.cloneTree());
      }
      // Use a sortKey of "1" to make sure this is applied before the statement below.
      fix.insertBefore(parent, var, compiler, "1");
    }

    if (name.getNext() != null) {
      // Keep the original var statement, just remove the names that will be put in the added one.
      for (Node n : childrenOfAddedVarStatement) {
        fix.delete(n);
      }
      fix.delete(name);
      // Use a sortKey of "2" to make sure this is applied after the statement above.
      fix.insertBefore(parent, assign, compiler, "2");
    } else {
      // Remove the original var statement.
      fix.replace(parent, assign, compiler);
    }

    return fix.build();
  }

  /**
   * This fix is not ideal. It trades one warning (JSC_REFERENCE_BEFORE_DECLARE) for another
   * (JSC_REDECLARED_VARIABLE). But after running the fixer once, you can then run it again and
   * #getFixForRedeclaration will take care of the JSC_REDECLARED_VARIABLE warning.
   */
  private @Nullable SuggestedFix getFixForEarlyReference(JSError error) {
    Matcher m = EARLY_REF.matcher(error.description());
    if (m.matches()) {
      String name = m.group(1);
      Node stmt = NodeUtil.getEnclosingStatement(error.node());
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node(), compiler)
          .setDescription("Insert var declaration statement")
          .insertBefore(stmt, "var " + name + ";\n")
          .build();
    }
    return null;
  }

  private SuggestedFix getFixForReferenceToShortImportByLongName(JSError error) {
    SuggestedFix.Builder fix =
        new SuggestedFix.Builder().attachMatchedNodeInfo(error.node(), compiler);
    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.node(), metadata);

    Node script = compiler.getScriptNode(error.sourceName());
    ScriptMetadata scriptMetadata = this.getMetadataForScript(script);

    Matcher fullNameMatcher = FULLY_QUALIFIED_NAME.matcher(error.description());
    checkState(fullNameMatcher.matches(), error.description());
    String fullName = fullNameMatcher.group(1);

    Matcher shortNameMatcher = USE_SHORT_NAME.matcher(error.description());
    String shortName;
    if (shortNameMatcher.matches()) {
      shortName = shortNameMatcher.group(1);
    } else {
      fix.addGoogRequire(match, fullName, scriptMetadata);
      shortName = scriptMetadata.getAlias(fullName);
    }

    String oldName =
        error.node().isQualifiedName() ? error.node().getQualifiedName() : error.node().getString();

    return fix.replace(
            error.node(),
            NodeUtil.newQName(compiler, oldName.replace(fullName, shortName)),
            compiler)
        .build();
  }

  private ImmutableList<SuggestedFix> getFixesForImplicitNullabilityErrors(JSError error) {

    if (error.node().getSourceFileName() == null) {
      // If we don't have a source location we can't generate a valid fix.
      return ImmutableList.of();
    }

    SuggestedFix qmark =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.node(), compiler)
            .insertBefore(error.node(), "?")
            .setDescription("Make nullability explicit")
            .build();
    SuggestedFix bang =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.node(), compiler)
            .insertBefore(error.node(), "!")
            .setDescription("Make type non-nullable")
            .build();
    return switch (error.type().key) {
      case "JSC_NULL_MISSING_NULLABILITY_MODIFIER_JSDOC" ->
          // When initializer was null, we can be confident about nullability
          ImmutableList.of(qmark);
      case "JSC_MISSING_NULLABILITY_MODIFIER_JSDOC" ->
          // Otherwise, the linter should assume ! is preferred over ?.
          ImmutableList.of(bang, qmark);
      case "JSC_IMPLICITLY_NULLABLE_JSDOC" ->
          // The type-based check prefers ? over ! since it only warns for names that are nullable.
          ImmutableList.of(qmark, bang);
      case "JSC_IMPLICITLY_NONNULL_JSDOC" ->
          // Using type-information, we can also be confident about ! for enums and typedefs.
          ImmutableList.of(bang);
      default -> throw new IllegalArgumentException("Unexpected JSError Type: " + error.type().key);
    };
  }

  private SuggestedFix removeEmptyStatement(JSError error) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node(), compiler)
        .setDescription("Remove empty statement")
        .deleteWithoutRemovingWhitespace(error.node())
        .build();
  }

  private SuggestedFix getFixForMissingSemicolon(JSError error) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node(), compiler)
        .insertAfter(error.node(), ";")
        .build();
  }

  private SuggestedFix getFixForMissingSuper(JSError error) {
    Node constructorFunction = error.node().getFirstChild();
    Node body = NodeUtil.getFunctionBody(constructorFunction);
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node(), compiler)
        .addChildToFront(body, "super();")
        .build();
  }

  private @Nullable SuggestedFix getFixForInvalidSuper(JSError error) {
    Matcher m = DID_YOU_MEAN.matcher(error.description());
    if (m.matches()) {
      String superDotSuggestion = checkNotNull(m.group(1));
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node(), compiler)
          .setDescription("Call '" + superDotSuggestion + "' instead")
          .replace(error.node(), NodeUtil.newQName(compiler, superDotSuggestion), compiler)
          .build();
    }
    return null;
  }

  private @Nullable SuggestedFix getFixForInexistentProperty(JSError error) {
    Matcher m = DID_YOU_MEAN.matcher(error.description());
    if (m.matches()) {
      String suggestedPropName = m.group(1);
      return new SuggestedFix.Builder()
          .attachMatchedNodeInfo(error.node(), compiler)
          .setDescription("Change property name to '" + suggestedPropName + "'")
          .rename(error.node(), suggestedPropName)
          .build();
    }
    return null;
  }

  private SuggestedFix getFixForMissingRequire(JSError error, ImportType importType) {
    Matcher regexMatcher = MISSING_REQUIRE.matcher(error.description());
    checkState(regexMatcher.matches(), "Unexpected error description: %s", error.description());
    String namespaceToRequire = regexMatcher.group(1);

    String qName =
        error.node().isQualifiedName() ? error.node().getQualifiedName() : error.node().getString();

    checkState(
        qName.startsWith(namespaceToRequire),
        "Expected error location %s to start with namespace <%s>",
        error.node(),
        namespaceToRequire);

    Node script = compiler.getScriptNode(error.sourceName());
    ScriptMetadata scriptMetadata = this.getMetadataForScript(script);

    NodeMetadata metadata = new NodeMetadata(compiler);
    Match match = new Match(error.node(), metadata);
    SuggestedFix.Builder fix =
        new SuggestedFix.Builder()
            .attachMatchedNodeInfo(error.node(), compiler)
            .addImport(match, namespaceToRequire, importType, scriptMetadata);

    String alias = scriptMetadata.getAlias(namespaceToRequire);
    if (alias != null) {
      fix.replace(
          error.node(),
          NodeUtil.newQName(compiler, qName.replace(namespaceToRequire, alias)),
          compiler);
    }

    return fix.build();
  }

  private SuggestedFix getFixForExtraRequire(JSError error) {
    Node node = error.node();

    SuggestedFix.Builder fix = new SuggestedFix.Builder().attachMatchedNodeInfo(node, compiler);
    boolean destructuring = NodeUtil.getEnclosingType(node, Token.OBJECT_PATTERN) != null;
    if (destructuring) {
      fix.setDescription("Delete unused symbol");
      if (node.isStringKey()) {
        fix.delete(node);
      } else {
        checkState(node.getParent().isStringKey(), node.getParent());
        fix.delete(node.getParent());
      }
    } else {
      fix.setDescription("Delete extra require");
      fix.deleteWithoutRemovingWhitespaceBefore(NodeUtil.getEnclosingStatement(node));
    }
    return fix.build();
  }

  private @Nullable SuggestedFix getFixForUnsortedRequires(JSError error) {
    // TODO(tjgq): Encode enough information in the error to avoid the need to run a traversal in
    // order to produce the fix.
    Node script = NodeUtil.getEnclosingScript(error.node());
    CheckRequiresSorted callback = new CheckRequiresSorted(CheckRequiresSorted.Mode.COLLECT_ONLY);
    NodeTraversal.traverse(compiler, script, callback);

    if (!callback.needsFix()) {
      return null;
    }

    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(callback.getFirstNode(), compiler)
        .replaceRange(callback.getFirstNode(), callback.getLastNode(), callback.getReplacement())
        .build();
  }

  private @Nullable SuggestedFix getFixForUnsortedProvides(JSError error) {
    // TODO(tjgq): Encode enough information in the error to avoid the need to run a traversal in
    // order to produce the fix.
    Node script = NodeUtil.getEnclosingScript(error.node());
    CheckProvidesSorted callback = new CheckProvidesSorted(CheckProvidesSorted.Mode.COLLECT_ONLY);
    NodeTraversal.traverse(compiler, script, callback);

    if (!callback.needsFix()) {
      return null;
    }

    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(callback.getFirstNode(), compiler)
        .replaceRange(callback.getFirstNode(), callback.getLastNode(), callback.getReplacement())
        .build();
  }

  private SuggestedFix getFixForRedundantNullabilityModifierJsDoc(JSError error) {
    return new SuggestedFix.Builder()
        .attachMatchedNodeInfo(error.node(), compiler)
        .replaceText(error.node(), 1, "")
        .build();
  }
  /**
   * Suggests a fix for a constant case error.
   *
   * <p>If the variable is in a let clause, suggest adding a const. If the variable is not, suggest
   * adding a @const annotation. Don't try to adjust the JSDoc, because that can produce invalid
   * output.
   */
  private @Nullable SuggestedFix getFixForConstantCaseErrors(JSError error) {
    Node n = error.node();
    Node parent = n.getParent();
    if (!n.isName() || !NodeUtil.isNameDeclaration(parent)) {
      return null;
    }

    if (parent.isLet()) {
      // Convert to a `const` declaration
      return new SuggestedFix.Builder()
          .setDescription("Make explicitly constant")
          .attachMatchedNodeInfo(parent, compiler)
          .replaceText(parent, 3, "const")
          .build();
    }
    // Don't convert a `var` to a `const` to avoid breaking variable scoping.
    checkState(parent.isVar(), parent);
    JSDocInfo info = parent.getJSDocInfo();
    if (info == null) {
      return new SuggestedFix.Builder()
          .setDescription("Make explicitly constant")
          .attachMatchedNodeInfo(parent, compiler)
          .addOrReplaceJsDoc(parent, "/** @const */\n")
          .build();
    }
    return null;
  }

  private ScriptMetadata getMetadataForScript(Node script) {
    checkArgument(script.isScript());
    return this.metadataForEachScript.computeIfAbsent(
        script, (s) -> ScriptMetadata.create(s, this.compiler));
  }
}
