package csu.research.AURA;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Vector;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


public class AppMonitor extends Service {
    private static final int NUM_SATELLITES = 255;
    public final static String GLOBAL_TOUCH_EVENT = "android.app.GLOBAL_TOUCH_EVENT";
    public final static String GLOBAL_FOCUS_EVENT = "android.app.GLOBAL_FOCUS_EVENT";
    public final static String SCREENBRIGHTNESS_CHANGE_EVENT = "android.app.SCREENBRIGHTNESS_CHANGE_EVENT";
    public final static String GLOBAL_CONFIG_EVENT = "android.inputmethodservice.GLOBAL_CONFIG_EVENT";
    public final static String GLOBAL_KEY_EVENT = "android.inputmethodservice.GLOBAL_KEY_EVENT";
    public final static String GLOBAL_SOFTKEY_EVENT = "android.inputmethodservice.GLOBAL_SOFTKEY_EVENT";
    public final static String APPMONITOR_GET_DATABASE_INFO_EVENT = "csu.research.AURA.APPMONITOR_GET_DATABASE_INFO_EVENT";
    public final static String APPMONITOR_DATABASE_INFO_EVENT = "csu.research.AURA.APPMONITOR_DATABASE_INFO_EVENT";
    public final static int APPMONITOR_NOTIFICATION_ID = 12345;
    public final static int EVENT_AVERAGE_WINDOW = 6;
    public final static int NUM_EVENTS_CLASSIFIER = 10;
    private final IBinder mBinder = new AppMonitorBinder();
    public class AppMonitorBinder extends Binder {
        public AppMonitor getService() {
            return AppMonitor.this;
        }
    }
    private boolean isInitialized = false;
    private boolean isAlgorithmRunning = false;
    private DVFSControl dvfs;
    private boolean notificationsEnabled;
    private String currentFocusedAppName;
    private boolean isHardKeyboardOpen;
    private int touchEvents;
    private int keyEvents;
    private int numLocChanges;
    private long previousTouchEventTime;
    private long previousKeyEventTime;
    private long eventStartTime;
    private ApplicationDatabase appDatabase;
    private ApplicationInfo currentFocusedApp;
    private ArrayList<Long> deltaTouchEventTimeList = new ArrayList<Long>();
    private ArrayList<Long> deltaKeyEventTimeList = new ArrayList<Long>();
    private int startMinuteOfDay;
    private Thread mRunDVFSAlgorithm;
    private int mBatteryLevel = -1;
    private boolean mTrackBatteryLevelChanges = false;
    private ArrayList<Float> mAverageWindowedEventTime = new 
            ArrayList<Float>(EVENT_AVERAGE_WINDOW);
    private ArrayList<Float> mTimeAverageWindowedEventTime = new 
            ArrayList<Float>(EVENT_AVERAGE_WINDOW);
    // journal additions
    int numQStateDims = 2;
    int numActions = 2;
    int[] QState;
    int[] newQState;
    int action; // 1 = change state, 0 = don't
    double epsilon;
    double temp;
    double alpha;
    double gamma;
    double lambda;
    double reward;
    int epochs;
    public int epochsdone;
    Vector trace = new Vector();
    int[] saPair;
    boolean randomEps;
    @SuppressWarnings("unused")
    private static boolean resetBrightness = false;
    private static int highCount = 0;
    private static int lowCount = 0;
    private static int batteryLevelChanged = 0;
    private static float totalPowerSaved = 0F;
    private static float totalPowerSavedTime = 0F;
    private static float totalEnergySaved = 0F;
    private static float totalSOCSaved = 0F;
    private static float totalPowerNominal = 0F;
    private static float avgPowerSavedPct = 0F;
    private static float avgCpuUtil = 0F;
    private static int missCount = 0;
    private static int hitCount = 0;
    public static Toast LastStatsToast = null;
    public static ApplicationDatabase AppDatabase;
    public static DVFSControl DVFSController;
    public static ControlAlgorithm Algorithm = ControlAlgorithm.NORMAL_MDP;
    private PackageManager mPackageManager;
    private NetInfo mNetInfo;
    private PowerManager mPowerManager;
    private LocationManager mLocationManager;
    private ActivityManager mActivityManager;
    private NotificationManager mNotificationManager;
    private AudioManager mAudioM;
    private Handler mLogHandler;
    private boolean mThreadDone;
    private int mCpuTotalPct;
    private int mScreenOn;
    private int mScreenBrightness;
    private Thread mParamLogThread;
    private AppClassification previousClassification;
    private int unchangedClassCount;
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public void setNotifications(boolean areEnabled) {
        notificationsEnabled = areEnabled;
    }

    public boolean areNotificationsEnabled() {
        return notificationsEnabled;
    }

    private void resetStats() {
        mCpuTotalPct = 0;
        mScreenOn = 0;
        mScreenBrightness = 0;
        previousClassification = AppClassification.NotClassified;
        unchangedClassCount = 0;
        touchEvents = 0;
        keyEvents = 0;
        previousKeyEventTime = 0L;
        previousTouchEventTime = 0L;
        deltaTouchEventTimeList = new ArrayList<Long>();
        deltaKeyEventTimeList = new ArrayList<Long>();
    }

    public ApplicationDatabase getApplicationDatabase() {
        return appDatabase;
    }

    public boolean isHardKeyboardOpen() {
        return isHardKeyboardOpen;
    }

    @Override
    public void onLowMemory() {
        setForeground(true);
        Toast.makeText(getBaseContext(), "App Monitor Service Low Memory Warning!", Toast.LENGTH_LONG).show();
        super.onLowMemory();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(getBaseContext(), "App Monitor Service Destroyed!", Toast.LENGTH_LONG).show();
        if (!saveDatabase()) {
            Toast.makeText(getBaseContext(),
                    "Error occurred saving application database!",
                    Toast.LENGTH_LONG);
        }
        shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {   
        if (intent.getAction().equals("KILL_APP_MONITOR")) {
            stopSelf();
            return true;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        if (!isInitialized) {
            initialize();
        }
        Toast.makeText(getBaseContext(), "App Monitor Service Created!", Toast.LENGTH_LONG).show();
        AppDatabase = appDatabase;
        DVFSController = dvfs;
        Algorithm = ControlAlgorithm.NORMAL_MDP;
        super.onCreate();
    }

    private boolean saveDatabase() {
        try {
            if (appDatabase.isDirty()
                    && Environment.getExternalStorageState().equals(
                            Environment.MEDIA_MOUNTED)) {
                String filePath = String.format("%s/AppMonitor", 
                        Environment.getExternalStorageDirectory());

                if (!new File(filePath).exists()) {
                    new File(filePath).mkdirs();
                }
                if (new File(filePath).canWrite()) {
                    return appDatabase.save(filePath);
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
                String filePath = String.format("%s/AppMonitor/AppStats.ser", 
                        Environment.getExternalStorageDirectory());

                if (new File(filePath).exists()) {
                    FileInputStream fis = new FileInputStream(filePath);
                    ObjectInputStream in = new ObjectInputStream(fis);

                    appDatabase = (ApplicationDatabase) in.readObject();
                    in.close();
                    return true;
                }
            }
            appDatabase = new ApplicationDatabase();
            return false;
        } catch (Exception e) {
            appDatabase = new ApplicationDatabase();
            return false;
        }
    }

    public boolean isAlgorithmActive() {
        return isAlgorithmRunning;
    }

    private void initialize() {
        if (!loadDatabase()) {
            Toast.makeText(getBaseContext(), "Error occurred loading default database!", Toast.LENGTH_LONG).show();
        }
        mLogHandler = new Handler();
        mPackageManager = getPackageManager();
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mActivityManager = (ActivityManager) getSystemService(
                Context.ACTIVITY_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        TelephonyManager cellManager = (TelephonyManager) getSystemService(
                TELEPHONY_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                CONNECTIVITY_SERVICE);

        mAudioM = (AudioManager) getSystemService(AUDIO_SERVICE);
        mNetInfo = new NetInfo(wifiManager, connectivityManager, cellManager);
        mThreadDone = false;
        // journal additions
        QState = new int[numQStateDims];
        newQState = new int[numQStateDims];
        epsilon = 0.1;
        temp = 1;
        alpha = 1;
        gamma = 0.1;
        lambda = 0.1;
        randomEps = false;
        dvfs = new DVFSControl();
        resetDVFSThread();
        setForeground(true);        
        notificationsEnabled = true;
        currentFocusedAppName = "";
        currentFocusedApp = new ApplicationInfo("", "");
        isHardKeyboardOpen = false;
        IntentFilter filt = new IntentFilter();

        filt.addAction(GLOBAL_CONFIG_EVENT);
        filt.addAction(GLOBAL_FOCUS_EVENT);
        filt.addAction(GLOBAL_KEY_EVENT);
        filt.addAction(GLOBAL_SOFTKEY_EVENT);
        filt.addAction(GLOBAL_TOUCH_EVENT);
        filt.addAction(Intent.ACTION_SCREEN_OFF);
        filt.addAction(Intent.ACTION_SCREEN_ON);
        filt.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(
                new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setForeground(true);
                long currentTime = SystemClock.elapsedRealtime();

                startMinuteOfDay = (Calendar.HOUR_OF_DAY * 3600)
                        + Calendar.MINUTE;
                if (intent.getAction().equals(GLOBAL_CONFIG_EVENT)) {
                    isHardKeyboardOpen = intent.getBooleanExtra(
                            "ishardkeyboardopen", false);
                } else if (intent.getAction().equals(GLOBAL_FOCUS_EVENT)
                        && intent.getBooleanExtra("hasfocus", false)) {
                    String activity = intent.getStringExtra("activity");

                    if (activity != null) {
                        if (!currentFocusedAppName.equals(activity)) {
                            if (eventStartTime != 0L
                                    && !appDatabase.getIgnoredAppNames().contains(
                                            currentFocusedAppName)) {
                                if (isAlgorithmRunning) {
                                    stopDVFSAlgorithm();
                                    String low = String.format(
                                            "Low Frequency: %.1f %%",
                                            lowCount * 100F
                                            / Math.max((lowCount + highCount), 1));
                                    String high = String.format(
                                            "\nHigh Frequency: %.1f %%",
                                            highCount * 100F
                                            / Math.max((lowCount + highCount), 1));
                                    String misses = String.format(
                                            "\nMiss Ratio: %.1f %%",
                                            missCount * 100F
                                            / Math.max((missCount + hitCount), 1));
                                    String power = String.format(
                                            "\nPower Saved: %.2f mW",
                                            totalPowerSaved);

                                    totalEnergySaved = (totalPowerSaved
                                            * totalPowerSavedTime)
                                                    / 1000F;
                                    String energy = String.format(
                                            "\nEnergy Saved: %.2f J",
                                            totalEnergySaved);

                                    avgPowerSavedPct = totalPowerSaved * 100
                                            / totalPowerNominal;
                                    String powerPct = String.format(
                                            "\nPower Avg Pct Saved: %.1f %%",
                                            avgPowerSavedPct);
                                    String powerNominal = String.format(
                                            "\nTotal Power Nominal: %.2f mW",
                                            totalPowerNominal);

                                    totalSOCSaved = BatteryModels.getPercentSOCSaved(
                                            totalPowerSaved);
                                    String soc = String.format(
                                            "\nSOC Saved: %.3f %%",
                                            totalSOCSaved);
                                    String cpuUtil = String.format(
                                            "\nAvg CPU Util: %.1f %%",
                                            avgCpuUtil
                                                    / Math.max(
                                                            (lowCount
                                                                    + highCount), 
                                                                    1));
                                    String batt = String.format(
                                            "\nBattery SOC Changed: %d %%",
                                            batteryLevelChanged);
                                    String temp = low + high + misses + power
                                            + powerNominal + energy + powerPct
                                            + soc + cpuUtil + batt;

                                    LastStatsToast = Toast.makeText(
                                            getApplicationContext(), temp,
                                            Toast.LENGTH_LONG);
                                    LastStatsToast.show();
                                }
                                float touchMean = calculateMean(
                                        deltaTouchEventTimeList);
                                float keyMean = calculateMean(
                                        deltaKeyEventTimeList);
                                float touchSD = calculateStandardDev(
                                        deltaTouchEventTimeList, touchMean);
                                float keySD = calculateStandardDev(
                                        deltaKeyEventTimeList, keyMean);
                                float touchMedian = calculateMedian(
                                        deltaTouchEventTimeList);
                                float keyMedian = calculateMedian(
                                        deltaKeyEventTimeList);
                                int currentMinuteOfDay = (Calendar.HOUR_OF_DAY
                                        * 3600)
                                                + Calendar.MINUTE;

                                currentFocusedApp.addTouchStats(touchMean,
                                        touchSD, touchMedian);
                                currentFocusedApp.addKeyStats(keyMean, keySD,
                                        keyMedian);
                                currentFocusedApp.addUsageStats(startMinuteOfDay,
                                        currentMinuteOfDay, 
                                        (float) (currentTime - eventStartTime)
                                        / 1000F);
                                // currentFocusedApp.addMiscStats((float)(currentTime  -eventStartTime) / 1000F, 
                                // mCpuTotalPct, mScreenOn, mScreenBrightness, mWifiConnected, mWifiHasTraffic, mWifiTraffic,
                                // mOffHook, mDataHasTraffic, mDataTraffic, mAudioPlaying, mBluetoothOn, mGpsEnabled);
                                String temp = currentFocusedAppName.toUpperCase();

                                if (temp.contains(".")) {
                                    temp = temp.substring(
                                            temp.lastIndexOf(".") + 1,
                                            temp.length());
                                }
                                if (lowCount > 0 || highCount > 0) {
                                    float den = lowCount + highCount;

                                    postNotification(
                                            String.format("%s  - [%.1f, %.1f]!",
                                            temp, (lowCount * 100 / den),
                                            highCount * 100 / den),
                                            R.drawable.monitor_icon);
                                    lowCount = 0;
                                    highCount = 0;
                                } else {
                                    postNotification(
                                            String.format("%s Updated!", temp),
                                            R.drawable.monitor_icon);
                                }
                                // journal addition
                                // String filePath = 
                                String.format(
                                        "%s/AppMonitor/TouchTimeCounts_%s.txt", 
                                        Environment.getExternalStorageDirectory(),
                                        temp);
                                // File file = new File(filePath);
                                // try {
                                // PrintWriter writer = new PrintWriter(file);
                                // int oneCount = 0;
                                // int twoCount = 0;
                                // int threeCount = 0;
                                // int fourCount = 0;
                                // int fiveCount = 0;
                                // int sixCount = 0;
                                // int sevenCount = 0;
                                // for (int i = 0; i < deltaTouchEventTimeList.size(); i++) {
                                // long touchTime = deltaTouchEventTimeList.get(i);
                                // if (touchTime > 0 && touchTime < 1500) {
                                // oneCount++;
                                // }
                                // else if (touchTime < 2500) {
                                // twoCount++;
                                // }
                                // else if (touchTime < 3500) {
                                // threeCount++;
                                // }
                                // else if (touchTime < 4500) {
                                // fourCount++;
                                // }
                                // else if (touchTime < 5500) {188
                                // fiveCount++;
                                // }
                                // else if (touchTime < 6500) {
                                // sixCount++;
                                // }
                                // else if (touchTime >= 6500) {
                                // sevenCount++;
                                // }
                                // }
                                //
                                writer.println(
                                        String.format("1000: %d", oneCount));
                                //
                                writer.println(
                                        String.format("2000: %d", twoCount));
                                //
                                writer.println(
                                        String.format("3000: %d", threeCount));
                                //
                                writer.println(
                                        String.format("4000: %d", fourCount));
                                //
                                writer.println(
                                        String.format("5000: %d", fiveCount));
                                //
                                writer.println(
                                        String.format("6000: %d", sixCount));
                                //
                                writer.println(
                                        String.format("7000+: %d", sevenCount));
                                // writer.close();
                                // } catch (FileNotFoundException e) {
                                // // TODO Auto-generated catch block
                                // e.printStackTrace();
                                // }
                                // journal addition
                                // String filePath = 
                                String.format(
                                        "%s/AppMonitor/TouchTimeline_%s.txt", 
                                        Environment.getExternalStorageDirectory(),
                                        temp);
                                // File file = new File(filePath);
                                // try {
                                // PrintWriter writer = new PrintWriter(file);
                                // long touchTime = 0;
                                //
                                // for (int i = 0; i < deltaTouchEventTimeList.size(); i++) {
                                // touchTime = touchTime + deltaTouchEventTimeList.get(i);
                                // writer.println(String.format("%d", touchTime));
                                // }
                                // writer.close();
                                // } catch (FileNotFoundException e) {
                                // // TODO Auto-generated catch block
                                // e.printStackTrace();
                                // }
                                
                            }
                            String pkg = intent.getStringExtra("package");

                            currentFocusedAppName = activity;
                            eventStartTime = currentTime;
                            if (appDatabase.containsApplication(activity)) {
                                currentFocusedApp = appDatabase.getApplicationInfo(
                                        activity);
                            } else {
                                currentFocusedApp = new 
                                        ApplicationInfo(activity, pkg);     
                                appDatabase.addUntrainedApp(currentFocusedApp);
                            }
                                
                            resetStats();
                            if (currentFocusedApp.isTrained()) {
                                startDVFSAlgorithm();
                                mAverageWindowedEventTime.clear();
                            }
                        }
                    }
                } else if (intent.getAction().equals(GLOBAL_TOUCH_EVENT)) {
                    touchEvents++;
                    if (previousTouchEventTime != 0L) {
                        long dT = getDeltaTime(previousTouchEventTime);

                        deltaTouchEventTimeList.add(dT);
                        synchronized (mAverageWindowedEventTime) {
                            if (mAverageWindowedEventTime.size()
                                    >= EVENT_AVERAGE_WINDOW) {
                                mAverageWindowedEventTime.remove(0);
                                mAverageWindowedEventTime.add((float) dT);
                            } else {
                                mAverageWindowedEventTime.add((float) dT);
                            }
                        }
                        float touchMean = calculateMean(deltaTouchEventTimeList);
                        float touchSD = calculateStandardDev(
                                deltaTouchEventTimeList, touchMean);

                        if (currentFocusedApp.isTrained() == false) {
                            bayesianClassifier(touchMean, touchSD, dT);
                        }
                        //
                        // if (currentFocusedApp.isTrained() == false) {
                        // naiveNumEventClassifier();
                        // }
                    }
                    previousTouchEventTime = currentTime;
                    interruptDVFSAlgorithm();
                } else if (intent.getAction().equals(GLOBAL_KEY_EVENT)
                        || intent.getAction().equals(GLOBAL_SOFTKEY_EVENT)) {
                    keyEvents++;
                    if (previousTouchEventTime != 0L) {
                        long dT = getDeltaTime(previousTouchEventTime);

                        deltaTouchEventTimeList.add(dT);
                        synchronized (mAverageWindowedEventTime) {
                            if (mAverageWindowedEventTime.size()
                                    >= EVENT_AVERAGE_WINDOW) {
                                mAverageWindowedEventTime.remove(0);
    
                                mAverageWindowedEventTime.add((float) dT);
                            } else {
    
                                mAverageWindowedEventTime.add((float) dT);
                            }
                        }
    
                        float keyMean = calculateMean(deltaKeyEventTimeList);
                        float keySD = calculateStandardDev(deltaKeyEventTimeList, 
                                keyMean);

                        if (currentFocusedApp.isTrained() == false) {
                            bayesianClassifier(keyMean, keySD, dT);
                        }
    
                        // if (currentFocusedApp.isTrained() == false) {
                        // naiveNumEventClassifier();
                        // }
                    }
                    previousKeyEventTime = currentTime;
                    interruptDVFSAlgorithm();
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    String filePath = String.format(
                            "%s/AppMonitor/AppDatabase.txt", 
                            Environment.getExternalStorageDirectory());

                    postNotification(
                            String.format("Saved = %s, Exported = %s",
                            saveDatabase(), appDatabase.exportDatabase(filePath)),
                            R.drawable.save_icon);
                    setScreenOffDVFSLevel();
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    if (!isAlgorithmRunning) {
                        if (currentFocusedApp != null
                                && currentFocusedApp.isTrained()) {
                            startDVFSAlgorithm();
                        } else {
                            setScreenOnDVFSLevel();
                        }
                    }
                } else if (intent.getAction().equals(
                        Intent.ACTION_BATTERY_CHANGED)) {
                    int prev = mBatteryLevel;

                    mBatteryLevel = intent.getIntExtra(
                            BatteryManager.EXTRA_LEVEL, -1);
                    if (prev > 0 && mTrackBatteryLevelChanges) {
                        batteryLevelChanged += (prev - mBatteryLevel);
                    }
                }
            }
        },
                filt);
        isInitialized = true;
    }

    private long getDeltaTime(long time) {
        return SystemClock.elapsedRealtime() - time;
    }

    private float calculateMean(ArrayList<Long> list) {
        long sum = 0;

        if (list.size() > 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += list.get(i);
            }
            return (float) sum / list.size();
        }
        return 0F;
    }

    private float calculateStandardDev(ArrayList<Long> list, float mean) {
        float sum = 0F;

        if (list.size() > 0) {
            for (int i = 0; i < list.size(); i++) {
                sum += Math.pow((list.get(i) - mean), 2);
            }
            return (float) Math.sqrt(sum / list.size());
        }
        return 0F;
    }

    private float calculateMedian(ArrayList<Long> list) {
        ArrayList<Long> tempList = new ArrayList<Long>(list);

        if (tempList.size() > 1 && (tempList.size() % 2) == 0) { // Even number of points
            java.util.Collections.sort(tempList);
            float first = tempList.get(tempList.size() / 2 - 1);
            float second = tempList.get(tempList.size() / 2);

            return (first + second) / 2;
        } else if (tempList.size() > 1) { // Odd number of points
            java.util.Collections.sort(tempList);
            return tempList.get(tempList.size() / 2);
        } else if (tempList.size() == 1) {
            return tempList.get(0);
        }
        return 0F;
    }

    private void shutdown() {
        if (isAlgorithmRunning) {
            stopDVFSAlgorithm();
        }
        if (dvfs != null) {
            dvfs.setFrequencyGovernor(FrequencyGovernors.OnDemand);
        }
        if (mThreadDone == false) {
            mThreadDone = true;
        }
        if (mNotificationManager != null) {
            mNotificationManager.cancel(APPMONITOR_NOTIFICATION_ID);
        }
    }

    private void postNotification(String notificationText, int icon) {
        try {
            if (notificationsEnabled) {
                // In this sample, we'll use the same text for the ticker and the expanded notification
                CharSequence text = "App Monitor";
    
                // Set the icon, scrolling text and time stamp
                Notification notification = new Notification(icon, text, 
                        System.currentTimeMillis());
        
                notification.flags |= Notification.FLAG_AUTO_CANCEL
                        | Notification.FLAG_ONGOING_EVENT;
    
                Intent notifyIntent = new Intent(this, AppMonitor.class);
                Bundle bundle = new Bundle();

                bundle.putString("action", "view");
                notifyIntent.putExtras(bundle);
        
                // notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                notifyIntent.setAction("csu.research.AURA.AppMonitor");
        
                // The PendingIntent to launch our activity if the user selects this notification
                PendingIntent contentIntent = PendingIntent.getService(this, 0, 
                        notifyIntent, 0);
    
                // Set the info for the views that show in the notification panel.
                notification.setLatestEventInfo(this, "App Monitor", 
                        notificationText, contentIntent);
    
                // Send the notification.
                // We use a string id because it is a unique number.  We use it later to cancel.
                mNotificationManager.notify(APPMONITOR_NOTIFICATION_ID,
                        notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startDVFSAlgorithm() {
        if (mRunDVFSAlgorithm == null || isAlgorithmRunning
                || mRunDVFSAlgorithm.isAlive()) {
            interruptDVFSAlgorithm();
            mRunDVFSAlgorithm.interrupt();
            resetDVFSThread();
            mRunDVFSAlgorithm.start();
        } else {
            mRunDVFSAlgorithm.start();
        }
    }

    private void stopDVFSAlgorithm() {
        if (isAlgorithmRunning || mRunDVFSAlgorithm.isAlive()) {
            isAlgorithmRunning = false;
            mTrackBatteryLevelChanges = false;
            interruptDVFSAlgorithm();
            mRunDVFSAlgorithm.interrupt();
            resetDVFSThread();
        }
    }

    private void interruptDVFSAlgorithm() {
        if (isAlgorithmRunning) {
            synchronized (mRunDVFSAlgorithm) {
                mRunDVFSAlgorithm.notify();
                resetBrightness = true;
            }
        }
    }

    private void resetDVFSThread() {
        mRunDVFSAlgorithm = new Thread(
                new Runnable() {
            public void run() {
                if (currentFocusedApp != null && currentFocusedApp.isTrained()) {
                    try {
                        lowCount = 0;
                        highCount = 0;
                        totalPowerSaved = 0F;
                        totalEnergySaved = 0F; 
                        totalSOCSaved = 0F;
                        avgPowerSavedPct = 0F;
                        totalPowerSavedTime = 0F;
                        totalPowerNominal = 0F;
                        batteryLevelChanged = 0; 
                        mTrackBatteryLevelChanges = true;
                        hitCount = 0;
                        missCount = 0;
                        avgCpuUtil = 0;
                        switch (Algorithm) {
                        case POWERSAVER: 
                            isAlgorithmRunning = true;
                            runPowerSaverAlgorithm();
                            break;

                        case CHANGE_BLINDNESS:
                            isAlgorithmRunning = true;
                            runChangeBlindnessAlgorithm();
                            break;

                        case Q_LEARNING:
                            isAlgorithmRunning = true;
                            runQLearningAlgorithm();
                            break;

                        case NORMAL_MDP:
                            isAlgorithmRunning = true;
                            runNormalMDPAlgorithm();
                            break;

                        case NORMAL_MDP_ADAPT:
                            isAlgorithmRunning = true;
                            runAdaptiveMDPAlgorithm();
                            break;

                        case MOVING_AVERAGE:
                            isAlgorithmRunning = true;
                            runMovingAverageAlgorithm();
                            break;

                        default:
                            Toast.makeText(getApplicationContext(), String.format("%s is not classified!", currentFocusedAppName), Toast.LENGTH_LONG).show();
                            stopDVFSAlgorithm();
                            dvfs.setNominalCPUFrequency();
                        }
                    } catch (InterruptedException e) {
                        isAlgorithmRunning = false;
                        dvfs.setNominalCPUFrequency();
                    } catch (Exception e) {
                        isAlgorithmRunning = false;
                        dvfs.setNominalCPUFrequency();
                    }
                }
            }
        });
    }

    private void runPowerSaverAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            State state = State.HIGH;
            long basePeriod = 1000;
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 245000; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();
            int prevEventCount = touchEvents + keyEvents;
            float adjustScreenDown = 0.1F;
            float defaultBrightness = getDefaultScreenBrightness();
            float currentBrightness = defaultBrightness;
            float maxBrightness = defaultBrightness;
            float minBrightness = 0.4F;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            int cpuUtil = 0;
            long timeSinceStartup = 0;
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int notificationCount = 0;
            float mean, dev;
            int lowIntervalCount = 0;
            int highStateCount = 0, highStateLimit = 0;

            if (currentFocusedApp.getKeyMean()
                    < currentFocusedApp.getTouchMean()) {
                mean = currentFocusedApp.getKeyMean();
                dev = currentFocusedApp.getKeyStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getTouchMean();
                    dev = currentFocusedApp.getTouchStandardDeviation();
                }
            } else {
                mean = currentFocusedApp.getTouchMean();
                dev = currentFocusedApp.getTouchStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getKeyMean();
                    dev = currentFocusedApp.getKeyStandardDeviation();
                }
            }
            switch (currentFocusedApp.getAppClassification()) {
            // journal changes
            case VeryLowInteraction:
                basePeriod = 1000;
                adjustScreenDown = 0.4F;
                highStateLimit = 2;

            case LowInteraction:
                basePeriod = 850;
                adjustScreenDown = 0.35F;
                highStateLimit = 3;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                adjustScreenDown = 0.3F;
                highStateLimit = 4;

            case MedInteraction:
                basePeriod = 550;
                adjustScreenDown = 0.25F;
                highStateLimit = 5;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                adjustScreenDown = 0.2F;
                highStateLimit = 6;

            case HighInteraction:
                basePeriod = 250;
                adjustScreenDown = 0.15F;
                highStateLimit = 7;
                break;

            case VeryHighInteraction:
                basePeriod = 100;
                adjustScreenDown = 0.05F;
                highStateLimit = 8;
            }
            while (isAlgorithmRunning) {
                // DVFS
                if ((touchEvents + keyEvents) > prevEventCount) {
                    prevEventCount = touchEvents + keyEvents;
                    if (state == State.LOW) {
                        missCount++;
                    } else {
                        hitCount++;
                    }
                }
                if (lowIntervalCount >= mean) {
                    if (curFreq != nominalFreq) {
                        dvfs.setNominalCPUFrequency();
                        curFreq = nominalFreq;        
                    }
                    state = State.HIGH;  
                    lowIntervalCount = 0;
                    highStateCount = 0;
                } else if (state == State.HIGH
                        && highStateCount++ >= highStateLimit) {
                    if (curFreq != lowFreq) {
                        dvfs.setLowestCPUFrequency();
                        curFreq = lowFreq;
                    }
                    state = State.LOW;
                }
                // SCREEN
                switch (state) {
                case LOW:
                    if (currentBrightness > minBrightness) {
                        currentBrightness = AppUtil.saturate(
                                currentBrightness - adjustScreenDown,
                                minBrightness, maxBrightness);
                        brightnessIntent.putExtra("brightness", 
                                currentBrightness);
                        sendBroadcast(brightnessIntent);
                    }
                    lowCount++;
                    break;

                case HIGH:
                    if (currentBrightness < defaultBrightness) {
                        currentBrightness = defaultBrightness;
                        brightnessIntent.putExtra("brightness", 
                                currentBrightness);
                        sendBroadcast(brightnessIntent);
                    }
                    highCount++;
                    break;
                }
                synchronized (mRunDVFSAlgorithm) {
                    mRunDVFSAlgorithm.wait(basePeriod);
                }
                lowIntervalCount += basePeriod;
                cpuUtil = CPUInfo.getCPUUtilizationPct();
                avgCpuUtil += cpuUtil;
                totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq,
                        cpuUtil);
                totalPowerNominal += PowerModels.getMWScreenPower(
                        defaultBrightnessPct);
                if (curFreq < nominalFreq
                        || currentBrightness < defaultBrightness) {
                    totalPowerSavedTime += (basePeriod / 1000F);
                    if (currentBrightness < defaultBrightness) {
                        totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                defaultBrightness, currentBrightness);
                    }
                    if (curFreq < nominalFreq) {    
                        totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                nominalFreq, curFreq, cpuUtil);
                    }
                }
                if (notificationCount++ >= 10) {          
                    postNotification(
                            String.format("[%.3f mW, %.3f %%]", totalPowerSaved,
                            totalSOCSaved),
                            R.drawable.cpu_icon);
                    notificationCount = 0;
                }    
            }
        }
    }

    private void runChangeBlindnessAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            State state = State.HIGH;
            long basePeriod = 1000;
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 691200; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();
            int prevEventCount = touchEvents + keyEvents;
            float adjustScreenDown = 0.07F;
            float defaultBrightness = getDefaultScreenBrightness();
            float currentBrightness = defaultBrightness;
            float maxBrightness = defaultBrightness;
            float minBrightness = 0.8F;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            float totalPowerSavedTime = 0F;
            int cpuUtil = 0;
            long timeSinceStartup = 0;
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int dvfsCountDown = 30000; // Milliseconds
            int screenCountDown = 40000; // Milliseconds197
            int adjustScreenEveryNms = 3000; // Milliseconds
            int adjustDVFSEveryNms = 4000; // Milliseconds
            int screenCounter = 0;
            int dvfsCounter = 0;
            int cpuUtilLowToHighThresh = 70; // %
            int cpuUtilHighToLowThresh = 70; // %
            int notificationCount = 0;
            int resetInterval = 30000; // Milliseconds
            int resetCounter = 0;
           
            switch (currentFocusedApp.getAppClassification()) {
            // journal changes
            case VeryLowInteraction:
                basePeriod = 1000;
                resetInterval = 30000;

            case LowInteraction:
                basePeriod = 850;
                resetInterval = 27000;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                resetInterval = 24000;

            case MedInteraction:
                basePeriod = 550;
                resetInterval = 20000;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                resetInterval = 17000;

            case HighInteraction:
                basePeriod = 250;
                resetInterval = 14000;
                break;

            case VeryHighInteraction:
                basePeriod = 100;
                resetInterval = 10000;
            }
           
            while (isAlgorithmRunning) {
                // MAY WANT TO CHANGE HOW WE DO STATS...........
                // STATS
                hitCount++;
                if ((touchEvents + keyEvents) > prevEventCount) {
                    if (state == State.LOW) {
                        missCount++; // Missed, so adjust hit
                        hitCount--;
                    }
                    prevEventCount = touchEvents + keyEvents;
                }
                             
                // Update CPU Frequency Minimum Level
                if (dvfsCountDown > 0) {
                    dvfsCountDown -= basePeriod;
                    dvfsCounter += basePeriod;
                    if (dvfsCounter >= adjustDVFSEveryNms) {
                        dvfsCounter = 0;
                        lowFreq = (int) Math.max(
                                (0.7F + 0.3F * (dvfsCountDown / 40000F))
                                        * nominalFreq,
                                        499200);
                        // step down levels changed for Nexus One
                        if (lowFreq >= 691200) {
                            lowFreq = 691200;
                        } else if (lowFreq >= 652800) {
                            lowFreq = 652800;
                        } else if (lowFreq >= 614400) {
                            lowFreq = 614400;
                        } else if (lowFreq >= 576000) {
                            lowFreq = 576000;
                        } else if (lowFreq >= 537600) {
                            lowFreq = 537600;
                        } else {
                            lowFreq = 499200;
                        }
                    }
                }
               
                // Update Screen Minimum Level
                if (screenCountDown > 0) {
                    screenCountDown -= basePeriod;
                    screenCounter += basePeriod;
                    if (screenCounter >= adjustScreenEveryNms) {
                        screenCounter = 0;
                        currentBrightness = AppUtil.saturate(
                                currentBrightness - adjustScreenDown,
                                minBrightness, maxBrightness);
                        brightnessIntent.putExtra("brightness",
                                currentBrightness);
                        sendBroadcast(brightnessIntent);
                    }
                }
                // Reset cpu min freq & screen brightness every reset interval
                resetCounter += basePeriod;
                if (resetCounter >= resetInterval) {
                    resetCounter = 0;
                    lowFreq = 691200; // changed for Nexus One
                    currentBrightness = defaultBrightness;
                    brightnessIntent.putExtra("brightness", currentBrightness);
                    sendBroadcast(brightnessIntent);
                    dvfsCountDown = 30000; // Milliseconds
                    screenCountDown = 40000; // Milliseconds
                    screenCounter = 0;
                    dvfsCounter = 0;
                }
                // GET CPU UTILIZATION
                cpuUtil = CPUInfo.getCPUUtilizationPct();
                avgCpuUtil += cpuUtil;
               
                switch (state) {
                case LOW:
                    if (cpuUtil > cpuUtilLowToHighThresh) {
                        dvfs.setNominalCPUFrequency();
                        state = State.HIGH;
                        curFreq = 729600;
                    }
                    lowCount++;
                    break;

                case HIGH:
                    if (cpuUtil < cpuUtilHighToLowThresh) {
                        dvfs.setCPUFrequency(lowFreq);
                        state = State.LOW;
                        curFreq = lowFreq;
                    }
                    highCount++;
                    break;
                }
               
                Thread.sleep(basePeriod);
                totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq,
                        cpuUtil);
                totalPowerNominal += PowerModels.getMWScreenPower(
                        defaultBrightnessPct);
                
                if (curFreq < nominalFreq
                        || currentBrightness < defaultBrightness) {
                    totalPowerSavedTime += (basePeriod / 1000F);
                    if (currentBrightness < defaultBrightness) {
                        totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                defaultBrightness, currentBrightness);
                    }
                    if (curFreq < nominalFreq) {     
                        totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                nominalFreq, curFreq, cpuUtil);
                    }
                }
                timeSinceStartup += basePeriod;
                if (notificationCount++ >= 10) {          
                    postNotification(
                            String.format("[%.3f mW, %.3f %%]", totalPowerSaved,
                            totalSOCSaved),
                            R.drawable.cpu_icon);
                    notificationCount = 0;
                }                    
            }
        }
    }
    
    // journal addition
    private int selectEGreedyAction(int[] Qstate) {
        // epsilon greedy algorithm
        double[] qValues = currentFocusedApp.getQValuesAt(Qstate);
        int selectedAction = -1;

        randomEps = false;
        double maxQ = -Double.MAX_VALUE;
        int[] doubleValues = new int[qValues.length];
        int maxDV = 0;

        // Explore
        if (Math.random() < epsilon) {
            selectedAction = -1;
            randomEps = true;
        } else {
            for (int action = 0; action < qValues.length; action++) {
                
                if (qValues[action] > maxQ) {
                    selectedAction = action;
                    maxQ = qValues[action];
                    maxDV = 0;
                    doubleValues[maxDV] = selectedAction;
                } else if (qValues[action] == maxQ) {
                    maxDV++;
                    doubleValues[maxDV] = action; 
                }
            }
        
            if (maxDV > 0) {
                int randomIndex = (int) (Math.random() * (maxDV + 1));

                selectedAction = doubleValues[ randomIndex ];
            }
        }
        // Select random action if all qValues == 0 or exploring.
        if (selectedAction == -1) {
            // System.out.println( "Exploring ..." );
            selectedAction = (int) (Math.random() * qValues.length);
        }
        return selectedAction;
    }

    /* private double getMaxQValue( int[] state, int action ) {
     double maxQ = 0;
     double[] qValues = policy.getQValuesAt( state );
     for( action = 0 ; action < qValues.length ; action++ ) {
     if( qValues[action] > maxQ ) {
     maxQ = qValues[action];
     }
     }
     return maxQ;
     }
     */
    // journal addition
    private void runQLearningAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            long basePeriod = 1000; // Default milliseconds
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 245000; // changed for Nexus One
            int lowNominalFreq = 537600; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();

            switch (currentFocusedApp.getAppClassification()) {
            case VeryLowInteraction:
                basePeriod = 1000;
                break;

            case LowInteraction:
                basePeriod = 850;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                break;

            case MedInteraction:
                basePeriod = 550;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                break;

            case HighInteraction:
                basePeriod = 250;
                break;

            case VeryHighInteraction:
                basePeriod = 100;
                break;
            }
            if (curFreq != nominalFreq) {
                dvfs.setNominalCPUFrequency();
                curFreq = nominalFreq;
            }
            float mean, dev;        
            int cpuUtil = 0;
            float adjustScreenDown = 0.1F;

            if (currentFocusedApp.getKeyMean()
                    < currentFocusedApp.getTouchMean()) {
                mean = currentFocusedApp.getKeyMean();
                dev = currentFocusedApp.getKeyStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getTouchMean();
                    dev = currentFocusedApp.getTouchStandardDeviation();
                }
            } else {
                mean = currentFocusedApp.getTouchMean();
                dev = currentFocusedApp.getTouchStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getKeyMean();
                    dev = currentFocusedApp.getKeyStandardDeviation();
                }
            }
            float cov = dev / mean;

            if (cov < 0.5) { // Low variability
                adjustScreenDown = 0.15F;
            } else if (cov < 0.75) { // Med variability
                adjustScreenDown = 0.10F;
            } else if (cov > 1) { // High variability
                adjustScreenDown = 0.05F;
            }
            int previousTouchKeyEvents;
            long timeSinceStartup = 0;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            float currentBrightness = getDefaultScreenBrightness();
            float defaultBrightness = getDefaultScreenBrightness();
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int notificationCount = 0;
           
            double this_Q;
            double max_Q;
            double new_Q;

            // Reset state to start state (nominal, eval interval 0)
            QState[0] = 1;
            QState[1] = 0;
            if (dev > 0 && mean > 0) {
                previousTouchKeyEvents = touchEvents + keyEvents;
                
                while (isAlgorithmRunning) {
                    // for( int i = 0 ; i < epochs ; i++ ) {202
                    // get action
                    action = selectEGreedyAction(QState);
                    // get next state
                    if (action == 1) {
                        if (QState[0] == 0) {
                            newQState[0] = 1; // change to nominal
                        } else {
                            newQState[0] = 0; // change to below nominal
                        }
                    } else {
                        newQState[0] = QState[0]; // stay the same state
                    }
                    if (QState[1] < 10) {
                        newQState[1] = QState[1] + 1; // increment eval interval count
                    } else {
                        newQState[1] = QState[1]; // don't increment
                    }
                    // calculate reward
                    // reward is 0 if we don't change state and no touch is received,
                    // 0 if we do change state and a touch is received,
                    // -1 for all other scenarios
                    if ((touchEvents + keyEvents) > previousTouchKeyEvents) {
                        newQState[1] = 0; // touch received, so reset counter
                        if (newQState[0] == 0) { // if predicted the low nominal state,
                            missCount++; // missed
                            reward = -1; // predicted incorrectly, so penalize
                        } else if (newQState[0] == 1) { // if we predicted the nominal state,
                            hitCount++; // hit
                            reward = 2; // predicted correctly, so reward
                        }
                        previousTouchKeyEvents = touchEvents + keyEvents;
                    } else {
                        if (newQState[0] == 0) { // if we predicted the below nominal state,
                            hitCount++; // hit
                            reward = 0; // predicted correctly, so don't penalize
                        } else if (newQState[0] == 1) {
                            reward = -1; // predicted incorrectly, so penalize
                        }
                    }
                    // update screen and CPU freq
                    if (QState[0] == 0) {
                        if (newQState[0] == 0) {
                            // stay in below nominal state
                            // adjust screen down
                            if (currentBrightness > 0.4F) {
                                currentBrightness = currentBrightness
                                        - adjustScreenDown;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                        } else {
                            // change from below nominal to nominal
                            // set brightness to default level
                            if (currentBrightness != defaultBrightness) {
                                currentBrightness = defaultBrightness;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                            // set CPU frequency to nominal frequency
                            if (curFreq != nominalFreq) {
                                dvfs.setNominalCPUFrequency();
                                curFreq = nominalFreq;
                            }
                        }
                        lowCount++;
                    } else {
                        if (newQState[0] == 0) {
                            // change from nominal to below nominal
                            // adjust screen down
                            if (currentBrightness > 0.4F) {
                                currentBrightness = currentBrightness
                                        - adjustScreenDown;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                            // set low CPU frequency
                            if (curFreq != lowFreq) {
                                dvfs.setLowestCPUFrequency();
                                curFreq = lowFreq;
                            }   
                        } else {// stay in nominal state (do nothing)
                        }
                        highCount++;
                    }
            
                    this_Q = currentFocusedApp.getQValue(QState, action);
                    max_Q = currentFocusedApp.getMaxQValue(newQState);
                    // Calculate new Value for Q
                    new_Q = this_Q + alpha * (reward + gamma * max_Q - this_Q);
                    currentFocusedApp.setQValue(QState, action, new_Q);
                    // Set state to the new state.
                    QState[0] = newQState[0];
                    QState[1] = newQState[1];
                    synchronized (mRunDVFSAlgorithm) {
                        mRunDVFSAlgorithm.wait(basePeriod);
                    }
                    // curFreq = dvfs.getCPUFrequency();
                    totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq, 
                            cpuUtil);
                    totalPowerNominal += PowerModels.getMWScreenPower(
                            defaultBrightnessPct);
                    if (curFreq < nominalFreq
                            || currentBrightness < defaultBrightness) {
                        totalPowerSavedTime += (basePeriod / 1000F);
                        if (currentBrightness < defaultBrightness) {
                            totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                    defaultBrightness, currentBrightness);
                        }
                        if (curFreq < nominalFreq) {     
                            totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                    nominalFreq, curFreq, cpuUtil);
                        }
                    }
                    timeSinceStartup += basePeriod;
                    if (notificationCount++ >= 10) {          
                        postNotification(
                                String.format("[%.3f mW, %.3f %%]", 
                                totalPowerSaved, totalSOCSaved),
                                R.drawable.cpu_icon);
                        notificationCount = 0;
                    } 
                }
            } else {
                postNotification("STAT ERROR", R.drawable.cpu_icon);
            }
        }
    }

    private void runNormalMDPAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace 
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            long basePeriod = 1000; // Default milliseconds
            float lowToHighProb = 0.7F;
            float lowStepUpProb = 0.6F;
            float highToLowProb = 0.5F;
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 245000; // changed for Nexus One
            int lowNominalFreq = 537600; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();

            switch (currentFocusedApp.getAppClassification()) {
            // journal changes
            case VeryLowInteraction:
                basePeriod = 1000;
                lowToHighProb = 0.75F;
                lowStepUpProb = 0.65F;
                highToLowProb = 0.55F;

            case LowInteraction:
                basePeriod = 850;
                lowToHighProb = 0.7F;
                lowStepUpProb = 0.6F;
                highToLowProb = 0.5F;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                lowToHighProb = 0.65F;
                lowStepUpProb = 0.55F;
                highToLowProb = 0.45F;

            case MedInteraction:
                basePeriod = 550;
                lowToHighProb = 0.6F;
                lowStepUpProb = 0.5F;
                highToLowProb = 0.4F;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                lowToHighProb = 0.55F;
                lowStepUpProb = 0.45F;
                highToLowProb = 0.35F;

            case HighInteraction:
                basePeriod = 250;
                lowToHighProb = 0.5F;
                lowStepUpProb = 0.4F;
                highToLowProb = 0.3F;

            case VeryHighInteraction:
                basePeriod = 100;
                lowToHighProb = 0.45F;
                lowStepUpProb = 0.35F;
                highToLowProb = 0.25F;
                break;
            }
            float probOfEvent = 0F;

            if (curFreq != nominalFreq) {
                dvfs.setNominalCPUFrequency();
                curFreq = nominalFreq;
            }
            State state = State.HIGH;
            float mean, dev;        
            int cpuUtil = 0;
            float adjustScreenDown = 0.1F;

            if (currentFocusedApp.getKeyMean()
                    < currentFocusedApp.getTouchMean()) {
                mean = currentFocusedApp.getKeyMean();
                dev = currentFocusedApp.getKeyStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getTouchMean();
                    dev = currentFocusedApp.getTouchStandardDeviation();
                }
            } else {
                mean = currentFocusedApp.getTouchMean();
                dev = currentFocusedApp.getTouchStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getKeyMean();
                    dev = currentFocusedApp.getKeyStandardDeviation();
                }
            }
            float cov = dev / mean;

            if (cov < 0.5) { // Low variability
                adjustScreenDown = 0.15F;
            } else if (cov < 0.75) { // Med variability
                adjustScreenDown = 0.10F;
            } else if (cov > 1) { // High variability
                adjustScreenDown = 0.05F;
            }
            int previousTouchKeyEvents;
            long timeSinceLastEvent = 0;
            long timeSinceStartup = 0;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            float currentBrightness = getDefaultScreenBrightness();
            float defaultBrightness = getDefaultScreenBrightness();
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int notificationCount = 0;
            float diff;

            if (dev > 0 && mean > 0) {
                previousTouchKeyEvents = touchEvents + keyEvents;
                    
                while (isAlgorithmRunning) {           
                    // hitCount++; //Predict hit
                    // SCREEN
                    if ((touchEvents + keyEvents) > previousTouchKeyEvents) {
                        if (state == State.LOW) {
                            missCount++; // Missed, so adjust hit
                            // hitCount--;
                        } else {
                            hitCount++; // CHANGED FOR PERFORMANCE ANALYSIS  - NO HIT IF NO TOUCH EVENT
                        }
                        previousTouchKeyEvents = touchEvents + keyEvents;
                        // currentBrightness = Math.min(currentBrightness + adjustScreenUp, defaultBrightness);
                        if (currentBrightness != defaultBrightness) {
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness", 
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        }
                        timeSinceLastEvent = timeSinceStartup;
                    } else {
                        if (state == State.LOW) {
                            if (currentBrightness > 0.4F) {
                                currentBrightness = currentBrightness
                                        - adjustScreenDown;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                        }
                    }
                    // DVFS  
                    cpuUtil = CPUInfo.getCPUUtilizationPct();
                    avgCpuUtil += cpuUtil;
                    diff = timeSinceStartup - timeSinceLastEvent;
                    switch (state) {
                    case LOW:
                        probOfEvent = AppUtil.getNormProb(diff, mean, dev, 100);
                        if (probOfEvent > lowToHighProb || diff == 0) {
                            state = State.HIGH;
                            if (curFreq != nominalFreq) {
                                dvfs.setNominalCPUFrequency();
                                curFreq = nominalFreq;
                            }
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness", 
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        } else if (probOfEvent > lowStepUpProb || cpuUtil > 80) {
                            if (curFreq != lowNominalFreq) {
                                dvfs.setLowNominalCPUFrequency();
                                curFreq = lowNominalFreq;
                            }
                        }
                        lowCount++;
                        break;

                    case HIGH:
                        if (diff > 0) {
                            probOfEvent = AppUtil.getNormProb(diff, mean, dev, 
                                    100);
                            if (probOfEvent < highToLowProb && cpuUtil < 80) {
                                state = State.LOW;
                                if (curFreq != lowFreq) {
                                    dvfs.setLowestCPUFrequency();
                                    curFreq = lowFreq;
                                }                                   
                            }
                        }
                        highCount++;
                        break;
                    }
                    synchronized (mRunDVFSAlgorithm) {
                        mRunDVFSAlgorithm.wait(basePeriod);
                    }
                    // curFreq = dvfs.getCPUFrequency();
                    totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq, 
                            cpuUtil);
                    totalPowerNominal += PowerModels.getMWScreenPower(
                            defaultBrightnessPct);
                    if (curFreq < nominalFreq
                            || currentBrightness < defaultBrightness) {
                        totalPowerSavedTime += (basePeriod / 1000F);
                        if (currentBrightness < defaultBrightness) {
                            totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                    defaultBrightness, currentBrightness);
                        }
                        if (curFreq < nominalFreq) {     
                            totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                    nominalFreq, curFreq, cpuUtil);
                        }
                    }
                    timeSinceStartup += basePeriod;
                    
                    if (notificationCount++ >= 10) {          
                        postNotification(
                                String.format("[%.3f mW, %.3f %%]", 
                                totalPowerSaved, totalSOCSaved),
                                R.drawable.cpu_icon);
                        notificationCount = 0;
                    }                    
                }
            } else {
                postNotification("STAT ERROR", R.drawable.cpu_icon);
            }
        }
    }

    private void runAdaptiveMDPAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            long basePeriod = 1000; // Default milliseconds
            float lowToHighProb = 0.7F;
            float lowStepUpProb = 0.6F;
            float highToLowProb = 0.5F;
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 245000; // changed for Nexus One
            int lowNominalFreq = 537600; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();

            switch (currentFocusedApp.getAppClassification()) {
            // journal changes
            case VeryLowInteraction:
                basePeriod = 1000;
                lowToHighProb = 0.75F;
                lowStepUpProb = 0.65F;
                highToLowProb = 0.55F;

            case LowInteraction:
                basePeriod = 850;
                lowToHighProb = 0.7F;
                lowStepUpProb = 0.6F;
                highToLowProb = 0.5F;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                lowToHighProb = 0.65F;
                lowStepUpProb = 0.55F;
                highToLowProb = 0.45F;

            case MedInteraction:
                basePeriod = 550;
                lowToHighProb = 0.6F;
                lowStepUpProb = 0.5F;
                highToLowProb = 0.4F;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                lowToHighProb = 0.55F;
                lowStepUpProb = 0.45F;
                highToLowProb = 0.35F;

            case HighInteraction:
                basePeriod = 250;
                lowToHighProb = 0.5F;
                lowStepUpProb = 0.4F;
                highToLowProb = 0.3F;

            case VeryHighInteraction:
                basePeriod = 100;
                lowToHighProb = 0.45F;
                lowStepUpProb = 0.35F;
                highToLowProb = 0.25F;
                break;
            }
            float probOfEvent = 0F;

            if (curFreq != nominalFreq) {
                dvfs.setNominalCPUFrequency();
                curFreq = nominalFreq;
            }
            State state = State.HIGH;
            float mean, dev;        
            int cpuUtil = 0;
            float adjustScreenDown = 0.1F;

            if (currentFocusedApp.getKeyMean()
                    < currentFocusedApp.getTouchMean()) {
                mean = currentFocusedApp.getKeyMean();
                dev = currentFocusedApp.getKeyStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getTouchMean();
                    dev = currentFocusedApp.getTouchStandardDeviation();
                }
            } else {
                mean = currentFocusedApp.getTouchMean();
                dev = currentFocusedApp.getTouchStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getKeyMean();
                    dev = currentFocusedApp.getKeyStandardDeviation();
                }
            }
            float cov = dev / mean;

            if (cov < 0.5) { // Low variability
                adjustScreenDown = 0.15F;
            } else if (cov < 0.75) { // Med variability
                adjustScreenDown = 0.10F;
            } else if (cov > 1) { // High variability
                adjustScreenDown = 0.05F;
            }
            int previousTouchKeyEvents;
            long timeSinceLastEvent = 0;
            long timeSinceStartup = 0;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            float currentBrightness = getDefaultScreenBrightness();
            float defaultBrightness = getDefaultScreenBrightness();
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int notificationCount = 0;
            float diff;

            if (dev > 0 && mean > 0) {
                previousTouchKeyEvents = touchEvents + keyEvents;
                    
                while (isAlgorithmRunning) {           
                    // hitCount++; //Predict hit
                    // SCREEN
                    if ((touchEvents + keyEvents) > previousTouchKeyEvents) {
                        synchronized (mAverageWindowedEventTime) {
                            if (mAverageWindowedEventTime.size()
                                    > (EVENT_AVERAGE_WINDOW / 2)) {
                                mean = AppUtil.getMean(mAverageWindowedEventTime);
                                dev = AppUtil.getStandardDeviation(
                                        mAverageWindowedEventTime);
                            }
                        }
                        if (state == State.LOW) {
                            missCount++; // Missed, so adjust hit
                            // hitCount--;
                        } else {
                            hitCount++; // CHANGED FOR PERFORMANCE ANALYSIS  - NO HIT IF NO TOUCH EVENT
                        }
                        previousTouchKeyEvents = touchEvents + keyEvents;
                        // currentBrightness = Math.min(currentBrightness + adjustScreenUp, defaultBrightness);
                        if (currentBrightness != defaultBrightness) {
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness",
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        }
                        timeSinceLastEvent = timeSinceStartup;
                    } else {
                        if (state == State.LOW) {
                            if (currentBrightness > 0.4F) {
                                currentBrightness = currentBrightness
                                        - adjustScreenDown;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                        }
                    }
                    // DVFS  
                    cpuUtil = CPUInfo.getCPUUtilizationPct();
                    avgCpuUtil += cpuUtil;
                    diff = timeSinceStartup - timeSinceLastEvent;
                    switch (state) {
                    case LOW:
                        probOfEvent = AppUtil.getNormProb(diff, mean, dev, 100);
                        if (probOfEvent > lowToHighProb || diff == 0) {
                            state = State.HIGH;
                            if (curFreq != nominalFreq) {
                                dvfs.setNominalCPUFrequency();
                                curFreq = nominalFreq;
                            }
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness", 
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        } else if (probOfEvent > lowStepUpProb || cpuUtil > 80) {
                            if (curFreq != lowNominalFreq) {
                                dvfs.setLowNominalCPUFrequency();
                                curFreq = lowNominalFreq;
                            }
                        }
                        lowCount++;
                        break;

                    case HIGH:
                        if (diff > 0) {
                            probOfEvent = AppUtil.getNormProb(diff, mean, dev, 
                                    100);
                            if (probOfEvent < highToLowProb && cpuUtil < 80) {
                                state = State.LOW;
                                if (curFreq != lowFreq) {
                                    dvfs.setLowestCPUFrequency();
                                    curFreq = lowFreq;
                                }                                   
                            }
                        }
                        highCount++;
                        break;
                    }
                    synchronized (mRunDVFSAlgorithm) {
                        mRunDVFSAlgorithm.wait(basePeriod);
                    }
                    
                    // curFreq = dvfs.getCPUFrequency();
                    totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq, 
                            cpuUtil);
                    totalPowerNominal += PowerModels.getMWScreenPower(
                            defaultBrightnessPct);
                    
                    if (curFreq < nominalFreq
                            || currentBrightness < defaultBrightness) {
                        totalPowerSavedTime += (basePeriod / 1000F);
                        if (currentBrightness < defaultBrightness) {
                            totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                    defaultBrightness, currentBrightness);
                        }
                        if (curFreq < nominalFreq) {     
                            totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                    nominalFreq, curFreq, cpuUtil);
                        }
                    }
                    timeSinceStartup += basePeriod;
                    if (notificationCount++ >= 10) {          
                        postNotification(
                                String.format("[%.3f mW, %.3f %%]", 
                                totalPowerSaved, totalSOCSaved),
                                R.drawable.cpu_icon);
                        notificationCount = 0;
                    }                    
                }
            } else {
                postNotification("STAT ERROR", R.drawable.cpu_icon);
            }
        }
    }

    private void runMovingAverageAlgorithm() throws InterruptedException {
        if (dvfs != null
                && (dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace 
                        || dvfs.setFrequencyGovernor(
                                FrequencyGovernors.Userspace))) {
            long basePeriod = 1000; // Default milliseconds
            float lowToHighProb = 0.7F;
            float lowStepUpProb = 0.6F;
            float highToLowProb = 0.5F;
            int nominalFreq = 729600; // changed for Nexus One
            int lowFreq = 245000; // changed for Nexus One
            int lowNominalFreq = 537600; // changed for Nexus One
            int curFreq = dvfs.getCPUFrequency();

            switch (currentFocusedApp.getAppClassification()) {
            // journal changes
            case VeryLowInteraction:
                basePeriod = 1000;
                lowToHighProb = 0.75F;
                lowStepUpProb = 0.65F;
                highToLowProb = 0.55F;

            case LowInteraction:
                basePeriod = 850;
                lowToHighProb = 0.7F;
                lowStepUpProb = 0.6F;
                highToLowProb = 0.5F;
                break;

            case LowMedInteraction:
                basePeriod = 700;
                lowToHighProb = 0.65F;
                lowStepUpProb = 0.55F;
                highToLowProb = 0.45F;

            case MedInteraction:
                basePeriod = 550;
                lowToHighProb = 0.6F;
                lowStepUpProb = 0.5F;
                highToLowProb = 0.4F;
                break;

            case MedHighInteraction:
                basePeriod = 400;
                lowToHighProb = 0.55F;
                lowStepUpProb = 0.45F;
                highToLowProb = 0.35F;

            case HighInteraction:
                basePeriod = 250;
                lowToHighProb = 0.5F;
                lowStepUpProb = 0.4F;
                highToLowProb = 0.3F;

            case VeryHighInteraction:
                basePeriod = 100;
                lowToHighProb = 0.45F;
                lowStepUpProb = 0.35F;
                highToLowProb = 0.25F;
                break;
            }
            float probOfEvent = 0F;

            if (curFreq != nominalFreq) {
                dvfs.setNominalCPUFrequency();
                curFreq = nominalFreq;
            }
            State state = State.HIGH;
            float mean, dev;        
            int cpuUtil = 0;
            float adjustScreenDown = 0.1F;

            if (currentFocusedApp.getKeyMean()
                    < currentFocusedApp.getTouchMean()) {
                mean = currentFocusedApp.getKeyMean();
                dev = currentFocusedApp.getKeyStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getTouchMean();
                    dev = currentFocusedApp.getTouchStandardDeviation();
                }
            } else {
                mean = currentFocusedApp.getTouchMean();
                dev = currentFocusedApp.getTouchStandardDeviation();
                if (mean <= 0) {
                    mean = currentFocusedApp.getKeyMean();
                    dev = currentFocusedApp.getKeyStandardDeviation();
                }
            }
            float cov = dev / mean;

            if (cov < 0.5) { // Low variability
                adjustScreenDown = 0.15F;
            } else if (cov < 0.75) { // Med variability
                adjustScreenDown = 0.10F;
            } else if (cov > 1) { // High variability
                adjustScreenDown = 0.05F;
            }
            int previousTouchKeyEvents;
            long timeSinceLastEvent = 0;
            long timeSinceStartup = 0;
            Intent brightnessIntent = new Intent();

            brightnessIntent.setAction(SCREENBRIGHTNESS_CHANGE_EVENT);
            float currentBrightness = getDefaultScreenBrightness();
            float defaultBrightness = getDefaultScreenBrightness();
            int defaultBrightnessPct = (int) defaultBrightness * 100;
            int notificationCount = 0;
            float diff;
            int timeAvgWindow = (int) Math.max(1, (mean / basePeriod) * 0.5F);
            int windowCounter = 0;

            if (dev > 0 && mean > 0) {
                previousTouchKeyEvents = touchEvents + keyEvents;
                    
                while (isAlgorithmRunning) {           
                    // hitCount++; //Predict hit
                    // SCREEN
                    if ((touchEvents + keyEvents) > previousTouchKeyEvents) {
                        if (state == State.LOW) {
                            missCount++; // Missed, so adjust hit
                            // hitCount--;
                        } else {
                            hitCount++; // CHANGED FOR PERFORMANCE ANALYSIS  - NO HIT IF NO TOUCH EVENT
                        }
                        previousTouchKeyEvents = touchEvents + keyEvents;
                        // currentBrightness = Math.min(currentBrightness + adjustScreenUp, defaultBrightness);
                        if (currentBrightness != defaultBrightness) {
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness",
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        }
                        timeSinceLastEvent = timeSinceStartup;
                    } else {
                        if (state == State.LOW) {
                            if (currentBrightness > 0.4F) {
                                currentBrightness = currentBrightness
                                        - adjustScreenDown;
                                brightnessIntent.putExtra("brightness", 
                                        currentBrightness);
                                sendBroadcast(brightnessIntent);
                            }
                        }
                    }
                    // DVFS  
                    cpuUtil = CPUInfo.getCPUUtilizationPct();
                    avgCpuUtil += cpuUtil;
                    diff = timeSinceStartup - timeSinceLastEvent;
                    switch (state) {
                    case LOW:
                        probOfEvent = AppUtil.getNormProb(diff, mean, dev, 100);
                        if (probOfEvent > lowToHighProb || diff == 0) {
                            state = State.HIGH;
                            if (curFreq != nominalFreq) {
                                dvfs.setNominalCPUFrequency();
                                curFreq = nominalFreq;
                            }
                            currentBrightness = defaultBrightness;
                            brightnessIntent.putExtra("brightness", 
                                    currentBrightness);
                            sendBroadcast(brightnessIntent);
                        } else if (probOfEvent > lowStepUpProb || cpuUtil > 80) {
                            if (curFreq != lowNominalFreq) {
                                dvfs.setLowNominalCPUFrequency();
                                curFreq = lowNominalFreq;
                            }
                        }
                        lowCount++;
                        break;

                    case HIGH:
                        if (diff > 0) {
                            probOfEvent = AppUtil.getNormProb(diff, mean, dev, 
                                    100);
                            if (probOfEvent < highToLowProb && cpuUtil < 80) {
                                state = State.LOW;
                                if (curFreq != lowFreq) {
                                    dvfs.setLowestCPUFrequency();
                                    curFreq = lowFreq;
                                }                                   
                            }
                        }
                        highCount++;
                        break;
                    }
                    synchronized (mRunDVFSAlgorithm) {
                        mRunDVFSAlgorithm.wait(basePeriod);
                    }
                    // curFreq = dvfs.getCPUFrequency();
                    totalPowerNominal += PowerModels.getMWCPUPower(nominalFreq, 
                            cpuUtil);
                    totalPowerNominal += PowerModels.getMWScreenPower(
                            defaultBrightnessPct);
                    if (curFreq < nominalFreq
                            || currentBrightness < defaultBrightness) {
                        totalPowerSavedTime += (basePeriod / 1000F);
                        if (currentBrightness < defaultBrightness) {
                            totalPowerSaved += PowerModels.getMWScreenPowerSavings(
                                    defaultBrightness, currentBrightness);
                        }
                        if (curFreq < nominalFreq) {     
                            totalPowerSaved += PowerModels.getMWCPUPowerSavings(
                                    nominalFreq, curFreq, cpuUtil);
                        }
                    }
                    timeSinceStartup += basePeriod;
                    if (notificationCount++ >= 10) {          
                        postNotification(
                                String.format("[%.3f mW, %.3f %%]", 
                                totalPowerSaved, totalSOCSaved),
                                R.drawable.cpu_icon);
                        notificationCount = 0;
                    }        
                    if (mTimeAverageWindowedEventTime.size()
                            >= EVENT_AVERAGE_WINDOW) {
                        mTimeAverageWindowedEventTime.remove(0);
                        mTimeAverageWindowedEventTime.add((float) diff);
                    } else {
                        mTimeAverageWindowedEventTime.add((float) diff);
                    }
                    if (windowCounter++ >= timeAvgWindow) {
                        float windowMean = AppUtil.getMean(
                                mTimeAverageWindowedEventTime);
                    
                        switch (currentFocusedApp.getAppClassification()) {
                        // journal changes
                        case VeryLowInteraction:                           
                            if (mean > (windowMean + 1000)) {
                                mean = Math.max(1000, mean - 1000);
                            } else if (mean < (windowMean - 1000)) {
                                mean = mean + 1000;
                            }
                            break;

                        case LowInteraction:                           
                            if (mean > (windowMean + 850)) {
                                mean = Math.max(850, mean - 850);
                            } else if (mean < (windowMean - 850)) {
                                mean = mean + 850;
                            }
                            break;

                        case LowMedInteraction:                           
                            if (mean > (windowMean + 700)) {
                                mean = Math.max(700, mean - 700);
                            } else if (mean < (windowMean - 700)) {
                                mean = mean + 700;
                            }
                            break;

                        case MedInteraction:
                            if (mean > (windowMean + 550)) {
                                mean = Math.max(500, mean - 500);
                            } else if (mean < (windowMean - 500)) {
                                mean = mean + 500;
                            }
                            break;

                        case MedHighInteraction:
                            if (mean > (windowMean + 400)) {
                                mean = Math.max(400, mean - 400);
                            } else if (mean < (windowMean - 400)) {
                                mean = mean + 400;
                            }
                            break;

                        case HighInteraction:
                            if (mean > (windowMean + 250)) {
                                mean = Math.max(250, mean - 250);
                            } else if (mean < (windowMean - 250)) {
                                mean = mean + 250;
                            }
                            break;

                        case VeryHighInteraction:
                            if (mean > (windowMean + 100)) {
                                mean = Math.max(100, mean - 100);
                            } else if (mean < (windowMean - 100)) {
                                mean = mean + 100;
                            }
                            break;
                        }
                        windowCounter = 0;
                    }
                }
            } else {
                postNotification("STAT ERROR", R.drawable.cpu_icon);
            }
        }
    }

    private void setScreenOffDVFSLevel() {
        int nominalFreq = 528000;
        int curFreq = nominalFreq;
        int lowNominalFreq = 245760;

        if (isAlgorithmRunning) {
            stopDVFSAlgorithm();
        }
        if (dvfs != null
                && dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace) {
            if (curFreq != lowNominalFreq) {
                dvfs.setLowNominalCPUFrequency();
                curFreq = lowNominalFreq;
            }
        }
    }

    private void setScreenOnDVFSLevel() {
        int nominalFreq = 528000;
        int curFreq = nominalFreq;

        if (dvfs != null
                && dvfs.getCurrentGovernor() == FrequencyGovernors.Userspace
                && !isAlgorithmRunning) {
            ArrayList<Integer> freq = dvfs.getFrequencyScaleModes();

            if (freq != null && freq.size() > 0) {
                if (curFreq != nominalFreq) {
                    dvfs.setNominalCPUFrequency();
                    curFreq = nominalFreq;
                }
            } else {
                dvfs.setCPUFrequency(dvfs.getMaxCPUFrequency());
                curFreq = dvfs.getMaxCPUFrequency();
            }
        }
    }

    private float getDefaultScreenBrightness() {
        float temp;

        try {
            int brightness = (int) 
                    android.provider.Settings.System.getFloat(
                    getContentResolver(), 
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);

            temp = brightness / 255F;
            if (temp < 0.4F) {
                temp = 0.4F;
            }
            return temp;
        } catch (SettingNotFoundException e) {
            return 1F;
        }
    }
    
    /*
     * naiveNumEventClassifier classifies apps after a specified number
     * of events by taking the average of all time between events
     * received thus far
     */
    public void naiveNumEventClassifier() {
        int i;
        long totalTime = 0;
        long average = 0;

        if (touchEvents + keyEvents < NUM_EVENTS_CLASSIFIER) {
            return;
        } else {
            for (i = 0; i < deltaTouchEventTimeList.size(); i++) {
                totalTime += deltaTouchEventTimeList.get(i);
            }
            for (i = 0; i < deltaKeyEventTimeList.size(); i++) {
                totalTime += deltaKeyEventTimeList.get(i);
            }
            average = totalTime
                    / (deltaTouchEventTimeList.size()
                            + deltaKeyEventTimeList.size());
            if (average <= 2000) {
                // classify as very high interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.VeryHighInteraction);
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            } else if (average <= 3000) {
                // classify as high interaction
                
                currentFocusedApp.classifyApplication(
                        AppClassification.HighInteraction);
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            } else if (average <= 4000) {
                // classify as medium-high interaction
                
                currentFocusedApp.classifyApplication(
                        AppClassification.MedHighInteraction);
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            } else if (average <= 5000) {
                // classify as medium interaction
                
                currentFocusedApp.classifyApplication(
                        AppClassification.MedInteraction);
                
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            } else if (average <= 6000) {
                // classify as low-medium interaction
                
                currentFocusedApp.classifyApplication(
                        AppClassification.LowMedInteraction);
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            } else {
                // classify as low interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.LowInteraction);
                currentFocusedApp.setTrainedWithClassification(
                        currentFocusedApp.getAppClassification(
                        ));
                Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void bayesianClassifier(float mean, float sd, long dT) {
        String meanLevel = "";
        String sdLevel = "";
        String etLevel = "";
        // posterior probabilities
        float postProbVeryFast = 0F; // journal addition
        float postProbFast = 0F;    
        float postProbMedFast = 0F; // journal addition
        float postProbMed = 0F;
        float postProbSlowMed = 0F; // journal addition
        float postProbSlow = 0F;
        float postProbVerySlow = 0F; // journal addition
        // prior probabilities
        float priorProbVeryFast = 0F; // journal addition
        float priorProbFast = 0F;
        float priorProbMedFast = 0F; // journal addition
        float priorProbMed = 0F;
        float priorProbSlowMed = 0F; // journal addition
        float priorProbSlow = 0F;
        float priorProbVerySlow = 0F; // journal addition
        // class likelihoods
        float likelihoodMeanVeryFast = 0F; // journal addition219
        float likelihoodSDVeryFast = 0F; // journal addition 
        float likelihoodMeanFast = 0F;
        float likelihoodSDFast = 0F;
        float likelihoodMeanMedFast = 0F; // journal addition
        float likelihoodSDMedFast = 0F; // journal addition 
        float likelihoodMeanMed = 0F;
        float likelihoodSDMed = 0F;
        float likelihoodMeanSlowMed = 0F; // journal addition
        float likelihoodSDSlowMed = 0F; // journal addition 
        float likelihoodMeanSlow = 0F;
        float likelihoodSDSlow = 0F;
        float likelihoodMeanVerySlow = 0F; // journal addition
        float likelihoodSDVerySlow = 0F; // journal addition 
        
        if (mean > 0 && sd > 0 && dT > 0) {
            if (mean <= 1000) {
                meanLevel = "very fast";
            } else if (mean <= 2000) {
                meanLevel = "fast";
            } else if (mean <= 3000) {
                meanLevel = "med fast";
            } else if (mean <= 4000) {
                meanLevel = "med";
            } else if (mean <= 5000) {
                meanLevel = "slow med";
            } else if (mean <= 6000) {
                meanLevel = "slow";
            } else {
                meanLevel = "very slow";
            }
            if (sd <= 1000) {
                sdLevel = "very low";
            } else if (sd <= 1500) {
                sdLevel = "low";
            } else if (sd <= 2000) {
                sdLevel = "low med";
            } else if (sd <= 2500) {
                sdLevel = "med";
            } else if (sd <= 3000) {
                sdLevel = "med high";
            } else if (sd <= 3500) {
                sdLevel = "high";
            } else {
                sdLevel = "very high";
            }
            if (dT <= 1000) {
                etLevel = "very fast";
            } else if (dT <= 2000) {
                etLevel = "fast";
            } else if (dT <= 3000) {
                etLevel = "med fast";
            } else if (dT <= 4000) {
                etLevel = "med";
            } else if (dT <= 5000) {
                etLevel = "slow med";
            } else if (dT <= 6000) {
                etLevel = "slow";
            } else {
                etLevel = "very slow";
            }
        }
        if (meanLevel.compareTo("") != 0 && sdLevel.compareTo("") != 0
                && etLevel.compareTo("") != 0) {
            priorProbVeryFast = currentFocusedApp.findTTProb("very fast");
            priorProbFast = currentFocusedApp.findTTProb("fast");
            priorProbMedFast = currentFocusedApp.findTTProb("med fast");
            priorProbMed = currentFocusedApp.findTTProb("med");
            priorProbSlowMed = currentFocusedApp.findTTProb("slow med");
            priorProbSlow = currentFocusedApp.findTTProb("slow");
            priorProbVerySlow = currentFocusedApp.findTTProb("very slow");
            likelihoodMeanVeryFast = currentFocusedApp.findMeanAndTTProb(
                    meanLevel, "very fast");
            likelihoodSDVeryFast = currentFocusedApp.findSDAndTTProb(sdLevel,
                    "very fast");
            likelihoodMeanFast = currentFocusedApp.findMeanAndTTProb(meanLevel, 
                    "fast");
            likelihoodSDFast = currentFocusedApp.findSDAndTTProb(sdLevel, "fast");
            likelihoodMeanMedFast = currentFocusedApp.findMeanAndTTProb(
                    meanLevel, "med fast");
            likelihoodSDMedFast = currentFocusedApp.findSDAndTTProb(sdLevel,
                    "med fast");
            likelihoodMeanMed = currentFocusedApp.findMeanAndTTProb(meanLevel,
                    "med");
            likelihoodSDMed = currentFocusedApp.findSDAndTTProb(sdLevel, "med");
            likelihoodMeanSlowMed = currentFocusedApp.findMeanAndTTProb(
                    meanLevel, "slow med");
            likelihoodSDSlowMed = currentFocusedApp.findSDAndTTProb(sdLevel,
                    "slow med");
            likelihoodMeanSlow = currentFocusedApp.findMeanAndTTProb(meanLevel, 
                    "slow");
            likelihoodSDSlow = currentFocusedApp.findSDAndTTProb(sdLevel, "slow");
            likelihoodMeanVerySlow = currentFocusedApp.findMeanAndTTProb(
                    meanLevel, "very slow");
            likelihoodSDVerySlow = currentFocusedApp.findSDAndTTProb(sdLevel,
                    "very slow");
    
            postProbVeryFast = priorProbVeryFast * likelihoodMeanVeryFast
                    * likelihoodSDVeryFast;
            postProbFast = priorProbFast * likelihoodMeanFast * likelihoodSDFast;
            postProbMedFast = priorProbMedFast * likelihoodMeanMedFast
                    * likelihoodSDMedFast;
            postProbMed = priorProbMed * likelihoodMeanMed * likelihoodSDMed;
            postProbSlowMed = priorProbSlowMed * likelihoodMeanSlowMed
                    * likelihoodSDSlowMed;
            postProbSlow = priorProbSlow * likelihoodMeanSlow * likelihoodSDSlow;
            postProbVerySlow = priorProbVerySlow * likelihoodMeanVerySlow
                    * likelihoodSDVerySlow;
            if (Float.isNaN(postProbVeryFast)) {
                postProbVeryFast = 0;
            }
            if (Float.isNaN(postProbFast)) {
                postProbFast = 0;
            }
            if (Float.isNaN(postProbMedFast)) {
                postProbMedFast = 0;
            }
            if (Float.isNaN(postProbMed)) {
                postProbMed = 0;
            }
            if (Float.isNaN(postProbSlowMed)) {
                postProbSlowMed = 0;
            }
            if (Float.isNaN(postProbSlow)) {
                postProbSlow = 0;
            }
            if (Float.isNaN(postProbVerySlow)) {
                postProbVerySlow = 0;
            }
            
            if (postProbVeryFast >= postProbFast
                    && postProbVeryFast >= postProbMedFast
                    && postProbVeryFast >= postProbMed
                    && postProbVeryFast >= postProbSlowMed
                    && postProbVeryFast >= postProbSlow
                    && postProbVeryFast >= postProbVerySlow) {
                // probability of an event happening fast is high
                // so classify app as high interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.VeryHighInteraction);
            } else if (postProbFast >= postProbVeryFast
                    && postProbFast >= postProbMedFast
                    && postProbFast >= postProbMed
                    && postProbFast >= postProbSlowMed
                    && postProbFast >= postProbSlow
                    && postProbFast >= postProbVerySlow) {
                // probability of an event happening fast is high
                // so classify app as high interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.HighInteraction);
            } else if (postProbMedFast >= postProbVeryFast
                    && postProbMedFast >= postProbFast
                    && postProbMedFast >= postProbMed
                    && postProbMedFast >= postProbSlowMed
                    && postProbMedFast >= postProbSlow
                    && postProbMedFast >= postProbVerySlow) {
                // probability of an event happening fast is high
                // so classify app as high interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.MedHighInteraction);
            } else if (postProbMed >= postProbVeryFast
                    && postProbMed >= postProbFast
                    && postProbMed >= postProbMedFast
                    && postProbMed >= postProbSlowMed
                    && postProbMed >= postProbSlow
                    && postProbMed >= postProbVerySlow) {
                
                currentFocusedApp.classifyApplication(
                        AppClassification.MedInteraction);
            } else if (postProbSlowMed >= postProbVeryFast
                    && postProbSlowMed >= postProbFast
                    && postProbSlowMed >= postProbMedFast
                    && postProbSlowMed >= postProbMed222
                    && postProbSlowMed >= postProbSlow
                    && postProbSlowMed >= postProbVerySlow) {
                
                currentFocusedApp.classifyApplication(
                        AppClassification.LowMedInteraction);
            } else if (postProbSlow >= postProbVeryFast
                    && postProbSlow >= postProbFast
                    && postProbSlow >= postProbMedFast
                    && postProbSlow >= postProbMed
                    && postProbSlow >= postProbSlowMed
                    && postProbSlow >= postProbVerySlow) {
                // probability of an event happening slow is high
                // so classify app as low interaction
                currentFocusedApp.classifyApplication(
                        AppClassification.LowInteraction);
            } else if (postProbVerySlow >= postProbVeryFast
                    && postProbVerySlow >= postProbFast
                    && postProbVerySlow >= postProbMedFast
                    && postProbVerySlow >= postProbMed
                    && postProbVerySlow >= postProbSlowMed
                    && postProbVerySlow >= postProbSlow) {
                
                currentFocusedApp.classifyApplication(
                        AppClassification.VeryLowInteraction);
            }
            currentFocusedApp.addTrainingSetEntry(meanLevel, sdLevel, etLevel);
            if (previousClassification != AppClassification.NotClassified) {
                if (currentFocusedApp.getAppClassification()
                        == previousClassification) {
                    unchangedClassCount++;
                    if (unchangedClassCount > 5) {
                        // set app trained with current classification
                        
                        currentFocusedApp.setTrainedWithClassification(
                                currentFocusedApp.getAppClassification(
                                ));
                        Toast.makeText(getBaseContext(), String.format("App classified as %s!", AppClassification.toString(currentFocusedApp.getAppClassification())), Toast.LENGTH_LONG).show();
                    }
                } else {
                    unchangedClassCount = 0;
                }
            }
            previousClassification = currentFocusedApp.getAppClassification();

            /* Toast.makeText(getBaseContext(), String.format("app: %s\nmean: %.1f\nsd: 
             %.1f\ntt: %d\nmeanLevel: %s\nsdLevel: %s\nttLevel: %s\ntrainingSet size: 
             %d\npostProbHigh: %.1f\npostProbMed: %.1f\npostProbLow: %.1f\nunchangedClassCount: 
             %d\nclassification: %s", 
             //                              currentFocusedApp.getName(), mean, sd, et, 
             meanLevel, sdLevel, etLevel, currentFocusedApp.getTrainingSet().size(), postProbFast, 
             postProbMed, postProbSlow, unchangedClassCount, 
             currentFocusedApp.getAppClassification().toString()), Toast.LENGTH_LONG).show();
             */  
        }
    }
}
