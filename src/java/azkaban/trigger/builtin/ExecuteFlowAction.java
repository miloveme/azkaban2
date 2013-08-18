package azkaban.trigger.builtin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutorManager;
import azkaban.executor.ExecutorManagerException;
import azkaban.flow.Flow;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.sla.SlaOption;
import azkaban.trigger.Condition;
import azkaban.trigger.ConditionChecker;
import azkaban.trigger.Trigger;
import azkaban.trigger.TriggerAction;
import azkaban.triggerapp.TriggerRunnerManager;

public class ExecuteFlowAction implements TriggerAction {

	public static final String type = "ExecuteFlowAction";

	public static final String EXEC_ID = "ExecuteFlowAction.execid";
	
	private static ExecutorManager executorManager;
	private static TriggerRunnerManager triggerRunnerManager;
	private String actionId;
	private int projectId;
	private String projectName;
	private String flowName;
	private String submitUser;
	private static ProjectManager projectManager;
	private ExecutionOptions executionOptions = new ExecutionOptions();
	private List<SlaOption> slaOptions;
	private Map<String, Object> context;
	
	private static Logger logger = Logger.getLogger(ExecuteFlowAction.class);
	
	public ExecuteFlowAction(String actionId, int projectId, String projectName, String flowName, String submitUser, ExecutionOptions executionOptions, List<SlaOption> slaOptions) {
		this.actionId = actionId;
		this.projectId = projectId;
		this.projectName = projectName;
		this.flowName = flowName;
		this.submitUser = submitUser;
		this.executionOptions = executionOptions;
		this.slaOptions = slaOptions;
	}
	
	public static void setLogger(Logger logger) {
		ExecuteFlowAction.logger = logger;
	}
	
	public String getProjectName() {
		return projectName;
	}

	public int getProjectId() {
		return projectId;
	}

	public void setProjectId(int projectId) {
		this.projectId = projectId;
	}

	public String getFlowName() {
		return flowName;
	}

	public void setFlowName(String flowName) {
		this.flowName = flowName;
	}

	public String getSubmitUser() {
		return submitUser;
	}

	public void setSubmitUser(String submitUser) {
		this.submitUser = submitUser;
	}

	public ExecutionOptions getExecutionOptions() {
		return executionOptions;
	}

	public void setExecutionOptions(ExecutionOptions executionOptions) {
		this.executionOptions = executionOptions;
	}
	
	public List<SlaOption> getSlaOptions() {
		return slaOptions;
	}

	public void setSlaOptions(List<SlaOption> slaOptions) {
		this.slaOptions = slaOptions;
	}

	public static ExecutorManager getExecutorManager() {
		return executorManager;
	}
 	
	public static void setExecutorManager(ExecutorManager executorManager) {
		ExecuteFlowAction.executorManager = executorManager;
	}
	
	public static TriggerRunnerManager getTriggerRunnerManager() {
		return triggerRunnerManager;
	}
 	
	public static void setTriggerRunnerManager(TriggerRunnerManager triggerRunnerManager) {
		ExecuteFlowAction.triggerRunnerManager = triggerRunnerManager;
	}

	public static ProjectManager getProjectManager() {
		return projectManager;
	}
	
	public static void setProjectManager(ProjectManager projectManager) {
		ExecuteFlowAction.projectManager = projectManager;
	}

	@Override
	public String getType() {
		return type;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TriggerAction fromJson(Object obj) {
		return createFromJson((HashMap<String, Object>) obj);
	}

	@SuppressWarnings("unchecked")
	public static TriggerAction createFromJson(HashMap<String, Object> obj) {
		Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
		String objType = (String) jsonObj.get("type");
		if(! objType.equals(type)) {
			throw new RuntimeException("Cannot create action of " + type + " from " + objType);
		}
		String actionId = (String) jsonObj.get("actionId");
		int projectId = Integer.valueOf((String)jsonObj.get("projectId"));
		String projectName = (String) jsonObj.get("projectName");
		String flowName = (String) jsonObj.get("flowName");
		String submitUser = (String) jsonObj.get("submitUser");
		ExecutionOptions executionOptions = null;
		if(jsonObj.containsKey("executionOptions")) {
			executionOptions = ExecutionOptions.createFromObject(jsonObj.get("executionOptions"));
		}
		List<SlaOption> slaOptions = null;
		if(jsonObj.containsKey("slaOptions")) {
			slaOptions = new ArrayList<SlaOption>();
			List<Object> slaOptionsObj = (List<Object>) jsonObj.get("slaOptions");
			for(Object slaObj : slaOptionsObj) {
				slaOptions.add(SlaOption.fromObject(slaObj));
			}
		}
		return new ExecuteFlowAction(actionId, projectId, projectName, flowName, submitUser, executionOptions, slaOptions);
	}
	
	@Override
	public Object toJson() {
		Map<String, Object> jsonObj = new HashMap<String, Object>();
		jsonObj.put("actionId", actionId);
		jsonObj.put("type", type);
		jsonObj.put("projectId", String.valueOf(projectId));
		jsonObj.put("projectName", projectName);
		jsonObj.put("flowName", flowName);
		jsonObj.put("submitUser", submitUser);
		if(executionOptions != null) {
			jsonObj.put("executionOptions", executionOptions.toObject());
		}
		if(slaOptions != null) {
			List<Object> slaOptionsObj = new ArrayList<Object>();
			for(SlaOption sla : slaOptions) {
				slaOptionsObj.add(sla.toObject());
			}
			jsonObj.put("slaOptions", slaOptionsObj);
		}
		return jsonObj;
	}

	@Override
	public void doAction() throws Exception {
		if(projectManager == null || executorManager == null) {
			throw new Exception("ExecuteFlowAction not properly initialized!");
		}
		
		Project project = projectManager.getProject(projectId);
		if(project == null) {
			logger.error("Project to execute " + projectId + " does not exist!");
			throw new RuntimeException("Error finding the project to execute " + projectId);
		}
		
		Flow flow = project.getFlow(flowName);
		if(flow == null) {
			logger.error("Flow " + flowName + " cannot be found in project " + project.getName());
			throw new RuntimeException("Error finding the flow to execute " + flowName);
		}
		
		ExecutableFlow exflow = new ExecutableFlow(flow);
		exflow.setSubmitUser(submitUser);
		exflow.addAllProxyUsers(project.getProxyUsers());
		
		if(!executionOptions.isFailureEmailsOverridden()) {
			executionOptions.setFailureEmails(flow.getFailureEmails());
		}
		if(!executionOptions.isSuccessEmailsOverridden()) {
			executionOptions.setSuccessEmails(flow.getSuccessEmails());
		}
		exflow.setExecutionOptions(executionOptions);
		
		try{
			executorManager.submitExecutableFlow(exflow);
			Map<String, Object> outputProps = new HashMap<String, Object>();
			outputProps.put(EXEC_ID, exflow.getExecutionId());
			context.put(actionId, outputProps);
			logger.info("Invoked flow " + project.getName() + "." + flowName);
		} catch (ExecutorManagerException e) {
			throw new RuntimeException(e);
		}
		
		// deal with sla
		if(slaOptions != null && slaOptions.size() > 0) {
			int execId = exflow.getExecutionId();
			for(SlaOption sla : slaOptions) {
				logger.info("Adding sla trigger " + sla.toString());
				SlaChecker slaFailChecker = new SlaChecker("slaFailChecker", sla, execId, false);
				Map<String, ConditionChecker> failCheckers = new HashMap<String, ConditionChecker>();
				failCheckers.put(slaFailChecker.getId(), slaFailChecker);
				Condition triggerCond = new Condition(failCheckers, slaFailChecker.getId() + ".eval()");
				SlaChecker slaPassChecker = new SlaChecker("slaPassChecker", sla, execId, true);
				Map<String, ConditionChecker> passCheckers = new HashMap<String, ConditionChecker>();
				passCheckers.put(slaPassChecker.getId(), slaPassChecker);
				Condition expireCond = new Condition(passCheckers, slaPassChecker.getId() + ".eval()");
				List<TriggerAction> actions = new ArrayList<TriggerAction>();
				List<String> slaActions = sla.getActions();
				for(String act : slaActions) {
					if(act.equals(SlaOption.ACTION_ALERT)) {
						SlaAlertAction slaAlert = new SlaAlertAction("slaAlert", sla, execId);
						actions.add(slaAlert);
					} else if(act.equals(SlaOption.ACTION_CANCEL_FLOW)) {
						KillExecutionAction killAct = new KillExecutionAction("killExecution", execId);
						actions.add(killAct);
					}
				}
				Trigger slaTrigger = new Trigger("azkaban_sla", "azkaban", triggerCond, expireCond, actions);
				slaTrigger.setResetOnTrigger(false);
				slaTrigger.setResetOnExpire(false);
				triggerRunnerManager.insertTrigger(slaTrigger);
			}
		}
		
	}

	@Override
	public String getDescription() {
		return "Execute flow " + getFlowName() + 
				" from project " + getProjectName();
	}

	@Override
	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	@Override
	public String getId() {
		return actionId;
	}


}