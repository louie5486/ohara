/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.agent
import java.util.Objects

import com.island.ohara.client.configurator.v0.{BrokerApi, ClusterInfo}
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.kafka.TopicMeter

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait BrokerCollie extends Collie[BrokerClusterInfo, BrokerCollie.ClusterCreator] {

  /**
    * Create a topic admin according to passed cluster name.
    * Noted: if target cluster doesn't exist, an future with exception will return
    * @param clusterName target cluster
    * @return cluster info and topic admin
    */
  def topicAdmin(clusterName: String)(
    implicit executionContext: ExecutionContext): Future[(BrokerClusterInfo, TopicAdmin)] = cluster(clusterName).map {
    case (c, _) => (c, topicAdmin(c))
  }

  /**
    * Create a topic admin according to passed cluster.
    * @param cluster target cluster
    * @return topic admin
    */
  def topicAdmin(cluster: BrokerClusterInfo): TopicAdmin = TopicAdmin(cluster.connectionProps)

  /**
    * Get all meter beans from specific broker cluster
    * @param cluster cluster
    * @return meter beans
    */
  def topicMeters(cluster: BrokerClusterInfo): Seq[TopicMeter] = cluster.nodeNames.flatMap { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().topicMeters().asScala
  }

  private[agent] def toBrokerCluster(clusterName: String, containers: Seq[ContainerInfo]): Future[BrokerClusterInfo] = {
    val first = containers.head
    Future.successful(
      BrokerClusterInfo(
        name = clusterName,
        imageName = first.imageName,
        zookeeperClusterName = first.environments(BrokerCollie.ZOOKEEPER_CLUSTER_NAME),
        exporterPort = first.environments(BrokerCollie.EXPORTER_PORT_KEY).toInt,
        clientPort = first.environments(BrokerCollie.CLIENT_PORT_KEY).toInt,
        jmxPort = first.environments(BrokerCollie.JMX_PORT_KEY).toInt,
        nodeNames = containers.map(_.nodeName)
      ))
  }
}

object BrokerCollie {
  trait ClusterCreator extends Collie.ClusterCreator[BrokerClusterInfo] {
    private[this] var clientPort: Int = BrokerApi.CLIENT_PORT_DEFAULT
    private[this] var zookeeperClusterName: String = _
    private[this] var exporterPort: Int = BrokerApi.EXPORTER_PORT_DEFAULT
    private[this] var jmxPort: Int = BrokerApi.JMX_PORT_DEFAULT

    override def copy(clusterInfo: ClusterInfo): ClusterCreator.this.type = clusterInfo match {
      case bk: BrokerClusterInfo =>
        super.copy(clusterInfo)
        zookeeperClusterName(bk.zookeeperClusterName)
        clientPort(bk.clientPort)
        exporterPort(bk.exporterPort)
        jmxPort(bk.jmxPort)
        this
      case _ =>
        throw new IllegalArgumentException(
          s"you should pass BrokerClusterInfo rather than ${clusterInfo.getClass.getName}")
    }

    def zookeeperClusterName(zookeeperClusterName: String): ClusterCreator = {
      this.zookeeperClusterName = CommonUtils.requireNonEmpty(zookeeperClusterName)
      this
    }

    @Optional("default is com.island.ohara.client.configurator.v0.BrokerApi.CLIENT_PORT_DEFAULT")
    def clientPort(clientPort: Int): ClusterCreator = {
      this.clientPort = CommonUtils.requirePositiveInt(clientPort)
      this
    }

    @Optional("default is com.island.ohara.client.configurator.v0.BrokerApi.EXPORTER_PORT_DEFAULT")
    def exporterPort(exporterPort: Int): ClusterCreator = {
      this.exporterPort = CommonUtils.requirePositiveInt(exporterPort)
      this
    }

    @Optional("default is BrokerApi.CLIENT_PORT_DEFAULT")
    def jmxPort(jmxPort: Int): ClusterCreator = {
      this.jmxPort = CommonUtils.requirePositiveInt(jmxPort)
      this
    }

    override def create()(implicit executionContext: ExecutionContext): Future[BrokerClusterInfo] = doCreate(
      executionContext = Objects.requireNonNull(executionContext),
      clusterName = CommonUtils.requireNonEmpty(clusterName),
      imageName = CommonUtils.requireNonEmpty(imageName),
      zookeeperClusterName = CommonUtils.requireNonEmpty(zookeeperClusterName),
      clientPort = CommonUtils.requirePositiveInt(clientPort),
      exporterPort = CommonUtils.requirePositiveInt(exporterPort),
      jmxPort = CommonUtils.requirePositiveInt(jmxPort),
      nodeNames = CommonUtils.requireNonEmpty(nodeNames.asJava).asScala
    )

    protected def doCreate(executionContext: ExecutionContext,
                           clusterName: String,
                           imageName: String,
                           zookeeperClusterName: String,
                           clientPort: Int,
                           exporterPort: Int,
                           jmxPort: Int,
                           nodeNames: Seq[String]): Future[BrokerClusterInfo]
  }

  private[agent] val ID_KEY: String = "BROKER_ID"
  private[agent] val DATA_DIRECTORY_KEY: String = "BROKER_DATA_DIR"
  private[agent] val ZOOKEEPERS_KEY: String = "BROKER_ZOOKEEPERS"
  private[agent] val CLIENT_PORT_KEY: String = "BROKER_CLIENT_PORT"
  private[agent] val ADVERTISED_HOSTNAME_KEY: String = "BROKER_ADVERTISED_HOSTNAME"
  private[agent] val ADVERTISED_CLIENT_PORT_KEY: String = "BROKER_ADVERTISED_CLIENT_PORT"
  private[agent] val EXPORTER_PORT_KEY: String = "PROMETHEUS_EXPORTER_PORT"
  private[agent] val JMX_HOSTNAME_KEY: String = "JMX_HOSTNAME"
  private[agent] val JMX_PORT_KEY: String = "JMX_PORT"

  /**
    * internal key used to save the zookeeper cluster name.
    * All nodes of broker cluster should have this environment variable.
    */
  private[agent] val ZOOKEEPER_CLUSTER_NAME: String = "CCI_ZOOKEEPER_CLUSTER_NAME"
}
