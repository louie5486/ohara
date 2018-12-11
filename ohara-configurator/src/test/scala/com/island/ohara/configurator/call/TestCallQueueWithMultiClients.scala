package com.island.ohara.configurator.call

import java.time.Duration

import com.island.ohara.client.ConfiguratorJson._
import com.island.ohara.common.data.DataType
import com.island.ohara.common.util.{CloseOnce, CommonUtil}
import com.island.ohara.integration.With3Brokers
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
class TestCallQueueWithMultiClients extends With3Brokers with Matchers {
  private[this] val requestTopicName = random()
  private[this] val responseTopicName = random()
  private[this] val defaultServerBuilder =
    CallQueue
      .serverBuilder()
      .brokers(testUtil.brokersConnProps)
      .requestTopic(requestTopicName)
      .responseTopic(responseTopicName)
      .groupId(com.island.ohara.common.util.CommonUtil.uuid())
  private[this] val server0: CallQueueServer[SourceRequest, Source] =
    defaultServerBuilder.build[SourceRequest, Source]()
  private[this] val server1: CallQueueServer[SourceRequest, Source] =
    defaultServerBuilder.build[SourceRequest, Source]()
  private[this] val server2: CallQueueServer[SourceRequest, Source] =
    defaultServerBuilder.build[SourceRequest, Source]()

  private[this] val servers = Seq(server0, server1, server2)

  private[this] val requestData: SourceRequest =
    SourceRequest(name = "name",
                  className = "jdbc",
                  topics = Seq.empty,
                  numberOfTasks = 1,
                  schema = Seq(Column("cf", DataType.BOOLEAN, 1)),
                  configs = Map("a" -> "b"))
  private[this] val responseData: Source =
    Source(
      uuid = "uuid",
      name = "name2",
      className = "jdbc",
      schema = Seq(Column("cf", DataType.BOOLEAN, 1)),
      configs = Map("a" -> "b"),
      lastModified = com.island.ohara.common.util.CommonUtil.current(),
      numberOfTasks = 1,
      topics = Seq.empty,
      state = None
    )
  @Test
  def test(): Unit = {
    val clientCount = 10
    val clients = 0 until clientCount map { _ =>
      CallQueue
        .clientBuilder()
        .brokers(testUtil.brokersConnProps)
        .requestTopic(requestTopicName)
        .responseTopic(responseTopicName)
        .build[SourceRequest, Source]()
    }
    val requests = clients.map(_.request(requestData))
    // wait the one from servers receive the request
    CommonUtil.await(() => servers.map(_.countOfUndealtTasks).sum == clientCount, Duration.ofSeconds(10))
    val tasks = servers.flatMap(server => {
      Iterator.continually(server.take(1 second)).takeWhile(_.isDefined).map(_.get)
    })
    tasks.size shouldBe clientCount

    tasks.foreach(_.complete(responseData))

    requests.foreach(Await.result(_, 10 seconds) match {
      case Right(r) => r shouldBe responseData
      case _        => throw new RuntimeException("All requests should work")
    })
  }

  @After
  def tearDown(): Unit = servers.foreach(CloseOnce.close)

}