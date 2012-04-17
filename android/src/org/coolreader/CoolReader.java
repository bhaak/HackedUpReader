// Main Class
package org.coolreader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Locale;

import org.coolreader.crengine.AboutDialog;
import org.coolreader.crengine.BackgroundThread;
import org.coolreader.crengine.BookInfo;
import org.coolreader.crengine.BookmarksDlg;
import org.coolreader.crengine.DeviceInfo;
import org.coolreader.crengine.EinkScreen;
import org.coolreader.crengine.Engine;
import org.coolreader.crengine.Engine.HyphDict;
import org.coolreader.crengine.FileBrowser;
import org.coolreader.crengine.FileInfo;
import org.coolreader.crengine.History;
import org.coolreader.crengine.InterfaceTheme;
import org.coolreader.crengine.L;
import org.coolreader.crengine.Logger;
import org.coolreader.crengine.OptionsDialog;
import org.coolreader.crengine.Properties;
import org.coolreader.crengine.ReaderAction;
import org.coolreader.crengine.ReaderView;
import org.coolreader.crengine.Scanner;
import org.coolreader.crengine.Settings;
import org.coolreader.crengine.Settings.Lang;
import org.coolreader.crengine.TTS;
import org.coolreader.crengine.TTS.OnTTSCreatedListener;
import org.coolreader.crengine.ToastView;
import org.coolreader.crengine.Utils;
import org.coolreader.db.CRDBService;
import org.coolreader.db.CRDBServiceAccessor;
import org.coolreader.donations.BillingService;
import org.coolreader.donations.BillingService.RequestPurchase;
import org.coolreader.donations.BillingService.RestoreTransactions;
import org.coolreader.donations.Consts;
import org.coolreader.donations.Consts.PurchaseState;
import org.coolreader.donations.Consts.ResponseCode;
import org.coolreader.donations.PurchaseObserver;
import org.coolreader.donations.ResponseHandler;
import org.coolreader.sync.SyncServiceAccessor;
import org.koekak.android.ebookdownloader.SonyBookSelector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.PowerManager;
import android.text.ClipboardManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.Toast;

public class CoolReader extends Activity
{
	public static final Logger log = L.create("cr");
	
	Engine mEngine;
	ReaderView mReaderView;
	Scanner mScanner;
	FileBrowser mBrowser;
	FrameLayout mFrame;
	//View startupView;
	History mHistory;
	//CRDB mDB;
	private BackgroundThread mBackgroundThread;
	
	
	public CoolReader() {
	    brightnessHackError = false; //DeviceInfo.SAMSUNG_BUTTONS_HIGHLIGHT_PATCH;
	}
	
	public Scanner getScanner()
	{
		return mScanner;
	}
	
	public History getHistory() 
	{
		return mHistory;
	}
	
	public Engine getEngine() {
		return mEngine;
	}
	
	public FileBrowser getBrowser() {
		return mBrowser;
	}
	
	public ReaderView getReaderView() 
	{
		return mReaderView;
	}
	
	public CRDBService.LocalBinder getDB()
	{
		return mCRDBService.get();
	}
	
	private static String PREF_FILE = "CR3LastBook";
	private static String PREF_LAST_BOOK = "LastBook";
	private static String PREF_HELP_FILE = "HelpFile";
	public String getLastSuccessfullyOpenedBook()
	{
		SharedPreferences pref = getSharedPreferences(PREF_FILE, 0);
		String res = pref.getString(PREF_LAST_BOOK, null);
		pref.edit().putString(PREF_LAST_BOOK, null).commit();
		return res;
	}
	
	public void setLastSuccessfullyOpenedBook( String filename )
	{
		SharedPreferences pref = getSharedPreferences(PREF_FILE, 0);
		pref.edit().putString(PREF_LAST_BOOK, filename).commit();
	}
	
	public String getLastGeneratedHelpFileSignature()
	{
		SharedPreferences pref = getSharedPreferences(PREF_FILE, 0);
		String res = pref.getString(PREF_HELP_FILE, null);
		return res;
	}
	
	public void setLastGeneratedHelpFileSignature(String v)
	{
		SharedPreferences pref = getSharedPreferences(PREF_FILE, 0);
		pref.edit().putString(PREF_HELP_FILE, v).commit();
	}
	
	private int mScreenUpdateMode = 0;
	public int getScreenUpdateMode() {
		return mScreenUpdateMode;
	}
	public void setScreenUpdateMode( int screenUpdateMode, View view ) {
		if (mReaderView != null) {
			mScreenUpdateMode = screenUpdateMode;
			if (EinkScreen.UpdateMode != screenUpdateMode || EinkScreen.UpdateMode == 2) {
				EinkScreen.ResetController(screenUpdateMode, view);
			}
		}
	}

	private int mScreenUpdateInterval = 0;
	public int getScreenUpdateInterval() {
		return mScreenUpdateInterval;
	}
	public void setScreenUpdateInterval( int screenUpdateInterval, View view ) {
		mScreenUpdateInterval = screenUpdateInterval;
		if (EinkScreen.UpdateModeInterval != screenUpdateInterval) {
			EinkScreen.UpdateModeInterval = screenUpdateInterval;
			EinkScreen.ResetController(mScreenUpdateMode, view);
		}
	}

	private boolean mNightMode = false;
	public boolean isNightMode() {
		return mNightMode;
	}
	public void setNightMode( boolean nightMode ) {
		mNightMode = nightMode;
	}
	
	private InterfaceTheme currentTheme = DeviceInfo.FORCE_LIGHT_THEME ? InterfaceTheme.WHITE : InterfaceTheme.LIGHT;
	
	public InterfaceTheme getCurrentTheme() {
		return currentTheme;
	}

	public void setCurrentTheme(String themeCode) {
		InterfaceTheme theme = InterfaceTheme.findByCode(themeCode);
		if (theme != null) {
			setCurrentTheme(theme);
		}
	}

	public void setCurrentTheme(InterfaceTheme theme) {
		currentTheme = theme;
		getApplication().setTheme(theme.getThemeId());
		setTheme(theme.getThemeId());
		if (mFrame != null) {
			TypedArray a = getTheme().obtainStyledAttributes(new int[] {android.R.attr.windowBackground, android.R.attr.background, android.R.attr.textColor, android.R.attr.colorBackground, android.R.attr.colorForeground});
			int bgRes = a.getResourceId(0, 0);
			//int clText = a.getColor(1, 0);
			int clBackground = a.getColor(2, 0);
			//int clForeground = a.getColor(3, 0);
			a.recycle();
			if (clBackground != 0)
				mFrame.setBackgroundColor(clBackground);
			if (bgRes != 0)
				mFrame.setBackgroundResource(bgRes);
		}
		if (mBrowser != null)
			mBrowser.onThemeChanged();
	}

	private boolean mFullscreen = false;
	public boolean isFullscreen() {
		return mFullscreen;
	}

	public void applyFullscreen( Window wnd )
	{
		if ( mFullscreen ) {
			//mActivity.getWindow().requestFeature(Window.)
			wnd.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
			        WindowManager.LayoutParams.FLAG_FULLSCREEN );
		} else {
			wnd.setFlags(0, 
			        WindowManager.LayoutParams.FLAG_FULLSCREEN );
		}
		mEngine.setSystemUiVisibility();
	}
	public void setFullscreen( boolean fullscreen )
	{
		if ( mFullscreen!=fullscreen ) {
			mFullscreen = fullscreen;
			applyFullscreen( getWindow() );
		}
	}
	
	
	public boolean isWakeLockEnabled() {
		return screenBacklightDuration > 0;
	}

	/**
	 * @param backlightDurationMinutes 0 = system default, 1 == 3 minutes, 2..5 == 2..5 minutes
	 */
	public void setScreenBacklightDuration(int backlightDurationMinutes)
	{
		if (backlightDurationMinutes == 1)
			backlightDurationMinutes = 3;
		if (screenBacklightDuration != backlightDurationMinutes * 60 * 1000) {
			screenBacklightDuration = backlightDurationMinutes * 60 * 1000;
			if (screenBacklightDuration == 0)
				backlightControl.release();
			else
				backlightControl.onUserActivity();
		}
	}
	
	int screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
	public void applyScreenOrientation( Window wnd )
	{
		if ( wnd!=null ) {
			WindowManager.LayoutParams attrs = wnd.getAttributes();
			attrs.screenOrientation = screenOrientation;
			wnd.setAttributes(attrs);
			if (DeviceInfo.EINK_SCREEN){
				EinkScreen.ResetController(mReaderView);
			}
			
		}
	}

	public int getScreenOrientation()
	{
		switch ( screenOrientation ) {
		case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
			return 0;
		case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
			return 1;
		case ActivityInfo_SCREEN_ORIENTATION_REVERSE_PORTRAIT:
			return 2;
		case ActivityInfo_SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
			return 3;
		default:
			return orientationFromSensor;
		}
	}

	public boolean isLandscape()
	{
		return screenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || screenOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
	}

	// support pre API LEVEL 9
	final static public int ActivityInfo_SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7;
	final static public int ActivityInfo_SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6;
	final static public int ActivityInfo_SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9;
	final static public int ActivityInfo_SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8;
	final static public int ActivityInfo_SCREEN_ORIENTATION_FULL_SENSOR = 10;

	public void setScreenOrientation( int angle )
	{
		int newOrientation = screenOrientation;
		boolean level9 = DeviceInfo.getSDKLevel() >= 9;
		switch (angle) {
		case 0:
			newOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; // level9 ? ActivityInfo_SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			break;
		case 1:
			newOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; // level9 ? ActivityInfo_SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			break;
		case 2:
			newOrientation = level9 ? ActivityInfo_SCREEN_ORIENTATION_REVERSE_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
			break;
		case 3:
			newOrientation = level9 ? ActivityInfo_SCREEN_ORIENTATION_REVERSE_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
			break;
		case 4:
			newOrientation = level9 ? ActivityInfo_SCREEN_ORIENTATION_FULL_SENSOR : ActivityInfo.SCREEN_ORIENTATION_SENSOR;
			break;
		}
		if (newOrientation != screenOrientation) {
			log.d("setScreenOrientation(" + angle + ")");
			screenOrientation = newOrientation;
			setRequestedOrientation(screenOrientation);
			applyScreenOrientation(getWindow());
		}
	}

	private Runnable backlightTimerTask = null;
	private static long lastUserActivityTime;
	public static final int DEF_SCREEN_BACKLIGHT_TIMER_INTERVAL = 3 * 60 * 1000;
	private int screenBacklightDuration = DEF_SCREEN_BACKLIGHT_TIMER_INTERVAL;

	private class ScreenBacklightControl {
		PowerManager.WakeLock wl = null;

		public ScreenBacklightControl() {
		}

		long lastUpdateTimeStamp;
		
		public void onUserActivity() {
			lastUserActivityTime = Utils.timeStamp();
			if (Utils.timeInterval(lastUpdateTimeStamp) < 5000)
				return;
			lastUpdateTimeStamp = android.os.SystemClock.uptimeMillis();
			if (!isWakeLockEnabled())
				return;
			if (wl == null) {
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
				/* | PowerManager.ON_AFTER_RELEASE */, "cr3");
				log.d("ScreenBacklightControl: WakeLock created");
			}
			if (!isStarted()) {
				log.d("ScreenBacklightControl: user activity while not started");
				release();
				return;
			}

			if (!isHeld()) {
				log.d("ScreenBacklightControl: acquiring WakeLock");
				wl.acquire();
			}

			if (backlightTimerTask == null) {
				log.v("ScreenBacklightControl: timer task started");
				backlightTimerTask = new BacklightTimerTask();
				BackgroundThread.instance().postGUI(backlightTimerTask,
						screenBacklightDuration / 10);
			}
		}

		public boolean isHeld() {
			return wl != null && wl.isHeld();
		}

		public void release() {
			if (wl != null && wl.isHeld()) {
				log.d("ScreenBacklightControl: wl.release()");
				wl.release();
			}
			backlightTimerTask = null;
			lastUpdateTimeStamp = 0;
		}

		private class BacklightTimerTask implements Runnable {

			@Override
			public void run() {
				if (backlightTimerTask == null)
					return;
				long interval = Utils.timeInterval(lastUserActivityTime);
				log.v("ScreenBacklightControl: timer task, lastActivityMillis = "
						+ interval);
				int nextTimerInterval = screenBacklightDuration / 20;
				boolean dim = false;
				if (interval > screenBacklightDuration * 8 / 10) {
					nextTimerInterval = nextTimerInterval / 8;
					dim = true;
				}
				if (interval > screenBacklightDuration) {
					log.v("ScreenBacklightControl: interval is expired");
					release();
				} else {
					BackgroundThread.instance().postGUI(backlightTimerTask, nextTimerInterval);
					if (dim) {
						updateBacklightBrightness(-0.9f); // reduce by 9%
					}
				}
			}

		};

	}

	ScreenBacklightControl backlightControl = new ScreenBacklightControl();
	
	public int getPalmTipPixels()
	{
		return densityDpi / 3; // 1/3"
	}
	
	public int getDensityDpi()
	{
		return densityDpi;
	}
	
	private int densityDpi = 160;
	int initialBatteryState = -1;
	String fileToLoadOnStart = null;
	BroadcastReceiver intentReceiver;
	
	private String mVersion = "3.0";
	
	public String getVersion() {
		return mVersion;
	}
	
	TTS tts;
	boolean ttsInitialized;
	boolean ttsError;
	
	public boolean initTTS(final OnTTSCreatedListener listener) {
		if ( ttsError || !TTS.isFound() ) {
			if ( !ttsError ) {
				ttsError = true;
				showToast("TTS is not available");
			}
			return false;
		}
		if ( ttsInitialized && tts!=null ) {
			BackgroundThread.instance().executeGUI(new Runnable() {
				@Override
				public void run() {
					listener.onCreated(tts);
				}
			});
			return true;
		}
		if ( ttsInitialized && tts!=null ) {
			showToast("TTS initialization is already called");
			return false;
		}
		showToast("Initializing TTS");
    	tts = new TTS(this, new TTS.OnInitListener() {
			@Override
			public void onInit(int status) {
				//tts.shutdown();
				L.i("TTS init status: " + status);
				if ( status==TTS.SUCCESS ) {
					ttsInitialized = true;
					BackgroundThread.instance().executeGUI(new Runnable() {
						@Override
						public void run() {
							listener.onCreated(tts);
						}
					});
				} else {
					ttsError = true;
					BackgroundThread.instance().executeGUI(new Runnable() {
						@Override
						public void run() {
							showToast("Cannot initialize TTS");
						}
					});
				}
			}
		});
		return true;
	}
	
	private AudioManager am;
	private int maxVolume;
	public AudioManager getAudioManager() {
		if ( am==null ) {
			am = (AudioManager)getSystemService(AUDIO_SERVICE);
			maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		}
		return am;
	}
	
	public int getVolume() {
		AudioManager am = getAudioManager();
		if (am!=null) {
			return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume;
		}
		return 0;
	}
	
	public void setVolume( int volume ) {
		AudioManager am = getAudioManager();
		if (am!=null) {
			am.setStreamVolume(AudioManager.STREAM_MUSIC, volume * maxVolume / 100, 0);
		}
	}
	
	public View getContentView() {
		return mFrame;
	}
	
	private boolean isFirstStart = true;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
		log.i("CoolReader.onCreate() entered");
		super.onCreate(savedInstanceState);

		try {
			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			mVersion = pi.versionName;
		} catch ( NameNotFoundException e ) {
			// ignore
		}
		log.i("CoolReader version : " + getVersion());
		
		// testing background thread
    	mBackgroundThread = BackgroundThread.instance();
		mEngine = new Engine(this, mBackgroundThread);
		
		mSyncService = new SyncServiceAccessor(this);
		mSyncService.bind(new Runnable() {
			@Override
			public void run() {
				log.i("Initialization after SyncService is bound");
				BackgroundThread.instance().postGUI(new Runnable() {
					@Override
					public void run() {
			        	mSyncService.setSyncDirectory(new File(mScanner.getDownloadDirectory().getPathName()));
					}
				});
			}
		});
		mCRDBService = new CRDBServiceAccessor(this, mEngine.getPathCorrector());
        mCRDBService.bind(new Runnable() {
			@Override
			public void run() {
				log.i("Initialization after SyncService is bound");
				mHistory.loadFromDB(200);
			}
        });

    	isFirstStart = true;
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		Display d = getWindowManager().getDefaultDisplay();
		DisplayMetrics m = new DisplayMetrics(); 
		d.getMetrics(m);
		try {
			Field fld = d.getClass().getField("densityDpi");
			if ( fld!=null ) {
				Object v = fld.get(m);
				if ( v!=null && v instanceof Integer ) {
					densityDpi = ((Integer)v).intValue();
					log.i("Screen density detected: " + densityDpi + "DPI");
				}
			}
		} catch ( Exception e ) {
			log.e("Cannot find field densityDpi, using default value");
		}
		
		intentReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				int level = intent.getIntExtra("level", 0);
				if ( mReaderView!=null )
					mReaderView.setBatteryState(level);
				else
					initialBatteryState = level;
			}
			
		};
		registerReceiver(intentReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));


		log.i("CoolReader.window=" + getWindow());
		WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
		lp.alpha = 1.0f;
		lp.dimAmount = 0.0f;
		lp.format = PixelFormat.RGB_565;
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
		lp.horizontalMargin = 0;
		lp.verticalMargin = 0;
		lp.windowAnimations = 0;
		lp.layoutAnimationParameters = null;
		lp.memoryType = WindowManager.LayoutParams.MEMORY_TYPE_NORMAL;
		getWindow().setAttributes(lp);
		
		// load settings
		Properties props = loadSettings();
		String theme = props.getProperty(ReaderView.PROP_APP_THEME, DeviceInfo.FORCE_LIGHT_THEME ? "WHITE" : "LIGHT");
		String lang = props.getProperty(ReaderView.PROP_APP_LOCALE, Lang.DEFAULT.code);
		setCurrentTheme(theme);
		setLanguage(lang);
    	
		mFrame = new FrameLayout(this);
		mBackgroundThread.setGUI(mFrame);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setFullscreen( props.getBool(ReaderView.PROP_APP_FULLSCREEN, (DeviceInfo.EINK_SCREEN?true:false)));
		int orientation = props.getInt(ReaderView.PROP_APP_SCREEN_ORIENTATION, 4); //(DeviceInfo.EINK_SCREEN?0:4)
		if ( orientation < 0 || orientation > 4 )
			orientation = 0;
		setScreenOrientation(orientation);
		int backlight = props.getInt(ReaderView.PROP_APP_SCREEN_BACKLIGHT, -1);
		if ( backlight<-1 || backlight>100 )
			backlight = -1;
		setScreenBacklightLevel(backlight);

        mEngine.showProgress( 0, R.string.progress_starting_cool_reader );

        // wait until all background tasks are executed
        mBackgroundThread.syncWithBackground();

        String code = props.getProperty(ReaderView.PROP_HYPHENATION_DICT, Engine.HyphDict.RUSSIAN.toString());
        Engine.HyphDict dict = HyphDict.byCode(code);
		mEngine.setHyphenationDictionary(dict);
		
		//this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
        //       WindowManager.LayoutParams.FLAG_FULLSCREEN );
//		startupView = new View(this) {
//		};
//		startupView.setBackgroundColor(Color.BLACK);
		setScreenBacklightDuration(props.getInt(ReaderView.PROP_APP_SCREEN_BACKLIGHT_LOCK, 3));

		// open DB
		final String SQLITE_DB_NAME = "cr3db.sqlite";
		File dbdir = getDir("db", Context.MODE_PRIVATE);
		dbdir.mkdirs();
		File dbfile = new File(dbdir, SQLITE_DB_NAME);
		File externalDir = Engine.getExternalSettingsDir();
		if ( externalDir!=null ) {
			dbfile = Engine.checkOrMoveFile(externalDir, dbdir, SQLITE_DB_NAME);
		}
		//mDB = new CRDB(dbfile);

       	mScanner = new Scanner(this, mEngine);
       	mScanner.initRoots(mEngine.getMountedRootsMap());
		
       	mHistory = new History(this, mScanner);

//		if ( DeviceInfo.FORCE_LIGHT_THEME ) {
//			setTheme(android.R.style.Theme_Light);
//			getWindow().setBackgroundDrawableResource(drawable.editbox_background);
//		}
//		if ( DeviceInfo.FORCE_LIGHT_THEME ) {
//			mFrame.setBackgroundColor( Color.WHITE );
//			setTheme(R.style.Dialog_Fullscreen_Day);
//		}
		
		mReaderView = new ReaderView(this, mEngine, mBackgroundThread, props);

		mScanner.setDirScanEnabled(props.getBool(ReaderView.PROP_APP_BOOK_PROPERTY_SCAN_ENABLED, true));
		
		mBrowser = new FileBrowser(this, mEngine, mScanner, mHistory);
		mBrowser.setCoverPagesEnabled(props.getBool(ReaderView.PROP_APP_SHOW_COVERPAGES, true));
		mBrowser.setCoverPageFontFace(props.getProperty(ReaderView.PROP_FONT_FACE, DeviceInfo.DEF_FONT_FACE));
		mBrowser.setCoverPageSizeOption(props.getInt(ReaderView.PROP_APP_COVERPAGE_SIZE, 1));

		
		mFrame.addView(mReaderView);
		mFrame.addView(mBrowser);
//		mFrame.addView(startupView);
		setContentView( mFrame );
        log.i("initializing browser");
        mBrowser.init();
		showView(mBrowser, false);
        log.i("initializing reader");
        mBrowser.setSortOrder( props.getProperty(ReaderView.PROP_APP_BOOK_SORT_ORDER));
		mBrowser.setSimpleViewMode(props.getBool(ReaderView.PROP_APP_FILE_BROWSER_SIMPLE_MODE, false));
        mBrowser.showDirectory(mScanner.getRoot(), null);
        
        fileToLoadOnStart = null;
		Intent intent = getIntent();
		if ( intent!=null && Intent.ACTION_VIEW.equals(intent.getAction()) ) {
			Uri uri = intent.getData();
			if ( uri!=null ) {
				fileToLoadOnStart = extractFileName(uri);
			}
			intent.setData(null);
		}
		if ( initialBatteryState>=0 )
			mReaderView.setBatteryState(initialBatteryState);
        
		//==========================================
		// Donations related code
		try {
	        mHandler = new Handler();
	        mPurchaseObserver = new CRPurchaseObserver(mHandler);
	        mBillingService = new BillingService();
	        mBillingService.setContext(this);
	
	        //mPurchaseDatabase = new PurchaseDatabase(this);
	
	        // Check if billing is supported.
	        ResponseHandler.register(mPurchaseObserver);
	        billingSupported = mBillingService.checkBillingSupported();
		} catch (VerifyError e) {
			log.e("Exception while trying to initialize billing service for donations");
		}
        if (!billingSupported) {
        	log.i("Billing is not supported");
        } else {
        	log.i("Billing is supported");
        }
		
        log.i("CoolReader.onCreate() exiting");
    }

    private SyncServiceAccessor mSyncService;
    public SyncServiceAccessor getSyncService() {
    	return mSyncService;
    }
    private CRDBServiceAccessor mCRDBService;
    
    public ClipboardManager getClipboardmanager() {
    	return (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    }
    
    private boolean keyBacklightOff = true;
    public boolean isKeyBacklightDisabled() {
    	return keyBacklightOff;
    }
    
    public void setKeyBacklightDisabled(boolean disabled) {
    	keyBacklightOff = disabled;
    	onUserActivity();
    }
    
    public void setScreenBacklightLevel( int percent )
    {
    	if ( percent<-1 )
    		percent = -1;
    	else if ( percent>100 )
    		percent = -1;
    	screenBacklightBrightness = percent;
    	onUserActivity();
    }
    
    private int screenBacklightBrightness = -1; // use default
    //private boolean brightnessHackError = false;
    private boolean brightnessHackError = false;

    private void turnOffKeyBacklight() {
    	if (!isStarted())
    		return;
    	// repeat again in short interval
    	if (!mEngine.setKeyBacklight(0)) {
    		log.w("Cannot control key backlight directly");
    		return;
    	}
    	// repeat again in short interval
    	Runnable task = new Runnable() {
			@Override
			public void run() {
		    	if (!isStarted())
		    		return;
		    	if (!mEngine.setKeyBacklight(0))
		    		log.w("Cannot control key backlight directly (delayed)");
			}
		};
		BackgroundThread.instance().postGUI(task, 1);
		BackgroundThread.instance().postGUI(task, 10);
    }
    
    private void updateBacklightBrightness(float b) {
        Window wnd = getWindow();
        if (wnd != null) {
	    	LayoutParams attrs =  wnd.getAttributes();
	    	boolean changed = false;
	    	if (b < 0) {
	    		log.d("dimming screen by " + (int)((1 + b)*100) + "%");
	    		b = -b * attrs.screenBrightness;
	    		if (b < 0.15)
	    			return;
	    	}
	    	float delta = attrs.screenBrightness - b;
	    	if (delta < 0)
	    		delta = -delta;
	    	if (delta > 0.01) {
	    		attrs.screenBrightness = b;
	    		changed = true;
	    	}
	    	if ( changed ) {
	    		log.d("Window attribute changed: " + attrs);
	    		wnd.setAttributes(attrs);
	    	}
        }
    }

    private void updateButtonsBrightness(float buttonBrightness) {
        Window wnd = getWindow();
        if (wnd != null) {
	    	LayoutParams attrs =  wnd.getAttributes();
	    	boolean changed = false;
	    	// hack to set buttonBrightness field
	    	//float buttonBrightness = keyBacklightOff ? 0.0f : -1.0f;
	    	if (!brightnessHackError)
	    	try {
	        	Field bb = attrs.getClass().getField("buttonBrightness");
	        	if ( bb!=null ) {
	        		Float oldValue = (Float)bb.get(attrs);
	        		if ( oldValue==null || oldValue.floatValue()!=0 ) {
	        			bb.set(attrs, buttonBrightness);
		        		changed = true;
	        		}
	        	}
	    	} catch ( Exception e ) {
	    		log.e("WindowManager.LayoutParams.buttonBrightness field is not found, cannot turn buttons backlight off");
	    		brightnessHackError = true;
	    	}
	    	//attrs.buttonBrightness = 0;
	    	if ( changed ) {
	    		log.d("Window attribute changed: " + attrs);
	    		wnd.setAttributes(attrs);
	    	}
	    	if (keyBacklightOff)
	    		turnOffKeyBacklight();
        }
    }

    private final static int MIN_BACKLIGHT_LEVEL_PERCENT = DeviceInfo.MIN_SCREEN_BRIGHTNESS_PERCENT;
    
    public void onUserActivity()
    {
    	if (backlightControl != null)
      	    backlightControl.onUserActivity();
    	// Hack
    	//if ( backlightControl.isHeld() )
    	BackgroundThread.guiExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
		        	float b;
		        	int dimmingAlpha = 255;
		        	// screenBacklightBrightness is 0..100
		        	if (screenBacklightBrightness >= 0) {
	        			float minb = MIN_BACKLIGHT_LEVEL_PERCENT / 100.0f; 
		        		if ( screenBacklightBrightness >= 10 ) {
		        			// real brightness control, no colors dimming
		        			b = (screenBacklightBrightness - 10) / (100.0f - 10.0f); // 0..1
		        			b = minb + b * (1-minb); // minb..1
				        	if (b < minb) // BRIGHTNESS_OVERRIDE_OFF
				        		b = minb;
				        	else if (b > 1.0f)
				        		b = 1.0f; //BRIGHTNESS_OVERRIDE_FULL
		        		} else {
			        		// minimal brightness with colors dimming
			        		b = minb;
			        		dimmingAlpha = 255 - (11-screenBacklightBrightness) * 180 / 10; 
		        		}
		        	} else {
		        		// system
		        		b = -1.0f; //BRIGHTNESS_OVERRIDE_NONE
		        	}
		        	mReaderView.setDimmingAlpha(dimmingAlpha);
			    	log.d("Brightness: " + b + ", dim: " + dimmingAlpha);
			    	updateBacklightBrightness(b);
			    	updateButtonsBrightness(keyBacklightOff ? 0.0f : -1.0f);
				} catch ( Exception e ) {
					// ignore
				}
			}
    	});
    }
    
    boolean mDestroyed = false;
	@Override
	protected void onDestroy() {

		log.i("CoolReader.onDestroy() entered");
		mDestroyed = true;
		if ( !CLOSE_BOOK_ON_STOP )
			mReaderView.close();
		
		//if ( mReaderView!=null )
		//	mReaderView.close();
		
		//if ( mHistory!=null && mDB!=null ) {
			//history.saveToDB();
		//}
		if ( intentReceiver!=null ) {
			unregisterReceiver(intentReceiver);
			intentReceiver = null;
		}

		if ( mReaderView!=null ) {
			mReaderView.destroy();
		}
		
		if ( tts!=null ) {
			tts.shutdown();
			tts = null;
			ttsInitialized = false;
			ttsError = false;
		}
		
		if ( mEngine!=null ) {
			//mEngine.uninit();
		}

		mCRDBService.unbind();
//		if ( mBackgroundThread!=null ) {
//			mBackgroundThread.quit();
//		}
			
		mReaderView = null;
		//mEngine = null;
		mBackgroundThread = null;
		
		//===========================
		// Donations support code
		if (billingSupported) {
			mBillingService.unbind();
			//mPurchaseDatabase.close();
		}
		
		log.i("CoolReader.onDestroy() exiting");
		super.onDestroy();
		mSyncService.unbind();
	}

	private String extractFileName( Uri uri )
	{
		if ( uri!=null ) {
			if ( uri.equals(Uri.parse("file:///")) )
				return null;
			else
				return uri.getPath();
		}
		return null;
	}

	public void showHomeScreen() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		startActivity(intent);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		log.i("onNewIntent : " + intent);
		if ( mDestroyed ) {
			log.e("engine is already destroyed");
			return;
		}
		String fileToOpen = null;
		if ( Intent.ACTION_VIEW.equals(intent.getAction()) ) {
			Uri uri = intent.getData();
			if ( uri!=null ) {
				fileToOpen = extractFileName(uri);
			}
			intent.setData(null);
		}
		log.v("onNewIntent, fileToOpen=" + fileToOpen);
		if ( fileToOpen!=null ) {
			// load document
			final String fn = fileToOpen;
			BackgroundThread.instance().postGUI(new Runnable() {
				@Override
				public void run() {
					mReaderView.loadDocument(fn, new Runnable() {
						public void run() {
							log.v("onNewIntent, loadDocument error handler called");
							showToast("Error occured while loading " + fn);
							mEngine.hideProgress();
						}
					});
				}
			}, 100);
		}
	}

	private boolean mPaused = false; 
	public boolean isPaused() {
		return mPaused;
	}
	
	@Override
	protected void onPause() {
		log.i("CoolReader.onPause() : saving reader state");
		mIsStarted = false;
		mPaused = true;
//		setScreenUpdateMode(-1, mReaderView);
		releaseBacklightControl();
		mReaderView.onAppPause();
		super.onPause();
	}
	
	public void releaseBacklightControl()
	{
		backlightControl.release();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		log.i("CoolReader.onPostCreate()");
		super.onPostCreate(savedInstanceState);
	}

	@Override
	protected void onPostResume() {
		log.i("CoolReader.onPostResume()");
		super.onPostResume();
	}

//	private boolean restarted = false;
	@Override
	protected void onRestart() {
		log.i("CoolReader.onRestart()");
		//restarted = true;
		super.onRestart();
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		log.i("CoolReader.onRestoreInstanceState()");
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onResume() {
		log.i("CoolReader.onResume()");
		mPaused = false;
		mIsStarted = true;
		Properties props = mReaderView.getSettings();
		
		if (DeviceInfo.EINK_SCREEN) {
			setScreenUpdateMode(props.getInt(ReaderView.PROP_APP_SCREEN_UPDATE_MODE, 0), mReaderView);
            if (DeviceInfo.EINK_SONY) {
                SharedPreferences pref = getSharedPreferences(PREF_FILE, 0);
                String res = pref.getString(PREF_LAST_BOOK, null);
                if( res != null && res.length() > 0 ) {
                    SonyBookSelector selector = new SonyBookSelector(this);
                    long l = selector.getContentId(res);
                    if(l != 0) {
                       selector.setReadingTime(l);
                       selector.requestBookSelection(l);
                    }
                }
            }
		}
		
		backlightControl.onUserActivity();
		super.onResume();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		log.i("CoolReader.onSaveInstanceState()");
		super.onSaveInstanceState(outState);
	}

	static final boolean LOAD_LAST_DOCUMENT_ON_START = true; 
	
	private boolean mIsStarted = false;
	
	public boolean isStarted() { return mIsStarted; }
	
	@Override
	protected void onStart() {
		log.i("CoolReader.onStart() fileToLoadOnStart=" + fileToLoadOnStart);
		super.onStart();
		
		mPaused = false;
		
		backlightControl.onUserActivity();
		
		PhoneStateReceiver.setPhoneActivityHandler(new Runnable() {
			@Override
			public void run() {
				if (mReaderView != null) {
					mReaderView.stopTTS();
					mReaderView.save();
				}
			}
		});

		// Donations support code
		if (billingSupported)
			ResponseHandler.register(mPurchaseObserver);

		mBackgroundThread.postGUI(new Runnable() {
			public void run() {
				// fixing font settings
				Properties settings = mReaderView.getSettings();
				if (fixFontSettings(settings)) {
					log.i("Missing font settings were fixed");
					mBrowser.setCoverPageFontFace(settings.getProperty(ReaderView.PROP_FONT_FACE, DeviceInfo.DEF_FONT_FACE));
					mReaderView.setSettings(settings, null);
				}
			}
		});
		
		if (!isFirstStart)
			return;
		isFirstStart = false;
		
		if ( fileToLoadOnStart==null ) {
			if ( mReaderView!=null && currentView==mReaderView && mReaderView.isBookLoaded() ) {
				log.v("Book is already opened, showing ReaderView");
				showReader();
				return;
			}
			
			//!stopped && 
//			if ( restarted && mReaderView!=null && mReaderView.isBookLoaded() ) {
//				log.v("Book is already opened, showing ReaderView");
//		        restarted = false;
//		        return;
//			}
		}
		if ( !stopped ) {
			mEngine.showProgress( 500, R.string.progress_starting_cool_reader );
		}
        //log.i("waiting for engine tasks completion");
        //engine.waitTasksCompletion();
//		restarted = false;
		stopped = false;
		
		final String fileName = fileToLoadOnStart;
		mBackgroundThread.postGUI(new Runnable() {
			public void run() {
				log.i("onStart, scheduled runnable: submitting task");
		        mEngine.execute(new LoadLastDocumentTask(fileName));
			}
		});
		log.i("CoolReader.onStart() exiting");
	}
	
	class LoadLastDocumentTask implements Engine.EngineTask {

		final String fileName;
		public LoadLastDocumentTask( String fileName ) {
			super();
			this.fileName = fileName;
		}
		
		public void done() {
	        log.i("onStart, scheduled task: trying to load " + fileToLoadOnStart);
			if ( fileName!=null || LOAD_LAST_DOCUMENT_ON_START ) {
				//currentView=mReaderView;
				if ( fileName!=null ) {
					log.v("onStart() : loading " + fileName);
					mReaderView.loadDocument(fileName, new Runnable() {
						public void run() {
							// cannot open recent book: load another one
							log.e("Cannot open document " + fileToLoadOnStart + " starting file browser");
							showBrowser(null);
						}
					});
				} else {
					log.v("onStart() : loading last document");
					mReaderView.loadLastDocument(new Runnable() {
						public void run() {
							// cannot open recent book: load another one
							log.e("Cannot open last document, starting file browser");
							showBrowser(null);
						}
					});
				}
			} else {
				showBrowser(null);
			}
			fileToLoadOnStart = null;
		}

		public void fail(Exception e) {
	        log.e("onStart, scheduled task failed", e);
		}

		public void work() throws Exception {
	        log.v("onStart, scheduled task work()");
		}
    }
 

	public final static boolean CLOSE_BOOK_ON_STOP = false;
	private boolean stopped = false;
	@Override
	protected void onStop() {
		log.i("CoolReader.onStop() entering");
		stopped = true;
		mPaused = false;
		// will close book at onDestroy()
		if ( CLOSE_BOOK_ON_STOP )
			mReaderView.close();

		// Donations support code
		if (billingSupported)
			ResponseHandler.unregister(mPurchaseObserver);
		
		super.onStop();
		log.i("CoolReader.onStop() exiting");
	}

	private View currentView;
	public void showView( View view )
	{
		showView( view, true );
	}
	public void showView( View view, boolean hideProgress )
	{
		if ( mBackgroundThread==null )
			return;
		if ( hideProgress )
		mBackgroundThread.postGUI(new Runnable() {
			public void run() {
				mEngine.hideProgress();
			}
		});
		if ( currentView==view ) {
			log.v("showView : view " + view.getClass().getSimpleName() + " is already shown");
			return;
		}
		log.v("showView : showing view " + view.getClass().getSimpleName());
		mFrame.bringChildToFront(view);
		for ( int i=0; i<mFrame.getChildCount(); i++ ) {
			View v = mFrame.getChildAt(i);
			v.setVisibility(view==v?View.VISIBLE:View.INVISIBLE);
		}
		currentView = view;
	}
	
	public void showReader()
	{
		log.v("showReader() is called");
		showView(mReaderView);
	}
	
	public boolean isBookOpened()
	{
		return mReaderView.isBookLoaded();
	}
	
	public void loadDocument( FileInfo item )
	{
		//showView(readerView);
		//setContentView(readerView);
		mReaderView.loadDocument(item, null);
	}
	
	public void showBrowser( final FileInfo fileToShow )
	{
		log.v("showBrowser() is called");
		if ( currentView == mReaderView )
			mReaderView.save();
		mEngine.runInGUI( new Runnable() {
			public void run() {
				showView(mBrowser);
		        if (fileToShow == null || mBrowser.isBookShownInRecentList(fileToShow))
		        	mBrowser.showLastDirectory();
		        else
		        	mBrowser.showDirectory(fileToShow, fileToShow);
			}
		});
	}

	public void showBrowserRecentBooks()
	{
		log.v("showBrowserRecentBooks() is called");
		if ( currentView == mReaderView )
			mReaderView.save();
		mEngine.runInGUI( new Runnable() {
			public void run() {
				showView(mBrowser);
	        	mBrowser.showRecentBooks();
			}
		});
	}

	public void showBrowserRoot()
	{
		log.v("showBrowserRoot() is called");
		if ( currentView == mReaderView )
			mReaderView.save();
		mEngine.runInGUI( new Runnable() {
			public void run() {
				showView(mBrowser);
	        	mBrowser.showRootDirectory();
			}
		});
	}

	private void fillMenu(Menu menu) {
		menu.clear();
	    MenuInflater inflater = getMenuInflater();
	    if ( currentView==mReaderView ) {
	    	inflater.inflate(R.menu.cr3_reader_menu, menu);
	    	MenuItem item = menu.findItem(R.id.cr3_mi_toggle_document_styles);
	    	if ( item!=null )
	    		item.setTitle(mReaderView.getDocumentStylesEnabled() ? R.string.mi_book_styles_disable : R.string.mi_book_styles_enable);
	    	item = menu.findItem(R.id.cr3_mi_toggle_day_night);
	    	if ( item!=null )
	    		item.setTitle(mReaderView.isNightMode() ? R.string.mi_night_mode_disable : R.string.mi_night_mode_enable);
	    	item = menu.findItem(R.id.cr3_mi_toggle_text_autoformat);
	    	if ( item!=null ) {
	    		if (mReaderView.isTextFormat())
	    			item.setTitle(mReaderView.isTextAutoformatEnabled() ? R.string.mi_text_autoformat_disable : R.string.mi_text_autoformat_enable);
	    		else
	    			menu.removeItem(item.getItemId());
	    	}
	    } else {
	    	FileInfo currDir = mBrowser.getCurrentDir();
	    	inflater.inflate(currDir!=null && currDir.isOPDSRoot() ? R.menu.cr3_browser_menu : R.menu.cr3_browser_menu, menu);
	    	if ( !isBookOpened() ) {
	    		MenuItem item = menu.findItem(R.id.book_back_to_reading);
	    		if ( item!=null )
	    			item.setEnabled(false);
	    	}
    		MenuItem item = menu.findItem(R.id.book_toggle_simple_mode);
    		if ( item!=null )
    			item.setTitle(mBrowser.isSimpleViewMode() ? R.string.mi_book_browser_normal_mode : R.string.mi_book_browser_simple_mode );
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		fillMenu(menu);
	    return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		fillMenu(menu);
	    return true;
	}

	public void showToast(int stringResourceId) {
		showToast(stringResourceId, Toast.LENGTH_LONG);
	}

	public void showToast(int stringResourceId, int duration) {
		String s = getString(stringResourceId);
		if (s != null)
			showToast(s, duration);
	}

	public void showToast(String msg) {
		showToast(msg, Toast.LENGTH_LONG);
	}

	public void showToast(String msg, int duration) {
		log.v("showing toast: " + msg);
		if (DeviceInfo.USE_CUSTOM_TOAST) {
			ToastView.showToast(mReaderView, msg, Toast.LENGTH_LONG);
		} else {
			// classic Toast
			Toast toast = Toast.makeText(this, msg, duration);
			toast.show();
		}
	}

	private int orientationFromSensor = 0;
	public int getOrientationFromSensor()
	{
		return orientationFromSensor;
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// pass
		orientationFromSensor = newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE ? 1 : 0;
		//final int orientation = newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
//		if ( orientation!=screenOrientation ) {
//			log.d("Screen orientation has been changed: ask for change");
//			AlertDialog.Builder dlg = new AlertDialog.Builder(this);
//			dlg.setTitle(R.string.win_title_screen_orientation_change_apply);//R.string.win_title_options_apply);
//			dlg.setPositiveButton(R.string.dlg_button_ok, new OnClickListener() {
//				public void onClick(DialogInterface arg0, int arg1) {
//					//onPositiveButtonClick();
//					Properties oldSettings = mReaderView.getSettings();
//					Properties newSettings = new Properties(oldSettings);
//					newSettings.setInt(ReaderView.PROP_APP_SCREEN_ORIENTATION, orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ? 1 : 0);
//					mReaderView.setSettings(newSettings, oldSettings);
//				}
//			});
//			dlg.setNegativeButton(R.string.dlg_button_cancel, new OnClickListener() {
//				public void onClick(DialogInterface arg0, int arg1) {
//					//onNegativeButtonClick();
//				}
//			});
//		}
		super.onConfigurationChanged(newConfig);
	}

	String[] mFontFaces;

	public void showOptionsDialog(final OptionsDialog.Mode mode)
	{
		final CoolReader _this = this;
		mBackgroundThread.executeBackground(new Runnable() {
			public void run() {
				mFontFaces = mEngine.getFontFaceList();
				mBackgroundThread.executeGUI(new Runnable() {
					public void run() {
						OptionsDialog dlg = new OptionsDialog(_this, mReaderView, mFontFaces, mode);
						dlg.show();
					}
				});
			}
		});
	}
	
	public void saveSetting( String name, String value ) {
		mReaderView.saveSetting(name, value);
	}
	public String getSetting( String name ) {
		return mReaderView.getSetting(name);
	}
	
	public void showBookmarksDialog()
	{
		BackgroundThread.instance().executeGUI(new Runnable() {
			@Override
			public void run() {
				BookmarksDlg dlg = new BookmarksDlg(CoolReader.this, mReaderView);
				dlg.show();
			}
		});
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if ( mReaderView.onMenuItem(itemId))
			return true; // processed by ReaderView
		// other commands
		switch ( itemId ) {
		case R.id.book_toggle_simple_mode:
			mBrowser.setSimpleViewMode(!mBrowser.isSimpleViewMode());
			mReaderView.saveSetting(ReaderView.PROP_APP_FILE_BROWSER_SIMPLE_MODE, mBrowser.isSimpleViewMode()?"1":"0");
			return true;
		case R.id.mi_browser_options:
			showOptionsDialog(OptionsDialog.Mode.BROWSER);
			return true;
//		case R.id.book_sort_order:
//			mBrowser.showSortOrderMenu();
//			return true;
		case R.id.book_root:
			mBrowser.showRootDirectory();
			return true;
		case R.id.book_opds_root:
			mBrowser.showOPDSRootDirectory();
			return true;
		case R.id.catalog_add:
			mBrowser.editOPDSCatalog(null);
			return true;
		case R.id.book_recent_books:
			mBrowser.showRecentBooks();
			return true;
		case R.id.book_find:
			mBrowser.showFindBookDialog();
			return true;
		case R.id.cr3_mi_user_manual:
			showReader();
			mReaderView.showManual();
			return true;
		case R.id.book_scan_recursive:
			mBrowser.scanCurrentDirectoryRecursive();
			return true;
		case R.id.book_back_to_reading:
			if ( isBookOpened() )
				showReader();
			else
				showToast("No book opened");
			return true;
		default:
			return false;
			//return super.onOptionsItemSelected(item);
		}
	}
	

	private static class DefKeyAction {
		public int keyCode;
		public int type;
		public ReaderAction action;
		public DefKeyAction(int keyCode, int type, ReaderAction action) {
			this.keyCode = keyCode;
			this.type = type;
			this.action = action;
		}
		public String getProp() {
			return ReaderView.PROP_APP_KEY_ACTIONS_PRESS + ReaderAction.getTypeString(type) + keyCode;			
		}
	}
	private static class DefTapAction {
		public int zone;
		public boolean longPress;
		public ReaderAction action;
		public DefTapAction(int zone, boolean longPress, ReaderAction action) {
			this.zone = zone;
			this.longPress = longPress;
			this.action = action;
		}
	}
	private static DefKeyAction[] DEF_KEY_ACTIONS = {
		new DefKeyAction(KeyEvent.KEYCODE_BACK, ReaderAction.NORMAL, ReaderAction.GO_BACK),
		new DefKeyAction(KeyEvent.KEYCODE_BACK, ReaderAction.LONG, ReaderAction.EXIT),
		new DefKeyAction(KeyEvent.KEYCODE_BACK, ReaderAction.DOUBLE, ReaderAction.EXIT),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, ReaderAction.NORMAL, ReaderAction.RECENT_BOOKS),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, ReaderAction.LONG, ReaderAction.BOOKMARKS),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_UP, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_UP, ReaderAction.LONG, (DeviceInfo.EINK_SONY? ReaderAction.PAGE_UP_10 : ReaderAction.REPEAT)),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, ReaderAction.LONG, (DeviceInfo.EINK_SONY? ReaderAction.PAGE_DOWN_10 : ReaderAction.REPEAT)),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, ReaderAction.NORMAL, (DeviceInfo.NAVIGATE_LEFTRIGHT ? ReaderAction.PAGE_UP : ReaderAction.PAGE_UP_10)),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, ReaderAction.NORMAL, (DeviceInfo.NAVIGATE_LEFTRIGHT ? ReaderAction.PAGE_DOWN : ReaderAction.PAGE_DOWN_10)),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, ReaderAction.LONG, ReaderAction.REPEAT),
		new DefKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, ReaderAction.LONG, ReaderAction.REPEAT),
		new DefKeyAction(KeyEvent.KEYCODE_VOLUME_UP, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(KeyEvent.KEYCODE_VOLUME_UP, ReaderAction.LONG, ReaderAction.REPEAT),
		new DefKeyAction(KeyEvent.KEYCODE_VOLUME_DOWN, ReaderAction.LONG, ReaderAction.REPEAT),
		new DefKeyAction(KeyEvent.KEYCODE_MENU, ReaderAction.NORMAL, ReaderAction.READER_MENU),
		new DefKeyAction(KeyEvent.KEYCODE_MENU, ReaderAction.LONG, ReaderAction.OPTIONS),
		new DefKeyAction(KeyEvent.KEYCODE_CAMERA, ReaderAction.NORMAL, ReaderAction.NONE),
		new DefKeyAction(KeyEvent.KEYCODE_CAMERA, ReaderAction.LONG, ReaderAction.NONE),
		new DefKeyAction(KeyEvent.KEYCODE_SEARCH, ReaderAction.NORMAL, ReaderAction.SEARCH),
		new DefKeyAction(KeyEvent.KEYCODE_SEARCH, ReaderAction.LONG, ReaderAction.TOGGLE_SELECTION_MODE),
		
		new DefKeyAction(ReaderView.NOOK_KEY_NEXT_RIGHT, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.NOOK_KEY_SHIFT_DOWN, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.NOOK_KEY_PREV_LEFT, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(ReaderView.NOOK_KEY_PREV_RIGHT, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(ReaderView.NOOK_KEY_SHIFT_UP, ReaderAction.NORMAL, ReaderAction.PAGE_UP),

		new DefKeyAction(ReaderView.NOOK_12_KEY_NEXT_LEFT, ReaderAction.NORMAL, (DeviceInfo.EINK_NOOK ? ReaderAction.PAGE_UP : ReaderAction.PAGE_DOWN)),
		new DefKeyAction(ReaderView.NOOK_12_KEY_NEXT_LEFT, ReaderAction.LONG, (DeviceInfo.EINK_NOOK ? ReaderAction.PAGE_UP_10 : ReaderAction.PAGE_DOWN_10)),
		
		new DefKeyAction(ReaderView.KEYCODE_PAGE_BOTTOMLEFT, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
//		new DefKeyAction(ReaderView.KEYCODE_PAGE_BOTTOMRIGHT, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(ReaderView.KEYCODE_PAGE_TOPLEFT, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.KEYCODE_PAGE_TOPRIGHT, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.KEYCODE_PAGE_BOTTOMLEFT, ReaderAction.LONG, ReaderAction.PAGE_UP_10),
//		new DefKeyAction(ReaderView.KEYCODE_PAGE_BOTTOMRIGHT, ReaderAction.LONG, ReaderAction.PAGE_UP_10),
		new DefKeyAction(ReaderView.KEYCODE_PAGE_TOPLEFT, ReaderAction.LONG, ReaderAction.PAGE_DOWN_10),
		new DefKeyAction(ReaderView.KEYCODE_PAGE_TOPRIGHT, ReaderAction.LONG, ReaderAction.PAGE_DOWN_10),
		
		new DefKeyAction(ReaderView.SONY_DPAD_DOWN_SCANCODE, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.SONY_DPAD_UP_SCANCODE, ReaderAction.NORMAL, ReaderAction.PAGE_UP),
		new DefKeyAction(ReaderView.SONY_DPAD_DOWN_SCANCODE, ReaderAction.LONG, ReaderAction.PAGE_DOWN_10),
		new DefKeyAction(ReaderView.SONY_DPAD_UP_SCANCODE, ReaderAction.LONG, ReaderAction.PAGE_UP_10),

		
		new DefKeyAction(ReaderView.KEYCODE_ESCAPE, ReaderAction.NORMAL, ReaderAction.PAGE_DOWN),
		new DefKeyAction(ReaderView.KEYCODE_ESCAPE, ReaderAction.LONG, ReaderAction.REPEAT),
		
//	    public static final int KEYCODE_PAGE_BOTTOMLEFT = 0x5d; // fwd
//	    public static final int KEYCODE_PAGE_BOTTOMRIGHT = 0x5f; // fwd
//	    public static final int KEYCODE_PAGE_TOPLEFT = 0x5c; // back
//	    public static final int KEYCODE_PAGE_TOPRIGHT = 0x5e; // back
		
	};
	private static DefTapAction[] DEF_TAP_ACTIONS = {
		new DefTapAction(1, false, ReaderAction.PAGE_UP),
		new DefTapAction(2, false, ReaderAction.PAGE_UP),
		new DefTapAction(4, false, ReaderAction.PAGE_UP),
		new DefTapAction(1, true, ReaderAction.GO_BACK), // back by link
		new DefTapAction(2, true, ReaderAction.TOGGLE_DAY_NIGHT),
		new DefTapAction(4, true, ReaderAction.PAGE_UP_10),
		new DefTapAction(3, false, ReaderAction.PAGE_DOWN),
		new DefTapAction(6, false, ReaderAction.PAGE_DOWN),
		new DefTapAction(7, false, ReaderAction.PAGE_DOWN),
		new DefTapAction(8, false, ReaderAction.PAGE_DOWN),
		new DefTapAction(9, false, ReaderAction.PAGE_DOWN),
		new DefTapAction(3, true, ReaderAction.TOGGLE_AUTOSCROLL),
		new DefTapAction(6, true, ReaderAction.PAGE_DOWN_10),
		new DefTapAction(7, true, ReaderAction.PAGE_DOWN_10),
		new DefTapAction(8, true, ReaderAction.PAGE_DOWN_10),
		new DefTapAction(9, true, ReaderAction.PAGE_DOWN_10),
		new DefTapAction(5, false, ReaderAction.READER_MENU),
		new DefTapAction(5, true, ReaderAction.OPTIONS),
	};
	
	
	private boolean isValidFontFace(String face) {
		String[] fontFaces = mEngine.getFontFaceList();
		if (fontFaces == null)
			return true;
		for (String item : fontFaces) {
			if (item.equals(face))
				return true;
		}
		return false;
	}

	private boolean applyDefaultFont(Properties props, String propName, String defFontFace) {
		String currentValue = props.getProperty(propName);
		boolean changed = false;
		if (currentValue == null) {
			currentValue = defFontFace;
			changed = true;
		}
		if (!isValidFontFace(currentValue)) {
			if (isValidFontFace("Droid Sans"))
				currentValue = "Droid Sans";
			else if (isValidFontFace("Roboto"))
				currentValue = "Roboto";
			else if (isValidFontFace("Droid Serif"))
				currentValue = "Droid Serif";
			else if (isValidFontFace("Arial"))
				currentValue = "Arial";
			else if (isValidFontFace("Times New Roman"))
				currentValue = "Times New Roman";
			else if (isValidFontFace("Droid Sans Fallback"))
				currentValue = "Droid Sans Fallback";
			else {
				String[] fontFaces = mEngine.getFontFaceList();
				if (fontFaces != null)
					currentValue = fontFaces[0];
			}
			changed = true;
		}
		if (changed)
			props.setProperty(propName, currentValue);
		return changed;
	}

	public boolean fixFontSettings(Properties props) {
		boolean res = false;
        res = applyDefaultFont(props, ReaderView.PROP_FONT_FACE, DeviceInfo.DEF_FONT_FACE) || res;
        res = applyDefaultFont(props, ReaderView.PROP_STATUS_FONT_FACE, DeviceInfo.DEF_FONT_FACE) || res;
        res = applyDefaultFont(props, ReaderView.PROP_FALLBACK_FONT_FACE, "Droid Sans Fallback") || res;
        return res;
	}
	
	public Properties loadSettings(File file) {
        Properties props = new Properties();

        if ( file.exists() && !DEBUG_RESET_OPTIONS ) {
        	try {
        		FileInputStream is = new FileInputStream(file);
        		props.load(is);
        		log.v("" + props.size() + " settings items loaded from file " + propsFile.getAbsolutePath() );
        	} catch ( Exception e ) {
        		log.e("error while reading settings");
        	}
        }
        
        // default key actions
        for ( DefKeyAction ka : DEF_KEY_ACTIONS ) {
        		props.applyDefault(ka.getProp(), ka.action.id);
        }
        // default tap zone actions
        for ( DefTapAction ka : DEF_TAP_ACTIONS ) {
        	if ( ka.longPress )
        		props.applyDefault(ReaderView.PROP_APP_TAP_ZONE_ACTIONS_TAP + ".long." + ka.zone, ka.action.id);
        	else
        		props.applyDefault(ReaderView.PROP_APP_TAP_ZONE_ACTIONS_TAP + "." + ka.zone, ka.action.id);
        }
        
        if ( DeviceInfo.EINK_SCREEN ) {
    		props.applyDefault(ReaderView.PROP_PAGE_ANIMATION, ReaderView.PAGE_ANIMATION_NONE);
        } else {
    		props.applyDefault(ReaderView.PROP_PAGE_ANIMATION, ReaderView.PAGE_ANIMATION_SLIDE2);
        }

        props.applyDefault(ReaderView.PROP_APP_LOCALE, Lang.DEFAULT.code);
        
        props.applyDefault(ReaderView.PROP_APP_THEME, DeviceInfo.FORCE_LIGHT_THEME ? "WHITE" : "LIGHT");
        props.applyDefault(ReaderView.PROP_APP_THEME_DAY, DeviceInfo.FORCE_LIGHT_THEME ? "WHITE" : "LIGHT");
        props.applyDefault(ReaderView.PROP_APP_THEME_NIGHT, DeviceInfo.FORCE_LIGHT_THEME ? "BLACK" : "DARK");
        props.applyDefault(ReaderView.PROP_APP_SELECTION_PERSIST, "0");
        props.applyDefault(ReaderView.PROP_APP_SCREEN_BACKLIGHT_LOCK, "3");
        if ("1".equals(props.getProperty(ReaderView.PROP_APP_SCREEN_BACKLIGHT_LOCK)))
            props.setProperty(ReaderView.PROP_APP_SCREEN_BACKLIGHT_LOCK, "3");
        props.applyDefault(ReaderView.PROP_APP_BOOK_PROPERTY_SCAN_ENABLED, "1");
        props.applyDefault(ReaderView.PROP_APP_KEY_BACKLIGHT_OFF, DeviceInfo.SAMSUNG_BUTTONS_HIGHLIGHT_PATCH ? "0" : "1");
        // autodetect best initial font size based on display resolution
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(m);
        int screenWidth = m.widthPixels;//getWindowManager().getDefaultDisplay().getWidth();
        int fontSize = 20;
        String hmargin = "4";
        String vmargin = "2";
        if ( screenWidth<=320 ) {
        	fontSize = 20;
            hmargin = "4";
            vmargin = "2";
        } else if ( screenWidth<=400 ) {
        	fontSize = 24;
            hmargin = "10";
            vmargin = "4";
        } else if ( screenWidth<=600 ) {
        	fontSize = 28;
            hmargin = "20";
            vmargin = "8";
        } else {
        	fontSize = 32;
            hmargin = "25";
            vmargin = "15";
        }

        fixFontSettings(props);
        props.applyDefault(ReaderView.PROP_FONT_SIZE, String.valueOf(fontSize));
        props.applyDefault(ReaderView.PROP_FONT_HINTING, "2");
        props.applyDefault(ReaderView.PROP_STATUS_FONT_SIZE, DeviceInfo.EINK_NOOK ? "15" : "16");
        props.applyDefault(ReaderView.PROP_FONT_COLOR, "#000000");
        props.applyDefault(ReaderView.PROP_FONT_COLOR_DAY, "#000000");
        props.applyDefault(ReaderView.PROP_FONT_COLOR_NIGHT, "#808080");
        props.applyDefault(ReaderView.PROP_BACKGROUND_COLOR, "#FFFFFF");
        props.applyDefault(ReaderView.PROP_BACKGROUND_COLOR_DAY, "#FFFFFF");
        props.applyDefault(ReaderView.PROP_BACKGROUND_COLOR_NIGHT, "#101010");
        props.applyDefault(ReaderView.PROP_STATUS_FONT_COLOR, "#FF000000"); // don't use separate color
        props.applyDefault(ReaderView.PROP_STATUS_FONT_COLOR_DAY, "#FF000000"); // don't use separate color
        props.applyDefault(ReaderView.PROP_STATUS_FONT_COLOR_NIGHT, "#80000000"); // don't use separate color
        props.setProperty(ReaderView.PROP_ROTATE_ANGLE, "0"); // crengine's rotation will not be user anymore
        props.setProperty(ReaderView.PROP_DISPLAY_INVERSE, "0");
        props.applyDefault(ReaderView.PROP_APP_FULLSCREEN, "0");
        props.applyDefault(ReaderView.PROP_APP_VIEW_AUTOSCROLL_SPEED, "1500");
        props.applyDefault(ReaderView.PROP_APP_SCREEN_BACKLIGHT, "-1");
		props.applyDefault(ReaderView.PROP_SHOW_BATTERY, "1"); 
		props.applyDefault(ReaderView.PROP_SHOW_POS_PERCENT, "0"); 
		props.applyDefault(ReaderView.PROP_SHOW_PAGE_COUNT, "1"); 
		props.applyDefault(ReaderView.PROP_SHOW_TIME, "1");
		props.applyDefault(ReaderView.PROP_FONT_ANTIALIASING, "2");
		props.applyDefault(ReaderView.PROP_APP_GESTURE_PAGE_FLIPPING, "1");
		props.applyDefault(ReaderView.PROP_APP_SHOW_COVERPAGES, "1");
		props.applyDefault(ReaderView.PROP_APP_COVERPAGE_SIZE, "1");
		props.applyDefault(ReaderView.PROP_APP_SCREEN_ORIENTATION, DeviceInfo.EINK_SCREEN ? "0" : "4"); // "0"
		props.applyDefault(ReaderView.PROP_CONTROLS_ENABLE_VOLUME_KEYS, "1");
		props.applyDefault(ReaderView.PROP_APP_TAP_ZONE_HILIGHT, "0");
		props.applyDefault(ReaderView.PROP_APP_BOOK_SORT_ORDER, FileInfo.DEF_SORT_ORDER.name());
		props.applyDefault(ReaderView.PROP_APP_DICTIONARY, dicts[0].id);
		props.applyDefault(ReaderView.PROP_APP_FILE_BROWSER_HIDE_EMPTY_FOLDERS, "0");
		props.applyDefault(ReaderView.PROP_APP_SELECTION_ACTION, "0");
		props.applyDefault(ReaderView.PROP_APP_MULTI_SELECTION_ACTION, "0");

		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMOUT_BLOCK_MODE, "1");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMIN_BLOCK_MODE, "1");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMOUT_INLINE_MODE, "1");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMIN_INLINE_MODE, "1");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMOUT_BLOCK_SCALE, "0");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMIN_BLOCK_SCALE, "0");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMOUT_INLINE_SCALE, "0");
		props.applyDefault(ReaderView.PROP_IMG_SCALING_ZOOMIN_INLINE_SCALE, "0");
		
		props.applyDefault(ReaderView.PROP_PAGE_MARGIN_LEFT, hmargin);
		props.applyDefault(ReaderView.PROP_PAGE_MARGIN_RIGHT, hmargin);
		props.applyDefault(ReaderView.PROP_PAGE_MARGIN_TOP, vmargin);
		props.applyDefault(ReaderView.PROP_PAGE_MARGIN_BOTTOM, vmargin);
		
        props.applyDefault(ReaderView.PROP_APP_SCREEN_UPDATE_MODE, "0");
        props.applyDefault(ReaderView.PROP_APP_SCREEN_UPDATE_INTERVAL, "10");
        
        props.applyDefault(ReaderView.PROP_NIGHT_MODE, "0");
        if (DeviceInfo.FORCE_LIGHT_THEME) {
        	props.applyDefault(ReaderView.PROP_PAGE_BACKGROUND_IMAGE, Engine.NO_TEXTURE.id);
        } else {
        	if ( props.getBool(ReaderView.PROP_NIGHT_MODE, false) )
        		props.applyDefault(ReaderView.PROP_PAGE_BACKGROUND_IMAGE, Engine.DEF_NIGHT_BACKGROUND_TEXTURE);
        	else
        		props.applyDefault(ReaderView.PROP_PAGE_BACKGROUND_IMAGE, Engine.DEF_DAY_BACKGROUND_TEXTURE);
        }
        props.applyDefault(ReaderView.PROP_PAGE_BACKGROUND_IMAGE_DAY, Engine.DEF_DAY_BACKGROUND_TEXTURE);
        props.applyDefault(ReaderView.PROP_PAGE_BACKGROUND_IMAGE_NIGHT, Engine.DEF_NIGHT_BACKGROUND_TEXTURE);
        
        props.applyDefault(ReaderView.PROP_FONT_GAMMA, DeviceInfo.EINK_SCREEN ? "1.5" : "1.0");
		
		props.setProperty(ReaderView.PROP_MIN_FILE_SIZE_TO_CACHE, "100000");
		props.setProperty(ReaderView.PROP_FORCED_MIN_FILE_SIZE_TO_CACHE, "32768");
		props.applyDefault(ReaderView.PROP_HYPHENATION_DICT, Engine.HyphDict.RUSSIAN.toString());
		props.applyDefault(ReaderView.PROP_APP_FILE_BROWSER_SIMPLE_MODE, "0");
		
		if (!DeviceInfo.EINK_SCREEN) {
			props.applyDefault(ReaderView.PROP_APP_HIGHLIGHT_BOOKMARKS, "1");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_SELECTION_COLOR, "#AAAAAA");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_COMMENT, "#AAAA55");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_CORRECTION, "#C07070");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_SELECTION_COLOR_DAY, "#AAAAAA");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_COMMENT_DAY, "#AAAA55");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_CORRECTION_DAY, "#C07070");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_SELECTION_COLOR_NIGHT, "#808080");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_COMMENT_NIGHT, "#A09060");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_CORRECTION_NIGHT, "#906060");
		} else {
			props.applyDefault(ReaderView.PROP_APP_HIGHLIGHT_BOOKMARKS, "2");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_SELECTION_COLOR, "#808080");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_COMMENT, "#000000");
			props.applyDefault(ReaderView.PROP_HIGHLIGHT_BOOKMARK_COLOR_CORRECTION, "#000000");
		}
        
        return props;
	}
	
	public File getSettingsFile(int profile) {
		if (profile == 0)
			return propsFile;
		return new File(propsFile.getAbsolutePath() + ".profile" + profile);
	}
	
	File propsFile;
	private static final String SETTINGS_FILE_NAME = "cr3.ini";
	private static boolean DEBUG_RESET_OPTIONS = false;
	private Properties loadSettings()
	{
		File[] dataDirs = mEngine.getDataDirectories(null, false, true);
		File existingFile = null;
		for ( File dir : dataDirs ) {
			File f = new File(dir, SETTINGS_FILE_NAME);
			if ( f.exists() && f.isFile() ) {
				existingFile = f;
				break;
			}
		}
        if ( existingFile!=null )
        	propsFile = existingFile;
        else {
	        File propsDir = getDir("settings", Context.MODE_PRIVATE);
			propsFile = new File( propsDir, SETTINGS_FILE_NAME);
			File dataDir = Engine.getExternalSettingsDir();
			if ( dataDir!=null ) {
				log.d("external settings dir: " + dataDir);
				propsFile = Engine.checkOrMoveFile(dataDir, propsDir, SETTINGS_FILE_NAME);
			} else {
				propsDir.mkdirs();
			}
        }
        
        Properties props = loadSettings(propsFile);

		return props;
	}

	public static class DictInfo {
		public final String id; 
		public final String name;
		public final String packageName;
		public final String className;
		public final String action;
		public final Integer internal;
		public String dataKey = SearchManager.QUERY; 
		public DictInfo ( String id, String name, String packageName, String className, String action, Integer internal ) {
			this.id = id;
			this.name = name;
			this.packageName = packageName;
			this.className = className;
			this.action = action;
			this.internal = internal;
		}
		public DictInfo setDataKey(String key) { this.dataKey = key; return this; }
	}
	private static final DictInfo dicts[] = {
		new DictInfo("Fora", "Fora Dictionary", "com.ngc.fora", "com.ngc.fora.ForaDictionary", Intent.ACTION_SEARCH, 0),
		new DictInfo("ColorDict", "ColorDict", "com.socialnmobile.colordict", "com.socialnmobile.colordict.activity.Main", Intent.ACTION_SEARCH, 0),
		new DictInfo("ColorDictApi", "ColorDict new / GoldenDict", "com.socialnmobile.colordict", "com.socialnmobile.colordict.activity.Main", Intent.ACTION_SEARCH, 1),
		new DictInfo("AardDict", "Aard Dictionary", "aarddict.android", "aarddict.android.Article", Intent.ACTION_SEARCH, 0),
		new DictInfo("AardDictLookup", "Aard Dictionary Lookup", "aarddict.android", "aarddict.android.Lookup", Intent.ACTION_SEARCH, 0),
		new DictInfo("Dictan", "Dictan Dictionary", "", "", Intent.ACTION_VIEW, 2),
		new DictInfo("FreeDictionary.org", "Free Dictionary . org", "org.freedictionary.MainActivity", "org.freedictionary", Intent.ACTION_VIEW, 0),
		new DictInfo("LingoQuizLite", "Lingo Quiz Lite", "mnm.lite.lingoquiz", "mnm.lite.lingoquiz.ExchangeActivity", "lingoquiz.intent.action.ADD_WORD", 0).setDataKey("EXTRA_WORD"),
		new DictInfo("LingoQuiz", "Lingo Quiz", "mnm.lingoquiz", "mnm.lingoquiz.ExchangeActivity", "lingoquiz.intent.action.ADD_WORD", 0).setDataKey("EXTRA_WORD"),
		new DictInfo("LEODictionary", "LEO Dictionary", "org.leo.android.dict", "org.leo.android.dict.LeoDict", "android.intent.action.SEARCH", 0),
	};

	public DictInfo[] getDictList() {
		return dicts;
	}
	
	private DictInfo currentDict = dicts[0];
	
	public void setDict( String id ) {
		for ( DictInfo d : dicts ) {
			if ( d.id.equals(id) ) {
				currentDict = d;
				return;
			}
		}
	}

	private final static int DICTAN_ARTICLE_REQUEST_CODE = 100;
	
	private final static String DICTAN_ARTICLE_WORD = "article.word";
	
	private final static String DICTAN_ERROR_MESSAGE = "error.message";

	private final static int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
	
	private void findInDictionaryInternal(String s) {
		switch (currentDict.internal) {
		case 0:
			Intent intent0 = new Intent(currentDict.action).setComponent(new ComponentName(
				currentDict.packageName, currentDict.className
				)).addFlags(DeviceInfo.getSDKLevel() >= 7 ? FLAG_ACTIVITY_CLEAR_TASK : Intent.FLAG_ACTIVITY_NEW_TASK);
			if (s!=null)
				intent0.putExtra(currentDict.dataKey, s);
			try {
				startActivity( intent0 );
			} catch ( ActivityNotFoundException e ) {
				showToast("Dictionary \"" + currentDict.name + "\" is not installed");
			}
			break;
		case 1:
			final String SEARCH_ACTION  = "colordict.intent.action.SEARCH";
			final String EXTRA_QUERY   = "EXTRA_QUERY";
			final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
			final String EXTRA_HEIGHT  = "EXTRA_HEIGHT";
			final String EXTRA_WIDTH   = "EXTRA_WIDTH";
			final String EXTRA_GRAVITY  = "EXTRA_GRAVITY";
			final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
			final String EXTRA_MARGIN_TOP  = "EXTRA_MARGIN_TOP";
			final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";
			final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";

			Intent intent1 = new Intent(SEARCH_ACTION);
			if (s!=null)
				intent1.putExtra(EXTRA_QUERY, s); //Search Query
			intent1.putExtra(EXTRA_FULLSCREEN, true); //
			try
			{
				startActivity(intent1);
			} catch ( ActivityNotFoundException e ) {
				showToast("Dictionary \"" + currentDict.name + "\" is not installed");
			}
			break;
		case 2:
			// Dictan support
			Intent intent2 = new Intent("android.intent.action.VIEW");
			// Add custom category to run the Dictan external dispatcher
            intent2.addCategory("info.softex.dictan.EXTERNAL_DISPATCHER");
            
   	        // Don't include the dispatcher in activity  
            // because it doesn't have any content view.	      
            intent2.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		  
	        intent2.putExtra(DICTAN_ARTICLE_WORD, s);
			  
	        try {
	        	startActivityForResult(intent2, DICTAN_ARTICLE_REQUEST_CODE);
	        } catch (ActivityNotFoundException e) {
				showToast("Dictionary \"" + currentDict.name + "\" is not installed");
	        }
			break;
		}
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == DICTAN_ARTICLE_REQUEST_CODE) {
	       	switch (resultCode) {
	        	
	        	// The article has been shown, the intent is never expected null
			case RESULT_OK:
				break;
					
			// Error occured
			case RESULT_CANCELED: 
				String errMessage = "Unknown Error.";
				if (intent != null) {
					errMessage = "The Requested Word: " + 
					intent.getStringExtra(DICTAN_ARTICLE_WORD) + 
					". Error: " + intent.getStringExtra(DICTAN_ERROR_MESSAGE);
				}
				showToast(errMessage);
				break;
					
			// Must never occur
			default: 
				showToast("Unknown Result Code: " + resultCode);
				break;
			}
        }
    }
	
	public void showDictionary() {
		findInDictionaryInternal(null);
	}
	
	public void findInDictionary( String s ) {
		if ( s!=null && s.length()!=0 ) {
			s = s.trim();
			for ( ;s.length()>0; ) {
				char ch = s.charAt(s.length()-1);
				if ( ch>=128 )
					break;
				if ( ch>='0' && ch<='9' || ch>='A' && ch<='Z' || ch>='a' && ch<='z' )
					break;
				s = s.substring(0, s.length()-1);
			}
			if ( s.length()>0 ) {
				//
				final String pattern = s;
				BackgroundThread.instance().executeBackground(new Runnable() {
					@Override
					public void run() {
						BackgroundThread.instance().postGUI(new Runnable() {
							@Override
							public void run() {
								findInDictionaryInternal(pattern);
							}
						}, 100);
					}
				});
			}
		}
	}
	
	public Properties loadSettings(int profile) {
		File f = getSettingsFile(profile);
		if (!f.exists() && profile != 0)
			f = getSettingsFile(0);
		Properties res = loadSettings(f);
		if (profile != 0) {
			res = filterProfileSettings(res);
			res.setInt(Settings.PROP_PROFILE_NUMBER, profile);
		}
		return res;
	}
	
	public static Properties filterProfileSettings(Properties settings) {
		Properties res = new Properties();
		res.entrySet();
		for (Object k : settings.keySet()) {
			String key = (String)k;
			String value = settings.getProperty(key);
			boolean found = false;
			for (String pattern : Settings.PROFILE_SETTINGS) {
				if (pattern.endsWith("*")) {
					if (key.startsWith(pattern.substring(0, pattern.length()-1))) {
						found = true;
						break;
					}
				} else if (pattern.equalsIgnoreCase(key)) {
					found = true;
					break;
				} else if (key.startsWith("styles.")) {
					found = true;
					break;
				}
			}
			if (found) {
				res.setProperty(key, value);
			}
		}
		return res;
	}
	
	public void saveSettings(int profile, Properties settings) {
		File f = getSettingsFile(profile);
		if (profile != 0) {
			settings = filterProfileSettings(settings);
			settings.setInt(Settings.PROP_PROFILE_NUMBER, profile);
		}
		saveSettings(f, settings);
	}
	
	public void saveSettings(File f, Properties settings)
	{
		try {
			log.v("saveSettings()");
    		FileOutputStream os = new FileOutputStream(f);
    		settings.store(os, "Cool Reader 3 settings");
			log.i("Settings successfully saved to file " + f.getAbsolutePath());
		} catch ( Exception e ) {
			log.e("exception while saving settings", e);
		}
	}

	public void saveSettings(Properties settings)
	{
		saveSettings(propsFile, settings);
	}

	private static Debug.MemoryInfo info = new Debug.MemoryInfo();
	private static Field[] infoFields = Debug.MemoryInfo.class.getFields();
	private static String dumpFields( Field[] fields, Object obj) {
		StringBuilder buf = new StringBuilder();
		try {
			for ( Field f : fields ) {
				if ( buf.length()>0 )
					buf.append(", ");
				buf.append(f.getName());
				buf.append("=");
				buf.append(f.get(obj));
			}
		} catch ( Exception e ) {
			
		}
		return buf.toString();
	}
	public static void dumpHeapAllocation() {
		Debug.getMemoryInfo(info);
		log.d("nativeHeapAlloc=" + Debug.getNativeHeapAllocatedSize() + ", nativeHeapSize=" + Debug.getNativeHeapSize() + ", info: " + dumpFields(infoFields, info));
	}
	
	public void showAboutDialog() {
		AboutDialog dlg = new AboutDialog(this);
		dlg.show();
	}
	
	public void openURL(String url) {
		try {
			Intent i = new Intent(Intent.ACTION_VIEW);  
			i.setData(Uri.parse(url));  
			startActivity(i);
		} catch (Exception e) {
			log.e("Exception " + e + " while trying to open URL " + url);
			showToast("Cannot open URL " + url);
		}
	}
	
	public void sendBookFragment(BookInfo bookInfo, String text) {
        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
    	emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, bookInfo.getFileInfo().getAuthors() + " " + bookInfo.getFileInfo().getTitle());
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
		startActivity(Intent.createChooser(emailIntent, null));	
	}

	public void askConfirmation(int questionResourceId, final Runnable action) {
		AlertDialog.Builder dlg = new AlertDialog.Builder(this);
		dlg.setTitle(questionResourceId);
		dlg.setPositiveButton(R.string.dlg_button_ok, new OnClickListener() {
			public void onClick(DialogInterface arg0, int arg1) {
				action.run();
			}
		});
		dlg.setNegativeButton(R.string.dlg_button_cancel, new OnClickListener() {
			public void onClick(DialogInterface arg0, int arg1) {
				// do nothing
			}
		});
		dlg.show();
	}

	
	private String currentLanguage;
	
	public String getCurrentLanguage() {
		return currentLanguage;
	}
	
	public void setLanguage(String lang) {
		setLanguage(Lang.byCode(lang));
	}
	
	public void setLanguage(Lang lang) {
		try {
			Resources res = getResources();
		    // Change locale settings in the app.
		    DisplayMetrics dm = res.getDisplayMetrics();
		    android.content.res.Configuration conf = res.getConfiguration();
		    conf.locale = (lang == Lang.DEFAULT) ? defaultLocale : lang.getLocale();
		    currentLanguage = (lang == Lang.DEFAULT) ? Lang.getCode(defaultLocale) : lang.code;
		    res.updateConfiguration(conf, dm);
		} catch (Exception e) {
			log.e("error while setting locale " + lang, e);
		}
	}
	
	// Store system locale here, on class creation
	private static final Locale defaultLocale = Locale.getDefault();
	
	//==============================================================
	// 
	// Donations related code
	// (from Dungeons sample) 
    private static final int DIALOG_CANNOT_CONNECT_ID = 1;
    private static final int DIALOG_BILLING_NOT_SUPPORTED_ID = 2;
    /**
     * Used for storing the log text.
     */
    private static final String LOG_TEXT_KEY = "DUNGEONS_LOG_TEXT";

    /**
     * The SharedPreferences key for recording whether we initialized the
     * database.  If false, then we perform a RestoreTransactions request
     * to get all the purchases for this user.
     */
    private static final String DB_INITIALIZED = "db_initialized";

    /**
     * Each product in the catalog is either MANAGED or UNMANAGED.  MANAGED
     * means that the product can be purchased only once per user (such as a new
     * level in a game). The purchase is remembered by Android Market and
     * can be restored if this application is uninstalled and then
     * re-installed. UNMANAGED is used for products that can be used up and
     * purchased multiple times (such as poker chips). It is up to the
     * application to keep track of UNMANAGED products for the user.
     */
    private enum Managed { MANAGED, UNMANAGED }

    private CRPurchaseObserver mPurchaseObserver;
    private BillingService mBillingService;
    private Handler mHandler;
    private DonationListener mDonationListener = null;
    private boolean billingSupported = false;
    private double mTotalDonations = 0;
    
    public boolean isDonationSupported() {
    	return billingSupported;
    }
    public void setDonationListener(DonationListener listener) {
    	mDonationListener = listener;
    }
    public static interface DonationListener {
    	void onDonationTotalChanged(double total);
    }
    public double getTotalDonations() {
    	return mTotalDonations;
    }
    public boolean makeDonation(double amount) {
		final String itemName = "donation" + (amount >= 1 ? String.valueOf((int)amount) : String.valueOf(amount));
    	log.i("makeDonation is called, itemName=" + itemName);
    	if (!billingSupported)
    		return false;
    	String mPayloadContents = null;
    	String mSku = itemName;
        if (!mBillingService.requestPurchase(mSku, mPayloadContents)) {
        	showToast("Purchase is failed");
        }
    	return true;
    }
    

	private static String DONATIONS_PREF_FILE = "cr3donations";
	private static String DONATIONS_PREF_TOTAL_AMOUNT = "total";
    /**
     * A {@link PurchaseObserver} is used to get callbacks when Android Market sends
     * messages to this application so that we can update the UI.
     */
    private class CRPurchaseObserver extends PurchaseObserver {
    	
    	private String TAG = "cr3Billing";
        public CRPurchaseObserver(Handler handler) {
            super(CoolReader.this, handler);
        }

        @Override
        public void onBillingSupported(boolean supported) {
            if (Consts.DEBUG) {
                Log.i(TAG, "supported: " + supported);
            }
            if (supported) {
            	billingSupported = true;
        		SharedPreferences pref = getSharedPreferences(DONATIONS_PREF_FILE, 0);
        		try {
        			mTotalDonations = pref.getFloat(DONATIONS_PREF_TOTAL_AMOUNT, 0.0f);
        		} catch (Exception e) {
        			log.e("exception while reading total donations from preferences", e);
        		}
            	// TODO:
//                restoreDatabase();
            }
        }

        @Override
        public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
                int quantity, long purchaseTime, String developerPayload) {
            if (Consts.DEBUG) {
                Log.i(TAG, "onPurchaseStateChange() itemId: " + itemId + " " + purchaseState);
            }

            if (developerPayload == null) {
                logProductActivity(itemId, purchaseState.toString());
            } else {
                logProductActivity(itemId, purchaseState + "\n\t" + developerPayload);
            }

            if (purchaseState == PurchaseState.PURCHASED) {
            	double amount = 0;
            	try {
	            	if (itemId.startsWith("donation"))
	            		amount = Double.parseDouble(itemId.substring(8));
            	} catch (NumberFormatException e) {
            		//
            	}

            	mTotalDonations += amount;
        		SharedPreferences pref = getSharedPreferences(DONATIONS_PREF_FILE, 0);
        		pref.edit().putString(DONATIONS_PREF_TOTAL_AMOUNT, String.valueOf(mTotalDonations)).commit();

            	if (mDonationListener != null)
            		mDonationListener.onDonationTotalChanged(mTotalDonations);
                //mOwnedItems.add(itemId);
            }
//            mCatalogAdapter.setOwnedItems(mOwnedItems);
//            mOwnedItemsCursor.requery();
        }

        @Override
        public void onRequestPurchaseResponse(RequestPurchase request,
                ResponseCode responseCode) {
            if (Consts.DEBUG) {
                Log.d(TAG, request.mProductId + ": " + responseCode);
            }
            if (responseCode == ResponseCode.RESULT_OK) {
                if (Consts.DEBUG) {
                    Log.i(TAG, "purchase was successfully sent to server");
                }
                logProductActivity(request.mProductId, "sending purchase request");
            } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
                if (Consts.DEBUG) {
                    Log.i(TAG, "user canceled purchase");
                }
                logProductActivity(request.mProductId, "dismissed purchase dialog");
            } else {
                if (Consts.DEBUG) {
                    Log.i(TAG, "purchase failed");
                }
                logProductActivity(request.mProductId, "request purchase returned " + responseCode);
            }
        }

        @Override
        public void onRestoreTransactionsResponse(RestoreTransactions request,
                ResponseCode responseCode) {
            if (responseCode == ResponseCode.RESULT_OK) {
                if (Consts.DEBUG) {
                    Log.d(TAG, "completed RestoreTransactions request");
                }
                // Update the shared preferences so that we don't perform
                // a RestoreTransactions again.
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean(DB_INITIALIZED, true);
                edit.commit();
            } else {
                if (Consts.DEBUG) {
                    Log.d(TAG, "RestoreTransactions error: " + responseCode);
                }
            }
        }
    }
    private void logProductActivity(String product, String activity) {
    	// TODO: some logging
    	Log.i(LOG_TEXT_KEY, activity);
    }
}
