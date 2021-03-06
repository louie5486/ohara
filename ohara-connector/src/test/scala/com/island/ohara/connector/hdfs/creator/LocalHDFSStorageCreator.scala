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

package com.island.ohara.connector.hdfs.creator
import com.island.ohara.common.util.Releasable
import com.island.ohara.connector.hdfs.HDFSSinkConnectorConfig
import com.island.ohara.connector.hdfs.storage.{HDFSStorage, Storage}
import com.island.ohara.testing.service.Hdfs
import org.apache.hadoop.fs.FileSystem

class LocalHDFSStorageCreator(config: HDFSSinkConnectorConfig) extends StorageCreator {
  // TODO: we should not use Hdfs.local directly ... by chia
  private[this] val fileSystem: FileSystem = Hdfs.local().fileSystem()
  private[this] val hdfsStorage: HDFSStorage = new HDFSStorage(fileSystem)

  override def storage(): Storage = {
    hdfsStorage
  }

  override def close(): Unit = {
    Releasable.close(fileSystem)
  }
}
