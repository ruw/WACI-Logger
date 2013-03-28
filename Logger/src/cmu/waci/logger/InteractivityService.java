package cmu.waci.logger;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class InteractivityService extends Service{
	InteractivityView mView;

	public void onCreate() {
		super.onCreate();
		System.out.println("h");
		
		mView = new InteractivityView(this);
		
		
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
		
		
		final GestureDetector gestureDetector = new GestureDetector(this, new InteractivityListener());
		View.OnTouchListener gestureListener = new View.OnTouchListener() {
		      public boolean onTouch(View v, MotionEvent event) {
		    	  System.out.println("good1");
		            return false;//gestureDetector.onTouchEvent(event);
		      }
		};
		
		WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
		
		mView.setOnTouchListener(gestureListener);
		wm.addView(mView, params);
		
        Thread thr = new Thread(null, doWork, "Interactivity");
        thr.start();
		
	}
	
	Runnable doWork = new Runnable() {
		public void run(){
			System.out.println("wat");
		
		}
	};
    
    
	class InteractivityView extends View {

		public InteractivityView(Context context) {
			super(context);
		}
		
	    @Override
	    public boolean onTouchEvent(MotionEvent event) {
	        //return super.onTouchEvent(event);
	    	System.out.println("good2");
	    //    Toast.makeText(getContext(),"onTouchEvent", Toast.LENGTH_LONG).show();
	        return false;
	    }
		
	}
	
	class InteractivityListener extends SimpleOnGestureListener {
		
		
		public boolean onTouchEvent(MotionEvent e) {
			System.out.println("good");
			Toast.makeText(getBaseContext(), "HELLLLLLLOOOOOOO WORLD! ", 0).show();
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