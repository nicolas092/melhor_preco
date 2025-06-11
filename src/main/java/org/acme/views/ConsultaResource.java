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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.print.attribute.standard.Media;

@Path("/consulta")
public class ConsultaResource {

    @Inject
    @RestClient
    SefazClient sefazClient;

    @Inject
    ObjectMapper objectMapper;  // Para JSON

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance consulta(String token);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response receber(
        @FormParam("token") String token,
        @FormParam("gtin") String gtin,
        @FormParam("longitude") double longitude,
        @FormParam("latitude") double latitude,
        @FormParam("nroKmDistancia") int nroKmDistancia,
        @FormParam("nroDiaPrz") int nroDiaPrz
    ) throws Exception {
        String authHeader = "Bearer " + token;
        String jsonResponse = sefazClient.consultaItem(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader);

        Map<String, Object> resposta = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) resposta.get("itens");

        List<Map<String, Object>> listaFormatada = new ArrayList<>();
        for (Map<String, Object> item : itens) {
            Map<String, Object> estabelecimento = (Map<String, Object>) item.get("estabelecimento");
            Map<String, Object> mapItem = new HashMap<>();
            mapItem.put("vlrItem", item.get("vlrItem"));
            mapItem.put("nomeContrib", estabelecimento.get("nomeContrib"));
            mapItem.put("nomeLograd", estabelecimento.get("nomeLograd"));
            listaFormatada.add(mapItem);
        }

        String html = Templates.consulta(token)
            .data("itens", listaFormatada)
            .data("token", token)
            .render(); // <-- aqui renderiza o HTML

        return Response.ok(html).build();
    }
}

