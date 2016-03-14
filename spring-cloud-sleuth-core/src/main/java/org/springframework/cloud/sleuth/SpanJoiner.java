/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth;

import org.springframework.cloud.sleuth.Span.SpanBuilder;

/**
 * Taken from <a href="https://github.com/opentracing/opentracing-java/blob/master/opentracing/src/main/java/opentracing/Tracer.java"></a>OpenTracing</a>
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
public interface SpanJoiner {
	/**  Returns a SpanBuilder provided
	 *    a “carrier” object from which to extract identifying information needed by the new Span instance.
	 *
	 * If the carrier object has no such span stored within it, a new Span is created.
	 *
	 * Unless there’s an error, it returns a SpanBuilder.
	 * The Span generated from the builder can be used in the host process like any other.
	 *
	 * (Note that some OpenTracing implementations consider the Spans on either side of an RPC to have the same identity,
	 * and others consider the caller to be the parent and the receiver to be the child)
	 *
	 * Attempting to join from a carrier that has been registered/configured to this Tracer will result in a
	 * IllegalStateException.
	 *
	 * If the span serialized state is invalid (corrupt, wrong version, etc) inside the carrier this will result in a
	 * IllegalArgumentException.
	 */
	<T> SpanBuilder join(T carrier);
}
