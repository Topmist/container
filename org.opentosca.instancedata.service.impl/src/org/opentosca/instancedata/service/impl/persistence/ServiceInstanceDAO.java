/**
 * 
 */
package org.opentosca.instancedata.service.impl.persistence;

import java.net.URI;
import java.util.List;

import javax.persistence.Query;
import javax.xml.namespace.QName;

import org.opentosca.model.instancedata.IdConverter;
import org.opentosca.model.instancedata.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Access Object for ServiceInstances
 * 
 * @author Marcus Eisele (marcus.eisele@gmail.com)
 * 
 */
public class ServiceInstanceDAO extends AbstractDAO {
	
	// Logging
	private final static Logger LOG = LoggerFactory
			.getLogger(ServiceInstanceDAO.class);
	
	public void deleteServiceInstance(ServiceInstance si) {
		this.init();
		this.em.getTransaction().begin();
		this.em.remove(si);
		this.em.getTransaction().commit();
		ServiceInstanceDAO.LOG.debug("Deleted ServiceInstance with ID: "
				+ si.getServiceInstanceID());
		
		
	}
	
	public void storeServiceInstance(ServiceInstance serviceInstance) {
		this.init();
		
		this.em.getTransaction().begin();
		this.em.persist(serviceInstance);
		this.em.getTransaction().commit();
		ServiceInstanceDAO.LOG.debug("Stored ServiceInstance: "
				+ serviceInstance.getServiceTemplateName() + " successful!");
		
	}
	
	public List<ServiceInstance> getServiceInstances(URI serviceInstanceID,
			String serviceTemplateName, QName serviceTemplateID) {
		this.init();
		
		/**
		 * Create Query to retrieve ServiceInstances
		 * 
		 * @see ServiceInstance#getServiceInstances
		 */
		Query getServiceInstancesQuery = this.em
				.createNamedQuery(ServiceInstance.getServiceInstances);

		Integer internalID = null;
		if (serviceInstanceID != null) {
			internalID = IdConverter.serviceInstanceUriToID(serviceInstanceID);
		}
		
		String serviceTemplateID_String = null;
		if (serviceTemplateID != null) {
			serviceTemplateID_String = serviceTemplateID.toString();
		}
		
		// Set Parameters for the Query
		// getServiceInstancesQuery.setParameter("param", param);
		getServiceInstancesQuery.setParameter("id", internalID);
		getServiceInstancesQuery.setParameter("serviceTemplateName",
				serviceTemplateName);
		getServiceInstancesQuery.setParameter("serviceTemplateID",
				serviceTemplateID_String);
		
		// getServiceInstancesQuery.setParameter("serviceTemplateID",
		// serviceTemplateID);
		// getServiceInstancesQuery.setParameter("serviceTemplateNamespace",
		// serviceTemplateNamespace);
		// Get Query-Results (ServiceInstances) and add them to the result list.
		@SuppressWarnings("unchecked")
		// Result can only be a ServiceInstance
		List<ServiceInstance> queryResults = getServiceInstancesQuery
		.getResultList();
		return queryResults;
		
	}
	
}
