/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   John Lenz
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompDoubles.isPositive;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An AST construction helper class
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class IR {

  private IR() {}

  public static Node empty() {
    return new Node(Token.EMPTY);
  }

  public static Node export(Node declaration) {
    return new Node(Token.EXPORT, declaration);
  }

  public static Node importNode(Node name, Node importSpecs, Node moduleIdentifier) {
    checkState(name.isName() || name.isEmpty(), name);
    checkState(
        importSpecs.isImportSpec() || importSpecs.isImportStar() || importSpecs.isEmpty(),
        importSpecs);
    checkState(moduleIdentifier.isStringLit(), moduleIdentifier);
    return new Node(Token.IMPORT, name, importSpecs, moduleIdentifier);
  }

  public static Node importStar(String name) {
    return Node.newString(Token.IMPORT_STAR, name);
  }

  public static Node function(Node name, Node params, Node body) {
    checkState(name.isName());
    checkState(params.isParamList());
    checkState(body.isBlock());
    return new Node(Token.FUNCTION, name, params, body);
  }

  public static Node arrowFunction(Node name, Node params, Node body) {
    checkState(name.isName());
    checkState(params.isParamList());
    checkState(body.isBlock() || mayBeExpression(body));
    Node func = new Node(Token.FUNCTION, name, params, body);
    func.setIsArrowFunction(true);
    return func;
  }

  public static Node paramList(Node... params) {
    Node paramList = new Node(Token.PARAM_LIST);
    for (Node param : params) {
      checkState(param.isName() || param.isRest() || param.isDefaultValue());
      paramList.addChildToBack(param);
    }
    return paramList;
  }

  public static Node root(Node... rootChildren) {
    Node root = new Node(Token.ROOT);
    for (Node child : rootChildren) {
      checkState(child.getToken() == Token.ROOT || child.getToken() == Token.SCRIPT);
      root.addChildToBack(child);
    }
    return root;
  }

  public static Node block() {
    return new Node(Token.BLOCK);
  }

  public static Node block(Node stmt) {
    checkState(mayBeStatement(stmt), "Block node cannot contain %s", stmt.getToken());
    return new Node(Token.BLOCK, stmt);
  }

  public static Node block(Node... stmts) {
    Node block = block();
    for (Node stmt : stmts) {
      checkState(mayBeStatement(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node block(List<Node> stmts) {
    Node paramList = block();
    for (Node stmt : stmts) {
      checkState(mayBeStatement(stmt));
      paramList.addChildToBack(stmt);
    }
    return paramList;
  }

  private static Node blockUnchecked(Node stmt) {
    return new Node(Token.BLOCK, stmt);
  }

  public static Node script() {
    // TODO(johnlenz): finish setting up the SCRIPT node
    return new Node(Token.SCRIPT);
  }

  public static Node script(Node... stmts) {
    Node block = script();
    for (Node stmt : stmts) {
      checkState(mayBeStatementNoReturn(stmt));
      block.addChildToBack(stmt);
    }
    return block;
  }

  public static Node script(List<Node> stmts) {
    Node paramList = script();
    for (Node stmt : stmts) {
      checkState(mayBeStatementNoReturn(stmt));
      paramList.addChildToBack(stmt);
    }
    return paramList;
  }

  public static Node var(Node lhs, Node value) {
    return declaration(lhs, value, Token.VAR);
  }

  public static Node var(Node lhs) {
    return declaration(lhs, Token.VAR);
  }

  public static Node let(Node lhs, Node value) {
    return declaration(lhs, value, Token.LET);
  }

  public static Node let(Node lhs) {
    return declaration(lhs, Token.LET);
  }

  public static Node constNode(Node lhs, Node value) {
    return declaration(lhs, value, Token.CONST);
  }

  public static Node declaration(Node lhs, Token type) {
    checkState(lhs.isName() || lhs.isDestructuringPattern() || lhs.isDestructuringLhs(), lhs);
    if (lhs.isDestructuringPattern()) {
      lhs = new Node(Token.DESTRUCTURING_LHS, lhs);
    }
    return new Node(type, lhs);
  }

  public static Node declaration(Node lhs, Node value, Token type) {
    if (lhs.isName()) {
      checkState(!lhs.hasChildren());
    } else {
      checkState(lhs.isArrayPattern() || lhs.isObjectPattern());
      lhs = new Node(Token.DESTRUCTURING_LHS, lhs);
    }
    Preconditions.checkState(mayBeExpression(value), "%s can't be an expression", value);

    lhs.addChildToBack(value);
    return new Node(type, lhs);
  }

  public static Node returnNode() {
    return new Node(Token.RETURN);
  }

  public static Node returnNode(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.RETURN, expr);
  }

  public static Node yieldNode(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.YIELD, expr);
  }

  public static Node await(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.AWAIT, expr);
  }

  public static Node throwNode(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.THROW, expr);
  }

  public static Node exprResult(Node expr) {
    checkState(mayBeExpression(expr), expr);
    return new Node(Token.EXPR_RESULT, expr);
  }

  public static Node ifNode(Node cond, Node then) {
    checkState(mayBeExpression(cond));
    checkState(then.isBlock());
    return new Node(Token.IF, cond, then);
  }

  public static Node ifNode(Node cond, Node then, Node elseNode) {
    checkState(mayBeExpression(cond));
    checkState(then.isBlock());
    checkState(elseNode.isBlock());
    return new Node(Token.IF, cond, then, elseNode);
  }

  public static Node doNode(Node body, Node cond) {
    checkState(body.isBlock());
    checkState(mayBeExpression(cond));
    return new Node(Token.DO, body, cond);
  }

  public static Node whileNode(Node cond, Node body) {
    checkState(body.isBlock());
    checkState(mayBeExpression(cond));
    return new Node(Token.WHILE, cond, body);
  }

  public static Node forIn(Node target, Node cond, Node body) {
    checkState(target.isVar() || mayBeExpression(target));
    checkState(mayBeExpression(cond));
    checkState(body.isBlock());
    return new Node(Token.FOR_IN, target, cond, body);
  }

  public static Node forNode(Node init, Node cond, Node incr, Node body) {
    checkState(init.isVar() || init.isLet() || init.isConst() || mayBeExpressionOrEmpty(init));
    checkState(mayBeExpressionOrEmpty(cond));
    checkState(mayBeExpressionOrEmpty(incr));
    checkState(body.isBlock());
    Node r = new Node(Token.FOR, init, cond, incr);
    r.addChildToBack(body);
    return r;
  }

  public static Node switchNode(Node cond, Node... cases) {
    checkState(mayBeExpression(cond));
    Node switchNode = new Node(Token.SWITCH, cond);
    Node switchBody = new Node(Token.SWITCH_BODY);
    switchNode.addChildToBack(switchBody);
    for (Node caseNode : cases) {
      checkState(caseNode.isCase() || caseNode.isDefaultCase());
      switchBody.addChildToBack(caseNode);
    }
    return switchNode;
  }

  public static Node caseNode(Node expr, Node body) {
    checkState(mayBeExpression(expr));
    checkState(body.isBlock());
    body.setIsAddedBlock(true);
    return new Node(Token.CASE, expr, body);
  }

  public static Node defaultCase(Node body) {
    checkState(body.isBlock());
    body.setIsAddedBlock(true);
    return new Node(Token.DEFAULT_CASE, body);
  }

  public static Node label(Node name, Node stmt) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    checkState(mayBeStatement(stmt));
    return new Node(Token.LABEL, name, stmt);
  }

  public static Node labelName(String name) {
    checkState(!name.isEmpty());
    return Node.newString(Token.LABEL_NAME, name);
  }

  public static Node tryFinally(Node tryBody, Node finallyBody) {
    checkState(tryBody.isBlock());
    checkState(finallyBody.isBlock());
    Node catchBody = block().srcrefIfMissing(tryBody);
    return new Node(Token.TRY, tryBody, catchBody, finallyBody);
  }

  public static Node tryCatch(Node tryBody, Node catchNode) {
    checkState(tryBody.isBlock());
    checkState(catchNode.isCatch());
    Node catchBody = blockUnchecked(catchNode).srcrefIfMissing(catchNode);
    return new Node(Token.TRY, tryBody, catchBody);
  }

  public static Node tryCatchFinally(Node tryBody, Node catchNode, Node finallyBody) {
    checkState(finallyBody.isBlock());
    Node tryNode = tryCatch(tryBody, catchNode);
    tryNode.addChildToBack(finallyBody);
    return tryNode;
  }

  public static Node catchNode(Node expr, Node body) {
    checkState(expr.isName());
    checkState(body.isBlock());
    return new Node(Token.CATCH, expr, body);
  }

  public static Node breakNode() {
    return new Node(Token.BREAK);
  }

  public static Node breakNode(Node name) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    return new Node(Token.BREAK, name);
  }

  public static Node continueNode() {
    return new Node(Token.CONTINUE);
  }

  public static Node continueNode(Node name) {
    // TODO(johnlenz): additional validation here.
    checkState(name.isLabelName());
    return new Node(Token.CONTINUE, name);
  }

  public static Node call(Node target, Node... args) {
    Node call = new Node(Token.CALL, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg) || arg.isSpread(), arg);
      call.addChildToBack(arg);
    }
    return call;
  }

  public static Node startOptChainCall(Node target, Node... args) {
    Node call = new Node(Token.OPTCHAIN_CALL, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg) || arg.isSpread(), arg);
      call.addChildToBack(arg);
    }
    call.setIsOptionalChainStart(true);
    return call;
  }

  public static Node continueOptChainCall(Node target, Node... args) {
    Node call = new Node(Token.OPTCHAIN_CALL, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg) || arg.isSpread(), arg);
      call.addChildToBack(arg);
    }
    call.setIsOptionalChainStart(false);
    return call;
  }

  public static Node newNode(Node target, Node... args) {
    Node newcall = new Node(Token.NEW, target);
    for (Node arg : args) {
      checkState(mayBeExpression(arg) || arg.isSpread(), arg);
      newcall.addChildToBack(arg);
    }
    return newcall;
  }

  public static Node name(String name) {
    Preconditions.checkState(
        name.indexOf('.') == -1, "Invalid name '%s'. Did you mean to use NodeUtil.newQName?", name);
    return Node.newString(Token.NAME, name);
  }

  public static Node startOptChainGetprop(Node target, String prop) {
    checkState(mayBeExpression(target), target);
    Node optChainGetProp = Node.newString(Token.OPTCHAIN_GETPROP, prop);
    optChainGetProp.addChildToBack(target);
    optChainGetProp.setIsOptionalChainStart(true);
    return optChainGetProp;
  }

  public static Node continueOptChainGetprop(Node target, String prop) {
    checkState(mayBeExpression(target), target);
    Node optChainGetProp = Node.newString(Token.OPTCHAIN_GETPROP, prop);
    optChainGetProp.addChildToBack(target);
    optChainGetProp.setIsOptionalChainStart(false);
    return optChainGetProp;
  }

  public static Node getprop(Node target, String prop) {
    checkState(mayBeExpression(target));
    Node getprop = Node.newString(Token.GETPROP, prop);
    getprop.addChildToBack(target);
    return getprop;
  }

  public static Node getprop(Node target, String prop, String... moreProps) {
    checkState(mayBeExpression(target));
    Node result = IR.getprop(target, prop);
    for (String moreProp : moreProps) {
      result = IR.getprop(result, moreProp);
    }
    return result;
  }

  public static Node startOptChainGetelem(Node target, Node elem) {
    checkState(mayBeExpression(target), target);
    checkState(mayBeExpression(elem), elem);
    Node optChainGetElem = new Node(Token.OPTCHAIN_GETELEM, target, elem);
    optChainGetElem.setIsOptionalChainStart(true);
    return optChainGetElem;
  }

  public static Node continueOptChainGetelem(Node target, Node elem) {
    checkState(mayBeExpression(target), target);
    checkState(mayBeExpression(elem), elem);
    Node optChainGetElem = new Node(Token.OPTCHAIN_GETELEM, target, elem);
    optChainGetElem.setIsOptionalChainStart(false);
    return optChainGetElem;
  }

  public static Node getelem(Node target, Node elem) {
    checkState(mayBeExpression(target));
    checkState(mayBeExpression(elem));
    return new Node(Token.GETELEM, target, elem);
  }

  public static Node delprop(Node target) {
    checkState(mayBeExpression(target));
    return new Node(Token.DELPROP, target);
  }

  public static Node assign(Node target, Node expr) {
    checkState(target.isValidAssignmentTarget(), target);
    checkState(mayBeExpression(expr), expr);
    return new Node(Token.ASSIGN, target, expr);
  }

  public static Node hook(Node cond, Node trueval, Node falseval) {
    checkState(mayBeExpression(cond));
    checkState(mayBeExpression(trueval));
    checkState(mayBeExpression(falseval));
    return new Node(Token.HOOK, cond, trueval, falseval);
  }

  public static Node in(Node expr1, Node expr2) {
    return binaryOp(Token.IN, expr1, expr2);
  }

  public static Node comma(Node expr1, Node expr2) {
    return binaryOp(Token.COMMA, expr1, expr2);
  }

  public static Node and(Node expr1, Node expr2) {
    return binaryOp(Token.AND, expr1, expr2);
  }

  public static Node or(Node expr1, Node expr2) {
    return binaryOp(Token.OR, expr1, expr2);
  }

  public static Node coalesce(Node expr1, Node expr2) {
    return binaryOp(Token.COALESCE, expr1, expr2);
  }

  public static Node not(Node expr1) {
    return unaryOp(Token.NOT, expr1);
  }

  /** "&lt;" */
  public static Node lt(Node expr1, Node expr2) {
    return binaryOp(Token.LT, expr1, expr2);
  }

  /** "&gt;=" */
  public static Node ge(Node expr1, Node expr2) {
    return binaryOp(Token.GE, expr1, expr2);
  }

  /** "==" */
  public static Node eq(Node expr1, Node expr2) {
    return binaryOp(Token.EQ, expr1, expr2);
  }

  /** "!=" */
  public static Node ne(Node expr1, Node expr2) {
    return binaryOp(Token.NE, expr1, expr2);
  }

  /** "===" */
  public static Node sheq(Node expr1, Node expr2) {
    return binaryOp(Token.SHEQ, expr1, expr2);
  }

  /** "!==" */
  public static Node shne(Node expr1, Node expr2) {
    return binaryOp(Token.SHNE, expr1, expr2);
  }

  public static Node voidNode(Node expr1) {
    return unaryOp(Token.VOID, expr1);
  }

  public static Node neg(Node expr1) {
    return unaryOp(Token.NEG, expr1);
  }

  public static Node pos(Node expr1) {
    return unaryOp(Token.POS, expr1);
  }

  public static Node cast(Node expr1, JSDocInfo jsdoc) {
    Node op = unaryOp(Token.CAST, expr1);
    op.setJSDocInfo(jsdoc);
    return op;
  }

  public static Node inc(Node exp, boolean isPost) {
    Node op = unaryOp(Token.INC, exp);
    op.putBooleanProp(Node.INCRDECR_PROP, isPost);
    return op;
  }

  public static Node dec(Node exp, boolean isPost) {
    Node op = unaryOp(Token.DEC, exp);
    op.putBooleanProp(Node.INCRDECR_PROP, isPost);
    return op;
  }

  public static Node add(Node expr1, Node expr2) {
    return binaryOp(Token.ADD, expr1, expr2);
  }

  public static Node sub(Node expr1, Node expr2) {
    return binaryOp(Token.SUB, expr1, expr2);
  }

  /** "||=" */
  public static Node assignOr(Node expr1, Node expr2) {
    return binaryOp(Token.ASSIGN_OR, expr1, expr2);
  }

  /** "&&=" */
  public static Node assignAnd(Node expr1, Node expr2) {
    return binaryOp(Token.ASSIGN_AND, expr1, expr2);
  }

  /** "??=" */
  public static Node assignCoalesce(Node expr1, Node expr2) {
    return binaryOp(Token.ASSIGN_COALESCE, expr1, expr2);
  }

  /** "&" */
  public static Node bitwiseAnd(Node expr1, Node expr2) {
    return binaryOp(Token.BITAND, expr1, expr2);
  }

  /** ">>" */
  public static Node rightShift(Node expr1, Node expr2) {
    return binaryOp(Token.RSH, expr1, expr2);
  }

  // TODO(johnlenz): the rest of the ops

  // literals
  public static Node objectlit(Node... propdefs) {
    Node objectlit = new Node(Token.OBJECTLIT);
    for (Node propdef : propdefs) {
      switch (propdef.getToken()) {
        case STRING_KEY:
        case MEMBER_FUNCTION_DEF:
        case GETTER_DEF:
        case SETTER_DEF:

        case OBJECT_SPREAD:
        case COMPUTED_PROP:
          break;
        default:
          throw new IllegalStateException("Unexpected OBJECTLIT child: " + propdef);
      }

      objectlit.addChildToBack(propdef);
    }
    return objectlit;
  }

  public static Node objectPattern(Node... keys) {
    Node objectPattern = new Node(Token.OBJECT_PATTERN);
    for (Node key : keys) {
      checkState(key.isStringKey() || key.isComputedProp() || key.isRest());
      objectPattern.addChildToBack(key);
    }
    return objectPattern;
  }

  public static Node arrayPattern(Node... keys) {
    Node arrayPattern = new Node(Token.ARRAY_PATTERN);
    for (Node key : keys) {
      checkState(key.isRest() || key.isValidAssignmentTarget());
      arrayPattern.addChildToBack(key);
    }
    return arrayPattern;
  }

  public static Node computedProp(Node key, Node value) {
    checkState(mayBeExpression(key), key);
    checkState(mayBeExpression(value), value);
    return new Node(Token.COMPUTED_PROP, key, value);
  }

  public static Node propdef(Node string, Node value) {
    checkState(string.isStringKey());
    checkState(!string.hasChildren());
    checkState(mayBeExpression(value));
    string.addChildToFront(value);
    return string;
  }

  public static Node arraylit(Node... exprs) {
    return arraylit(Arrays.asList(exprs));
  }

  public static Node arraylit(Iterable<Node> exprs) {
    Node arraylit = new Node(Token.ARRAYLIT);
    for (Node expr : exprs) {
      checkState(mayBeExpressionOrEmpty(expr) || expr.isSpread(), expr);
      arraylit.addChildToBack(expr);
    }
    return arraylit;
  }

  public static Node regexp(Node expr) {
    checkState(expr.isStringLit());
    return new Node(Token.REGEXP, expr);
  }

  public static Node regexp(Node expr, Node flags) {
    checkState(expr.isStringLit());
    checkState(flags.isStringLit());
    return new Node(Token.REGEXP, expr, flags);
  }

  public static Node string(String s) {
    return Node.newString(s);
  }

  public static Node stringKey(String s) {
    return Node.newString(Token.STRING_KEY, s);
  }

  public static Node stringKey(String s, Node value) {
    checkState(mayBeExpression(value) || value.isDefaultValue() || value.isObjectPattern());
    Node stringKey = stringKey(s);
    stringKey.addChildToFront(value);
    return stringKey;
  }

  public static Node quotedStringKey(String s, Node value) {
    Node k = stringKey(s, value);
    k.putBooleanProp(Node.QUOTED_PROP, true);
    return k;
  }

  public static Node templateLiteral() {
    return new Node(Token.TEMPLATELIT);
  }

  public static Node templateLiteralString(@Nullable String cooked, String raw) {
    return Node.newTemplateLitString(cooked, raw);
  }

  public static Node templateLiteralSubstitution(Node child) {
    var sub = new Node(Token.TEMPLATELIT_SUB);
    sub.addChildToBack(child);
    return sub;
  }

  public static Node iterRest(Node target) {
    checkState(target.isValidAssignmentTarget(), target);
    return new Node(Token.ITER_REST, target);
  }

  public static Node objectRest(Node target) {
    checkState(target.isValidAssignmentTarget(), target);
    return new Node(Token.OBJECT_REST, target);
  }

  public static Node iterSpread(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.ITER_SPREAD, expr);
  }

  public static Node objectSpread(Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(Token.OBJECT_SPREAD, expr);
  }

  public static Node superNode() {
    return new Node(Token.SUPER);
  }

  public static Node getterDef(String name, Node value) {
    checkState(value.isFunction());
    Node member = Node.newString(Token.GETTER_DEF, name);
    member.addChildToFront(value);
    return member;
  }

  public static Node setterDef(String name, Node value) {
    checkState(value.isFunction());
    Node member = Node.newString(Token.SETTER_DEF, name);
    member.addChildToFront(value);
    return member;
  }

  public static Node memberFieldDef(String name, Node value) {
    checkState(mayBeExpression(value));
    Node member = Node.newString(Token.MEMBER_FIELD_DEF, name);
    member.addChildToFront(value);
    return member;
  }

  public static Node memberFunctionDef(String name, Node function) {
    checkState(function.isFunction());
    Node member = Node.newString(Token.MEMBER_FUNCTION_DEF, name);
    member.addChildToBack(function);
    return member;
  }

  public static Node number(double d) {
    checkState(!Double.isNaN(d), d);
    checkState(isPositive(d), d);
    return Node.newNumber(d);
  }

  public static Node bigint(BigInteger b) {
    checkNotNull(b);
    checkState(b.signum() >= 0, b);
    return Node.newBigInt(b);
  }

  public static Node thisNode() {
    return new Node(Token.THIS);
  }

  public static Node trueNode() {
    return new Node(Token.TRUE);
  }

  public static Node falseNode() {
    return new Node(Token.FALSE);
  }

  public static Node nullNode() {
    return new Node(Token.NULL);
  }

  public static Node typeof(Node expr) {
    return unaryOp(Token.TYPEOF, expr);
  }

  public static Node importMeta() {
    return new Node(Token.IMPORT_META);
  }
  
  // helper methods

  private static Node binaryOp(Token token, Node expr1, Node expr2) {
    checkState(mayBeExpression(expr1), expr1);
    checkState(mayBeExpression(expr2), expr2);
    return new Node(token, expr1, expr2);
  }

  private static Node unaryOp(Token token, Node expr) {
    checkState(mayBeExpression(expr));
    return new Node(token, expr);
  }

  private static boolean mayBeExpressionOrEmpty(Node n) {
    return n.isEmpty() || mayBeExpression(n);
  }

  // NOTE: some nodes are neither statements nor expression nodes:
  //   SCRIPT, LABEL_NAME, PARAM_LIST, CASE, DEFAULT_CASE, CATCH
  //   GETTER_DEF, SETTER_DEF

  /**
   * It isn't possible to always determine if a detached node is a expression, so make a best guess.
   */
  private static boolean mayBeStatementNoReturn(Node n) {
    return switch (n.getToken()) {
      case EMPTY, FUNCTION ->
          // EMPTY and FUNCTION are used both in expression and statement
          // contexts
          true;
      case BLOCK,
          BREAK,
          CLASS,
          CONST,
          CONTINUE,
          DEBUGGER,
          DO,
          EXPR_RESULT,
          FOR,
          FOR_IN,
          FOR_OF,
          FOR_AWAIT_OF,
          IF,
          LABEL,
          LET,
          SWITCH,
          THROW,
          TRY,
          VAR,
          WHILE,
          WITH ->
          true;
      default -> false;
    };
  }

  /**
   * It isn't possible to always determine if a detached node is a expression, so make a best guess.
   */
  public static boolean mayBeStatement(Node n) {
    if (!mayBeStatementNoReturn(n)) {
      return n.isReturn();
    }
    return true;
  }

  /**
   * It isn't possible to always determine if a detached node is a expression, so make a best guess.
   */
  public static boolean mayBeExpression(Node n) {
    return switch (n.getToken()) {
      case FUNCTION, CLASS ->
          // FUNCTION and CLASS are used both in expression and statement
          // contexts.
          true;
      case ADD,
          AND,
          ARRAYLIT,
          ASSIGN,
          ASSIGN_BITOR,
          ASSIGN_BITXOR,
          ASSIGN_BITAND,
          ASSIGN_LSH,
          ASSIGN_RSH,
          ASSIGN_URSH,
          ASSIGN_ADD,
          ASSIGN_SUB,
          ASSIGN_MUL,
          ASSIGN_EXPONENT,
          ASSIGN_DIV,
          ASSIGN_MOD,
          ASSIGN_OR,
          ASSIGN_AND,
          ASSIGN_COALESCE,
          AWAIT,
          BIGINT,
          BITAND,
          BITOR,
          BITNOT,
          BITXOR,
          CALL,
          CAST,
          COALESCE,
          COMMA,
          DEC,
          DELPROP,
          DIV,
          DYNAMIC_IMPORT,
          EQ,
          EXPONENT,
          FALSE,
          GE,
          GETPROP,
          GETELEM,
          GT,
          HOOK,
          IMPORT_META,
          IN,
          INC,
          INSTANCEOF,
          LE,
          LSH,
          LT,
          MOD,
          MUL,
          NAME,
          NE,
          NEG,
          NEW,
          NEW_TARGET,
          NOT,
          NUMBER,
          NULL,
          OBJECTLIT,
          OPTCHAIN_CALL,
          OPTCHAIN_GETELEM,
          OPTCHAIN_GETPROP,
          OR,
          POS,
          REGEXP,
          RSH,
          SHEQ,
          SHNE,
          STRINGLIT,
          SUB,
          SUPER,
          TEMPLATELIT,
          TAGGED_TEMPLATELIT,
          THIS,
          TYPEOF,
          TRUE,
          URSH,
          VOID,
          YIELD ->
          true;
      default -> false;
    };
  }
}
