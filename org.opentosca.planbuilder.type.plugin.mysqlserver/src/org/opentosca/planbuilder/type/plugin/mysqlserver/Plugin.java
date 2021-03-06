/**
 *
 */
package org.opentosca.planbuilder.type.plugin.mysqlserver;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.opentosca.planbuilder.model.tosca.AbstractNodeTemplate;
import org.opentosca.planbuilder.model.tosca.AbstractRelationshipTemplate;
import org.opentosca.planbuilder.plugins.IPlanBuilderTypePlugin;
import org.opentosca.planbuilder.plugins.context.TemplatePlanContext;
import org.opentosca.planbuilder.type.plugin.mysqlserver.handler.Handler;

/**
 * Copyright 2014 IAAS University of Stuttgart <br>
 * <br>
 * 
 * @author nyu
 * 
 */
public class Plugin implements IPlanBuilderTypePlugin {
	
	private static final QName mySqlServerNodeType = new QName("http://docs.oasis-open.org/tosca/ns/2011/12/ToscaSpecificTypes", "MySQL");
	private static final QName ubuntuNodeTypeOpenTOSCAPlanBuilder = new QName("http://opentosca.org/types/declarative", "Ubuntu");
	private final static QName ubuntu1310ServerNodeType = new QName("http://opentosca.org/types/declarative", "Ubuntu-13.10-Server");
	private Handler handler;
	
	
	public Plugin() {
		try {
			this.handler = new Handler();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public String getID() {
		return "OpenTOSCA PlanBuilder Type Plugin MySQL Server";
	}
	
	@Override
	public boolean handle(TemplatePlanContext templateContext) {
		return this.handler.handle(templateContext);
	}
	
	@Override
	public boolean canHandle(AbstractNodeTemplate nodeTemplate) {
		// check first the nodeTemplate
		if (this.isCompatibleMySQLServerNodeType(nodeTemplate.getType().getId())) {
			// check whether the mysql server is connected to a Ubuntu
			// NodeTemplate
			for (AbstractRelationshipTemplate relation : nodeTemplate.getOutgoingRelations()) {
				if (this.isCompatibleUbuntuNodeType(relation.getTarget().getType().getId())) {
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public boolean canHandle(AbstractRelationshipTemplate relationshipTemplate) {
		// we can't handle relationshipTemplates
		return false;
	}
	
	/**
	 * Checks whether the given QName represents a MySQL Server NodeType
	 * understood by this plugin
	 * 
	 * @param nodeTypeId a QName
	 * @return true iff the QName represents a MySQL NodeType
	 */
	public static boolean isCompatibleMySQLServerNodeType(QName nodeTypeId) {
		return Plugin.mySqlServerNodeType.toString().equals(nodeTypeId.toString());
	}
	
	/**
	 * Checks whether the given QName represents a Ubuntu OS NodeType
	 * 
	 * @param nodeTypeId a QName
	 * @return true iff the QName represents a Ubuntu NodeType
	 */
	public static boolean isCompatibleUbuntuNodeType(QName nodeTypeId) {
		if (nodeTypeId.toString().equals(Plugin.ubuntuNodeTypeOpenTOSCAPlanBuilder.toString())) {
			return true;
		}
		
		if (nodeTypeId.toString().equals(Plugin.ubuntu1310ServerNodeType.toString())) {
			return true;
		}
		return false;
	}
	
}
