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

package com.island.ohara.configurator

import com.island.ohara.client.configurator.v0.ConnectorApi.{ConnectorCreationRequest, ConnectorState}
import com.island.ohara.client.configurator.v0.PipelineApi.PipelineCreationRequest
import com.island.ohara.client.configurator.v0.TopicApi.TopicCreationRequest
import com.island.ohara.client.configurator.v0.{ConnectorApi, PipelineApi, TopicApi}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.testing.WithBrokerWorker
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
class TestOhara1415 extends WithBrokerWorker with Matchers {

  private[this] val configurator =
    Configurator.builder().fake(testUtil.brokersConnProps(), testUtil().workersConnProps()).build()

  private[this] def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  @Test
  def startUnknownConnector(): Unit = {
    val topicName = methodName

    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(name = Some(topicName),
                               brokerClusterName = None,
                               numberOfPartitions = None,
                               numberOfReplications = None)))

    val connector = result(
      ConnectorApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          ConnectorCreationRequest(
            workerClusterName = None,
            className = Some("asdasdasd"),
            columns = Seq.empty,
            topicNames = Seq(topic.id),
            numberOfTasks = Some(1),
            settings = Map.empty
          )))
    an[IllegalArgumentException] should be thrownBy result(
      ConnectorApi.access().hostname(configurator.hostname).port(configurator.port).start(connector.id))
  }

  @Test
  def startConnectorWhichShouldFail(): Unit = {
    val topicName = methodName
    val topic = result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(name = Some(topicName),
                               brokerClusterName = None,
                               numberOfPartitions = None,
                               numberOfReplications = None)))

    val connector = result(
      ConnectorApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(ConnectorCreationRequest(
          workerClusterName = None,
          className = Some(classOf[DumbSink].getName),
          columns = Seq.empty,
          topicNames = Seq(topic.id),
          numberOfTasks = Some(1),
          settings = Map("you_should_fail" -> "asdasd")
        )))

    result(ConnectorApi.access().hostname(configurator.hostname).port(configurator.port).start(connector.id))

    CommonUtils.await(
      () => {
        try {
          val c =
            result(ConnectorApi.access().hostname(configurator.hostname).port(configurator.port).get(connector.id))
          c.state.contains(ConnectorState.FAILED) && c.error.nonEmpty
        } catch {
          case _: Throwable => false
        }
      },
      java.time.Duration.ofSeconds(10)
    )

    val pipeline = result(
      PipelineApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(PipelineCreationRequest(name = methodName(),
                                     workerClusterName = None,
                                     rules = Map(topic.id -> Seq(connector.id)))))
    pipeline.objects.find(_.id == connector.id).get.state shouldBe Some("FAILED")
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
