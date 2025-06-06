/*
 * Copyright 2012 The Closure Compiler Authors
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
 * @fileoverview Definitions for the Media Source Extensions. Note that the
 * properties available here are the union of several versions of the spec.
 * @see http://dvcs.w3.org/hg/html-media/raw-file/tip/media-source/media-source.html
 *
 * @externs
 */

/**
 * @constructor
 * @implements {EventTarget}
 */
function MediaSource() {}

/** @override */
MediaSource.prototype.addEventListener = function(type, listener, opt_options) {
};

/** @override */
MediaSource.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
MediaSource.prototype.dispatchEvent = function(evt) {};

/** @type {Array<SourceBuffer>} */
MediaSource.prototype.sourceBuffers;

/** @type {Array<SourceBuffer>} */
MediaSource.prototype.activeSourceBuffers;

/** @type {number} */
MediaSource.prototype.duration;

/**
 * @param {string} type
 * @return {SourceBuffer}
 */
MediaSource.prototype.addSourceBuffer = function(type) {};

/**
 * @param {SourceBuffer} sourceBuffer
 * @return {undefined}
 */
MediaSource.prototype.removeSourceBuffer = function(sourceBuffer) {};

/**
 * Updates the live seekable range.
 * @param {number} start
 * @param {number} end
 */
MediaSource.prototype.setLiveSeekableRange = function(start, end) {};

/**
 * Clears the live seekable range.
 * @return {void}
 */
MediaSource.prototype.clearLiveSeekableRange = function() {};

/** @type {string} */
MediaSource.prototype.readyState;

/**
 * @param {string=} opt_error
 * @return {undefined}
 */
MediaSource.prototype.endOfStream = function(opt_error) {};

/**
 * @param {string} type
 * @return {boolean}
 */
MediaSource.isTypeSupported = function(type) {};

/**
 * @const {boolean}
 * @see https://developer.mozilla.org/docs/Web/API/MediaSource/canConstructInDedicatedWorker_static
 */
MediaSource.canConstructInDedicatedWorker;

/** @type {?function(!Event)} */
MediaSource.prototype.onsourceclose;

/** @type {?function(!Event)} */
MediaSource.prototype.onsourceended;

/** @type {?function(!Event)} */
MediaSource.prototype.onsourceopen;

/**
 * @constructor
 */
function MediaSourceHandle() {}

/**
 * @constructor
 * @implements {EventTarget}
 */
function SourceBuffer() {}

/** @override */
SourceBuffer.prototype.addEventListener = function(
    type, listener, opt_options) {};

/** @override */
SourceBuffer.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
SourceBuffer.prototype.dispatchEvent = function(evt) {};

/** @type {string} */
SourceBuffer.prototype.mode;

/** @type {boolean} */
SourceBuffer.prototype.updating;

/** @type {TimeRanges} */
SourceBuffer.prototype.buffered;

/** @type {number} */
SourceBuffer.prototype.timestampOffset;

/** @type {number} */
SourceBuffer.prototype.appendWindowStart;

/** @type {number} */
SourceBuffer.prototype.appendWindowEnd;

/**
 * @param {Uint8Array} data
 * @return {undefined}
 */
SourceBuffer.prototype.append = function(data) {};

/**
 * @param {ArrayBuffer|ArrayBufferView} data
 * @return {undefined}
 */
SourceBuffer.prototype.appendBuffer = function(data) {};

/**
 * Abort the current segment append sequence.
 * @return {undefined}
 */
SourceBuffer.prototype.abort = function() {};

/**
 * @param {number} start
 * @param {number} end
 * @return {undefined}
 */
SourceBuffer.prototype.remove = function(start, end) {};

/**
 * @param {string} type
 * @return {undefined}
 */
SourceBuffer.prototype.changeType = function(type) {};

/** @type {?function(!Event)} */
SourceBuffer.prototype.onabort;

/** @type {?function(!Event)} */
SourceBuffer.prototype.onerror;

/** @type {?function(!Event)} */
SourceBuffer.prototype.onupdate;

/** @type {?function(!Event)} */
SourceBuffer.prototype.onupdateend;

/** @type {?function(!Event)} */
SourceBuffer.prototype.onupdatestart;

/**
 * @constructor
 * @implements {EventTarget}
 * @implements {IArrayLike<!SourceBuffer>}
 */
function SourceBufferList() {}

/** @type {number} */
SourceBufferList.prototype.length;

/** @type {?function(!Event)} */
SourceBufferList.prototype.onaddsourcebuffer;

/** @type {?function(!Event)} */
SourceBufferList.prototype.onremovesourcebuffer;

/** @override */
SourceBufferList.prototype.addEventListener = function(
    type, listener, opt_options) {};

/** @override */
SourceBufferList.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
SourceBufferList.prototype.dispatchEvent = function(evt) {};