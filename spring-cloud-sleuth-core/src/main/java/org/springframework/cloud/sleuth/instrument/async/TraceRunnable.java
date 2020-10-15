/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.api.ScopedSpan;
import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.api.Tracer;

/**
 * Runnable that passes Span between threads. The Span name is taken either from the
 * passed value or from the {@link SpanNamer} interface.
 *
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class TraceRunnable implements Runnable {

	/**
	 * Since we don't know the exact operation name we provide a default name for the
	 * Span.
	 */
	private static final String DEFAULT_SPAN_NAME = "async";

	private final Tracer tracer;

	private final Runnable delegate;

	private final Span parent;

	private final String spanName;

	public TraceRunnable(Tracer tracer, SpanNamer spanNamer, Runnable delegate) {
		this(tracer, spanNamer, delegate, null);
	}

	public TraceRunnable(Tracer tracer, SpanNamer spanNamer, Runnable delegate, String name) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.parent = tracer.currentSpan();
		this.spanName = name != null ? name : spanNamer.name(delegate, DEFAULT_SPAN_NAME);
	}

	@Override
	public void run() {
		ScopedSpan span = this.tracer.startScopedSpan(this.spanName, this.parent);
		try {
			this.delegate.run();
		}
		catch (Exception | Error e) {
			span.error(e);
			throw e;
		}
		finally {
			span.end();
		}
	}

}
