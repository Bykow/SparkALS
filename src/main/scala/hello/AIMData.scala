package hello


import java.io.File

import org.apache.spark.rdd.RDD

object AIMData {

  def filePath(filename : String) = {
    val resource = this.getClass.getClassLoader.getResource(filename)
    if (resource == null) sys.error("Error in loading of resources")
    new File(resource.toURI).getPath
  }

  def filesname(filePrefix: String, fileLowerIndex: Int, fileHigherIndex: Int): Seq[String] = {
   Seq.tabulate(fileHigherIndex-fileLowerIndex){i => filePrefix + i + ".txt" }
  }

  def parse(line: String): (String, String) = {
    val separator = "\"abstract\": \""
    val i = line.indexOf(separator)
    val j = line.indexOf("\"", i + separator.length)
    val id = line.substring(8, 32)
    val abstr  = line.substring(i + separator.length, j)
    (id, abstr)
  }
}
