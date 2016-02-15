/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.async;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author Spencer Gibb
 */
public abstract class TraceDelegate<T> {

	private static final String ASYNC_COMPONENT = "async";

	private final Tracer tracer;
	private final T delegate;
	private final String name;
	private final Span parent;

	public TraceDelegate(Tracer tracer, T delegate) {
		this(tracer, delegate, null);
	}

	public TraceDelegate(Tracer tracer, T delegate, String name) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.name = name;
		this.parent = tracer.getCurrentSpan();
	}

	protected void close(Span span) {
		this.tracer.close(span);
	}

	protected Span startSpan() {
		return this.tracer.joinTrace(getSpanName(), this.parent);
	}

	protected String getSpanName() {
		return this.name == null ?
				ASYNC_COMPONENT + ":" + Thread.currentThread().getName()
				: this.name;
	}

	public Tracer getTracer() {
		return this.tracer;
	}

	public T getDelegate() {
		return this.delegate;
	}

	public String getName() {
		return this.name;
	}

	public Span getParent() {
		return this.parent;
	}

	@Override
	public String toString() {
		return "TraceDelegate{" +
				"tracer=" + this.tracer +
				", delegate=" + this.delegate +
				", name=" + this.name +
				", parent=" + this.parent +
				'}';
	}
}
