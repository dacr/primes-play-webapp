package controllers

import play.api._
import play.api.mvc._
import fr.janalyse.primes.PrimesGenerator
import play.api.libs.iteratee.Enumerator
import models.PrimesEngine
import scala.concurrent.Await
import scala.concurrent.duration._

object Application extends Controller {

  val primesGenerator = new PrimesGenerator[Long]()

  def index = Action {
    Ok(views.html.index("Prime web application is ready."))
  }

  def prime(num: Long) = Action {
    val fresult = PrimesEngine.check(num)
    
    val result = Await.result(fresult, 20.seconds)
    
    val message = result match {
      case Some(chk) if chk.isPrime => s"$num is a prime, number ${chk.nth}th"
      case Some(chk) => s"$num is not a prime, number ${chk.nth}th"
      case None => s"Don't know if $num is prime, not in the database"
    }
    
    SimpleResult(
      header = ResponseHeader(200, Map(CONTENT_TYPE -> "text/plain")),
      body = Enumerator(message.getBytes()))
  }

}