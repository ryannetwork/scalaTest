package src.Tests

/**
  * Created by ramaharjan on 1/18/17.
  */
import java.io.File
import java.util.Scanner

import jdk.nashorn.api.scripting.JSObject
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.ScalaReflection.Schema
import org.apache.spark.sql.types._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.{Row, SQLContext, SparkSession}

import scala.io.Source

object Main {
  def main(args: Array[String]) {
    // val logFile = "file:///home/ramaharjan/Desktop/SparkWordCount.scala" // Should be some file on your system
    val sourceFile = "file:/home/ramaharjan/bugs/110054/elig.csv" // Should be some file on your system
    val layoutFile = "file:/home/ramaharjan/bugs/110054/eligibilityLayout.csv" // Should be some file on your system
    val conf = new SparkConf().setAppName("Simple Application").setMaster("local")
    val sc = new SparkContext(conf)

    dynamicSchema(sc, layoutFile)

    sc.stop()
  }

  def readData(sc : SparkContext, dataFile : String, layoutFile : String): Unit ={
    sc.hadoopConfiguration.set("textinputformat.record.delimiter","^*~")
    val inputLines = sc.textFile(dataFile)

    val schema = dynamicSchema(sc, layoutFile)
    val rowFields = inputLines.map{line => line.split("\\^%~")}.map{ array => Row.fromSeq(array)}
    val arrayList = sc.parallelize(Array(Array("1","This is a line for testing", "third1"), Array("2","The second line", "third2")))
    val rowRdd = arrayList.map{ array => Row.fromSeq(array.zip(schema.toSeq).map{ case (value, struct) => convertTypes(value, struct) })}
    //    val arrayFields = sc.parallelize(Array(rowFields.toLocalIterator))
    //    val rowRdd = arrayFields.map{ array => Row.fromSeq(Seq(array))}

    val sqlContext = new SQLContext(sc)
    val df = sqlContext.createDataFrame(rowFields, dynamicSchema(sc, layoutFile))
    df.show()
  }

  def dynamicSchema(sc : SparkContext, file : String): StructType ={
    val readData = sc.textFile(file).filter(!_.startsWith("#"))
    val schema = readData.map(x=>x.split(";", -1)).map {value => StructField(value(1), dataType(value(4)))}
    val structType = StructType(schema.collect().toSeq)
//    structType.foreach(println)
    println(structType.prettyJson)
    structType
  }

  def dataType(dataType : String) : DataType ={
    if(dataType.equalsIgnoreCase("int")){
      IntegerType
    }
    else if(dataType.equalsIgnoreCase("date")){
      DateType
    }
    else if(dataType.equalsIgnoreCase("float")){
      FloatType
    }
    else if(dataType.equalsIgnoreCase("double")){
      DoubleType
    }
    else{
      StringType
    }
  }



  def jsonParser(): Unit ={
    import scala.util.parsing.json._

    val parsed = JSON.parseFull("""{"Name":"abc", "age":10}""")
    println(parsed)
  }

}
