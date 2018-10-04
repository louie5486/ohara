package com.island.ohara.connector.ftp
import java.io.{BufferedWriter, OutputStreamWriter}

import com.island.ohara.client.ConfiguratorJson.Column
import com.island.ohara.client.FtpClient
import com.island.ohara.data.{Cell, Row}
import com.island.ohara.integration.{OharaTestUtil, With3Brokers3Workers}
import com.island.ohara.io.{CloseOnce, IoUtil}
import com.island.ohara.kafka.{Consumer, ConsumerRecord}
import com.island.ohara.serialization.DataType
import org.junit.{After, Before, Test}
import org.scalatest.Matchers

import scala.concurrent.duration._

class TestFtpSource extends With3Brokers3Workers with Matchers {
  var consumer: Consumer[Array[Byte], Row] = _

  private[this] val schema: Seq[Column] = Seq(
    Column("name", DataType.STRING, 1),
    Column("ranking", DataType.INT, 2),
    Column("single", DataType.BOOLEAN, 3)
  )
  private[this] val rows: Seq[Row] = Seq(
    Row(Cell("name", "chia"), Cell("ranking", 1), Cell("single", false)),
    Row(Cell("name", "jack"), Cell("ranking", 99), Cell("single", true))
  )
  private[this] val header: String = rows.head.map(_.name).mkString(",")
  private[this] val data: Seq[String] = rows.map(row => {
    row.map(_.value.toString).mkString(",")
  })
  private[this] val ftpClient = FtpClient
    .builder()
    .host(testUtil.ftpServer.host)
    .port(testUtil.ftpServer.port)
    .user(testUtil.ftpServer.writableUser.name)
    .password(testUtil.ftpServer.writableUser.password)
    .build()

  private[this] val props = FtpSourceProps(
    input = "/input",
    output = "/output",
    error = "/error",
    user = testUtil.ftpServer.writableUser.name,
    password = testUtil.ftpServer.writableUser.password,
    host = testUtil.ftpServer.host,
    port = testUtil.ftpServer.port,
    encode = Some("UTF-8")
  )

  private[this] def setupInput(): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(ftpClient.create(IoUtil.path(props.input, "abc"))))
    try {
      writer.append(header)
      writer.newLine()
      data.foreach(line => {
        writer.append(line)
        writer.newLine()
      })
    } finally writer.close()
  }

  @Before
  def setup(): Unit = {
    def rebuild(path: String): Unit = {
      if (ftpClient.exist(path)) {
        ftpClient.listFileNames(path).map(IoUtil.path(path, _)).foreach(ftpClient.delete)
        ftpClient.listFileNames(path).size shouldBe 0
        ftpClient.delete(path)
      }
      ftpClient.mkdir(path)
    }
    // cleanup all files in order to avoid corrupted files
    rebuild(props.input)
    rebuild(props.error)
    rebuild(props.output)
    setupInput()
    ftpClient.listFileNames(props.input).isEmpty shouldBe false
  }

  private[this] def pollData(topicName: String,
                             timeout: Duration = 60 seconds,
                             size: Int = data.length): Seq[ConsumerRecord[Array[Byte], Row]] = {
    if (consumer == null)
      consumer =
        Consumer.builder().topicName(methodName).offsetFromBegin().brokers(testUtil.brokers).build[Array[Byte], Row]

    consumer.poll(timeout, size)
  }

  private[this] def checkFileCount(inputCount: Int, outputCount: Int, errorCount: Int): Unit = {
    OharaTestUtil.await(
      () => {
        ftpClient.listFileNames(props.input).size == inputCount &&
        ftpClient.listFileNames(props.output).size == outputCount &&
        ftpClient.listFileNames(props.error).size == errorCount
      },
      10 seconds
    )
  }

  @Test
  def testDuplicateInput(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(schema)
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)

      checkFileCount(0, 1, 0)
      var records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 3
      row0.cell(0) shouldBe rows(0).cell(0)
      row0.cell(1) shouldBe rows(0).cell(1)
      row0.cell(2) shouldBe rows(0).cell(2)
      val row1 = records(1).value.get
      row1.size shouldBe 3
      row1.cell(0) shouldBe rows(1).cell(0)
      row1.cell(1) shouldBe rows(1).cell(1)
      row1.cell(2) shouldBe rows(1).cell(2)

      // put a duplicate file
      setupInput()
      checkFileCount(0, 2, 0)
      records = pollData(topicName, 5 second)
      records.size shouldBe 0

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testColumnRename(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(
        Seq(
          Column("name", "newName", DataType.STRING, 1),
          Column("ranking", "newRanking", DataType.INT, 2),
          Column("single", "newSingle", DataType.BOOLEAN, 3)
        ))
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 1, 0)

      val records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 3
      row0.cell(0).name shouldBe "newName"
      row0.cell(0).value shouldBe rows(0).cell(0).value
      row0.cell(1).name shouldBe "newRanking"
      row0.cell(1).value shouldBe rows(0).cell(1).value
      row0.cell(2).name shouldBe "newSingle"
      row0.cell(2).value shouldBe rows(0).cell(2).value
      val row1 = records(1).value.get
      row1.size shouldBe 3
      row0.cell(0).name shouldBe "newName"
      row1.cell(0).value shouldBe rows(1).cell(0).value
      row0.cell(1).name shouldBe "newRanking"
      row1.cell(1).value shouldBe rows(1).cell(1).value
      row0.cell(2).name shouldBe "newSingle"
      row1.cell(2).value shouldBe rows(1).cell(2).value

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testObjectType(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(
        Seq(
          Column("name", DataType.OBJECT, 1),
          Column("ranking", DataType.INT, 2),
          Column("single", DataType.BOOLEAN, 3)
        ))
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 1, 0)

      val records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 3
      row0.cell(0) shouldBe rows(0).cell(0)
      row0.cell(1) shouldBe rows(0).cell(1)
      row0.cell(2) shouldBe rows(0).cell(2)
      val row1 = records(1).value.get
      row1.size shouldBe 3
      row1.cell(0) shouldBe rows(1).cell(0)
      row1.cell(1) shouldBe rows(1).cell(1)
      row1.cell(2) shouldBe rows(1).cell(2)

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testNormalCase(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(schema)
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 1, 0)

      val records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 3
      row0.cell(0) shouldBe rows(0).cell(0)
      row0.cell(1) shouldBe rows(0).cell(1)
      row0.cell(2) shouldBe rows(0).cell(2)
      val row1 = records(1).value.get
      row1.size shouldBe 3
      row1.cell(0) shouldBe rows(1).cell(0)
      row1.cell(1) shouldBe rows(1).cell(1)
      row1.cell(2) shouldBe rows(1).cell(2)

    } finally testUtil.connectorClient.delete(connectorName)

  }

  @Test
  def testNormalCaseWithoutSchema(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 1, 0)

      val records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 3
      // NOTED: without schema all value are converted to string
      row0.cell(0) shouldBe Cell(rows(0).cell(0).name, rows(0).cell(0).value.toString)
      row0.cell(1) shouldBe Cell(rows(0).cell(1).name, rows(0).cell(1).value.toString)
      row0.cell(2) shouldBe Cell(rows(0).cell(2).name, rows(0).cell(2).value.toString)
      val row1 = records(1).value.get
      row1.size shouldBe 3
      row1.cell(0) shouldBe Cell(rows(1).cell(0).name, rows(1).cell(0).value.toString)
      row1.cell(1) shouldBe Cell(rows(1).cell(1).name, rows(1).cell(1).value.toString)
      row1.cell(2) shouldBe Cell(rows(1).cell(2).name, rows(1).cell(2).value.toString)

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testPartialColumns(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      // skip last column
      .schema(schema.slice(0, schema.length - 1))
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 1, 0)

      val records = pollData(topicName)
      records.size shouldBe data.length
      val row0 = records(0).value.get
      row0.size shouldBe 2
      row0.cell(0) shouldBe rows(0).cell(0)
      row0.cell(1) shouldBe rows(0).cell(1)
      val row1 = records(1).value.get
      row1.size shouldBe 2
      row1.cell(0) shouldBe rows(1).cell(0)
      row1.cell(1) shouldBe rows(1).cell(1)

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testUnmatchedSchema(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      // the name can't be casted to int
      .schema(Seq(Column("name", DataType.INT, 1)))
      .config(props.toMap)
      .create()
    try {
      TestFtpUtil.checkConnector(testUtil, connectorName)
      checkFileCount(0, 0, 1)

      val records = pollData(topicName, 5 second)
      records.size shouldBe 0

    } finally testUtil.connectorClient.delete(connectorName)
  }

  @Test
  def testInvalidInput(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(schema)
      .config(props.copy(input = "/abc").toMap)
      .create()
    TestFtpUtil.assertFailedConnector(testUtil, connectorName)
  }

  @Test
  def testInvalidOutput(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(schema)
      .config(props.copy(output = "/abc").toMap)
      .create()
    TestFtpUtil.assertFailedConnector(testUtil, connectorName)
  }

  @Test
  def testInvalidError(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(schema)
      .config(props.copy(error = "/abc").toMap)
      .create()
    TestFtpUtil.assertFailedConnector(testUtil, connectorName)
  }

  @Test
  def testInvalidSchema(): Unit = {
    val topicName = methodName
    val connectorName = methodName
    testUtil.connectorClient
      .connectorCreator()
      .topic(topicName)
      .connectorClass(classOf[FtpSource])
      .numberOfTasks(1)
      .disableConverter()
      .name(connectorName)
      .schema(
        Seq(
          // 0 is invalid
          Column("name", DataType.STRING, 0),
          Column("ranking", DataType.INT, 2),
          Column("single", DataType.BOOLEAN, 3)
        ))
      .config(props.toMap)
      .create()
    TestFtpUtil.assertFailedConnector(testUtil, connectorName)
  }

  @After
  def tearDown(): Unit = {
    CloseOnce.close(ftpClient)
    CloseOnce.close(consumer)
    consumer = null
  }
}
