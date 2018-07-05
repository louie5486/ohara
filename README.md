# Ohara

a powerful ETL tool

## Getting Started

[TODO]

### Prerequisites

* JDK1.8+
* Scala 2.12
* gradle 4.5+

### Installing

[TODO]

## Running the tests

```
gradle test
```
The test report is in ./ohara-{module}/build/reports/tests/test/.

## Building a binary release

*gzipped ball*

```
gradle clean distZip
```

*tarred ball*

```
gradle clean distTar
```

*ZIP and TAR*

```
gradle clean build
```

The release files are in ./ohara-assembly/build/.

## quick start configurator

```
gradle runConfigurator
```
## quick start ohara

```
gradle runOhara
```
NOTED: a full ohara service consists of 1) manager, 2) configurator and 3) kafka cluster. The above command starts
a local kafka cluster with 3 brokers.

## Built With

* [Kafka](https://github.com/apache/kafka) - streaming tool
* [AKKA](https://akka.io/) - message-driven tool
* [Gradle](https://gradle.org) - dependency Management
* [SLF4J](https://www.slf4j.org/) - LOG wrapper
* [SCALALOGGING](https://github.com/typesafehub/scalalogging) - LOG wrapper
* [LOG4J](https://logging.apache.org/log4j/2.x/) - log plugin default

## Versioning

[TODO]

## Authors

* **Vito Jeng (vito@is-land.com.tw)** - leader
* **Yung-An He (stana@is-land.com.tw)** - manager
* **Jimin Hsieh (jimin.hsieh@is-land.com.tw)** - committer
* **Jack Yang (jack@is-land.com.tw)** - committer
* **Chia-Ping Tsai (chia7712@is-land.com.tw)** - committer
* **Joshua_Lin (joshua@is-land.com.tw)** - committer

## License

[TODO] This project is licensed under the is-land License

