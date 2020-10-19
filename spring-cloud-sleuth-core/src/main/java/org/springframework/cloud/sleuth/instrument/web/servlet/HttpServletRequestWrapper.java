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

package org.springframework.cloud.sleuth.instrument.web.servlet;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

import org.springframework.cloud.sleuth.api.http.HttpServerRequest;
import org.springframework.lang.Nullable;

/**
 * Besides delegating to {@link HttpServletRequest} methods, this also parses the remote
 * IP of the client.
 *
 * @since 5.10
 */
// Public for use in sparkjava or other frameworks that re-use servlet types
class HttpServletRequestWrapper implements HttpServerRequest {

	/** @since 5.10 */
	public static HttpServerRequest create(HttpServletRequest request) {
		return new HttpServletRequestWrapper(request);
	}

	HttpServletRequest delegate;

	HttpServletRequestWrapper(HttpServletRequest delegate) {
		if (delegate == null) {
			throw new NullPointerException("delegate == null");
		}
		this.delegate = delegate;
	}

	@Override
	public Object unwrap() {
		return delegate;
	}

	@Override
	public String method() {
		return delegate.getMethod();
	}

	@Override
	public String route() {
		Object maybeRoute = delegate.getAttribute("http.route");
		return maybeRoute instanceof String ? (String) maybeRoute : null;
	}

	@Override
	public String path() {
		return delegate.getRequestURI();
	}

	// not as some implementations may be able to do this more efficiently
	@Override
	public String url() {
		StringBuffer url = delegate.getRequestURL();
		if (delegate.getQueryString() != null && !delegate.getQueryString().isEmpty()) {
			url.append('?').append(delegate.getQueryString());
		}
		return url.toString();
	}

	@Override
	public String header(String name) {
		return delegate.getHeader(name);
	}

	/** Looks for a valid request attribute "error". */
	@Nullable
	Throwable maybeError() {
		Object maybeError = delegate.getAttribute("error");
		if (maybeError instanceof Throwable) {
			return (Throwable) maybeError;
		}
		maybeError = delegate.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (maybeError instanceof Throwable) {
			return (Throwable) maybeError;
		}
		return null;
	}

}
