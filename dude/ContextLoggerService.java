package csu.research;


import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * This is an example of implementing an application service that will run in
 * response to an alarm, allowing us to move long duration work out of an intent
 * receiver.
 * 
 */
public class ContextLoggerService extends Service implements AccelerometerListener {
    private final String TAG = "ContextLoggerService";
    private LocationDatabase mLocDatabase;
    private NotificationManager mNM;
    private WifiManager mWM;
    private ConnectivityManager mCM;
    private TelephonyManager mTM;
    private LocationManager mLM;
    private ActivityManager mAM;
    private PackageManager mPackM;
    private PowerManager mPM;
    private AudioManager mAudioM;
    private AccelerometerManager mAccM;
    private NetInfo mNetInfo;
    private BatteryInfo mBattInfo;
    private SensorInfo mSensorInfo;
    private CpuInfo mCpuInfo;
    private LocationInfo mLocInfo;
    private boolean mLocationCheck;
    private boolean mMoving = false;
    private int mScreenBrightness;
    @Override
    public void onCreate() {
        // SERVICES
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mWM = (WifiManager) getSystemService(WIFI_SERVICE);
        mCM = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mTM = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mLM = (LocationManager) getSystemService(LOCATION_SERVICE);
        mAM = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mPackM = getPackageManager();
        mAudioM = (AudioManager) getSystemService(AUDIO_SERVICE);
        mAccM = new AccelerometerManager(getApplicationContext());
        // CLASSES
        mNetInfo = new NetInfo(mWM, mCM, mTM);
        mBattInfo = new BatteryInfo(getApplicationContext());
        mSensorInfo = new SensorInfo(getApplicationContext());
        mCpuInfo = new CpuInfo();
        mLocInfo = new LocationInfo(mLM);
        // DATABASE
        if (!loadDatabase()) {
            Toast.makeText(getBaseContext(), "New database created!", Toast.LENGTH_LONG).show();
        }
        // VARIABLES
        // show the icon in the status bar
        showNotification();
        // if (!mPM.isScreenOn()) {
        mLocationCheck = mLocInfo.checkForLocation();
        // }
  
        // Start accelerometer
        if (mAccM.isSupported()) {
            mAccM.startListening(this);
        }
        IntentFilter filt = new IntentFilter();

        filt.addAction(Intent.ACTION_SCREEN_OFF);
        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.
        Thread thr = new Thread(null, mTask, "Context Logger service");

        thr.start();
        // The the user we started
        // Toast.makeText(this, R.string.alarm_service_started,
        // Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the notification -- we use the same ID that we had used to
        // start it
        mNM.cancel(R.string.iconized);
        if (mAccM.isListening()) {
            mAccM.stopListening();
        }
        mSensorInfo.unregisterListeners();
        // Tell the user we stopped.
        // Toast.makeText(this, R.string.alarm_service_stopped,
        // Toast.LENGTH_SHORT).show();
        
        if (!saveDatabase()) {
            Toast.makeText(getBaseContext(),
                    "Error occurred saving location database!",
                    Toast.LENGTH_LONG);
        }
    }

    /**
     * The function that runs in our worker thread
     */
    Runnable mTask = new Runnable() {
        public void run() {
            String foregroundApp = "";
            String outputString = "";
            long oldWifiTraffic;
            long newWifiTraffic;
            long wifiTrafficDiff;
            Calendar calendar = Calendar.getInstance();
            File log = new File(
                    String.format("%s/ContextLogger", 
                    Environment.getExternalStorageDirectory()),
                
                    String.format("ContextLoggerLog_%d.txt",
                    calendar.get(Calendar.DAY_OF_MONTH)));
            
            try {
                BufferedWriter out = new BufferedWriter(
                        new FileWriter(log.getAbsolutePath(), log.exists()));
            
                mCpuInfo.getCPUStats();
                // foregroundApp = getForegroundAppName();
                
                /*
                 * ATTRIBUTES:
                 * Day of Week
                 * Time of Day (Minutes)
                 * Latitude
                 * Longitude
                 * GPS Satellite Count (measure of GPS signal strength)
                 * Number of Wifi APs Available
                 * WiFi RSSI (measure of WiFi signal strength)
                 * CDMA Data Signal Strength
                 * EVDO Data Signal Strength
                 * GSM Data Signal Strength
                 * Call State
                 * Battery Level
                 * Battery Status
                 * CPU Utilization
                 * Context Switches
                 * Processes Created
                 * Processes Running
                 * Processes Blocked
                 * Screen On?
                 * User Moving?
                 * Ambient Light
                 * 
                 * TARGET VARIABLES:
                 * Data Needed?
                 * Coarse Location Needed?
                 * Fine Location Needed?
                 */

                /*
                 * MODIFY LOGGER TO LOG START/STOP OF INTERACTION SESSION
                 * DURING INTERACTION SESSION
                 */             
                mScreenBrightness = (int) 
                        android.provider.Settings.System.getFloat(
                        getContentResolver(), 
                        android.provider.Settings.System.SCREEN_BRIGHTNESS);
                
                // get original traffic so difference can be calculated
                oldWifiTraffic = mNetInfo.readWifiTraffic();
                int minuteOfDay = (calendar.get(Calendar.HOUR_OF_DAY) * 60)
                        + calendar.get(Calendar.MINUTE);

                if (mLocationCheck == false) {
                    // wait for 20 seconds.  
                    long endTime = System.currentTimeMillis() + 20 * 1000;

                    while (System.currentTimeMillis() < endTime) {
                        ;
                    }
                    
                    outputString = String.format("%d,%d,%s,%s", 
                            calendar.get(Calendar.DAY_OF_WEEK), minuteOfDay, "?", // latitude not available
                            "?"); // longitude not available 
                } else {
                    Location loc = mLocInfo.getCurrentLocation();
                    // Check for best location for 20 seconds.  
                    long endTime = System.currentTimeMillis() + 20 * 1000;

                    while (loc == null && System.currentTimeMillis() < endTime) {
                        loc = mLocInfo.getCurrentLocation();
                        // Log.d(TAG, "loc = null");
                    }
                    mLocInfo.removeUpdates();
                    outputString = String.format("%d,%d", 
                            calendar.get(Calendar.DAY_OF_WEEK), minuteOfDay);
                    
                    if (loc != null) {
                        // add location to database
                        mLocDatabase.addLocation(loc);
                        outputString = outputString.concat(
                                String.format(",%.6f,%.6f", loc.getLatitude(),
                                loc.getLongitude()));
                    } else {
                        outputString = outputString.concat(",?,?");
                    }
                            
                }
                if (mLocInfo.isGpsEnabled()) {
                    outputString = outputString.concat(
                            String.format(",%d", mLocInfo.getNumSatellites()));
                } else {
                    outputString = outputString.concat(",?");
                }
                if (mNetInfo.isWifiEnabled()) {
                    outputString = outputString.concat(
                            String.format(",%d", mNetInfo.getNumAPs()));
                } else {
                    outputString = outputString.concat(",?"); 
                }
                if (mNetInfo.isWifiConnected()) {
                    outputString = outputString.concat(
                            String.format(",%d", mNetInfo.getWifiSignalLevel()));
                } else {
                    outputString = outputString.concat(",?"); 
                }
                // Get network data signal strengths
                outputString = outputString.concat(
                        String.format(",%d,%d,%d", mNetInfo.getCdmaSigStrength(),
                        mNetInfo.getEvdoSigStrength(),
                        mNetInfo.getGsmSigStrength()));
                
                outputString = outputString.concat(
                        String.format(",%d,%d,%d,%.1f,%d,%d,%d,%d,%d,%d",
                        mNetInfo.getCallState(), mBattInfo.getBatteryLevel(),
                        mBattInfo.getBatteryStatus(),
                        mCpuInfo.getUtilizationPct(),
                        mCpuInfo.getContextSwitches(),
                        mCpuInfo.getProcessesCreated(),
                        mCpuInfo.getProcessesRunning(),
                        mCpuInfo.getProcessesBlocked(), mPM.isScreenOn() ? 1 : 0,
                        mMoving ? 1 : 0));
                
                if (mSensorInfo.getAmbientLight() == -1) {
                    outputString = outputString.concat(",?");
                } else {
                    outputString = outputString.concat(
                            String.format(",%d", mSensorInfo.getAmbientLight()));
                }
                // Data Needed?
                newWifiTraffic = mNetInfo.readWifiTraffic();                
               
                if (oldWifiTraffic != -1 && newWifiTraffic != -1) {
                    wifiTrafficDiff = newWifiTraffic - oldWifiTraffic;
                } else {
                    wifiTrafficDiff = 0;
                }
                if (wifiTrafficDiff > 5
                        || mNetInfo.isNetworkDataActive() == true
                        || internetBeingUsed()) {
                    outputString = outputString.concat(",1");
                } else {
                    outputString = outputString.concat(",0");
                }
                // Coarse Location Needed?
                if (coarseLocBeingUsed()) {
                    outputString = outputString.concat(",1");
                } else {
                    outputString = outputString.concat(",0");
                }
                // Fine Location Needed?
                if (fineLocBeingUsed()) {
                    outputString = outputString.concat(",1\n");
                } else {
                    outputString = outputString.concat(",0\n");
                }
                // outputString = outputString.concat(String.format(",%d,%d,%.1f,%.1f,%.1f,%d,%d,%d,%d,%s,%d,%d,%d", 
                // mBattInfo.getBatteryLevel(),
                // mBattInfo.getBatteryStatus(),
                // mBattInfo.getBatteryTemperatureInDegF(),
                // mBattInfo.getBatteryVoltageInVolts(),
                // mCpuInfo.getUtilizationPct(),
                // mCpuInfo.getContextSwitches(),
                // mCpuInfo.getProcessesCreated(),
                // mCpuInfo.getProcessesRunning(),
                // mCpuInfo.getProcessesBlocked(),
                // foregroundApp == null ? "?" : foregroundApp,
                // mPM.isScreenOn() ? 1 : 0,
                // mScreenBrightness,
                // mMoving ? 1 : 0));
                
                // if (mSensorInfo.getProximity() == -1) {
                // outputString = outputString.concat(",?");
                // }
                // else {
                // outputString = outputString.concat(String.format(",%d", mSensorInfo.getProximity()));
                // }
                
                // if (mSensorInfo.getAzimuth() == -1) {
                // outputString = outputString.concat(",?");
                // }
                // else {
                // outputString = outputString.concat(String.format(",%d", mSensorInfo.getAzimuth()));
                // }
                
                // if (mSensorInfo.getPitch() == -181) {
                // outputString = outputString.concat(",?");
                // }
                // else{
                // outputString = outputString.concat(String.format(",%d", mSensorInfo.getPitch()));
                // }
                
                // if (mSensorInfo.getRoll() == -91) {
                // outputString = outputString.concat(",?");
                // }
                // else {
                // outputString = outputString.concat(String.format(",%d", mSensorInfo.getRoll()));
                // } 
                // // get call status
                // outputString = outputString.concat(String.format(",%d", mNetInfo.getCallStatus()));
                
                // // get DSP (music on?) status
                // if (mAudioM.isMusicActive() == true) {
                // outputString = outputString.concat(String.format(",1\n"));
                // }
                // else {
                // outputString = outputString.concat(String.format(",0\n"));
                // }
                
                out.write(outputString);
                out.close();
 
            }
            // // sleep for 30 seconds.
            // long endTime = System.currentTimeMillis() + 15 * 1000;
            // while (System.currentTimeMillis() < endTime) {
            // synchronized (mBinder) {
            // try {
            // mBinder.wait(endTime - System.currentTimeMillis());
            // } catch (Exception e) {
            // }
            // }
            // }
            
            // Done with our work... stop the service!
            ContextLoggerService.this.stopSelf();
        }
    };

    /**
     * AccelerometerListener callback methods
     */
    public void onShake(float force) {}

    public void onAccelerationChanged(float x, float y, float z) {}

    public void onMove(float metric) {
        mMoving = true;
    }

    public void onStationary() {
        mMoving = false;
    }

    // private boolean isRunningService(String processname){
    // if(processname==null)
    // return false;
    //
    // RunningServiceInfo service;
    //
    // List <RunningServiceInfo> l = mAM.getRunningServices(9999);
    // Iterator <RunningServiceInfo> i = l.iterator();
    // while(i.hasNext()){ 
    // service = i.next();
    // if(service.process.equals(processname))
    // return true;
    // }
    //
    // return false;
    // }
    //
    // private String getForegroundAppName() {
    // RunningAppProcessInfo result=null, info=null;
    //
    // List <RunningAppProcessInfo> l = mAM.getRunningAppProcesses();
    // Iterator <RunningAppProcessInfo> i = l.iterator();
    // while(i.hasNext()){
    // info = i.next();
    // if(info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    // && !isRunningService(info.processName)){
    // result=info;
    // break;
    // }
    // }
    // if (result != null) {
    // return result.processName;
    // }
    // else {
    // return null;
    // }
    // }
    
    private boolean fineLocBeingUsed() {
        RunningAppProcessInfo info = null;
        List <RunningAppProcessInfo> l = mAM.getRunningAppProcesses();
        Iterator <RunningAppProcessInfo> i = l.iterator();

        while (i.hasNext()) {
            info = i.next();
            if (info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (mPackM.checkPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION, 
                        info.processName)
                                == PackageManager.PERMISSION_GRANTED
                                        && !info.processName.equals(
                                                "com.android.phone")
                                                && !info.processName.equals(
                                                        "com.android.systemui")
                                                        && !info.processName.equals(
                                                                "csu.research")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean coarseLocBeingUsed() {
        RunningAppProcessInfo info = null;
        List <RunningAppProcessInfo> l = mAM.getRunningAppProcesses();
        Iterator <RunningAppProcessInfo> i = l.iterator();

        while (i.hasNext()) {
            info = i.next();
            if (info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (mPackM.checkPermission(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION, 
                        info.processName)
                                == PackageManager.PERMISSION_GRANTED
                                        && !info.processName.equals(
                                                "com.android.phone")
                                                && !info.processName.equals(
                                                        "com.android.systemui")
                                                        && !info.processName.equals(
                                                                "csu.research")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean internetBeingUsed() {
        RunningAppProcessInfo info = null;
        List <RunningAppProcessInfo> l = mAM.getRunningAppProcesses();
        Iterator <RunningAppProcessInfo> i = l.iterator();

        while (i.hasNext()) {
            info = i.next();
            if (info.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (mPackM.checkPermission(android.Manifest.permission.INTERNET, 
                        info.processName)
                        == PackageManager.PERMISSION_GRANTED
                                && !info.processName.equals("com.android.phone")
                                && !info.processName.equals(
                                        "com.android.systemui")
                                        && !info.processName.equals(
                                                "csu.research")) {
                    return true;
                }
            }
        }
        return false;
    }

    public LocationDatabase getLocationDatabase() {
        return mLocDatabase;
    }

    private boolean saveDatabase() {
        try {
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED)) {
                String filePath = String.format("%s/ContextLogger", 
                        Environment.getExternalStorageDirectory());

                if (!new File(filePath).exists()) {
                    new File(filePath).mkdirs();
                }
                if (new File(filePath).canWrite()) {
                    return mLocDatabase.save(filePath);
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean loadDatabase() {
        try {
            if (Environment.getExternalStorageState().equals(
                    Environment.MEDIA_MOUNTED)) {
                String filePath = String.format("%s/ContextLogger/Locations.ser", 
                        Environment.getExternalStorageDirectory());

                if (new File(filePath).exists()) {
                    FileInputStream fis = new FileInputStream(filePath);
                    ObjectInputStream in = new ObjectInputStream(fis);

                    mLocDatabase = (LocationDatabase) in.readObject();
                    in.close();
                    return true;
                }
            }
            mLocDatabase = new LocationDatabase();
            return false;
        } catch (Exception e) {
            mLocDatabase = new LocationDatabase();
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the
        // expanded notification
        CharSequence text = getText(R.string.app_name);
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.plan_48, text,
                System.currentTimeMillis());
        Intent notifyIntent = new Intent(this, ContextLogger.class);

        notifyIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        // The PendingIntent to launch our activity if the user selects this
        // notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                notifyIntent, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.iconized), text,
                contentIntent);
        // Send the notification.
        // We use a layout id because it is a unique number. We use it later to
        // cancel.
        mNM.notify(R.string.iconized, notification);
    }

    /**
     * This is the object that receives interactions from clients. See
     * RemoteService for a more complete example.
     */
    private final IBinder mBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
    };
}
