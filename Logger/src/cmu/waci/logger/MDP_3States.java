package cmu.waci.logger;

import java.util.ArrayList;

/*
// For Inter 5s
 
 	ArrayList<Integer> freqModes = dvfs.getFrequencyScaleModes();
 	
 	int freqs[] = {freqModes.get(1),freqModes.get(4),freqModes.get(10)};
 
 	int pow5_s = 0;
 	int pow15_s = 0;

 	int perf15_s = 0;
 	
 	double[][] pow5_t = {{.99968,.00032,0},{.01845,.96749,.01406},{0,.0166,.9834}};
 	double[][] pow15_t = {{.99971,.00029,0},{.0171,.9721,.0108},{0,.01258,.98742}};
 	
 	double[][] perf5_t = {{.84776,.15224,0},{.03976,.95543,.00481},{0,.1291,.87079}};
 	double[][] perf15_t = {{.93094,.06906,0},{.0088,.9901,.0011},{0,.11111,.88889}};	
 
 	MDP_3States pow5 = new MDP_3States(pow5_s,pow5_t,freqs);
  	MDP_3States pow15 = new MDP_3States(pow15_s,pow15_t,freqs);


	//TODO
	int perf_s = 0;
	int nextPerfState = 0;
	int currState = 0;
	int guessFreq = 0;
	
	double[][] perf_t0 = {{.7,.3,0},{.5,.5,0},{0,1,0}};
	//double[][] perf_t1 = {{.12,.3,0},{.5,.5,0},{0,1,0}};
	double[][] perf_t2 = {{},{},{}};	

 	MDP_3States perf_ns0 = new MDP_3States(perf_s,perf_t0,freqs);
 	MDP_3States perf_ns1 = new MDP_3States(perf_s,perf_t1,freqs);  	
 	MDP_3States perf_ns2 = new MDP_3States(perf_s,perf_t2,freqs);  	
  	
  	if (CPUInfo.getCPUUtilizationPct()<=0.3) {
  		nextPerfState = 0;
  	} else if (CPUInfo.getCPUUtilizationPct()<=0.7) {
  		nextPerfState = 1;
  	} else {
  		nextPerfState = 2;
  	}
  	
  	switch (nextPerfState) {
  		case 0:
  			perf_ns0.doRun();
  			guessFreq = perf_ns0.getFreq();
  			currState = perf_ns0.getCurrState();
  			perf_ns1.setCurrState(currState);
  			perf_ns2.setCurrState(currState); 			
  			break;
  		case 1:
  		  	perf_ns1.doRun();
  			guessFreq = perf_ns1.getFreq();
  			currState = perf_ns1.getCurrState();
  			perf_ns0.setCurrState(currState);
  			perf_ns2.setCurrState(currState); 	
  			break;
  		case 2:
  		  	perf_ns2.doRun();
  			guessFreq = perf_ns2.getFreq();
  			currState = perf_ns2.getCurrState();
  			perf_ns0.setCurrState(currState);
  			perf_ns2.setCurrState(currState); 	
  			break;
  		default:
  			break;
  	
  	}
  	

 */



public class MDP_3States {
	private int CurrState = 0;
	
	private static int S0 = 0;
	private static int S1 = 1;	
	private static int S2 = 2;
	
	private double P[][] = new double[3][3];
	private int freq[] = new int[3];
	
	// Assume only three states
	public MDP_3States(int curr, double tran[][], int inFreq[]) {
		CurrState = curr;
		P = tran;
		freq = inFreq;
	}
	
	public void doRun() {
		double prob = Math.random();
		int nextState = CurrState;
		
		if (prob < P[CurrState][S0]) {
			nextState = S0;
		} else if (prob < (P[CurrState][S1]+P[CurrState][S0])) {
			nextState = S1;
		} else {
			nextState = S2;
		}		
		CurrState = nextState;
	}
	
	public int getCurrState() {
        return CurrState;
    }
	
	public void setCurrState(int curr) {
        CurrState = curr;
    }
	
	public int getFreq() {
		return freq[CurrState];
	}
	
	public double getTransition(int to, int from) {
		return P[to][from];
	}	
}
