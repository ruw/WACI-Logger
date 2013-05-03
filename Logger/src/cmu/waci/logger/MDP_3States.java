package cmu.waci.logger;

/*
// For Inter 5s
 
 	int pow5_s = 0;
 	int pow15_s = 0;
 	int perf5_s = 0;
 	int perf15_s = 0;
 	
 	double[][] pow5_t = {{.99968,.00032,0},{.01845,.96749,.01406},{0,.0166,.9834}};
 	double[][] pow15_t = {{.99971,.00029,0},{.0171,.9721,.0108},{0,.01258,.98742}};
 	
 	double[][] perf5_t = {{.84776,.15224,0},{.03976,.95543,.00481},{0,.1291,.87079}};
 	double[][] perf15_t = {{.93094,.06906,0},{.0088,.9901,.0011},{0,.11111,.88889}};	
 
 	MDP_3States pow5 = new MDP_3States(pow5_s,pow5_t);
  	MDP_3States pow15 = new MDP_3States(pow15_s,pow15_t);

 	MDP_3States perf5 = new MDP_3States(perf5_s,perf5_t);
  	MDP_3States perf15 = new MDP_3States(perf15_s,perf15_t);

 */


public class MDP_3States {
	private int CurrState = 0;
	
	private static int S0 = 0;
	private static int S1 = 1;	
	private static int S2 = 2;
	
	private double P[][] = new double[3][3];
	
	// Assume only three states
	public MDP_3States(int curr, double tran[][]) {
		CurrState = curr;
		P = tran;
	}
	
	public int getNextState(int curr) {
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
		return nextState;
	}
	
	public int getCurrState() {
        return CurrState;
    }
	
	public double getTransition(int to, int from) {
		return P[to][from];
	}	
}
