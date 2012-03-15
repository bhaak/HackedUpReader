//
// C++ Implementation: settings
//
// Description:
//
//
// Author: Vadim Lopatin <vadim.lopatin@coolreader.org>, (C) 2008
//
// Copyright: See COPYING file that comes with this distribution
//
//

#include "booksdlg.h"
#include <crgui.h>
//#include "fsmenu.h"
#ifdef CR_POCKETBOOK
#include "cr3pocketbook.h"
#endif

#include <cri18n.h>
#include <dirent.h>    /* struct DIR, struct dirent, opendir().. */ 

#include "mainwnd.h"

bool CRBooksDialogMenu::onCommand( int command, int params )
{
	if ( command >= MCMD_SELECT_1 && command <= MCMD_SELECT_9 ) {
        int index = command - MCMD_SELECT_1 + getTopItem();
        CRMenuItem * item = getItems()[index];  
        highlightCommandItem( item->getId() ); 
        _docview->close(); 
        lString16 fname = lString16("/mnt/us/documents/") + item->getLabel();         
        if ( !_docview->LoadDocument( fname.c_str() ) ) {
			CRLog::error("CRBooksDialogMenu::onCommand( %s ) - failed!", fname.c_str());
		}
		_docview->restorePosition();
		closeMenu(0, 0);
        return true;
    } else {
		return CRMenu::onCommand( command, params );	
	}
}

CRBooksDialogMenu::CRBooksDialogMenu( CRGUIWindowManager * wm, CRPropRef newProps, int id, LVFontRef font, CRGUIAcceleratorTableRef menuAccelerators, lvRect &rc, LVDocView * docview)
: CRFullScreenMenu( wm, id, lString16(_("Select book")), 8, rc ),
  props( newProps ),
  _menuAccelerators( menuAccelerators ),
  _docview(docview),
  _valueFont(LVFontRef(fontMan->GetFont( VALUE_FONT_SIZE, 400, true, css_ff_sans_serif, lString8("Arial"))))
{
    setSkinName(lString16(L"#settings"));
	setAccelerators( _menuAccelerators );
    _fullscreen = true;
    addEbookFiles();
    reconfigure(0);
}

void CRBooksDialogMenu::addEbookFiles() {
	walkDirRecursively("/mnt/us/documents");
}

void CRBooksDialogMenu::walkDirRecursively(const char *dirname) {
	static size_t totalFound = 0;
    DIR* dir = opendir(dirname);
    if (!dir) {
		CRLog::error("unable to open %s using opendir", dirname);
	} else {
			char fn[FILENAME_MAX];
			int len = strlen(dirname);
			strcpy(fn, dirname);
			fn[len++] = '/';
			
			struct dirent* entry;
			while ((entry = readdir(dir)) != NULL) {
				const char *fname = entry->d_name;
				if (strcmp(fname, ".") == 0 || strcmp(fname, "..") == 0) {
					continue;
				} 
				
				strncpy(fn + len, entry->d_name, FILENAME_MAX - len);
				if ((entry->d_type) == DT_REG) {
					if (endsWith(fname, ".epub") 
						|| endsWith(fname, ".fb2") 
						|| endsWith(fname, ".txt") 
						|| endsWith(fname, ".rtf")
						|| endsWith(fname, ".html")
						|| endsWith(fname, ".htm")
						|| endsWith(fname, ".pdb") 
						|| endsWith(fname, ".mobi")) 	
					{		
						CRLog::info("adding ebook file: %s", fname);
						++totalFound;				
						CRMenu * fNameItem = new CRMenu(_wm, this, BOOKS_DIALOG_MENU_COMMANDS_START + totalFound,
							_(fname),
							 LVImageSourceRef(), LVFontRef(), _valueFont, props, PROP_FONT_FACE );
						fNameItem->setSkinName(lString16(L"#settings"));
						fNameItem->setFullscreen( true );
						fNameItem->setAccelerators( _menuAccelerators );
						fNameItem->reconfigure( 0 );
						
						addItem(fNameItem);
					}
				} else if((entry->d_type) == DT_DIR) {
					if (strcmp(fname, "dictionary") != 0) {
						walkDirRecursively(fn);
					}
				}
			}
			
			if (closedir(dir) == -1) {
				CRLog::error("unable to open %s using closedir", dirname);
			}
	}
}
