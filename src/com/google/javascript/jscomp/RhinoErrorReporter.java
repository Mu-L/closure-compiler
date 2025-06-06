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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Msg;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * An error reporter for serializing Rhino errors into our error format.
 */
class RhinoErrorReporter {

  static final DiagnosticType PARSE_ERROR =
      DiagnosticType.error("JSC_PARSE_ERROR", "Parse error. {0}");

  static final DiagnosticType TYPE_PARSE_ERROR =
      DiagnosticType.warning("JSC_TYPE_PARSE_ERROR", "{0}");

  static final DiagnosticType UNRECOGNIZED_TYPE_ERROR =
      DiagnosticType.warning("JSC_UNRECOGNIZED_TYPE_ERROR", "{0}");

  static final DiagnosticType UNRECOGNIZED_TYPEOF_ERROR =
      DiagnosticType.warning("JSC_UNRECOGNIZED_TYPEOF_ERROR", "{0}");

  static final DiagnosticType CYCLIC_INHERITANCE_ERROR =
      DiagnosticType.warning("JSC_CYCLIC_INHERITANCE_ERROR", "{0}");

  // This is separate from TYPE_PARSE_ERROR because there are many instances of this warning
  // and it is unfeasible to fix them all right away.
  static final DiagnosticType JSDOC_MISSING_BRACES_WARNING =
      DiagnosticType.disabled("JSC_JSDOC_MISSING_BRACES_WARNING", "{0}");

  // This is separate from TYPE_PARSE_ERROR because there are many instances of this warning
  // and it is unfeasible to fix them all right away.
  static final DiagnosticType JSDOC_MISSING_TYPE_WARNING =
      DiagnosticType.disabled("JSC_JSDOC_MISSING_TYPE_WARNING", "{0}");

  // Import is supported by VSCode and is pretty much a standard now.
  static final DiagnosticType JSDOC_IMPORT_TYPE_WARNING =
      DiagnosticType.disabled("JSC_JSDOC_IMPORT_TYPE_WARNING", "{0}");

  static final DiagnosticType TOO_MANY_TEMPLATE_PARAMS =
      DiagnosticType.warning("JSC_TOO_MANY_TEMPLATE_PARAMS", "{0}");

  // Special-cased errors, so that they can be configured via the
  // warnings API.
  static final DiagnosticType TRAILING_COMMA =
      DiagnosticType.error(
          "JSC_TRAILING_COMMA",
          "Parse error. IE8 (and below) will parse trailing commas in "
              + "array and object literals incorrectly. "
              + "If you are targeting newer versions of JS, "
              + "set the appropriate language_in option.");

  static final DiagnosticType DUPLICATE_PARAM =
      DiagnosticType.error("JSC_DUPLICATE_PARAM", "Parse error. {0}");

  static final DiagnosticType DUPLICATE_VISIBILITY =
      DiagnosticType.warning("JSC_DUPLICATE_VISIBILITY", "{0}");

  static final DiagnosticType UNNECESSARY_ESCAPE =
      DiagnosticType.disabled("JSC_UNNECESSARY_ESCAPE", "Parse error. {0}");

  static final DiagnosticType INVALID_PARAM =
      DiagnosticType.warning("JSC_INVALID_PARAM", "Parse error. {0}");

  static final DiagnosticType BAD_JSDOC_ANNOTATION =
      DiagnosticType.warning("JSC_BAD_JSDOC_ANNOTATION", "Parse error. {0}");

  static final DiagnosticType INVALID_ES3_PROP_NAME =
      DiagnosticType.warning(
          "JSC_INVALID_ES3_PROP_NAME",
          "Keywords and reserved words are not allowed as unquoted property "
              + "names in older versions of JavaScript. "
              + "If you are targeting newer versions of JavaScript, "
              + "set the appropriate language_in option.");

  static final DiagnosticType PARSE_TREE_TOO_DEEP =
      DiagnosticType.error("JSC_PARSE_TREE_TOO_DEEP", "Parse tree too deep.");

  static final DiagnosticType INVALID_OCTAL_LITERAL =
      DiagnosticType.warning(
          "JSC_INVALID_OCTAL_LITERAL",
          "This style of octal literal is not supported in strict mode.");

  // This check should only be enabled in the lintChecks DiagnosticGroup as part of the linter
  static final DiagnosticType STRING_CONTINUATION =
      DiagnosticType.disabled("JSC_STRING_CONTINUATION", "{0}");

  static final DiagnosticType LANGUAGE_FEATURE =
      DiagnosticType.error("JSC_LANGUAGE_FEATURE", "{0}.");

  static final DiagnosticType UNSUPPORTED_LANGUAGE_FEATURE =
      DiagnosticType.error("JSC_UNSUPPORTED_LANGUAGE_FEATURE", "{0}.");

  static final DiagnosticType UNSUPPORTED_BOUNDED_GENERIC_TYPES =
      DiagnosticType.error(
          "JSC_UNSUPPORTED_BOUNDED_GENERIC_TYPES",
          "Bounded generic semantics are currently still in development");

  static final DiagnosticType BOUNDED_GENERIC_TYPE_ERROR =
      DiagnosticType.error(
          "JSC_BOUNDED_GENERIC_TYPE_ERROR",
          "Bounded generic type error. "
              + "{0} assigned to template type {1} is not a subtype of bound {2}");

  static final DiagnosticType CLOSURE_UNAWARE_ANNOTATION_PRESENT =
      DiagnosticType.disabled(
          "JSC_CLOSURE_UNAWARE_ANNOTATION_PRESENT",
          Msg.JSDOC_CLOSURE_UNAWARE_CODE_INVALID.format());

  // A map of Rhino messages to their DiagnosticType.
  private static final ImmutableMap<Pattern, DiagnosticType> typeMap =
      ImmutableMap.<Pattern, DiagnosticType>builder()
          // Trailing comma
          .put(
              Pattern.compile("Trailing comma is not legal in an ECMA-262 object initializer"),
              TRAILING_COMMA)
          // Duplicate parameter
          .put(replacePlaceHolders("Duplicate parameter name \"{0}\""), DUPLICATE_PARAM)
          // Duplicate visiblity
          .put(replacePlaceHolders(Msg.JSDOC_EXTRA_VISIBILITY.format()), DUPLICATE_VISIBILITY)
          .put(Pattern.compile("Unnecessary escape:.*"), UNNECESSARY_ESCAPE)
          .put(Pattern.compile("^invalid param name.*"), INVALID_PARAM)
          // Unknown @annotations.
          .put(replacePlaceHolders(Msg.BAD_JSDOC_TAG.format()), BAD_JSDOC_ANNOTATION)
          .put(
              Pattern.compile(
                  "^Keywords and reserved words are not allowed as unquoted property.*"),
              INVALID_ES3_PROP_NAME)
          .put(Pattern.compile("^Too many template parameters\n.*"), TOO_MANY_TEMPLATE_PARAMS)
          // Type annotation warnings.
          .put(
              Pattern.compile(".*Type annotations should have curly braces.*"),
              JSDOC_MISSING_BRACES_WARNING)
          .put(Pattern.compile("Missing type declaration\\."), JSDOC_MISSING_TYPE_WARNING)
          // Unresolved types that aren't forward declared.
          .put(Pattern.compile(".*Unknown type.*"), UNRECOGNIZED_TYPE_ERROR)
          .put(Pattern.compile(".*Unknown type.*\n.*"), UNRECOGNIZED_TYPE_ERROR)
          // Unrecognized `typeof some.prop` errors
          .put(Pattern.compile("^Missing type for `typeof` value.*"), UNRECOGNIZED_TYPEOF_ERROR)
          // Cyclic inheritance errors
          .put(
              Pattern.compile("^Cycle detected in inheritance chain of type .*"),
              CYCLIC_INHERITANCE_ERROR)
          // Import annotation errors.
          .put(
              Pattern.compile("^Bad type annotation. Import in typedef.*"),
              JSDOC_IMPORT_TYPE_WARNING)
          // Type annotation errors.
          .put(Pattern.compile("^Bad type annotation.*"), TYPE_PARSE_ERROR)
          .put(Pattern.compile("constructed type must be an object type"), TYPE_PARSE_ERROR)
          // Parse tree too deep.
          .put(Pattern.compile("Too deep recursion while parsing"), PARSE_TREE_TOO_DEEP)
          // Old-style octal literals
          .put(Pattern.compile("^Octal .*literal.*"), INVALID_OCTAL_LITERAL)
          .put(Pattern.compile("^String continuations.*"), STRING_CONTINUATION)
          .put(Pattern.compile("^This language feature is only supported for .*"), LANGUAGE_FEATURE)
          .put(
              Pattern.compile(
                  "^This language feature is not currently supported by the compiler:" + " .*"),
              UNSUPPORTED_LANGUAGE_FEATURE)
          .put(
              Pattern.compile("Bounded generic semantics are currently still in development"),
              UNSUPPORTED_BOUNDED_GENERIC_TYPES)
          .put(Pattern.compile("^Bounded generic type error.*"), BOUNDED_GENERIC_TYPE_ERROR)
          .put(
              replacePlaceHolders(Msg.JSDOC_CLOSURE_UNAWARE_CODE_INVALID.format()),
              CLOSURE_UNAWARE_ANNOTATION_PRESENT)
          .buildOrThrow();

  private final ErrorHandler internalReporter;

  /**
   * For each message such as "Not a good use of {0}", replace the place holder {0} with a wild card
   * that matches all possible strings. Also put the any non-place-holder in quotes for regex
   * matching later.
   */
  private static Pattern replacePlaceHolders(String s) {
    s = Pattern.quote(s);
    return Pattern.compile(s.replaceAll("\\{\\d+\\}", "\\\\E.*\\\\Q"));
  }

  private RhinoErrorReporter(ErrorHandler internalReporter) {
    this.internalReporter = internalReporter;
  }

  public static ErrorReporter forOldRhino(ErrorHandler internalReporter) {
    return new OldRhinoErrorReporter(internalReporter);
  }

  void warningAtLine(String message, String sourceName, int line,
      int lineOffset) {
    internalReporter.report(
        null, makeError(message, sourceName, line, lineOffset, CheckLevel.WARNING));
  }

  void errorAtLine(String message, String sourceName, int line,
      int lineOffset) {
    internalReporter.report(
        null, makeError(message, sourceName, line, lineOffset, CheckLevel.ERROR));
  }

  protected static @Nullable DiagnosticType mapError(String message) {
    for (Entry<Pattern, DiagnosticType> entry : typeMap.entrySet()) {
      if (entry.getKey().matcher(message).matches()) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static JSError makeError(
      String message, String sourceName, int line, int lineOffset, CheckLevel defaultLevel) {
    // Try to see if the message is one of the rhino errors we want to
    // expose as DiagnosticType by matching it with the regex key.
    DiagnosticType type = mapError(message);
    JSError.Builder builder =
        JSError.builder(type != null ? type : PARSE_ERROR, message)
            .setSourceLocation(sourceName, line, lineOffset);

    // If a specific DiagnosticType is present, its default level overrides the defaultLevel
    // passed to this method.
    if (type == null) {
      builder.setLevel(defaultLevel);
    }

    return builder.build();
  }

  private static class OldRhinoErrorReporter extends RhinoErrorReporter
      implements ErrorReporter {

    private OldRhinoErrorReporter(ErrorHandler internalReporter) {
      super(internalReporter);
    }

    @Override
    public void error(String message, String sourceName, int line,
        int lineOffset) {
      super.errorAtLine(message, sourceName, line, lineOffset);
    }

    @Override
    public void warning(String message, String sourceName, int line,
        int lineOffset) {
      super.warningAtLine(message, sourceName, line, lineOffset);
    }
  }
}
