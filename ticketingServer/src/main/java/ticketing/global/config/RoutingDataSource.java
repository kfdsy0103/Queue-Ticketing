package ticketing.global.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ticketing.global.enums.DataSourceType;

public class RoutingDataSource extends AbstractRoutingDataSource {

	/**
	 * 트랜잭션이 읽기 전용인지 확인하여 MASTER 또는 SLAVE DataSource를 반환
	 * @return DataSourceType.SLAVE (읽기 전용) 또는 DataSourceType.MASTER (쓰기 가능)
	 */
	@Override
	protected Object determineCurrentLookupKey() {
		return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
			? DataSourceType.SLAVE : DataSourceType.MASTER;
	}
}