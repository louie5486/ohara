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

package com.island.ohara.connector.ftp
import java.io.{BufferedWriter, OutputStreamWriter}
import java.util

import com.island.ohara.client.ftp.FtpClient
import com.island.ohara.common.data.Column
import com.island.ohara.common.util.Releasable
import com.island.ohara.connector.ftp.FtpSinkTask._
import com.island.ohara.kafka.connector._
import com.typesafe.scalalogging.Logger

import scala.collection.JavaConverters._

class FtpSinkTask extends RowSinkTask {
  private[this] var settings: TaskSetting = _
  private[this] var props: FtpSinkTaskProps = _
  private[this] var ftpClient: FtpClient = _
  private[this] var schema: Seq[Column] = _

  override protected def _start(settings: TaskSetting): Unit = {
    this.settings = settings
    this.props = FtpSinkTaskProps(settings)
    this.schema = settings.columns.asScala
    this.ftpClient =
      FtpClient.builder().hostname(props.hostname).port(props.port).user(props.user).password(props.password).build()
  }

  override protected def _stop(): Unit = {
    Releasable.close(ftpClient)
  }

  override protected def _put(records: util.List[RowSinkRecord]): Unit = try {
    val result = records.asScala
    // process only matched column name
      .filter(record => schema.map(_.name).forall(name => record.row.cells().asScala.exists(_.name == name)))
      // to line
      .map(record => {
        (record,
         record.row
           .cells()
           .asScala
           // pass if there is no columns
           .filter(c => schema.isEmpty || schema.exists(_.name == c.name))
           //
           .zipWithIndex
           .map {
             case (c, index) =>
               (if (schema.isEmpty) index else schema.find(_.name == c.name).get.order, c.value)
           }
           .sortBy(_._1)
           .map(_._2.toString)
           .mkString(","))
      })
      // NOTED: we don't want to write an "empty" line
      .filter(_._2.nonEmpty)
    if (result.nonEmpty) {
      val needHeader = props.needHeader && !ftpClient.exist(props.outputFolder)
      val writer = new BufferedWriter(
        new OutputStreamWriter(if (ftpClient.exist(props.outputFolder)) ftpClient.append(props.outputFolder)
                               else ftpClient.create(props.outputFolder),
                               props.encode))
      if (needHeader) {
        val header =
          if (schema.nonEmpty) schema.sortBy(_.order).map(_.newName).mkString(",")
          else result.head._1.row.cells().asScala.map(_.name).mkString(",")
        writer.append(header)
        writer.newLine()
      }
      try result.foreach {
        case (r, line) =>
          writer.append(line)
          writer.newLine()
      } finally writer.close()
    }
  } catch {
    case e: Throwable => LOG.error("failed to parse records", e)
  }
}

object FtpSinkTask {
  val LOG: Logger = Logger(classOf[FtpSinkTask])
}
