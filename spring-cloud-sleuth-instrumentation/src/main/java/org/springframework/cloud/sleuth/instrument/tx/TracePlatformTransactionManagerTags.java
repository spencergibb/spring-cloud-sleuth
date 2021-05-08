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

package org.springframework.cloud.sleuth.instrument.tx;

import org.springframework.cloud.sleuth.Span;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

final class TracePlatformTransactionManagerTags {

	private TracePlatformTransactionManagerTags() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	static void tag(Span span, TransactionDefinition def, Class transactionManagerClass) {
		span.tag("tx.transaction-manager", ClassUtils.getQualifiedName(transactionManagerClass));
		span.tag("tx.read-only", String.valueOf(def.isReadOnly()));
		span.tag("tx.propagation-level", propagationLevel(def));
		span.tag("tx.isolation-level", isolationLevel(def));
		if (def.getTimeout() > 0) {
			span.tag("tx.timeout", String.valueOf(def.getTimeout()));
		}
		if (StringUtils.hasText(def.getName())) {
			span.tag("tx.name", def.getName());
		}
	}

	private static String propagationLevel(TransactionDefinition def) {
		switch (def.getPropagationBehavior()) {
		case 0:
			return "PROPAGATION_REQUIRED";
		case 1:
			return "PROPAGATION_SUPPORTS";
		case 2:
			return "PROPAGATION_MANDATORY";
		case 3:
			return "PROPAGATION_REQUIRES_NEW";
		case 4:
			return "PROPAGATION_NOT_SUPPORTED";
		case 5:
			return "PROPAGATION_NEVER";
		case 6:
			return "PROPAGATION_NESTED";
		default:
			return String.valueOf(def.getPropagationBehavior());
		}
	}

	private static String isolationLevel(TransactionDefinition def) {
		switch (def.getIsolationLevel()) {
		case -1:
			return "ISOLATION_DEFAULT";
		case 1:
			return "ISOLATION_READ_UNCOMMITTED";
		case 2:
			return "ISOLATION_READ_COMMITTED";
		case 4:
			return "ISOLATION_REPEATABLE_READ";
		case 8:
			return "ISOLATION_SERIALIZABLE";
		default:
			return String.valueOf(def.getIsolationLevel());
		}
	}

}
