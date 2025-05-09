/*
 * Copyright 2017 The Closure Compiler Authors
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

/**
 * @fileoverview Definitions for WebAssembly JS API
 *
 *  @see http://webassembly.org/docs/js/
 *
 * @externs
 * @author loorongjie@gmail.com (Loo Rong Jie)
 */

 /**
 * @const
 */
var WebAssembly = {};

/**
 * @constructor
 * @param {!BufferSource} bytes
 */
WebAssembly.Module = function(bytes) {};

/**
 * @constructor
 * @param {!WebAssembly.Module} moduleObject
 * @param {Object=} importObject
 */
WebAssembly.Instance = function(moduleObject, importObject) {};

/**
 * @typedef {{initial:number, maximum:(number|undefined), shared:(boolean|undefined)}}
 */
var MemoryDescriptor;

/**
 * @constructor
 * @param {MemoryDescriptor} memoryDescriptor
 */
WebAssembly.Memory = function(memoryDescriptor) {};

/**
 * @typedef {{element:string, initial:number, maximum:(number|undefined)}}
 */
var TableDescriptor;

/**
 * @constructor
 * @param {TableDescriptor} tableDescriptor
 */
WebAssembly.Table = function(tableDescriptor) {};

/**
 * @constructor
 * @extends {Error}
 */
WebAssembly.CompileError = function() {};

/**
 * @constructor
 * @extends {Error}
 */
WebAssembly.LinkError = function() {};

/**
 * @constructor
 * @param {string=} message
 * @param {string=} fileName
 * @param {number=} lineNumber
 * @extends {Error}
 */
WebAssembly.RuntimeError = function(message, fileName, lineNumber) {};

/**
 * @record
 */
function WebAssemblyTagOptions() {};

/**
 * @type {Array<string>}
 */
WebAssemblyTagOptions.prototype.parameters;

/**
 * @constructor
 * @param {!WebAssemblyTagOptions} type
 */
WebAssembly.Tag = function(type) {};

/**
 * @type {!WebAssembly.Tag}
 */
WebAssembly.JSTag;

/**
 * @record
 */
function WebAssemblyExceptionOptions() {};

/**
 * @type {undefined|boolean}
 */
WebAssemblyExceptionOptions.prototype.traceStack;

/**
 * @constructor
 * @param {!WebAssembly.Tag} tag
 * @param {!Array} payload
 * @param {WebAssemblyExceptionOptions=} options
 */
WebAssembly.Exception = function(tag, payload, options) {};

/**
 * @type {undefined|string}
 */
WebAssembly.Exception.prototype.stack;

// Note: Closure compiler does not support function overloading, omit this overload for now.
// {function(!WebAssembly.Module, Object=):!Promise<!WebAssembly.Instance>}
/**
 * @param {!BufferSource} moduleObject
 * @param {Object=} importObject
 * @return {!Promise<{module:!WebAssembly.Module, instance:!WebAssembly.Instance}>}
 */
WebAssembly.instantiate = function(moduleObject, importObject) {};

/**
 * @param {!Promise<!Response>|!Response} source
 * @param {Object=} importObject
 * @return {!Promise<{module:!WebAssembly.Module, instance:!WebAssembly.Instance}>}
 */
WebAssembly.instantiateStreaming = function(source, importObject) {};

/**
 * @param {!BufferSource} bytes
 * @return {!Promise<!WebAssembly.Module>}
 */
WebAssembly.compile = function(bytes) {};

/**
 * @param {!Promise<!Response>} moduleStream
 * @return {!Promise<!WebAssembly.Module>}
 */
WebAssembly.compileStreaming = function(moduleStream) {};

/**
 * @param {!BufferSource} bytes
 * @return {boolean}
 * @nosideeffects
 */
WebAssembly.validate = function(bytes) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @return {!Array<{name:string, kind:string}>}
 */
WebAssembly.Module.exports = function(moduleObject) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @return {!Array<{module:string, name:string, kind:string}>}
 */
WebAssembly.Module.imports = function(moduleObject) {};

/**
 * @param {!WebAssembly.Module} moduleObject
 * @param {string} sectionName
 * @return {!Array<!ArrayBuffer>}
 */
WebAssembly.Module.customSections = function(moduleObject, sectionName) {};

WebAssembly.Instance.prototype.exports;

/**
 * @param {number} delta
 * @return {number}
 */
WebAssembly.Memory.prototype.grow = function(delta) {};

/**
 * @type {!ArrayBuffer}
 */
WebAssembly.Memory.prototype.buffer;

/**
 * @param {number} delta
 * @return {number}
 */
WebAssembly.Table.prototype.grow = function(delta) {};

/**
 * @type {number}
 */
WebAssembly.Table.prototype.length;

/** @typedef {function(...)} */
var TableFunction;

/**
 * @param {number} index
 * @return {TableFunction}
 */
WebAssembly.Table.prototype.get = function(index) {};

/**
 * @param {number} index
 * @param {?TableFunction} value
 * @return {undefined}
 */
WebAssembly.Table.prototype.set = function(index, value) {};

/**
 * @typedef {{
 *   anyfunc: !Function,
 *   externref: ?,
 *   f32: number,
 *   f64: number,
 *   i32: number,
 *   i64: bigint,
 *   v128: *
 * }}
 * Note: This declaration is only here to document the acceptable type strings
 * ("anyfunc"|"externref"|"f32"|"f64"|"i32"|"i64"|"v128") and to prevent
 * the properties to be marked as missing externs in d.ts files.
 */
WebAssembly.ValueTypeMap;

/**
 * @typedef {string}
 * Really: keyof ValueTypeMap, i.e. ("anyfunc"|"externref"|"f32"|"f64"|"i32"|"i64"|"v128")
 */
WebAssembly.ValueType;

/**
 * @typedef {{
 *   mutable: (boolean|undefined),
 *   value: WebAssembly.ValueType
 * }}
 */
WebAssembly.GlobalDescriptor;

/**
 * @constructor
 * @param {WebAssembly.GlobalDescriptor} descriptor
 * @param {?=} v
 */
WebAssembly.Global = function(descriptor, v) {};

/**
 * @type {?}
 */
WebAssembly.Global.prototype.value;