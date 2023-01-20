# OpenMRS Laboratory Workflows Module

## Overview
This module provides support for FHIR-based communication between OpenMRS and a FHIR-enabled 
Laboratory Information System (LIS) like OpenELIS.

The laboratory workflows supported by this module are based on 
[OpenHIE specifications](https://guides.ohie.org/arch-spec/introduction/laboratory-work-flows) and the 
[FHIR Workflow Module](https://build.fhir.org/workflow-module.html) resources and communication patterns.  

The technical and functional specification for the workflows supported by this module can be found in the following
[FHIR Implementation Guide](https://build.fhir.org/ig/FHIR/ig-guidance/): https://i-tech-uw.github.io/laboratory-workflows-ig

## Building and Deploying

1. Build and create `.omod` file:
```shell
mvn clean package
```

2. Use omod file as part of an OpenMRS distribution. 

## Usage
To use this Module , first Load the [Order Entry UI module](https://github.com/openmrs/openmrs-module-orderentryui)  and the [Order Entry OWA](https://github.com/openmrs/openmrs-owa-orderentry) for creating the Orders With in OpenMRS .    

see more [instructions](https://wiki.openmrs.org/display/projects/Order+Entry+UI+Administrator+Guide) for Configuring the OWA and the [User Guide](https://wiki.openmrs.org/display/projects/Order+Entry+UI+End+User+Guide+for+Creating+Drug+Orders)

Note: Ensure to Configure these [Global Properties](https://github.com/openmrs/openmrs-owa-orderentry#usage)(Settings) Required by the OWA to function

The Lab on FHir Module only generates the Lab WorkFlow Fhir Bundle When an order is created in OpenMRS and Pushes the Lab Fhir Bundle  to an external LIS system ,and Polls for Completed Orders from the LIS

see more about the [EMR-LIS FHIR Workflow](https://wiki.openmrs.org/display/projects/Lab+Integration+Workflow)

Configure the Following Global Properties Required By the Lab on Fhir Module
* `labonfhir.lisUrl` ,The URL for the OpenELIS system to communicate with
* `labonfhir.lisUserUuid` ,UUID for the service user that represents OpenELIS
* `labonfhir.truststorePath` , Path to truststore for HttpClient
* `labonfhir.truststorePass` , Truststore password
* `labonfhir.keystorePath` , Path to keystore for HttpClient
* `labonfhir.keystorePass` , Keystore password
* `labonfhir.activateFhirPush` ,Switches on/off the FHIR Push Functionality with in the module to an external LIS
* `labonfhir.authType` , The HTTP Auth type to support .Either SSL or Basic
* `labonfhir.userName`  ,User name for HTTP Basic Auth with the LIS
* `labonfhir.password`  ,Password for HTTP Basic Auth with the LIS


