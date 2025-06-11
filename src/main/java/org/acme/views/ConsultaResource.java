package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.externo.SefazClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Path("/consulta")
public class ConsultaResource {
    @Inject
    @RestClient
    SefazClient sefazClient;

    @Inject
    ObjectMapper objectMapper;  // injeta Jackson pra parse JSON

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
    @Produces("text/csv")
    public Response receber(
            @FormParam("token") String token,
            @FormParam("gtin") String gtin,
            @FormParam("longitude") double longitude,
            @FormParam("latitude") double latitude,
            @FormParam("nroKmDistancia") int nroKmDistancia,
            @FormParam("nroDiaPrz") int nroDiaPrz) throws Exception {

        String authHeader = "Bearer " + token;
        String jsonResponse = sefazClient.consultaItem(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader);

        Map<String, Object> resposta = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.get("itens");

        StringBuilder csv = new StringBuilder();
        csv.append("vlrItem,nomeContrib,nomeLograd\n");

        for (Map<String, Object> item : itens) {
            Double vlrItem = (Double) item.get("vlrItem");
            Map<String, Object> estabelecimento = (Map<String, Object>) item.get("estabelecimento");
            String nomeContrib = (String) estabelecimento.get("nomeContrib");
            String nomeLograd = (String) estabelecimento.get("nomeLograd");

            csv.append(vlrItem).append(",")
                    .append(nomeContrib).append(",")
                    .append(nomeLograd).append("\n");
        }

        return Response.ok(csv.toString())
                .header("Content-Disposition", "attachment; filename=\"resultado.csv\"")
                .build();
    }

}
