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

package org.springframework.cloud.sleuth.brave.bridge;

import brave.baggage.BaggageField;

import org.springframework.cloud.sleuth.api.Baggage;
import org.springframework.cloud.sleuth.api.TraceContext;

public class BraveBaggage implements Baggage {

	private final BaggageField delegate;

	public BraveBaggage(BaggageField delegate) {
		this.delegate = delegate;
	}

	@Override
	public String name() {
		return this.delegate.name();
	}

	@Override
	public String get() {
		return this.delegate.getValue();
	}

	@Override
	public String get(TraceContext traceContext) {
		return this.delegate.getValue(BraveTraceContext.toBrave(traceContext));
	}

	@Override
	public void set(String value) {
		this.delegate.updateValue(value);
	}

	@Override
	public void set(TraceContext traceContext, String value) {
		this.delegate.updateValue(BraveTraceContext.toBrave(traceContext), value);
	}

}
