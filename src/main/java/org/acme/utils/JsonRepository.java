package org.acme.utils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDate; // Importação necessária
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class JsonRepository {

    @Inject
    MongoClient mongoClient;

    public void salvarJson(String json) {
        MongoDatabase db = mongoClient.getDatabase("meu_banco");
        MongoCollection<Document> collection = db.getCollection("jsons");
        Document doc = Document.parse(json);
        collection.insertOne(doc);
    }

    /**
     * MÉTODO NOVO E OTIMIZADO
     * Busca itens em um intervalo de datas com uma única consulta ao banco.
     * @param startDate Data inicial do intervalo.
     * @param endDate   Data final do intervalo.
     * @return Lista de documentos com os itens encontrados.
     */
    public List<Document> findItemsByDateRange(LocalDate startDate, LocalDate endDate) {
        MongoDatabase db = mongoClient.getDatabase("meu_banco");
        MongoCollection<Document> coll = db.getCollection("jsons");
        List<Document> out = new ArrayList<>();

        // A data final na consulta é exclusiva (menor que o dia seguinte)
        // para incluir corretamente todos os horários do último dia.
        String startRange = startDate.toString();
        String endRange = endDate.plusDays(1).toString();

        // --- Pipeline 1: para o array 'itens' ---
        {
            Bson unwind = Aggregates.unwind("$itens");
            // Filtro otimizado: itens.dthEmiNFe >= startDate E itens.dthEmiNFe < endDate+1day
            Bson match = Aggregates.match(
                Filters.and(
                    Filters.gte("itens.dthEmiNFe", startRange),
                    Filters.lt("itens.dthEmiNFe", endRange)
                )
            );
            Bson project = Aggregates.project(Projections.fields(
                Projections.computed("texDesc", "$itens.texDesc"),
                Projections.computed("nomeContrib", "$itens.estabelecimento.nomeContrib"),
                Projections.computed("nomeFant", "$itens.estabelecimento.nomeFant"),
                Projections.computed("produtoPadronizado", "$itens.produtoPadronizado"),
                Projections.computed("gtin", "$itens.gtin"),
                Projections.computed("vlrItem", "$itens.vlrItem"),
                Projections.computed("estabelecimento", "$itens.estabelecimento")
            ));
            List<Bson> pipeline = Arrays.asList(unwind, match, project);
            coll.aggregate(pipeline).into(out);
        }

        // --- Pipeline 2: para o array 'itensComAgrupamento' ---
        {
            Bson unwind = Aggregates.unwind("$itensComAgrupamento");
            Bson match = Aggregates.match(
                Filters.and(
                    Filters.gte("itensComAgrupamento.dthEmiNFe", startRange),
                    Filters.lt("itensComAgrupamento.dthEmiNFe", endRange)
                )
            );
            Bson project = Aggregates.project(Projections.fields(
                Projections.computed("texDesc", "$itensComAgrupamento.texDesc"),
                Projections.computed("nomeContrib", "$itensComAgrupamento.estabelecimento.nomeContrib"),
                Projections.computed("nomeFant", "$itensComAgrupamento.estabelecimento.nomeFant"),
                Projections.computed("produtoPadronizado", "$itensComAgrupamento.produtoPadronizado"),
                Projections.computed("gtin", "$itensComAgrupamento.gtin"),
                Projections.computed("vlrItem", "$itensComAgrupamento.vlrItem"),
                Projections.computed("estabelecimento", "$itensComAgrupamento.estabelecimento")
            ));
            List<Bson> pipeline = Arrays.asList(unwind, match, project);
            coll.aggregate(pipeline).into(out);
        }

        return out;
    }


    /**
     * MÉTODO ANTIGO (MANTIDO PARA REFERÊNCIA)
     * Retorna lista de Documents com campos úteis: texDesc, nomeContrib, nomeFant, produtoPadronizado, vlrItem, estabelecimento
     * datePrefix = "YYYY-MM-DD"
     * limit = 0 -> sem limite
     */
    public List<Document> findItemsByDatePrefix(String datePrefix, int limit) {
        MongoDatabase db = mongoClient.getDatabase("meu_banco");
        MongoCollection<Document> coll = db.getCollection("jsons");

        Pattern p = Pattern.compile("^" + Pattern.quote(datePrefix));

        List<Document> out = new ArrayList<>();

        // 1) itens
        {
            Bson unwind = Aggregates.unwind("$itens");
            Bson match = Aggregates.match(Filters.regex("itens.dthEmiNFe", p));
            Bson project = Aggregates.project(Projections.fields(
                    Projections.computed("texDesc", "$itens.texDesc"),
                    Projections.computed("nomeContrib", "$itens.estabelecimento.nomeContrib"),
                    Projections.computed("nomeFant", "$itens.estabelecimento.nomeFant"),
                    Projections.computed("produtoPadronizado", "$itens.produtoPadronizado"),
                    Projections.computed("gtin", "$itens.gtin"),
                    Projections.computed("vlrItem", "$itens.vlrItem"),
                    Projections.computed("estabelecimento", "$itens.estabelecimento")
            ));

            List<Bson> pipeline = limit > 0 ?
                Arrays.asList(unwind, match, project, Aggregates.limit(limit)) :
                Arrays.asList(unwind, match, project);

            coll.aggregate(pipeline).into(out);
        }

        // 2) itensComAgrupamento (se houver) — junta resultados
        {
            Bson unwind = Aggregates.unwind("$itensComAgrupamento");
            Bson match = Aggregates.match(Filters.regex("itensComAgrupamento.dthEmiNFe", p));
            Bson project = Aggregates.project(Projections.fields(
                    Projections.computed("texDesc", "$itensComAgrupamento.texDesc"),
                    Projections.computed("nomeContrib", "$itensComAgrupamento.estabelecimento.nomeContrib"),
                    Projections.computed("nomeFant", "$itensComAgrupamento.estabelecimento.nomeFant"),
                    Projections.computed("produtoPadronizado", "$itensComAgrupamento.produtoPadronizado"),
                    Projections.computed("gtin", "$itensComAgrupamento.gtin"),
                    Projections.computed("vlrItem", "$itensComAgrupamento.vlrItem"),
                    Projections.computed("estabelecimento", "$itensComAgrupamento.estabelecimento")
            ));

            List<Bson> pipeline = limit > 0 ?
                Arrays.asList(unwind, match, project, Aggregates.limit(limit)) :
                Arrays.asList(unwind, match, project);

            coll.aggregate(pipeline).into(out);
        }

        return out;
    }
}