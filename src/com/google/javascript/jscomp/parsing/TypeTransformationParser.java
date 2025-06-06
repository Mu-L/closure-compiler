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

package com.google.javascript.jscomp.parsing;

import static com.google.javascript.jscomp.base.JSCompDoubles.isExactInt32;
import static java.lang.Double.isNaN;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.parsing.ParserRunner.ParseResult;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Msg;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;

/**
 * A parser for the type transformation expressions (TTL-Exp) as in
 * {@code @template T := TTL-Exp =:}
 */
public final class TypeTransformationParser {

  private final String typeTransformationString;
  private Node typeTransformationAst;
  private final StaticSourceFile sourceFile;
  private final ErrorReporter errorReporter;
  private final int templateLineno;
  private final int templateCharno;

  private static final int VAR_ARGS = Integer.MAX_VALUE;
  // Set the length of every TTL node as the length of "@template" (which is 9).
  // This is used for error message logging where we underline the error location with ^^^^^^^^^.
  private static final int TTL_NODE_LENGTH = "@template".length();

  /** The classification of the keywords */
  public static enum OperationKind {
    TYPE_CONSTRUCTOR,
    OPERATION,
    STRING_PREDICATE,
    TYPE_PREDICATE,
    TYPEVAR_PREDICATE
  }

  /** Keywords of the type transformation language */
  public static enum Keywords {
    ALL("all", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    COND("cond", 3, 3, OperationKind.OPERATION),
    EQ("eq", 2, 2, OperationKind.TYPE_PREDICATE),
    ISCTOR("isCtor", 1, 1, OperationKind.TYPE_PREDICATE),
    ISDEFINED("isDefined", 1, 1, OperationKind.TYPEVAR_PREDICATE),
    ISRECORD("isRecord", 1, 1, OperationKind.TYPE_PREDICATE),
    ISTEMPLATIZED("isTemplatized", 1, 1, OperationKind.TYPE_PREDICATE),
    ISUNKNOWN("isUnknown", 1, 1, OperationKind.TYPE_PREDICATE),
    INSTANCEOF("instanceOf", 1, 1, OperationKind.OPERATION),
    MAPUNION("mapunion", 2, 2, OperationKind.OPERATION),
    MAPRECORD("maprecord", 2, 2, OperationKind.OPERATION),
    NONE("none", 0, 0, OperationKind.TYPE_CONSTRUCTOR),
    PRINTTYPE("printType", 2, 2, OperationKind.OPERATION),
    PROPTYPE("propType", 2, 2, OperationKind.OPERATION),
    RAWTYPEOF("rawTypeOf", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    SUB("sub", 2, 2, OperationKind.TYPE_PREDICATE),
    STREQ("streq", 2, 2, OperationKind.STRING_PREDICATE),
    RECORD("record", 1, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TEMPLATETYPEOF("templateTypeOf", 2, 2, OperationKind.TYPE_CONSTRUCTOR),
    TYPE("type", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    TYPEEXPR("typeExpr", 1, 1, OperationKind.TYPE_CONSTRUCTOR),
    TYPEOFVAR("typeOfVar", 1, 1, OperationKind.OPERATION),
    UNION("union", 2, VAR_ARGS, OperationKind.TYPE_CONSTRUCTOR),
    UNKNOWN("unknown", 0, 0, OperationKind.TYPE_CONSTRUCTOR);

    public final String name;
    public final int minParamCount;
    public final int maxParamCount;
    public final OperationKind kind;

    Keywords(String name, int minParamCount, int maxParamCount,
        OperationKind kind) {
      this.name = name;
      this.minParamCount = minParamCount;
      this.maxParamCount = maxParamCount;
      this.kind = kind;
    }
  }

  public TypeTransformationParser(String typeTransformationString,
      StaticSourceFile sourceFile, ErrorReporter errorReporter,
      int templateLineno, int templateCharno) {
    this.typeTransformationString = typeTransformationString;
    this.sourceFile = sourceFile;
    this.errorReporter = errorReporter;
    this.templateLineno = templateLineno;
    this.templateCharno = templateCharno;
  }

  public Node getTypeTransformationAst() {
    return typeTransformationAst;
  }

  private void addNewWarning(Msg messageId, String messageArg) {
    // TODO(lpino): Use the exact lineno and charno, it is currently using
    // the lineno and charno of the parent @template
    // TODO(lpino): Use only constants as parameters of this method
    errorReporter.warning(
        "Bad type annotation. " + messageId.format(messageArg),
        sourceFile.getName(),
        templateLineno,
        templateCharno);
  }

  private Keywords nameToKeyword(String s) {
    return Keywords.valueOf(Ascii.toUpperCase(s));
  }

  private boolean isValidKeyword(String name) {
    for (Keywords k : Keywords.values()) {
      if (k.name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isOperationKind(String name, OperationKind kind) {
    return isValidKeyword(name) && nameToKeyword(name).kind == kind;
  }

  private boolean isValidStringPredicate(String name) {
    return isOperationKind(name, OperationKind.STRING_PREDICATE);
  }

  private boolean isValidTypePredicate(String name) {
    return isOperationKind(name, OperationKind.TYPE_PREDICATE);
  }

  private boolean isValidTypevarPredicate(String name) {
    return isOperationKind(name, OperationKind.TYPEVAR_PREDICATE);
  }

  private boolean isBooleanOperation(Node n) {
    return n.isAnd() || n.isOr() || n.isNot();
  }

  private boolean isValidPredicate(String name) {
    return isValidStringPredicate(name)
        || isValidTypePredicate(name)
        || isValidTypevarPredicate(name);
  }

  private int getFunctionParamCount(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getSecondChild().getChildCount();
  }

  private Node getFunctionBody(Node n) {
    Preconditions.checkArgument(n.isFunction(),
        "Expected a function node, found %s", n);
    return n.getChildAtIndex(2);
  }

  private String getCallName(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getFirstChild().getString();
  }

  private Node getCallArgument(Node n, int i) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildAtIndex(i + 1);
  }

  private int getCallParamCount(Node n) {
    Preconditions.checkArgument(n.isCall(),
        "Expected a call node, found %s", n);
    return n.getChildCount() - 1;
  }

  private boolean isTypeVar(Node n) {
    return n.isName();
  }

  private boolean isTypeName(Node n) {
    return n.isStringLit();
  }

  private boolean isOperation(Node n) {
    return n.isCall();
  }

  /**
   * A valid expression is either:
   * - NAME for a type variable
   * - STRING for a type name
   * - CALL for the other expressions
   */
  private boolean isValidExpression(Node e) {
    return isTypeVar(e) || isTypeName(e) || isOperation(e);
  }

  private void warnInvalid(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID, msg);
  }

  private void warnInvalidExpression(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID_EXPRESSION, msg);
  }

  private void warnMissingParam(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_MISSING_PARAM, msg);
  }

  private void warnExtraParam(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_EXTRA_PARAM, msg);
  }

  private void warnInvalidInside(String msg) {
    addNewWarning(Msg.JSDOC_TYPETRANSFORMATION_INVALID_INSIDE, msg);
  }

  private boolean checkParameterCount(Node expr, Keywords keyword) {
    int paramCount = getCallParamCount(expr);
    if (paramCount < keyword.minParamCount) {
      warnMissingParam(keyword.name);
      return false;
    }
    if (paramCount > keyword.maxParamCount) {
      warnExtraParam(keyword.name);
      return false;
    }
    return true;
  }

  /**
   * Takes a type transformation expression, transforms it to an AST using
   * the ParserRunner of the JSCompiler and then verifies that it is a valid
   * AST.
   * @return true if the parsing was successful otherwise it returns false and
   * at least one warning is reported
   */
  public boolean parseTypeTransformation() {
    Config config =
        Config.builder()
            .setLanguageMode(Config.LanguageMode.ES_NEXT)
            .setStrictMode(Config.StrictMode.SLOPPY)
            .build();
    // TODO(lpino): ParserRunner reports errors if the expression is not
    // ES6 valid. We need to abort the validation of the type transformation
    // whenever an error is reported.
    ParseResult result = ParserRunner.parse(
        sourceFile, typeTransformationString, config, errorReporter);
    Node ast = result.ast;
    // Check that the expression is a script with an expression result
    if (ast == null
        || !ast.isScript()
        || !ast.hasChildren()
        || !ast.getFirstChild().isExprResult()) {
      warnInvalidExpression("type transformation");
      return false;
    }

    Node expr = ast.getFirstFirstChild();
    // The AST of the type transformation must correspond to a valid expression
    if (!validTypeTransformationExpression(expr)) {
      // No need to add a new warning because the validation does it
      return false;
    }
    fixTTLNodeLineNoCharNoAndLength(expr);
    // Store the result if the AST is valid
    typeTransformationAst = expr;
    return true;
  }

  /**
   * Set the lineno/charno of the TTL node to the lineno/charno of the "@" in the JSDoc's template
   * annotation. This lineno/charno of the "@" is initialized by JsDocInfoParser when parsing TTLs.
   *
   * <p>The TTL AST currently has lineno/charno relative to its position within the JSDoc, and
   * `fixTTLNodeLineNoCharNoAndLength` changes this to be relative to its position in the source JS
   * file.
   *
   * <p>We are using the lineno/charno of "@" because adjusting the TTL AST node's lineno/charno to
   * be more accurate is tricky, especially for handling multiline TTL expressions. If we set an
   * inaccurate lineno/charno, the compiler may crash with an "index out of bounds" error when
   * logging errors that occur in the TTL expression. Instead, we will use the lineno/charno of "@"
   * to safely highlight which JSDoc annotation an error occurs in.
   */
  private void fixTTLNodeLineNoCharNoAndLength(Node expr) {
    expr.setLinenoCharno(templateLineno, templateCharno);
    expr.setLength(TTL_NODE_LENGTH);
    for (Node child = expr.getFirstChild(); child != null; child = child.getNext()) {
      fixTTLNodeLineNoCharNoAndLength(child);
    }
  }

  /**
   * A template type expression must be of the form type(typename, TTLExp,...)
   * or type(typevar, TTLExp...)
   */
  private boolean validTemplateTypeExpression(Node expr) {
    // The expression must have at least three children the type keyword,
    // a type name (or type variable) and a type expression
    if (!checkParameterCount(expr, Keywords.TYPE)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    // The first parameter must be a type variable or a type name
    Node firstParam = getCallArgument(expr, 0);
    if (!isTypeVar(firstParam) && !isTypeName(firstParam)) {
      warnInvalid("type name or type variable");
      warnInvalidInside("template type operation");
      return false;
    }
    // The rest of the parameters must be valid type expressions
    for (int i = 1; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("template type operation");
        return false;
      }
    }
    return true;
  }

  /**
   * A Union type expression must be a valid type variable or
   * a union(TTLExp, TTLExp, ...)
   */
  private boolean validUnionTypeExpression(Node expr) {
    // The expression must have at least three children: The union keyword and
    // two type expressions
    if (!checkParameterCount(expr, Keywords.UNION)) {
      return false;
    }
    int paramCount = getCallParamCount(expr);
    // Check if each of the members of the union is a valid type expression
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("union type");
        return false;
      }
    }
    return true;
  }

  /**
   * A none type expression must be of the form: none()
   */
  private boolean validNoneTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.NONE);
  }

  /**
   * An all type expression must be of the form: all()
   */
  private boolean validAllTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.ALL);
  }

  /**
   * An unknown type expression must be of the form: unknown()
   */
  private boolean validUnknownTypeExpression(Node expr) {
    // The expression must have no children
    return checkParameterCount(expr, Keywords.UNKNOWN);
  }

  /**
   * A raw type expression must be of the form rawTypeOf(TTLExp)
   */
  private boolean validRawTypeOfTypeExpression(Node expr) {
    // The expression must have two children. The rawTypeOf keyword and the
    // parameter
    if (!checkParameterCount(expr, Keywords.RAWTYPEOF)) {
      return false;
    }
    // The parameter must be a valid type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.RAWTYPEOF.name);
      return false;
    }
    return true;
  }

  /**
   * A template type of expression must be of the form
   * templateTypeOf(TTLExp, index)
   */
  private boolean validTemplateTypeOfExpression(Node expr) {
    // The expression must have three children. The templateTypeOf keyword, a
    // templatized type and an index
    if (!checkParameterCount(expr, Keywords.TEMPLATETYPEOF)) {
      return false;
    }
    // The parameter must be a valid type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    if (!getCallArgument(expr, 1).isNumber()) {
      warnInvalid("index");
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    double index = getCallArgument(expr, 1).getDouble();
    if (isNaN(index) || !isExactInt32(index)) {
      warnInvalid("index");
      warnInvalidInside(Keywords.TEMPLATETYPEOF.name);
      return false;
    }
    return true;
  }

  /**
   * A record must be a valid type transformation expression or a node of the form:
   * {prop:TTLExp, prop:TTLExp, ...}
   * Notice that the values are mandatory and they must be valid type
   * transformation expressions
   */
  private boolean validRecordParam(Node expr) {
    if (expr.isObjectLit()) {
      // Each value of a property must be a valid expression
      for (Node prop = expr.getFirstChild(); prop != null; prop = prop.getNext()) {
        if (prop.isShorthandProperty()) {
          warnInvalid("property, missing type");
          return false;
        } else if (!validTypeTransformationExpression(prop.getFirstChild())) {
          return false;
        }
      }
    } else if (!validTypeTransformationExpression(expr)) {
      return false;
    }
    return true;
  }

  /**
   * A record type expression must be of the form:
   * record(RecordExp, RecordExp, ...)
   */
  private boolean validRecordTypeExpression(Node expr) {
    // The expression must have at least two children. The record keyword and
    // a record expression
    if (!checkParameterCount(expr, Keywords.RECORD)) {
      return false;
    }
    // Each child must be a valid record
    for (int i = 0; i < getCallParamCount(expr); i++) {
      if (!validRecordParam(getCallArgument(expr, i))) {
        warnInvalidInside(Keywords.RECORD.name);
        return false;
      }
    }
    return true;
  }

  private boolean validNativeTypeExpr(Node expr) {
    // The expression must have two children:
    // - The typeExpr keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.TYPEEXPR)) {
      return false;
    }
    Node typeString = getCallArgument(expr, 0);
    if (!typeString.isStringLit()) {
      warnInvalidExpression("native type");
      warnInvalidInside(Keywords.TYPEEXPR.name);
      return false;
    }
    Node typeExpr = JsDocInfoParser.parseTypeString(typeString.getString());
    typeString.detach();
    expr.addChildToBack(typeExpr);
    return true;
  }

  /**
   * A TTL type expression must be a union type, a template type, a record type
   * or any of the type predicates (none, rawTypeOf, templateTypeOf).
   */
  private boolean validTypeExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    return switch (keyword) {
      case TYPE -> validTemplateTypeExpression(expr);
      case UNION -> validUnionTypeExpression(expr);
      case NONE -> validNoneTypeExpression(expr);
      case ALL -> validAllTypeExpression(expr);
      case UNKNOWN -> validUnknownTypeExpression(expr);
      case RAWTYPEOF -> validRawTypeOfTypeExpression(expr);
      case TEMPLATETYPEOF -> validTemplateTypeOfExpression(expr);
      case RECORD -> validRecordTypeExpression(expr);
      case TYPEEXPR -> validNativeTypeExpr(expr);
      default -> throw new IllegalStateException("Invalid type expression");
    };
  }

  private boolean validTypePredicate(Node expr, int paramCount) {
    // All the types must be valid type expressions
    for (int i = 0; i < paramCount; i++) {
      if (!validTypeTransformationExpression(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean isValidStringParam(Node expr) {
    if (!expr.isName() && !expr.isStringLit()) {
      warnInvalid("string");
      return false;
    }
    if (expr.getString().isEmpty()) {
      warnInvalid("string parameter");
      return false;
    }
    return true;
  }

  private boolean validStringPredicate(Node expr, int paramCount) {
    // Each parameter must be valid string parameter
    for (int i = 0; i < paramCount; i++) {
      if (!isValidStringParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean validTypevarParam(Node expr) {
    if (!isTypeVar(expr)) {
      warnInvalid("name");
      return false;
    }
    return true;
  }

  private boolean validTypevarPredicate(Node expr, int paramCount) {
    // Each parameter must be valid string parameter
    for (int i = 0; i < paramCount; i++) {
      if (!validTypevarParam(getCallArgument(expr, i))) {
        warnInvalidInside("boolean");
        return false;
      }
    }
    return true;
  }

  private boolean validBooleanOperation(Node expr) {
    boolean valid;
    if (expr.isNot()) {
      valid = validBooleanExpression(expr.getFirstChild());
    } else {
      valid = validBooleanExpression(expr.getFirstChild())
          && validBooleanExpression(expr.getSecondChild());
    }
    if (!valid) {
      warnInvalidInside("boolean");
      return false;
    }
    return true;
  }

  /**
   * A boolean expression must be a boolean predicate or a boolean
   * type predicate
   */
  private boolean validBooleanExpression(Node expr) {
    if (isBooleanOperation(expr)) {
      return validBooleanOperation(expr);
    }

    if (!isOperation(expr)) {
      warnInvalidExpression("boolean");
      return false;
    }
    if (!isValidPredicate(getCallName(expr))) {
      warnInvalid("boolean predicate");
      return false;
    }
    Keywords keyword = nameToKeyword(getCallName(expr));
    if (!checkParameterCount(expr, keyword)) {
      return false;
    }
    return switch (keyword.kind) {
      case TYPE_PREDICATE -> validTypePredicate(expr, getCallParamCount(expr));
      case STRING_PREDICATE -> validStringPredicate(expr, getCallParamCount(expr));
      case TYPEVAR_PREDICATE -> validTypevarPredicate(expr, getCallParamCount(expr));
      default -> throw new IllegalStateException("Invalid boolean expression");
    };
  }

  /**
   * A conditional type transformation expression must be of the
   * form cond(BoolExp, TTLExp, TTLExp)
   */
  private boolean validConditionalExpression(Node expr) {
    // The expression must have four children:
    // - The cond keyword
    // - A boolean expression
    // - A type transformation expression with the 'if' branch
    // - A type transformation expression with the 'else' branch
    if (!checkParameterCount(expr, Keywords.COND)) {
      return false;
    }
    // Check for the validity of the boolean and the expressions
    if (!validBooleanExpression(getCallArgument(expr, 0))) {
      warnInvalidInside("conditional");
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside("conditional");
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 2))) {
      warnInvalidInside("conditional");
      return false;
    }
    return true;
  }

  /**
   * A mapunion type transformation expression must be of the form
   * mapunion(TTLExp, (typevar) => TTLExp).
   */
  private boolean validMapunionExpression(Node expr) {
    // The expression must have four children:
    // - The mapunion keyword
    // - A union type expression
    // - A map function
    if (!checkParameterCount(expr, Keywords.MAPUNION)) {
      return false;
    }
    // The second child must be a valid union type expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    // The third child must be a function
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    // The map function must have only one parameter
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 1) {
      warnMissingParam("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    if (mapFnParamCount > 1) {
      warnExtraParam("map function");
      warnInvalidInside(Keywords.MAPUNION.name);
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body");
      return false;
    }
    return true;
  }

  /**
   * A maprecord type transformation expression must be of the form
   * maprecord(TTLExp, (typevar, typevar) => TTLExp).
   */
  private boolean validMaprecordExpression(Node expr) {
    // The expression must have four children:
    // - The maprecord keyword
    // - A type expression
    // - A map function
    if (!checkParameterCount(expr, Keywords.MAPRECORD)) {
      return false;
    }
    // The second child must be a valid expression
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    // The third child must be a function
    if (!getCallArgument(expr, 1).isFunction()) {
      warnInvalid("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    Node mapFn = getCallArgument(expr, 1);
    // The map function must have exactly two parameters
    int mapFnParamCount = getFunctionParamCount(mapFn);
    if (mapFnParamCount < 2) {
      warnMissingParam("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    if (mapFnParamCount > 2) {
      warnExtraParam("map function");
      warnInvalidInside(Keywords.MAPRECORD.name);
      return false;
    }
    // The body must be a valid type transformation expression
    Node mapFnBody = getFunctionBody(mapFn);
    if (!validTypeTransformationExpression(mapFnBody)) {
      warnInvalidInside("map function body");
      return false;
    }
    return true;
  }

  /**
   * A typeOfVar expression must be of the form typeOfVar('name')
   */
  private boolean validTypeOfVarExpression(Node expr) {
 // The expression must have two children:
    // - The typeOfVar keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.TYPEOFVAR)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("name");
      warnInvalidInside(Keywords.TYPEOFVAR.name);
      return false;
    }
    return true;
  }

  /**
   * A typeOfVar expression must be of the form instanceOf('name')
   */
  private boolean validInstanceOfExpression(Node expr) {
    // The expression must have two children:
    // - The instanceOf keyword
    // - A string
    if (!checkParameterCount(expr, Keywords.INSTANCEOF)) {
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 0))) {
      warnInvalidInside(Keywords.INSTANCEOF.name);
      return false;
    }
    return true;
  }

  private boolean validPrintTypeExpression(Node expr) {
    // The expression must have three children. The printType keyword, a
    // message and a type transformation expression
    if (!checkParameterCount(expr, Keywords.PRINTTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("message");
      warnInvalidInside(Keywords.PRINTTYPE.name);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PRINTTYPE.name);
      return false;
    }
    return true;
  }

  private boolean validPropTypeExpression(Node expr) {
    // The expression must have three children. The propType keyword, a
    // a string and a type transformation expression
    if (!checkParameterCount(expr, Keywords.PROPTYPE)) {
      return false;
    }
    if (!getCallArgument(expr, 0).isStringLit()) {
      warnInvalid("property name");
      warnInvalidInside(Keywords.PROPTYPE.name);
      return false;
    }
    if (!validTypeTransformationExpression(getCallArgument(expr, 1))) {
      warnInvalidInside(Keywords.PROPTYPE.name);
      return false;
    }
    return true;
  }

  /**
   * An operation expression is a cond or a mapunion
   */
  private boolean validOperationExpression(Node expr) {
    String name = getCallName(expr);
    Keywords keyword = nameToKeyword(name);
    return switch (keyword) {
      case COND -> validConditionalExpression(expr);
      case MAPUNION -> validMapunionExpression(expr);
      case MAPRECORD -> validMaprecordExpression(expr);
      case TYPEOFVAR -> validTypeOfVarExpression(expr);
      case INSTANCEOF -> validInstanceOfExpression(expr);
      case PRINTTYPE -> validPrintTypeExpression(expr);
      case PROPTYPE -> validPropTypeExpression(expr);
      default -> throw new IllegalStateException("Invalid type transformation operation");
    };
  }

  /**
   * Checks the structure of the AST of a type transformation expression
   * in @template T := TTLExp =:
   */
  private boolean validTypeTransformationExpression(Node expr) {
    if (!isValidExpression(expr)) {
      warnInvalidExpression("type transformation");
      return false;
    }
    if (isTypeVar(expr) || isTypeName(expr)) {
      return true;
    }
    // Check for valid keyword
    String name = getCallName(expr);
    if (!isValidKeyword(name)) {
      warnInvalidExpression("type transformation");
      return false;
    }
    Keywords keyword = nameToKeyword(name);
    // Check the rest of the expression depending on the kind
    return switch (keyword.kind) {
      case TYPE_CONSTRUCTOR -> validTypeExpression(expr);
      case OPERATION -> validOperationExpression(expr);
      default -> throw new IllegalStateException("Invalid type transformation expression");
    };
  }
}
