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

package com.google.javascript.jscomp.modules;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * An <code>export</code>ed name in a module.
 *
 * <p>See <a href="https://www.ecma-international.org/ecma-262/9.0/index.html#table-41">the
 * ExportEntry in the ECMAScript spec.</a>
 */
// TODO(johnplaisted): Add validation tests. Current ModulePath makes this difficult as it is non
@AutoValue // TODO: b/408030907 - Migrate to a Java record
public abstract class Export {
  /**
   * The {@link Export#localName()} of anonymous ES module default exports, e.g. {@code export
   * default 0}.
   */
  public static final String DEFAULT_EXPORT_NAME = "*default*";

  /**
   * The {@link Export#exportName()} of anonymous ES module default exports, e.g. {@code export
   * default 0}.
   */
  static final String DEFAULT = "default";

  /**
   * The {@link Export#exportName()} of goog.module default exports, e.g. {@code exports = class
   * {};}, and the 'namespace' of an ES module consisting of all exported names.
   */
  public static final String NAMESPACE = "*exports*";

  // Prevent unwanted subclasses.
  Export() {}

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder exportName(@Nullable String value);

    abstract Builder moduleRequest(@Nullable String value);

    abstract Builder importName(@Nullable String value);

    abstract Builder localName(@Nullable String value);

    abstract Builder modulePath(@Nullable ModulePath value);

    abstract Builder exportNode(@Nullable Node value);

    abstract Builder nameNode(@Nullable Node value);

    abstract Builder moduleMetadata(ModuleMetadata value);

    abstract Builder closureNamespace(@Nullable String value);

    abstract Builder mutated(boolean value);

    abstract Export autoBuild();

    final Export build() {
      Export e = autoBuild();
      if (e.moduleMetadata().isEs6Module()) {
        validateEsModule(e);
      } else if (e.moduleMetadata().isGoogModule()) {
        validateGoogModule(e);
      } else {
        validateOtherModule(e);
      }
      return e;
    }

    /** Export from an ES module. */
    private void validateEsModule(Export e) {
      checkState(e.closureNamespace() == null);

      checkState(
          !"*".equals(e.importName())
              || (e.moduleRequest() != null && e.exportName() == null && e.localName() == null),
          "Star exports should not have exported / local names.");

      checkState(
          e.localName() == null || e.moduleRequest() == null,
          "Local exports should not have module requests.");
      checkState(
          e.moduleRequest() == null || e.localName() == null,
          "Reexports should not have local names.");

      checkState(
          e.moduleRequest() == null || e.importName() != null,
          "Reexports should have import names.");
      checkState(
          e.importName() == null || e.moduleRequest() != null,
          "Exports with an import name should be a reexport.");
    }

    /** Some export from a goog module. */
    private static void validateGoogModule(Export e) {
      checkState(e.closureNamespace() != null, "Exports should be associated with a namespace");
      checkState(e.exportName() != null, "Exports should be named");
      checkState(e.exportNode() != null, "Exports should have a node");
      checkState(e.localName() == null, "goog.module Exports don't set a localName");
      checkState(e.moduleRequest() == null, "goog modules cannot export from other modules");
    }

    /** Some faux export from a non-ES module. */
    private static void validateOtherModule(Export e) {
      checkNotNull(e.exportName());

      // Fields ignored for these fake exports. Should not set these.
      checkState(e.exportNode() == null);
      checkState(e.localName() == null);
      checkState(e.moduleRequest() == null);
      checkState(e.importName() == null);
      checkState(e.nameNode() == null);
    }
  }

  static Builder builder() {
    return new AutoValue_Export.Builder().mutated(false);
  }

  abstract Builder toBuilder();

  /** Returns a copy of this export that has the {@link #mutated()} bit set. */
  final Export mutatedCopy() {
    return toBuilder().mutated(true).autoBuild();
  }

  /** Returns the name of this export or null if this is an {@code export * from}. */
  public abstract @Nullable String exportName();

  /**
   * Returns the module identifier of an export from or null if this is not an {@code export {}
   * from} or {@code export * from}.
   */
  public abstract @Nullable String moduleRequest();

  /**
   * Returns the name imported from another module. * if import all or null if not an {@code export
   * {} from}.
   */
  public abstract @Nullable String importName();

  /** Returns the local name of this export or null if none. *default* if default. */
  public abstract @Nullable String localName();

  /** Returns the path of the containing module */
  public abstract @Nullable ModulePath modulePath();

  /**
   * Node that this export originates from. Used for its source location.
   *
   * <p>Null only if from non-ES module or from a missing ES module.
   */
  public abstract @Nullable Node exportNode();

  /**
   * Node that this export originates from. Used for its source location.
   *
   * <p>Null only if from non-ES6 module or an export syntax that has no associated name, e.g.
   * {@code export * from}.
   */
  public abstract @Nullable Node nameNode();

  /** The module that contains this export. */
  public abstract ModuleMetadata moduleMetadata();

  public abstract @Nullable String closureNamespace();

  /**
   * Whether or not this export is potentially mutated after module execution (i.e. in a function
   * scope).
   */
  public abstract boolean mutated();
}
