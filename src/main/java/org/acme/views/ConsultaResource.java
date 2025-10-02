package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.externo.SefazClient;
import org.acme.utils.GtinUtils;
import org.acme.utils.JsonRepository;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.util.*;

@Path("/consulta")
public class ConsultaResource {
    @Inject
    JsonRepository repo;

    @Inject
    @RestClient
    SefazClient sefazClient;

    @Inject
    ObjectMapper objectMapper;  // Para JSON

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance consulta();  // Sem parâmetro aqui
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

        String jsonResponse = GtinUtils.isGtin(gtin)?
                sefazClient.consultaItem(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader) : //se gtin
                sefazClient.consultaItemPorDescricao(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader); // se nao

        // parse pra poder injetar produtoPadronizado (somente se não for GTIN)
        Map<String, Object> root = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) root.get("itens");

        if (!GtinUtils.isGtin(gtin) && itens != null) {
            for (Map<String, Object> item : itens) {
                // sobrescreve/insere produtoPadronizado com o input (descrição)
                item.put("produtoPadronizado", gtin);
            }
            // reserializa e salva o JSON modificado
            String modifiedJson = objectMapper.writeValueAsString(root);
            repo.salvarJson(modifiedJson);
        } else {
            // se for GTIN ou não há itens, salva response original
            repo.salvarJson(jsonResponse);
        }

        // monta listaFormatada para exibir na view (usa os itens já modificados em 'root' para consistência)
        List<Map<String, Object>> listaFormatada = new ArrayList<>();
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        if (itens != null) {
            for (Map<String, Object> item : itens) {
                Map<String, Object> estabelecimento = (Map<String, Object>) item.get("estabelecimento");
                Map<String, Object> mapItem = new HashMap<>();

                // trata vlrItem de forma segura (Number ou String)
                Object vlrObj = item.get("vlrItem");
                Double vlrItem = null;
                if (vlrObj instanceof Number) {
                    vlrItem = ((Number) vlrObj).doubleValue();
                } else if (vlrObj instanceof String) {
                    try { vlrItem = Double.parseDouble(((String) vlrObj).replace(",", ".")); }
                    catch (Exception ignored) {}
                }

                String valorFormatado = vlrItem == null ? "" : formatter.format(vlrItem);
                String texDesc = Objects.toString(item.get("texDesc"), "");

                mapItem.put("vlrItem", valorFormatado);
                mapItem.put("nomeContrib", estabelecimento != null ? estabelecimento.get("nomeContrib") : "");
                mapItem.put("nomeLograd", estabelecimento != null ? estabelecimento.get("nomeLograd") : "");
                mapItem.put("texDesc", texDesc);

                // produtoPadronizado inserido apenas quando o input era descrição (não GTIN)
                if (!GtinUtils.isGtin(gtin)) {
                    mapItem.put("produtoPadronizado", gtin);
                }

                listaFormatada.add(mapItem);
            }
        }

        String html = Templates.consulta()
                .data("itens", listaFormatada)
                .data("token", token)
                .render();

        return Response.ok(html).build();
    }

    @POST
    @Path("/limpar")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response limpar(@FormParam("token") String accessToken) {
        return Response.ok(
                ConsultaResource.Templates.consulta()
                        .data("itens", Collections.emptyList())
                        .data("token", accessToken)
                        .render()
        ).build();
    }
}
