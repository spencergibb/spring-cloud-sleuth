package org.springframework.cloud.sleuth.sampler;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation that converts String into UUID
 * On parse exceptions a null is returned.
 *
 * @author Marcin Grzejszczak
 * @author Adrian Cole
 */
@Slf4j
public class DefaultStringToUuidConverter implements StringToUuidConverter {

	/** Returns a UUID parsed from the input, or null if failed for any reason. */
	@Override
	public UUID convert(String source) {
		try {
			UUID uuid = UUID.fromString(source);
			incrementSuccess();
			return uuid;
		} catch (IllegalArgumentException e) {
			log.debug("Exception occurred while trying to parse String to UUID", e);
			incrementFailures();
			return null;
		}
	}

	/**
	 * Override to increment your counter.
	 */
	protected void incrementSuccess() {
	}


	/**
	 * Override to increment your counter.
	 */
	protected void incrementFailures() {
	}
}
