package org.acme.externo;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/API/ConsultaMenorPreco")
@RegisterRestClient(configKey = "sefaz-api")
public interface SefazClient {

    @POST
    @Path("/logon")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Response login(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("grant_type") String grantType,
            @FormParam("private_key") String privateKey
    );

    @GET
    @Path("/api/v1/Item/PorGtin")
    @Produces(MediaType.APPLICATION_JSON)
    String consultaItem(
            @QueryParam("gtin") String gtin,
            @QueryParam("Longitude") double longitude,
            @QueryParam("Latitude") double latitude,
            @QueryParam("NroKmDistancia") int nroKmDistancia,
            @QueryParam("NroDiaPrz") int nroDiaPrz,
            @HeaderParam("Authorization") String authorization
    );
}
