package org.openmrs.module.labonfhir.api.fhir;

public class OrderCreationException extends Exception {
	
	public OrderCreationException(String message) {
		super(message);
	}
	
	public OrderCreationException(String message, Throwable cause) {
		super(message, cause);
	}
}
