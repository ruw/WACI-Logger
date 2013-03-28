package cmu.waci.logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.app.ActivityManager;
import android.app.ApplicationErrorReport.BatteryInfo;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class LoggerService extends Service{
	private static final String TAG = "LoggerService";

	private NotificationManager notMan;
    private WifiManager wifiMan;
    private ConnectivityManager connMan;
    private TelephonyManager telMan;
    private LocationManager locMan;
    private ActivityManager actMan;
    private PackageManager packMan;
    private PowerManager powMan;
    private AudioManager audMan;
    private SensorManager sensMan;//?
    private NetInfo mNetInfo;//?
    private BatteryInfo battInfo;
    private SignalStrength sigStrength;
 //   private SensorInfo mSensorInfo;
    private CPUInfo mCpuInfo;
    private List<Sensor> sensAcc;
    
    private DVFSControl dvfs;
	
    //public static DVFSControl DVFSController;
    
    
    public void onCreate() {
    	notMan = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    	wifiMan = (WifiManager) getSystemService(WIFI_SERVICE);
    	connMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        telMan = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        locMan = (LocationManager) getSystemService(LOCATION_SERVICE);
        actMan = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        powMan = (PowerManager) getSystemService(POWER_SERVICE);
        packMan = getPackageManager();
        audMan = (AudioManager) getSystemService(AUDIO_SERVICE);
        sensMan = (SensorManager) getSystemService(SENSOR_SERVICE);
        audMan = (AudioManager) getSystemService(AUDIO_SERVICE);
       
        //DVFSController  =  dvfs;
        dvfs  =  new  DVFSControl();
   //     sigStrength = new SignalStrength();
        
        mNetInfo = new NetInfo(wifiMan, connMan, telMan);
        Toast.makeText(this, "poop ",1).show();
        System.out.println("ppop");
        Thread thr = new Thread(null, doWork, "Logger service");
        thr.start();
    }
    
    /**
     * The function that runs in our worker thread
     */
    Runnable doWork = new Runnable() {
        public void run() {
        	boolean mExternalStorageAvailable = false;
        	boolean mExternalStorageWriteable = false;
        	String state = Environment.getExternalStorageState();
        	System.out.println("wat");

        	if (Environment.MEDIA_MOUNTED.equals(state)) {
        	    // We can read and write the media
        	    mExternalStorageAvailable = mExternalStorageWriteable = true;
        	} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
        	    // We can only read the media
        	    mExternalStorageAvailable = true;
        	    mExternalStorageWriteable = false;
        	} else {
        	    // Something else is wrong. It may be one of many other states, but all we need
        	    //  to know is we can neither read nor write
        	    mExternalStorageAvailable = mExternalStorageWriteable = false;
        	}
        	System.out.println("wat2");

        	//can't open a file, abort
        	if(!(mExternalStorageAvailable && mExternalStorageWriteable)) {
        		Log.e(TAG, "Could not get external storage");
        		LoggerService.this.stopSelf();
        	}
        	System.out.println(getExternalFilesDir(null));
        	File log = new File(getExternalFilesDir(null), "log3.txt");
            
        	BufferedWriter out = null;
        	try {
            	out = new BufferedWriter(
            			new FileWriter(log.getAbsolutePath(), log.exists()));
            } catch(IOException e) {
            	Log.e(TAG, "Exception opening log file",e);
            	LoggerService.this.stopSelf();
            }
                    
        	
          
            for(int i=0; i<10;i++) {
            
	            try{
	            	String outputString = "";
	            	System.out.println("logging: "+ i);
	                
	                
	                //check the brightness
	                int curBrightness;
	            	curBrightness = android.provider.Settings.System.getInt(
	                        getContentResolver(), 
	                        android.provider.Settings.System.SCREEN_BRIGHTNESS);
	                
	            	outputString = outputString.concat(
	            			String.format("%d", curBrightness));
	            	
	                //Get battery data
	                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	                Intent batteryStatus = registerReceiver(null, ifilter);
	                
	                
	             // Are we charging / charged?
	                int batStat = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
	                boolean isCharging = batStat == BatteryManager.BATTERY_STATUS_CHARGING ||
	                                     batStat == BatteryManager.BATTERY_STATUS_FULL;
	
	                // How are we charging?
	                int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
	          //      boolean usbCharge = chargePlug == BATTERY_PLUGGED_USB;
	          //     boolean acCharge = chargePlug == BATTERY_PLUGGED_AC;
	                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
	                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
	
	                float batteryPct = level / (float)scale;
	               
	                //TODO Accelerometer
	                sensAcc = sensMan.getSensorList(Sensor.TYPE_ACCELEROMETER);
	                //System.out.println("Acc "+sensAcc.values[0]+","+sensAcc.values[1]+","+sensAcc.values[2]);
	                
	                int curr;
	                curr = dvfs.getCPUFrequency();
	                
	                /*
	                System.out.println("CPU Freq: " + curr);
	                System.out.println("Try Step Down");
	            	dvfs.stepDownFrequency();
	                System.out.println("Done Step Down"); 
	                */
	                
	                //TODO figure out API calls for network sig strength
	                // Get network data signal strengths
	            /*    outputString = outputString.concat(
	                        String.format(",%d,%d,%d", mNetInfo.getCdmaSigStrength(),
	                        mNetInfo.getEvdoSigStrength(),
	                        mNetInfo.getGsmSigStrength()));
	             */   
	                outputString = outputString.concat(
	                        String.format(",%s,%f,%d,%d,%d,%d,%d,%d,%d\n",
	                        mNetInfo.getCallStatus(),  batteryPct,
	                        batStat,
	                        CPUInfo.getCPUUtilizationPct(),
	                        CPUInfo.getContextSwitches(),
	                        CPUInfo.getProcessesCreated(),
	                        CPUInfo.getProcessesRunning(),
	                        CPUInfo.getProcessesBlocked(), powMan.isScreenOn() ? 1 : 0));
	                  //      mMoving ? 1 : 0)); TODO figure out accelerometer and put this back in
	                
	                //TODO ambient light
	               
	                
	                out.write(outputString);
	                SystemClock.sleep(500);
	
	            } catch (IOException e) {
	                Log.e(TAG, "Exception appending to log file",e);
	            } catch (SettingNotFoundException e) {
	               // e.printStackTrace();
	            	Log.e(TAG, "Brightness setting not found");
	            }
            }
            
            try {
            	
            	out.close();
            } catch(IOException e) {
            	
            }
            
            LoggerService.this.stopSelf();
        }
    };
    
    
    
    @Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}
    
    
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
	
    public class LocalBinder extends Binder {
        LoggerService getService() {
            return LoggerService.this;
        }
    }
	

}
