package org.opentosca.planbuilder.plugins;

import org.opentosca.planbuilder.model.tosca.AbstractNodeTemplate;
import org.opentosca.planbuilder.model.tosca.AbstractRelationshipTemplate;
import org.opentosca.planbuilder.plugins.context.TemplatePlanContext;

/**
 * <p>
 * This interface should be implemented by Plugins which are PostPhasePlugins.
 * PostPhasePlugins are used to update data outside of the BuildPlan, like
 * Databases
 * </p>
 * Copyright 2013 IAAS University of Stuttgart <br>
 * <br>
 * 
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 * 
 */
public interface IPlanBuilderPostPhasePlugin extends IPlanBuilderPlugin {
	
	/**
	 * When this method is called the Plugin should fetch relevant runtime data
	 * inside the BuildPlan of the given NodeTemplate and send it to the
	 * Component it belongs to
	 * 
	 * @param context a TemplatePlanContext for accessing data inside the
	 *            BuildPlan
	 * @param nodeTemplate the NodeTemplate the plugin should handle
	 * @return true if generating the Fragment of this Plugin was successful,
	 *         else false
	 */
	public boolean handle(TemplatePlanContext context, AbstractNodeTemplate nodeTemplate);
	
	/**
	 * When this method is called the Plugin should fetch relevant runtime data
	 * inside the BuildPlan of the given RelationshipTemplate and send it to the
	 * Component it belongs to
	 * 
	 * @param context a TemplatePlanContext for accessing data inside the
	 *            BuildPlan
	 * @param relationshipTemplate the RelationshipTemplate the plugin should
	 *            handle
	 * @return true if generating the Fragment of this Plugin was successful,
	 *         else false
	 */
	public boolean handle(TemplatePlanContext context, AbstractRelationshipTemplate relationshipTemplate);
	
	/**
	 * Evaluates whether the given NodeTemplate can be handled by this post
	 * phase plugin.
	 * 
	 * @param nodeTemplate An AbstractNodeTemplate
	 * @return true iff this plugin can handle the given nodeTemplate
	 */
	public boolean canHandle(AbstractNodeTemplate nodeTemplate);
	
	/**
	 * Evaluates whether the given RelationshipTemplate can be handled by this
	 * post phase plugin.
	 * 
	 * @param relationshipTemplate An AbstractRelationshipTemplate
	 * @return true iff this plugin can handle the given relationshipTemplate
	 */
	public boolean canHandle(AbstractRelationshipTemplate relationshipTemplate);
}
