package cmu.waci.logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.sql.Timestamp;

import com.manor.currentwidget.library.CurrentReaderFactory;

import csu.research.AURA.CPUInfo;
import csu.research.AURA.DVFSControl;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

//@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class LoggerService extends Service {
	private static final String TAG = "LoggerService";

	private PowerManager powMan;

	// private SensorInfo mSensorInfo;

	private DVFSControl dvfs;

	private BufferedWriter mOut;
	private boolean mRunning;
	Date date;
	Timestamp tstamp;
	Calendar rightNow;


	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void onCreate() {
		powMan = (PowerManager) getSystemService(POWER_SERVICE);

		dvfs = new DVFSControl();
		mRunning = true;

		Toast.makeText(this, "Create LoggerService ", 1).show();
		System.out.println("Create LoggerService");

		Thread thr = new Thread(null, doWork, "Logger service");
		thr.start();
	}

	/**
	 * The function that runs in our worker thread
	 */
	Runnable doWork = new Runnable() {
		@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
		public void run() {

			// notification and set foreground
			@SuppressWarnings("deprecation")
			Notification note = new Notification.Builder(
					getApplicationContext()).setContentTitle("Logging")
					.setSmallIcon(R.drawable.ic_launcher).getNotification();

			startForeground(1337, note);

			boolean mExternalStorageAvailable = false;
			boolean mExternalStorageWriteable = false;
			String state = Environment.getExternalStorageState();

			if (Environment.MEDIA_MOUNTED.equals(state)) {
				// We can read and write the media
				mExternalStorageAvailable = mExternalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				// We can only read the media
				mExternalStorageAvailable = true;
				mExternalStorageWriteable = false;
			} else {
				// Something else is wrong. It may be one of many other states,
				// but all we need
				// to know is we can neither read nor write
				mExternalStorageAvailable = mExternalStorageWriteable = false;
			}
			// can't open a file, abort
			if (!(mExternalStorageAvailable && mExternalStorageWriteable)) {
				Log.e(TAG, "Could not get external storage");
				LoggerService.this.stopSelf();
			}
			// System.out.println(getExternalFilesDir(null));
			System.out.println("Write to log.");
			
			Calendar now = Calendar.getInstance();
			String log_n;
			//log file name is current date and time
			log_n = (now.get(Calendar.MONTH)+1)+"_"+(now.get(Calendar.DAY_OF_MONTH)+1)+"_"+
				now.get(Calendar.HOUR_OF_DAY)+"_"+now.get(Calendar.MINUTE)+"_"+
				now.get(Calendar.SECOND)+".txt";
			File log = new File(getExternalFilesDir(null), log_n);

			mOut = null;
			try {
				mOut = new BufferedWriter(new FileWriter(log.getAbsolutePath(),
						log.exists()));
			} catch (IOException e) {
				Log.e(TAG, "Exception opening log file", e);
				LoggerService.this.stopSelf();
			}

			while (mRunning) {

				try {
					String outputString = "";
			
					 //Get battery data
					 IntentFilter ifilter = new IntentFilter(
							Intent.ACTION_BATTERY_CHANGED);
					Intent batteryStatus = registerReceiver(null, ifilter);
					
					// Are we charging / charged? int batStat =
					int level = batteryStatus.getIntExtra(
							BatteryManager.EXTRA_LEVEL, -1);
					int scale = batteryStatus.getIntExtra(
							BatteryManager.EXTRA_SCALE, -1);
					float currentVoltage = (float) batteryStatus.getIntExtra(
							"voltage", 0) / 1000;
					float batteryPct = level / (float) scale;
				
					outputString = String.format(
							//"%d,%d,%d,%d,%f,%f,%d,%d,%d,%d,%d\n", 
							"%d,%d,%d,%d,%f,%f,%d\n", 
							CPUInfo.getCPUUtilizationPct(), 
							dvfs.getCPUFrequency(),
							InteractivityService.mActs.size(),
							CurrentReaderFactory.getValue() * -1, batteryPct,
							currentVoltage, 
							powMan.isScreenOn() ? 1 : 0);

					date = new Date();
					tstamp = new Timestamp(date.getTime());
					mOut.write(tstamp.toString()+",");
					
					
					mOut.write(outputString);
					SystemClock.sleep(500);

				} catch (IOException e) {
					Log.e(TAG, "Exception appending to log file", e);
				}
			}

			try {
				mOut.close();
			} catch (IOException e) {

			}
			System.out.println("Finished/Done");
			stopForeground(true);
			LoggerService.this.stopSelf();
		}
	};

	public void onDestroy() {

		mRunning = false;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		LoggerService getService() {
			return LoggerService.this;
		}
	}

}
