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

import org.springframework.util.Assert;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Interface for gathering and reporting statistics about a block of execution.
 * <p/>
 * Spans should form a directed acyclic graph structure. It should be possible to keep
 * following the parents of a span until you arrive at a span with no parents.
 * <p/>
 */
public interface Span {

	/**
	 * A human-readable name assigned to this span instance.
	 * <p/>
	 */
	String getName();

	/**
	 * A pseudo-unique (random) number assigned to this span instance.
	 * <p/>
	 * <p/>
	 * The spanId is immutable and cannot be changed. It is safe to access this from
	 * multiple threads.
	 */
	long getSpanId();

	/**
	 * A pseudo-unique (random) number assigned to the trace associated with this span
	 */
	long getTraceId();

	/**
	 * Return a unique id for the process from which this Span originated.
	 * <p/>
	 * <p/>
	 * // TODO: Check when this is going to be null (cause it may be null)
	 */
	String getProcessId();

	/**
	 * Returns the parent IDs of the span.
	 * <p/>
	 * <p/>
	 * The collection will be empty if there are no parents.
	 */
	List<Long> getParents();

	/**
	 * Flag that tells us whether the span was started in another process. Useful in RPC
	 * tracing when the receiver actually has to add annotations to the senders span.
	 */
	boolean isRemote();

	/**
	 * The block has completed, stop the clock
	 */
	void stop();

	/**
	 * Get the start time, in milliseconds
	 */
	long getBegin();

	/**
	 * Get the stop time, in milliseconds
	 */
	long getEnd();

	/**
	 * Return the total amount of time elapsed since start was called, if running, or
	 * difference between stop and start
	 */
	long getAccumulatedMillis();

	/**
	 * Has the span been started and not yet stopped?
	 */
	boolean isRunning();

	/**
	 * Is the span eligible for export? If not then we may not need accumulate annotations
	 * (for instance).
	 */
	boolean isExportable();

	/**
	 * Add a tag or data annotation associated with this span
	 */
	void tag(String key, String value);

	/**
	 * Add a log or timeline annotation associated with this span
	 */
	void log(String msg);

	/**
	 * Get tag data associated with this span (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	Map<String, String> tags();

	/**
	 * Get any logs or annotations (read only)
	 * <p/>
	 * <p/>
	 * Will never be null.
	 */
	List<Log> logs();


	/**
	 * Class used for conversions of long ids to their String representation
	 */
	class IdConverter {

		/**
		 * Represents given long id as hex string
		 */
		public static String toHex(long id) {
			return Long.toHexString(id);
		}

		/**
		 * Represents hex string as long
		 */
		public static long fromHex(String hexString) {
			Assert.hasText(hexString, "Can't convert empty hex string to long");
			return new BigInteger(hexString, 16).longValue();
		}
	}
}
