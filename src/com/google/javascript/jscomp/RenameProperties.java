/*
 * Copyright 2004 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * RenameProperties renames properties (including methods) of all JavaScript objects. This includes
 * prototypes, functions, object literals, etc.
 *
 * <p>If provided a VariableMap of previously used names, it tries to reuse those names.
 *
 * <p>To prevent a property from getting renamed you may extern it (add it to your externs file) or
 * put it in quotes.
 *
 * <p>To avoid run-time JavaScript errors, use quotes when accessing properties that are defined
 * using quotes.
 *
 * <pre>
 *   var a = {'myprop': 0}, b = a['myprop'];  // correct
 *   var x = {'myprop': 0}, y = x.myprop;     // incorrect
 * </pre>
 *
 * This pass also recognizes and replaces special renaming functions. They supply a property name as
 * the string literal for the first argument. This pass will replace them as though they were JS
 * property references. Here are two examples: JSCompiler_renameProperty('propertyName') -> 'jYq'
 * JSCompiler_renameProperty('myProp.nestedProp.innerProp') -> 'e4.sW.C$'
 *
 * <p>This class is not thread-safe.
 */
class RenameProperties implements CompilerPass {
  private static final Splitter DOT_SPLITTER = Splitter.on('.');

  private final AbstractCompiler compiler;
  private final boolean generatePseudoNames;

  /** Property renaming map from a previous compilation. */
  private final VariableMap prevUsedPropertyMap;

  private final List<Node> toRemove = new ArrayList<>();
  private final List<Node> stringNodesToRename = new ArrayList<>();
  private final Map<Node, Node> callNodeToParentMap =
      new LinkedHashMap<>();
  private final Set<Character> reservedFirstCharacters;
  private final Set<Character> reservedNonFirstCharacters;

  // Map from property name to Property object
  private final Map<String, Property> propertyMap = new LinkedHashMap<>();

  // Property names that don't get renamed
  private final Set<String> externedNames = new LinkedHashSet<>(
      Arrays.asList("prototype"));

  // Names to which properties shouldn't be renamed, to avoid name conflicts
  private final Set<String> quotedNames = new LinkedHashSet<>();

  // Shared name generator
  private final NameGenerator nameGenerator;

  // Filter function for property names. The function takes a node as input and returns true if the
  // node should be renamed.
  private final Predicate<Node> propertyRenameEligibilityFilter;
  // Property names that should not be renamed, based on the propertyNameFilter. Unlike
  // externedNames, this set is dynamically constructed during the pass.
  private final Set<String> filteredOutNames = new LinkedHashSet<>();

  private static final Comparator<Property> FREQUENCY_COMPARATOR =
      (Property p1, Property p2) -> {

        /* First a frequently used names would always be picked first. */
        if (p1.numOccurrences != p2.numOccurrences) {
          return p2.numOccurrences - p1.numOccurrences;
        }

        /* Finally, for determinism, we compare them based on the old name. */
        return p1.oldName.compareTo(p2.oldName);
      };

  static final DiagnosticType BAD_CALL = DiagnosticType.error(
      "JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_CALL",
      "Bad {0} call - the first argument must be a string literal");

  static final DiagnosticType BAD_ARG = DiagnosticType.error(
      "JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_ARG",
      "Bad {0} argument - ''{1}'' is not a valid JavaScript identifier");

  /**
   * Creates an instance.
   *
   * @param compiler The JSCompiler.
   * @param generatePseudoNames Generate pseudo names. e.g foo -> $foo$ instead of compact
   *     obfuscated names. This is used for debugging.
   * @param prevUsedPropertyMap The property renaming map used in a previous compilation.
   * @param reservedFirstCharacters If specified these characters won't be used in generated names
   *     for the first character
   * @param reservedNonFirstCharacters If specified these characters won't be used in generated
   *     names for characters after the first
   * @param nameGenerator a shared NameGenerator that this instance can use; the instance may reset
   *     or reconfigure it, so the caller should not expect any state to be preserved
   */
  RenameProperties(
      AbstractCompiler compiler,
      boolean generatePseudoNames,
      VariableMap prevUsedPropertyMap,
      Set<Character> reservedFirstCharacters,
      Set<Character> reservedNonFirstCharacters,
      NameGenerator nameGenerator) {
    this(
        compiler,
        generatePseudoNames,
        prevUsedPropertyMap,
        reservedFirstCharacters,
        reservedNonFirstCharacters,
        nameGenerator,
        (Node n) -> true);
  }

  /**
   * Creates an instance.
   *
   * @param compiler the JSCompiler
   * @param generatePseudoNames generate pseudo names. e.g. foo -> $foo$ instead of compact
   *     obfuscated names. This is used for debugging.
   * @param prevUsedPropertyMap the property renaming map used in a previous compilation
   * @param reservedFirstCharacters if specified these characters won't be used in generated names
   *     for the first character
   * @param reservedNonFirstCharacters if specified these characters won't be used in generated
   *     names for characters after the first
   * @param nameGenerator a shared NameGenerator that this instance can use; the instance may reset
   *     or reconfigure it, so the caller should not expect any state to be preserved
   * @param propertyRenameEligibilityFilter limit the nodes that are renamed using a predicate
   *     function. When this returns true, the property name is renamed if and only if all other
   *     property references for the same name match the predicate. If this returns false for any
   *     instance of a property, then no property references anywhere in the AST to that property
   *     are renamed
   */
  RenameProperties(
      AbstractCompiler compiler,
      boolean generatePseudoNames,
      VariableMap prevUsedPropertyMap,
      Set<Character> reservedFirstCharacters,
      Set<Character> reservedNonFirstCharacters,
      NameGenerator nameGenerator,
      Predicate<Node> propertyRenameEligibilityFilter) {
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    this.prevUsedPropertyMap = prevUsedPropertyMap;
    this.reservedFirstCharacters = reservedFirstCharacters;
    this.reservedNonFirstCharacters = reservedNonFirstCharacters;
    this.nameGenerator = nameGenerator;
    this.propertyRenameEligibilityFilter = propertyRenameEligibilityFilter;
    externedNames.addAll(compiler.getExternProperties());
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());

    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    Set<String> reservedNames =
        Sets.newLinkedHashSetWithExpectedSize(externedNames.size() + quotedNames.size());
    reservedNames.addAll(externedNames);
    reservedNames.addAll(quotedNames);

    // Assign names, sorted by descending frequency to minimize code size.
    Set<Property> propsByFreq = new TreeSet<>(FREQUENCY_COMPARATOR);
    propsByFreq.addAll(propertyMap.values());

    // First, try and reuse as many property names from the previous compilation
    // as possible.
    if (prevUsedPropertyMap != null) {
      reusePropertyNames(reservedNames, propsByFreq);
    }

    generateNames(propsByFreq, reservedNames);

    // Update the string nodes.
    for (Node n : stringNodesToRename) {
      String oldName = n.getString();
      if (filteredOutNames.contains(oldName)) {
        continue;
      }
      Property p = propertyMap.get(oldName);
      if (p != null && p.newName != null) {
        checkState(oldName.equals(p.oldName));
        n.setString(p.newName);
        if (!p.newName.equals(oldName)) {
          compiler.reportChangeToEnclosingScope(n);
        }
      }
    }

    // Update the call nodes.
    for (Map.Entry<Node, Node> nodeEntry : callNodeToParentMap.entrySet()) {
      Node parent = nodeEntry.getValue();
      Node firstArg = nodeEntry.getKey().getSecondChild();
      StringBuilder sb = new StringBuilder();
      for (String oldName : DOT_SPLITTER.split(firstArg.getString())) {
        Property p = propertyMap.get(oldName);
        String replacement;
        if (p != null && p.newName != null) {
          checkState(oldName.equals(p.oldName));
          if (filteredOutNames.contains(oldName)) {
            replacement = oldName;
          } else {
            replacement = p.newName;
          }
        } else {
          replacement = oldName;
        }
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(replacement);
      }
      nodeEntry.getKey().replaceWith(IR.string(sb.toString()));
      compiler.reportChangeToEnclosingScope(parent);
    }

    // Complete queued removals.
    for (Node n : toRemove) {
      Node parent = n.getParent();
      compiler.reportChangeToEnclosingScope(n);
      n.detach();
      NodeUtil.markFunctionsDeleted(n, compiler);
      if (!parent.hasChildren() && !parent.isScript()) {
        parent.detach();
      }
    }

    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED_OBFUSCATED);
    // This pass may rename getter or setter properties
    GatherGetterAndSetterProperties.update(compiler, externs, root);
  }

  /**
   * Runs through the list of properties and renames as many as possible with
   * names from the previous compilation. Also, updates reservedNames with the
   * set of reused names.
   * @param reservedNames Reserved names to use during renaming.
   * @param allProps Properties to rename.
   */
  private void reusePropertyNames(Set<String> reservedNames,
                                  Collection<Property> allProps) {
    for (Property prop : allProps) {
      // Check if this node can reuse a name from a previous compilation - if
      // it can set the newName for the property too.
      String prevName = prevUsedPropertyMap.lookupNewName(prop.oldName);
      if (!generatePseudoNames && prevName != null) {
        // We can reuse prevName if it's not reserved.
        if (reservedNames.contains(prevName)) {
          continue;
        }

        prop.newName = prevName;
        reservedNames.add(prevName);
      }
    }
  }

  /**
   * Generates new names for properties.
   *
   * @param props Properties to generate new names for
   * @param reservedNames A set of names to which properties should not be
   *     renamed
   */
  private void generateNames(Set<Property> props, Set<String> reservedNames) {
    nameGenerator.reset(
        reservedNames, "", this.reservedFirstCharacters, this.reservedNonFirstCharacters);
    for (Property p : props) {
      if (generatePseudoNames) {
        p.newName = "$" + p.oldName + "$";
      } else {
        // If we haven't already given this property a reusable name.
        if (p.newName == null) {
          p.newName = nameGenerator.generateNextName();
        }
      }
      reservedNames.add(p.newName);
    }
  }

  /**
   * Gets the property renaming map (the "answer key").
   *
   * @return A mapping from original names to new names
   */
  VariableMap getPropertyMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (Property p : propertyMap.values()) {
      if (p.newName != null) {
        map.put(p.oldName, p.newName);
      }
    }
    return new VariableMap(map.buildOrThrow());
  }


  // -------------------------------------------------------------------------

  /**
   * A traversal callback that collects property names and counts how
   * frequently each property name occurs.
   */
  private class ProcessProperties extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case GETPROP:
        case OPTCHAIN_GETPROP:
        case MEMBER_FIELD_DEF:
          maybeMarkCandidate(n);
          break;
        case GETELEM:
        case OPTCHAIN_GETELEM:
          // If this is a quoted property access (e.g. x['myprop']), we need to
          // ensure that we never rename some other property in a way that
          // could conflict with this quoted name.
          Node child = n.getLastChild();
          if (child != null && child.isStringLit()) {
            quotedNames.add(child.getString());
          }
          break;
        case CALL:
          {
            // We replace property renaming function calls with a string
            // containing the renamed property.
            Node fnName = n.getFirstChild();

            if (compiler.getCodingConvention().isPropertyRenameFunction(fnName)) {
              callNodeToParentMap.put(n, parent);
              countCallCandidates(t, n);
              Node stringArgument = n.getSecondChild();
              if (!propertyRenameEligibilityFilter.test(n)) {
                for (String name : DOT_SPLITTER.split(stringArgument.getString())) {
                  filteredOutNames.add(name);
                }
              }
            }
            break;
          }
        case MEMBER_FUNCTION_DEF:
          checkState(!n.isQuotedStringKey());
          if (NodeUtil.isEs6ConstructorMemberFunctionDef(n)) {
            externedNames.add(n.getString());
          } else {
            maybeMarkCandidate(n);
          }
          break;
        case GETTER_DEF:
        case SETTER_DEF:
        case STRING_KEY:
          if (n.isQuotedStringKey()) {
            // Ensure that we never rename some other property in a way
            // that could conflict with this quoted key.
            quotedNames.add(n.getString());
          } else {
            maybeMarkCandidate(n);
          }
          break;
        case FUNCTION:
          {
            // We eliminate any stub implementations of JSCompiler_renameProperty
            // that we encounter.
            if (NodeUtil.isFunctionDeclaration(n)) {
              String name = n.getFirstChild().getString();
              if (NodeUtil.JSC_PROPERTY_NAME_FN.equals(name)) {
                toRemove.add(n);
              }
            } else if (parent.isName()
                && NodeUtil.JSC_PROPERTY_NAME_FN.equals(parent.getString())) {
              Node varNode = parent.getParent();
              if (varNode.isVar()) {
                toRemove.add(parent);
              }
            } else if (NodeUtil.isFunctionExpression(n)
                && parent.isAssign()
                && parent
                    .getFirstChild()
                    .isGetProp() // JSCompiler does not handle optional calls to property rename
                // function
                && compiler
                    .getCodingConvention()
                    .isPropertyRenameFunction(parent.getFirstChild())) {
              Node exprResult = parent.getParent();
              if (exprResult.isExprResult()
                  && NodeUtil.isStatementBlock(exprResult.getParent())
                  && exprResult.getFirstChild().isAssign()) {
                toRemove.add(exprResult);
              }
            }
            break;
          }
        default:
          break;
      }
    }

    /**
     * If a property node is eligible for renaming, stashes a reference to it
     * and increments the property name's access count.
     *
     * @param n The STRING node for a property
     */
    private void maybeMarkCandidate(Node n) {
      String name = n.getString();
      if (!externedNames.contains(name)) {
        if (!propertyRenameEligibilityFilter.test(n)) {
          filteredOutNames.add(n.getString());
        }
        stringNodesToRename.add(n);
        countPropertyOccurrence(name);
      }
    }

    /**
     * Counts references to property names that occur in a special function
     * call.
     *
     * @param callNode The CALL node for a property
     * @param t The traversal
     */
    private void countCallCandidates(NodeTraversal t, Node callNode) {
      String fnName = callNode.getFirstChild().getOriginalName();
      if (fnName == null) {
        fnName = callNode.getFirstChild().getString();
      }
      Node firstArg = callNode.getSecondChild();
      if (!firstArg.isStringLit()) {
        t.report(callNode, BAD_CALL, fnName);
        return;
      }

      for (String name : DOT_SPLITTER.split(firstArg.getString())) {
        if (!TokenStream.isJSIdentifier(name)) {
          t.report(callNode, BAD_ARG, fnName);
          continue;
        }
        if (!externedNames.contains(name)) {
          countPropertyOccurrence(name);
        }
      }
    }

    /**
     * Increments the occurrence count for a property name.
     *
     * @param name The property name
     */
    private void countPropertyOccurrence(String name) {
      Property prop = propertyMap.computeIfAbsent(name, Property::new);
      prop.numOccurrences++;
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Encapsulates the information needed for renaming a property.
   */
  private static class Property {
    final String oldName;
    String newName;
    int numOccurrences;

    Property(String name) {
      this.oldName = name;
    }
  }
}
