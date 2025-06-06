/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Scope contains information about a variable scope in JavaScript. Scopes can be nested, a scope
 * points back to its parent scope. A Scope contains information about variables defined in that
 * scope.
 *
 * <p>ES 2015 introduces new scoping rules, which adds some complexity to this class. In particular,
 * scopes fall into two mutually exclusive categories: <i>block</i> and <i>container</i>. Block
 * scopes are all scopes whose roots are blocks, as well as any control structures whose optional
 * blocks are omitted. These scopes did not exist at all prior to ES 2015. Container scopes comprise
 * function scopes, global scopes, and module scopes, and (aside from modules, which didn't exist in
 * ES5) corresponds to the ES5 scope rules. This corresponds roughly to one container scope per CFG
 * root (but not exactly, due to SCRIPT-level CFGs).
 *
 * <p>All container scopes, plus the outermost block scope within a function (i.e. the <i>function
 * block scope</i>) are considered <i>hoist scopes</i>. All functions thus have two hoist scopes:
 * the function scope and the function block scope. Hoist scopes are relevant because "var"
 * declarations are hoisted to the closest hoist scope, as opposed to "let" and "const" which always
 * apply to the specific scope in which they occur.
 *
 * <p>Note that every function actually has two distinct hoist scopes: a container scope on the
 * FUNCTION node, and a block-scope on the top-level BLOCK in the function (the "function block").
 * Local variables are declared on the function block, while parameters and optionally the function
 * name (if it bleeds, i.e. from a named function expression) are declared on the container scope.
 * This is required so that default parameter initializers can refer to names from outside the
 * function that could possibly be shadowed in the function block. But these scopes are not fully
 * independent of one another, since the language does not allow a top-level local variable to
 * shadow a parameter name - so in some situations these scopes must be treated as a single scope.
 *
 * @see NodeTraversal
 */
public abstract class AbstractScope<S extends AbstractScope<S, V>, V extends AbstractVar<S, V>>
    implements StaticScope, Serializable {
  private Map<String, V> vars = ImmutableMap.of();
  private Map<ImplicitVar, V> implicitVars = ImmutableMap.of();
  private final Node rootNode;

  AbstractScope(Node rootNode) {
    this.rootNode = checkNotNull(rootNode);
  }

  /** The depth of the scope. The global scope has depth 0. */
  public abstract int getDepth();

  /** Returns the parent scope, or null if this is the global scope. */
  public abstract S getParent();

  @Override
  public final String toString() {
    return "Scope@" + rootNode;
  }

  public Scope untyped() {
    throw new IllegalStateException("untyped() called, but not an untyped scope.");
  }

  public TypedScope typed() {
    throw new IllegalStateException("typed() called, but not a typed scope.");
  }

  /**
   * @return True if this scope contains {@code other}, or is the same scope as {@code other}.
   */
  final boolean contains(S other) {
    S s = checkNotNull(other);
    while (s != null) {
      if (s == this) {
        return true;
      }
      s = s.getParent();
    }
    return false;
  }

  /**
   * Gets the container node of the scope. This is typically the FUNCTION node or the global
   * BLOCK/SCRIPT node.
   */
  @Override
  public final Node getRootNode() {
    return rootNode;
  }

  /** Walks up the tree to find the global scope. */
  public final S getGlobalScope() {
    S result = thisScope();
    while (result.getParent() != null) {
      result = result.getParent();
    }
    return result;
  }

  @Override
  public final S getParentScope() {
    return getParent();
  }

  abstract V makeImplicitVar(ImplicitVar type);

  /**
   * Undeclares a variable, to be used when the compiler optimizes out a variable and removes it
   * from the scope.
   */
  final void undeclare(V var) {
    checkState(var.getScope() == this);
    checkState(vars.get(var.getName()).equals(var));
    undeclareInteral(var);
  }

  /** Without any safety checks */
  final void undeclareInteral(V var) {
    // Assume that vars must contain var, and thus not be empty
    vars.remove(var.getName());
  }

  final void declareInternal(String name, V var) {
    checkState(hasOwnSlot(name) || canDeclare(name), "Illegal shadow: %s", var.getNode());

    // For memory savings, only initialize the map once it needs to add its first element
    ImmutableMap<String, V> emptySentinel = ImmutableMap.of();
    if (vars == emptySentinel) {
      vars = Maps.newLinkedHashMapWithExpectedSize(1);
    }
    vars.put(name, var);
  }

  final void clearVarsInternal() {
    if (!vars.isEmpty()) {
      vars.clear();
    }
  }

  /** Returns true iff this scope implies a slot with the given name. */
  protected boolean hasOwnImplicitSlot(@Nullable ImplicitVar name) {
    return name != null && name.isMadeByScope(this);
  }

  /** Returns true if a variable is declared in this scope, with no recursion. */
  public final boolean hasOwnSlot(String name) {
    return vars.containsKey(name) || hasOwnImplicitSlot(ImplicitVar.of(name));
  }

  /** Returns true if a variable is declared in this or any parent scope. */
  public final boolean hasSlot(String name) {
    for (S scope = thisScope(); scope != null; scope = scope.getParent()) {
      if (scope.hasOwnSlot(name)) {
        return true;
      }
    }
    return false;
  }

  private final @Nullable V getOwnImplicitSlot(@Nullable ImplicitVar name) {
    if (!hasOwnImplicitSlot(name)) {
      return null;
    }

    // For memory savings, only initialize the map once it needs to add its first element
    ImmutableMap<ImplicitVar, V> emptySentinel = ImmutableMap.of();
    if (implicitVars == emptySentinel) {
      implicitVars = new EnumMap<>(ImplicitVar.class);
    }

    return implicitVars.computeIfAbsent(name, this::makeImplicitVar);
  }

  @Override
  public final V getOwnSlot(String name) {
    V var = vars.get(name);
    if (var != null) {
      return var;
    }

    return getOwnImplicitSlot(ImplicitVar.of(name));
  }

  @Override
  public final V getSlot(String name) {
    return getVar(name);
  }

  /**
   * Returns the variable, may be null
   *
   * <p>Non-final for {@link TypedScope} which needs to handle qualified names.
   */
  public V getVar(String name) {
    for (AbstractScope<S, V> scope = thisScope(); scope != null; scope = scope.getParent()) {
      @Nullable V var = scope.getOwnSlot(name);
      if (var != null) {
        return var;
      }
    }
    return null;
  }

  /**
   * Get a unique Var object to represent "arguments" within this scope.
   *
   * <p>This explicitly excludes user declared variables that are names "arguments". It only returns
   * special "arguments" variable that is inherent to a function.
   */
  public final V getArgumentsVar() {
    for (AbstractScope<S, V> scope = thisScope(); scope != null; scope = scope.getParent()) {
      @Nullable V arguments = scope.getOwnImplicitSlot(ImplicitVar.ARGUMENTS);
      if (arguments != null) {
        return arguments;
      }
    }
    return null;
  }

  /**
   * Returns true if the name can be declared on this scope without causing illegal shadowing.
   * Specifically, this is aware of the connection between function container scopes and function
   * block scopes and returns false for redeclaring parameters on the block scope.
   */
  final boolean canDeclare(String name) {
    return !hasOwnSlot(name)
        && (!isFunctionBlockScope()
            || !getParent().hasOwnSlot(name)
            || isBleedingFunctionName(name));
  }

  /**
   * Returns true if the given name is a bleeding function name in this scope. Local variables in
   * the function block are not allowed to shadow parameters, but they are allowed to shadow a
   * bleeding function name.
   */
  private boolean isBleedingFunctionName(String name) {
    V var = getVar(name);
    return var != null //
        && var.getNode() != null //
        && var.getNode().getParent().isFunction();
  }

  /**
   * Return an iterable over all of the variables declared in this scope (except the special
   * 'arguments' variable).
   */
  public final Iterable<V> getVarIterable() {
    return vars.values();
  }

  /**
   * Return an iterable over all of the variables accessible to this scope (i.e. the variables in
   * this scope and its parent scopes). Any variables declared in the local scope with the same name
   * as a variable declared in a parent scope gain precedence - if let x exists in the block scope,
   * a declaration let x from the parent scope would not be included because the parent scope's
   * variable gets shadowed.
   *
   * <p>The iterable contains variables from inner scopes before adding variables from outer parent
   * scopes.
   *
   * <p>We do not include the special 'arguments' variable.
   */
  public final Iterable<V> getAllAccessibleVariables() {
    Map<String, V> accessibleVars = new LinkedHashMap<>();
    S s = thisScope();

    while (s != null) {
      for (V v : s.getVarIterable()) {
        accessibleVars.putIfAbsent(v.getName(), v);
      }
      s = s.getParent();
    }
    return accessibleVars.values();
  }

  public final Iterable<V> getAllSymbols() {
    return Collections.unmodifiableCollection(vars.values());
  }

  /** Returns number of variables in this scope (excluding the special 'arguments' variable) */
  public final int getVarCount() {
    return vars.size();
  }

  /** Returns whether this is the global scope. */
  public final boolean isGlobal() {
    return getParent() == null;
  }

  /** Returns whether this is a local scope (i.e. not the global scope). */
  public final boolean isLocal() {
    return getParent() != null;
  }

  public final boolean isBlockScope() {
    return NodeUtil.createsBlockScope(rootNode);
  }

  public final boolean isStaticBlockScope() {
    return NodeUtil.isClassStaticBlock(getRootNode());
  }

  public final boolean isFunctionBlockScope() {
    return NodeUtil.isFunctionBlock(getRootNode());
  }

  public final boolean isFunctionScope() {
    return getRootNode().isFunction();
  }

  public final boolean isModuleScope() {
    return getRootNode().isModuleBody();
  }

  public final boolean isMemberFieldDefScope() {
    return getRootNode().isMemberFieldDef();
  }

  public final boolean isComputedFieldDefRhsScope() {
    return getRootNode().isComputedFieldDef();
  }

  public final boolean isCatchScope() {
    return getRootNode().isBlock()
        && getRootNode().hasOneChild()
        && getRootNode().getFirstChild().isCatch();
  }

  public final boolean isCfgRootScope() {
    return NodeUtil.isValidCfgRoot(rootNode);
  }

  /**
   * If a var were declared in this scope, would it belong to this scope (as opposed to some
   * enclosing scope)?
   *
   * <p>We consider function scopes to be hoist scopes. Even though it's impossible to declare a var
   * inside function parameters, it would make less sense to say that if you did declare one in the
   * function parameters, it would be hoisted somewhere else.
   */
  final boolean isHoistScope() {
    return isFunctionScope()
        || isFunctionBlockScope()
        || isGlobal()
        || isModuleScope()
        || isStaticBlockScope();
  }

  /**
   * If a var were declared in this scope, return the scope it would be hoisted to.
   *
   * <p>For function scopes, we return back the scope itself, since even though there is no way to
   * declare a var inside function parameters, it would make even less sense to say that such
   * declarations would be "hoisted" somewhere else.
   */
  public final S getClosestHoistScope() {
    S current = thisScope();
    while (current != null) {
      if (current.isHoistScope()) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  public final S getClosestCfgRootScope() {
    S current = thisScope();
    while (!current.isCfgRootScope()) {
      current = current.getParent();
    }
    return current;
  }

  /**
   * Returns the closest container scope. This is equivalent to what the current scope would have
   * been for non-ES6 scope creators, and is thus useful for migrating code to use block scopes.
   */
  public final S getClosestContainerScope() {
    S scope = getClosestHoistScope();
    if (scope.isFunctionBlockScope()) {
      scope = scope.getParent();
      checkState(!scope.isBlockScope(), scope);
    }
    return scope;
  }

  // This is safe because any concrete subclass of AbstractScope<S> should be assignable to S.
  // While it's theoretically possible to do otherwise, such a class would be very awkward to
  // implement, and is therefore not worth worrying about.
  @SuppressWarnings("unchecked")
  private S thisScope() {
    return (S) this;
  }

  /** Performs simple validity checks on when constructing a child scope. */
  final void checkChildScope(S parent) {
    checkNotNull(parent);
    checkArgument(NodeUtil.createsScope(rootNode), rootNode);
    checkArgument(
        rootNode != parent.getRootNode(),
        "rootNode should not be the parent's root node: %s",
        rootNode);
  }

  /** Performs simple validity checks on when constructing a root scope. */
  final void checkRootScope() {
    // TODO(tbreisacher): Can we tighten this to just NodeUtil.createsScope?
    checkArgument(
        NodeUtil.createsScope(rootNode) || rootNode.isScript() || rootNode.isRoot(), rootNode);
  }

  /** Returns the nearest common parent between two scopes. */
  S getCommonParent(S other) {
    S left = thisScope();
    S right = other;
    while (left != null && right != null && left != right) {
      int leftDepth = left.getDepth();
      int rightDepth = right.getDepth();
      if (leftDepth >= rightDepth) {
        left = left.getParent();
      }
      if (leftDepth <= rightDepth) {
        right = right.getParent();
      }
    }

    checkState(left != null && left == right);
    return left;
  }

  /**
   * Whether {@code this} and the {@code other} scope have the same container scope (i.e. are in the
   * same function, or else both in the global hoist scope). This is equivalent to asking whether
   * the two scopes would be equivalent in a pre-ES2015-block-scopes view of the world.
   */
  boolean hasSameContainerScope(S other) {
    // Do identity check first as a shortcut.
    return this == other || getClosestContainerScope() == other.getClosestContainerScope();
  }

  /**
   * Returns the closest scope upon which `this` is defined, which is either the global scope or a
   * non-arrow-function scope.
   */
  final S getScopeOfThis() {
    S scope = getClosestContainerScope();
    while (!scope.isGlobal() && !NodeUtil.isNonArrowFunction(scope.getRootNode())) {
      scope = scope.getParent().getClosestContainerScope();
    }
    return scope;
  }

  /**
   * The three implicit var types, which are defined implicitly (at least) in every vanilla function
   * scope without actually being declared.
   */
  enum ImplicitVar {
    ARGUMENTS("arguments"),
    EXPORTS("exports"),
    SUPER("super"),
    // TODO(sdh): Expand THIS.isMadeByScope to check super.isMadeByScope(scope) || scope.isGlobal()
    // Currently this causes a number of problems (see b/74980936), but could eventually lead to
    // better type information.  We might also want to restrict this so that module-root scopes
    // explicitly *don't* have access to the global this, though I think this is more than just
    // returning false in isMadeByScope - rather, getVar() needs to stop checking and immediately
    // return null.
    THIS("this");

    final String name;

    ImplicitVar(String name) {
      this.name = name;
    }

    /** Whether this kind of implicit variable is created/owned by the given scope. */
    boolean isMadeByScope(AbstractScope<?, ?> scope) {
      return switch (this) {
        case EXPORTS ->
            scope.isModuleScope()
                && scope.getRootNode().getParent().getBooleanProp(Node.GOOG_MODULE);
        case SUPER, THIS ->
            scope.isStaticBlockScope()
                || NodeUtil.isNonArrowFunction(scope.getRootNode())
                || scope.isMemberFieldDefScope()
                || scope.isComputedFieldDefRhsScope();
        case ARGUMENTS -> NodeUtil.isNonArrowFunction(scope.getRootNode());
      };
    }

    static @Nullable ImplicitVar of(String name) {
      return switch (name) {
        case "arguments" -> ARGUMENTS;
        case "super" -> SUPER;
        case "this" -> THIS;
        case "exports" -> EXPORTS;
        default -> null;
      };
    }
  }
}
