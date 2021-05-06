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

package org.springframework.cloud.sleuth.autoconfig.instrument.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.messaging.handler.annotation.MessageMapping;

/**
 * Properties for messaging.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
@ConfigurationProperties("spring.sleuth.messaging")
public class SleuthMessagingProperties {

	/**
	 * Should messaging be turned on.
	 */
	private boolean enabled;

	/**
	 * Aspect related properties.
	 */
	private Aspect aspect = new Aspect();

	/**
	 * Rabbit related properties.
	 */
	private Rabbit rabbit = new Rabbit();

	/**
	 * Kafka related properties.
	 */
	private Kafka kafka = new Kafka();

	/**
	 * JMS related properties.
	 */
	private Jms jms = new Jms();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Aspect getAspect() {
		return this.aspect;
	}

	public void setAspect(Aspect aspect) {
		this.aspect = aspect;
	}

	public Rabbit getRabbit() {
		return this.rabbit;
	}

	public void setRabbit(Rabbit rabbit) {
		this.rabbit = rabbit;
	}

	public Kafka getKafka() {
		return this.kafka;
	}

	public void setKafka(Kafka kafka) {
		this.kafka = kafka;
	}

	public Jms getJms() {
		return this.jms;
	}

	public void setJms(Jms jms) {
		this.jms = jms;
	}

	/**
	 * Aspect configuration.
	 */
	public static class Aspect {

		/**
		 * Should {@link MessageMapping} wrapping be enabled.
		 */
		private boolean enabled;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	/**
	 * RabbitMQ configuration.
	 */
	public static class Rabbit {

		/**
		 * Should Rabbit be turned on.
		 */
		private boolean enabled;

		/**
		 * Rabbit remote service name.
		 */
		private String remoteServiceName = "rabbitmq";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

	}

	/**
	 * Kafka configuration.
	 */
	public static class Kafka {

		/**
		 * Should Kafka be turned on.
		 */
		private boolean enabled;

		/**
		 * Kafka remote service name.
		 */
		private String remoteServiceName = "kafka";

		/**
		 * Kafka Streams related properties.
		 */
		private Streams streams = new Streams();

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

		public Streams getStreams() {
			return streams;
		}

		public void setStreams(Streams streams) {
			this.streams = streams;
		}

		/**
		 * Kafka streams configuration.
		 */
		public static class Streams {

			/**
			 * Should Kafka Streams be turned on.
			 */
			private boolean enabled;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

		}

	}

	/**
	 * JMS configuration.
	 */
	public static class Jms {

		/**
		 * Should JMS be turned on.
		 */
		private boolean enabled;

		/**
		 * JMS remote service name.
		 */
		private String remoteServiceName = "jms";

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getRemoteServiceName() {
			return this.remoteServiceName;
		}

		public void setRemoteServiceName(String remoteServiceName) {
			this.remoteServiceName = remoteServiceName;
		}

	}

}
