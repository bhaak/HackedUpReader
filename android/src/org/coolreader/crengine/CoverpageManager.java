package org.coolreader.crengine;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;

import org.coolreader.CoolReader;
import org.coolreader.db.CRDBService;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class CoverpageManager {

	public static final Logger log = L.create("cp");
	
	/**
	 * Callback on coverpage decoding finish.
	 */
	public interface CoverpageReadyListener {
		void onCoverpageReady(FileInfo file);
	}

	/**
	 * Cancel queued tasks for specified files.
	 */
	public void unqueue(Collection<FileInfo> filesToUnqueue) {
		synchronized(LOCK) {
			for (FileInfo file : filesToUnqueue) {
				mCheckFileCacheQueue.remove(file);
				mScanFileQueue.remove(file);
				mCache.unqueue(file);
			}
		}
	}
	
	/**
	 * Set listener for cover page load completion.
	 */
	public void setCoverpageReadyListener(CoverpageReadyListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Constructor.
	 * @param activity is CoolReader main activity.
	 */
	public CoverpageManager (CoolReader activity) {
		this.mActivity = activity;
	}
	
	/**
	 * Returns coverpage drawable for book.
	 * Internally it will load coverpage in background.
	 * @param book is file to get coverpage for.
	 * @return Drawable which can be used to draw coverpage.
	 */
	public Drawable getCoverpageDrawableFor(FileInfo book) {
		return new CoverImage(book);
	}
	
	private CoolReader mActivity;
	
	private int maxWidth = 90;
	private int maxHeight = 120;

	private enum State {
		UNINITIALIZED,
		LOAD_SCHEDULED,
		FILE_CACHE_LOOKUP,
		IMAGE_DRAW_SCHEDULED,
		DRAWING,
		READY,
	}
	
	// hack for heap size limit
	private static final VMRuntimeHack runtime = new VMRuntimeHack();

	private class BitmapCacheItem {
		private final FileInfo file;
		private Bitmap bitmap;
		private State state = State.UNINITIALIZED;
		public BitmapCacheItem(FileInfo file) {
			this.file = file;
		}
		private boolean canUnqueue() {
			switch (state) {
			case FILE_CACHE_LOOKUP:
			case LOAD_SCHEDULED:
			case UNINITIALIZED:
				return true;
			default:
				return false;
			}
		}
		private void setBitmap(Bitmap bmp) {
			if (bitmap != null)
				removed();
			bitmap = bmp;
			if (bitmap != null) {
				int bytes = bitmap.getRowBytes() * bitmap.getHeight();
				runtime.trackFree(bytes); // hack for heap size limit
			}
		}
		private void removed() {
			if (bitmap != null) {
				int bytes = bitmap.getRowBytes() * bitmap.getHeight();
				runtime.trackAlloc(bytes); // hack for heap size limit
				bitmap.recycle();
				bitmap = null;
			}
		}
		@Override
		protected void finalize() throws Throwable {
			// don't forget to free resource
			removed();
			super.finalize();
		}
		
	}

	private class BitmapCache {
		public BitmapCache(int maxSize) {
			this.maxSize = maxSize;
		}
		private ArrayList<BitmapCacheItem> list = new ArrayList<BitmapCacheItem>();
		private int maxSize;
		private int find(FileInfo file) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).file.pathNameEquals(file))
					return i;
			}
			return -1;
		}
		private void moveOnTop(int index) {
			if (index >= list.size() - 1)
				return;
			BitmapCacheItem item = list.get(index);
			list.remove(index);
			list.add(item);
		}
		private void checkMaxSize() {
			int itemsToRemove = list.size() - maxSize;
			for (int i = itemsToRemove - 1; i >= 0; i--) {
				BitmapCacheItem item = list.get(i);
				list.remove(i);
				item.removed();
			}
		}
		public BitmapCacheItem getItem(FileInfo file) {
			int index = find(file);
			if (index < 0)
				return null;
			BitmapCacheItem item = list.get(index);
			moveOnTop(index);
			return item;
		}
		public BitmapCacheItem addItem(FileInfo file) {
			BitmapCacheItem item = new BitmapCacheItem(file);
			list.add(item);
			checkMaxSize();
			return item;
		}
		public void unqueue(FileInfo file) {
			int index = find(file);
			if (index < 0)
				return;
			BitmapCacheItem item = list.get(index);
			if (item.canUnqueue()) {
				list.remove(index);
				item.removed();
			}
		}
		public Bitmap getBitmap(FileInfo file) {
			synchronized (LOCK) {
				BitmapCacheItem item = getItem(file);
				if (item == null || item.bitmap == null || item.bitmap.isRecycled())
					return null;
				return item.bitmap;
			}
		}
	}
	private BitmapCache mCache = new BitmapCache(32);
	
	private FileInfoQueue mCheckFileCacheQueue = new FileInfoQueue(); 
	private FileInfoQueue mScanFileQueue = new FileInfoQueue();
	
	private static class FileInfoQueue {
		ArrayList<FileInfo> list = new ArrayList<FileInfo>();
		public int indexOf(FileInfo file) {
			for (int i = list.size() - 1; i >= 0; i--) {
				if (file.pathNameEquals(list.get(i))) {
					return i;
				}
			}
			return -1;
		}
		public void remove(FileInfo file) {
			int index = indexOf(file);
			if (index >= 0)
				list.remove(index);
		}
		public void moveOnTop(FileInfo file) {
			int index = indexOf(file);
			if (index == 0)
				return;
			moveOnTop(index);
		}
		public void moveOnTop(int index) {
			FileInfo item = list.get(index);
			list.remove(index);
			list.add(0, item);
		}
		public void add(FileInfo file) {
			int index = indexOf(file);
			if (index >= 0)
				return;
			list.add(file);
		}
		public boolean addOnTop(FileInfo file) {
			int index = indexOf(file);
			if (index >= 0) {
				moveOnTop(index);
				return false;
			}
			list.add(0, file);
			return true;
		}
		public FileInfo next() {
			if (list.size() == 0)
				return null;
			FileInfo item = list.get(0);
			list.remove(0);
			return item;
		}
	}
	
	private Object LOCK = new Object();

	private Runnable lastCheckCacheTask = null;
	private Runnable lastScanFileTask = null;
	private BitmapCacheItem setItemState(FileInfo file, State state) {
		synchronized(LOCK) {
			BitmapCacheItem item = mCache.getItem(file);
			if (item == null)
				item = mCache.addItem(file);
			item.state = state;
			return item;
		}
	}
	private void notifyBitmapIsReady(final FileInfo file) {
		BackgroundThread.instance().postGUI(new Runnable() {
			@Override
			public void run() {
				listener.onCoverpageReady(file);
			}
		});
	}

	private void draw(FileInfo file, byte[] data) {
		BitmapCacheItem item = null;
		synchronized(LOCK) {
			item = mCache.getItem(file);
			if (item == null)
				return;
			if (item.state == State.DRAWING || item.state == State.READY)
				return;
			item.state = State.DRAWING;
		}
		Bitmap bmp = decodeCoverPage(data);
		if (bmp != null) {
			// successfully decoded
			log.v("coverpage is decoded for " + file);
			item.setBitmap(bmp);
			item.state = State.READY;
			notifyBitmapIsReady(file);
		} else {
			// draw default coverpage
			bmp = drawDefaultCoverpage(file);
			log.v("default coverpage is created for " + file);
			item.setBitmap(bmp);
			item.state = State.READY;
			notifyBitmapIsReady(file);
		}
	}
	private void coverpageLoaded(final FileInfo file, final byte[] data) {
		log.v("coverpage data is loaded for " + file);
		setItemState(file, State.IMAGE_DRAW_SCHEDULED);
		BackgroundThread.instance().postBackground(new Runnable() {
			@Override
			public void run() {
				draw(file, data);
			}
		});
	}
	private void scheduleCheckCache() {
		// cache lookup
		lastCheckCacheTask = new Runnable() {
			@Override
			public void run() {
				FileInfo file = null;
				synchronized(LOCK) {
					if (lastCheckCacheTask == this) {
						file = mCheckFileCacheQueue.next();
					}
				}
				if (file != null) {
					mActivity.getDB().loadBookCoverpage(file, new CRDBService.CoverpageLoadingCallback() {
						@Override
						public void onCoverpageLoaded(FileInfo fileInfo, byte[] data) {
							coverpageLoaded(fileInfo, data);
						}
					});
					scheduleCheckCache();
				}
			}
		};
		BackgroundThread.instance().postGUI(lastCheckCacheTask);
	}
	private void scheduleScanFile() {
		// file scan
		lastScanFileTask = new Runnable() {
			@Override
			public void run() {
				FileInfo file = null;
				synchronized(LOCK) {
					if (lastScanFileTask == this) {
						file = mScanFileQueue.next();
					}
				}
				if (file != null) {
					final FileInfo fileInfo = file;
					BackgroundThread.instance().postBackground(new Runnable() {
						@Override
						public void run() {
							byte[] data = mActivity.getEngine().scanBookCover(fileInfo.getPathName());
							if (data == null)
								data = new byte[] {};
							if (fileInfo.format.needCoverPageCaching())
								mActivity.getDB().saveBookCoverpage(fileInfo, data);
							coverpageLoaded(fileInfo, data);
						}
					});
					scheduleScanFile();
				}
			}
		};
		BackgroundThread.instance().postGUI(lastScanFileTask);
	}

	private void queueForDrawing(FileInfo file) {
		synchronized (LOCK) {
			BitmapCacheItem item = mCache.getItem(file);
			if (item != null && (item.state == State.READY || item.state == State.DRAWING))
				return;
			if (file.format.needCoverPageCaching()) {
				if (mCheckFileCacheQueue.addOnTop(file)) {
					log.v("Scheduled coverpage DB lookup for " + file);
					scheduleCheckCache();
				}
			} else {
				if (mScanFileQueue.addOnTop(file)) {
					log.v("Scheduled coverpage filescan for " + file);
					scheduleScanFile();
				}
			}
		}
	}

	private class CoverImage extends Drawable {
		
		FileInfo book;
		Paint defPaint;
		
		public CoverImage(FileInfo book) {
			this.book = new FileInfo(book);
			defPaint = new Paint();
			defPaint.setColor(0xFF000000);
			defPaint.setFilterBitmap(true);
		}

		@Override
		public void draw(Canvas canvas) {
			try {
				Rect rc = getBounds();
				int w = rc.width();
				int h = rc.height();
				synchronized (mCache) {
					Bitmap bitmap = mCache.getBitmap(book);
					if (bitmap != null) {
						Rect dst = getBestCoverSize(rc, bitmap.getWidth(), bitmap.getHeight());
						canvas.drawBitmap(bitmap, null, dst, defPaint);
						return;
					}
				}
				queueForDrawing(book);
				//if (h * bestWidth / bestHeight > w)
				//canvas.drawRect(rc, defPaint);
			} catch (Exception e) {
				log.e("exception in draw", e);
			}
		}
		
		@Override
		public int getIntrinsicHeight() {
			return maxHeight;
		}

		@Override
		public int getIntrinsicWidth() {
			return maxWidth;
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSPARENT; // part of pixels are transparent
		}

		@Override
		public void setAlpha(int alpha) {
			// ignore, not supported
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			// ignore, not supported
		}
	}

	private Rect getBestCoverSize(int srcWidth, int srcHeight) {
		if (srcWidth < 20 || srcHeight < 20) {
			return new Rect(0, 0, maxWidth, maxHeight);
		}
		int sw = srcHeight * maxWidth / maxHeight;
		int sh = srcWidth * maxHeight / maxWidth;
		if (sw <= maxWidth)
			sh = maxHeight;
		else
			sw = maxWidth;
		int dx = (maxWidth - sw) / 2;
		int dy = (maxHeight - sh) / 2;
		return new Rect(dx, dy, sw + dx, sh + dy); 
	}
	
	private Rect getBestCoverSize(Rect dst, int srcWidth, int srcHeight) {
		int w = dst.width();
		int h = dst.height();
		if (srcWidth < 20 || srcHeight < 20) {
			return dst;
		}
		int sw = srcHeight * w / h;
		int sh = srcWidth * h / w;
		if (sw <= w)
			sh = h;
		else
			sw = w;
		int dx = (w - sw) / 2;
		int dy = (h - sh) / 2;
		return new Rect(dst.left + dx, dst.top + dy, dst.left + sw + dx, dst.top + sh + dy); 
	}
	
	private static Method bitmapSetDensityMethod;
	private static Method canvasSetDensityMethod;
	private static boolean isNewApiChecked;
	private Bitmap decodeCoverPage( byte[] data )
	{
		if (data == null || data.length == 0)
			return null;
		try {
			ByteArrayInputStream is = new ByteArrayInputStream(data);
			Bitmap srcbmp = BitmapFactory.decodeStream(is);
			
			if ( !isNewApiChecked ) {
				isNewApiChecked = true;
				try {
					bitmapSetDensityMethod = Bitmap.class.getMethod("setDensity", new Class[] {int.class});
					canvasSetDensityMethod = Canvas.class.getMethod("setDensity", new Class[] {int.class});
				} catch ( Exception e ) {
					L.w("No Bitmap.setDensity() method found");
				}
			}
			
			Rect bestSize = getBestCoverSize(srcbmp.getWidth(), srcbmp.getHeight());
			Bitmap bmp = Bitmap.createScaledBitmap(srcbmp, bestSize.width(), bestSize.height(), true); 
				//Bitmap.createBitmap(bestSize.width(), bestSize.height(), Bitmap.Config.ARGB_8888);
//			if (bitmapSetDensityMethod != null)
//				bitmapSetDensityMethod.invoke(bmp, Bitmap.DENSITY_NONE);
//			//bmp.setDensity(Bitmap.DENSITY_NONE); // mCoolReader.getResources().getDisplayMetrics().densityDpi
//			Canvas canvas = new Canvas(bmp);
//			if ( canvasSetDensityMethod!=null )
//				canvasSetDensityMethod.invoke(canvas, Bitmap.DENSITY_NONE);
//			//canvas.setDensity(Bitmap.DENSITY_NONE); // mCoolReader.getResources().getDisplayMetrics().densityDpi
//			canvas.drawBitmap(srcbmp, null,
//					new Rect(0, 0, bestSize.width(), bestSize.height()), null);
			srcbmp.recycle();
    		//Log.d("cr3", "cover page format: " + srcbmp.getWidth() + "x" + srcbmp.getHeight());
    		return bmp;
		} catch ( Exception e ) {
    		Log.e("cr3", "exception while decoding coverpage " + e.getMessage());
    		return null;
		}
	}
	private Bitmap drawDefaultCoverpage(FileInfo file)
	{
		try {
			if ( !isNewApiChecked ) {
				isNewApiChecked = true;
				try {
					bitmapSetDensityMethod = Bitmap.class.getMethod("setDensity", new Class[] {int.class});
					canvasSetDensityMethod = Canvas.class.getMethod("setDensity", new Class[] {int.class});
				} catch ( Exception e ) {
					L.w("No Bitmap.setDensity() method found");
				}
			}
			
			// Create drawing buffer bitmap
			int factor = 3;
			int width = maxWidth * factor;
			int height = maxHeight * factor;
			Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
			if (bitmapSetDensityMethod != null)
				bitmapSetDensityMethod.invoke(bmp, Bitmap.DENSITY_NONE);
			//bmp.setDensity(Bitmap.DENSITY_NONE); // mCoolReader.getResources().getDisplayMetrics().densityDpi
			Canvas canvas = new Canvas(bmp);
			if ( canvasSetDensityMethod!=null )
				canvasSetDensityMethod.invoke(canvas, Bitmap.DENSITY_NONE);
			
			// Draw cover page to buffer
			// draw background

			// frame
			Rect rc = new Rect(0, 0, width, height);
			Paint framepaint = new Paint();
			framepaint.setColor(0xFFA0A0C0);
			canvas.drawRect(rc, framepaint);
			
			// background
			int frameW = 3;
			Rect rc2 = new Rect(frameW, frameW, width - frameW * 2, height - frameW * 2);
			Paint bgpaint = new Paint();
			bgpaint.setColor(0xFFD0D0D0);
			canvas.drawRect(rc2, bgpaint);
			
			Paint textPaint = new Paint();
			textPaint.setColor(0xFF000000);
			textPaint.setTextSize(24);
			textPaint.setTextAlign(Paint.Align.LEFT);
			String title = file.title;
			if (title == null || title.length() == 0)
				title = file.getFileNameToDisplay();
			canvas.drawText(title, 20, 20, textPaint);

			// TODO: draw text 

			// RESIZE
			Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, maxWidth, maxHeight, true);
//			if (bitmapSetDensityMethod != null)
//				bitmapSetDensityMethod.invoke(bmp2, Bitmap.DENSITY_NONE);
//			//bmp.setDensity(Bitmap.DENSITY_NONE); // mCoolReader.getResources().getDisplayMetrics().densityDpi
//			Canvas canvas2 = new Canvas(bmp2);
//			if (canvasSetDensityMethod != null)
//				canvasSetDensityMethod.invoke(canvas2, Bitmap.DENSITY_NONE);
//			//canvas.setDensity(Bitmap.DENSITY_NONE); // mCoolReader.getResources().getDisplayMetrics().densityDpi
//			Paint resizePaint = new Paint();
//			resizePaint.setFilterBitmap(true);
//			canvas2.drawBitmap(bmp, null,
//					new Rect(0, 0, maxWidth, maxHeight), resizePaint);
			bmp.recycle();
    		return bmp2;
		} catch ( Exception e ) {
    		Log.e("cr3", "exception while decoding coverpage " + e.getMessage());
    		return null;
		}
	}

	private CoverpageReadyListener listener = null;
}
