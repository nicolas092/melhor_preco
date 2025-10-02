package org.acme.views;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.utils.JsonRepository;
import org.bson.Document;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import java.text.NumberFormat;
import java.util.*;

@Path("/dia")
public class DiaResource {

    @Inject
    JsonRepository repo;

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance dia(); // resources/templates/dia.html
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response form() {
        String html = Templates.dia()
                .data("cols", Collections.emptyList())
                .data("rows", Collections.emptyList())
                .data("dia", "")
                .render();
        return Response.ok(html).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response buscar(@FormParam("dia") String diaInput) {
        String datePrefix = normalizeToIsoDatePrefix(diaInput);
        if (datePrefix == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Formato de data inválido. Use ddMMyy ou YYYY-MM-DD").build();
        }

        List<Document> docs = repo.findItemsByDatePrefix(datePrefix, 0); // 0 = sem limite

        // pivot: produto -> (loja -> menorPreco)
        Set<String> lojasSet = new TreeSet<>();
        Map<String, Map<String, Double>> pivot = new TreeMap<>();

        for (Document doc : docs) {
            if (doc.containsKey("texDesc") && doc.containsKey("nomeContrib")) {
                String texDesc = Objects.toString(doc.get("texDesc"), "SEM_DESC");
                String nomeContrib = Objects.toString(doc.get("nomeContrib"), "SEM_NOME");
                Double vlr = toDouble(doc.get("vlrItem"));

                lojasSet.add(nomeContrib);
                pivot.computeIfAbsent(texDesc, k -> new HashMap<>());
                Map<String, Double> row = pivot.get(texDesc);
                if (vlr != null) row.merge(nomeContrib, vlr, Double::min);
                continue;
            }

            List<Document> itens = doc.getList("itens", Document.class);
            if (itens == null) continue;

            for (Document item : itens) {
                String texDesc = Objects.toString(item.get("texDesc"), "SEM_DESC");

                String nomeContrib = "SEM_NOME";
                Object estabObj = item.get("estabelecimento");
                if (estabObj instanceof Document) {
                    Document estab = (Document) estabObj;
                    nomeContrib = Objects.toString(estab.get("nomeContrib"), null);
                    if (nomeContrib == null || nomeContrib.isBlank()) {
                        nomeContrib = Objects.toString(estab.get("nomeFant"), "SEM_NOME");
                    }
                }

                Double vlr = toDouble(item.get("vlrItem"));

                lojasSet.add(nomeContrib);
                pivot.computeIfAbsent(texDesc, k -> new HashMap<>());
                Map<String, Double> row = pivot.get(texDesc);
                if (vlr != null) row.merge(nomeContrib, vlr, Double::min);
            }
        }

        List<String> cols = new ArrayList<>(lojasSet);
        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : pivot.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("texDesc", e.getKey());

            // Aqui criamos a lista de preços para Qute
            List<Map<String, String>> pricesList = new ArrayList<>();
            for (String loja : cols) {
                Map<String, String> p = new HashMap<>();
                p.put("loja", loja);
                Double v = e.getValue().get(loja);
                p.put("valor", v == null ? "" : fmt.format(v));
                pricesList.add(p);
            }
            row.put("pricesList", pricesList);

            rows.add(row);
        }

        String html = Templates.dia()
                .data("cols", cols)
                .data("rows", rows)
                .data("dia", datePrefix)
                .render();

        return Response.ok(html).build();
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble(((String) o).replace(",", ".")); }
            catch (Exception ignored) { return null; }
        }
        return null;
    }

    private String normalizeToIsoDatePrefix(String input) {
        if (input == null) return null;
        input = input.trim();
        if (input.matches("\\d{6}")) { // ddMMyy
            String dd = input.substring(0,2);
            String MM = input.substring(2,4);
            String yy = input.substring(4,6);
            String yyyy = "20" + yy;
            return yyyy + "-" + MM + "-" + dd;
        }
        if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        return null;
    }
}
