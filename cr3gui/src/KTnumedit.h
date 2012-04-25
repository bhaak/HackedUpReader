//
// C++ Interface: on-screen keyboard
//
// Description: 
//
//
// Author: Vadim Lopatin <vadim.lopatin@coolreader.org>, (C) 2008
//
// Copyright: See COPYING file that comes with this distribution
//
// scrkbd.h

#ifndef KTNUMEDIT_H_INCLUDED
#define KTNUMEDIT_H_INCLUDED

#include "mainwnd.h"

class KTNumedit : public CRGUIWindowBase
{
    int _minvalue;
    int _maxvalue;
    int _rectBottom;
protected:
    lString16 _value;
    CRMenuSkinRef _skin;
    lString16 _title;
    int _resultCmd;
    lChar16 _lastDigit;
    int _cols;
    virtual void draw();
    bool onKeyPressed( int key, int flags );
public:
    KTNumedit(CRGUIWindowManager * wm, const lString16 &title, const lString16 &initialValue, int resultCmd, int minvalue, int maxvalue, lvRect rc);

    virtual ~KTNumedit() { }

    /// returns true if command is processed
    virtual bool onCommand( int command, int params );
};

#endif
