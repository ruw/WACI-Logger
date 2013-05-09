package cmu.waci.logger;

import java.util.ArrayList;
import java.util.LinkedList;

import csu.research.AURA.CPUInfo;
import csu.research.AURA.DVFSControl;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.format.Time;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;

public class InteractivityService extends Service {
	InteractivityView mView;
	//list of touches, size is the number of touches within window:
	static LinkedList<Time> mActs;
	private DVFSControl dvfs;
	
	static boolean savePowerOn = false;
	private boolean mRunning;
	private static boolean curOptPow = false;
	private static boolean curOptPerf = false;
	
	private int util_ind = 0;
	private int inter_ind = 0;
	int[] interRecord = {0,0,0,0,0,0,0,0,0,0};
	int[] utilRecord = {0,0,0,0,0,0,0,0,0,0};
	
	// Markov States
	private int nextState = 0;
	private int currState = 0;
	private int guessFreq = 0;
	
	/*
	private static int INTER_LOW = 10;
	private static int INTER_MID = 25;
	
	private static double UTIL_LOW = 30.0;
	private static double UTIL_MID = 70.0;
	*/
	//Gun
	
	private static int INTER_LOW = 10;
	private static int INTER_MID = 25;
	
	private static double UTIL_LOW = 75.0;
	private static double UTIL_MID = 95.0;
	
	// Transition Rates For Performance Metric
	//Gun:
	/*
	double[][] perf_t0 = {{.7,.3,0},{.5,.5,0},{0,1,0}};
	double[][] perf_t1 = {{.12,88,0},{.1,.88,.02},{0,.96,.04}};
	double[][] perf_t2 = {{0,1,0},{0,.43,.57},{0,.39,.61}};
	*/
	
	//Gun:
	double[][] perf_t0 = {{.75,.25,0},{.71,.29,0},{0,1,0}};
	double[][] perf_t1 = {{.27,.73,0},{.21,.71,.08},{0,.99,.01}};
	double[][] perf_t2 = {{0,1,0},{0,.41,.59},{0,.24,.76}};
	
 	MDP_3States perf_ns0;
 	MDP_3States perf_ns1;  	
 	MDP_3States perf_ns2;
 	
	// Transition Rates For Power Metric	
 	//Marcus:
	/*
 	double[][] pow_t0 = {{.99,.01,0},{.999,.001,0},{0,1,0}};
 	double[][] pow_t1 = {{.001,.999,0},{.12,.87,.01},{0,.999,.001}};
 	double[][] pow_t2 = {{0,1,0},{0,.67,.33},{0,.1,.9}};	
 	*/
 	
 	//Gun:
 	double[][] pow_t0 = {{.994,.05,0},{.999,.001,0},{0,1,0}};
 	double[][] pow_t1 = {{.001,.999,0},{.12,.85,.03},{0,.999,.001}};
 	double[][] pow_t2 = {{0,1,0},{0,.999,.001},{0,.44,56}};	
 	
 	
 	MDP_3States pow_ns0;
 	MDP_3States pow_ns1;  	
 	MDP_3States pow_ns2;
 	
 	//Selected Transition matrices
 	MDP_3States p_ns0;
 	MDP_3States p_ns1;
 	MDP_3States p_ns2;
	
	private double getAvg(int rec[]) {
		double sum = 0;
		for (int i=0; i<10; i++) {
			sum += rec[i];
		}
		return sum*1.0/10;
	}
	
	private void addUtil(int data) {
		utilRecord[util_ind] = data;
		util_ind = (util_ind+1)%10;
	}
	
	private void addInter(int data) {
		interRecord[inter_ind] = data;
		inter_ind = (inter_ind+1)%10;
	}
	
	public void onCreate() {
		super.onCreate();
		mActs = new LinkedList<Time>();
		mView = new InteractivityView(this);
		dvfs = new DVFSControl();
		
		//Setup 3 CPU frequency states
		ArrayList<Integer> freqModes = dvfs.getFrequencyScaleModes();
		int freqs[] = {freqModes.get(1),freqModes.get(4),freqModes.get(10)};
		//Setup transition matrices
		//Performance
	 	perf_ns0 = new MDP_3States(0,perf_t0,freqs);
	 	perf_ns1 = new MDP_3States(0,perf_t1,freqs);  	
	 	perf_ns2 = new MDP_3States(0,perf_t2,freqs); 	
	 	//Power
	 	pow_ns0 = new MDP_3States(0,pow_t0,freqs);
	 	pow_ns1 = new MDP_3States(0,pow_t1,freqs);  	
	 	pow_ns2 = new MDP_3States(0,pow_t2,freqs);		

		mRunning = true;
		
		
		//setup touch detection
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
						| WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
						| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
				PixelFormat.TRANSLUCENT);

	//	final GestureDetector gestureDetector = new GestureDetector(this,
	//			new InteractivityListener());
		View.OnTouchListener gestureListener = new View.OnTouchListener() {
			//Callback for touches, adds the current time to mActs
			public boolean onTouch(View v, MotionEvent event) {
				Time t = new Time();
				t.setToNow();
				mActs.add(t);
				return false;
			}
		};

		WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

		mView.setOnTouchListener(gestureListener);
		wm.addView(mView, params);

		Thread thr = new Thread(null, doWork, "Interactivity");
		thr.start();

	}
	
	public static void startOptPow() {
		curOptPow = true;
		curOptPerf = false;
	}
	
	public static void stopOptPow() {
		curOptPow = false;
	}
	
	public static void startOptPerf() {
		curOptPerf = true;
		curOptPow = false;
	}
	
	public static void stopOptPerf() {
		curOptPerf = false;
	}
	
	Runnable doWork = new Runnable() {
		@SuppressWarnings("deprecation")
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public void run() {
			
			//setup foreground so can be run for long periods
            Notification note=new Notification.Builder(getApplicationContext())
    		.setContentTitle("Optimizing")
    		.setSmallIcon(R.drawable.ic_launcher)
            .getNotification();
    
            startForeground(1338, note);
			
            while (mRunning) {
            	removeOld(15000);
            	// Depending on optimization selection
            	// Set next states of power or performance options
            	if (curOptPow) {
            		p_ns0 = pow_ns0;
            		p_ns1 = pow_ns1;
            		p_ns2 = pow_ns2;
            		
            		addInter(mActs.size());
            		double interAvg = getAvg(interRecord);
            		if (interAvg <= INTER_LOW) {
            			nextState = 0;
            		} else if (interAvg <= INTER_MID) {
            			nextState = 1;
            		} else {
            			nextState = 2;
            		}  	
            	} else if (curOptPerf) {
            		p_ns0 = perf_ns0;
            		p_ns1 = perf_ns1;
            		p_ns2 = perf_ns2; 
            		
            		addUtil(CPUInfo.getCPUUtilizationPct());
            		double utilAvg = getAvg(utilRecord);
            		if (utilAvg <= UTIL_LOW) {
            			nextState = 0;
            		} else if (utilAvg <= UTIL_MID) {
            			nextState = 1;
            		} else {
            			nextState = 2;
            		}
            	}
            	
            	if (curOptPow || curOptPerf) {
					// Do transtion
					switch (nextState) {
					case 0:
						p_ns0.doRun();
						guessFreq = p_ns0.getFreq();
						currState = p_ns0.getCurrState();
						p_ns1.setCurrState(currState);
						p_ns2.setCurrState(currState);
						break;
					case 1:
						p_ns1.doRun();
						guessFreq = p_ns1.getFreq();
						currState = p_ns1.getCurrState();
						p_ns0.setCurrState(currState);
						p_ns2.setCurrState(currState);
						break;
					case 2:
						p_ns2.doRun();
						guessFreq = p_ns2.getFreq();
						currState = p_ns2.getCurrState();
						p_ns0.setCurrState(currState);
						p_ns2.setCurrState(currState);
						break;
					default:
						break;
					}
					dvfs.setCPUFrequency(guessFreq);
					
					//TODO
					System.out.println("CurrSetting-> State: "+currState+", Pow: "+curOptPow+ ", Perf: "+curOptPerf);
					
					SystemClock.sleep(5000);
				}
            }
	
			stopForeground(true);
			mActs = null;
			InteractivityService.this.stopSelf();
		}
	};
	
	//removes any times from mActs greater than age
	private void removeOld(long age) {
		Time t = new Time();
		t.setToNow();
		long cur = t.toMillis(false);
		while (mActs.peek() != null) {
			if (mActs.peek().toMillis(false) + age < cur) {
				mActs.remove();
			} else {
				break;
			}
		}

	}

	public void onDestroy() {
		mRunning = false;
		mView.setOnTouchListener(null);
	}
	
	class InteractivityView extends View {

		public InteractivityView(Context context) {
			super(context);
		}

	}

	class InteractivityListener extends SimpleOnGestureListener {

		public boolean onTouchEvent(MotionEvent e) {
			System.out.println("Different Touch");
			Toast.makeText(getBaseContext(), "Different Touch! ", 1)
					.show();
			return false;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	private final IBinder mBinder = new InteractBinder();

	public class InteractBinder extends Binder {
		InteractivityService getService() {
			return InteractivityService.this;
		}
	}
}
