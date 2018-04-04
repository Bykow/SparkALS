/*
 * Copyright 2015 and onwards Sanford Ryza, Uri Laserson, Sean Owen and Joshua Wills
 *
 * See LICENSE file for further information.
 */
package hello

import java.util.Properties

import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import edu.stanford.nlp.ling.CoreAnnotations.{LemmaAnnotation, SentencesAnnotation, TokensAnnotation}
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import org.apache.spark.sql.functions.size
import org.apache.spark.ml.feature.{CountVectorizer, IDF}
import org.apache.spark.rdd.RDD

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class CreateDocumentTermMatrix(private val spark: SparkSession) extends Serializable {
  import spark.implicits._

  def parseArticles(filenames : Seq[String]) : Dataset[(String,String)] = {
    val nameRDD : RDD[String] = spark.sparkContext.parallelize(filenames)
    spark.createDataset(nameRDD.flatMap(filename => getLinesfromFilename(filename).map(article => AIMData.parse(article))))
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
    val stopWords = Source.fromFile(AIMData.filePath(stopWordsFile)).getLines().toSet
    val bStopWords = spark.sparkContext.broadcast(stopWords)

    articles.mapPartitions { iter =>
      val pipeline = createNLPPipeline()
      iter.map { case (title, contents) => (title, plainTextToLemmas(contents, bStopWords.value, pipeline)) }
    }
  }


  /**
    * Returns a document-term matrix where each element is the TF-IDF of the row's document and
    * the column's term.
    *
    * @param docTexts a DF with two columns: title and text
    */
  def documentTermMatrix(docTexts: Dataset[(String, String)], stopWordsFile: String, numTerms: Int)
  : (DataFrame, Array[String], Map[Long, String], Array[Double]) = {
    val terms = contentsToTerms(docTexts, stopWordsFile)

    val termsDF = terms.toDF("title", "terms")
    val filtered = termsDF.where(size($"terms") > 1)

    val countVectorizer = new CountVectorizer()
      .setInputCol("terms").setOutputCol("termFreqs").setVocabSize(numTerms)
    val vocabModel = countVectorizer.fit(filtered)
    val docTermFreqs = vocabModel.transform(filtered)

    val termIds = vocabModel.vocabulary

    docTermFreqs.cache()

    val docIds = docTermFreqs.rdd.map(_.getString(0)).zipWithUniqueId().map(_.swap).collect().toMap

    val idf = new IDF().setInputCol("termFreqs").setOutputCol("tfidfVec")
    val idfModel = idf.fit(docTermFreqs)
    val docTermMatrix = idfModel.transform(docTermFreqs).select("title", "tfidfVec")

    (docTermMatrix, termIds, docIds, idfModel.idf.toArray)
  }

}
