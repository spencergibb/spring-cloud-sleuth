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

package org.springframework.cloud.sleuth.instrument.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.CommonDataSource;
import javax.sql.DataSource;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.core.Ordered;

/**
 * Listener to represent each connection and sql query as a span.
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class TraceQueryExecutionListener implements QueryExecutionListener, MethodExecutionListener, Ordered {

	private static final Log log = LogFactory.getLog(TraceQueryExecutionListener.class);

	private final TraceListenerStrategy<String, Statement, ResultSet> strategy;

	public TraceQueryExecutionListener(Tracer tracer, List<TraceType> traceTypes,
			List<TraceListenerStrategySpanCustomizer> customizers) {
		this.strategy = new TraceListenerStrategy<>(tracer, traceTypes, customizers);
	}

	@Override
	public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
		this.strategy.beforeQuery(execInfo.getConnectionId(), connection(execInfo), execInfo.getStatement(),
				execInfo.getDataSourceName());
	}

	private Connection connection(ExecutionInfo execInfo) {
		try {
			return execInfo.getStatement().getConnection();
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Can't retrieve the connection - will return null", ex);
			}
			return null;
		}
	}

	@Override
	public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
		if (execInfo.getMethod().getName().equals("executeUpdate") && execInfo.getThrowable() == null) {
			this.strategy.addQueryRowCount(execInfo.getConnectionId(), execInfo.getStatement(),
					(int) execInfo.getResult());
		}
		String sql = queryInfoList.stream().map(QueryInfo::getQuery).collect(Collectors.joining("\n"));
		this.strategy.afterQuery(execInfo.getConnectionId(), execInfo.getStatement(), sql, execInfo.getThrowable());
	}

	@Override
	public void beforeMethod(MethodExecutionContext executionContext) {
		Object target = executionContext.getTarget();
		String methodName = executionContext.getMethod().getName();
		String dataSourceName = executionContext.getProxyConfig().getDataSourceName();
		String connectionId = executionContext.getConnectionInfo().getConnectionId();
		if (target instanceof DataSource && methodName.equals("getConnection")) {
			this.strategy.beforeGetConnection(connectionId, toCommonDataSource(target), dataSourceName);
		}
		else if (target instanceof ResultSet) {
			ResultSet resultSet = (ResultSet) target;
			if (methodName.equals("next")) {
				try {
					this.strategy.beforeResultSetNext(connectionId, resultSet.getStatement().getConnection(),
							resultSet.getStatement(), resultSet, dataSourceName);
				}
				catch (SQLException ignore) {
				}
			}
		}
	}

	private CommonDataSource toCommonDataSource(Object target) {
		try {
			return ((DataSource) target).unwrap(CommonDataSource.class);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to cast to common data source. Will return null", ex);
			}
			return null;
		}
	}

	private Connection getConnection(DataSource targetDataSource) {
		try {
			DataSource source = targetDataSource instanceof ProxyDataSource
					? (targetDataSource).unwrap(DataSource.class) : targetDataSource;
			return source.getConnection();
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to retrieve connection. Will return null", ex);
			}
			return null;
		}
	}

	@Override
	public void afterMethod(MethodExecutionContext executionContext) {
		Object target = executionContext.getTarget();
		String methodName = executionContext.getMethod().getName();
		String connectionId = executionContext.getConnectionInfo().getConnectionId();
		Throwable t = executionContext.getThrown();
		if (target instanceof DataSource && methodName.equals("getConnection")) {
			this.strategy.afterGetConnection(connectionId, getConnection((DataSource) target), t);
		}
		else if (target instanceof Connection) {
			switch (methodName) {
			case "commit":
				this.strategy.afterCommit(connectionId, t);
				break;
			case "rollback":
				this.strategy.afterRollback(connectionId, t);
				break;
			case "close":
				this.strategy.afterConnectionClose(connectionId, t);
				break;
			}
		}
		else if (target instanceof Statement && methodName.equals("close")) {
			this.strategy.afterStatementClose(connectionId, (Statement) target);
		}
		else if (target instanceof ResultSet && methodName.equals("close")) {
			ResultSet resultSet = (ResultSet) target;
			this.strategy.afterResultSetClose(connectionId, resultSet, -1, t);
		}
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

}
