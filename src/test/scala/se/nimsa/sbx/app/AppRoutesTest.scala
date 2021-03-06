package se.nimsa.sbx.app

import scala.concurrent.Future

import spray.http.BasicHttpCredentials
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._
import spray.routing._

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import se.nimsa.sbx.app.UserProtocol.AuthToken

class AppRoutesTest extends FlatSpec with Matchers with RoutesTestBase {

  def dbUrl() = "jdbc:h2:mem:approutestest;DB_CLOSE_DELAY=-1"

  def testRoute =
    path("castexception") {
      onSuccess(Future.failed[String](new IllegalArgumentException("Oups"))) { extraction =>
        complete(extraction)
      }
    }

  val invalidCredentials = BasicHttpCredentials("john", "password")
  "The system" should "respond with BadRequest when a route throws an IllegalArgumentException" in {
    Get("/castexception") ~> testRoute ~> check {
    	status should be (BadRequest)
      responseAs[String] should be ("Oups")
    }    
  }
  
  it should "respond with Unautorized when requesting an arbitrary URL under /api without any credentials" in {
    Get("/api/some/url") ~> sealRoute(routes) ~> check {
      status should be (Unauthorized)
    }   
  }

  it should "respond with Unautorized when requesting an arbitrary URL under /api with an invalid auth token" in {
    Get("/api/some/url?authtoken=notvalid") ~> sealRoute(routes) ~> check {
      status should be (Unauthorized)
    }   
  }

  it should "respond with Unautorized when requesting an arbitrary URL under /api with bad credentials" in {
    Get("/api/some/url") ~> addCredentials(invalidCredentials) ~> sealRoute(routes) ~> check {
      status should be (Unauthorized)
    }   
  }
  
  it should "respond with OK when using a valid auth token and no credentials" in {
    val tokens = Post("/api/users/generateauthtokens?n=1") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (Created)
      responseAs[List[AuthToken]]
    }
    
    Get(s"/api/metadata/patients?authtoken=${tokens(0).token}") ~> routes ~> check {
      status should be (OK)
    }
  }
  
  it should "respond with OK when using no auth token and valid credentials" in {
    Get(s"/api/metadata/patients") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (OK)
    }
  }
  
  it should "respond with OK when using a valid auth token and valid credentials" in {
    val tokens = Post("/api/users/generateauthtokens?n=1") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (Created)
      responseAs[List[AuthToken]]
    }
    
    Get(s"/api/metadata/patients?authtoken=${tokens(0).token}") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (OK)
    }
  }
  
  it should "respond with OK when using a valid auth token and invalid credentials" in {
    val tokens = Post("/api/users/generateauthtokens?n=1") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (Created)
      responseAs[List[AuthToken]]
    }
    
    Get(s"/api/metadata/patients?authtoken=${tokens(0).token}")  ~> addCredentials(invalidCredentials) ~> routes ~> check {
      status should be (OK)
    }
  }
  
  it should "respond with Unauthorized when using an invalid auth token and valid credentials" in {
    Get("/api/metadata/patients?authtoken=hej")  ~> addCredentials(userCredentials) ~> sealRoute(routes) ~> check {
      status should be (Unauthorized)
    }
  }
  
  it should "respond with NotFound when requesting a non-existant asset" in {
    Get("/assets/someasset") ~> sealRoute(routes) ~> check {
      status should be (NotFound)
    }
  }
 
  it should "respond with Forbidden when requesting an admin resource as a user" in {
    DeleteAsUser("/api/users/1") ~> sealRoute(routes) ~> check {
      status should be (Forbidden)
    }
  }
  
}