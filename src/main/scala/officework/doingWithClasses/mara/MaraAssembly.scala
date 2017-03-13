package main.scala.officework.doingWithClasses.mara


import java.text.SimpleDateFormat

import milliman.mara.exception.{MARAClassLoaderException, MARALicenseException}
import milliman.mara.model.{InputEnums, ModelProcessor, ModelProperties}
import officework.doingWithClasses.mara.{MaraUDAF, MaraUtils}
import org.apache.spark.SparkFiles
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, lit, row_number, udf}


/**
  * Created by ramaharjan on 3/2/17.
  */
class MaraAssembly(eligDataFrame : DataFrame, medDataFrame : DataFrame, rxDataFrame : DataFrame) {

  var eligDF = eligDataFrame
  var medDF = medDataFrame
  var rxDF = rxDataFrame


  def setSortDate = udf((date: String) => date )

  def maraCalculator() : Unit = {

    //todo groupfilter of elig
    eligDF = eligDF.select(MaraUtils.eligRetain.map(col): _*)
    for (column <- 0 until MaraUtils.insertToEligColumns.size) {
      eligDF = eligDF.withColumn(MaraUtils.insertToEligColumns(column), lit(MaraUtils.insertToEligValues(column)))
    }
    eligDF = eligDF.withColumn(MaraUtils.sortDateColumn, setSortDate(eligDF("ins_med_term_date")))
    eligDF = eligDF.select(MaraUtils.finalOrderingColumns.map(col):_*)

    val window = Window.partitionBy(col("dw_member_id")).orderBy(col("ins_med_term_date").desc, col("ins_med_eff_date").desc, col("mbr_relationship_code").desc, col("ins_emp_group_id").desc)
    val latestEligDF = eligDF.withColumn("rn", row_number.over(window)).where(col("rn") === 1).drop(col("rn")).withColumn(MaraUtils.inputTypeFlagColumn(0), lit(MaraUtils.inputTypeFlagEligLatest(0)))

    //todo groupfilter of medical
    medDF = medDF.select(MaraUtils.medRetain.map(col): _*)
    for (column <- 0 until MaraUtils.insertToMedColumns.size) {
      medDF = medDF.withColumn(MaraUtils.insertToMedColumns(column), lit(MaraUtils.insertToMedValues(column)))
    }
    medDF = medDF.withColumn(MaraUtils.sortDateColumn, setSortDate(medDF("svc_service_frm_date")))
    //todo convert "svc_service_frm_date", "svc_service_to_date", "rev_paid_date", "ins_med_eff_date", "ins_med_term_date" to date
    medDF = medDF.select(MaraUtils.finalOrderingColumns.map(col):_*)

    //todo groupfilter of pharmacy
    rxDF = rxDF.withColumnRenamed("svc_service_frm_date", "rx_svc_filled_date")
    rxDF = rxDF.select(MaraUtils.rxRetain.map(col): _*)
    for (column <- 0 until MaraUtils.insertToRxColumns.size) {
      rxDF = rxDF.withColumn(MaraUtils.insertToRxColumns(column), lit(MaraUtils.insertToRxValues(column)))
    }
    rxDF = rxDF.withColumn(MaraUtils.sortDateColumn, setSortDate(rxDF("rx_svc_filled_date")))
    //todo convert "rx_svc_filled_date","ins_med_eff_date", "ins_med_term_date" to date
    rxDF = rxDF.select(MaraUtils.finalOrderingColumns.map(col):_*)

    var combined = latestEligDF.union(eligDF).union(medDF).union(rxDF)
    val maraUdaf = new MaraUDAF(combined.schema)
    combined = combined.groupBy("dw_member_id").agg(maraUdaf(MaraUtils.finalOrderingColumns.map(col):_*)("prospectiveInpatient").as("prospectiveInpatient"), maraUdaf(MaraUtils.finalOrderingColumns.map(col):_*)("prospectivePharmacy").as("prospectivePharmacy"))
    combined.show(false)
//    val modelProcessor = prepareModelProcessor(DateUtils.convertStringToLong("2016-12-31"))

//    combined.map(row => {println(row)} )


  }


  def prepareModelProcessor(endOfCycleDate: Long): ModelProcessor = {
    System.out.println("License File Location : " + SparkFiles.get("mara.lic"))
    System.out.println("Mara Data Folder Location: " + SparkFiles.getRootDirectory())
    try {
      new ModelProcessor(setupModelProperties(endOfCycleDate))
    }
    catch {
      case e: MARAClassLoaderException => {
        e.printStackTrace()
        System.out.println("MaraClassLoader Exception occurred in setup")
        throw new RuntimeException(e)
      }
      case e: MARALicenseException => {
        System.out.println("MaraLicense exception occurred in setup")
        e.printStackTrace()
        throw new RuntimeException(e)
      }
    }
  }


  private def setupModelProperties(endOfCycleDate: Long): ModelProperties = {
    val modelProperties = new ModelProperties
    var maraFolder = SparkFiles.getRootDirectory()

    val modelList = new java.util.ArrayList[InputEnums.ModelName]
    modelList.add(InputEnums.ModelName.CXPROLAG0)
    modelList.add(InputEnums.ModelName.CXCONLAG0)
    val df_mmddyyyy = new SimpleDateFormat("MM/dd/yyyy")

    modelProperties.setLicenseFileLocation(SparkFiles.get("mara.lic"))
    modelProperties.setMaraDataFolderLocation(maraFolder)
    modelProperties.setOutputPercentContributions(true)
    modelProperties.setModelList(modelList)
    modelProperties.setEndBasePeriodDate(df_mmddyyyy.format(endOfCycleDate))

    modelProperties
  }
}