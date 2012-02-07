#ifndef SEARCHDLG_H
#define SEARCHDLG_H

#include <QDialog>
#include <QMessageBox>
#include <QPainter>
#include <QEvent>
#include <QKeyEvent>
#include <QWSEvent>
#include <QDebug>

#include "lvstring.h"

#include "cr3widget.h"

namespace Ui {
	class SearchDialog;
}

class CR3View;

class SearchDialog : public QDialog {
	Q_OBJECT
public:
	SearchDialog(QWidget *parent, CR3View * docView);
	~SearchDialog();

	static bool showDlg( QWidget * parent, CR3View * docView );
	bool findText( lString16 pattern, int origin, bool reverse, bool caseInsensitive );
protected:
	void changeEvent(QEvent *e);
	virtual void paintEvent (QPaintEvent * event);
private:
	Ui::SearchDialog *ui;
	CR3View * _docview;
	lString16 _lastPattern;
private slots:
	void on_btnFindNext_clicked();
	void on_rbForward_toggled(bool checked);
	void on_rbBackward_toggled(bool checked);
};

#endif // SEARCHDLG_H
