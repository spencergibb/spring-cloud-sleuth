/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.instrument.async;

import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.sleuth.brave.BraveTestTracing;
import org.springframework.cloud.sleuth.instrument.async.TraceCallable;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.cloud.sleuth.test.TestTracingAware;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceCallableTests extends org.springframework.cloud.sleuth.instrument.async.TraceCallableTests {

	BraveTestTracing testTracing;

	@Override
	public TestTracingAware tracerTest() {
		if (this.testTracing == null) {
			this.testTracing = new BraveTestTracing();
		}
		return this.testTracing;
	}

	@Test
	public void should_provide_delegate() {
		Callable<String> delegate = () -> "test";

		TraceCallable<String> traceCallable = new TraceCallable<>(tracerTest().tracing().tracer(),
				new DefaultSpanNamer(), delegate);

		assertThat(traceCallable.getDelegate()).isEqualTo(delegate);
	}

}
