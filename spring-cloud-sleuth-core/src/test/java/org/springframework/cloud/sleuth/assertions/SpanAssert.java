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

package org.springframework.cloud.sleuth.assertions;

import java.util.Objects;

import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;
import org.springframework.cloud.sleuth.Span;

public class SpanAssert extends AbstractAssert<SpanAssert, Span> {

	private static final Logger log = org.slf4j.LoggerFactory.getLogger(SpanAssert.class);

	public SpanAssert(Span actual) {
		super(actual, SpanAssert.class);
	}

	public static SpanAssert then(Span actual) {
		return new SpanAssert(actual);
	}

	public SpanAssert hasTraceIdEqualTo(Long traceId) {
		isNotNull();
		if (!Objects.equals(this.actual.getTraceId(), traceId)) {
			String message = String.format("Expected span's traceId to be <%s> but was <%s>", traceId, this.actual.getTraceId());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameEqualTo(String name) {
		isNotNull();
		if (!Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name to be <%s> but it was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert nameStartsWith(String string) {
		isNotNull();
		if (!this.actual.getName().startsWith(string)) {
			String message = String.format("Expected span's name to start with <%s> but it was equal to <%s>", string, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasNameNotEqualTo(String name) {
		isNotNull();
		if (Objects.equals(this.actual.getName(), name)) {
			String message = String.format("Expected span's name NOT to be <%s> but it was <%s>", name, this.actual.getName());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert isALocalComponentSpan() {
		isNotNull();
		if (!this.actual.tags().containsKey(Span.SPAN_LOCAL_COMPONENT_TAG_NAME)) {
			String message = String.format("Expected span to be a local component. "
					+ "LC tag is missing. Found tags are <%s>", this.actual.tags());
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}

	public SpanAssert hasATag(String tagKey, String tagValue) {
		isNotNull();
		if (!this.actual.tags().containsKey(tagKey)) {
			String message = String.format("Expected span to have the tag with key <%s>. "
					+ "Found tags are <%s>", tagKey, this.actual.tags());
			log.error(message);
			failWithMessage(message);
		}
		String foundTagValue = this.actual.tags().get(tagKey);
		if (!foundTagValue.equals(tagValue)) {
			String message = String.format("Expected span to have the tag with key <%s> and value <%s>. "
					+ "Found value for that tag is <%s>", tagKey, tagValue, foundTagValue);
			log.error(message);
			failWithMessage(message);
		}
		return this;
	}
}