package com.island.ohara.hdfs.storage

import java.io.{InputStream, OutputStream}

import com.island.ohara.integration.{LocalHDFS, OharaTestUtil}
import com.island.ohara.rule.LargeTest
import org.apache.hadoop.fs.{FileSystem, Path}
import com.island.ohara.io.CloseOnce._
import org.apache.hadoop.hdfs.server.common.Storage
import org.junit.Test
import org.scalatest.Matchers

class TestHDFSStorage extends LargeTest with Matchers {

  @Test
  def testHdfsStorage(): Unit = {
    val localHDFS: LocalHDFS = OharaTestUtil.localHDFS(1)

    val fileSystem: FileSystem = localHDFS.fileSystem()
    val hdfsTempDir: String = localHDFS.tmpDirPath
    val hdfsStorage: Storage = new HDFSStorage(fileSystem)
    hdfsStorage.list(hdfsTempDir).size shouldBe 0

    fileSystem.createNewFile(new Path(s"$hdfsTempDir/file.txt"))
    hdfsStorage.list(hdfsTempDir).size shouldBe 1

    fileSystem.mkdirs(new Path(s"$hdfsTempDir/1"))
    fileSystem.mkdirs(new Path(s"$hdfsTempDir/2"))
    hdfsStorage.list(hdfsTempDir).size shouldBe 3
  }

  @Test
  def testOpenFile(): Unit = {
    val localHDFS: LocalHDFS = OharaTestUtil.localHDFS(1)

    val fileSystem: FileSystem = localHDFS.fileSystem()
    val hdfsTempDir: String = localHDFS.tmpDirPath()
    val fileName: String = s"$hdfsTempDir/file.txt"
    val hdfsStorage: Storage = new HDFSStorage(fileSystem)
    val text: String = "helloworld"

    fileSystem.createNewFile(new Path(fileName))
    val outputStream: OutputStream = hdfsStorage.open(fileName, true)
    outputStream.write(text.getBytes)
    outputStream.close()

    val inputStream: InputStream = fileSystem.open(new Path(fileName))
    var buffer: Array[Byte] = Array[Byte]()
    var result: StringBuilder = new StringBuilder()
    Stream
      .continually(inputStream.read())
      .takeWhile(_ != -1)
      .foreach(x => {
        result.append(x.toChar)
      })
    inputStream.close()
    result.toString() shouldBe text
  }

  @Test
  def testRename(): Unit = {
    val localHDFS: LocalHDFS = OharaTestUtil.localHDFS(1)

    val fileSystem: FileSystem = localHDFS.fileSystem()
    val hdfsTempDir: String = localHDFS.tmpDirPath()
    val folderName: String = s"$hdfsTempDir/folder1"
    val newFolderName: String = s"$hdfsTempDir/folder2"

    fileSystem.create(new Path(folderName))

    val hdfsStorage: Storage = new HDFSStorage(fileSystem)
    hdfsStorage.exists(folderName) shouldBe true
    hdfsStorage.renameFile(folderName, newFolderName) shouldBe true
    hdfsStorage.exists(folderName) shouldBe false
    hdfsStorage.exists(newFolderName) shouldBe true
  }

  @Test
  def testNewInstance(): Unit = {
    val localHDFS: FileSystem = OharaTestUtil.localHDFS(1).fileSystem()
    val hdfsStorage: Storage = Class
      .forName("com.island.ohara.hdfs.storage.HDFSStorage")
      .getConstructor(classOf[FileSystem])
      .newInstance(localHDFS)
      .asInstanceOf[Storage]

    hdfsStorage.mkdirs("test")

  }
}