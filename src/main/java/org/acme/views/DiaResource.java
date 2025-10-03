package org.acme.views;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.utils.JsonRepository;
import org.bson.Document;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate; // <-- IMPORTAÇÃO ADICIONADA
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Path("/dia")
public class DiaResource {

    @Inject
    JsonRepository repo;

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {
        public static native TemplateInstance dia();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response form() {
        String html = Templates.dia()
                .data("cols", Collections.emptyList())
                .data("rows", Collections.emptyList())
                .data("diaInicio", "")
                .data("diaFim", "")
                .render();
        return Response.ok(html).build();
    }

    // thresholds — ajuste rápido aqui se estiver juntando demais ou de menos
    private static final double TOKEN_MATCH_THRESHOLD = 0.45; // overlap (menor = mais permissivo)
    private static final double LEVENSHTEIN_RATIO_THRESHOLD = 0.75; // 0..1 (maior = mais parecidos)
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9\\s]");
    private static final Set<String> REMOVE_WORDS = new HashSet<>(Arrays.asList(
            "kg","g","un","unidade","unid","cx","pct","litro","lt","l","ml","ml.","ml,", "lata","longneck",
            "cerveja","garrafa","pack","promo","promocao","promoção","cx","frasco"
    ));

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response buscar(@FormParam("datainicio") String diaInicioInput,
                           @FormParam("datafim") String diaFimInput) {

        // normaliza para ISO (YYYY-MM-DD) se possível
        String startIso = normalizeToIsoDatePrefix(diaInicioInput);
        String endIso = normalizeToIsoDatePrefix(diaFimInput);

        if (startIso == null && endIso == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Formato de data inválido. Informe datainicio ou datafim (ddMMyy ou YYYY-MM-DD).").build();
        }

        if (startIso == null) startIso = endIso;
        if (endIso == null) endIso = startIso;

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startIso);
            end = LocalDate.parse(endIso);
        } catch (DateTimeParseException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Erro ao parsear datas. Use ddMMyy ou YYYY-MM-DD.").build();
        }

        // se inverteram, troca para ficar start <= end
        if (end.isBefore(start)) {
            LocalDate tmp = start; start = end; end = tmp;
            String tmpIso = startIso; startIso = endIso; endIso = tmpIso;
        }

        // Busca documentos para cada dia do intervalo e junta em uma lista
       List<Document> docs = repo.findItemsByDateRange(start, end);


        // --- processamento (sua lógica original mantida) ---
        Map<String, ProductRowData> products = new LinkedHashMap<>();
        List<Group> groups = new ArrayList<>();

        for (Document doc : docs) {
            List<Document> itens = tryGetList(doc, "itens");
            if (itens != null && !itens.isEmpty()) {
                for (Document it : itens) addToProducts(products, it, groups);
                continue;
            }
            List<Document> itensA = tryGetList(doc, "itensComAgrupamento");
            if (itensA != null && !itensA.isEmpty()) {
                for (Document it : itensA) addToProducts(products, it, groups);
                continue;
            }
            if (doc.containsKey("vlrItem")) addToProducts(products, doc, groups);
        }

        // coleta colunas (lojas)
        Set<String> lojasSet2 = new TreeSet<>();
        for (ProductRowData pr : products.values()) lojasSet2.addAll(pr.prices.keySet());
        List<String> cols = new ArrayList<>(lojasSet2);

        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        List<Map<String, Object>> rows = new ArrayList<>();

        for (ProductRowData pr : products.values()) {
            Map<String, Object> row = new HashMap<>();
            row.put("texDesc", pr.displayName);
            row.put("gtin", pr.gtin == null ? "" : pr.gtin.toString());

            // menor preço da linha
            Double min = pr.prices.values().stream().filter(Objects::nonNull).min(Double::compare).orElse(null);

            List<Map<String, String>> pricesList = new ArrayList<>();
            for (String loja : cols) {
                Map<String, String> cell = new HashMap<>();
                Double v = pr.prices.get(loja);
                cell.put("loja", loja);
                cell.put("valor", v == null ? "-" : fmt.format(v));
                boolean isMin = (v != null && min != null && Double.compare(v, min) == 0);
                cell.put("cssClass", isMin ? "price-lowest price" : "price");
                pricesList.add(cell);
            }

            row.put("pricesList", pricesList);
            rows.add(row);
        }

        // passa os dados e também as duas datas pro template
        String html = Templates.dia()
                .data("cols", cols)
                .data("rows", rows)
                .data("diaInicio", startIso)
                .data("diaFim", endIso)
                .render();

        return Response.ok(html).build();
    }

    // --- classes e métodos auxiliares (sem alterações) ---

    private static class ProductRowData {
        final String key;
        String displayName;
        Long gtin;
        Map<String, Double> prices = new HashMap<>();
        int count = 0;
        ProductRowData(String key, String displayName, Long gtin) {
            this.key = key;
            this.displayName = displayName;
            this.gtin = gtin;
        }
        void addPrice(String loja, Double valor) {
            if (loja == null) loja = "SEM_NOME";
            if (valor == null) {
                count++;
                return;
            }
            Double prev = prices.get(loja);
            if (prev == null || valor < prev) prices.put(loja, valor);
            count++;
        }
    }

    private void addToProducts(Map<String, ProductRowData> products, Document item, List<Group> groups) {
        Long gtin = null;
        Object gobj = item.get("gtin");
        if (gobj instanceof Number) gtin = ((Number) gobj).longValue();
        else if (gobj instanceof String) {
            try { gtin = Long.parseLong(((String) gobj).trim()); } catch (Exception ignored) {}
        }

        String key;
        if (gtin != null && gtin != 0L) {
            key = "GTIN:" + gtin;
        } else {
            String pad = Objects.toString(item.get("produtoPadronizado"), "").trim();
            if (!pad.isEmpty()) key = "PAD:" + pad;
            else key = getProdutoGroupingKey(item, groups);
        }

        String display = Objects.toString(item.get("texDesc"), key);
        String loja = extractNomeContrib(item);
        Double vlr = toDouble(item.get("vlrItem"));

        ProductRowData pr = products.get(key);
        if (pr == null) {
            pr = new ProductRowData(key, display, gtin);
            products.put(key, pr);
        } else {
            if (display != null && display.length() < pr.displayName.length()) pr.displayName = display;
        }
        pr.addPrice(loja, vlr);
    }

    private String getProdutoGroupingKey(Document doc, List<Group> groups) {
        String padRaw = Objects.toString(doc.get("produtoPadronizado"), null);
        if (padRaw != null && !padRaw.isBlank()) {
            String normPad = Normalizer.normalize(padRaw, Normalizer.Form.NFKD)
                    .replaceAll("\\p{M}", "")
                    .replaceAll("[^A-Za-z0-9 ]", " ")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase();
            if (!normPad.isEmpty()) return "PAD:" + normPad;
        }
        String texDesc = Objects.toString(doc.get("texDesc"), "").trim();
        if (texDesc.isEmpty()) {
            return Objects.toString(doc.get("codIntItem"), Objects.toString(doc.get("codCnpjEstab"), "SEM_DESC"));
        }

        String normalized = normalizeEnhanced(texDesc);
        Set<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) return texDesc;

        for (Group g : groups) {
            double overlap = intersectionRatio(tokens, g.tokens);
            if (overlap >= TOKEN_MATCH_THRESHOLD) {
                g.count++;
                if (texDesc.length() < g.displayName.length()) g.displayName = texDesc;
                g.tokens.addAll(tokens);
                return g.displayName;
            }
        }

        for (Group g : groups) {
            double levRatio = levenshteinRatio(normalized, g.normalized);
            if (levRatio >= LEVENSHTEIN_RATIO_THRESHOLD) {
                g.count++;
                if (texDesc.length() < g.displayName.length()) g.displayName = texDesc;
                g.tokens.addAll(tokens);
                g.normalized = mergeNormalized(g.normalized, normalized);
                return g.displayName;
            }
        }

        Group newG = new Group(texDesc, tokens, normalized);
        groups.add(newG);
        return newG.displayName;
    }

    private String extractNomeContrib(Document itemOrDoc) {
        String nomeContrib = "SEM_NOME";
        Object estabObj = itemOrDoc.get("estabelecimento");
        if (estabObj instanceof Document) {
            Document estab = (Document) estabObj;
            String nc = Objects.toString(estab.get("nomeContrib"), "").trim();
            String nl = Objects.toString(estab.get("nomeLograd"), "").trim();
            if (nc == null || nc.isBlank()) nc = Objects.toString(estab.get("nomeFant"), "").trim();
            String combined;
            if ((nc == null || nc.isBlank()) && (nl == null || nl.isBlank())) {
                Object cnpjObj = estab.get("codCnpjEstab");
                combined = cnpjObj == null ? "SEM_NOME" : Objects.toString(cnpjObj);
            } else if (nl == null || nl.isBlank()) {
                combined = nc;
            } else {
                combined = nc + " (" + nl + ")";
            }
            combined = combined.replaceAll("\\.", "").replaceAll("[\u00A0]", " ").replaceAll("\\s+", " ").trim();
            nomeContrib = combined;
        } else {
            nomeContrib = Objects.toString(itemOrDoc.get("nomeContrib"), nomeContrib);
            if (nomeContrib != null) {
                nomeContrib = nomeContrib.replaceAll("\\.", "").replaceAll("\\s+", " ").trim();
            }
        }
        if (nomeContrib == null || nomeContrib.isBlank()) nomeContrib = "SEM_NOME";
        return nomeContrib;
    }

    private static double intersectionRatio(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() > b.size() ? a : b;
        long common = smaller.stream().filter(larger::contains).count();
        return (double) common / (double) smaller.size();
    }

    private static Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) return Collections.emptySet();
        String[] parts = s.split("\\s+");
        return Arrays.stream(parts)
                .filter(p -> p.length() > 1)
                .filter(p -> !REMOVE_WORDS.contains(p))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeEnhanced(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        n = n.replaceAll("\\b(\\d{1,4})\\s*ml\\.?\\b", "$1ml");
        n = NON_ALNUM.matcher(n).replaceAll(" ");
        return n.replaceAll("\\s+", " ").trim();
    }

    private static String mergeNormalized(String a, String b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return a.length() <= b.length() ? a : b;
    }

    private static double levenshteinRatio(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - ((double) dist / (double) max);
    }

    private static int levenshtein(String s0, String s1) {
        int len0 = s0.length() + 1;
        int len1 = s1.length() + 1;
        int[] prev = new int[len1];
        int[] cur = new int[len1];
        for (int j = 0; j < len1; j++) prev[j] = j;
        for (int i = 1; i < len0; i++) {
            cur[0] = i;
            for (int j = 1; j < len1; j++) {
                int cost = (s0.charAt(i - 1) == s1.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[len1 - 1];
    }

    @SuppressWarnings("unchecked")
    private List<Document> tryGetList(Document doc, String key) {
        Object o = doc.get(key);
        if (o instanceof List) {
            List<?> list = (List<?>) o;
            List<Document> out = new ArrayList<>();
            for (Object it : list) {
                if (it instanceof Document) out.add((Document) it);
                else if (it instanceof Map) out.add(new Document((Map<String, Object>) it));
            }
            return out;
        }
        return null;
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
        if (input.matches("\\d{6}")) {
            String dd = input.substring(0,2);
            String MM = input.substring(2,4);
            String yy = input.substring(4,6);
            String yyyy = "20" + yy;
            return yyyy + "-" + MM + "-" + dd;
        }
        if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        return null;
    }

    private static class Group {
        String displayName;
        Set<String> tokens;
        String normalized;
        int count;
        Group(String displayName, Set<String> tokens, String normalized) {
            this.displayName = displayName;
            this.tokens = new LinkedHashSet<>(tokens);
            this.normalized = normalized;
            this.count = 1;
        }
    }
}