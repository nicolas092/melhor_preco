package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;

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
    @Produces(MediaType.TEXT_HTML)
    public Response autenticar(@FormParam("username") String username, @FormParam("password") String password) {
        try {
            Response loginResponse = sefazClient.login(username, password, "password", "68cdf21a37c40f9bf7eaa0bf9ac934e3");
            String responseBody = loginResponse.readEntity(String.class);

            if (loginResponse.getStatus() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(responseBody);
                String accessToken = json.get("access_token").asText();

                return Response.ok(
                        ConsultaResource.Templates.consulta()
                        .data("itens", Collections.emptyList()) // Pode colocar lista vazia aqui
                        .data("token", accessToken)
                        .render()
                ).build();
            } else {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("Login externo falhou").build();
            }
        } catch (Exception e) {
            return Response.serverError()
                    .entity("Erro no login externo: " + e.getMessage()).build();
        }
    }
}
