package com.dslplatform.api.client

import scala.reflect._
import scala.concurrent.Future
import com.dslplatform.api.patterns.Searchable
import com.dslplatform.api.patterns.Specification
import com.dslplatform.api.patterns.History
import com.dslplatform.api.patterns.Report
import com.dslplatform.api.patterns.Identifiable
import com.dslplatform.api.patterns.AggregateRoot
import com.dslplatform.api.patterns.Cube

class HttpReportingProxy(httpClient: HttpClient) extends ReportingProxy {
  import HttpClientUtil._

  private val REPORTING_URI = "Reporting.svc"

  def populate[TResult: ClassTag](report: Report[TResult]): Future[TResult] = {
    val domainName: String = httpClient.getDslName(report.getClass)
    httpClient.sendRequest[TResult](
      PUT(report),
      REPORTING_URI / "report" / domainName,
      Set(200))
  }

  def createReport[TResult](
    report: Report[TResult],
    templater: String): Future[Array[Byte]] =
    httpClient.sendRequest[Array[Byte]](
      PUT(report),
      REPORTING_URI / "report" / httpClient.getDslName(report.getClass) / templater,
      Set(201))

  def olapCube[TCube <: Cube[TSearchable]: ClassTag, TSearchable <: Searchable: ClassTag](
    templater: String,
    specification: Option[Specification[TSearchable]],
    dimensions: TraversableOnce[String],
    facts: TraversableOnce[String],
    limit: Option[Int],
    offset: Option[Int],
    order: Map[String, Boolean]): Future[Array[Byte]] = {
    val cubeName = httpClient.getDslName[TCube]
    val parentName: String = httpClient.getDslName[TSearchable]
    val args: String = Utils.buildOlapArguments(dimensions, facts, limit, offset, order)
    specification match {
      case Some(spec) =>
        val specClass = spec.getClass
        val specName: String = if (parentName == cubeName) parentName + "/" else ""
        httpClient.sendRequest[Array[Byte]](
          PUT(specification),
          REPORTING_URI / "olap" / cubeName / specName + specClass.getSimpleName().replace("$", "") / templater + args,
          Set(200))
      case _ =>
        httpClient.sendRequest[Array[Byte]](
          GET, REPORTING_URI / "olap" / cubeName / templater + args, Set(200))
    }
  }

  def getHistory[TAggregate <: AggregateRoot: ClassTag](
    uris: TraversableOnce[String]): Future[IndexedSeq[History[TAggregate]]] = {
    val domainName: String = httpClient.getDslName
    httpClient.sendRequest[IndexedSeq[History[TAggregate]]](
      PUT(uris.toArray),
      REPORTING_URI / "history" / httpClient.getDslName,
      Set(200))
  }

  def findTemplater[TIdentifiable <: Identifiable: ClassTag](
    file: String,
    uri: String,
    toPdf: Boolean): Future[Array[Byte]] = {
    if (file == null) throw new IllegalArgumentException("file not specified")
    if (uri == null) throw new IllegalArgumentException("uri not specified")
    val domainName = httpClient.getDslName
    httpClient.sendRawRequest(
      GET,
      REPORTING_URI / "templater" / file / domainName + "?uri=" + encode(uri),
      Set(200),
      prepareHeaders(toPdf))
  }

  private def prepareHeaders(toPdf: Boolean) =
    Map("Accept" -> Set((if (toPdf) "application/pdf" else "application/octet-stream")))

  def searchTemplater[TSearchable <: Searchable: ClassTag](
    file: String,
    specification: Option[Specification[TSearchable]],
    toPdf: Boolean): Future[Array[Byte]] = {
    if (file == null || file.isEmpty) throw new IllegalArgumentException("file not specified")
    val domainName: String = httpClient.getDslName
    specification match {
      case Some(spec) =>
        val specClass: Class[_] = spec.getClass()
        httpClient.sendRawRequest(
          PUT(specification),
          REPORTING_URI / "templater" / file / domainName / specClass.getSimpleName().replace("$", ""),
          Set(200),
          prepareHeaders(toPdf))
      case _ =>
        httpClient.sendRawRequest(
          GET,
          REPORTING_URI / "templater" / file / domainName,
          Set(200),
          prepareHeaders(toPdf))
    }
  }
}
