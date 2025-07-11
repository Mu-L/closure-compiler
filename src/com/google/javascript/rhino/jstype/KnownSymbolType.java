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
 *   Nick Santos
 *   Google Inc.
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

package com.google.javascript.rhino.jstype;

/**
 * A concrete instance of a JavaScript symbol.
 *
 * <p>This includes all "well-known" symbols in the spec - Symbol.iterator, Symbol.asyncIterator,
 * etc.
 *
 * <p>This is analogous to the {@link EnumElementType} class - each KnownSymbolType is a subtype of
 * the general symbol type, but unique KnownSymbolTypes are not subtypes of one another.
 */
public final class KnownSymbolType extends SymbolType {
  private static final JSTypeClass TYPE_CLASS = JSTypeClass.WELL_KNOWN_SYMBOL;

  private final String name;

  public KnownSymbolType(JSTypeRegistry registry, String name) {
    super(registry);
    this.name = name;
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  public String getDisplayName() {
    return name;
  }

  @Override
  public boolean isKnownSymbolValueType() {
    return true;
  }

  @Override
  public KnownSymbolType toMaybeKnownSymbolType() {
    return this;
  }
}
