package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import org.acme.externo.SefazClient;

@Path("/logon")
public class LogonResource {

    @Inject
    @RestClient
    SefazClient sefazClient;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance logon(String mensagem);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance mostrar() {
        return Templates.logon("Informe seus dados");
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response autenticar(@FormParam("username") String username, @FormParam("password") String password) {
        try {
            // Ajuste grant_type e private_key conforme necessário
            Response loginResponse = sefazClient.login(username, password, "password", "68cdf21a37c40f9bf7eaa0bf9ac934e3");
            String responseBody = loginResponse.readEntity(String.class);
            System.out.println("Resposta do login: " + responseBody);

            if (loginResponse.getStatus() == 200) {
                // Pega o cookie da resposta (exemplo)
                String cookie = loginResponse.getHeaderString("Set-Cookie");

                // Redireciona para /consulta passando o cookie como query param (não ideal pra segurança, mas pra teste)
                return Response.seeOther(UriBuilder.fromPath("/consulta").queryParam("cookie", cookie).build()).build();
            } else {
                return Response.status(Response.Status.UNAUTHORIZED).entity("Login externo falhou").build();
            }
        } catch (Exception e) {
            return Response.serverError().entity("Erro no login externo: " + e.getMessage()).build();
        }
    }
}