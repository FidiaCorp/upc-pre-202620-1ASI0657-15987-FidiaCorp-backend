package com.fidiacorp.credicasa.interfaces.rest;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:properties;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PropertyIntegrationTest {
    private static final String PASSWORD="CorrectHorseBattery123!",BASE="/api/v1/properties";
    record User(UUID id,String token) {}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PropertyJpaRepository properties;
    @Autowired AccountJpaRepository accounts;
    @Autowired QuotationJpaRepository quotations;
    private User owner,other;
    private String body(Object value)throws Exception{return json.writeValueAsString(value);}
    private JsonNode response(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
    private User user(String email)throws Exception {
        var registered=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Lucía","lastName","Pérez","email",email,"password",PASSWORD))))
            .andExpect(status().isCreated()).andReturn();
        var logged=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        return new User(UUID.fromString(response(registered).get("id").asText()),response(logged).get("token").asText());
    }
    @BeforeEach void prepare()throws Exception {
        properties.deleteAll();quotations.deleteAll();accounts.deleteAll();owner=user("owner@example.com");other=user("other@example.com");
    }
    private Map<String,Object> data(String title,List<String> tags) {
        Map<String,Object> d=new HashMap<>();d.put("title",title);d.put("address","Av. Central 120, Lima");
        d.put("description","Amplio inmueble luminoso");d.put("price","250000.50");d.put("currency","PEN");d.put("tags",tags);return d;
    }
    private JsonNode create(User user,String title,List<String> tags)throws Exception {
        return response(mvc.perform(post(BASE).header("Authorization","Bearer "+user.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(data(title,tags)))).andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn());
    }
    @Test void registersPersistentPropertyWithPrincipalOwnerAndNormalizedTags()throws Exception {
        var d=data(" Casa familiar ",List.of(" ＴＥＲＲＡＺＡ ","terraza","Con   Estacionamiento"));d.put("ownerId",other.id().toString());
        var created=response(mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(d))).andExpect(status().isCreated()).andExpect(jsonPath("title").value("Casa familiar"))
            .andExpect(jsonPath("ownerId").value(owner.id().toString())).andExpect(jsonPath("tags.length()").value(2))
            .andExpect(jsonPath("tags[0]").value("con estacionamiento")).andExpect(jsonPath("tags[1]").value("terraza")).andReturn());
        UUID id=UUID.fromString(created.get("id").asText());
        assertThat(properties.findById(id).orElseThrow().ownerId).isEqualTo(owner.id());
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+other.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("price").value("250000.50")).andExpect(jsonPath("createdAt").isString());
    }
    @Test void preservesMaximumPriceAsExactDecimalStringInCreateGetAndList()throws Exception {
        var d=data("Precio máximo",List.of());d.put("price","9999999999999999.99");d.put("currency","USD");
        var created=response(mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token())
            .contentType(MediaType.APPLICATION_JSON).content(body(d))).andExpect(status().isCreated())
            .andExpect(jsonPath("price").value("9999999999999999.99")).andReturn());
        String id=created.get("id").asText();
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+owner.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("price").value("9999999999999999.99"));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("items[0].price").value("9999999999999999.99"));
        assertThat(properties.findById(UUID.fromString(id)).orElseThrow().price)
            .isEqualByComparingTo("9999999999999999.99");
    }
    @Test void searchesPersistedCatalogueWithAllTagsAndMineAndEmptyResults()throws Exception {
        create(owner,"Casa Sur",List.of("terraza","estacionamiento"));
        create(other,"Departamento Sur",List.of("terraza"));
        create(other,"Casa Norte",List.of("terraza","estacionamiento"));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("tags"," ＴＥＲＲＡＺＡ ","ESTACIONAMIENTO"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("tags","terraza","estacionamiento")
            .param("q"," SUR ").param("mine","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("items[0].title").value("Casa Sur"));
        mvc.perform(get(BASE).header("Authorization","Bearer "+other.token()).param("mine","true"))
            .andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("q","no coincide"))
            .andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty()).andExpect(jsonPath("totalPages").value(0));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("q","LUMINOSO"))
            .andExpect(jsonPath("totalElements").value(3));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("q","central 120"))
            .andExpect(jsonPath("totalElements").value(3));
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()))
            .andExpect(jsonPath("totalElements").value(3));
    }
    @Test void escapesSqlWildcardsAndPaginatesWithoutDuplicates()throws Exception {
        create(owner,"Casa 100%_unica\\sur",List.of());create(owner,"Casa alternativa",List.of());create(owner,"Casa tercera",List.of());
        for(String query:List.of("100%","_unica","\\sur")) {
            mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("q",query))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        }
        Set<String> ids=new HashSet<>();
        for(int page=0;page<3;page++) {
            var node=response(mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token())
                .param("page",Integer.toString(page)).param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("totalElements").value(3)).andReturn());
            ids.add(node.get("items").get(0).get("id").asText());
        }
        assertThat(ids).hasSize(3);
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("page","99"))
            .andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty());
    }
    @Test void rejectsInvalidQueryAndCreationWithoutWriting()throws Exception {
        for(var pair:List.of(Map.entry("page","-1"),Map.entry("size","0"),Map.entry("size","101"),Map.entry("page","NaN"),
            Map.entry("q","x".repeat(121)),Map.entry("tags"," "),Map.entry("tags","x".repeat(41)),Map.entry("mine","invalid"))) {
            mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param(pair.getKey(),pair.getValue()))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get(BASE+"/invalid-uuid").header("Authorization","Bearer "+owner.token())).andExpect(status().isBadRequest());
        var invalid=data(" ",List.of(""));invalid.put("address"," ");invalid.put("price","-2");
        mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON).content(body(invalid)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("validationErrors.title").exists())
            .andExpect(jsonPath("validationErrors.price").exists());
        for(String price:List.of("0","1.001","10000000000000000")) {
            var d=data("Casa",List.of());d.put("price",price);
            mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON).content(body(d)))
                .andExpect(status().isBadRequest());
        }
        var wrongCurrency=data("Casa",List.of());wrongCurrency.put("currency","EUR");
        mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON).content(body(wrongCurrency)))
            .andExpect(status().isBadRequest());
        assertThat(properties.count()).isZero();
    }
    @Test void onlyOwnerCanSoftDeleteAndNoDeletedPropertyRemainsQueryable()throws Exception {
        String id=create(owner,"Casa propia",List.of("terraza")).get("id").asText();
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+other.token())).andExpect(status().isForbidden());
        assertThat(properties.findById(UUID.fromString(id)).orElseThrow().deletedAt).isNull();
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+owner.token())).andExpect(status().isNoContent());
        assertThat(properties.findById(UUID.fromString(id)).orElseThrow().deletedAt).isNotNull();
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+owner.token())).andExpect(status().isNotFound());
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+owner.token())).andExpect(status().isNotFound());
        mvc.perform(get(BASE).header("Authorization","Bearer "+other.token()).param("tags","terraza"))
            .andExpect(status().isOk()).andExpect(jsonPath("items").isEmpty());
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token()).param("mine","true"))
            .andExpect(jsonPath("items").isEmpty());
    }
    @Test void enforcesAllowedRolesAndDoesNotExposeUpdate()throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body(data("Casa",List.of())))).andExpect(status().isUnauthorized());
        var realtor=accounts.findById(other.id()).orElseThrow();realtor.roles="ROLE_REALTOR";accounts.saveAndFlush(realtor);
        String id=create(other,"Casa asesor",List.of()).get("id").asText();
        mvc.perform(get(BASE).header("Authorization","Bearer "+other.token())).andExpect(status().isOk());
        mvc.perform(put(BASE+"/"+id).header("Authorization","Bearer "+other.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(data("Modificada",List.of())))).andExpect(status().isMethodNotAllowed());
        var denied=accounts.findById(owner.id()).orElseThrow();denied.roles="ROLE_VIEWER";accounts.saveAndFlush(denied);
        mvc.perform(get(BASE).header("Authorization","Bearer "+owner.token())).andExpect(status().isForbidden());
        mvc.perform(post(BASE).header("Authorization","Bearer "+owner.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(data("Otra",List.of())))).andExpect(status().isForbidden());
    }
    @Test void deletingCataloguePropertyDoesNotChangeHistoricalQuotationSnapshot()throws Exception {
        String propertyId=create(owner,"Casa histórica",List.of()).get("id").asText();
        var calculated=mvc.perform(post("/api/v1/simulations/calculate").header("Authorization","Bearer "+owner.token())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("loanAmount",200000,"propertyValue",250000.50,"currency","PEN",
                "termMonths",12,"annualInterestRate",0.085,"propertyAddress","Av. Central 120, Lima","startDate","2027-01-01"))))
            .andExpect(status().isCreated()).andReturn();
        String quotationId=response(calculated).get("quotationId").asText();
        JsonNode before=response(mvc.perform(get("/api/v1/simulations/"+quotationId).header("Authorization","Bearer "+owner.token()))
            .andExpect(status().isOk()).andReturn());
        mvc.perform(delete(BASE+"/"+propertyId).header("Authorization","Bearer "+owner.token())).andExpect(status().isNoContent());
        JsonNode after=response(mvc.perform(get("/api/v1/simulations/"+quotationId).header("Authorization","Bearer "+owner.token()))
            .andExpect(status().isOk()).andReturn());
        assertThat(after).isEqualTo(before);
        var snapshot=quotations.findById(UUID.fromString(quotationId)).orElseThrow();
        assertThat(snapshot.getPropertyAddress()).isEqualTo("Av. Central 120, Lima");
        assertThat(snapshot.getPropertyValue()).isEqualByComparingTo("250000.50");
    }
}
