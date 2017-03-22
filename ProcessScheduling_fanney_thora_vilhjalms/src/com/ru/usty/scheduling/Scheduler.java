package com.ru.usty.scheduling;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;


import com.ru.usty.scheduling.process.ProcessExecution;
import com.ru.usty.scheduling.process.ProcessHandler;
import com.ru.usty.scheduling.process.ProcessInfo;

public class Scheduler implements Runnable {

	ProcessExecution processExecution;
	Policy policy;
	int quantum;
	ProcessData currentProcess;
	
	// arrays of time info for keeping track of W E S
	long[] added = new long[15];
	long[] startRun = new long[15];
	long[] finished = new long[15];
	
	boolean processIsRunning;
	
	// Thread time monitor for Round Robin
	// there may be only one thread so after starting one, threadMayRun becomes false
	Thread threadRR;
	boolean threadMayRun = true;
	
	// The time when a process starts running
	long startedProcess;
	
	Queue<ProcessData> processQueue;
	ArrayList<ProcessData> processQueueSPN;
	ArrayList<ProcessData> processQueueSRT;

	/**
	 * DO NOT CHANGE DEFINITION OF OPERATION
	 */
	public Scheduler(ProcessExecution processExecution) {
		this.processExecution = processExecution;
	}

	/**
	 * DO NOT CHANGE DEFINITION OF OPERATION
	 */
	public void startScheduling(Policy policy, int quantum) {

		this.policy = policy;
		this.quantum = quantum;
		this.processIsRunning = false;

		// initialize time-arrays with 0
		for(int i = 0; i < 15; i++) {
			added[i] = 0;
			startRun[i] = 0;
			finished[i] = 0;
		}

		switch(policy) {
		case FCFS:	//First-come-first-served, non-preemptive, interrupt on finish
			System.out.println("Starting new scheduling task: First-come-first-served");
			processQueue = new LinkedList<ProcessData>(); 		
			break;
		case RR:	//Round robin, preemptive and timer interrupt
			System.out.println("Starting new scheduling task: Round robin, quantum = " + quantum);
			processQueue = new LinkedList<ProcessData>();
			// start the thread monitor
			if(threadMayRun) {
				threadRR = new Thread(this);
				threadRR.start();
			}
			threadMayRun = false;
			break;
		case SPN:	//Shortest process next
			System.out.println("Starting new scheduling task: Shortest process next");
			processQueueSPN = new ArrayList<ProcessData>();
			
			
			break;
		case SRT:	//Shortest remaining time, preemptive (interrupt in add and finish)
			System.out.println("Starting new scheduling task: Shortest remaining time");
			/**
			 * Add your policy specific initialization code here (if needed)
			 */
			break;
		case HRRN:	//Highest response ratio next, non-preemptive (interrupt in finish)
		 			// monitor if there is a process already running						
			System.out.println("Starting new scheduling task: Highest response ratio next");
			/**
			 * Add your policy specific initialization code here (if needed)
			 */
			break;
		case FB:	//Feedback, preemptive and timer
			System.out.println("Starting new scheduling task: Feedback, quantum = " + quantum);
			/**
			 * Add your policy specific initialization code here (if needed)
			 */
			break;
		}

		/**
		 * Add general scheduling or initialization code here (if needed)
		 */

	}

	/**
	 * DO NOT CHANGE DEFINITION OF OPERATION
	 */
	public void processAdded(int processID) {
		added[processID] = System.currentTimeMillis();
		
		switch(policy) {
		case FCFS:
			// tékka hvort einhver process sé keyrandi
			if(!processIsRunning) {
				// process er addað og er fremstur og er valinn strax
				processQueue.add(new ProcessData(processID,0));
				currentProcess = processQueue.peek();
				processExecution.switchToProcess(processID);
				processIsRunning = true;
			}
			else {
				// process fer í biðröð
				processQueue.add(new ProcessData(processID,0));	
			}
			break;
		case RR:
			if(!processIsRunning) {
				processQueue.add(new ProcessData(processID,0));
				currentProcess = processQueue.peek();
				startedProcess = System.currentTimeMillis();
				processExecution.switchToProcess(processID);
				processIsRunning = true;
			}
			else {
				processQueue.add(new ProcessData(processID,0));
			}
			break;
		case SPN:
			//Er process runnandi
			if(!processIsRunning) {

				processExecution.switchToProcess(processID);
				this.processIsRunning = true;
			}
			
			else {
				processQueueSPN.add(new ProcessData(processID, processExecution.getProcessInfo(processID).totalServiceTime));
			}
				
		default:
			break;
		
		}
	}

	/**
	 * DO NOT CHANGE DEFINITION OF OPERATION
	 */
	public void processFinished(int processID) {
		finished[processID] = System.currentTimeMillis();
		
		switch(policy) {
		case FCFS:
			System.out.println("Búúúúiiiin: " + processID);
			processQueue.remove();
			processIsRunning = false;
			// switch ef queue er ekki tóm
			if(processQueue.peek() != null){
				processIsRunning = true;
				currentProcess = processQueue.peek();
				System.out.println("Switching to process: " + processQueue.peek().processID);	
				processExecution.switchToProcess(processQueue.peek().processID);			
			}		
			break;
		case RR:/*
			System.out.println("Búúúúiiiin: " + processID);	
			processQueue.remove();
			processIsRunning = false;
			// switch ef queue er ekki tóm
			if(processQueue.peek() != null){
				processIsRunning = true;
				currentProcess = processQueue.peek();
				startedProcess = System.currentTimeMillis();
				processExecution.switchToProcess(processQueue.peek().processID);
				
				// Ef hann hefur ekki fengið að keyra gefa tímagildi á starti
				if(startRun[currentProcess.processID] == 0){
					startRun[currentProcess.processID] = System.currentTimeMillis();
				}
			}
			else {processIsRunning = false;}*/
			
			// TODO: mælingar á tíma
			break;
		case SPN:
			
			if(!processQueueSPN.isEmpty()) {
								
				int queueID = 0;
				long tempTime= 1000000000;
				
				for(int i = 0; i < processQueueSPN.size(); i++) {
					
					if(processQueueSPN.get(i).someTime < tempTime) {
						
						tempTime = processQueueSPN.get(i).someTime;
						queueID = i;
					}
				}
				processExecution.switchToProcess(processQueueSPN.get(queueID).processID);	
				processQueueSPN.remove(queueID);
				processIsRunning = true;
			}
			else {
				
				processExecution.switchToProcess(processID);
				processIsRunning  = false;
			}
			System.out.println("Búúúúiiiin: " + processID);	
			
			break;
			
		default:
			break;
		}
		
	}

	// Time Interrupt in Round Robin
	@Override
	public void run() {
		while(true){

			try {
				Thread.sleep(quantum);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			//Sleep ef annar process er kominn í gang
			while(System.currentTimeMillis() - startedProcess <= quantum){
				try{
					Thread.sleep(System.currentTimeMillis() - startedProcess);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			processQueue.add(currentProcess);

			if(processQueue.peek() != null){
				processIsRunning = true;
				currentProcess = processQueue.peek();	
				processExecution.switchToProcess(processQueue.remove().processID);	
				
				// Ef hann hefur ekki fengið að keyra gefa tímagildi á starti
				if(startRun[currentProcess.processID] == 0){
					startRun[currentProcess.processID] = System.currentTimeMillis();
				}
			}
			else{
				processIsRunning = false;
			}

			if(this.policy != Policy.RR){
				return; 
			}
		}
		
	}
}
