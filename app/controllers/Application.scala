package controllers

import play.api._
import play.api.mvc._
import fr.janalyse.primes.PrimesGenerator
import models.PrimesEngine
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import concurrent._
import play.api.libs.iteratee.Enumerator


object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Prime web application is ready."))
  }

  def check(num: Long) = Action.async {
    val fresult = PrimesEngine.check(num)
    val fcontent = fresult map {
      case Some(chk) if chk.isPrime => s"$num is a prime, position ${chk.nth}th"
      case Some(chk) => s"$num is not a prime, position ${chk.nth}th"
      case None => s"Don't know if $num is prime, not in the database"
    }
    fcontent.map(Ok(_))
  }
  
  def primesTo(to:Long) = Action {
	  val content = PrimesEngine.listPrimes(to=to).map(x => s"${x.value}\n")
	  Ok.chunked(content)
  }
  
  def primesFromTo(from:Long, to:Long) = Action {
	  val content = PrimesEngine.listPrimes(from=from, to=to).map(x => s"${x.value}\n")
	  Ok.chunked(content)
  }

  def populate(to:Long) = Action.async {
    val fresult = PrimesEngine.populatePrimesIfRequired(to)
    fresult.map(r => Ok(r.toString))
  }
  
}