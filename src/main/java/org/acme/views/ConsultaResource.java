package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.MediaType;

@Path("/consulta")
public class ConsultaResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance consulta(String token);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance mostrar(@QueryParam("token") String token) {
        return Templates.consulta(token);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String receber(@FormParam("token") String token, @FormParam("campo1") String campo1, @FormParam("campo2") String campo2) {
        // Aqui você processa os dados, ex: chamar serviço externo usando o token e os campos
        return "Recebido token: " + token + ", campo1: " + campo1 + ", campo2: " + campo2;
    }
}
