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

import com.island.ohara.client.configurator.v0.ConnectorApi.ConnectorCreationRequest
import com.island.ohara.client.configurator.v0.PipelineApi.{Flow, PipelineCreationRequest}
import com.island.ohara.client.configurator.v0.{ConnectorApi, PipelineApi, TopicApi}
import com.island.ohara.client.configurator.v0.TopicApi.TopicCreationRequest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.testing.WithBrokerWorker
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
class TestListManyPipelines extends WithBrokerWorker with Matchers {

  private[this] val configurator =
    Configurator.builder().fake(testUtil().brokersConnProps(), testUtil().workersConnProps()).build()

  private[this] val numberOfPipelines = 30
  @Test
  def test(): Unit = {
    val topic = Await.result(
      TopicApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(
          TopicCreationRequest(name = Some(CommonUtils.randomString(10)),
                               brokerClusterName = None,
                               numberOfPartitions = None,
                               numberOfReplications = None)),
      10 seconds
    )
    val connector = Await.result(
      ConnectorApi
        .access()
        .hostname(configurator.hostname)
        .port(configurator.port)
        .add(ConnectorCreationRequest(
          className = Some("com.island.ohara.connector.perf.PerfSource"),
          columns = Seq.empty,
          topicNames = Seq(topic.name),
          numberOfTasks = Some(1),
          settings = Map.empty,
          workerClusterName = None
        )),
      10 seconds
    )

    val pipelines = (0 until numberOfPipelines).map { index =>
      Await.result(
        PipelineApi
          .access()
          .hostname(configurator.hostname)
          .port(configurator.port)
          .add(
            PipelineCreationRequest(
              name = s"pipeline-$index",
              flows = Seq(
                Flow(
                  from = connector.id,
                  to = Seq(topic.id)
                )),
              workerClusterName = None
            )),
        10 seconds
      )
    }

    val listPipeline =
      Await.result(PipelineApi.access().hostname(configurator.hostname).port(configurator.port).list, 10 seconds)
    pipelines.size shouldBe listPipeline.size
    pipelines.foreach { p =>
      listPipeline.exists(_.id == p.id) shouldBe true
    }
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
