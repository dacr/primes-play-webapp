package controllers

import play.api._
import play.api.mvc._
import fr.janalyse.primes.PrimesGenerator
import models.PrimesEngine
import play.api.libs.concurrent.Execution.Implicits.defaultContext


object Application extends Controller {

  val primesGenerator = new PrimesGenerator[Long]()

  def index = Action {
    Ok(views.html.index("Prime web application is ready."))
  }

  def prime(num: Long) = Action.async {
    val fresult = PrimesEngine.check(num)
    val fcontent = fresult map {
      case Some(chk) if chk.isPrime => s"$num is a prime, number ${chk.nth}th"
      case Some(chk) => s"$num is not a prime, number ${chk.nth}th"
      case None => s"Don't know if $num is prime, not in the database"
    }
    fcontent.map(Ok(_))
  }

}