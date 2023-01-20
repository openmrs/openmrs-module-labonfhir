package org.openmrs.module.labonfhir;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openmrs.module.labonfhir.LabOnFhirConfig.AuthType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Configuration
public class FhirConfig {
    
    @Autowired
    private LabOnFhirConfig config;
    
    @Autowired
    @Qualifier("fhirR4")
    private FhirContext fhirContext;
    
    private void configureFhirHttpClient(CloseableHttpClient httpClient) {
        IRestfulClientFactory clientFactory = new ApacheRestfulClientFactory(this.fhirContext);
        clientFactory.setHttpClient(httpClient);
        fhirContext.setRestfulClientFactory(clientFactory);
    }
    
    @Bean
    public IGenericClient getFhirClient() throws Exception {
        if (config.getAuthType().equals(AuthType.SSL)) {
            CloseableHttpClient client = HttpClientBuilder.create().setSSLSocketFactory(config.sslConnectionSocketFactory()).build();
            configureFhirHttpClient(client);
        }

        IGenericClient fhirClient = fhirContext.newRestfulGenericClient(config.getLisUrl());
        if (config.getAuthType().equals(AuthType.BASIC)) {
            BasicAuthInterceptor authInterceptor = new BasicAuthInterceptor(config.getLisUserName(),
                    config.getLisPassword());
            fhirClient.registerInterceptor(authInterceptor);
        }
        return fhirClient;
    }
    
}
