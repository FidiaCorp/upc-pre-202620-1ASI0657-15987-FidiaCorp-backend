package com.fidiacorp.credicasa.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.CrediCasaApplication;
import com.fidiacorp.credicasa.application.dto.request.*;
import com.fidiacorp.credicasa.application.usecase.*;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.OfferBank;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Local browser acceptance fixture. Never included in the production artifact. */
public final class BrowserFixtureServer {
    private BrowserFixtureServer() {}
    private record BrowserAccount(UUID id,String email,String password) {}
    public static void main(String[] arguments)throws Exception {
        if(arguments.length!=1)throw new IllegalArgumentException("Expected one local manifest path.");
        Path manifest=Path.of(arguments[0]).toAbsolutePath();
        if(Files.exists(manifest))throw new IllegalArgumentException("Manifest already exists; choose a new path.");
        Path parent=manifest.getParent();Files.createDirectories(parent);
        String namespace=UUID.randomUUID().toString().replace("-","");
        SecureRandom random=new SecureRandom();
        String jwt=randomSecret(random,48),encryption=randomSecret(random,32);
        // Internal command-line properties override inherited environment variables and application.yml.
        String[] settings={
            "--server.address=127.0.0.1","--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:browser_"+namespace+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","--spring.jpa.hibernate.ddl-auto=create-drop",
            "--spring.jpa.show-sql=false","--spring.h2.console.enabled=false",
            "--app.security.jwt.secret="+jwt,"--app.security.two-factor.encryption-key="+encryption,
            "--logging.level.root=WARN","--logging.level.com.fidiacorp.credicasa=WARN"
        };
        ServletWebServerApplicationContext context=(ServletWebServerApplicationContext)new SpringApplication(CrediCasaApplication.class).run(settings);
        boolean published=false;
        try {
            AccountService accounts=context.getBean(AccountService.class);
            BrowserAccount client=register(accounts,"client",namespace,random),other=register(accounts,"other",namespace,random),advisor=register(accounts,"advisor",namespace,random);
            var repository=context.getBean(AccountJpaRepository.class);
            var entity=repository.findById(advisor.id()).orElseThrow();entity.roles="ROLE_REALTOR";repository.saveAndFlush(entity);
            var property=context.getBean(PropertyService.class).create(advisor.id(),new CreatePropertyRequest("Casa de prueba DEMO","Lima, dirección de prueba","Catálogo exclusivo del fixture local",new BigDecimal("250000.00"),Currency.PEN,List.of("terraza","estacionamiento")));
            var offer=context.getBean(CreditOfferService.class).issue(advisor.id(),new CreateCreditOfferRequest(client.email(),property.id(),OfferBank.BCP,new BigDecimal("200000.00"),Currency.PEN,new BigDecimal("0.085"),240,LocalDate.of(2024,1,31)));
            Map<String,Object> data=new LinkedHashMap<>();
            data.put("apiBaseUrl","http://127.0.0.1:"+context.getWebServer().getPort()+"/api/v1");
            data.put("client",client);data.put("otherClient",other);data.put("realtor",advisor);
            data.put("propertyId",property.id());data.put("offerId",offer.id());data.put("pid",ProcessHandle.current().pid());
            // Temp file is private from creation; atomic rename never exposes partially written credentials.
            Path temporary=Files.createTempFile(parent,".browser-fixture-",".json",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.writeString(temporary,context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValueAsString(data)+"\n",StandardCharsets.UTF_8);
                Files.move(temporary,manifest,StandardCopyOption.ATOMIC_MOVE);
            } finally {Files.deleteIfExists(temporary);}
            published=true;
            Runtime.getRuntime().addShutdownHook(new Thread(()->{context.close();try{Files.deleteIfExists(manifest);}catch(Exception ignored){}}));
            System.out.println("Browser fixture ready; private manifest: "+manifest);
            new CountDownLatch(1).await();
        } finally {
            context.close();
            if(published)Files.deleteIfExists(manifest);
        }
    }
    private static String randomSecret(SecureRandom random,int bytes){byte[] value=new byte[bytes];random.nextBytes(value);return Base64.getEncoder().encodeToString(value);}
    private static BrowserAccount register(AccountService service,String prefix,String namespace,SecureRandom random){
        String email=prefix+"-"+namespace+"@example.com",password=randomSecret(random,24);
        var profile=service.register(new RegisterAccountRequest("Prueba","Navegador",email,password));
        return new BrowserAccount(profile.id(),profile.email(),password);
    }
}
