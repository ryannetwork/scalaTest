package main.scala.officework.doingWithClasses

import cascading.tuple.{Tuple, Tuples}
import main.scala.officework.ScalaUtils
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat
import org.apache.spark.sql.SparkSession

import scala.collection.JavaConverters._

/**
  * Created by ramaharjan on 2/1/17.
  */
object SparkEntry {

  def main(args: Array[String]) {

    //    val clientId = args(0)+"/"
    val eligJobConfig = new JobCfgParameters("/validation_eligibility.jobcfg")
    val clientConfig = new ClientCfgParameters("/client_config.properties")

    //spark configurations
    val sparkSession = SparkSession.builder().appName("Simple Application")
      .master("local")
      .config("", "")
      .getOrCreate()

    //    val conf = new SparkConf().setAppName("Simple Application").setMaster("local")
    val sc = sparkSession.sparkContext
    val sqlContext = sparkSession.sqlContext

    //schema generation for the input source
    val generateSchemas = new GenerateSchemas
    val eligSchema = generateSchemas.dynamicSchema(eligJobConfig.getInputLayoutFilePath)

    //defining line delimiter for source files
    sc.hadoopConfiguration.set("textinputformat.record.delimiter", "^*~")
    val sourceDataRdd = sc.textFile(eligJobConfig.getSourceFilePath)

    //data frame generation for input source
    val generateDataFrame = new GenerateDataFrame
    val eligibilityTable = generateDataFrame.eligDataFrame(sqlContext, sourceDataRdd, eligSchema)

    val eligibilityGoldenRules = new EligibilityGoldenRules(clientConfig.getEOC, clientConfig.getClientType)
    val eligibilityGoldenRulesApplied = eligibilityGoldenRules.applyEligibilityGoldenRules(eligibilityTable)
    eligibilityGoldenRulesApplied.show()
    //applying golden rules

    //deleting the outputs if they exists
    ScalaUtils.deleteResource(eligJobConfig.getSinkFilePath)
    ScalaUtils.deleteResource(eligJobConfig.getIntMemberId)

    //eligibility validation output
    val eligRDD = eligibilityGoldenRulesApplied.rdd.map(row => row.toString())
    val eligibilityOutput = eligRDD
      .map(row => row.toString().split(",").toList.asJava)
      .map(v => (Tuple.NULL, Tuples.create(v.asInstanceOf[java.util.List[AnyRef]])))
      .saveAsNewAPIHadoopFile(eligJobConfig.getSinkFilePath, classOf[Tuple], classOf[Tuple], classOf[SequenceFileOutputFormat[Tuple, Tuple]], ScalaUtils.getHadoopConf)

    //integer member id output
    val memberIdRDD = eligibilityTable.select("dw_member_id").rdd
    val intRDD = memberIdRDD
      .distinct()
      .zipWithUniqueId()
      .map(kv => (Tuple.NULL, new Tuple(kv._1(0).toString, kv._2.toString)))
      .saveAsNewAPIHadoopFile(eligJobConfig.getIntMemberId, classOf[Tuple], classOf[Tuple], classOf[SequenceFileOutputFormat[Tuple, Tuple]], ScalaUtils.getHadoopConf)

    //stopping sparkContext
    sc.stop()
  }
}