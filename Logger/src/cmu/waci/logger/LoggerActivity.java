package cmu.waci.logger;

import java.util.Arrays;

import cmu.waci.logger.LoggerService.LocalBinder;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.widget.Toast;

public class LoggerActivity extends Activity {
	private static final String TAG = "LoggerActivity";
	
	private LoggerService mLogger;
	private boolean mBound;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logger);
        
        System.out.println("HELLO?");
        Toast.makeText(this, "hi ",0).show();
 //       System.out.println(Arrays.toString(CPUInfo.getCPUStats()));
        
  //      mLogger.bindService(new Intent(this, 
  //              LoggerService.class), mConnection, Context.BIND_AUTO_CREATE);
        Intent intent = new Intent(this, LoggerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }


    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.logger, menu);
        return true;
    }
    
    
    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService

    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mLogger = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}