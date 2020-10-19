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

package org.springframework.cloud.sleuth.api.http;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.lang.Nullable;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract response type used for parsing and sampling.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Response {

	/**
	 * @return The remote {@link Span.Kind} describing the direction and type of the
	 * request.
	 */
	Span.Kind spanKind();

	/**
	 * @return corresponding request
	 */
	@Nullable
	Request request();

	/**
	 * @return exception that occurred or {@code null} if there was none.
	 */
	@Nullable
	Throwable error();

	/**
	 * @return the underlying request object or {@code null} if there is none.
	 */
	Object unwrap();

}
