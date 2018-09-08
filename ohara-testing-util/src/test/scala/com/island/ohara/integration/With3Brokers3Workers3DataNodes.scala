package com.island.ohara.integration

import com.island.ohara.io.CloseOnce.close
import com.island.ohara.rule.LargeTest
import org.junit.{AfterClass, BeforeClass}

/**
  * This class create a mini broker/worker/datanode cluster with 3 nodes. And the cluster will be closed after all test cases have been done.
  */
abstract class With3Brokers3Workers3DataNodes extends LargeTest {
  protected def testUtil: OharaTestUtil = With3Brokers3Workers3DataNodes.util
}

object With3Brokers3Workers3DataNodes {
  private var util: OharaTestUtil = _

  @BeforeClass
  def beforeAll(): Unit = {
    util = OharaTestUtil.builder().numberOfBrokers(3).numberOfWorkers(3).numberOfDataNodes(3).build()
  }

  @AfterClass
  def afterAll(): Unit = {
    close(util)
  }
}
