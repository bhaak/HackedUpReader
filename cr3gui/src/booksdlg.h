#ifndef CR3_BOOKS_DIALOG_H_INCLUDED
#define CR3_BOOKS_DIALOG_H_INCLUDED

#include <crgui.h>
#include "settings.h"
#include "lvstring.h"

#define BOOKS_DIALOG_MENU_COMMANDS_START 900

class CRBookMenuItem : public CRMenuItem
{
protected:
    lString16 _fBookPath;
public:
	CRBookMenuItem( CRMenu * parentMenu, int id, const char *label, LVImageSourceRef image, LVFontRef defFont, LVFontRef valueFont, const lString16 &fBookPath )
    :CRMenuItem(parentMenu, id, label, image, defFont),
    _fBookPath(fBookPath)
    {}
    lString16 getBookPath() const;
};

class CRBooksMenu : public CRFullScreenMenu
{
protected:
    LVFontRef _valueFont;
    CRGUIAcceleratorTableRef _menuAccelerators;
    LVDocView *_docview;
public:	
    CRBooksMenu(CRGUIWindowManager * wm, CRMenu * parentMenu, int id, const char * label, LVImageSourceRef image, LVFontRef defFont, LVFontRef valueFont, CRPropRef props, CRGUIAcceleratorTableRef menuAccelerators, LVDocView *docview);
    virtual bool onCommand( int command, int params );
	LVDocView *getDocview() const {
        return _docview;
	}
    virtual ~CRBooksMenu() {
        CRLog::trace("Calling fontMan->gc() on BooksDialog menu destroy");
        fontMan->gc();
        CRLog::trace("Done fontMan->gc() on BooksDialog menu destroy");
	}
};

CRMenu *createBooksDialogMenu(const char *path, CRGUIWindowManager * wm, LVFontRef font, CRPropRef props, CRGUIAcceleratorTableRef menuAccelerators, LVDocView *docview);

#endif //CR3_BOOKS_DIALOG_H_INCLUDED