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

package com.google.javascript.jscomp.parsing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.parsing.JsDocToken.ANNOTATION;
import static com.google.javascript.jscomp.parsing.JsDocToken.BANG;
import static com.google.javascript.jscomp.parsing.JsDocToken.COLON;
import static com.google.javascript.jscomp.parsing.JsDocToken.COMMA;
import static com.google.javascript.jscomp.parsing.JsDocToken.EOC;
import static com.google.javascript.jscomp.parsing.JsDocToken.EOF;
import static com.google.javascript.jscomp.parsing.JsDocToken.EOL;
import static com.google.javascript.jscomp.parsing.JsDocToken.EQUALS;
import static com.google.javascript.jscomp.parsing.JsDocToken.ITER_REST;
import static com.google.javascript.jscomp.parsing.JsDocToken.LEFT_ANGLE;
import static com.google.javascript.jscomp.parsing.JsDocToken.LEFT_CURLY;
import static com.google.javascript.jscomp.parsing.JsDocToken.LEFT_PAREN;
import static com.google.javascript.jscomp.parsing.JsDocToken.LEFT_SQUARE;
import static com.google.javascript.jscomp.parsing.JsDocToken.PIPE;
import static com.google.javascript.jscomp.parsing.JsDocToken.QMARK;
import static com.google.javascript.jscomp.parsing.JsDocToken.RIGHT_ANGLE;
import static com.google.javascript.jscomp.parsing.JsDocToken.RIGHT_CURLY;
import static com.google.javascript.jscomp.parsing.JsDocToken.RIGHT_PAREN;
import static com.google.javascript.jscomp.parsing.JsDocToken.RIGHT_SQUARE;
import static com.google.javascript.jscomp.parsing.JsDocToken.STAR;
import static com.google.javascript.jscomp.parsing.JsDocToken.STRING;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JsDocTokenStream}. */
@RunWith(JUnit4.class)
public final class JsDocTokenStreamTest {

  @Test
  public void testJsDocTokenization1() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(STAR, ANNOTATION, LEFT_CURLY, STRING, RIGHT_CURLY, EOL, STAR, ANNOTATION);
    ImmutableList<String> strings = ImmutableList.of("type", "string", "private");
    testJSDocTokenStream(" * @type {string}\n * @private", tokens, strings);
    testJSDocTokenStream(" *    @type { string } \n * @private", tokens, strings);
    testJSDocTokenStream(" * @type   {  string}\n * @private", tokens, strings);
    testJSDocTokenStream(" * @type {string  }\n * @private", tokens, strings);
    testJSDocTokenStream(" * @type {string}\n *   @private", tokens, strings);
    testJSDocTokenStream(" * @type {string}   \n * @private", tokens, strings);
  }

  @Test
  public void testJsDocTokenization2() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(
            ANNOTATION,
            LEFT_CURLY,
            STRING,
            LEFT_ANGLE,
            STRING,
            PIPE,
            STRING,
            RIGHT_ANGLE,
            RIGHT_CURLY);
    ImmutableList<String> strings = ImmutableList.of("param", "Array", "string", "null");
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string |null>}", tokens, strings);
    testJSDocTokenStream(" @param {Array.<string |  null>}", tokens, strings);
    testJSDocTokenStream(" @param {Array.<string|null  >}", tokens, strings);
    testJSDocTokenStream("@param {Array  .<string|null>}", tokens, strings);
    testJSDocTokenStream("@param   {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {  Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|   null>}  ", tokens, strings);
    testJSDocTokenStream("@param {Array.<string|null>}", tokens, strings);
    testJSDocTokenStream("     @param { Array .< string |null > } ", tokens, strings);
  }

  @Test
  public void testJsDocTokenization4() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(
            ANNOTATION,
            LEFT_CURLY,
            STRING,
            LEFT_ANGLE,
            LEFT_PAREN,
            STRING,
            COMMA,
            STRING,
            RIGHT_PAREN,
            RIGHT_ANGLE,
            RIGHT_CURLY,
            EOF);
    ImmutableList<String> strings = ImmutableList.of("param", "Array", "string", "null");
    testJSDocTokenStream("@param {Array.<(string,null)>}", tokens, strings);
    testJSDocTokenStream("@param {Array  .<(string,null)> } ", tokens, strings);
    testJSDocTokenStream(" @param {Array.<  (  string,null)>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<(string  , null)>}", tokens, strings);
    testJSDocTokenStream("@param {Array.<(string,   null)  > }  ", tokens, strings);
    testJSDocTokenStream("@param {  Array  .<  (string,null)>}   ", tokens, strings);
  }

  @Test
  public void testJsDocTokenization5() {
    ImmutableList<JsDocToken> tokens = ImmutableList.of(ANNOTATION, STRING, EOC, EOF);
    ImmutableList<String> strings = ImmutableList.of("param", "foo.Bar");
    testJSDocTokenStream("@param foo.Bar*/", tokens, strings);
    testJSDocTokenStream(" @param   foo.Bar*/", tokens, strings);
    testJSDocTokenStream(" @param foo.Bar   */", tokens, strings);
  }

  @Test
  public void testJsDocTokenization6() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ANNOTATION, EOL, ANNOTATION, EOL, ANNOTATION, EOC);
    ImmutableList<String> strings = ImmutableList.of("private", "static", "desc");
    testJSDocTokenStream("@private\n@static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@private\n @static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@private\n@static\n @desc*/", tokens, strings);
    testJSDocTokenStream("@private\n@static\n@desc */", tokens, strings);
    testJSDocTokenStream(" @private \n@static\n @desc*/", tokens, strings);
    testJSDocTokenStream("@private\n@static    \n @desc  */", tokens, strings);
    testJSDocTokenStream("@private\n@static\n@desc*/", tokens, strings);
    testJSDocTokenStream("@private   \n@static   \n @desc*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization7() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ITER_REST, ITER_REST, ITER_REST, ITER_REST, ITER_REST, LEFT_ANGLE, EOC);
    ImmutableList<String> strings = ImmutableList.of();

    testJSDocTokenStream("................<*/", tokens, strings);
    testJSDocTokenStream("............... .<*/", tokens, strings);
    testJSDocTokenStream("................< */", tokens, strings);
    testJSDocTokenStream("............... .< */", tokens, strings);
    testJSDocTokenStream("............... .< */ ", tokens, strings);
    testJSDocTokenStream(" ............... .< */ ", tokens, strings);
  }

  @Test
  public void testJsDocTokenization8() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(
            STAR, ANNOTATION, STRING, STRING, STRING, STRING, STRING, STRING, STRING, EOL, EOC);
    ImmutableList<String> strings =
        ImmutableList.of("param", "foo.Bar", "opt_name", "this", "parameter", "is", "a", "name");
    testJSDocTokenStream(
        """
        * @param foo.Bar opt_name this parameter is a name
        */
        """,
        tokens,
        strings);
    testJSDocTokenStream(
        """
         *  @param foo.Bar opt_name this parameter is a name\s
        */\s
        """,
        tokens,
        strings);
  }

  @Test
  public void testJsDocTokenization9() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(
            STAR, ANNOTATION, STRING, STRING, STRING, STRING, STRING, ANNOTATION, STRING, EOL, EOC);
    ImmutableList<String> strings =
        ImmutableList.of(
            "param", "foo.Bar", "opt_name", "this", "parameter", "does", "media", "blah");
    testJSDocTokenStream(
        """
        * @param foo.Bar opt_name this parameter does @media blah
        */
        """,
        tokens,
        strings);
  }

  @Test
  public void testJsDocTokenization10() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(STRING, LEFT_ANGLE, STRING, RIGHT_ANGLE, EOC);
    ImmutableList<String> strings = ImmutableList.of("Array", "String");
    testJSDocTokenStream("Array<String>*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization11() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ANNOTATION, LEFT_CURLY, STRING, QMARK, RIGHT_CURLY, EOC, EOF);
    ImmutableList<String> strings = ImmutableList.of("param", "string");
    testJSDocTokenStream("@param {string?}*/", tokens, strings);
    testJSDocTokenStream(" @param {string?}*/", tokens, strings);
    testJSDocTokenStream("@param { string?}*/", tokens, strings);
    testJSDocTokenStream("@param {string ?}*/", tokens, strings);
    testJSDocTokenStream("@param  {string ?  } */", tokens, strings);
    testJSDocTokenStream("@param { string  ?  }*/", tokens, strings);
    testJSDocTokenStream("@param {string?  }*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization12() {
    ImmutableList<JsDocToken> tokens = ImmutableList.of(STRING, ITER_REST, EOC);
    ImmutableList<String> strings = ImmutableList.of("function");

    testJSDocTokenStream("function ...*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization13() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ITER_REST, LEFT_SQUARE, STRING, RIGHT_SQUARE, EOC);
    ImmutableList<String> strings = ImmutableList.of("number");

    testJSDocTokenStream("...[number]*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization14() {
    // Since ES4 type parsing only requires to parse an ITER_REST when it is
    // followed by a comma (,) we are allowing this case to parse this way.
    // This is a simplification of the tokenizer, but the extra complexity is
    // never used.
    ImmutableList<JsDocToken> tokens = ImmutableList.of(STRING, LEFT_SQUARE, STRING, EOC);
    ImmutableList<String> strings = ImmutableList.of("foo", "bar...");

    testJSDocTokenStream("foo[ bar...*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization15() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(STRING, LEFT_SQUARE, STRING, COMMA, ITER_REST, EOC);
    ImmutableList<String> strings = ImmutableList.of("foo", "bar");

    testJSDocTokenStream("foo[ bar,...*/", tokens, strings);
    testJSDocTokenStream("foo[ bar ,...*/", tokens, strings);
    testJSDocTokenStream("foo[bar, ...*/", tokens, strings);
    testJSDocTokenStream("foo[ bar  ,   ...  */", tokens, strings);
    testJSDocTokenStream("foo [bar,... */", tokens, strings);
  }

  @Test
  public void testJsDocTokenization16() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(STRING, COLON, COLON, COLON, ITER_REST, STRING, COLON, STRING, EOC);
    ImmutableList<String> strings = ImmutableList.of("foo", "bar", "bar2");

    testJSDocTokenStream("foo:::...bar:bar2*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization17() {
    ImmutableList<JsDocToken> tokens = ImmutableList.of(STRING, EOL, EOC);
    ImmutableList<String> strings = ImmutableList.of("..");

    testJSDocTokenStream("..\n*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization18() {
    ImmutableList<JsDocToken> tokens = ImmutableList.of(STRING, EOL, EOC);
    ImmutableList<String> strings = ImmutableList.of(".");

    testJSDocTokenStream(".\n*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization19() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ANNOTATION, LEFT_CURLY, STAR, RIGHT_CURLY, EOC);
    ImmutableList<String> strings = ImmutableList.of("type", "*");

    testJSDocTokenStream("@type {*}*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization20() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ANNOTATION, LEFT_CURLY, BANG, STRING, RIGHT_CURLY, EOC, EOF);
    ImmutableList<String> strings = ImmutableList.of("param", "Object");
    testJSDocTokenStream("@param {!Object}*/", tokens, strings);
    testJSDocTokenStream(" @param {!Object}*/", tokens, strings);
    testJSDocTokenStream("@param {! Object}*/", tokens, strings);
    testJSDocTokenStream("@param { !Object}*/", tokens, strings);
    testJSDocTokenStream("@param  {!Object  } */", tokens, strings);
    testJSDocTokenStream("@param {  ! Object  }*/", tokens, strings);
    testJSDocTokenStream("@param {!Object  }*/", tokens, strings);
  }

  @Test
  public void testJsDocTokenization21() {
    ImmutableList<JsDocToken> tokens =
        ImmutableList.of(ANNOTATION, LEFT_CURLY, STRING, EQUALS, RIGHT_CURLY, EOC, EOF);
    ImmutableList<String> strings = ImmutableList.of("param", "Object");
    testJSDocTokenStream("@param {Object=}*/", tokens, strings);
    testJSDocTokenStream(" @param {Object=}*/", tokens, strings);
    testJSDocTokenStream("@param { Object =}*/", tokens, strings);
    testJSDocTokenStream("@param { Object=}*/", tokens, strings);
    testJSDocTokenStream("@param  {Object=  } */", tokens, strings);
    testJSDocTokenStream("@param { Object = }*/", tokens, strings);
    testJSDocTokenStream("@param {Object=  }*/", tokens, strings);
  }

  private static void testJSDocTokenStream(
      String comment, List<JsDocToken> tokens, List<String> strings) {
    JsDocTokenStream stream = new JsDocTokenStream(comment, 0);
    int stringsIndex = 0;
    for (JsDocToken token : tokens) {
      JsDocToken readToken = stream.getJsDocToken();

      // token equality
      if (token != readToken) {
        assertThat(readToken).isEqualTo(token);
      }

      // string equality
      if (token == ANNOTATION || token == STRING) {
        assertThat(stream.getString()).isEqualTo(strings.get(stringsIndex++));
      }
    }
  }
}
