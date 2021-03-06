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

import java.io.File

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import com.island.ohara.client.configurator.v0.JarApi._
import com.island.ohara.configurator.jar.JarStore
import spray.json.DefaultJsonProtocol._

import scala.concurrent.ExecutionContext
private[configurator] object JarRoute {

  def tempDestination(fileInfo: FileInfo): File =
    File.createTempFile(fileInfo.fileName, ".tmp")

  def apply(implicit jarStore: JarStore, executionContext: ExecutionContext): server.Route =
    pathPrefix(JAR_PREFIX_PATH) {
      storeUploadedFile("jar", tempDestination) {
        case (metadata, file) =>
          complete(jarStore.add(file, metadata.fileName))
      } ~ path(Segment) { id =>
        get(complete(jarStore.jarInfo(id))) ~ delete(complete(jarStore.remove(id).map(_ => StatusCodes.NoContent)))
      } ~ pathEnd {
        get(complete(jarStore.jarInfos()))
      }
    }
}
