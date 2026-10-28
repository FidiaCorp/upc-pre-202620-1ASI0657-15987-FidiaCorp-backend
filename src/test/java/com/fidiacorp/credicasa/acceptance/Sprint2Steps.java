package com.fidiacorp.credicasa.acceptance;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.domain.service.TotpAlgorithm;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import io.cucumber.java.en.*;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@CucumberContextConfiguration
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop",
 "app.security.jwt.secret=acceptance-only-test-signing-key-at-least-thirty-two-bytes",
 "app.security.two-factor.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(Sprint2Steps.AcceptanceTime.class)
public class Sprint2Steps {
 @TestConfiguration public static class AcceptanceTime {
  @Bean @Primary Clock acceptanceClock(){return Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"),ZoneOffset.UTC);}
 }
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired AccountJpaRepository accounts;
 @Autowired TotpAlgorithm totp;
 @Autowired Clock clock;
 record User(String id,String email,String token){}
 private static final String PASSWORD="AcceptancePassword123!";
 private User client,other,advisor;
 private String propertyId,offerId,secondToken;
 private JsonNode schedule,notice;
 private JsonNode call(MockHttpServletRequestBuilder req,String token,Object data,int status)throws Exception{
  if(token!=null)req.header("Authorization","Bearer "+token);
  if(data!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
  MvcResult result=mvc.perform(req).andReturn();
  assertThat(result.getResponse().getStatus()).as("HTTP %s %s: %s",req,result.getRequest().getRequestURI(),result.getResponse().getContentAsString()).isEqualTo(status);
  String body=result.getResponse().getContentAsString();return body.isBlank()?json.nullNode():json.readTree(body);
 }
 private User register(String prefix)throws Exception{
  String email=prefix+UUID.randomUUID()+"@example.com";
  JsonNode profile=call(post("/api/v1/auth/register"),null,Map.of("firstName","Ana","lastName","Perez","email",email,"password",PASSWORD),201);
  return new User(profile.get("id").asText(),email,login(email));
 }
 private String login(String email)throws Exception{return call(post("/api/v1/auth/login"),null,Map.of("email",email,"password",PASSWORD),200).get("token").asText();}
 private String property(User owner,String title,List<String> tags)throws Exception{
  return call(post("/api/v1/properties"),owner.token(),Map.of("title",title,"address","Lima Central","price","250000.00","currency","PEN","tags",tags),201).get("id").asText();
 }
 @Given("registered clients and an advisor with independent sessions") public void setup()throws Exception{
  client=register("client");other=register("other");advisor=register("advisor");
  var entity=accounts.findById(UUID.fromString(advisor.id())).orElseThrow();entity.roles="ROLE_REALTOR";accounts.saveAndFlush(entity);
  propertyId=null;offerId=null;schedule=null;notice=null;
 }
 @When("the client submits an invalid profile then saves a valid profile") public void profile()throws Exception{
  call(put("/api/v1/users/me"),client.token(),Map.of("firstName"," ","lastName","Perez","email",client.email()),400);
  assertThat(call(get("/api/v1/users/me"),client.token(),null,200).get("firstName").asText()).isEqualTo("Ana");
  call(put("/api/v1/users/me"),client.token(),Map.of("firstName","Elena","lastName","Actualizada","email",client.email()),200);
 }
 @Then("a new login reads the saved profile and the other client remains unchanged") public void persistedProfile()throws Exception{
  assertThat(call(get("/api/v1/users/me"),login(client.email()),null,200).get("firstName").asText()).isEqualTo("Elena");
  assertThat(call(get("/api/v1/users/me"),other.token(),null,200).get("firstName").asText()).isEqualTo("Ana");
 }
 @When("the client configures TOTP and tries an invalid code followed by a valid code") public void enableTotp()throws Exception{
  JsonNode setup=call(post("/api/v1/users/me/two-factor/setup"),client.token(),Map.of("password",PASSWORD),200);
  String code=totp.code(setup.get("secret").asText(),clock.instant().getEpochSecond()/30);
  call(post("/api/v1/users/me/two-factor/confirm"),client.token(),Map.of("otp",code.equals("000000")?"111111":"000000"),401);
  assertThat(call(get("/api/v1/users/me/two-factor"),client.token(),null,200).get("enabled").asBoolean()).isFalse();
  call(post("/api/v1/users/me/two-factor/confirm"),client.token(),Map.of("otp",code),200);
 }
 @Then("login without the second factor issues no token") public void noJwt()throws Exception{
  JsonNode result=call(post("/api/v1/auth/login"),null,Map.of("email",client.email(),"password",PASSWORD),401);
  assertThat(result.get("code").asText()).isEqualTo("TWO_FACTOR_REQUIRED");assertThat(result.has("token")).isFalse();
 }
 @When("the client closes one of two active sessions") public void logout()throws Exception{
  secondToken=login(client.email());call(post("/api/v1/auth/logout"),client.token(),null,204);
 }
 @Then("the closed session is rejected and the other session can read its profile") public void revoked()throws Exception{
  call(get("/api/v1/users/me"),client.token(),null,401);call(get("/api/v1/users/me"),secondToken,null,200);
 }
 @When("another client tries to delete the property before its owner deletes it") public void removeProperty()throws Exception{
  propertyId=property(client,"Casa privada",List.of("terraza"));call(delete("/api/v1/properties/"+propertyId),other.token(),null,403);
  call(get("/api/v1/properties/"+propertyId),client.token(),null,200);call(delete("/api/v1/properties/"+propertyId),client.token(),null,204);
 }
 @Then("the deleted property is no longer in the catalogue or available by ID") public void removed()throws Exception{
  call(get("/api/v1/properties/"+propertyId),client.token(),null,404);
  assertThat(call(get("/api/v1/properties").param("mine","true"),client.token(),null,200).get("items")).isEmpty();
 }
 @When("the advisor issues an offer to the client") public void issue()throws Exception{
  propertyId=property(advisor,"Casa DEMO",List.of("terraza","garaje"));
  JsonNode offer=call(post("/api/v1/offers"),advisor.token(),Map.of("recipientEmail",client.email(),"propertyId",propertyId,"bankCode","BCP","loanAmount","200000.00","currency","PEN","annualInterestRate","0.085","termMonths",12,"startDate","2024-01-31"),201);
  offerId=offer.get("id").asText();assertThat(offer.get("origin").asText()).isEqualTo("DEMO");
 }
 @Then("only the recipient sees the offer and its notification and marking it read is idempotent") public void readNotice()throws Exception{
  call(get("/api/v1/offers/"+offerId),other.token(),null,404);
  assertThat(call(get("/api/v1/offers"),other.token(),null,200).get("items")).isEmpty();
  JsonNode own=call(get("/api/v1/notifications"),client.token(),null,200);assertThat(own.get("unreadCount").asInt()).isEqualTo(1);notice=own.get("items").get(0);
  String path="/api/v1/notifications/"+notice.get("id").asText()+"/read";call(patch(path),other.token(),null,404);
  JsonNode first=call(patch(path),client.token(),null,200),second=call(patch(path),client.token(),null,200);assertThat(second).isEqualTo(first);
  assertThat(call(get("/api/v1/notifications"),client.token(),null,200).get("unreadCount").asInt()).isZero();
 }
 @When("the source property is removed and the recipient generates its schedule twice") public void generate()throws Exception{
  call(delete("/api/v1/properties/"+propertyId),advisor.token(),null,204);
  String path="/api/v1/offers/"+offerId+"/schedule";schedule=call(post(path),client.token(),null,200);
  assertThat(call(post(path),client.token(),null,200)).isEqualTo(schedule);
 }
 @Then("the same persisted schedule is returned only to its recipient with zero final balance") public void schedule()throws Exception{
  String path="/api/v1/offers/"+offerId+"/schedule";assertThat(call(get(path),client.token(),null,200)).isEqualTo(schedule);call(get(path),other.token(),null,404);
  assertThat(schedule.get("schedule").get(11).get("finalBalance").asText()).isEqualTo("0.00");assertThat(schedule.get("loanAmount").asText()).isEqualTo("200000.00");
 }
 @When("properties with overlapping tags are registered") public void tags()throws Exception{
  property(client,"Casa matching",List.of(" ＴＥＲＲＡＺＡ ","garaje"));property(client,"Casa missing",List.of("terraza"));property(other,"Casa ajena",List.of("terraza","garaje"));
 }
 @Then("the search returns only properties matching every requested tag and owner") public void matching()throws Exception{
  JsonNode result=call(get("/api/v1/properties").param("tags","TERRAZA","garaje").param("q","matching").param("mine","true"),client.token(),null,200);
  assertThat(result.get("totalElements").asInt()).isEqualTo(1);assertThat(result.get("items").get(0).get("title").asText()).isEqualTo("Casa matching");
 }
}
