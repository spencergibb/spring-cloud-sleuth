package org.springframework.cloud.sleuth.zipkin2;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matcin Wielgus
 */
public class DiscoveryClientEndpointLocatorConfigurationTest {

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocator() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				EmptyConfiguration.class).run("--spring.jmx.enabled=false");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isInstanceOf(ServerPropertiesEndpointLocator.class);
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocatorEvenWhenDiscoveryClientPresent() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithDiscoveryClient.class).run("--spring.jmx.enabled=false");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isInstanceOf(ServerPropertiesEndpointLocator.class);
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldRespectExistingEndpointLocator() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithCustomLocator.class).run("--spring.jmx.enabled=false");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isSameAs(ConfigurationWithCustomLocator.locator);
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldBeFallbackHavingEndpointLocatorWhenAskedTo() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithDiscoveryClient.class).run("--spring.jmx.enabled=false",
				"--spring.zipkin.locator.discovery.enabled=true");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isInstanceOf(FallbackHavingEndpointLocator.class);
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldRespectExistingEndpointLocatorEvenWhenAskedToBeDiscovery() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithDiscoveryClient.class,
				ConfigurationWithCustomLocator.class).run("--spring.jmx.enabled=false",
				"--spring.zipkin.locator.discovery.enabled=true");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isSameAs(ConfigurationWithCustomLocator.locator);
		ctxt.close();
	}

	@Configuration
	@EnableAutoConfiguration
	public static class EmptyConfiguration {
	}

	@Configuration
	@EnableAutoConfiguration
	public static class ConfigurationWithDiscoveryClient {
		@Bean public DiscoveryClient getDiscoveryClient() {
			return Mockito.mock(DiscoveryClient.class);
		}
	}

	@Configuration
	@EnableAutoConfiguration
	public static class ConfigurationWithCustomLocator {
		static EndpointLocator locator = Mockito.mock(EndpointLocator.class);

		@Bean public EndpointLocator getEndpointLocator() {
			return locator;
		}
	}

}