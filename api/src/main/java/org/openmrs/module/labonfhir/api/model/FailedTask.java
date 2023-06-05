package org.openmrs.module.labonfhir.api.model;

import org.openmrs.BaseOpenmrsData;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "failed_task")
public class FailedTask extends BaseOpenmrsData {

    @Id
	@GeneratedValue
	@Column(name = "id")
	private Integer id;

    @Column(name = "task_uuid", length = 255)
	private String taskUuid;

	@Column(name = "error", length = 255)
	private String error;
	
	@Column(name = "is_sent")
	private boolean isSent;

    @Override
    public Integer getId() {
        return id;
    }
    @Override
    public void setId(Integer id) {
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public boolean isSent() {
        return isSent;
    }
    
    public void setIsSent(boolean isSent) {
        this.isSent = isSent;
    }
    
    public String getTaskUuid() {
        return taskUuid;
    }
    
    public void setTaskUuid(String taskUuid) {
        this.taskUuid = taskUuid;
    }
}
