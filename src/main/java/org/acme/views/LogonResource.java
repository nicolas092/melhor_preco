package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import org.acme.externo.SefazClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;

@Path("/logon")
public class LogonResource {

    @Inject
    @RestClient
    SefazClient sefazClient;

    @Inject
    ObjectMapper objectMapper;

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance logon();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance form() {
        return Templates.logon().data("mensagem", "");
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response autenticar(
        @FormParam("username") String username,
        @FormParam("password") String password,
        @Context UriInfo uriInfo
    ) {
        try {
            String grantType = "password";
            String privateKey = "nfg-sefaz"; // valor usado pela API da Sefaz

            Response response = sefazClient.login(username, password, grantType, privateKey);

            if (response.getStatus() != 200) {
                return Response.ok(
                    Templates.logon().data("mensagem", "Usuário ou senha inválidos.")
                ).build();
            }

            String json = response.readEntity(String.class);
            JsonNode root = objectMapper.readTree(json);
            String token = root.get("access_token").asText();

            URI redirectUri = uriInfo.getBaseUriBuilder()
                .path("/consulta")
                .queryParam("token", token)
                .build();

            return Response.seeOther(redirectUri).build();

        } catch (Exception e) {
            return Response.ok(
                Templates.logon().data("mensagem", "Erro ao logar: " + e.getMessage())
            ).build();
        }
    }
}
