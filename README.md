[![Build Status](https://datamountaineer.ci.landoop.com/buildStatus/icon?job=stream-reactor&style=flat&.png)](https://datamountaineer.ci.landoop.com/job/stream-reactor/)
[![Documentation Status](https://readthedocs.org/projects/streamreactor/badge/?version=latest)](http://docs.datamountaineer.com/en/latest/?badge=latest)


Join us on slack

[![Alt text](images/slack.jpeg)](http://datamountaineer.com/contact/)

# Stream Reactor
Streaming reference architecture built around Kafka. 

![Alt text](https://datamountaineer.com/wp-content/uploads/2016/01/stream-reactor-1.jpg)

A collection of components to build a real time ingestion pipeline.

### Connectors

**Please take a moment and read the documentation and make sure the software prerequisites are met!!**

|Connector       | Type   | Description                                                                                   | Docs |
|----------------|--------|-----------------------------------------------------------------------------------------------|------|
| AzureDocumentDb| Sink   | Kafka connect Azure DocumentDb sink to subscribe to write to the cloud Azure Document Db.     | [Docs](http://docs.datamountaineer.com/en/latest/azuredocumentdb.html)   |
| BlockChain     | Source | Kafka connect Blockchain source to subscribe to Blockchain streams and write to Kafka.        | [Docs](http://docs.datamountaineer.com/en/latest/blockchain.html)        |
| Bloomberg      | Source | Kafka connect source to subscribe to Bloomberg streams and write to Kafka.                    | [Docs](http://docs.datamountaineer.com/en/latest/bloomberg.html)         |
| Cassandra      | Source | Kafka connect Cassandra source to read Cassandra and write to Kafka.                          | [Docs](http://docs.datamountaineer.com/en/latest/cassandra-source.html)  |
| Coap           | Source | Kafka connect Coap source to read from IoT Coap endpoints using Californium.                  | [Docs](http://docs.datamountaineer.com/en/latest/coap-source.html)       |
| Coap           | Sink   | Kafka connect Coap sink to write kafka topic payload to IoT Coap endpoints using Californium. | [Docs](http://docs.datamountaineer.com/en/latest/coap-sink.html)         |
| *DSE Cassandra | Sink   | Certified DSE Kafka connect Cassandra sink task to write Kafka topic payloads to Cassandra.   | [Docs](http://docs.datamountaineer.com/en/latest/cassandra-sink.html)    |
| Druid          | Sink   | Kafka connect Druid sink to write Kafka topic payloads to Druid.                              | [Docs](http://docs.datamountaineer.com/en/latest/druid.html)             |
| Elastic        | Sink   | Kafka connect Elastic Search sink to write Kafka topic payloads to Elastic Search.            | [Docs](http://docs.datamountaineer.com/en/latest/elastic.html)           |
| FTP/HTTP       | Source | Kafka connect FTP and HTTP source to write file data into Kafka topics.                       | [Docs](kafka-connect-ftp/README.md)                                      |
| HBase          | Sink   | Kafka connect HBase sink to write Kafka topic payloads to HBase.                              | [Docs](http://docs.datamountaineer.com/en/latest/hbase.html)             |
| Hazelcast      | Sink   | Kafka connect Hazelcast sink to write Kafka topic payloads to Hazelcast.                      | [Docs](http://docs.datamountaineer.com/en/latest/hazelcast.html)         |
| Kudu           | Sink   | Kafka connect Kudu sink to write Kafka topic payloads to Kudu.                                | [Docs](http://docs.datamountaineer.com/en/latest/kudu.html)              |
| InfluxDb       | Sink   | Kafka connect InfluxDb sink to write Kafka topic payloads to InfluxDb.                        | [Docs](http://docs.datamountaineer.com/en/latest/influx.html)            |
| JMS            | Source | Kafka connect JMS source to write from JMS to Kafka topics.                                   | [Docs](http://docs.datamountaineer.com/en/latest/jms-source.html)               |
| JMS            | Sink   | Kafka connect JMS sink to write Kafka topic payloads to JMS.                                  | [Docs](http://docs.datamountaineer.com/en/latest/jms.html)               |
| MongoDB        | Sink   | Kafka connect MongoDB sink to write Kafka topic payloads to MongoDB.                          | [Docs](http://docs.datamountaineer.com/en/latest/mongo-sink.html)        |
| MQTT           | Source | Kafka connect MQTT source to write data from MQTT to Kafka.                                   | [Docs](http://docs.datamountaineer.com/en/latest/mqtt.html)              |
| Redis          | Sink   | Kafka connect Redis sink to write Kafka topic payloads to Redis.                              | [Docs](kafka-connect-redis/README.md)                                    |
| ReThinkDB      | Source | Kafka connect RethinkDb source subscribe to ReThinkDB changefeeds and write to Kafka.         | [Docs](http://docs.datamountaineer.com/en/latest/rethink_source.html)    |
| ReThinkDB      | Sink   | Kafka connect RethinkDb sink to write Kafka topic payloads to RethinkDb.                      | [Docs](http://docs.datamountaineer.com/en/latest/rethink.html)           |
| Yahoo Finance  | Source | Kafka connect Yahoo Finance source to write to Kafka.                                         | [Docs](http://docs.datamountaineer.com/en/latest/yahoo.html)             |
| VoltDB         | Sink   | Kafka connect Voltdb sink to write Kafka topic payloads to Voltdb.                            | [Docs](http://docs.datamountaineer.com/en/latest/voltdb.html)            |



## Release Notes

**0.2.6 (Pending)**

### Feature

*   Upgrade to KCQL 2x
*   Add CQL generator to Cassandra source
*   Add KCQL INCREMENTALMODE support to the Cassandra source, bulk mode and the timestamp column type is now take from KCQL
*   Support for setting key and truststore type on Cassandra connectors
*   Added token based paging support for Cassandra source
*   Added default bytes converter to JMS Source
*   Added default connection factory to JMS Source
*   Added support for SharedDurableConsumers to JMS Connectors
*   Upgraded JMS Connector to JMS 2.0
*   Moved to Elastic4s 2.4
*   Added Elastic5s with TCP, TCP+XPACK and HTTP client support
*   Upgrade Azure Documentdb to 1.11.0
*   Added optional progress counter to all connectors, it can be enabled with ``connect.progress.enabled`` which will periodically report log messages processed
*   Added authentication and TLS to ReThink Connectors
*   Added TLS support for ReThinkDB, add batch size option to source for draining the internal queues.
*   Upgrade Kudu Client to 1.4.0
*   Support for dates in Elastic Indexes and custom document types
*   Upgrade CoAP to 2.0.0-M4

### Bug Fixes

*   Fixes for high CPU on CoAP source
*   Fixes for high CPU on Cassandra source
*   Fixed Avro double fields mapping to Kudu columns
*   Fixes on JMS properties converter, Invalid schema when extracting properties

### Misc
*   Refactored Cassandra Tests to use only one embedded instance
*   Removed unused batch size and bucket size options from Kudu, they are taken from KCQL
*   Removed unused batch size option from DocumentDb
*   Rename Azure DocumentDb `connect.documentdb.db` to `connect.documentdb.db`
*   Rename Azure DocumentDb `connect.documentdb.database.create` to `connect.documentdb.db.create`
*   Rename Cassandra Source `connect.cassandra.source.kcql` to `connect.cassandra.kcql`
*   Rename Cassandra Source `connect.cassandra.source.timestamp.type` to `connect.cassandra.timestamp.type`
*   Rename Cassandra Source `connect.cassandra.source.import.poll.interval` to `connect.cassandra.import.poll.interval`
*   Rename Cassandra Source `connect.cassandra.source.error.policy` to `connect.cassandra.error.policy`
*   Rename Cassandra Source `connect.cassandra.source.max.retries` to `connect.cassandra.max.retries`
*   Rename Cassandra Sink `connect.cassandra.source.retry.interval` to `connect.cassandra.retry.interval`
*   Rename Cassandra Sink `connect.cassandra.sink.kcql` to `connect.cassandra.kcql`
*   Rename Cassandra Sink `connect.cassandra.sink.error.policy` to `connect.cassandra.error.policy`
*   Rename Cassandra Sink `connect.cassandra.sink.max.retries` to `connect.cassandra.max.retries`
*   Rename Cassandra Sink Sink `connect.cassandra.sink.retry.interval` to `connect.cassandra.retry.interval`
*   Rename Coap Source `connect.coap.bind.port` to `connect.coap.port`
*   Rename Coap Sink `connect.coap.bind.port` to `connect.coap.port`
*   Rename Coap Source `connect.coap.bind.host` to `connect.coap.host`
*   Rename Coap Sink `connect.coap.bind.host` to `connect.coap.host`
*   Rename MongoDb `connect.mongo.database` to `connect.mongo.db`
*   Rename MongoDb `connect.mongo.sink.batch.size` to `connect.mongo.batch.size`
*   Rename Druid `connect.druid.sink.kcql` to `connect.druid.kcql`
*   Rename Druid `connect.druid.sink.conf.file` to `connect.druid.kcql`
*   Rename Druid `connect.druid.sink.write.timeout` to `connect.druid.write.timeout`
*   Rename Elastic `connect.elastic.sink.kcql` to `connect.elastic.kcql`
*   Rename HBase `connect.hbase.sink.column.family` to `connect.hbase.column.family`
*   Rename HBase `connect.hbase.sink.kcql` to `connect.hbase.kcql`
*   Rename HBase `connect.hbase.sink.error.policy` to `connect.hbase.error.policy`
*   Rename HBase `connect.hbase.sink.max.retries` to `connect.hbase.max.retries`
*   Rename HBase `connect.hbase.sink.retry.interval` to `connect.hbase.retry.interval`
*   Rename Influx `connect.influx.sink.kcql` to `connect.influx.kcql`
*   Rename Influx `connect.influx.connection.user` to `connect.influx.username`
*   Rename Influx `connect.influx.connection.password` to `connect.influx.password`
*   Rename Influx `connect.influx.connection.database` to `connect.influx.db`
*   Rename Influx `connect.influx.connection.url` to `connect.influx.url`
*   Rename Kudu `connect.kudu.sink.kcql` to `connect.kudu.kcql`
*   Rename Kudu `connect.kudu.sink.error.policy` to `connect.kudu.error.policy`
*   Rename Kudu `connect.kudu.sink.retry.interval` to `connect.kudu.retry.interval`
*   Rename Kudu `connect.kudu.sink.max.retries` to `connect.kudu.max.reties`
*   Rename Kudu `connect.kudu.sink.schema.registry.url` to `connect.kudu.schema.registry.url`
*   Rename Redis `connect.redis.connection.password` to `connect.redis.password` 
*   Rename Redis `connect.redis.sink.kcql` to `connect.redis.kcql`
*   Rename Redis `connect.redis.connection.host` to `connect.redis.host`
*   Rename Redis `connect.redis.connection.port` to `connect.redis.port` 
*   Rename ReThink `connect.rethink.source.host` to `connect.rethink.host`
*   Rename ReThink `connect.rethink.source.port` to `connect.rethink.port`
*   Rename ReThink `connect.rethink.source.db` to `connect.rethink.db`
*   Rename ReThink `connect.rethink.source.kcql` to `connect.rethink.kcql`
*   Rename ReThink Sink `connect.rethink.sink.host` to `connect.rethink.host`
*   Rename ReThink Sink `connect.rethink.sink.port` to `connect.rethink.port`
*   Rename ReThink Sink `connect.rethink.sink.db` to `connect.rethink.db`
*   Rename ReThink Sink `connect.rethink.sink.kcql` to `connect.rethink.kcql`
*   Rename JMS `connect.jms.user` to `connect.jms.username`
*   Rename JMS `connect.jms.source.converters` to `connect.jms.converters`
*   Rename Mqtt `connect.mqtt.source.kcql` to `connect.mqtt.kcql`
*   Rename Mqtt `connect.mqtt.source.converters` to `connect.mqtt.converters`
*   Rename Mqtt `connect.mqtt.user` to `connect.mqtt.username`
*   Rename Mqtt `connect.mqtt.hosts` to `connect.mqtt.connection.hosts`
*   Rename Hazelcast `connect.hazelcast.sink.kcql` to `connect.hazelcast.kcql`
*   Rename Hazelcast `connect.hazelcast.sink.group.name` to `connect.hazelcast.group.name`
*   Rename Hazelcast `connect.hazelcast.sink.group.password` to `connect.hazelcast.group.password`
*   Rename Hazelcast `connect.hazelcast.sink.cluster.members` tp `connect.hazelcast.cluster.members`
*   Rename Hazelcast `connect.hazelcast.sink.batch.size` to `connect.hazelcast.batch.size`
*   Rename Hazelcast `connect.hazelcast.sink.error.policy` to `connect.hazelcast.error.policy`
*   Rename Hazelcast `connect.hazelcast.sink.max.retries` to `connect.hazelcast.max.retries`
*   Rename Hazelcast `connect.hazelcast.sink.retry.interval` to `connect.hazelcast.retry.interval`
*   Rename VoltDB `connect.volt.sink.kcql` to `connect.volt.kcql`
*   Rename VoltDB `connect.volt.sink.connection.servers` to `connect.volt.servers`
*   Rename VoltDB `connect.volt.sink.connection.user` to `connect.volt.username`
*   Rename VoltDB `connect.volt.sink.connection.password` to `connect.volt.password`
*   Rename VoltDB `connect.volt.sink.error.policy` to `connect.volt.error.policy`
*   Rename VoltDB `connect.volt.sink.max.retries` to `connect.volt.max.retries`
*   Rename VoltDB `connect.volt.sink.retry.interval` to `connect.volt.retry.interval`




**0.2.5 (8 Apr 2017)**

*   Added Azure DocumentDB Sink Connector
*   Added JMS Source Connector.
*   Added UPSERT to Elastic Search
*   Support Confluent 3.2 and Kafka 0.10.2.
*   Cassandra improvements `withunwrap`
*   Upgrade to Kudu 1.0 and CLI 1.0
*   Add ingest_time to CoAP Source
*   InfluxDB bug fixes for tags and field selection.
*   Added Schemaless Json and Json with schema support to JMS Sink.
*   Support for Cassandra data type of ``timestamp`` in the Cassandra Source for timestamp tracking.

**0.2.4** (26 Jan 2017)

*   Added FTP and HTTP Source.
*   Added InfluxDB tag support. KCQL: INSERT INTO targetdimension ``SELECT * FROM influx-topic WITHTIMESTAMP sys_time() WITHTAG(field1, CONSTANT_KEY1=CONSTANT_VALUE1, field2,CONSTANT_KEY2=CONSTANT_VALUE1)``
*   Added InfluxDb consistency level. Default is ``ALL``. Use ``connect.influx.consistency.level`` to set it to ONE/QUORUM/ALL/ANY
*   InfluxDb ``connect.influx.sink.route.query`` was renamed to ``connect.influx.sink.kcql``
*   Added support for multiple contact points in Cassandra

**0.2.3** (5 Jan 2017)

*   Added CoAP Source and Sink.
*   Added MongoDB Sink.
*   Added MQTT Source.
*   Hazelcast support for ring buffers.
*   Redis support for Sorted Sets.
*   Added start scripts.
*   Added Kafka Connect and Schema Registry CLI.
*   Kafka Connect CLI now supports pause/restart/resume; checking connectors on the classpath and validating configuration of connectors.
*   Support for ``Struct``, ``Schema.STRING`` and ``Json`` with schema in the Cassandra, ReThinkDB, InfluxDB and MongoDB sinks.
*   Rename ``export.query.route`` to ``sink.kcql``.
*   Rename ``import.query.route`` to ``source.kcql``.
*   Upgrade to KCQL 0.9.5 - Add support for `STOREAS` so specify target sink types, e.g. Redis Sorted Sets, Hazelcast map, queues, ringbuffers.

### Building

***Requires gradle 3.0 to build.***

To build

```bash
gradle compile
```

To test

```bash
gradle test
```

To create a fat jar

```bash
gradle shadowJar
```

You can also use the gradle wrapper

```
./gradlew shadowJar
```

To view dependency trees

```
gradle dependencies # or
gradle :kafka-connect-cassandra:dependencies
```
