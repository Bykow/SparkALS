package hello

import java.util.Properties

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}
import edu.stanford.nlp.ling.CoreAnnotations.{LemmaAnnotation, SentencesAnnotation, TokensAnnotation}
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class CreateDocumentTermMatrix(private val spark: SparkSession) extends Serializable {
  import spark.implicits._

  def parseArticles(filenames : Seq[String]) : RDD[(String,String)] = {
    val nameRDD = spark.sparkContext.parallelize(filenames)
    nameRDD.flatMap(filename => getLinesfromFilename(filename).map(article => AIMData.parse(article)))
  }

  def getLinesfromFilename(filename : String) : Iterator[String] = {
    Source.fromFile(AIMData.filePath(filename)).getLines()
  }

  /**
    * Create a StanfordCoreNLP pipeline object to lemmatize documents.
    */
  def createNLPPipeline(): StanfordCoreNLP = {
    val props = new Properties()
    props.put("annotators", "tokenize, ssplit, pos, lemma")
    new StanfordCoreNLP(props)
  }

  def isOnlyLetters(str: String): Boolean = {
    str.forall(c => Character.isLetter(c))
  }

  def plainTextToLemmas(text: String, stopWords: Set[String], pipeline: StanfordCoreNLP): Seq[String] = {
    val doc = new Annotation(text)
    pipeline.annotate(doc)
    val lemmas = new ArrayBuffer[String]()
    val sentences = doc.get(classOf[SentencesAnnotation])
    for (sentence <- sentences.asScala;
         token <- sentence.get(classOf[TokensAnnotation]).asScala) {
      val lemma = token.get(classOf[LemmaAnnotation])
      if (lemma.length > 2 && !stopWords.contains(lemma) && isOnlyLetters(lemma)) {
        lemmas += lemma.toLowerCase
      }
    }
    lemmas
  }

  def contentsToTerms(articles: Dataset[(String, String)], stopWordsFile: String): Dataset[(String, Seq[String])] = {
    val stopWords = scala.io.Source.fromFile(stopWordsFile).getLines().toSet
    val bStopWords = spark.sparkContext.broadcast(stopWords)

    articles.mapPartitions { iter =>
      val pipeline = createNLPPipeline()
      iter.map { case (title, contents) => (title, plainTextToLemmas(contents, bStopWords.value, pipeline)) }
    }
  }
}
