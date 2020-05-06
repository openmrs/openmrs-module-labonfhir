package org.openmrs.module.labonfhir;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.function.Predicate;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;

import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirPractitionerService;
import org.openmrs.module.labonfhir.api.scheduler.FetchTaskUpdates;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class ISantePlusLabOnFHIRConfig implements ApplicationContextAware {
	// https://wiki.openmrs.org/display/docs/Setting+and+Reading+Global+Properties?src=contextnavpagetreemode
	public static final String GP_TEST_ORDER_CONCEPT_UUID = "labonfhir.testsOrderedConceptUuid";

	public static final String GP_ORDER_DESTINATION_CONCEPT_UUID = "labonfhir.orderDestinationConceptUuid";

	public static final String GP_OPENELIS_URL = "labonfhir.openElisUrl";

	public static final String GP_OPENELIS_USER_UUID = "labonfhir.openelisUserUuid";

	public static final String GP_KEYSTORE_PATH = "labonfhir.keystorePath";

	public static final String GP_KEYSTORE_PASS = "labonfhir.keystorePass";

	public static final String GP_TRUSTSTORE_PATH = "labonfhir.truststorePath";

	public static final String GP_TRUSTSTORE_PASS = "labonfhir.truststorePass";

	private static final String TEMP_DEFAULT_OPENELIS_URL = "https://testapi.openelisci.org:8444/hapi-fhir-jpaserver/fhir";

	private static final String GP_DIAGNOSTIC_REPORT_CONCEPT_UUID = "labonfhir.diagnosticReportConceptUuid";

	private static Log log = LogFactory.getLog(ISantePlusLabOnFHIRConfig.class);

	private static ApplicationContext applicationContext;

	@Autowired
	@Qualifier("adminService")
	AdministrationService administrationService;

	@Autowired
	FhirPractitionerService practitionerService;

	@Bean
	public CloseableHttpClient httpClient() throws Exception {
		CloseableHttpClient client = HttpClientBuilder.create().setSSLSocketFactory(sslConnectionSocketFactory()).build();

		// return HttpClients.createSystem();
		return client;
	}

	public SSLConnectionSocketFactory sslConnectionSocketFactory() throws Exception {
		return new SSLConnectionSocketFactory(sslContext());
	}

	public SSLContext sslContext() throws Exception {
		SSLContextBuilder sslContextBuilder =  SSLContextBuilder.create();
		try {
			if(administrationService.getGlobalProperty(GP_KEYSTORE_PATH) != null && !administrationService.getGlobalProperty(GP_KEYSTORE_PATH).isEmpty()) {
				String keyPassword = administrationService.getGlobalProperty(GP_KEYSTORE_PASS);
				File truststoreFile = new File(administrationService.getGlobalProperty(GP_TRUSTSTORE_PATH));
				String truststorePassword = administrationService.getGlobalProperty(GP_TRUSTSTORE_PASS);

				KeyStore keystore = loadKeystore(administrationService.getGlobalProperty(GP_KEYSTORE_PATH));

				sslContextBuilder.loadKeyMaterial(keystore, keyPassword.toCharArray())
						.loadTrustMaterial(truststoreFile, truststorePassword.toCharArray());
			}
		} catch (Exception e) {
			log.error("ERROR creating SSL Context: " + e.toString() + getStackTrace(e));
		}

		return sslContextBuilder.build();
	}

	public String getOpenElisUrl() {
		//return GP_OPENELIS_URL
		String url = administrationService.getGlobalProperty(GP_OPENELIS_URL);

		if(StringUtils.isBlank(url)) {
			url = TEMP_DEFAULT_OPENELIS_URL;
		}

		return url;
	}

	public String getTestOrderConceptUuid() {
		return administrationService.getGlobalProperty(GP_TEST_ORDER_CONCEPT_UUID);
	}

	public String getDiagnosticReportConceptUuid() {
		return administrationService.getGlobalProperty(GP_DIAGNOSTIC_REPORT_CONCEPT_UUID);
	}

	public String getOrderDestinationConceptUuid() {
		return administrationService.getGlobalProperty(GP_ORDER_DESTINATION_CONCEPT_UUID);
	}

	public String getOpenElisUserUuid() {
		return administrationService.getGlobalProperty(GP_OPENELIS_USER_UUID);
	}

	public Predicate<Obs> isTestOrder() {
		final String testOrderConceptUuid = getTestOrderConceptUuid();
		return o -> testOrderConceptUuid.equals(o.getConcept().getUuid());
	}

	public boolean isOpenElisEnabled() {
		return StringUtils.isNotBlank(getOpenElisUrl());
	}

	public Practitioner getOpenElisPractitioner() {
		return practitionerService.getPractitionerByUuid(getOpenElisUserUuid());
	}

	private KeyStore loadKeystore(String filePath) {
		InputStream is = null;
		KeyStore keystore = null;

		try {
			File file = new File(administrationService.getGlobalProperty(GP_KEYSTORE_PATH));
			is = new FileInputStream(file);
			keystore = KeyStore.getInstance(KeyStore.getDefaultType());

			String password = administrationService.getGlobalProperty(GP_KEYSTORE_PASS);

			keystore.load(is, password.toCharArray());

			Enumeration<String> enumeration = keystore.aliases();
			while(enumeration.hasMoreElements()) {
				String alias = enumeration.nextElement();
				log.info("alias name: " + alias);
				Certificate certificate = keystore.getCertificate(alias);
				log.info(certificate.toString());
			}

		} catch (java.security.cert.CertificateException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(null != is)
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}

		return keystore;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
