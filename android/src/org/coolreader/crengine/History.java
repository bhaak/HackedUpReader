package org.coolreader.crengine;

import java.util.ArrayList;

import org.coolreader.CoolReader;
import org.coolreader.db.CRDBService;

import android.util.Log;

public class History extends FileInfoChangeSource {
	private ArrayList<BookInfo> mBooks = new ArrayList<BookInfo>();
	private final CoolReader mCoolReader;
	private FileInfo mRecentBooksFolder;

	private CRDBService.LocalBinder db() {
		return mCoolReader.getDB();
	}
	
	public History(CoolReader cr, Scanner scanner)
	{
		this.mCoolReader = cr;
		this.mScanner = scanner;
	}
	
	public BookInfo getLastBook()
	{
		if ( mBooks.size()==0 )
			return null;
		return mBooks.get(0);
	}

	public BookInfo getPreviousBook()
	{
		if ( mBooks.size()<2 )
			return null;
		return mBooks.get(1);
	}

	public interface BookInfoLoadedCallack {
		void onBookInfoLoaded(BookInfo bookInfo);
	}
	
	public void getOrCreateBookInfo(final FileInfo file, final BookInfoLoadedCallack callback)
	{
		BookInfo res = getBookInfo(file);
		if (res != null) {
			callback.onBookInfoLoaded(res);
			return;
		}
		db().loadBookInfo(file, new CRDBService.BookInfoLoadingCallback() {
			@Override
			public void onBooksInfoLoaded(BookInfo bookInfo) {
				if (bookInfo == null) {
					bookInfo = new BookInfo(file);
					mBooks.add(0, bookInfo);
				}
				callback.onBookInfoLoaded(bookInfo);
			}
		});
	}
	
	public BookInfo getBookInfo( FileInfo file )
	{
		int index = findBookInfo( file );
		if ( index>=0 )
			return mBooks.get(index);
		return null;
	}

	public BookInfo getBookInfo( String pathname )
	{
		int index = findBookInfo( pathname );
		if ( index>=0 )
			return mBooks.get(index);
		return null;
	}
	
	public void removeBookInfo(FileInfo fileInfo, boolean removeRecentAccessFromDB, boolean removeBookFromDB)
	{
		int index = findBookInfo(fileInfo);
		if (index >= 0)
			mBooks.remove(index);
		if ( removeBookFromDB )
			db().deleteBook(fileInfo);
		else if ( removeRecentAccessFromDB )
			db().deleteRecentPosition(fileInfo);
		updateRecentDir();
	}
	
	public void updateBookAccess( BookInfo bookInfo )
	{
		Log.v("cr3", "History.updateBookAccess() for " + bookInfo.getFileInfo().getPathName());
		int index = findBookInfo(bookInfo.getFileInfo());
		if ( index>=0 ) {
			BookInfo info = mBooks.get(index);
			if ( index>0 ) {
				mBooks.remove(index);
				mBooks.add(0, info);
			}
			info.updateAccess();
			updateRecentDir();
		}
	}

	public int findBookInfo( String pathname )
	{
		for ( int i=0; i<mBooks.size(); i++ )
			if ( pathname.equals(mBooks.get(i).getFileInfo().getPathName()) )
				return i;
		return -1;
	}
	
	public int findBookInfo( FileInfo file )
	{
		for ( int i=0; i<mBooks.size(); i++ )
			if (file.pathNameEquals(mBooks.get(i).getFileInfo()))
				return i;
		return -1;
	}
	
	public Bookmark getLastPos( FileInfo file )
	{
		int index = findBookInfo(file);
		if ( index<0 )
			return null;
		return mBooks.get(index).getLastPosition();
	}
	protected void updateRecentDir()
	{
		Log.v("cr3", "History.updateRecentDir()");
		if ( mRecentBooksFolder!=null ) { 
			mRecentBooksFolder.clear();
			for ( BookInfo book : mBooks )
				mRecentBooksFolder.addFile(book.getFileInfo());
			onChange(mRecentBooksFolder);
		} else {
			Log.v("cr3", "History.updateRecentDir() : mRecentBooksFolder is null");
		}
	}
	Scanner mScanner;
	public boolean loadFromDB(int maxItems )
	{
		Log.v("cr3", "History.loadFromDB()");
		mRecentBooksFolder = mScanner.getRecentDir();
		db().loadRecentBooks(100, new CRDBService.RecentBooksLoadingCallback() {
			@Override
			public void onRecentBooksListLoaded(ArrayList<BookInfo> bookList) {
				if (bookList != null) {
					mBooks = bookList;
					updateRecentDir();
				}
			}
		});
		if ( mRecentBooksFolder==null )
			Log.v("cr3", "History.loadFromDB() : mRecentBooksFolder is null");
		return true;
	}

}
