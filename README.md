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
