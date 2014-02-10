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
    driver.connection(List("localhost:27017"), nbChannelsPerNode = 200)

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

  def populatePrimesIfRequired(upTo: Long = 100000) = synchronized {

    import math._
    val db = use("primes")
    val primes = db("values")
    val lastPrime = db("lastPrime")
    val lastNotPrime = db("lastNotPrime")

    def insert(checked: CheckedValue[Long]) {
      val impacted = if (checked.isPrime) lastPrime else lastNotPrime
      val f1 = primes.insert(checked)
      val f2 = for {
        _ <- impacted.remove(BD())
        _ <- impacted.insert(checked)
      } yield 'done

      Await.ready(Future.sequence(List(f1, f2)), 5.seconds)
    }

    val fall = for {
      foundLastPrime <- lastPrime.find(BD()).cursor[CheckedValue[Long]].headOption
      foundLastNotPrime <- lastNotPrime.find(BD()).cursor[CheckedValue[Long]].headOption
    } yield {
      val foundLast = for {
        flp <- foundLastPrime
        flnp <- foundLastNotPrime
      } yield if (flp.value > flnp.value) flp else flnp

      val primeNth = foundLastPrime.map(_.nth).getOrElse(1L)
      val notPrimeNth = foundLastNotPrime.map(_.nth).getOrElse(0L)
      val resuming = foundLast.isDefined

      var resumedStream = pgen.checkedValues(foundLast.getOrElse(CheckedValue.first), primeNth, notPrimeNth) match {
        case s if resuming => s.tail
        case s => s
      }
      var howmany = upTo - resumedStream.head.value
      while (howmany > 0) {
        insert(resumedStream.head)
        resumedStream = resumedStream.tail
        howmany -= 1
      }

      'started
    }
    fall.onFailure {
      case x =>
        println(x.toString)
        println("NOK - try create an index on value field of primes collection")
    }

    fall
  }

  def check(num: Long): Future[Option[CheckedValue[Long]]] = {
    val db = use("primes")
    val primes = db("values")
    primes.find(BD("value" -> num)).one[CheckedValue[Long]]
  }

  def getPrime(nth:Long) = {
    val db = use("primes")
    val primes = db("values")
    val request = BD("isPrime" -> true,"nth" -> nth)
    primes.find(request).one[CheckedValue[Long]]
  }
  
  def listPrimes(from: Long = 0l, to: Long = Long.MaxValue) = {
    val db = use("primes")
    val primes = db("values")
    val request =
      BD("isPrime" -> true,
        "value" -> BD("$gte" -> from, "$lte" -> to))

    primes.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].enumerate()
  }
  
  def ulam(sz:Int) = {
    val db = use("primes")
    val primes = db("values")
    val request =BD("value"-> BD("$lte" -> sz.toLong*sz))
    val it = primes.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].collect[List]()
    it.map{ lst =>
      pgen.ulamSpiral(sz, lst.iterator)
    }
  }

  
  def factorize(num:Long) = {
    val db = use("primes")
    val primes = db("values")
    val request =BD("isPrime"->true, "value"-> BD("$lte" -> num))
    val it = primes.find(request).sort(BD("value" -> 1)).cursor[CheckedValue[Long]].collect[List]()
    it.map { lst =>
      pgen.factorize(num,lst.map(_.value).iterator)
    }
  }
}