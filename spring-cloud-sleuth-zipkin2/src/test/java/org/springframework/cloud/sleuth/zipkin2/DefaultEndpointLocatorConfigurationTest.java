package org.springframework.cloud.sleuth.zipkin2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matcin Wielgus
 */
public class DefaultEndpointLocatorConfigurationTest {

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocator() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				EmptyConfiguration.class).run("--spring.jmx.enabled=false");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isInstanceOf(DefaultEndpointLocator.class);
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldDefaultToServerPropertiesEndpointLocatorEvenWhenDiscoveryClientPresent() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithRegistration.class).run("--spring.jmx.enabled=false");
		assertThat(ctxt.getBean(EndpointLocator.class))
				.isInstanceOf(DefaultEndpointLocator.class);
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
	public void endpointLocatorShouldSetServiceNameToServiceId() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithRegistration.class).run("--spring.jmx.enabled=false",
				"--spring.zipkin.locator.discovery.enabled=true");
		assertThat(ctxt.getBean(EndpointLocator.class).local().serviceName())
				.isEqualTo("from-registration");
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldAcceptServiceNameOverride() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithRegistration.class).run("--spring.jmx.enabled=false",
				"--spring.zipkin.locator.discovery.enabled=true",
				"--spring.zipkin.service.name=foo");
		assertThat(ctxt.getBean(EndpointLocator.class).local().serviceName())
				.isEqualTo("foo");
		ctxt.close();
	}

	@Test
	public void endpointLocatorShouldRespectExistingEndpointLocatorEvenWhenAskedToBeDiscovery() {
		ConfigurableApplicationContext ctxt = new SpringApplication(
				ConfigurationWithRegistration.class,
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
	public static class ConfigurationWithRegistration {
		@Bean public Registration getRegistration() {
			return () -> "from-registration";
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
	public static final byte[] ADDRESS1234 = { 1, 2, 3, 4 };

	@Test
	public void portDefaultsTo8080() throws UnknownHostException {
		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				new ServerProperties(), "unknown", new ZipkinProperties(),
				localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(8080);
	}

	@Test
	public void portFromServerProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setPort(1234);

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				properties, "unknown", new ZipkinProperties(),localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(1234);
	}

	@Test
	public void portDefaultsToLocalhost() throws UnknownHostException {
		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				new ServerProperties(), "unknown", new ZipkinProperties(), localAddress(ADDRESS1234));

		assertThat(locator.local().ipv4()).isEqualTo("1.2.3.4");
	}

	@Test
	public void hostFromServerPropertiesIp() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setAddress(InetAddress.getByAddress(ADDRESS1234));

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				properties, "unknown", new ZipkinProperties(),
				localAddress(new byte[] { 4, 4, 4, 4 }));

		assertThat(locator.local().ipv4()).isEqualTo("1.2.3.4");
	}

	@Test
	public void appNameFromProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		ZipkinProperties zipkinProperties = new ZipkinProperties();
		zipkinProperties.getService().setName("foo");

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				properties, "unknown", zipkinProperties,localAddress(ADDRESS1234));

		assertThat(locator.local().serviceName()).isEqualTo("foo");
	}

	@Test
	public void negativePortFromServerProperties() throws UnknownHostException {
		ServerProperties properties = new ServerProperties();
		properties.setPort(-1);

		DefaultEndpointLocator locator = new DefaultEndpointLocator(null,
				properties, "unknown", new ZipkinProperties(),localAddress(ADDRESS1234));

		assertThat(locator.local().port()).isEqualTo(8080);
	}

	private InetUtils localAddress(byte[] address) throws UnknownHostException {
		InetUtils mocked = Mockito.spy(new InetUtils(new InetUtilsProperties()));
		Mockito.when(mocked.findFirstNonLoopbackAddress())
				.thenReturn(InetAddress.getByAddress(address));
		return mocked;
	}
}