package org.openmrs.module.labonfhir.web.resources;

import java.util.List;

import org.openmrs.api.context.Context;
import org.openmrs.module.labonfhir.api.model.FailedTask;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DataDelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;

@Resource(name = RestConstants.VERSION_1 + "/failedtask", supportedClass = FailedTask.class, supportedOpenmrsVersions = {
        "2.*", "3.*" })
public class FailedTaskResource extends DataDelegatingCrudResource<FailedTask> {
    
    LabOnFhirService labOnFhirService = Context.getService(LabOnFhirService.class);
    
    @Override
    public FailedTask newDelegate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'newDelegate'");
    }
    
    @Override
    public FailedTask save(FailedTask delegate) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }
    
    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("display");
        description.addProperty("taskUuid");
        description.addProperty("error");
        description.addProperty("isSent");
        description.addProperty("uuid");
        description.addSelfLink();
        return description;
    }
    
    @PropertyGetter("display")
    public String getDisplayString(FailedTask delegate) {
        return "Task Uuid : " + delegate.getTaskUuid() + " Is Sent : " + delegate.getIsSent();
    }
    
    @Override
    public FailedTask getByUniqueId(String uniqueId) {
        return labOnFhirService.getFailedTaskByUuid(uniqueId);
    }
    
    @Override
    protected void delete(FailedTask delegate, String reason, RequestContext context) throws ResponseException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'delete'");
    }
    
    @Override
    public void purge(FailedTask delegate, RequestContext context) throws ResponseException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'purge'");
    }
    
    @Override
    protected NeedsPaging<FailedTask> doSearch(RequestContext context) {
        Boolean isSent = Boolean.valueOf(context.getParameter("sent"));
        List<FailedTask> tasks = labOnFhirService.getAllFailedTasks(isSent);
        return new NeedsPaging<FailedTask>(tasks, context);
    }
    
    @Override
    public PageableResult doGetAll(RequestContext context) {
        List<FailedTask> tasks = labOnFhirService.getAllFailedTasks(null);
        return new NeedsPaging<FailedTask>(tasks, context);
    }
    
}
