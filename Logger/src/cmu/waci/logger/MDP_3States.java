package cmu.waci.logger;

import java.util.ArrayList;

/* 
 * This is the Markov Decision Process Model
 * Takes in transition probabilities and state values.
 * It will store the state and returns current state and state values.
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
	
	// Does the transition randomly based on given probabilities
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
	
	// Sets current state
	public void setCurrState(int curr) {
        CurrState = curr;
    }
	
	// Get attribute i.e. frequency associated with a state
	public int getFreq() {
		return freq[CurrState];
	}
	
	public double getTransition(int to, int from) {
		return P[to][from];
	}	
}
