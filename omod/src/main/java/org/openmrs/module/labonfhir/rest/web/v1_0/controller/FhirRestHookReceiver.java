package org.openmrs.module.labonfhir.rest.web.v1_0.controller;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openmrs.module.labonfhir.api.service.FetchTaskUpdatesService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/taskRequestUpdate")
public class FhirRestHookReceiver extends BaseRestController {
	
	@Autowired
	private FetchTaskUpdatesService fetchTaskUpdatesService;
	
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public Object fetchTaskUpdates(HttpServletRequest request, HttpServletResponse response)
	        throws ResponseException, JsonParseException, JsonMappingException, IOException {
		
		fetchTaskUpdatesService.fetchTaskUpdates(null);
		
		return RestUtil.updated(response, "");
	}
	
	@RequestMapping(value = "/{uuid}", method = RequestMethod.POST)
	@ResponseBody
	public Object fetchTaskUpdates(@PathVariable UUID uuid, HttpServletRequest request, HttpServletResponse response)
	        throws ResponseException, JsonParseException, JsonMappingException, IOException {
		
		fetchTaskUpdatesService.fetchTaskUpdates(uuid);
		
		return RestUtil.updated(response, "");
	}
}
