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

package org.springframework.cloud.sleuth.api;

import org.springframework.lang.Nullable;

/**
 * Inspired by OpenZipkin Brave's {@code BaggageField}.
 *
 * Represents a single baggage entry.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface BaggageEntry {

	/**
	 * @return name of the baggage entry
	 */
	String name();

	/**
	 * @return value of the baggage entry or {@code null} if not set.
	 */
	@Nullable
	String get();

	/**
	 * Retrieves baggage from the given {@link TraceContext}.
	 * @param traceContext context containing baggage
	 * @return value of the baggage entry or {@code null} if not set.
	 */
	@Nullable
	String get(TraceContext traceContext);

	/**
	 * Sets the baggage value.
	 * @param value to set
	 */
	void set(String value);

	/**
	 * Sets the baggage value for the given {@link TraceContext}.
	 * @param traceContext context containing baggage
	 * @param value to set
	 */
	void set(TraceContext traceContext, String value);

}
