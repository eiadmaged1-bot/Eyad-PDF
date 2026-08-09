from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import QObject, Qt, QThread, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QFileDialog, QFrame, QHBoxLayout, QLabel, QLineEdit,
    QListWidget, QListWidgetItem, QMainWindow, QMessageBox, QPushButton,
    QVBoxLayout, QWidget, QAbstractItemView, QPlainTextEdit,
)

from conversion import ConversionError, convert_and_merge, convert_to_pdf, engine_summary, supported_input

APP_VERSION = "0.11.0"

class FileList(QListWidget):
    filesDropped = Signal(list)

    def __init__(self):
        super().__init__()
        self.setAcceptDrops(True)
        self.setDragEnabled(True)
        self.setDragDropMode(QAbstractItemView.DragDropMode.InternalMove)
        self.setDefaultDropAction(Qt.DropAction.MoveAction)
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.setAlternatingRowColors(False)
        self.setObjectName("fileList")

    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
        else:
            super().dragEnterEvent(event)

    def dragMoveEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
        else:
            super().dragMoveEvent(event)

    def dropEvent(self, event):
        if event.mimeData().hasUrls() and event.source() is not self:
            paths = [u.toLocalFile() for u in event.mimeData().urls() if u.isLocalFile()]
            self.filesDropped.emit(paths)
            event.acceptProposedAction()
        else:
            super().dropEvent(event)

    def paths(self) -> list[Path]:
        return [Path(self.item(i).data(Qt.ItemDataRole.UserRole)) for i in range(self.count())]

class JobWorker(QObject):
    log = Signal(str)
    done = Signal(list)
    failed = Signal(str)
    finished = Signal()

    def __init__(self, mode: str, files: list[Path], output_dir: Path | None, same_input: bool, merge_name: str):
        super().__init__()
        self.mode = mode
        self.files = files
        self.output_dir = output_dir
        self.same_input = same_input
        self.merge_name = merge_name

    @Slot()
    def run(self):
        try:
            outputs: list[str] = []
            if self.mode == "convert":
                for i, source in enumerate(self.files, start=1):
                    if source.suffix.lower() == ".pdf":
                        self.log.emit(f"[{i}/{len(self.files)}] {source.name}: already PDF — skipped.")
                        continue
                    out_dir = source.parent if self.same_input else self.output_dir
                    if out_dir is None:
                        raise ConversionError("Choose an output folder.")
                    self.log.emit(f"[{i}/{len(self.files)}] Converting {source.name}")
                    result = convert_to_pdf(source, out_dir, log=self.log.emit)
                    outputs.append(str(result.output))
            else:
                base_dir = self.files[0].parent if self.same_input else self.output_dir
                if base_dir is None:
                    raise ConversionError("Choose an output folder.")
                name = self.merge_name.strip() or "Eyad PDF merged"
                if not name.lower().endswith(".pdf"):
                    name += ".pdf"
                result = convert_and_merge(self.files, base_dir / name, log=self.log.emit)
                outputs.append(str(result))
            self.done.emit(outputs)
        except Exception as exc:
            self.failed.emit(str(exc))
        finally:
            self.finished.emit()

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(f"Eyad PDF — Desktop Converter v{APP_VERSION}")
        self.resize(1180, 760)
        self.setMinimumSize(900, 620)
        self._thread = None
        self._worker = None

        root = QWidget()
        self.setCentralWidget(root)
        outer = QHBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)

        rail = QFrame(objectName="rail")
        rail.setFixedWidth(210)
        r = QVBoxLayout(rail)
        r.setContentsMargins(22, 26, 22, 24)
        brand = QLabel("EYAD PDF")
        brand.setObjectName("brand")
        sub = QLabel("Desktop workspace")
        sub.setObjectName("railSub")
        nav = QLabel("▸  Converter")
        nav.setObjectName("navActive")
        r.addWidget(brand)
        r.addWidget(sub)
        r.addSpacing(34)
        r.addWidget(nav)
        r.addStretch(1)
        engine = QLabel(engine_summary())
        engine.setWordWrap(True)
        engine.setObjectName("engineStatus")
        r.addWidget(QLabel("CONVERSION ENGINE", objectName="railLabel"))
        r.addWidget(engine)
        r.addSpacing(16)
        r.addWidget(QLabel(f"v{APP_VERSION}", objectName="version"))
        outer.addWidget(rail)

        content = QWidget(objectName="canvas")
        c = QVBoxLayout(content)
        c.setContentsMargins(34, 28, 34, 28)
        c.setSpacing(16)

        title = QLabel("Convert documents to PDF")
        title.setObjectName("title")
        subtitle = QLabel("High-fidelity Office export only. Broken text-only reconstruction is rejected.")
        subtitle.setObjectName("subtitle")
        c.addWidget(title)
        c.addWidget(subtitle)

        drop_card = QFrame(objectName="card")
        dc = QVBoxLayout(drop_card)
        dc.setContentsMargins(22, 20, 22, 18)
        dc.setSpacing(10)
        line = QHBoxLayout()
        line.addWidget(QLabel("Files", objectName="sectionTitle"))
        line.addStretch(1)
        add_btn = QPushButton("+ Add files", objectName="secondary")
        add_btn.clicked.connect(self.add_files_dialog)
        remove_btn = QPushButton("Remove selected", objectName="secondary")
        remove_btn.clicked.connect(self.remove_selected)
        clear_btn = QPushButton("Clear", objectName="secondary")
        clear_btn.clicked.connect(self.file_list_clear)
        line.addWidget(add_btn)
        line.addWidget(remove_btn)
        line.addWidget(clear_btn)
        dc.addLayout(line)
        hint = QLabel("Drop PPTX / DOCX / XLSX / ODT / ODS / ODP / RTF / PDF here. Drag rows to set merge order.")
        hint.setObjectName("hint")
        dc.addWidget(hint)
        self.file_list = FileList()
        self.file_list.filesDropped.connect(self.add_paths)
        dc.addWidget(self.file_list, 1)
        c.addWidget(drop_card, 1)

        output_card = QFrame(objectName="card")
        oc = QVBoxLayout(output_card)
        oc.setContentsMargins(22, 18, 22, 18)
        oc.setSpacing(12)
        oc.addWidget(QLabel("Output", objectName="sectionTitle"))
        self.same_input = QCheckBox("Save output in the same folder as each input file")
        self.same_input.setChecked(True)
        self.same_input.toggled.connect(self.sync_output_state)
        oc.addWidget(self.same_input)
        folder_row = QHBoxLayout()
        self.output_edit = QLineEdit()
        self.output_edit.setPlaceholderText("Choose output folder")
        browse = QPushButton("Browse…", objectName="secondary")
        browse.clicked.connect(self.choose_output)
        folder_row.addWidget(self.output_edit, 1)
        folder_row.addWidget(browse)
        oc.addLayout(folder_row)
        merge_row = QHBoxLayout()
        merge_row.addWidget(QLabel("Merged filename", objectName="fieldLabel"))
        self.merge_name = QLineEdit("Eyad PDF merged.pdf")
        merge_row.addWidget(self.merge_name, 1)
        oc.addLayout(merge_row)
        note = QLabel("Convert & Merge saves the merged PDF beside the first file when “same folder” is checked.")
        note.setObjectName("hint")
        oc.addWidget(note)
        c.addWidget(output_card)

        actions = QHBoxLayout()
        self.convert_btn = QPushButton("Convert", objectName="primary")
        self.convert_btn.clicked.connect(lambda: self.start_job("convert"))
        self.merge_btn = QPushButton("Convert & Merge", objectName="primaryAlt")
        self.merge_btn.clicked.connect(lambda: self.start_job("merge"))
        self.open_btn = QPushButton("Open output folder", objectName="secondary")
        self.open_btn.clicked.connect(self.open_output_folder)
        actions.addWidget(self.convert_btn)
        actions.addWidget(self.merge_btn)
        actions.addStretch(1)
        actions.addWidget(self.open_btn)
        c.addLayout(actions)

        self.status = QPlainTextEdit()
        self.status.setObjectName("status")
        self.status.setReadOnly(True)
        self.status.setMaximumHeight(128)
        self.status.setPlaceholderText("Conversion status appears here.")
        c.addWidget(self.status)

        outer.addWidget(content, 1)
        self.sync_output_state()
        self.apply_theme()

    def apply_theme(self):
        self.setStyleSheet('''
        QMainWindow { background: #F3F5F7; }
        QWidget { font-family: "Segoe UI Variable", "Segoe UI", sans-serif; font-size: 15px; color: #182531; }
        #rail { background: #18232D; }
        #brand { color: white; font-size: 22px; font-weight: 800; letter-spacing: 1px; }
        #railSub, #engineStatus, #version { color: #B8C5D0; font-size: 13px; }
        #railLabel { color: #7A95F3; font-size: 11px; font-weight: 700; }
        #navActive { color: white; background: #2A3845; border-left: 3px solid #7A95F3; border-radius: 7px; padding: 12px 12px; font-weight: 700; }
        #canvas { background: #F3F5F7; }
        #title { font-size: 27px; font-weight: 750; color: #182531; }
        #subtitle, #hint { color: #61717F; }
        #sectionTitle { font-size: 17px; font-weight: 700; }
        #fieldLabel { color: #61717F; min-width: 120px; }
        #card { background: white; border: 1px solid #DDE4EA; border-radius: 12px; }
        QLineEdit { min-height: 42px; padding: 0 12px; background: #FFFFFF; border: 1px solid #C8D2DA; border-radius: 8px; selection-background-color: #2854C7; }
        QLineEdit:focus { border: 2px solid #2854C7; }
        QCheckBox { min-height: 38px; spacing: 9px; }
        QListWidget#fileList { background: #F7F9FB; border: 1px dashed #AAB8C5; border-radius: 10px; padding: 8px; min-height: 190px; outline: none; }
        QListWidget#fileList::item { min-height: 40px; padding: 5px 8px; border-radius: 6px; }
        QListWidget#fileList::item:selected { background: #EEF3FF; color: #182531; border: 1px solid #7A95F3; }
        QPushButton { min-height: 44px; padding: 0 16px; border-radius: 8px; font-weight: 650; }
        QPushButton#primary { background: #2854C7; color: white; border: none; min-width: 126px; }
        QPushButton#primary:hover { background: #2149B1; }
        QPushButton#primaryAlt { background: #176C59; color: white; border: none; min-width: 176px; }
        QPushButton#primaryAlt:hover { background: #125848; }
        QPushButton#secondary { background: white; color: #182531; border: 1px solid #C8D2DA; }
        QPushButton#secondary:hover { background: #F7F9FB; border-color: #A8B5C0; }
        QPushButton:disabled { color: #9AA7B2; background: #EDF1F4; border-color: #DDE4EA; }
        QPlainTextEdit#status { background: #F7F9FB; border: 1px solid #DDE4EA; border-radius: 8px; padding: 9px; font-family: Consolas, monospace; font-size: 12px; }
        ''')

    def add_files_dialog(self):
        files, _ = QFileDialog.getOpenFileNames(self, "Add files", "", "Documents (*.ppt *.pptx *.doc *.docx *.xls *.xlsx *.odt *.ods *.odp *.rtf *.pdf);;All files (*.*)")
        self.add_paths(files)

    @Slot(list)
    def add_paths(self, paths):
        existing = {str(p.resolve()).lower() for p in self.file_list.paths()}
        rejected = []
        for raw in paths:
            p = Path(raw)
            if p.is_dir():
                continue
            if not supported_input(p):
                rejected.append(p.name)
                continue
            key = str(p.resolve()).lower()
            if key in existing:
                continue
            item = QListWidgetItem(p.name)
            item.setToolTip(str(p.resolve()))
            item.setData(Qt.ItemDataRole.UserRole, str(p.resolve()))
            self.file_list.addItem(item)
            existing.add(key)
        if rejected:
            self.append_status("Skipped unsupported: " + ", ".join(rejected))

    def remove_selected(self):
        for item in self.file_list.selectedItems():
            self.file_list.takeItem(self.file_list.row(item))

    def file_list_clear(self):
        self.file_list.clear()

    def sync_output_state(self):
        self.output_edit.setEnabled(not self.same_input.isChecked())

    def choose_output(self):
        folder = QFileDialog.getExistingDirectory(self, "Choose output folder")
        if folder:
            self.output_edit.setText(folder)
            self.same_input.setChecked(False)

    def append_status(self, text: str):
        self.status.appendPlainText(text)

    def start_job(self, mode: str):
        files = self.file_list.paths()
        if not files:
            QMessageBox.warning(self, "Eyad PDF", "Drop or add at least one file first.")
            return
        output_dir = None
        if not self.same_input.isChecked():
            raw = self.output_edit.text().strip()
            if not raw:
                QMessageBox.warning(self, "Eyad PDF", "Choose an output folder or enable “same folder as input”.")
                return
            output_dir = Path(raw)
        self.status.clear()
        self.append_status("Engine: " + engine_summary())
        self.set_busy(True)
        self._thread = QThread(self)
        self._worker = JobWorker(mode, files, output_dir, self.same_input.isChecked(), self.merge_name.text())
        self._worker.moveToThread(self._thread)
        self._thread.started.connect(self._worker.run)
        self._worker.log.connect(self.append_status)
        self._worker.done.connect(self.job_done)
        self._worker.failed.connect(self.job_failed)
        self._worker.finished.connect(self._thread.quit)
        self._worker.finished.connect(self._worker.deleteLater)
        self._thread.finished.connect(self._thread.deleteLater)
        self._thread.finished.connect(lambda: self.set_busy(False))
        self._thread.start()

    def set_busy(self, busy: bool):
        self.convert_btn.setDisabled(busy)
        self.merge_btn.setDisabled(busy)

    @Slot(list)
    def job_done(self, outputs):
        if outputs:
            self.append_status("DONE")
            for output in outputs:
                self.append_status(output)
            QMessageBox.information(self, "Eyad PDF", f"Completed successfully.\n\n{len(outputs)} output file(s) verified.")
        else:
            QMessageBox.information(self, "Eyad PDF", "Nothing needed conversion.")

    @Slot(str)
    def job_failed(self, message):
        self.append_status("FAILED: " + message)
        QMessageBox.critical(self, "Conversion failed safely", message)

    def open_output_folder(self):
        folder = None
        if self.same_input.isChecked() and self.file_list.count():
            folder = self.file_list.paths()[0].parent
        elif self.output_edit.text().strip():
            folder = Path(self.output_edit.text().strip())
        if folder and folder.exists():
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(folder)))

def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Eyad PDF")
    app.setOrganizationName("Eyad PDF")
    window = MainWindow()
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
