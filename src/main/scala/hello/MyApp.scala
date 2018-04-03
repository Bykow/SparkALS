package hello

import org.apache.spark.sql.SparkSession

object MyApp {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("Spark ALS")
      .master("local")
      .getOrCreate()


    val stopwords = "stopwords.txt"

    val filenames = AIMData.filesname("subset10_", 0, 2)

    print(spark.sparkContext.textFile(AIMData.filePath("subset10_0.txt")).map(u => AIMData.parse(u)).collect().toList)

    val doc = new CreateDocumentTermMatrix(spark)
    val articles = doc.parseArticles(filenames)

    articles.map(u => println(u)).collect()
  }


}