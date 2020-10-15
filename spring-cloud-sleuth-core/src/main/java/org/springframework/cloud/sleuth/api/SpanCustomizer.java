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

/**
 * Simple interface users can customize a span with. For example, this can add custom tags
 * useful in looking up spans.
 *
 * <h3>Usage notes</h3> This type is safer to expose directly to users than {@link Span},
 * as it has no hooks that can affect the span lifecycle.
 *
 * @see Tag
 */
public interface SpanCustomizer {

	/**
	 * Sets the string name for the logical operation this span represents.
	 */
	SpanCustomizer name(String name);

	/**
	 * Tags give your span context for search, viewing and analysis. For example, a key
	 * "your_app.version" would let you lookup spans by version. A tag "sql.query" isn't
	 * searchable, but it can help in debugging when viewing a trace.
	 *
	 * <p>
	 * Ex. <pre>{@code
	 * SUMMARY_TAG = new Tag<Summarizer>("summary") {
	 *   &#64;Override protected String parseValue(Summarizer input, TraceContext context) {
	 *     return input.computeSummary();
	 *   }
	 * }
	 * SUMMARY_TAG.tag(span);
	 * }</pre>
	 * @param key Name used to lookup spans, such as "your_app.version".
	 * @param value String value, cannot be <code>null</code>.
	 */
	SpanCustomizer tag(String key, String value);

	/**
	 * Associates an event that explains latency with the current system time.
	 * @param value A short tag indicating the event, like "finagle.retry"
	 */
	SpanCustomizer annotate(String value);

}
