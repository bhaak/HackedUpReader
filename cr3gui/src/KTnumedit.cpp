//
// C++ Implementation: on-screen keyboard
//
// Description: 
//      Shows keyboard, and allows to input text string
//
// Author: Vadim Lopatin <vadim.lopatin@coolreader.org>, (C) 2009
//
// Copyright: See COPYING file that comes with this distribution
//
// scrkbd.cpp

#include "KTnumedit.h"

bool KTNumedit::onKeyPressed( int key, int flags ) {

    CRRectSkinRef clientSkin = _skin->getClientSkin();
    lvRect inputRect = _skin->getClientRect( _rect );

    lvRect borders = clientSkin->getBorderWidths();
    inputRect.shrinkBy( borders );

    lvRect kbdRect = inputRect;
    inputRect.bottom = inputRect.top + 40; // TODO
    kbdRect.top = inputRect.bottom;

    const int xPos = _wm->getPosX();
    const int yPos = _wm->getPosY(); 

    int dx = kbdRect.width() / _cols;

    lvRect rc = kbdRect;
    rc.top += 50;
    rc.left += dx * 9;
    rc.bottom = rc.top + 40;
    rc.right = rc.left + dx;

    if (xPos > rc.left && xPos < rc.right && yPos > rc.top && yPos < rc.bottom) {
        //symbol "<" pressed
        if ( _value.length() > 0 ) {
            _value.erase( _value.length()-1, 1 );
            setDirty();
        }
    } else {
        for (int x = 1; x <= _cols; ++x) {

            rc = kbdRect;
            rc.left += dx * (x-1);
            rc.right = rc.left + dx;

            if (xPos > rc.left && xPos < rc.right  &&
                yPos > rc.top && yPos < rc.bottom 
            ) 
            {
                if (!(x == 1 && _value == "0")) {
                    _value << lString16::itoa(x-1);
                    setDirty();
                }
                break;
            }
        }
    }

    return CRGUIWindowBase::onKeyPressed( key, flags );
}

void KTNumedit::draw()
{
    CRRectSkinRef titleSkin = _skin->getTitleSkin();
    CRRectSkinRef clientSkin = _skin->getClientSkin();
    CRRectSkinRef itemSkin = _skin->getItemSkin();
    CRRectSkinRef shortcutSkin = _skin->getItemShortcutSkin();
    lvRect borders = clientSkin->getBorderWidths();
    LVRef<LVDrawBuf> drawbuf = _wm->getScreen()->getCanvas();
    _rect.bottom = _rectBottom + 46;
    _skin->draw( *drawbuf, _rect );
    _rect.bottom = _rectBottom;
    lvRect titleRect = _skin->getTitleRect( _rect );
    titleSkin->draw( *drawbuf, titleRect );
    titleSkin->drawText( *drawbuf, titleRect, _title );
    
    lvRect inputRect = _skin->getClientRect( _rect );
    inputRect.shrinkBy( borders );

    lvRect kbdRect = inputRect;
    inputRect.bottom = inputRect.top + 40; // TODO
    kbdRect.top = inputRect.bottom;

    int dx = kbdRect.width() / _cols;

    inputRect.right = inputRect.left + dx*_cols;

    for ( int x = 1; x <= _cols; ++x ) {

        lvRect rc = kbdRect;
        rc.left += dx * (x-1);
        rc.right = rc.left + dx;
        itemSkin->draw( *drawbuf, rc );

        itemSkin->drawText(*drawbuf, rc, lString16::itoa(x - 1));
    }

    //draw the last symbol "<"
    lvRect rc = kbdRect;
    rc.top += 50;
    rc.left += dx * 9;
    rc.bottom = rc.top + 40;
    rc.right = rc.left + dx;
    titleSkin->drawText( *drawbuf, rc, L"<" );

    // draw input area
    clientSkin->draw( *drawbuf, inputRect );
    clientSkin->drawText( *drawbuf, inputRect, lString16(" ") + _value+L"_" );
}

KTNumedit::KTNumedit(CRGUIWindowManager * wm, const lString16 &title, const lString16 &initialValue, int resultCmd, int minvalue, int maxvalue, lvRect rc)
: CRGUIWindowBase( wm ), _minvalue(minvalue), _maxvalue(maxvalue), _rectBottom(rc.bottom), _value(lString16::empty_str), _skin(_wm->getSkin()->getMenuSkin( L"#vkeyboard")), _title(title), _resultCmd(resultCmd), _cols(10)
{
    _passKeysToParent = false;
    _passCommandsToParent = false;
    _rect = rc;
    _fullscreen = false;
    setAccelerators( _wm->getAccTables().get("vkeyboard") );
}

/// returns true if command is processed
bool KTNumedit::onCommand( int command, int params )
{
    switch ( command ) {
    case MCMD_CANCEL: {
        _wm->closeWindow( this );
        return true;
    }
    case MCMD_OK: {
        int n = _value.atoi();
        if ( n >= _minvalue && n <= _maxvalue ) {
            _wm->postCommand( _resultCmd, n );
        }
        _wm->closeWindow( this );
        return true;
    }
    default:
        return true;
    }
    return true;
}
