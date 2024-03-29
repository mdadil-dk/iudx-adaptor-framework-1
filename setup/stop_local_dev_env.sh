#!/bin/bash

# To be executed from project root

./setup/scripts/stop_monitoring_service.sh
./setup/flink/kill_local.sh
docker-compose -f ./setup/rmq/docker-compose.yml down
docker-compose -f ./setup/postgres/docker-compose.yml down
docker-compose -f ./setup/mockserver/docker-compose.yml down
docker-compose -f ./setup/flink/docker-compose.yml down
docker network rm adaptor-net
