//
// C++ Interface: TOC dialog
//
// Description: 
//
//
// Author: Vadim Lopatin <vadim.lopatin@coolreader.org>, (C) 2008
//
// Copyright: See COPYING file that comes with this distribution
//
// tocdlg.h

#ifndef TOCDLG_H_INCLUDED
#define TOCDLG_H_INCLUDED

#include "mainwnd.h"
#include "numedit.h"

class CRTOCDialog : public CRGUIWindowBase
{
	protected:
        lString16 _title;
        lString16 _value;
        int _minvalue;
        int _maxvalue;
        int _resultCmd;
        CRMenuSkinRef _skin;
	
        LVPtrVector<LVTocItem, false> _items;
        LVFontRef _font;
        int _itemHeight;
        int _topItem;
        int _pageItems;
        LVDocView * _docview;

        virtual void draw();
#if KINDLE_TOUCH==1
        bool onKeyPressed( int key, int flags );
#endif
    public:
        CRTOCDialog( CRGUIWindowManager * wm, lString16 title, int resultCmd, int pageCount, LVDocView * docview );
        virtual ~CRTOCDialog()
        {
        }
#if KINDLE_TOUCH!=1
        bool digitEntered( lChar16 c );
#endif
		int getCurItemIndex();

        /// returns true if command is processed
        virtual bool onCommand( int command, int params );
};


#endif
