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

package org.springframework.cloud.sleuth.autoconfig.instrument.jdbc;

import javax.sql.DataSource;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceDecorator;
import org.springframework.cloud.sleuth.instrument.jdbc.TraceDataSourceNameResolver;
import org.springframework.core.Ordered;

/**
 * {@link Ordered} decorator for {@link ProxyDataSource}.
 *
 * @author Arthur Gavlyukovskiy
 */
public class TraceDataSourceProxyTraceDataSourceDecorator implements TraceDataSourceDecorator, Ordered {

	private final TraceDataSourceDecoratorProperties dataSourceDecoratorProperties;

	private final DataSourceProxyBuilderConfigurer dataSourceProxyBuilderConfigurer;

	private final TraceDataSourceNameResolver dataSourceNameResolver;

	public TraceDataSourceProxyTraceDataSourceDecorator(
			TraceDataSourceDecoratorProperties dataSourceDecoratorProperties,
			DataSourceProxyBuilderConfigurer dataSourceProxyBuilderConfigurer,
			TraceDataSourceNameResolver dataSourceNameResolver) {
		this.dataSourceDecoratorProperties = dataSourceDecoratorProperties;
		this.dataSourceProxyBuilderConfigurer = dataSourceProxyBuilderConfigurer;
		this.dataSourceNameResolver = dataSourceNameResolver;
	}

	@Override
	public DataSource decorate(String beanName, DataSource dataSource) {
		ProxyDataSourceBuilder proxyDataSourceBuilder = ProxyDataSourceBuilder.create();
		DataSourceProxyProperties datasourceProxy = dataSourceDecoratorProperties.getDatasourceProxy();
		dataSourceProxyBuilderConfigurer.configure(proxyDataSourceBuilder, datasourceProxy);
		String dataSourceName = dataSourceNameResolver.resolveDataSourceName(dataSource);
		return proxyDataSourceBuilder.dataSource(dataSource).name(dataSourceName).build();
	}

	@Override
	public int getOrder() {
		return 20;
	}

}
