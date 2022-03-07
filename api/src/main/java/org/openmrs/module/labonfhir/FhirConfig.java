package org.openmrs.module.labonfhir;

import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Configuration
public class FhirConfig {
    
    @Autowired
    private LabOnFhirConfig config;
    
    @Autowired
    CloseableHttpClient httpClient;
    
    @Autowired
    @Qualifier("fhirR4")
    private FhirContext fhirContext;
    
    private void configureFhirHttpClient() {
        IRestfulClientFactory clientFactory = new ApacheRestfulClientFactory(this.fhirContext);
        clientFactory.setHttpClient(httpClient);
        fhirContext.setRestfulClientFactory(clientFactory);
    }
    
    @Bean
    public IGenericClient getFhirClient() {
        configureFhirHttpClient();
        IGenericClient fhirClient = fhirContext.newRestfulGenericClient(config.getOpenElisUrl());
        return fhirClient;
    }
    
}
