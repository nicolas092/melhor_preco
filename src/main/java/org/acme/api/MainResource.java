package org.acme.api;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.views.LogonResource;

@Path("/")
public class MainResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index() {
        return LogonResource.Templates.logon("Logue novamente").render();
    }
}