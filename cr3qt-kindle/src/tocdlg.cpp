#include "tocdlg.h"
#include "ui_tocdlg.h"
#include <QKeyEvent>
#include <math.h>

class TocItem : public QTreeWidgetItem
{
	LVTocItem * _item;
public:
	LVTocItem * getItem() { return _item; }
	TocItem(LVTocItem * item, int currPage, int & nearestPage, TocItem * & nearestItem)
		: QTreeWidgetItem(QStringList() << (item ? cr2qt(item->getName()) : "No TOC items")
										<< (item ? cr2qt(lString16::itoa(item->getPage()+1)) : "")) , _item(item)
	{
		setTextAlignment(1, Qt::AlignRight|Qt::AlignVCenter);
		int page = item->getPage();
		if (!nearestItem || (page <= currPage && page > nearestPage)) {
			nearestItem = this;
			nearestPage = page;
		}
		setData(0, Qt::UserRole, QVariant(cr2qt(item->getXPointer().toString())));
		for (int i = 0; i < item->getChildCount(); i++) {
			addChild(new TocItem(item->getChild(i), currPage, nearestPage, nearestItem));
		}
	}
};

bool TocDlg::showDlg(QWidget * parent, CR3View * docView)
{
//	LVTocItem * root = docView->getToc();
//	if (!root || !root->getChildCount())
//		return false;
	TocDlg * dlg = new TocDlg(parent, docView);
	dlg->showMaximized();
	return true;
}

TocDlg::TocDlg(QWidget *parent, CR3View * docView) :
	QDialog(parent),
	m_ui(new Ui::TocDlg), m_docview(docView)
{
	setAttribute(Qt::WA_DeleteOnClose, true);
	m_ui->setupUi(this);
	addAction(m_ui->actionNextPage);
	addAction(m_ui->actionPrevPage);

	QAction *actionSelect = m_ui->actionGotoPage;
	actionSelect->setShortcut(Qt::Key_Select);
	addAction(actionSelect);

	m_ui->treeWidget->setColumnCount(2);
	m_ui->treeWidget->header()->setResizeMode(0, QHeaderView::Stretch);
	m_ui->treeWidget->header()->setResizeMode(1, QHeaderView::ResizeToContents);

	int nearestPage = -1;
	int currPage = docView->getCurPage();
	TocItem * nearestItem = NULL;
	LVTocItem * root = m_docview->getToc();
	for (int i=0; i<root->getChildCount(); i++ )
		m_ui->treeWidget->addTopLevelItem(new TocItem(root->getChild(i), currPage, nearestPage, nearestItem));

	m_ui->treeWidget->expandAll();
	if(nearestItem)
		m_ui->treeWidget->setCurrentItem(nearestItem);

	m_ui->pageNumEdit->setValidator(new QIntValidator(1, 999999999, this));
	m_ui->pageNumEdit->installEventFilter(this);
	m_ui->treeWidget->installEventFilter(this);
}

TocDlg::~TocDlg()
{
	delete m_ui;
}

void TocDlg::changeEvent(QEvent *e)
{
	switch (e->type()) {
	case QEvent::LanguageChange:
		m_ui->retranslateUi(this);
		break;
	default:
		break;
	}
}

void TocDlg::on_actionGotoPage_triggered()
{
	int pagenum = m_ui->pageNumEdit->text().toInt();
	if(pagenum && pagenum <= m_docview->getPageCount()) {
		m_docview->GoToPage(pagenum-1);
		close();
	}
}

bool TocDlg::eventFilter(QObject *obj, QEvent *event)
{
	if(event->type() == QEvent::KeyPress) {
		QKeyEvent *keyEvent = static_cast<QKeyEvent*>(event);
		QString text;
		switch(keyEvent->key()) {
			case Qt::Key_Q:
				text = "1";
				break;
			case Qt::Key_W:
				text = "2";
				break;
			case Qt::Key_E:
				text = "3";
				break;
			case Qt::Key_R:
				text = "4";
				break;
			case Qt::Key_T:
				text = "5";
				break;
			case Qt::Key_Y:
				text = "6";
				break;
			case Qt::Key_U:
				text = "7";
				break;
			case Qt::Key_I:
				text = "8";
				break;
			case Qt::Key_O:
				text = "9";
				break;
			case Qt::Key_P:
				text = "0";
				break;
			case Qt::Key_Select:
				qDebug(focusWidget()->objectName().toAscii().data());
				if(obj == m_ui->treeWidget) {
					QString s = m_ui->treeWidget->currentIndex().data(Qt::UserRole).toString();
					m_docview->goToXPointer(s);
					close();

					return true;
				}
		}
		if(keyEvent->key() >= Qt::Key_0 && keyEvent->key() <= Qt::Key_9) text = keyEvent->text();
		if(!text.isEmpty()) {
			m_ui->pageNumEdit->setText(m_ui->pageNumEdit->text() + text);
			m_ui->pageNumEdit->setEditFocus(true);
			return true;
		}
	}
	return false;
}

void TocDlg::showEvent(QShowEvent *e)
{
	fillOpts();
	titleMask = windowTitle();
	curPage = 1;
	updateTitle();
}

void TocDlg::updateTitle()
{
	setWindowTitle(titleMask + " (" + QString::number(curPage) + "/" + QString::number(pageCount) + ")");
}

void TocDlg::on_actionNextPage_triggered()
{
	if(curPage+1<=pageCount) {
		curPage+=1;
		QScrollBar * scrollBar = m_ui->treeWidget->verticalScrollBar();
		scrollBar->setMaximum((pageCount-1) * pageStrCount);
		scrollBar->setValue(curPage*pageStrCount-pageStrCount);
		updateTitle();
	}
}

void TocDlg::on_actionPrevPage_triggered()
{
	if(curPage-1>=1) {
		curPage-=1;
		QScrollBar * scrollBar = m_ui->treeWidget->verticalScrollBar();
		scrollBar->setValue(scrollBar->value()-pageStrCount);
		updateTitle();
	}
}

void TocDlg::fillOpts()
{
	QScrollBar * scrollBar = m_ui->treeWidget->verticalScrollBar();

	pageStrCount = scrollBar->pageStep();
	fullStrCount = scrollBar->maximum()-scrollBar->minimum()+pageStrCount;

	if(fullStrCount==pageStrCount) {
		pageCount = 1;
		return;
	}
	if(pageStrCount==1) {
		scrollBar->setMaximum(fullStrCount*2);
		pageStrCount = scrollBar->pageStep();
	}
	pageCount = ceil((double)fullStrCount/pageStrCount);
}
