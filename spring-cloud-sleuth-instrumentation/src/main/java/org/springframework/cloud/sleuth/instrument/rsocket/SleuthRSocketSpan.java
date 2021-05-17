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

package org.springframework.cloud.sleuth.instrument.rsocket;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

enum SleuthRSocketSpan implements DocumentedSpan {

	/**
	 * Span created on the RSocket responder side.
	 */
	RSOCKET_RESPONDER_SPAN {
		@Override
		public String getName() {
			return "%s";
		}
	},

	/**
	 * Span created on the RSocket responder side.
	 */
	RSOCKET_REQUESTER_SPAN {
		@Override
		public String getName() {
			return "%s";
		}

		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		@Override
		public String prefix() {
			return "rsocket.";
		}
	};

	enum Tags implements TagKey {

		/**
		 * Name of the RSocket route.
		 */
		ROUTE {
			@Override
			public String getKey() {
				return "rsocket.route";
			}
		},

		/**
		 * Name of the R2DBC thread.
		 */
		REQUEST_TYPE {
			@Override
			public String getKey() {
				return "rsocket.request-type";
			}
		}

	}

}
