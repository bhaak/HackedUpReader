TARGET = cr3
VERSION = 0.1.4
TEMPLATE = app

CONFIG += TARGET_KINDLE
DEFINES += USE_FREETYPE=1 \
		LDOM_USE_OWN_MEM_MAN=1 \
		COLOR_BACKBUFFER=1 \
		USE_DOM_UTF8_STORAGE=1 \
		NDEBUG=1

TARGET_KINDLE:DEFINES += QT_KEYPAD_NAVIGATION

debug:DEFINES += _DEBUG=1

INCLUDEPATH += /mnt/us/qtKindle/include \
		/mnt/us/qtKindle/include/QtCore \
		/mnt/us/qtKindle/include/QtGui \
		/mnt/us/qtKindle/include/freetype2 \
		../../crengine/crengine/include \
		../../crengine/thirdparty/antiword
SOURCES += main.cpp \
		mainwindow.cpp \
		searchdlg.cpp \
		cr3widget.cpp \
		crqtutil.cpp \
		tocdlg.cpp \
		recentdlg.cpp \
		settings.cpp \
		bookmarklistdlg.cpp \
		filepropsdlg.cpp \
		openfiledlg.cpp
HEADERS += mainwindow.h \
		cr3widget.h \
		crqtutil.h \
		tocdlg.h \
		recentdlg.h \
		settings.h \
		bookmarklistdlg.h \
		searchdlg.h \
		filepropsdlg.h \
		openfiledlg.h
FORMS += mainwindow.ui \
		tocdlg.ui \
		recentdlg.ui \
		settings.ui \
		bookmarklistdlg.ui \
		searchdlg.ui \
		filepropsdlg.ui \
		openfiledlg.ui

TRANSLATIONS += i18n/Russian.ts

RESOURCES += cr3res.qrc

#LIBS += ../tinydict/libtinydict.a -lfontconfig
LIBS += -lQtGui -lQtGui -lQtCore -lQtNetwork -lQtDBus -lpthread -ldl -ljpeg -lfreetype -lpng -lz

arm {
	LIBS += ../../crengine/crengine/libcrengine_kindle.a
} else {
	LIBS += ../../crengine/crengine/libcrengine_desktop.a
}
