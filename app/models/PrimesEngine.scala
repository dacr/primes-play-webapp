package models

import reactivemongo.api._
import reactivemongo.bson.{ BSONDocument => BD }
import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.bson._
import reactivemongo.core.commands._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import BSONDocument._
import scala.annotation.tailrec
import fr.janalyse.primes.PrimesGenerator
import fr.janalyse.primes.CheckedValue

object PrimesEngine {
  lazy val driver: MongoDriver = new MongoDriver()
  lazy val use: MongoConnection =
    driver.connection(List("localhost:27017"), nbChannelsPerNode = 20)
  lazy val db = use("primes")

  val pgen = new PrimesGenerator[Long]
    
  implicit object CheckedValueHandler
    extends BSONDocumentReader[CheckedValue[Long]]
    with BSONDocumentWriter[CheckedValue[Long]] {

    def read(doc: BSONDocument) = {
      CheckedValue(
        value = doc.getAs[Long]("value").get,
        isPrime = doc.getAs[Boolean]("isPrime").get,
        digitCount = doc.getAs[Long]("digitCount").get,
        nth = doc.getAs[Long]("nth").get)
    }
    def write(cv: CheckedValue[Long]) = {
      BSONDocument(
        "value" -> cv.value,
        "isPrime" -> cv.isPrime,
        "digitCount" -> cv.digitCount,
        "nth" -> cv.nth)
    }
  }

  private def populatePrimesIfRequired(upTo: Long = 100000) = {
    val values = db("values")
    val fall = for {
      foundLastPrime <- lastPrime
      foundLastNotPrime <- lastNotPrime
    } yield {
      var resumedStream = pgen.checkedValues(foundLastPrime, foundLastNotPrime)
      while (resumedStream.head.value <= upTo) {
        val f1 = values.insert(resumedStream.head)
        Await.ready(f1, 60.seconds) // We want to stay in synch
        resumedStream = resumedStream.tail
      }
      'done
    }
    fall.onFailure {
      case x => println(s"NOK - ${x.getMessage()} - add indexes on values collection")
    }
    fall
  }

  var worker:Option[Future[Symbol]]=None
  def populate(upTo:Long) = this.synchronized {
    if (worker.isEmpty || worker.get.isCompleted) {
      val populateFuture = populatePrimesIfRequired(upTo)
      worker = Some(populateFuture)
      concurrent.future {'JobStarted}
    } else concurrent.future {'StillInProgress}
  }
  
  def valuesCount(): Future[Int] = db.command(Count("values"))
  
  def primesCount(): Future[Int] = db.command(Count("values", Some(BD("isPrime"->true))))

  def notPrimesCount(): Future[Int] = db.command(Count("values", Some(BD("isPrime"->false))))

  
  def lastPrime():Future[Option[CheckedValue[Long]]] = {
    val values = db("values")
    values.find(BD("isPrime"->true)).sort(BD("nth"-> -1)).one[CheckedValue[Long]]
  }

  def lastNotPrime():Future[Option[CheckedValue[Long]]] = {
    val values = db("values")
    values.find(BD("isPrime"->false)).sort(BD("nth"-> -1)).one[CheckedValue[Long]]
  }
  
  
  def check(num: Long): Future[Option[CheckedValue[Long]]] = {
    val values = db("values")
    values.find(BD("value" -> num)).one[CheckedValue[Long]]
  }

  def getPrime(nth:Long) = {
    val values = db("values")
    val request = BD("isPrime" -> true,"nth" -> nth)
    values.find(request).one[CheckedValue[Long]]
  }
  
  def listPrimes(from: Long = 0l, to: Long = Long.MaxValue) = {
    val values = db("values")
    val request =
      BD("isPrime" -> true,
        "value" -> BD("$gte" -> from, "$lte" -> to))

    values.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].enumerate()
  }
  
  def ulam(sz:Int) = {
    val values = db("values")
    val request =BD("value"-> BD("$lte" -> sz.toLong*sz))
    val it = values.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].collect[Iterator]()
    it.map{ lst =>
      pgen.ulamSpiral(sz, lst)
    }
  }

  
  def factorize(num:Long) = {
    val values = db("values")
    val request =BD("isPrime"->true, "value"-> BD("$lte" -> num))
    val it = values.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].collect[List]()
    it.map { lst =>
      pgen.factorize(num,lst.map(_.value).iterator)
    }
  }
}