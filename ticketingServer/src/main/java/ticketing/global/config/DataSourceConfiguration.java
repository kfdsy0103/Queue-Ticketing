package ticketing.global.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;
import ticketing.global.enums.DataSourceType;

@Slf4j
@Configuration
public class DataSourceConfiguration {

	private static final String MASTER_DATA_SOURCE = "masterDataSource";
	private static final String SLAVE_DATA_SOURCE = "slaveDataSource";
	private static final String ROUTING_DATA_SOURCE = "routingDataSource";

	@Bean(MASTER_DATA_SOURCE)
	@ConfigurationProperties(prefix = "spring.datasource.master")
	public DataSource masterDataSource() {
		return DataSourceBuilder
			.create()
			.type(HikariDataSource.class)
			.build();
	}

	@Bean(SLAVE_DATA_SOURCE)
	@ConfigurationProperties(prefix = "spring.datasource.slave")
	public DataSource slaveDataSource() {
		return DataSourceBuilder
			.create()
			.type(HikariDataSource.class)
			.build();
	}

	/**
	 * 트랜잭션의 readOnly 여부에 따라 MASTER 또는 SLAVE를 선택하는 DataSource
	 */
	@Bean(ROUTING_DATA_SOURCE)
	public DataSource routingDataSource(
		@Qualifier(MASTER_DATA_SOURCE) DataSource masterDataSource,
		@Qualifier(SLAVE_DATA_SOURCE) DataSource slaveDataSource
	) {
		RoutingDataSource routingDataSource = new RoutingDataSource();

		Map<Object, Object> dataSourceMap = new HashMap<>();
		dataSourceMap.put(DataSourceType.MASTER, masterDataSource);
		dataSourceMap.put(DataSourceType.SLAVE, slaveDataSource);

		routingDataSource.setTargetDataSources(dataSourceMap);
		routingDataSource.setDefaultTargetDataSource(masterDataSource);

		return routingDataSource;
	}

	/**
	 * 커넥션 획득을 실제 쿼리 시점까지 미룬다.
	 * 이 프록시가 없으면 트랜잭션 시작 시점(readOnly 플래그가 세팅되기 전)에 커넥션이 잡혀 전부 MASTER로 간다.
	 */
	@Primary
	@Bean
	public DataSource dataSource(@Qualifier(ROUTING_DATA_SOURCE) DataSource routingDataSource) {
		return new LazyConnectionDataSourceProxy(routingDataSource);
	}
}
