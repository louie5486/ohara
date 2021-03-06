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

package com.island.ohara.configurator.route

import com.island.ohara.client.configurator.v0.ConnectorApi.{ConnectorCreationRequest, ConnectorDescription}
import com.island.ohara.client.configurator.v0.TopicApi.TopicCreationRequest
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterCreationRequest
import com.island.ohara.client.configurator.v0.{BrokerApi, ConnectorApi, TopicApi, WorkerApi}
import com.island.ohara.common.data.{Column, DataType}
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import com.island.ohara.kafka.connector.json.SettingDefinition
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
class TestConnectorRoute extends SmallTest with Matchers {
  private[this] val configurator = Configurator.builder().fake(1, 1).build()

  private[this] val connectorApi = ConnectorApi.access().hostname(configurator.hostname).port(configurator.port)

  @Test
  def runConnectorWithoutTopic(): Unit = {
    val connector = result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = Seq.empty,
          settings = Map("c0" -> "v0", "c1" -> "v1"),
          topicNames = Seq.empty,
          numberOfTasks = Some(1)
        )))

    an[IllegalArgumentException] should be thrownBy result(connectorApi.start(connector.id))
  }

  @Test
  def testSource(): Unit = {
    def compareRequestAndResponse(request: ConnectorCreationRequest,
                                  response: ConnectorDescription): ConnectorDescription = {
      request.columns shouldBe response.columns
      request.settings shouldBe response.settings.filter(_._1 != SettingDefinition.WORKER_CLUSTER_NAME_DEFINITION.key())
      response
    }

    def compare2Response(lhs: ConnectorDescription, rhs: ConnectorDescription): Unit = {
      lhs.id shouldBe rhs.id
      lhs.name shouldBe rhs.name
      lhs.columns shouldBe rhs.columns
      lhs.settings shouldBe rhs.settings
      lhs.lastModified shouldBe rhs.lastModified
    }

    val columns = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build(),
                      Column.builder().name("cf").dataType(DataType.BOOLEAN).order(2).build())
    // test add
    result(connectorApi.list).size shouldBe 0
    val request = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some("jdbc"),
      columns = columns,
      settings = Map("c0" -> "v0", "c1" -> "v1"),
      topicNames = Seq.empty,
      numberOfTasks = Some(1)
    )
    val response =
      compareRequestAndResponse(request, result(connectorApi.add(request)))

    // test get
    compare2Response(response, result(connectorApi.get(response.id)))

    // test update
    val anotherRequest = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some("jdbc"),
      columns = columns,
      settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
      topicNames = Seq.empty,
      numberOfTasks = Some(1)
    )
    val newResponse =
      compareRequestAndResponse(anotherRequest, result(connectorApi.update(response.id, anotherRequest)))

    // test get
    compare2Response(newResponse, result(connectorApi.get(newResponse.id)))

    // test delete
    result(connectorApi.list).size shouldBe 1
    result(connectorApi.delete(response.id))
    result(connectorApi.list).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(connectorApi.get("asdasdasd"))
    an[IllegalArgumentException] should be thrownBy result(connectorApi.update("Asdasd", anotherRequest))
  }

  @Test
  def testInvalidSource(): Unit = {
    result(connectorApi.list).size shouldBe 0

    val illegalOrder = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(0).build(),
                           Column.builder().name("cf").dataType(DataType.BOOLEAN).order(2).build())
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = illegalOrder,
          settings = Map("c0" -> "v0", "c1" -> "v1"),
          topicNames = Seq.empty,
          numberOfTasks = Some(1)
        )))
    result(connectorApi.list).size shouldBe 0

    val duplicateOrder = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build(),
                             Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build())
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = duplicateOrder,
          settings = Map("c0" -> "v0", "c1" -> "v1"),
          topicNames = Seq.empty,
          numberOfTasks = Some(1)
        )))
    result(connectorApi.list).size shouldBe 0
  }

  @Test
  def testSink(): Unit = {
    def compareRequestAndResponse(request: ConnectorCreationRequest,
                                  response: ConnectorDescription): ConnectorDescription = {
      request.settings shouldBe response.settings.filter(_._1 != SettingDefinition.WORKER_CLUSTER_NAME_DEFINITION.key())
      response
    }

    def compare2Response(lhs: ConnectorDescription, rhs: ConnectorDescription): Unit = {
      lhs.id shouldBe rhs.id
      lhs.name shouldBe rhs.name
      lhs.columns shouldBe rhs.columns
      lhs.settings shouldBe rhs.settings
      lhs.lastModified shouldBe rhs.lastModified
    }

    val columns = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build(),
                      Column.builder().name("cf").dataType(DataType.BOOLEAN).order(2).build())

    // test add
    result(connectorApi.list).size shouldBe 0
    val request = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some("jdbc"),
      columns = columns,
      settings = Map("c0" -> "v0", "c1" -> "v1"),
      topicNames = Seq.empty,
      numberOfTasks = Some(3)
    )
    val response =
      compareRequestAndResponse(request, result(connectorApi.add(request)))

    // test get
    compare2Response(response, result(connectorApi.get(response.id)))

    // test update
    val anotherRequest = ConnectorCreationRequest(
      workerClusterName = None,
      className = Some("jdbc"),
      columns = columns,
      settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
      topicNames = Seq.empty,
      numberOfTasks = Some(1)
    )
    val newResponse =
      compareRequestAndResponse(anotherRequest, result(connectorApi.update(response.id, anotherRequest)))

    // test get
    compare2Response(newResponse, result(connectorApi.get(newResponse.id)))

    // test delete
    result(connectorApi.list).size shouldBe 1
    result(connectorApi.delete(response.id))
    result(connectorApi.list).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(connectorApi.get("asdasdasd"))
    an[IllegalArgumentException] should be thrownBy result(connectorApi.update("Asdasd", anotherRequest))
  }

  @Test
  def testInvalidSink(): Unit = {

    result(connectorApi.list).size shouldBe 0

    val illegalOrder = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(0).build(),
                           Column.builder().name("cf").dataType(DataType.BOOLEAN).order(2).build())
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = illegalOrder,
          settings = Map("c0" -> "v0", "c1" -> "v1"),
          topicNames = Seq.empty,
          numberOfTasks = Some(1)
        )))
    result(connectorApi.list).size shouldBe 0

    val duplicateOrder = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build(),
                             Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build())
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = duplicateOrder,
          settings = Map("c0" -> "v0", "c1" -> "v1"),
          topicNames = Seq.empty,
          numberOfTasks = Some(1)
        )))
    result(connectorApi.list).size shouldBe 0
  }

  @Test
  def removeConnectorFromDeletedCluster(): Unit = {
    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq.empty,
        numberOfTasks = Some(1)
      )))

    result(configurator.clusterCollie.workerCollie().remove(connector.workerClusterName))

    result(connectorApi.delete(connector.id))

    result(connectorApi.list).exists(_.id == connector.id) shouldBe false
  }

  @Test
  def runConnectorOnNonexistentCluster(): Unit = {
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = Some(CommonUtils.randomString(10)),
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq.empty,
        numberOfTasks = Some(1)
      )))
  }

  @Test
  def runConnectorWithoutSpecificCluster(): Unit = {
    val bk = result(BrokerApi.access().hostname(configurator.hostname).port(configurator.port).list).head

    val wk = result(
      WorkerApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(WorkerClusterCreationRequest(
          name = CommonUtils.randomString(10),
          imageName = None,
          brokerClusterName = Some(bk.name),
          clientPort = Some(CommonUtils.availablePort()),
          jmxPort = Some(CommonUtils.availablePort()),
          groupId = Some(CommonUtils.randomString(10)),
          statusTopicName = Some(CommonUtils.randomString(10)),
          statusTopicPartitions = None,
          statusTopicReplications = None,
          configTopicName = Some(CommonUtils.randomString(10)),
          configTopicReplications = None,
          offsetTopicName = Some(CommonUtils.randomString(10)),
          offsetTopicPartitions = None,
          offsetTopicReplications = None,
          jarIds = Seq.empty,
          nodeNames = bk.nodeNames
        )))

    // there are two worker cluster so it fails to match worker cluster
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq.empty,
        numberOfTasks = Some(1)
      )))

    result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = Some(wk.name),
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq.empty,
        numberOfTasks = Some(1)
      ))).workerClusterName shouldBe wk.name

  }

  @Test
  def testIdempotentPause(): Unit = {
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(
            name = Some(CommonUtils.randomString(10)),
            brokerClusterName = None,
            numberOfPartitions = None,
            numberOfReplications = None
          )))

    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq(topic.id),
        numberOfTasks = Some(1)
      )))

    result(connectorApi.start(connector.id)).state should not be None

    (0 to 10).foreach(_ => result(connectorApi.pause(connector.id)).state should not be None)
  }

  @Test
  def testIdempotentResume(): Unit = {
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(
            name = Some(CommonUtils.randomString(10)),
            brokerClusterName = None,
            numberOfPartitions = None,
            numberOfReplications = None
          )))

    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq(topic.id),
        numberOfTasks = Some(1)
      )))

    result(connectorApi.start(connector.id)).state should not be None

    (0 to 10).foreach(_ => result(connectorApi.resume(connector.id)).state should not be None)
  }

  @Test
  def testIdempotentStop(): Unit = {
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(
            name = Some(CommonUtils.randomString(10)),
            brokerClusterName = None,
            numberOfPartitions = None,
            numberOfReplications = None
          )))

    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq(topic.id),
        numberOfTasks = Some(1)
      )))

    result(connectorApi.start(connector.id)).state should not be None

    (0 to 10).foreach(_ => result(connectorApi.stop(connector.id)).state shouldBe None)
  }

  @Test
  def testIdempotentStart(): Unit = {
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(
            name = Some(CommonUtils.randomString(10)),
            brokerClusterName = None,
            numberOfPartitions = None,
            numberOfReplications = None
          )))

    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq(topic.id),
        numberOfTasks = Some(1)
      )))

    result(connectorApi.start(connector.id)).state should not be None

    (0 to 10).foreach(_ => result(connectorApi.start(connector.id)).state should not be None)
  }

  @Test
  def failToChangeWorkerCluster(): Unit = {
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(
            name = Some(CommonUtils.randomString(10)),
            brokerClusterName = None,
            numberOfPartitions = None,
            numberOfReplications = None
          )))

    val connector = result(
      connectorApi.add(ConnectorCreationRequest(
        workerClusterName = None,
        className = Some("jdbc"),
        columns = Seq.empty,
        settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
        topicNames = Seq(topic.id),
        numberOfTasks = Some(1)
      )))

    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.update(
        connector.id,
        ConnectorCreationRequest(
          workerClusterName = Some("adadasd"),
          className = Some("jdbc"),
          columns = Seq.empty,
          settings = Map("c0" -> "v0", "c1" -> "v1", "c2" -> "v2"),
          topicNames = Seq(topic.id),
          numberOfTasks = Some(1)
        )
      ))
  }

  @Test
  def defaultNumberOfTasksShouldExist(): Unit = {
    val connectorDesc = result(
      connectorApi.add(
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = Seq.empty,
          settings = Map.empty,
          topicNames = Seq.empty,
          numberOfTasks = None
        )))
    connectorDesc.numberOfTasks shouldBe ConnectorRoute.DEFAULT_NUMBER_OF_TASKS

    result(
      connectorApi.update(
        connectorDesc.id,
        ConnectorCreationRequest(
          workerClusterName = None,
          className = Some("jdbc"),
          columns = Seq.empty,
          settings = Map.empty,
          topicNames = Seq.empty,
          numberOfTasks = None
        )
      )).numberOfTasks shouldBe ConnectorRoute.DEFAULT_NUMBER_OF_TASKS
  }
  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
