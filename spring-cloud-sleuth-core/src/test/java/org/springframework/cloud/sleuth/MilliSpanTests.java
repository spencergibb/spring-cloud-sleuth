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

package org.springframework.cloud.sleuth;

import org.junit.Test;

import java.util.Collections;

/**
 * @author Rob Winch
 * @author Spencer Gibb
 */
public class MilliSpanTests {

	@Test(expected = UnsupportedOperationException.class)
	public void getAnnotationsReadOnly() {
		MilliSpan span = new MilliSpan(1, 2, "name", 1L, Collections.<Long>emptyList(), 2L, true, true, "process");

		span.tags().put("a", "b");
	}


	@Test(expected = UnsupportedOperationException.class)
	public void getTimelineAnnotationsReadOnly() {
		MilliSpan span = new MilliSpan(1, 2, "name", 1L, Collections.<Long>emptyList(), 2L, true, true, "process");

		span.logs().add(new Log(1, "1"));
	}
}