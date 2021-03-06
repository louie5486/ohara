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

import com.island.ohara.client.configurator.v0.HadoopApi.{HdfsInfo, HdfsInfoRequest}
import com.island.ohara.client.configurator.v0.{HadoopApi, TopicApi}
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global

class TestHdfsInfoRoute extends SmallTest with Matchers {
  private[this] val configurator = Configurator.builder().fake().build()

  private[this] val hdfsApi = HadoopApi.access().hostname(configurator.hostname).port(configurator.port)
  @Test
  def test(): Unit = {
    def compareRequestAndResponse(request: HdfsInfoRequest, response: HdfsInfo): HdfsInfo = {
      request.name shouldBe response.name
      request.uri shouldBe response.uri
      response
    }

    def compare2Response(lhs: HdfsInfo, rhs: HdfsInfo): Unit = {
      lhs.id shouldBe rhs.id
      lhs.name shouldBe rhs.name
      lhs.uri shouldBe rhs.uri
      lhs.lastModified shouldBe rhs.lastModified
    }

    // test add
    result(hdfsApi.list).size shouldBe 0
    val request = HdfsInfoRequest(methodName, "file:///")
    val response = result(hdfsApi.add(request))

    // test get
    compare2Response(response, result(hdfsApi.get(response.id)))

    // test update
    val anotherRequest = HdfsInfoRequest(s"$methodName-2", "file:///")
    val newResponse =
      compareRequestAndResponse(anotherRequest, result(hdfsApi.update(response.id, anotherRequest)))

    // test get
    compare2Response(newResponse, result(hdfsApi.get(newResponse.id)))

    // test delete
    result(hdfsApi.list).size shouldBe 1
    result(hdfsApi.delete(response.id))
    result(hdfsApi.list).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(hdfsApi.get("123"))
    an[IllegalArgumentException] should be thrownBy result(hdfsApi.update("777", anotherRequest))
  }

  @Test
  def testGet2UnmatchedType(): Unit = {
    val access = HadoopApi.access().hostname(configurator.hostname).port(configurator.port)
    result(access.list).size shouldBe 0
    val request = HdfsInfoRequest(methodName, "file:///")
    var response: HdfsInfo = result(access.add(request))
    request.name shouldBe response.name
    request.uri shouldBe response.uri

    response = result(access.get(response.id))
    request.name shouldBe response.name
    request.uri shouldBe response.uri

    an[IllegalArgumentException] should be thrownBy result(
      TopicApi.access().hostname(configurator.hostname).port(configurator.port).get(response.id))
    result(access.delete(response.id))
  }

  @Test
  def duplicateDeleteStreamProperty(): Unit =
    (0 to 10).foreach(_ => result(hdfsApi.delete(CommonUtils.randomString(5))))

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
