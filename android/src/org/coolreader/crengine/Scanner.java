package org.coolreader.crengine;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.coolreader.CoolReader;
import org.coolreader.R;
import org.coolreader.db.CRDBService;

import android.util.Log;

public class Scanner extends FileInfoChangeSource {
	
	public static final Logger log = L.create("sc");
	
	HashMap<String, FileInfo> mFileList = new HashMap<String, FileInfo>();
//	ArrayList<FileInfo> mFilesForParsing = new ArrayList<FileInfo>();
	FileInfo mRoot;
	
	boolean mHideEmptyDirs = true;
	
	void setHideEmptyDirs( boolean flgHide ) {
		mHideEmptyDirs = flgHide;
	}

	private boolean dirScanEnabled = true;
	public boolean getDirScanEnabled()
	{
		return dirScanEnabled;
	}
	
	public void setDirScanEnabled(boolean dirScanEnabled)
	{
		this.dirScanEnabled = dirScanEnabled;
	}
	
	private FileInfo scanZip( FileInfo zip )
	{
		try {
			File zf = new File(zip.pathname);
			//ZipFile file = new ZipFile(zf);
			ArrayList<ZipEntry> entries = engine.getArchiveItems(zip.pathname);
			ArrayList<FileInfo> items = new ArrayList<FileInfo>();
			//for ( Enumeration<?> e = file.entries(); e.hasMoreElements(); ) {
			for ( ZipEntry entry : entries ) {
				if ( entry.isDirectory() )
					continue;
				String name = entry.getName();
				FileInfo item = new FileInfo();
				item.format = DocumentFormat.byExtension(name);
				if ( item.format==null )
					continue;
				File f = new File(name);
				item.filename = f.getName();
				item.path = f.getPath();
				item.pathname = entry.getName();
				item.size = (int)entry.getSize();
				//item.createTime = entry.getTime();
				item.createTime = zf.lastModified();
				item.arcname = zip.pathname;
				item.arcsize = (int)entry.getSize(); //getCompressedSize();
				item.isArchive = true;
				items.add(item);
			}
			if ( items.size()==0 ) {
				L.i("Supported files not found in " + zip.pathname);
				return null;
			} else if ( items.size()==1 ) {
				// single supported file in archive
				FileInfo item = items.get(0);
				item.isArchive = true;
				item.isDirectory = false;
				return item;
			} else {
				zip.isArchive = true;
				zip.isDirectory = true;
				zip.isListed = true;
				for ( FileInfo item : items ) {
					item.parent = zip;
					zip.addFile(item);
				}
				return zip;
			}
		} catch ( Exception e ) {
			L.e("IOException while opening " + zip.pathname + " " + e.getMessage());
		}
		return null;
	}
	
	/**
	 * Adds dir and file children to directory FileInfo item.
	 * @param baseDir is directory to list files and dirs for
	 * @return true if successful.
	 */
	public boolean listDirectory(FileInfo baseDir)
	{
		Set<String> knownItems = null;
		if ( baseDir.isListed ) {
			knownItems = new HashSet<String>();
			for ( int i=baseDir.itemCount()-1; i>=0; i-- ) {
				FileInfo item = baseDir.getItem(i);
				if ( !item.exists() ) {
					// remove item from list
					baseDir.removeChild(item);
				} else {
					knownItems.add(item.getBasePath());
				}
			}
		}
		try {
			File dir = new File(baseDir.pathname);
			File[] items = dir.listFiles();
			// process normal files
			if ( items!=null ) {
				for ( File f : items ) {
					// check whether file is a link
					if (Engine.isLink(f.getAbsolutePath()) != null) {
						log.w("skipping " + f + " because it's a link");
						continue;
					}
					if (!f.isDirectory()) {
						// regular file
						if ( f.getName().startsWith(".") )
							continue; // treat files beginning with '.' as hidden
						String pathName = f.getAbsolutePath();
						if ( knownItems!=null && knownItems.contains(pathName) )
							continue;
						if (engine.isRootsMountPoint(pathName)) {
							// skip mount root
							continue;
						}
						boolean isZip = pathName.toLowerCase().endsWith(".zip");
						FileInfo item = mFileList.get(pathName);
						boolean isNew = false;
						if ( item==null ) {
							item = new FileInfo( f );
							if ( isZip ) {
								item = scanZip( item );
								if ( item==null )
									continue;
								if ( item.isDirectory ) {
									// many supported files in ZIP
									item.parent = baseDir;
									baseDir.addDir(item);
									for ( int i=0; i<item.fileCount(); i++ ) {
										FileInfo file = item.getFile(i);
										mFileList.put(file.getPathName(), file);
									}
								} else {
									item.parent = baseDir;
									baseDir.addFile(item);
									mFileList.put(pathName, item);
								}
								continue;
							}
							isNew = true;
						}
						if ( item.format!=null ) {
							item.parent = baseDir;
							baseDir.addFile(item);
							if ( isNew )
								mFileList.put(pathName, item);
						}
					}
				}
				// process directories 
				for ( File f : items ) {
					if ( f.isDirectory() ) {
						if ( f.getName().startsWith(".") )
							continue; // treat dirs beginning with '.' as hidden
						FileInfo item = new FileInfo( f );
						if ( knownItems!=null && knownItems.contains(item.getPathName()) )
							continue;
						item.parent = baseDir;
						baseDir.addDir(item);					
					}
				}
			}
			baseDir.isListed = true;
			return !baseDir.isEmpty();
		} catch ( Exception e ) {
			L.e("Exception while listing directory " + baseDir.pathname, e);
			baseDir.isListed = true;
			return false;
		}
	}
	
	public static class ScanControl {
		volatile private boolean stopped = false;
		public boolean isStopped() {
			return stopped;
		}
		public void stop() {
			stopped = true;
		}
	}

	/**
	 * Call this method (in GUI thread) to update views if directory content is changed outside.
	 * @param dir is directory with changed content
	 */
	public void onDirectoryContentChanged(FileInfo dir) {
		onChange(dir);
	}
	
	/**
	 * For all files in directory, retrieve metadata from DB or scan and save into DB.
	 * Call in GUI thread only!
	 * @param baseDir is directory with files to lookup/scan; file items will be updated with info from file metadata or DB
	 * @param readyCallback is Runable to call when operation is finished or stopped (will be called in GUI thread)
	 * @param control allows to stop long operation
	 */
	private void scanDirectoryFiles(final FileInfo baseDir, final ScanControl control, final Engine.ProgressControl progress, final Runnable readyCallback) {
		// GUI thread
		BackgroundThread.ensureGUI();
		ArrayList<String> pathNames = new ArrayList<String>();
		for (int i=0; i < baseDir.fileCount(); i++)
			pathNames.add(baseDir.getFile(i).getPathName());

		if (pathNames.size() == 0) {
			readyCallback.run();
			return;
		}

		for (int i=0; i < baseDir.dirCount(); i++) {
			if (control.isStopped())
				break;
			listDirectory(baseDir.getDir(i));
		}
			
		db().loadFileInfos(pathNames, new CRDBService.FileInfoLoadingCallback() {
			@Override
			public void onFileInfoListLoaded(ArrayList<FileInfo> list) {
				log.v("onFileInfoListLoaded");
				// GUI thread
				final ArrayList<FileInfo> filesForParsing = new ArrayList<FileInfo>();
				ArrayList<FileInfo> filesForSave = new ArrayList<FileInfo>();
				Map<String, FileInfo> map = new HashMap<String, FileInfo>();
				for (FileInfo f : list)
					map.put(f.getPathName(), f);
				for (int i=0; i<baseDir.fileCount(); i++) {
					FileInfo item = baseDir.getFile(i);
					FileInfo fromDB = map.get(item.getPathName());
					if (fromDB != null) {
						// use DB value
						baseDir.setFile(i, fromDB);
					} else {
						// not found in DB
						if (item.format.canParseProperties()) {
							filesForParsing.add(new FileInfo(item));
						} else {
							filesForSave.add(new FileInfo(item));
						}
					}
				}
				if (filesForSave.size() > 0) {
					db().saveFileInfos(filesForSave);
				}
				if (filesForParsing.size() == 0 || control.isStopped()) {
					readyCallback.run();
					return;
				}
				// scan files in Background thread
				BackgroundThread.instance().postBackground(new Runnable() {
					@Override
					public void run() {
						// Background thread
						final ArrayList<FileInfo> filesForSave = new ArrayList<FileInfo>();
						try {
							int count = filesForParsing.size();
							for ( int i=0; i<count; i++ ) {
								if (control.isStopped())
									break;
								progress.setProgress(i * 10000 / count);
								FileInfo item = filesForParsing.get(i);
								engine.scanBookProperties(item);
								filesForSave.add(item);
							}
						} catch (Exception e) {
							L.e("Exception while scanning", e);
						}
						progress.hide();
						// jump to GUI thread
						BackgroundThread.instance().postGUI(new Runnable() {
							@Override
							public void run() {
								// GUI thread
								try {
									if (filesForSave.size() > 0) {
										db().saveFileInfos(filesForSave);
									}
									for (FileInfo file : filesForSave)
										baseDir.setFile(file);
								} catch (Exception e ) {
									L.e("Exception while scanning", e);
								}
								// call finish handler
								readyCallback.run();
							}
						});
					}
					
				});
			}
		});
	}
	
	/**
	 * Scan single directory for dir and file properties in background thread.
	 * @param baseDir is directory to scan
	 * @param readyCallback is called on completion
	 */
	public void scanDirectory(final FileInfo baseDir, final Runnable readyCallback, final boolean recursiveScan, final ScanControl scanControl) {
		// Call in GUI thread only!
		BackgroundThread.ensureGUI();
		listDirectory(baseDir);
		listSubtree( baseDir, 2, android.os.SystemClock.uptimeMillis() + 700 );
		if ( (!getDirScanEnabled() || baseDir.isScanned) && !recursiveScan ) {
			readyCallback.run();
			return;
		}
		Engine.ProgressControl progress = engine.createProgress(recursiveScan ? 0 : R.string.progress_scanning); 
		scanDirectoryFiles(baseDir, scanControl, progress, new Runnable() {
			@Override
			public void run() {
				// GUI thread
				onDirectoryContentChanged(baseDir);
				try {
					if (scanControl.isStopped()) {
						// scan is stopped
						readyCallback.run();
						return;
					} else {
						baseDir.isScanned = true;

						if ( recursiveScan ) {
							if ( scanControl.isStopped() )
								return;
							final ArrayList<FileInfo> dirsToScan = new ArrayList<FileInfo>(); 
							for ( int i=baseDir.dirCount()-1; i>=0; i-- ) {
								File dir = new File(baseDir.getDir(i).getPathName());
								if (!engine.getPathCorrector().isRecursivePath(dir))
									dirsToScan.add(baseDir.getDir(i));
							}
							Runnable dirIterator = new Runnable() {
								@Override
								public void run() {
									// process next directory from list
									if (dirsToScan.size() == 0 || scanControl.isStopped()) {
										readyCallback.run();
										return;
									}
									FileInfo dir = dirsToScan.get(0);
									dirsToScan.remove(0);
									scanDirectory(dir, this, true, scanControl);
								}
							};
							dirIterator.run();
						} else {
							readyCallback.run();
						}
					}
				} catch (Exception e) {
					// treat as finished
					readyCallback.run();
				}
			}
		});
	}
	
	private boolean addRoot( String pathname, int resourceId, boolean listIt) {
		return addRoot( pathname, coolReader.getResources().getString(resourceId), listIt);
	}

	private FileInfo findRoot(String pathname) {
		String normalized = engine.getPathCorrector().normalizeIfPossible(pathname);
		for (int i = 0; i<mRoot.dirCount(); i++) {
			FileInfo dir = mRoot.getDir(i);
			if (normalized.equals(engine.getPathCorrector().normalizeIfPossible(dir.getPathName())))
				return dir;
		}
		return null;
	}

	private boolean addRoot( String pathname, String filename, boolean listIt) {
		FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = pathname;
		dir.filename = filename;
		if (findRoot(pathname) != null) {
			log.w("skipping duplicate root " + pathname);
			return false; // exclude duplicates
		}
		if (listIt) {
			if (!dir.isWritableDirectory()) {
				log.w("Skipping " + pathname + " - it's not a writable directory");
				return false;
			}
			if (!listDirectory(dir)) {
				log.w("Skipping " + pathname + " - listing failed");
				return false;
			}
		}
		if (listIt && !listDirectory(dir))
			return false;
		mRoot.addDir(dir);
		dir.parent = mRoot;
		if ( !listIt ) {
			dir.isListed = true;
			dir.isScanned = true;
		}
		return true;
	}
	
	private void addOPDSRoot() {
		final FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = FileInfo.OPDS_LIST_TAG;
		dir.filename = coolReader.getString(R.string.mi_book_opds_root);
		dir.isListed = true;
		dir.isScanned = true;
		dir.parent = mRoot;
		mRoot.addDir(dir);
	}
	
	private void addSearchRoot() {
		FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = FileInfo.SEARCH_SHORTCUT_TAG;
		dir.filename = coolReader.getString(R.string.mi_book_search);
		dir.isListed = true;
		dir.isScanned = true;
		dir.parent = mRoot;
		mRoot.addDir(dir);
	}
	
	private void addAuthorsRoot() {
		FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = FileInfo.AUTHORS_TAG;
		dir.filename = coolReader.getString(R.string.folder_name_books_by_author);
		dir.isListed = true;
		dir.isScanned = true;
		dir.parent = mRoot;
		mRoot.addDir(dir);
	}
	
	private void addSeriesRoot() {
		FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = FileInfo.SERIES_TAG;
		dir.filename = coolReader.getString(R.string.folder_name_books_by_series);
		dir.isListed = true;
		dir.isScanned = true;
		dir.parent = mRoot;
		mRoot.addDir(dir);
	}
	
	private void addTitleRoot() {
		FileInfo dir = new FileInfo();
		dir.isDirectory = true;
		dir.pathname = FileInfo.TITLE_TAG;
		dir.filename = coolReader.getString(R.string.folder_name_books_by_title);
		dir.isListed = true;
		dir.isScanned = true;
		dir.parent = mRoot;
		mRoot.addDir(dir);
	}
	
	/**
	 * Lists all directories from root to directory of specified file, returns found directory.
	 * @param file
	 * @param root
	 * @return
	 */
	private FileInfo findParentInternal(FileInfo file, FileInfo root)	{
		if ( root==null || file==null || root.isRecentDir() )
			return null;
		if ( !root.isRootDir() && !file.getPathName().startsWith( root.getPathName() ) )
			return null;
		// to list all directories starting root dir
		if ( root.isDirectory && !root.isSpecialDir() )
				listDirectory(root);
		for ( int i=0; i<root.dirCount(); i++ ) {
			FileInfo found = findParentInternal( file, root.getDir(i));
			if ( found!=null )
				return found;
		}
		for ( int i=0; i<root.fileCount(); i++ ) {
			if ( root.getFile(i).getPathName().equals(file.getPathName()) )
				return root;
			if ( root.getFile(i).getPathName().startsWith(file.getPathName() + "@/") )
				return root;
		}
		return null;
	}
	
	public final static int MAX_DIR_LIST_TIME = 500; // 0.5 seconds
	
	/**
	 * Lists all directories from root to directory of specified file, returns found directory.
	 * @param file
	 * @param root
	 * @return
	 */
	public FileInfo findParent(FileInfo file, FileInfo root) {
		FileInfo parent = findParentInternal(file, root);
		if ( parent==null ) {
			autoAddRootForFile(new File(file.pathname) );
			parent = findParentInternal(file, root);
			if ( parent==null ) {
				L.e("Cannot find root directory for file " + file.pathname);
				return null;
			}
		}
		long maxTs = android.os.SystemClock.uptimeMillis() + MAX_DIR_LIST_TIME;
		listSubtrees(root, mHideEmptyDirs ? 5 : 1, maxTs);
		return parent;
	}
	
	/**
	 * List directories in subtree, limited by runtime and depth; remove empty branches (w/o books).  
	 * @param root is directory to start with
	 * @param maxDepth is maximum depth
	 * @param limitTs is limit for android.os.SystemClock.uptimeMillis()
	 * @return true if completed, false if stopped by limit. 
	 */
	private boolean listSubtree(FileInfo root, int maxDepth, long limitTs) {
		long ts = android.os.SystemClock.uptimeMillis();
		if ( ts>limitTs || maxDepth<=0 )
			return false;
		listDirectory(root);
		for ( int i=root.dirCount()-1; i>=-0; i-- ) {
			boolean res = listSubtree(root.getDir(i), maxDepth-1, limitTs);
			if ( !res )
				return false;
		}
		if ( mHideEmptyDirs )
			root.removeEmptyDirs();
		return true;
	}
	
	/**
	 * List directories in subtree, limited by runtime and depth; remove empty branches (w/o books).  
	 * @param root is directory to start with
	 * @param maxDepth is maximum depth
	 * @param limitTs is limit for android.os.SystemClock.uptimeMillis()
	 * @return true if completed, false if stopped by limit. 
	 */
	public boolean listSubtrees(FileInfo root, int maxDepth, long limitTs) {
		for ( int depth = 1; depth<=maxDepth; depth++ ) {
			boolean res = listSubtree( root, depth, limitTs );
			if ( res )
				return true;
			long ts = android.os.SystemClock.uptimeMillis();
			if ( ts>limitTs )
				return false; // limited by time
			// iterate deeper
		}
		return false; // limited by depth
	}
	
	public FileInfo setSearchResults( FileInfo[] results ) {
		FileInfo existingResults = null;
		for ( int i=0; i<mRoot.dirCount(); i++ ) {
			FileInfo dir = mRoot.getDir(i);
			if ( dir.isSearchDir() ) {
				existingResults = dir;
				dir.clear();
				break;
			}
		}
		if ( existingResults==null ) {
			FileInfo dir = new FileInfo();
			dir.isDirectory = true;
			dir.pathname = FileInfo.SEARCH_RESULT_DIR_TAG;
			dir.filename = coolReader.getResources().getString(R.string.dir_search_results);
			dir.parent = mRoot;
			dir.isListed = true;
			dir.isScanned = true;
			mRoot.addDir(dir);
			existingResults = dir;
		}
		for ( FileInfo item : results )
			existingResults.addFile(item);
		return existingResults;
	}
	
	public void initRoots(Map<String, String> fsRoots) {
		Log.d("cr3", "Scanner.initRoots(" + fsRoots + ")");
		mRoot.clear();
		// create recent books dir
		addRoot( FileInfo.RECENT_DIR_TAG, R.string.dir_recent_books, false);

		// create system dirs
		for (Map.Entry<String, String> entry : fsRoots.entrySet())
			addRoot( entry.getKey(), entry.getValue(), true);

		// create OPDS dir
		addOPDSRoot();
		
		// create search dir
		addSearchRoot();
		
		// create books by author root
		addAuthorsRoot();
		// create books by series root
		addSeriesRoot();
		// create books by title root
		addTitleRoot();
	}
	
	public boolean autoAddRootForFile( File f ) {
		File p = f.getParentFile();
		while ( p!=null ) {
			if ( p.getParentFile()==null || p.getParentFile().getParentFile()==null )
				break;
			p = p.getParentFile();
		}
		if ( p!=null ) {
			L.i("Found possible mount point " + p.getAbsolutePath());
			return addRoot(p.getAbsolutePath(), p.getAbsolutePath(), true);
		}
		return false;
	}
	
//	public boolean scan()
//	{
//		L.i("Started scanning");
//		long start = System.currentTimeMillis();
//		mFileList.clear();
//		mFilesForParsing.clear();
//		mRoot.clear();
//		// create recent books dir
//		FileInfo recentDir = new FileInfo();
//		recentDir.isDirectory = true;
//		recentDir.pathname = "@recent";
//		recentDir.filename = "Recent Books";
//		mRoot.addDir(recentDir);
//		recentDir.parent = mRoot;
//		// scan directories
//		lastPercent = -1;
//		lastProgressUpdate = System.currentTimeMillis() - 500;
//		boolean res = scanDirectories( mRoot );
//		// process found files
//		lookupDB();
//		parseBookProperties();
//		updateProgress(9999);
//		L.i("Finished scanning (" + (System.currentTimeMillis()-start)+ " ms)");
//		return res;
//	}
	
	
	public FileInfo getDownloadDirectory() {
		for ( int i=0; i<mRoot.dirCount(); i++ ) {
			FileInfo item = mRoot.getDir(i);
			if ( !item.isSpecialDir() && !item.isArchive ) {
				if (!item.isListed)
					listDirectory(item);
				FileInfo books = item.findItemByPathName(item.pathname + "/Books");
				if (books == null)
					books = item.findItemByPathName(item.pathname + "/books");
				if (books != null && books.exists())
					return books;
				File dir = new File(item.getPathName());
				if (dir.isDirectory()) {
					if (!dir.canWrite())
						Log.w("cr3", "Directory " + dir + " is readonly");
					File f = new File( dir, "Books" );
					if ( f.mkdirs() || f.isDirectory() ) {
						books = new FileInfo(f);
						books.parent = item;
						item.addDir(books);
						books.isScanned = true;
						books.isListed = true;
						return books;
					}
				}
			}
		}
		try {
			throw new Exception("download directory not found and cannot be created");
		} catch (Exception e) {
			Log.e("cr3", "download directory is not found!!!", e);
		}
		return null;
	}
	
	public FileInfo getRoot() 
	{
		return mRoot;
	}

	public FileInfo getOPDSRoot() 
	{
		for ( int i=0; i<mRoot.dirCount(); i++ ) {
			if ( mRoot.getDir(i).isOPDSRoot() )
				return mRoot.getDir(i);
		}
		L.w("OPDS root directory not found!");
		return null;
	}
	
	public FileInfo getRecentDir() 
	{
		for ( int i=0; i<mRoot.dirCount(); i++ ) {
			if ( mRoot.getDir(i).isRecentDir())
				return mRoot.getDir(i);
		}
		L.w("Recent books directory not found!");
		return null;
	}
	
	public Scanner( CoolReader coolReader, Engine engine )
	{
		this.engine = engine;
		this.coolReader = coolReader;
		mRoot = new FileInfo();
		mRoot.path = FileInfo.ROOT_DIR_TAG;	
		mRoot.filename = "File Manager";
		mRoot.pathname = FileInfo.ROOT_DIR_TAG;
		mRoot.isListed = true;
		mRoot.isScanned = true;
		mRoot.isDirectory = true;
	}

	private CRDBService.LocalBinder db() {
		return coolReader.getDB();
	}
	
	private final Engine engine;
	private final CoolReader coolReader;
}
