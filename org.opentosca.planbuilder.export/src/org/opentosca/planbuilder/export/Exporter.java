package org.opentosca.planbuilder.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.apache.ode.schemas.dd._2007._03.TProvide;
import org.oasis_open.docs.tosca.ns._2011._12.Definitions;
import org.oasis_open.docs.tosca.ns._2011._12.ObjectFactory;
import org.oasis_open.docs.tosca.ns._2011._12.Plan;
import org.oasis_open.docs.tosca.ns._2011._12.TBoolean;
import org.oasis_open.docs.tosca.ns._2011._12.TExtensibleElements;
import org.oasis_open.docs.tosca.ns._2011._12.TParameter;
import org.oasis_open.docs.tosca.ns._2011._12.TPlan;
import org.oasis_open.docs.tosca.ns._2011._12.TPlans;
import org.oasis_open.docs.tosca.ns._2011._12.TServiceTemplate;
import org.opentosca.core.model.artifact.file.AbstractFile;
import org.opentosca.core.model.csar.CSARContent;
import org.opentosca.core.model.csar.id.CSARID;
import org.opentosca.exceptions.SystemException;
import org.opentosca.exceptions.UserException;
import org.opentosca.planbuilder.csarhandler.CSARHandler;
import org.opentosca.planbuilder.export.exporters.SimpleFileExporter;
import org.opentosca.planbuilder.integration.layer.AbstractExporter;
import org.opentosca.planbuilder.model.plan.BuildPlan;
import org.opentosca.planbuilder.model.plan.Deploy;
import org.opentosca.util.fileaccess.service.IFileAccessService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.winery.model.selfservice.Application;
import org.eclipse.winery.model.selfservice.ApplicationOption;

/**
 * <p>
 * This class is a Exporter class for local filesystem exports based on the
 * OpenTOSCA Core
 * </p>
 * Copyright 2013 IAAS University of Stuttgart <br>
 * <br>
 *
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class Exporter extends AbstractExporter {
	
	private final static Logger LOG = LoggerFactory.getLogger(Exporter.class);
	
	private SimpleFileExporter simpleExporter;
	
	private ObjectFactory toscaFactory;
	private CSARHandler handler = new CSARHandler();
	
	
	/**
	 * Constructor
	 */
	public Exporter() {
		this.simpleExporter = new SimpleFileExporter();
		
		this.toscaFactory = new ObjectFactory();
		
	}
	
	/**
	 * Exports the given BuildPlan to the given URI
	 *
	 * @param destination the absolute location to export to
	 * @param buildPlan the BuildPlan to export
	 * @throws IOException is thrown when reading/writing to the given URI fails
	 * @throws JAXBException is thrown when writing with JAXB fails
	 */
	public void export(URI destination, BuildPlan buildPlan) throws IOException, JAXBException {
		this.simpleExporter.export(destination, buildPlan);
	}
	
	/**
	 * Exports the given BuildPlans repackaged with the CSAR denoted by the
	 * given CSARID
	 *
	 * @param buildPlans the BuildPlans to export
	 * @param csarId the CSARID of a CSAR
	 * @return a File denoting the absolute Path to the exported CSAR
	 */
	public File export(List<BuildPlan> buildPlans, CSARID csarId) {
		
		CSARContent csarContent = null;
		try {
			csarContent = this.handler.getCSARContentForID(csarId);
		} catch (UserException e1) {
			Exporter.LOG.error("Error occured while trying to retrieve CSAR content", e1);
		}
		
		if (csarContent == null) {
			return null;
		}
		
		String csarName = csarId.getFileName();
		
		IFileAccessService service = this.getFileAccessService();
		
		File tempDir = service.getTemp();
		File pathToRepackagedCsar = service.getTemp();
		File repackagedCsar = new File(pathToRepackagedCsar, csarName);
		
		try {
			Set<AbstractFile> files = csarContent.getFilesRecursively();
			AbstractFile mainDefFile = csarContent.getRootTOSCA();
			File rootDefFile = mainDefFile.getFile().toFile();
			Definitions defs = this.parseDefinitionsFile(rootDefFile);
			List<TServiceTemplate> servTemps = this.getServiceTemplates(defs);
			
			List<BuildPlan> plansToExport = new ArrayList<BuildPlan>();
			
			// add plans element to servicetemplates
			for (BuildPlan buildPlan : buildPlans) {
				for (TServiceTemplate serviceTemplate : servTemps) {
					if (buildPlan.getServiceTemplate().toString().equals(this.buildQName(defs, serviceTemplate).toString())) {
						TPlans plans = serviceTemplate.getPlans();
						if (plans == null) {
							plans = this.toscaFactory.createTPlans();
							serviceTemplate.setPlans(plans);
						}
						List<TPlan> planList = plans.getPlan();
						planList.add(this.generateTPlanElement(buildPlan));
						plansToExport.add(buildPlan);
					}
				}
			}
			
			for (AbstractFile file : files) {
				if (file.getFile().toFile().toString().equals(rootDefFile.toString())) {
					continue;
				}
				
				File newLocation = new File(tempDir, file.getPath());
				Exporter.LOG.debug(newLocation.getAbsolutePath());
				Exporter.LOG.debug(file.getFile().toString());
				if (newLocation.isDirectory()) {
					
					FileUtils.copyDirectory(file.getFile().toFile(), newLocation);
				} else {
					FileUtils.copyFile(file.getFile().toFile(), newLocation);
				}
				
			}
			
			// write new defs file
			File newDefsFile = new File(tempDir, mainDefFile.getPath());
			newDefsFile.createNewFile();
			JAXBContext jaxbContext = JAXBContext.newInstance(Definitions.class);
			Marshaller m = jaxbContext.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			// output to the console: m.marshal(defs, System.out);
			m.marshal(defs, newDefsFile);
			
			// write plans
			for (BuildPlan plan : plansToExport) {
				File planPath = new File(tempDir, this.generateRelativePlanPath(plan));
				Exporter.LOG.debug(planPath.toString());
				planPath.getParentFile().mkdirs();
				planPath.createNewFile();
				this.simpleExporter.export(planPath.toURI(), plan);
			}
			
			// Check if selfservice is already available
			File selfServiceDir = new File(tempDir, "SELFSERVICE-Metadata");
			File selfServiceDataXml = new File(selfServiceDir, "data.xml");
			JAXBContext jaxbContextWineryApplication = JAXBContext.newInstance(Application.class);
			
			if (selfServiceDir.exists() && selfServiceDataXml.exists()) {
				Unmarshaller u = jaxbContextWineryApplication.createUnmarshaller();
				Application appDesc = (Application) u.unmarshal(selfServiceDataXml);
				
				if (appDesc.getOptions() != null) {
					// check if planInput etc. is set properly
					for (ApplicationOption option : appDesc.getOptions().getOption()) {
						for (BuildPlan plan : plansToExport) {
							if (option.getPlanServiceName().equals(this.getBuildPlanServiceName(plan.getDeploymentDeskriptor()).getLocalPart())) {
								if (!new File(selfServiceDir, option.getPlanInputMessageUrl()).exists()) {
									// the planinput file is defined in the xml,
									// but
									// no file exists in the csar -> write one
									File planInputFile = new File(selfServiceDir, option.getPlanInputMessageUrl());
									this.writePlanInputMessageInstance(plan, planInputFile);
								}
							}
						}
					}
				} else {
					int optionCounter = 1;
					Application.Options options = new Application.Options();
					
					for (BuildPlan plan : plansToExport) {
						ApplicationOption option = new ApplicationOption();
						option.setName("Default" + optionCounter);
						option.setId(String.valueOf(optionCounter));
						option.setIconUrl("");
						option.setDescription("N/A");
						option.setPlanServiceName(this.getBuildPlanServiceName(plan.getDeploymentDeskriptor()).getLocalPart());
						option.setPlanInputMessageUrl("plan.input.default." + optionCounter + ".xml");
						this.writePlanInputMessageInstance(plan, new File(selfServiceDir, "plan.input.default." + optionCounter + ".xml"));
						optionCounter++;
						options.getOption().add(option);
					}
					appDesc.setOptions(options);
					
					Marshaller wineryAppMarshaller = jaxbContextWineryApplication.createMarshaller();
					wineryAppMarshaller.marshal(appDesc, selfServiceDataXml);
				}
				
			} else {
				// write SELFSERVICE-Metadata folder and files
				if (selfServiceDir.mkdirs() && selfServiceDataXml.createNewFile()) {
					Application appDesc = new Application();
					
					appDesc.setDisplayName(csarName);
					appDesc.setDescription("No description available. This application was partially generated");
					appDesc.setIconUrl("");
					appDesc.setImageUrl("");
					
					int optionCounter = 1;
					Application.Options options = new Application.Options();
					
					for (BuildPlan plan : plansToExport) {
						ApplicationOption option = new ApplicationOption();
						option.setName("Default" + optionCounter);
						option.setId(String.valueOf(optionCounter));
						option.setIconUrl("");
						option.setDescription("N/A");
						option.setPlanServiceName(this.getBuildPlanServiceName(plan.getDeploymentDeskriptor()).getLocalPart());
						option.setPlanInputMessageUrl("plan.input.default." + optionCounter + ".xml");
						this.writePlanInputMessageInstance(plan, new File(selfServiceDir, "plan.input.default." + optionCounter + ".xml"));
						optionCounter++;
						options.getOption().add(option);
					}
					appDesc.setOptions(options);
					
					Marshaller wineryAppMarshaller = jaxbContextWineryApplication.createMarshaller();
					wineryAppMarshaller.marshal(appDesc, selfServiceDataXml);
				}
			}
			
		} catch (IOException e) {
			Exporter.LOG.error("Some IO Exception occured", e);
		} catch (JAXBException e) {
			Exporter.LOG.error("Some error while marshalling with JAXB", e);
		} catch (SystemException e) {
			Exporter.LOG.error("Some error in the openTOSCA Core", e);
		}
		service.zip(tempDir, repackagedCsar);
		Exporter.LOG.debug(repackagedCsar.toString());
		return repackagedCsar;
	}
	
	/**
	 * Parses the given file to a JAXB Definitions class
	 *
	 * @param file a File denoting to a TOSCA Definitions file
	 * @return a JAXB Definitions class object if parsing was without errors,
	 *         else null
	 */
	private Definitions parseDefinitionsFile(File file) {
		Definitions def = null;
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance("org.oasis_open.docs.tosca.ns._2011._12");
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			def = (Definitions) unmarshaller.unmarshal(new FileReader(file));
		} catch (JAXBException e) {
			Exporter.LOG.error("Error while reading a Definitions file", e);
			return null;
		} catch (FileNotFoundException e) {
			Exporter.LOG.error("Definitions file not found", e);
			return null;
		}
		return def;
	}
	
	/**
	 * Returns the FileAccessService of the OpenTOSCA Core
	 *
	 * @return the IFileAccessService of the OpenTOSCA Core
	 */
	private IFileAccessService getFileAccessService() {
		BundleContext ctx = FrameworkUtil.getBundle(Exporter.class).getBundleContext();
		ServiceReference serviceReference = ctx.getServiceReference(IFileAccessService.class.getName());
		IFileAccessService service = (IFileAccessService) ctx.getService(serviceReference);
		return service;
	}
	
	/**
	 * Builds a valid QName for the given ServiceTemplate based on the given
	 * Definitions document
	 *
	 * @param defs a JAXB Definitions
	 * @param serviceTemplate a JAXB TServiceTemplate
	 * @return a QName denoting the given ServiceTemplate
	 */
	private QName buildQName(Definitions defs, TServiceTemplate serviceTemplate) {
		String namespace = serviceTemplate.getTargetNamespace();
		if (namespace == null) {
			namespace = defs.getTargetNamespace();
		}
		String id = serviceTemplate.getId();
		return new QName(namespace, id);
	}
	
	/**
	 * Returns a List of TServiceTemplate of the given Definitions document
	 *
	 * @param defs a JAXB Definitions document
	 * @return a List of TServiceTemplate which are the ServiceTemplates of the
	 *         given Definitions Document
	 */
	private List<TServiceTemplate> getServiceTemplates(Definitions defs) {
		List<TServiceTemplate> servTemps = new ArrayList<TServiceTemplate>();
		
		for (TExtensibleElements element : defs.getServiceTemplateOrNodeTypeOrNodeTypeImplementation()) {
			if (element instanceof TServiceTemplate) {
				servTemps.add((TServiceTemplate) element);
			}
		}
		return servTemps;
	}
	
	/**
	 * Generates a JAXB TPlan element for the given BuildPlan
	 *
	 * @param buildPlan a BuildPlan
	 * @return a JAXB TPlan Object which represents the given BuildPlan
	 */
	private TPlan generateTPlanElement(BuildPlan buildPlan) {
		TPlan plan = new Plan();
		TPlan.PlanModelReference ref = new TPlan.PlanModelReference();
		TPlan.InputParameters inputParams = new TPlan.InputParameters();
		TPlan.OutputParameters outputParams = new TPlan.OutputParameters();
		List<TParameter> inputParamsList = inputParams.getInputParameter();
		List<TParameter> outputParamsList = outputParams.getOutputParameter();
		
		ref.setReference(this.generateRelativePlanPath(buildPlan));
		plan.setPlanModelReference(ref);
		
		for (String paramName : buildPlan.getWsdl().getInputMessageLocalNames()) {
			// the builder supports only string types
			TParameter param = this.toscaFactory.createTParameter();
			param.setName(paramName);
			param.setRequired(TBoolean.YES);
			param.setType("String");
			inputParamsList.add(param);
		}
		
		for (String paramName : buildPlan.getWsdl().getOuputMessageLocalNames()) {
			TParameter param = this.toscaFactory.createTParameter();
			param.setName(paramName);
			param.setRequired(TBoolean.YES);
			param.setType("String");
			outputParamsList.add(param);
		}
		
		plan.setInputParameters(inputParams);
		plan.setOutputParameters(outputParams);
		
		plan.setPlanType("http://docs.oasis-open.org/tosca/ns/2011/12/PlanTypes/BuildPlan");
		plan.setId(buildPlan.getBpelProcessElement().getAttribute("name"));
		plan.setPlanLanguage(BuildPlan.bpelNamespace);
		
		return plan;
	}
	
	/**
	 * Generates a relative path for the BuildPlan to be used inside a CSAR file
	 *
	 * @param buildPlan the BuildPlan to get the path for
	 * @return a relative Path to be used inside a CSAR
	 */
	private String generateRelativePlanPath(BuildPlan buildPlan) {
		return "Plans/" + buildPlan.getBpelProcessElement().getAttribute("name") + ".zip";
	}
	
	private QName getBuildPlanServiceName(Deploy deploy) {
		// generated buildplans have only one process!
		for (TProvide provide : deploy.getProcess().get(0).getProvide()) {
			// "client" is a convention
			if (provide.getPartnerLink().equals("client")) {
				return provide.getService().getName();
			}
		}
		return null;
	}
	
	private void writePlanInputMessageInstance(BuildPlan buildPlan, File xmlFile) throws IOException {
		String messageNs = buildPlan.getWsdl().getTargetNamespace();
		String requestMessageLocalName = buildPlan.getWsdl().getRequestMessageLocalName();
		List<String> inputParamNames = buildPlan.getWsdl().getInputMessageLocalNames();
		
		VinothekKnownParameters paramMappings = new VinothekKnownParameters();
		String soapMessagePrefix = this.createPrefixPartOfSoapMessage(messageNs, requestMessageLocalName);
		String soapMessageSuffix = this.createSuffixPartOfSoapMessage(requestMessageLocalName);
		
		String soapMessage = soapMessagePrefix;
		for (String inputParamName : inputParamNames) {
			soapMessage += paramMappings.createXmlElement(inputParamName);
		}
		soapMessage += soapMessageSuffix;
		
		FileUtils.write(xmlFile, soapMessage);
	}
	
	private String createPrefixPartOfSoapMessage(String namespace, String messageBodyRootLocalName) {
		String soapEnvelopePrefix = "<soapenv:Envelope xmlns:wsa=\"http://www.w3.org/2005/08/addressing\" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:org=\"" + namespace + "\"><soapenv:Header><wsa:ReplyTo><wsa:Address>%CALLBACK-URL%</wsa:Address></wsa:ReplyTo><wsa:Action>" + namespace + "/initiate</wsa:Action><wsa:MessageID>%CORRELATION-ID%</wsa:MessageID></soapenv:Header><soapenv:Body><org:" + messageBodyRootLocalName + ">";
		return soapEnvelopePrefix;
	}
	
	private String createSuffixPartOfSoapMessage(String messageBodyRootLocalName) {
		String soapEnvelopeSuffix = "</org:" + messageBodyRootLocalName + "></soapenv:Body></soapenv:Envelope>";
		return soapEnvelopeSuffix;
	}
}
