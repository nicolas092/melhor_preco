package org.acme.views;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.PartType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
    ObjectMapper objectMapper;

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance consulta();
    }

    // O método receber() não foi alterado
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

        String jsonResponse = GtinUtils.isGtin(gtin) ?
                sefazClient.consultaItem(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader) :
                sefazClient.consultaItemPorDescricao(gtin, longitude, latitude, nroKmDistancia, nroDiaPrz, authHeader);

        Map<String, Object> root = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> itens = (List<Map<String, Object>>) root.get("itens");

        if (!GtinUtils.isGtin(gtin) && itens != null) {
            for (Map<String, Object> item : itens) {
                item.put("produtoPadronizado", gtin);
            }
            String modifiedJson = objectMapper.writeValueAsString(root);
            repo.salvarJson(modifiedJson);
        } else {
            repo.salvarJson(jsonResponse);
        }

        List<Map<String, Object>> listaFormatada = new ArrayList<>();
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        if (itens != null) {
            for (Map<String, Object> item : itens) {
                Map<String, Object> estabelecimento = (Map<String, Object>) item.get("estabelecimento");
                Map<String, Object> mapItem = new HashMap<>();

                Object vlrObj = item.get("vlrItem");
                Double vlrItem = null;
                if (vlrObj instanceof Number) {
                    vlrItem = ((Number) vlrObj).doubleValue();
                } else if (vlrObj instanceof String) {
                    try {
                        vlrItem = Double.parseDouble(((String) vlrObj).replace(",", "."));
                    } catch (Exception ignored) {}
                }

                String valorFormatado = vlrItem == null ? "" : formatter.format(vlrItem);
                String texDesc = Objects.toString(item.get("texDesc"), "");

                mapItem.put("vlrItem", valorFormatado);
                mapItem.put("nomeContrib", estabelecimento != null ? estabelecimento.get("nomeContrib") : "");
                mapItem.put("nomeLograd", estabelecimento != null ? estabelecimento.get("nomeLograd") : "");
                mapItem.put("texDesc", texDesc);

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

    // O método limpar() não foi alterado
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

    // ===================================================================================
    // MÉTODO UPLOADTXT AJUSTADO PARA O TEMPO CURTO (MILISSEGUNDOS)
    // ===================================================================================
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response uploadTxt(@MultipartForm TxtUploadForm form,
                              @FormParam("token") String token,
                              @FormParam("longitude") double longitude,
                              @FormParam("latitude") double latitude,
                              @FormParam("nroKmDistancia") int nroKmDistancia,
                              @FormParam("nroDiaPrz") int nroDiaPrz) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(form.file))) {
            String linha;
            Random random = new Random();

            while ((linha = reader.readLine()) != null) {
                String gtin = linha.trim();
                if (gtin.isEmpty()) continue;

                // 1. Processa uma linha do arquivo
                receber(token, gtin, longitude, latitude, nroKmDistancia, nroDiaPrz);

                // 2. Pausa por um tempo aleatório entre 300 e 600 MILISSEGUNDOS
                int delayInMillis = 500 + random.nextInt(401); // Gera de 0-300, soma 300 -> range 300-600
                Thread.sleep(delayInMillis);
            }

            // 3. Após o loop terminar, redireciona o usuário para a página /dia
            return Response.seeOther(new URI("/dia")).build();

        } catch (Exception e) {
            return Response.serverError().entity("Erro ao processar arquivo: " + e.getMessage()).build();
        }
    }

    public static class TxtUploadForm {
        @FormParam("file")
        @PartType(MediaType.TEXT_PLAIN)
        public InputStream file;
    }
}