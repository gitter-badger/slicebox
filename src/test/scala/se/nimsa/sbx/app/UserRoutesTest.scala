package se.nimsa.sbx.app

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import spray.httpx.SprayJsonSupport._
import UserProtocol._
import UserProtocol.UserRole._

import spray.http.StatusCodes._

class UserRoutesTest extends FlatSpec with Matchers with RoutesTestBase {

  def dbUrl() = "jdbc:h2:mem:userroutestest;DB_CLOSE_DELAY=-1"
  
  val user = ClearTextUser("name", ADMINISTRATOR, "password")
  var responseUser: ApiUser = null
  
  "The system" should "return the new user when a new user is added" in {
    PostAsAdmin("/api/users", user) ~> routes ~> check {
      responseUser = responseAs[ApiUser]
      responseUser.user should be(user.user)
    }
  }

  it should "return the new user when adding an already added user (idempotence)" in {
    PostAsAdmin("/api/users", user) ~> routes ~> check {
      val responseUser = responseAs[ApiUser]
      responseUser.user should be(user.user)
    }    
  }
  
  it should "return status NoContent when a user is deleted" in {
    DeleteAsAdmin("/api/users/" + responseUser.id) ~> routes ~> check {
      status should be (NoContent)
    }
  }

  it should "return status NoContent when trying to delete an user that does not exist" in {
    DeleteAsAdmin("/api/users/999") ~> routes ~> check {
      status should be (NoContent)
    }
    
  }
  
  it should "return a list of users when asking for all users" in {
    val user2 = ClearTextUser("name2", ADMINISTRATOR, "password2")
    PostAsAdmin("/api/users", user2) ~> routes
    GetAsUser("/api/users") ~> routes ~> check {
      val returnedUsers = responseAs[List[ApiUser]]
      returnedUsers.length should be(2)
    }
  }
  
  it should "not be possible to generate tokens using token authentication" in {
    val tokens = Post("/api/users/generateauthtokens?n=1") ~> addCredentials(userCredentials) ~> routes ~> check {
      status should be (Created)
      responseAs[List[AuthToken]]
    }
    
    tokens.size should be (1)
    val token = tokens(0).token
    
    Post(s"/api/users/generateauthtokens?authtoken=$token&n=1") ~> sealRoute(routes) ~> check {
      status should be (Unauthorized)
    }    
  }
  
}