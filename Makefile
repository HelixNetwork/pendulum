clean:
	@echo "Removing all data from pendulum for fresh start!"
	@rm -rf db
	@rm -rf mainnet_snapshots
	@rm -rf testnet_snapshots
	@rm -rf logs
	@rm -rf snapshots
	@rm -rf local_snapshots
	@rm -rf modules
	@rm -rf snapshot
	@rm -rf mainnet
	@rm -rf testnet
	@rm -rf spent-addresses-db
	@rm -rf spent-addresses-log
	@rm -rf mainnetdb
	@rm -rf mainnet.log
	@rm -rf testnetdb
	@rm -rf testnet.log
	@rm -rf snapshot-mainnet
	@rm -rf snapshot-testnet
	@rm -rf logs
	@rm -rf mainnet*
	@rm -rf testnet*
start-node:
	@echo "Starting pendulum"
	@java -jar target/pen*.jar --config config.ini

