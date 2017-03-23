package com.ru.usty.scheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.sound.midi.SysexMessage;

import org.lwjgl.Sys;

import com.ru.usty.scheduling.process.ProcessExecution;
import com.ru.usty.scheduling.process.ProcessHandler;
import com.ru.usty.scheduling.process.ProcessInfo;

public class Scheduler implements Runnable {

	ProcessExecution processExecution;
	Policy policy;
	int quantum;
	int runningProcID;
	ProcessData currentProcess;
	long currTime;
	//ProcessInfo currProcessInfo;
	
	// arrays of time info for keeping track of W E S
	long[] added = new long[15];
	long[] startRun = new long[15];
	long[] finished = new long[15];
	
	long totalResponseTime;
	long totalTurnaroundTime;
	long avgResponseTime;
	long avgTurnaroundTime;
	
	//long currentProcessTime;
	
	int numberOfProcesses;
	
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
	
	// assign priority based on RATIO
	long priorityHRRN(int processID){
		ProcessInfo info = processExecution.getProcessInfo(processID);
		long w = info.elapsedWaitingTime;
		long s = info.totalServiceTime;
		return (w+s)/s;	
	}
	
	// í HRRNviljum raða á queue eftir priority byggt á RATIO
	PriorityQueue<ProcessData> processQueueHRRN = new PriorityQueue<ProcessData>(new Comparator<ProcessData>(){

		@Override
		public int compare(ProcessData p1, ProcessData p2){

			if((priorityHRRN(p1.processID)) > (priorityHRRN(p2.processID))){
				return -1;
			}
			if((priorityHRRN(p1.processID)) < (priorityHRRN(p2.processID))){
				return -1;
			}
			return 0;
		}});

	public Scheduler(ProcessExecution processExecution) {
		this.processExecution = processExecution;
	}

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
		
		totalResponseTime = 0;
		totalTurnaroundTime = 0;
		avgResponseTime = 0;
		avgTurnaroundTime = 0;
		numberOfProcesses = 0;
		
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
			processQueueSRT = new ArrayList<ProcessData>();
			break;
		case HRRN:	//Highest response ratio next, non-preemptive (interrupt in finish)
		 			// monitor if there is a process already running						
			System.out.println("Starting new scheduling task: Highest response ratio next");
			break;
		case FB:	//Feedback, preemptive and timer
			System.out.println("Starting new scheduling task: Feedback, quantum = " + quantum);
			/**
			 * Add your policy specific initialization code here (if needed)
			 */
			break;
		}

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
				System.out.println("startRun fær timagildi");
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
				// Ef hann hefur ekki fengið að keyra gefa tímagildi á starti
				if(startRun[currentProcess.processID] == 0){
					startRun[currentProcess.processID] = System.currentTimeMillis();
				}
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
				//kveikja á process
				processExecution.switchToProcess(processID);
				runningProcID = processID;
				this.processIsRunning = true;
			}
			
			else {
				//bæta á röð
				processQueueSPN.add(new ProcessData(processID, processExecution.getProcessInfo(processID).totalServiceTime));
			}
			break;
		case SRT:
			if(!processIsRunning) {
				System.out.println("if: " + processID);
				//kveikja á process
				processExecution.switchToProcess(processID);
				runningProcID = processID;
				System.out.println("procid:" + runningProcID);
				this.processIsRunning = true;
				currTime = processExecution.getProcessInfo(runningProcID).totalServiceTime - processExecution.getProcessInfo(runningProcID).elapsedExecutionTime;
			}
			else if(currTime > (processExecution.getProcessInfo(processID).totalServiceTime)) {
				System.out.println("else if: " + processID);
				processExecution.switchToProcess(processID);
				processQueueSRT.add(new ProcessData(runningProcID,currTime));
				currTime = processExecution.getProcessInfo(processID).totalServiceTime - processExecution.getProcessInfo(processID).elapsedExecutionTime;
				runningProcID = processID;
				
			}
			else {
				System.out.println("else: " + processID);
				//bæta á röð
				processQueueSRT.add(new ProcessData(processID, processExecution.getProcessInfo(processID).totalServiceTime));
			}
				
			break;
		case HRRN:
			long p = priorityHRRN(processID);
			processQueueHRRN.add(new ProcessData(processID,p));

			if(!processIsRunning) {			
				currentProcess = processQueueHRRN.remove();
				processExecution.switchToProcess(currentProcess.processID); 
				processIsRunning = true;
				startRun[currentProcess.processID] = System.currentTimeMillis();
			}
			break;
		default:
			break;
		
		}
	}

	public void processFinished(int processID) {
		finished[processID] = System.currentTimeMillis();
		this.numberOfProcesses++;
		
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
				startRun[processQueue.peek().processID] = System.currentTimeMillis();
				processExecution.switchToProcess(processQueue.peek().processID);			
			
			}		
			break;
		case RR:
			if (numberOfProcesses == 15){
				calculator();
				}
			break;
		case SPN:
			//röði er ekki tóm, finna stysta processinn
			if(!processQueueSPN.isEmpty()) {
				processIsRunning = true;
				int queueID = 0;
				long tempTime= 1000000000;
				
				for(int i = 0; i < processQueueSPN.size(); i++) {
					
					if(processQueueSPN.get(i).someTime < tempTime) {
						
						tempTime = processQueueSPN.get(i).someTime;
						queueID = i;
					}
				}
//				kveikja á honum
				processExecution.switchToProcess(processQueueSPN.get(queueID).processID);	
				processQueueSPN.remove(queueID);
				//processIsRunning = true;
			}
			//enginn process í bið, kveikja á næsta
			else {
				
				processExecution.switchToProcess(processID);
				this.processIsRunning  = false;
			}
			System.out.println("Búúúúiiiin: " + processID);	
			if (numberOfProcesses == 15){
				calculator();
				}
			break;
			
		case SRT:
			
			if(!processQueueSRT.isEmpty()) {
				int queueID = 0;
				long tempTime= 1000000000;
				for(int i = 0; i < processQueueSRT.size(); i++) {
					
					if(processQueueSRT.get(i).someTime < tempTime) {
						
						tempTime = processQueueSRT.get(i).someTime;
						queueID = i;
					}
				}
//				kveikja á honum
				
				System.out.println("time:" + processQueueSRT.get(queueID).someTime);
				System.out.println("tmptime:" + tempTime);

				processExecution.switchToProcess(processQueueSRT.get(queueID).processID);	
				//System.out.println("procesid: " + processExecution.getProcessInfo(processQueueSRT.get(queueID).processID).totalServiceTime);
				currTime = tempTime;
				processQueueSRT.remove(queueID);
				//processIsRunning = true;
			}
			//enginn process í bið, kveikja á næsta
			else {
				
				//processExecution.switchToProcess(processID);
				this.processIsRunning  = false;
			}
			System.out.println("Búúúúiiiin: " + processID);	
			if(!processQueueSRT.isEmpty())
				
					
			System.out.println("processQueue: " + processQueueSRT.size());
				
			if (numberOfProcesses == 15){
				calculator();
				}
			break;
		case HRRN:
			System.out.println("Búúúúiiiin: " + processID);
			
			if(processQueueHRRN.peek() != null){
				currentProcess = processQueueHRRN.remove();
				processExecution.switchToProcess(currentProcess.processID);
				startRun[currentProcess.processID] = System.currentTimeMillis();
			}
			else{
				processIsRunning = false;
			}
			if(numberOfProcesses == 15){
				calculator ();
			}
			break;
		default:
			break;
		}
	}
	

	public void calculator (){
 		for(int i = 0; i<this.numberOfProcesses ; i++){
 			
 			this.totalResponseTime += (this.startRun[i] - this.added[i]); //bið þar til keyrður
 			this.totalTurnaroundTime += (this.finished[i] - this.added[i]); // bið í kerfinu
 			
 			System.out.println("startRun: "+ this.startRun[i]+ " - added: "+ this.added[i]+
 			" makes total responsetime: " + this.totalResponseTime);
 		}
 		
 		this.avgResponseTime = (this.totalResponseTime/15);
 		this.avgTurnaroundTime = (this.totalTurnaroundTime/15);
 		System.out.println("Average Response Time: " + this.avgResponseTime);
 		System.out.println("Average Turnaround Time: " + this.avgTurnaroundTime);	
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
					System.out.println("startRun fær timagildi");
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
