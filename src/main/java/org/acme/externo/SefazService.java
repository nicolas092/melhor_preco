package org.acme.externo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class SefazService {

    @Inject
    @RestClient
    SefazClient sefazClient;

    public String consultarPreco(String usuario, String senha) {
        var response = sefazClient.login(
                usuario,
                senha,
                "password",
                "68cdf21a37c40f9bf7eaa0bf9ac934e3"
        );

        // aqui o cookie real precisa ser capturado via header HTTP (não vem no JSON)
        // por padrão, isso só funciona manualmente com RestClientBuilder, se quiser seguir 100% REST Client anotado, tem que criar um ClientRequestFilter ou usar o Apache HttpClient

        // Exemplo de chamada direta
        String cookie = "AffinityMPRS=17862fa870521d5f692170679e247f5758f70826b875141b72ccded7abf5121c"; // substituir por valor real
        return sefazClient.consultaItem(
                "8410221110150", -51.1062926, -30.0668232, 10, 3, cookie
        );
    }
}
