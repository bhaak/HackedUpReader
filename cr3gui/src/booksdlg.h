#ifndef CR3_BOOKS_DIALOG_H_INCLUDED
#define CR3_BOOKS_DIALOG_H_INCLUDED

#include <crgui.h>
#include "settings.h"
#include "lvstring.h"

#define BOOKS_DIALOG_MENU_COMMANDS_START 900

class CRBookMenuItem : public CRMenu
{
    lString16 _fBookPath;
public:
	CRBookMenuItem( CRGUIWindowManager * wm, CRMenu * parentMenu, int id, const char *label, LVImageSourceRef image, LVFontRef defFont, LVFontRef valueFont, const lString16 &fBookPath, CRPropRef props = CRPropRef(), const char * propName = NULL, int pageItems = 8 )
    :CRMenu(wm, parentMenu, id, label, image, defFont, valueFont, props, propName, pageItems),
    _fBookPath(fBookPath)
    {
	}
    lString16 getBookPath() const;
};

class CRBooksDialogMenu : public CRFullScreenMenu
{
		LVFontRef _valueFont;
		void walkDirRecursively(const char *dir);
		void addEbookFiles();
	protected:
		CRPropRef _props;
        CRGUIAcceleratorTableRef _menuAccelerators;
        LVDocView * _docview;
    public:	
        CRBooksDialogMenu( CRGUIWindowManager * wm, CRPropRef props, int id, LVFontRef font, CRGUIAcceleratorTableRef menuAccelerators, lvRect & rc, LVDocView * docview);
        virtual bool onCommand( int command, int params );
        virtual ~CRBooksDialogMenu()
        {
            CRLog::trace("Calling fontMan->gc() on BooksDialog menu destroy");
            fontMan->gc();
            CRLog::trace("Done fontMan->gc() on BooksDialog menu destroy");
        }
};
#endif //CR3_BOOKS_DIALOG_H_INCLUDED
