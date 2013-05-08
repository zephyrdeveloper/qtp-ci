package com.thed.service.soap.client;


import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.xml.namespace.QName;


/**
 * This class uses code generated by Apache CXF 2.2.8
 * Mon Mar 14 1:30:41 PDT 2013
 */

public final class QTPRunner {

	/**
	 * Constants 
	 */
    private static final QName SERVICE_NAME = new QName("http://getzephyr.com/com/thed/services/soap/zephyrsoapservice", "ZephyrSoapService");
    private static final String EXECUTABLE = "c:\\Program Files (x86)\\HP\\Unified Functional Testing\\bin\\UFTBatchRunnerCMD.exe";
    
	private static final Integer ANY_ONE = 2;
	private static final String WIP = "3";

	/**********************Local Variables ********************/
    private ZephyrSoapService _port = null;
    private Scanner scanner = new Scanner(System.in);
    private final Logger logger = Logger.getLogger(Executor.class.getName());
    
    /**
     * Constructor
     * @param url
     */
    private QTPRunner(String url) {
		URL wsdlURL = ZephyrSoapService_Service.WSDL_LOCATION;
		if (url != null) {
			File wsdlFile = new File(url);
			try {
				if (wsdlFile.exists()) {
					wsdlURL = wsdlFile.toURI().toURL();
				} else {
					wsdlURL = new URL(url);
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		ZephyrSoapService_Service ss = new ZephyrSoapService_Service(wsdlURL, SERVICE_NAME);
		_port = ss.getZephyrSoapServiceImplPort();
	}

	public static void main(String args[]) throws java.lang.Exception {
		System.setProperty("javax.xml.stream.XMLOutputFactory", "com.thed.service.soap.client.ZWstxOutputFactory");
		String url = null;
		if (args != null && args.length > 0) {
			url = "http://" + args[0] + "/flex/services/soap/zephyrsoapservice-v1?wsdl";
		}
		String uName = "test.manager";
		String pwd = "test";
		String status = "1";
		String notes = "";
		QTPRunner client = new QTPRunner(url);
		
		/************* Get the automation folder **************/
		String token = client.login(uName, pwd);
		client.printPressAnyKey();
		
		/**************** Get Automation Repository ***********/
		RemoteRepositoryTree repositoryTree = client.getAutomationPhase(token);
		System.out.println("\nFetched repository Tree ID " + repositoryTree.getId());
		client.printPressAnyKey();
		
		/****** Create Executions *****************************/
		System.out.println("\nAdding Respository for execution");
		Long executableRepositoryTreeId = client.addRepositoryTreeForExecutions(repositoryTree.getId(), token);
		System.out.println("Added Respository for execution, Id: " + executableRepositoryTreeId);
		client.printPressAnyKey();
		
		/********** Fetch testcases for Executions ************/
		List<RemoteReleaseTestSchedule> schedules = client._port.getTestSchedulesByCriteria(client.getRemoteCriteriaList("cyclePhaseId", SearchOperation.EQUALS, executableRepositoryTreeId.toString()), false, token);
		for(RemoteReleaseTestSchedule schedule : schedules){
			System.out.print("Executing " + schedule.getScriptId() + " - " + schedule.getScriptName() + " @ " + schedule.getScriptPath());
			client.updateTestResult(token, String.valueOf(schedule.testScheduleId), WIP, "test under execution");
			long start = System.currentTimeMillis();
			Executor.executeSequentially(EXECUTABLE, schedule.getScriptPath() + File.separator + schedule.getScriptName());
			status = ResultParser.parseResults(schedule.getScriptPath() + File.separator + "Report" + File.separator +"Report");
			System.out.println("Execution completed with result " + status);
			long end = System.currentTimeMillis();
			client.updateTestResult(token, String.valueOf(schedule.testScheduleId), status, "Took " + (end - start)/1000 + " secs");
			client.printPressAnyKey();
		}
		client.logout(token);
		System.exit(0);
	}

	private Long addRepositoryTreeForExecutions(Long id, String token) throws ZephyrServiceException {
		List<RemoteCycle> cycles = _port.getCyclesByCriteria(getRemoteCriteriaList("id", SearchOperation.EQUALS, "1" ), false, token);
		RemoteCycle cycle = cycles.get(0);
		
		RemoteNameValue repositoryId = convertToRemoteNameValue(id);
		
		RemotePhase phase = new RemotePhase();
		phase.remoteCycle = convertToRemoteNameValue(1l);
		phase.remoteRepository = repositoryId;
		phase.startDate = cycle.startDate;
		phase.endDate = cycle.endDate;
		return _port.addPhaseToCycle(phase, ANY_ONE, token);
	}

	private RemoteNameValue convertToRemoteNameValue(Long id) {
		RemoteNameValue repositoryId = new RemoteNameValue();
		RemoteData remoteData = new RemoteData();
		remoteData.id = id;
		repositoryId.remoteData = remoteData;
		return repositoryId;
	}

	private RemoteRepositoryTree getAutomationPhase(String token) throws ZephyrServiceException {
		RemoteCriteria rc = getRemoteCriteria("name", SearchOperation.EQUALS, "automation" );
		List<RemoteRepositoryTree> repositoryTrees =  _port.getTestCaseTreesByCriteria(Arrays.asList(rc), false, token);
		return repositoryTrees.get(0);
	}

	private List<RemoteCriteria> getRemoteCriteriaList(String sName, SearchOperation searchOperation, String sVal) {
		return Arrays.asList(getRemoteCriteria(sName, searchOperation, sVal));
	}
	private RemoteCriteria getRemoteCriteria(String sName, SearchOperation searchOperation, String sVal) {
		RemoteCriteria rc = new RemoteCriteria();
		rc.searchName = sName;
		rc.searchOperation = searchOperation;
		rc.searchValue = sVal;
		return rc;
	}

	public void updateStatusOnly(String url, String uName, String pwd, String testExecutionId, String status, String notes) throws ZephyrServiceException {
		String token = login(uName, pwd);
		updateTestResult(token, testExecutionId, status, notes);
		logout(token);
	}

	public void updateTestResult(String token, String testExecutionId, String status, String notes) {
		System.out.println("Invoking updateTestStatus...");
		java.util.List<RemoteTestResult> testResults = new ArrayList<RemoteTestResult>();
		RemoteTestResult testResult = new RemoteTestResult();
		testResult.setReleaseTestScheduleId(testExecutionId);
		testResult.setExecutionStatus(status);
		testResult.setExecutionNotes(notes);
		testResults.add(testResult);
		try {
			java.util.List<RemoteFieldValue> statusUpdateResponse = _port.updateTestStatus(testResults, token);
			//System.out.println("updateTestStatus.result=" + statusUpdateResponse.);
		} catch (ZephyrServiceException e) {
			System.err.println("Expected exception: ZephyrServiceException has occurred.");
			System.err.println(e.toString());
		}
	}

	private String login(String _login_username, String _login_password) throws ZephyrServiceException {
		System.out.println("Invoking login...");
		try {
			java.lang.String token = _port.login(_login_username, _login_password);
			System.out.println("login is Successful - token " + token);
			return token;
		} catch (ZephyrServiceException e) {
			System.out.println("Exception encounterd during login, Details:");
			e.printStackTrace(System.out);
			throw e;
		}
	}
	
	private void logout(String token) {
		System.out.println("Invoking logout...");
		try {
			_port.logout(token);
		} catch (ZephyrServiceException e) {
			System.out.println("Expected exception: ZephyrServiceException has occurred.");
			System.out.println(e.toString());
		}
	}
	
	private void printPressAnyKey(){
		System.out.println("Press Enter Key to Continue...");
		scanner.nextLine();
//		System.out.println("");
	}

}

//int count = 0;
//while(count++ < 10){
//	Thread.sleep(400);
//	System.out.print(".");
//}
