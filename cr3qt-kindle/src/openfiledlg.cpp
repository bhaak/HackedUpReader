#include "openfiledlg.h"
#include "ui_openfiledlg.h"

OpenFileDlg::OpenFileDlg(QWidget *parent, CR3View * docView):
	QDialog(parent),
	m_ui(new Ui::OpenFileDlg),
	m_docview(docView)
{
	m_ui->setupUi(this);

	addAction(m_ui->actionGoToBegin);
	addAction(m_ui->actionNextPage);
	addAction(m_ui->actionPrevPage);
	addAction(m_ui->actionGoToFirstPage);
	addAction(m_ui->actionGoToLastPage);
	QAction *actionSelect = m_ui->actionSelectFile;
	actionSelect->setShortcut(Qt::Key_Select);
	addAction(actionSelect);

	folder = QIcon(":/icons/folder_sans_32.png");
	file = QIcon(":/icons/book_text_32.png");
	arrowUp = QIcon(":/icons/arrow_full_up_32.png");

	m_ui->FileList->setItemDelegate(new FileListDelegate());

	QString lastPathName;
	QString lastName;
	if(!docView->GetLastPathName(&lastPathName))
		#ifdef i386
		CurrentDir = "/home/";
		#else
		CurrentDir = "/mnt/us/documents/";
		#endif
	else {
		int pos = lastPathName.lastIndexOf("/");
		CurrentDir = lastPathName.mid(0, pos+1);
		lastName = lastPathName.mid(pos+1);
	}
	do {
		QDir Dir(CurrentDir);
		if(Dir.exists()) break;
		// trim last "/"
		CurrentDir.chop(1);
		int pos = CurrentDir.lastIndexOf("/");
		CurrentDir = CurrentDir.mid(0, pos+1);
		lastName = "";
	} while(true);

	FillFileList();
	m_ui->FileList->setCurrentRow(0);
	// showing last opened page
	int rc = docView->rowCount*2;
	curPage=0;
	if(!lastName.isEmpty()) {
		int pos = curFileList.indexOf(lastName)+1;
		if(pos!=0 && pos>rc) {
			curPage = (pos/rc)-1;
			if(pos%rc) curPage+=1;
		}
	}
	ShowPage(1);
	// selecting last opened book
	if(!lastName.isEmpty()) {
		QList<QListWidgetItem*> searchlist = m_ui->FileList->findItems(lastName, Qt::MatchExactly);
		if(searchlist.count())
			m_ui->FileList->setCurrentItem(searchlist.at(0));
	}
}

bool OpenFileDlg::showDlg(QWidget * parent, CR3View * docView)
{
	OpenFileDlg *dlg = new OpenFileDlg(parent, docView);
	dlg->showMaximized();
	return true;
}

OpenFileDlg::~OpenFileDlg()
{
	delete m_ui;
}

void OpenFileDlg::FillFileList()
{
	if(titleMask.isEmpty())
		titleMask = windowTitle();
	curFileList.clear();
	m_ui->FileList->clear();

	QDir::Filters filters;

	#ifdef i386
	if(CurrentDir=="/") filters = QDir::AllDirs|QDir::NoDotAndDotDot;
	#else
	if(CurrentDir=="/mnt/us/") filters = QDir::AllDirs|QDir::NoDotAndDotDot;
	#endif
	else filters = QDir::AllDirs|QDir::NoDot;
	QDir Dir(CurrentDir);
	curFileList=Dir.entryList(filters, QDir::Name);
	dirCount=curFileList.count();

	QStringList Filter;
	Filter << "*.fb2" << "*.zip" << "*.epub" << "*.rtf" << "*.txt" \
		   << "*.html" << "*.htm" << "*.tcr" << "*.pdb" << "*.chm" << "*.mobi" << "*.doc" << "*.azw";
	curFileList+= Dir.entryList(Filter, QDir::Files, QDir::Name);

	int count = curFileList.count();
	pageCount = 1;
	int rc = m_docview->rowCount*2;
	if(count>rc) {
		pageCount = count/rc;
		if(count%rc) pageCount+=1;
	}
}

void OpenFileDlg::ShowPage(int updown)
{
	if(updown>0) {
		if(curPage+1>pageCount) return;
		curPage+=1;
	} else {
		if(curPage-1<=0) return;
		curPage-=1;
	}
	m_ui->FileList->clear();
	setWindowTitle(titleMask + " (" + QString::number(curPage) + "/" + QString::number(pageCount) + ")");

	int rc = m_docview->rowCount*2;
	int h = (m_docview->height() -2 - (qApp->font().pointSize() + rc))/rc;
	QListWidgetItem *item = new QListWidgetItem();
	item->setSizeHint(QSize(item->sizeHint().width(), h));
	QListWidgetItem *pItem;

	int i=0;
	int startPos = ((curPage-1)*rc);
	if(startPos==0 && curFileList.at(0)=="..") {
		pItem = item->clone();
		pItem->setText("..");
		pItem->setIcon(arrowUp);
		m_ui->FileList->addItem(pItem);
		i++;
		startPos++;
	}

	for(int k=startPos; (k<curFileList.count()) && (i<rc); ++k, ++i) {
		if(k<dirCount) item->setIcon(folder);
		else item->setIcon(file);

		pItem = item->clone();
		pItem->setText(curFileList[k]);
		m_ui->FileList->addItem(pItem);
	}
	m_ui->FileList->setCurrentRow(0);
}

void OpenFileDlg::on_actionGoToBegin_triggered()
{
	m_ui->FileList->setCurrentRow(0);
}

void OpenFileDlg::on_actionSelectFile_triggered()
{
	QListWidgetItem *item  = m_ui->FileList->currentItem();
	QString ItemText = item->text();

	if(ItemText == "..")
	{
		int i;
		for(i=CurrentDir.length()-1; i>1; i--) {
			if(CurrentDir[i-1] == QChar('/')) break;
		}
		QString prevDir = CurrentDir.mid(i);
		prevDir.resize(prevDir.count()-1);

		CurrentDir.resize(i);
		FillFileList();
		// showing previous opened page
		int rc = m_docview->rowCount*2;
		curPage=0;
		if(!prevDir.isEmpty()) {
			int pos = curFileList.indexOf(prevDir)+1;
			if(pos!=0 && pos>rc) {
				curPage = (pos/rc)-1;
				if(pos%rc) curPage+=1;
			}
		}
		ShowPage(1);
		// selecting previous opened dir
		if(!prevDir.isEmpty()) {
			QList<QListWidgetItem*> searchlist = m_ui->FileList->findItems(prevDir, Qt::MatchExactly);
			if(searchlist.count())
				m_ui->FileList->setCurrentItem(searchlist.at(0));
		}
	} else {
		if (ItemText.length()==0) return;

		QString fileName = CurrentDir + ItemText;
		QFileInfo FileInfo(fileName);

		if(FileInfo.isDir()) {
			CurrentDir = fileName + QString("/");
			FillFileList();
			curPage=0;
			ShowPage(1);
		} else {
			m_docview->loadDocument(fileName);
			close();
		}
	}
}

void OpenFileDlg::on_actionNextPage_triggered()
{
	ShowPage(1);
}

void OpenFileDlg::on_actionPrevPage_triggered()
{
	ShowPage(-1);
}

void OpenFileDlg::on_actionGoToLastPage_triggered()
{
	if(pageCount-1==0) return;
	curPage=pageCount-1;
	ShowPage(1);
}

void OpenFileDlg::on_actionGoToFirstPage_triggered()
{
	if(curPage==1) return;
	curPage=0;
	ShowPage(1);
}
