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
     * Retorna lista de Documents com campos: texDesc, nomeContrib, vlrItem
     * datePrefix = "YYYY-MM-DD"
     * limit = 0 -> sem limite
     */
    public List<Document> findItemsByDatePrefix(String datePrefix, int limit) {
        MongoDatabase db = mongoClient.getDatabase("meu_banco");
        MongoCollection<Document> coll = db.getCollection("jsons");

        Bson unwind = Aggregates.unwind("$itens");
        Pattern p = Pattern.compile("^" + Pattern.quote(datePrefix));
        Bson match = Aggregates.match(Filters.regex("itens.dthEmiNFe", p));
        Bson project = Aggregates.project(Projections.fields(
                Projections.computed("texDesc", "$itens.texDesc"),
                Projections.computed("nomeContrib", "$itens.estabelecimento.nomeContrib"),
                Projections.computed("vlrItem", "$itens.vlrItem")
        ));

        List<Bson> pipeline;
        if (limit > 0) {
            pipeline = Arrays.asList(unwind, match, project, Aggregates.limit(limit));
        } else {
            pipeline = Arrays.asList(unwind, match, project);
        }

        List<Document> out = new ArrayList<>();
        coll.aggregate(pipeline).into(out);
        return out;
    }
}
