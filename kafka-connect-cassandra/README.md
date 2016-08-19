![](../images/DM-logo.jpg)
[![Documentation Status](https://readthedocs.org/projects/streamreactor/badge?version=latest)](http://docs.datamountaineer.com/en/latest/cassandra.html?badge=latest)

# Kafka Connect Cassandra

A Connector and Sink to write events from Kafka to Cassandra. 

**Please go to the documentation for Usage.**

## Perquisites
* Cassandra 2.2.4
* Confluent 2.0.1
* Java 1.8 
* Scala 2.11

##Build

```bash
gradle compile
```

To test

```bash
gradle test
```

To create a fat jar

```bash
gradle fatJar
```

or with no tests run

```
gradle fatJarNoTest
```

You can also use the gradle wrapper

```
./gradlew fatJar
```

See the documentation for more information.
