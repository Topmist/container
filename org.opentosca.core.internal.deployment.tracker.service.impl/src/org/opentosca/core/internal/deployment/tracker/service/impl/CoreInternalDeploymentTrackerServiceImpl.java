package org.opentosca.core.internal.deployment.tracker.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.eclipse.osgi.framework.console.CommandProvider;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.opentosca.core.internal.deployment.tracker.service.ICoreInternalDeploymentTrackerService;
import org.opentosca.core.model.csar.id.CSARID;
import org.opentosca.core.model.deployment.ia.IADeploymentInfo;
import org.opentosca.core.model.deployment.ia.IADeploymentState;
import org.opentosca.core.model.deployment.plan.PlanDeploymentInfo;
import org.opentosca.core.model.deployment.plan.PlanDeploymentState;
import org.opentosca.core.model.deployment.process.DeploymentProcessInfo;
import org.opentosca.core.model.deployment.process.DeploymentProcessState;
import org.opentosca.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the deployment process of CSAR files, Implementations Artifacts and
 * Plans by providing methods for storing and getting it deployment states. <br />
 * It is used by OpenTOSCA Control to allowing only a subset of all provided
 * operations in a certain deployment state of a CSAR file. <br />
 * <br />
 * Copyright 2012 IAAS University of Stuttgart <br />
 * <br />
 * 
 * @author Rene Trefft - trefftre@studi.informatik.uni-stuttgart.de
 * @author Matthias Fetzer - fetzerms@studi.informatik.uni-stuttgart.de
 * 
 */
public class CoreInternalDeploymentTrackerServiceImpl implements ICoreInternalDeploymentTrackerService, CommandProvider {
	
	private final static Logger LOG = LoggerFactory.getLogger(CoreInternalDeploymentTrackerServiceImpl.class);
	
	/**
	 * JDBC-URL to the database that stores the deployment informations. It will
	 * be created if it does not exist yet.
	 * 
	 * @see org.opentosca.settings.Settings
	 */
	private final String DB_LOCATION = Settings.getSetting("databaseLocation");
	private final String DB_URL = "jdbc:derby:" + this.DB_LOCATION + ";create=true";
	
	/**
	 * JPA EntityManager and Factory. These variables are global, as we do not
	 * want to create a new EntityManager / Factory each time a method is
	 * called.
	 */
	private EntityManagerFactory emf;
	private EntityManager em;
	
	
	public CoreInternalDeploymentTrackerServiceImpl() {
	}
	
	/**
	 * Initializes JPA.
	 */
	private void init() {
		if (this.emf == null) {
			Map<String, String> properties = new HashMap<String, String>();
			properties.put(PersistenceUnitProperties.JDBC_URL, this.DB_URL);
			this.emf = Persistence.createEntityManagerFactory("DeploymentTracker", properties);
			this.em = this.emf.createEntityManager();
		}
	}
	
	/**
	 * Destructor. This method is called when the garbage collector destroys the
	 * class. We will then manually close the EntityManager / Factory and pass
	 * control back.
	 */
	@Override
	protected void finalize() throws Throwable {
		this.em.close();
		this.emf.close();
		super.finalize();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean storeDeploymentState(CSARID csarID, DeploymentProcessState deploymentState) {
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for CSAR \"{}\"...", deploymentState, csarID);
		this.em.getTransaction().begin();
		// check if deployment state for this CSAR already exists
		DeploymentProcessInfo deploymentInfo = this.getDeploymentProcessInfo(csarID);
		if (deploymentInfo != null) {
			//CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Deployment state for CSAR \"{}\" already exists. Existent state will be overwritten!", csarID);
			deploymentInfo.setDeploymentProcessState(deploymentState);
			this.em.persist(deploymentInfo);
		} else {
			//CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Deployment state for CSAR \"{}\" did not already exist.", csarID);
			this.em.persist(new DeploymentProcessInfo(csarID, deploymentState));
		}
		this.em.getTransaction().commit();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for CSAR \"{}\" completed.", deploymentState, csarID);
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public DeploymentProcessState getDeploymentState(CSARID csarID) {
		
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Retrieving deployment state for CSAR \"{}\"...", csarID);
		this.init();
		DeploymentProcessInfo info = this.getDeploymentProcessInfo(csarID);
		
		DeploymentProcessState deploymentState = null;
		
		if (info == null) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.error("No deployment state for CSAR \"{}\" stored!", csarID);
		} else {
			deploymentState = info.getDeploymentProcessState();
			CoreInternalDeploymentTrackerServiceImpl.LOG.info("Deployment state of CSAR \"{}\": {}.", csarID, deploymentState);
		}
		
		return deploymentState;
		
	}
	
	@Override
	public void deleteDeploymentState(CSARID csarID) {
		CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Retrieving DeploymentProcessInfo for {}", csarID);
		DeploymentProcessInfo info = this.getDeploymentProcessInfo(csarID);
		if (info != null) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Beginning Transaction for removing DeploymentProcessInfo {}", csarID);
			this.em.getTransaction().begin();
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Removing DeploymentProcessInfo {}", csarID);
			
			Query queryRestEndpoints = this.em.createQuery("DELETE FROM DeploymentProcessInfo e where e.csarID = :csarID");
			queryRestEndpoints.setParameter("csarID", csarID);
			
			// this.em.remove(info);
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Commiting Transaction");
			this.em.getTransaction().commit();
		}
	}
	
	/**
	 * Gets the deployment process information of a CSAR file.
	 * 
	 * @param csarID that uniquely identifies a CSAR file
	 * @return the deployment process information, if the CSAR with
	 *         <code>csarID</code> exists, otherwise <code>null</code>
	 */
	private DeploymentProcessInfo getDeploymentProcessInfo(CSARID csarID) {
		
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Retrieving deployment process info for CSAR \"{}\"...", csarID);
		Query getDeploymentProcessInfo = this.em.createNamedQuery(DeploymentProcessInfo.getDeploymentProcessInfoByCSARID).setParameter("csarID", csarID);
		
		@SuppressWarnings("unchecked")
		List<DeploymentProcessInfo> results = getDeploymentProcessInfo.getResultList();
		
		if (results.isEmpty()) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("No deployment process info for CSAR \"{}\" stored.", csarID);
			return null;
		} else {
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Deployment process info for CSAR \"{}\" exists.", csarID);
			return results.get(0);
		}
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean storeIADeploymentInfo(IADeploymentInfo iaDeploymentInfo) {
		
		this.init();
		
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for IA \"{}\" of CSAR \"{}\"...", new Object[] {iaDeploymentInfo.getDeploymentState(), iaDeploymentInfo.getRelPath(), iaDeploymentInfo.getCSARID()});
		this.em.getTransaction().begin();
		
		// check if deployment info for this IA already exists
		IADeploymentInfo storedIA = this.getIADeploymentInfo(iaDeploymentInfo.getCSARID(), iaDeploymentInfo.getRelPath());
		
		// deployment info already exists
		if (storedIA != null) {
			
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("IA deployment info for IA \"{}\" of CSAR \"{}\" already exists. Existent deployment info will be overwritten!", iaDeploymentInfo.getRelPath(), iaDeploymentInfo.getCSARID());
			
			IADeploymentState storedIADeployState = storedIA.getDeploymentState();
			IADeploymentState newIADeployState = iaDeploymentInfo.getDeploymentState();
			
			// if IA is deployed and will be now undeployed (deployment state
			// change to IA_UNDEPLOYING) reset the attempt counter to 0
			if (storedIADeployState.equals(IADeploymentState.IA_DEPLOYED) && newIADeployState.equals(IADeploymentState.IA_UNDEPLOYING)) {
				CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Deployed IA \"{}\" of CSAR \"{}\" is now undeploying. Attempt count will be reseted.", iaDeploymentInfo.getRelPath(), iaDeploymentInfo.getCSARID());
				storedIA.setAttempt(0);
			}
			
			storedIA.setDeploymentState(newIADeployState);
			iaDeploymentInfo = storedIA;
			
		}
		
		// if IA is now deploying or undeploying (deployment state change to
		// IA_DEPLOYING / IA_UNDEPLOYING) increment attempt counter
		if (iaDeploymentInfo.getDeploymentState().equals(IADeploymentState.IA_DEPLOYING) || iaDeploymentInfo.getDeploymentState().equals(IADeploymentState.IA_UNDEPLOYING)) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("IA \"{}\" of CSAR \"{}\" is now deploying / undeploying. Increase attempt count.", iaDeploymentInfo.getRelPath(), iaDeploymentInfo.getCSARID());
			iaDeploymentInfo.setAttempt(iaDeploymentInfo.getAttempt() + 1);
		}
		
		this.em.persist(iaDeploymentInfo);
		this.em.getTransaction().commit();
		
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for IA \"{}\" of CSAR \"{}\" completed.", new Object[] {iaDeploymentInfo.getDeploymentState(), iaDeploymentInfo.getRelPath(), iaDeploymentInfo.getCSARID()});
		
		return true;
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public IADeploymentInfo getIADeploymentInfo(CSARID csarID, String iaRelPath) {
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Retrieving IA deployment info for IA \"{}\" of CSAR \"{}\"...", iaRelPath, csarID);
		Query getIADeploymentInfo = this.em.createNamedQuery(IADeploymentInfo.getIADeploymentInfoByCSARIDAndRelPath).setParameter("iaRelPath", iaRelPath).setParameter("csarID", csarID);
		@SuppressWarnings("unchecked")
		List<IADeploymentInfo> results = getIADeploymentInfo.getResultList();
		if (results.isEmpty()) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.error("No IA deployment info for IA \"{}\" of CSAR \"{}\" stored.", iaRelPath, csarID);
			return null;
		} else {
			CoreInternalDeploymentTrackerServiceImpl.LOG.info("IA deployment info for IA \"{}\" of CSAR \"{}\" exists.", iaRelPath, csarID);
			return results.get(0);
		}
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<IADeploymentInfo> getIADeploymentInfos(CSARID csarID) {
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Retrieving all IA deployment infos of CSAR \"{}\"...", csarID);
		ArrayList<IADeploymentInfo> results = new ArrayList<IADeploymentInfo>();
		Query getIADeploymentInfo = this.em.createNamedQuery(IADeploymentInfo.getIADeploymentInfoByCSARID).setParameter("csarID", csarID);
		@SuppressWarnings("unchecked")
		List<IADeploymentInfo> queryResults = getIADeploymentInfo.getResultList();
		for (IADeploymentInfo ia : queryResults) {
			results.add(ia);
		}
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("IA deployment infos of {} IA(s) of CSAR \"{}\" stored.", results.size(), csarID);
		return results;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean storePlanDeploymentInfo(PlanDeploymentInfo planDeploymentInfo) {
		this.init();
		
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for Plan \"{}\" of CSAR \"{}\"...", new Object[] {planDeploymentInfo.getDeploymentState(), planDeploymentInfo.getRelPath(), planDeploymentInfo.getCSARID()});
		
		this.em.getTransaction().begin();
		
		// check if deployment info for this Plan already exists
		PlanDeploymentInfo storedPlan = this.getPlanDeploymentInfo(planDeploymentInfo.getCSARID(), planDeploymentInfo.getRelPath());
		
		// deployment info already exists
		if (storedPlan != null) {
			
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Plan deployment info for Plan \"{}\" of CSAR \"{}\" already exists. Existent deployment info will be overwritten!", planDeploymentInfo.getRelPath(), planDeploymentInfo.getCSARID());
			
			PlanDeploymentState storedPlanDeployState = storedPlan.getDeploymentState();
			PlanDeploymentState newPlanDeployState = planDeploymentInfo.getDeploymentState();
			
			// if Plan is deployed and will be now undeployed (deployment state
			// change to PLAN_UNDEPLOYING) reset the attempt counter to 0
			if (storedPlanDeployState.equals(PlanDeploymentState.PLAN_DEPLOYED) && newPlanDeployState.equals(PlanDeploymentState.PLAN_UNDEPLOYING)) {
				CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Deployed Plan \"{}\" of CSAR \"{}\" is now undeploying. Attempt count will be reseted.", planDeploymentInfo.getRelPath(), planDeploymentInfo.getCSARID());
				storedPlan.setAttempt(0);
			}
			
			storedPlan.setDeploymentState(newPlanDeployState);
			planDeploymentInfo = storedPlan;
		}
		
		// if Plan is now deploying or undeploying (deployment state change to
		// PLAN_DEPLOYING / PLAN_UNDEPLOYING) increment attempt counter
		if (planDeploymentInfo.getDeploymentState().equals(PlanDeploymentState.PLAN_DEPLOYING) || planDeploymentInfo.getDeploymentState().equals(PlanDeploymentState.PLAN_UNDEPLOYING)) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.debug("Plan \"{}\" of CSAR \"{}\" is now deploying / undeploying. Increase attempt count.", planDeploymentInfo.getRelPath(), planDeploymentInfo.getCSARID());
			planDeploymentInfo.setAttempt(planDeploymentInfo.getAttempt() + 1);
		}
		
		this.em.persist(planDeploymentInfo);
		this.em.getTransaction().commit();
		
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Storing deployment state {} for Plan \"{}\" of CSAR \"{}\" completed.", new Object[] {planDeploymentInfo.getDeploymentState(), planDeploymentInfo.getRelPath(), planDeploymentInfo.getCSARID()});
		
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public PlanDeploymentInfo getPlanDeploymentInfo(CSARID csarID, String planRelPath) {
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Retrieving Plan deployment info for Plan \"{}\" of CSAR \"{}\"...", planRelPath, csarID);
		Query getPlanDeploymentInfo = this.em.createNamedQuery(PlanDeploymentInfo.getPlanDeploymentInfoByCSARIDAndRelPath).setParameter("csarID", csarID).setParameter("planRelPath", planRelPath);
		@SuppressWarnings("unchecked")
		List<PlanDeploymentInfo> results = getPlanDeploymentInfo.getResultList();
		if (results.isEmpty()) {
			CoreInternalDeploymentTrackerServiceImpl.LOG.error("No Plan deployment info for Plan \"{}\" of CSAR \"{}\" stored.", planRelPath, csarID);
			return null;
		} else {
			CoreInternalDeploymentTrackerServiceImpl.LOG.info("Plan deployment info for Plan \"{}\" of CSAR \"{}\" exists.", planRelPath, csarID);
			return results.get(0);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<PlanDeploymentInfo> getPlanDeploymentInfos(CSARID csarID) {
		this.init();
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Retrieving all Plan deployment infos of CSAR \"{}\"...", csarID);
		ArrayList<PlanDeploymentInfo> results = new ArrayList<PlanDeploymentInfo>();
		Query getIADeploymentInfo = this.em.createNamedQuery(PlanDeploymentInfo.getPlanDeploymentInfoByCSARID).setParameter("csarID", csarID);
		@SuppressWarnings("unchecked")
		List<PlanDeploymentInfo> queryResults = getIADeploymentInfo.getResultList();
		for (PlanDeploymentInfo ia : queryResults) {
			results.add(ia);
		}
		CoreInternalDeploymentTrackerServiceImpl.LOG.info("Plan deployment infos of {} Plan(s) of CSAR \"{}\" stored.", results.size(), csarID);
		return results;
	}
	
	/**
	 * Prints the available OSGi commands.
	 */
	@Override
	public String getHelp() {
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean storeIADeploymentInfo(CSARID csarID, String iaRelPath, IADeploymentState iaDeploymentState) {
		IADeploymentInfo iaDeploymentInfo = new IADeploymentInfo(csarID, iaRelPath, iaDeploymentState);
		this.storeIADeploymentInfo(iaDeploymentInfo);
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean storePlanDeploymentInfo(CSARID csarID, String planRelPath, PlanDeploymentState planDeploymentState) {
		PlanDeploymentInfo planDeploymentInfo = new PlanDeploymentInfo(csarID, planRelPath, planDeploymentState);
		this.storePlanDeploymentInfo(planDeploymentInfo);
		return true;
	}
	
}
