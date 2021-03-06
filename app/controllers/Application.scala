package controllers

import play.api._
import play.api.mvc._
import fr.janalyse.primes.PrimesGenerator
import models.PrimesEngine
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import concurrent._
import concurrent.duration._
import play.api.libs.iteratee.Enumerator
import play.libs.Akka
import scala.util.{Try,Success,Failure}

object Application extends Controller {

  def index = Action.async {
    for {
      valuesCount <- PrimesEngine.valuesCount()
      primesCount <- PrimesEngine.primesCount()
      lastPrime <- PrimesEngine.lastPrime()
    } yield Ok(views.html.index(
        "Prime web application is ready.",
        valuesCount,
        primesCount,
        lastPrime.map(_.value).getOrElse(0L),
        false))
  }

  
  private def checkContent(num:Long):Future[String] = {
    val fresult = PrimesEngine.check(num)
    fresult map {
      case Some(chk) if chk.isPrime => s"$num is the ${chk.nth}th prime"
      case Some(chk) => s"$num is the ${chk.nth}th not prime"
      case None => s"Don't know if $num is a prime, not in the database"
    }    
  }
  
  def check(num: Long) = Action.async {
    checkContent(num).map(msg=> Ok(views.html.check(msg, None)))
  }
  
  def rcheck = Action.async {
    val last=PrimesEngine.valuesCount
    val rnum=(math.random*20000d).toLong
    checkContent(rnum).map(msg => Ok(views.html.check(msg, Some(routes.Application.rcheck))))
  }

  def slowcheck(num: Long, secs:Long=10) = Action.async {
    val thepromise = Promise[String]()

    Akka.system.scheduler.scheduleOnce(secs.seconds) {
      thepromise success s"I'm slow, $secs seconds"
    }
    val fcontent = for {
      result <- PrimesEngine.check(num)
      _ <- thepromise.future
    } yield {
      result match {
      case Some(chk) if chk.isPrime => s"$num is a prime, position ${chk.nth}th"
      case Some(chk) => s"$num is not a prime, position ${chk.nth}th"
      case None => s"Don't know if $num is prime, not in the database"
      }
    }
    fcontent.map(Ok(_))
  }

  
  def prime(nth:Long) = Action.async {
    val fresult = PrimesEngine.getPrime(nth)
    val fcontent = fresult map {
      case Some(found) => s"prime#$nth is ${found.value}"
      case None => s"Not in the database, launch populate with the right limit"
    }
    fcontent.map(Ok(_))
  }
  
  def primesTo(to:Long) = Action {
	  val content = PrimesEngine.listPrimes(to=to).map(x => s"${x.value}\n")
	  Ok.chunked(content)
  }

  def primesFromTo(from: Long, to: Long) = Action {
    val content = PrimesEngine.listPrimes(from = from, to = to).map(x => s"${x.value}\n")
    Ok.chunked(content)
  }

  def populate(to: Long) = Action.async {
    val fresult = PrimesEngine.populate(to)
    fresult.map(r => Ok(r.toString))
  }

  def ulam(sz: Int) = Action.async {
    import javax.imageio.ImageIO
    import java.io._

    PrimesEngine.ulam(sz) map { bufferImage =>
      val out = new ByteArrayOutputStream()
      ImageIO.write(bufferImage, "PNG", out)
      Ok(out.toByteArray).as("image/png")
    }
  }

  def factors(num:Long) = Action.async {
    val fresult = PrimesEngine.factorize(num)
    val fcontent = fresult  map {
      case Some(found) => s"""$num = ${found.mkString("*")}"""
      case None => s"Not enough primes available in the database for $num"
    }
    fcontent.map(Ok(_))
  }
  
  
  def rfactors = Action.async {
    val fresult = 
      PrimesEngine
         .lastCheckedValue
         .map(_.map(_.value))
         .flatMap{
      case Some(last) =>
        val r = (math.random*last).toLong
        PrimesEngine.factorize(r).map(r -> _)
      case None => Future {0L->None}
      }
         
    val fcontent = fresult  map {
      case (num, Some(found)) => s"""$num = ${found.mkString("*")}"""
      case (num, None) => s"Not enough primes available in the database for $num"
    }
    fcontent.map(Ok(_))  
  }
}