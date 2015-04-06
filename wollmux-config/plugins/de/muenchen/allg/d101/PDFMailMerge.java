/*
 * Filename : PDFMailMerge.java
 * Project  : WollMux-Standard-Config
 * Function : Plugin for WollMux with an additional Printfunction that prints 
 *            into a PDF-document
 * Contact  : wollmux-general@lists.forge.osor.eu 
 * 
 * Copyright (c) 2010 Landeshauptstadt München
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the European Union Public Licence (EUPL), 
 * version 1.0 (or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * European Union Public Licence for more details.
 *
 * You should have received a copy of the European Union Public Licence
 * along with this program. If not, see 
 * http://ec.europa.eu/idabc/en/document/7330
 *
 * Änderungshistorie:
 * Datum      | Wer | Änderungsgrund
 * -------------------------------------------------------------------
 * 13.07.2010 | LUT | Erstellung
 * 11.11.2010 | LUT | Umstellung auf pdfBox
 * 21.12.2010 | ERT | [#6021] Druckfunktion PDFGesamtdokument bricht ab 
 *                    bei über 900 Drucken
 * 18.01.2011 | ERT | [#5556] Sicherheitsabfragen im PDF Gesamtdruck Formular
 * -------------------------------------------------------------------
 *
 * @author Christoph Lutz (D-III-ITD-D101)
 * 
 */
package de.muenchen.allg.d101;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.PDFMergerUtility;

import com.sun.star.beans.PropertyState;
import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertyChangeListener;
import com.sun.star.beans.XPropertySetInfo;
import com.sun.star.beans.XVetoableChangeListener;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XStorable;
import com.sun.star.lang.IllegalArgumentException;
import com.sun.star.lang.NoSuchMethodException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.lib.loader.WinRegistryAccess;
import com.sun.star.text.XTextDocument;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import de.muenchen.allg.itd51.wollmux.XPrintModel;

/**
 * Diese Klasse implementiert eine Druckfunktion für den WollMux, die als Plugin über
 * den WollMux aufgerufen werden kann und die Verkettung mehrerer Ausdrucke in eine
 * PDF-Datei ermöglicht. Dabei wird aus OpenOffice.org heraus jeder Ausdruck als
 * einzelne pdf-Datei exportiert und diese über die Bibliothek pdfBox zusammgefügt.
 * 
 * Die Einbindung der Druckfunktionen kann über die Datei conf/funktionen.conf der
 * WollMux-Konfiguration vorgenommen werden. Im Paket WollMux-Standard-Config ist die
 * Einbindung des Plugins bereits konfiguriert und kann daher als Beispiel für die
 * Einbindung herangezogen werden.
 * 
 * Die Haupt-Druckfuntion heißt mailMerge() und sollte in der WollMux-Konfig unter
 * dem Namen "PDFGesamtdokument" und mit einem geringen ORDER-Wert (z.B. 40)
 * eingebunden. Diese Methode benötigt darüber hinaus eine zweite Druckfunktion
 * mailMergeOutput(), die in der WollMux-Konfiguration unter dem Namen
 * "PDFGesamtdokumentOutput" und mit einem hohen ORDER-Wert (z.B. 200) eingebunden
 * werden muss.
 * 
 * @author Christoph Lutz (D-III-ITD-D101)
 */
public class PDFMailMerge
{
  /**
   * Enthält das Kommando für den PDF-Viewer, der im Druckdialog vorausgewählt sein
   * soll.
   */
  private static final String PDF_VIEWER_DEFAULT_COMMAND = "acroread";

  /**
   * Titel der Fehlerdialoge
   */
  private static final String FEHLER_TITLE = "Fehler beim PDF-Druck";

  /**
   * Name der Output-Druckfunktion, die von mailMerge() benötigt wird.
   */
  private static final String OUTPUT_METHOD_CONFIG_NAME = "PDFGesamtdokumentOutput";

  /**
   * Temporäre Dateien bekommen diesen Präfix im Dateinamen
   */
  private static final String TEMPFILE_PREFIX = "pdfMailMerge";

  private static final String PDFMM_TMP_OUTPUT_DIR =
    "PDFMailMerge_PDFBox_TMP_OUTPUT_DIR";

  private static final String PDFMM_TMP_FILE_COUNT =
    "PDFMailMerge_PDFBox_TMP_FILE_COUNT";

  /**
   * Die Aufgabe dieser Druckfunktion ist es, den Dialog zur Auswahl der Optionen
   * anzuzeigen, den Druck an Druckfunktionen mit höherem ORDER-Wert weiterzuleiten,
   * das PDF-Dokument abzuschließen und den externen PDF-Betrachter zu starten. Der
   * Export der Einzeldokumente (also die finale Druck-Aktion) findet in der Methode
   * mailMergeOutput statt, die in der WollMux-Konfiguration unter dem Namen
   * "PDFGesamtdokumentOutput" definiert sein muss.
   * 
   * @param pmod
   *          Das Print-Model, das der WollMux dieser Druckfunktion übergibt.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static void mailMerge(final XPrintModel pmod)
  {
    // Benötigte Output-Druckfunktion nachladen
    try
    {
      pmod.usePrintFunction(OUTPUT_METHOD_CONFIG_NAME);
    }
    catch (NoSuchMethodException e)
    {
      showInfo(
        FEHLER_TITLE,
        "Die benötigte Druckfunktion '"
          + OUTPUT_METHOD_CONFIG_NAME
          + "' konnte nicht gefunden werden. Bitte verständigen Sie Ihre Systemadministration.",
        null, 80);
      return;
    }

    // Dialog für Optionen des PDF-Gesamtdrucks starten und Werte PDFMM_DUPLEX,
    // PDFMM_OUTPUT_FILE, pdfViewer auswerten
    ParametersDialog pd = new ParametersDialog();
    pd.showDialog();
    if (pd.isCanceled())
    {
      pmod.cancel();
      return;
    }
    String pdfViewer = pd.getPDFViewerCmd();
    File outputFile = pd.getOutputFile();
    if (outputFile == null)
      try
      {
        outputFile = File.createTempFile(TEMPFILE_PREFIX, ".pdf");
      }
      catch (Exception e)
      {
        showInfo(FEHLER_TITLE,
          "Beim Drucken in das pdf-Gesamtdokument ist ein Fehler aufgetreten.", e,
          80);
        pmod.cancel();
        return;
      }

    // Temporäres Ausgabeverzeichnis anlegen für Output-Druckfunktion
    File tmpOutDir;
    try
    {
      tmpOutDir = File.createTempFile(TEMPFILE_PREFIX, null);
      if (!tmpOutDir.delete() || !tmpOutDir.mkdir())
        throw new Exception("Directory " + tmpOutDir + " nicht angelegt.");
    }
    catch (Exception e)
    {
      showInfo(FEHLER_TITLE, "Kann kein temporäres Ausgabeverzeichnis anlegen.", e,
        80);
      pmod.cancel();
      return;
    }
    try
    {
      pmod.setPropertyValue(PDFMM_TMP_OUTPUT_DIR, tmpOutDir.getAbsolutePath());
    }
    catch (Exception e)
    {}

    // Druck weiterleiten an Druckfunktionen mit höherem ORDER-Wert
    pmod.printWithProps();

    // Ausgabedokument abschließen
    PDFMergerUtility merger = new PDFMergerUtility();
    merger.setDestinationFileName(outputFile.getAbsolutePath());
    try
    {
      boolean duplex = pd.isDuplexPrintRequired();
      PDDocument dest = new PDDocument();
      pmod.setPrintProgressMaxValue((short) tmpOutDir.list().length);
      short n = 1;
      pmod.setPrintProgressValue(n++);

      // Files alphabetisch sortieren:
      SortedMap<String, File> sorter = new TreeMap<String, File>();
      for (File file : tmpOutDir.listFiles())
        sorter.put(file.getName(), file);

      // mergen
      for (Entry<String, File> entry : sorter.entrySet())
      {
        File file = entry.getValue();
        PDDocument source = PDDocument.load(file.getAbsolutePath());
        if (duplex && source.getNumberOfPages() % 2 != 0)
          source.addPage(new PDPage(PDPage.PAGE_SIZE_A4));
        merger.appendDocument(dest, source);
        source.close();
        file.delete();
        pmod.setPrintProgressValue(n++);
      }
      dest.save(outputFile.getAbsolutePath());

      // aufräumen
      dest.close();
      tmpOutDir.delete();
    }
    catch (Exception e)
    {
      showInfo(FEHLER_TITLE, "Fehler beim Verbinden der PDF-Dokumente:", e, 80);
      pmod.cancel();
      return;
    }

    // Ausgabedokument anzeigen oder speichern
    String outputFileName = null;
    if (outputFile != null && outputFile.exists())
      outputFileName = outputFile.getAbsolutePath();

    if (pdfViewer != null && outputFileName != null && pmod.isCanceled() == false)
      try
      {
        Runtime.getRuntime().exec(new String[] {
          pdfViewer, outputFileName });
      }
      catch (IOException e)
      {
        showInfo(FEHLER_TITLE,
          "Fehler beim Starten des PDF-Betrachters.\n\nFolgendes Kommando wurde aufgerufen:\n"
            + pdfViewer + " " + outputFileName, e, 80);
        return;
      }
  }

  /**
   * Diese Methode exportiert pmod.getTextDocument() als pdf-Datei im durch
   * PDFMM_TMP_OUTPUT_DIR definierten temporären Verzeichnis. Die erzeugten
   * pdf-Dateien bekommen eindeutige, alphabetisch sortierbare Namen, über die die
   * korrekte Reihenfolge der Ausdrucke festgelegt wird. Kommt es während dem
   * Seriendruck zu Fehlern, so erscheint ein modaler Info-Dialog mit der
   * entsprechenden Fehlermeldung.
   * 
   * @param pmod
   *          Das Print-Model, das der WollMux dieser Druckfunktion übergibt.
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static void mailMergeOutput(XPrintModel pmod)
  {
    if (pmod.isCanceled()) return;

    // tmporäres Output-Verzeichnis holen (muss von Vorgänger-Druckfunktion kommen)
    String tmpPath = (String) getProp(pmod, PDFMM_TMP_OUTPUT_DIR);
    if (tmpPath == null || tmpPath.length() == 0) return;

    // count initialisieren bzw. erhöhen.
    Integer count = (Integer) getProp(pmod, PDFMM_TMP_FILE_COUNT);
    if (count == null) count = -1;
    try
    {
      count++;
      pmod.setPropertyValue(PDFMM_TMP_FILE_COUNT, count);
    }
    catch (Exception e)
    {}
    String countStr = "000000000000000" + count;
    countStr = countStr.substring(countStr.length() - 12);

    try
    {
      // export current OOo-Document as pdf to tempfile
      File tmpFile = new File(tmpPath, countStr + ".pdf");
      XStorable xStorable =
        (XStorable) UnoRuntime.queryInterface(XStorable.class,
          pmod.getTextDocument());
      xStorable.storeToURL(tmpFile.toURI().toString(), new PropertyValue[] {
        new PropertyValue("FilterName", -1, "writer_pdf_Export",
          PropertyState.DIRECT_VALUE),
        new PropertyValue("Overwrite", -1, "true", PropertyState.DIRECT_VALUE) });
    }
    catch (Exception e)
    {
      showInfo(FEHLER_TITLE,
        "Beim Drucken in das pdf-Gesamtdokument ist ein Fehler aufgetreten.", e, 80);
      pmod.cancel();
      return;
    }
  }

  /**
   * Helpermethode: Macht das selbe wie pmod.getPropertyValue(id), Exceptions werden
   * aber ignoriert und statt dessen null zurück geliefert. Insbesondere soll
   * natürlich eine mögliche UnknownPropertyException ignoriert werden, wenn nur
   * getestet wird, ob der Wert schon gesetzt ist.
   */
  private static Object getProp(XPrintModel pmod, String id)
  {
    try
    {
      return pmod.getPropertyValue(id);
    }
    catch (UnknownPropertyException e)
    {}
    catch (WrappedTargetException e)
    {}
    return null;
  }

  /**
   * Diese Methode erzeugt einen nicht modalen Swing-Dialog zur Anzeige von
   * Informationen und kehrt sofort wieder zurück.
   * 
   * @param sTitle
   *          Titelzeile des Dialogs
   * @param sMessage
   *          die Nachricht, die im Dialog angezeigt werden soll.
   * @param t
   *          kann ein Throwable != null enthalten, dessen StackTrace dann zwei
   *          Leerzeilen nach sMessage ausgegeben wird, kann aber auch null sein.
   * @param margin
   *          ist margin > 0 und sind in einer Zeile mehr als margin Zeichen
   *          vorhanden, so wird der Text beim nächsten Leerzeichen umgebrochen.
   */
  private static void showInfo(java.lang.String sTitle, java.lang.String sMessage,
      Throwable t, int margin)
  {
    // StackTrace von e in String umwandeln:
    StringBuffer b = new StringBuffer();
    if (t != null)
    {
      b.append("\n\n" + t.toString() + "\n");
      for (StackTraceElement st : t.getStackTrace())
        b.append(st + "\n");
      sMessage += b;
    }

    try
    {
      // zu lange Strings ab margin Zeichen umbrechen:
      String formattedMessage = "";
      String[] lines = sMessage.split("\n");
      for (int i = 0; i < lines.length; i++)
      {
        String[] words = lines[i].split(" ");
        int chars = 0;
        for (int j = 0; j < words.length; j++)
        {
          String word = words[j];
          if (margin > 0 && chars > 0 && chars + word.length() > margin)
          {
            formattedMessage += "\n";
            chars = 0;
          }
          formattedMessage += word + " ";
          chars += word.length() + 1;
        }
        if (i != lines.length - 1) formattedMessage += "\n";
      }

      // infobox ausgeben:
      final String msg = formattedMessage;
      final String ti = sTitle;
      new Thread(new Runnable()
      {
        public void run()
        {
          JOptionPane pane =
            new JOptionPane(msg, javax.swing.JOptionPane.INFORMATION_MESSAGE);
          JDialog dialog = pane.createDialog(null, ti);
          dialog.setAlwaysOnTop(true);
          dialog.setVisible(true);
        }
      }).start();
    }
    catch (Exception e)
    {}
  }

  /**
   * Beschreibt den Dialog zur Einstellung der Optionen für das PDF-Gesamtdokument
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static class ParametersDialog
  {
    /**
     * Kommando-String, der dem closeActionListener übermittelt wird, wenn der Dialog
     * über den Drucken-Knopf geschlossen wird.
     */
    public static final String CMD_SUBMIT = "submit";

    /**
     * Kommando-String, der dem closeActionListener übermittelt wird, wenn der Dialog
     * über den Abbrechen oder "X"-Knopf geschlossen wird.
     */
    public static final String CMD_CANCEL = "cancel";

    private JDialog dialog;

    private boolean isCanceled = false;

    private ActionListener closeActionListener;

    private JCheckBox duplexCheckBox;

    private JCheckBox outputFileCheckBox;

    private JCheckBox viewerCheckBox;

    private JTextField outputFileTextField;

    private JTextField viewerTextField;

    private JButton outputFileChooserButton;

    private JButton viewerChooserButton;

    private String currentPath;

    private WindowListener myWindowListener = new WindowListener()
    {
      public void windowDeactivated(WindowEvent e)
      {}

      public void windowActivated(WindowEvent e)
      {}

      public void windowDeiconified(WindowEvent e)
      {}

      public void windowIconified(WindowEvent e)
      {}

      public void windowClosed(WindowEvent e)
      {}

      public void windowClosing(WindowEvent e)
      {
        abort(CMD_CANCEL);
      }

      public void windowOpened(WindowEvent e)
      {}
    };

    /**
     * Startet den Dialog und kehrt erst wieder zurück, wenn der Dialog beendet ist;
     * selbstverständlich wird dabei auf die Thread-Safety bezüglich des EDT
     * geachtet.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public void showDialog()
    {
      final boolean[] lock = new boolean[] { true };
      this.closeActionListener = new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          synchronized (lock)
          {
            lock[0] = false;
            lock.notify();
          }
        }
      };
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          createGUI();
        }
      });
      synchronized (lock)
      {
        while (lock[0] == true)
          try
          {
            lock.wait();
          }
          catch (InterruptedException e1)
          {
            isCanceled = true;
            return;
          }
      }
    }

    /**
     * Liefert true, wenn der Dialog mit Abbrechen oder dem X-Button beendet wurde.
     * 
     * @return
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public boolean isCanceled()
    {
      return isCanceled;
    }

    /**
     * Liefert das File unter dem das Ausgabedokument gespeichert werden soll zurück,
     * oder null, wenn die Checkbox "speichern unter..." nicht angekreuzt ist.
     * 
     * @return File-Objekt auf das Ausgabedokument; kann auch null sein, wenn kein
     *         Ausgabedokument definiert wurde.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public File getOutputFile()
    {
      if (outputFileCheckBox.isSelected())
        return new File(outputFileTextField.getText());
      return null;
    }

    /**
     * Liefert den Wert der Checkbox "Leerseiten für Duplexdruck" zurück.
     * 
     * @return true, wenn das Duplexdrucken erwünscht ist, ansonsten false.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public boolean isDuplexPrintRequired()
    {
      return duplexCheckBox.isSelected();
    }

    /**
     * Liefert das Kommando zurück, mit dem der PDF-Viewer aufgerufen werden soll,
     * oder null, wenn nicht.
     * 
     * @return Den Kommandostring für den gewünschten PDF-Viewer oder null, wenn die
     *         zugehörige Option nicht selektiert ist.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    public String getPDFViewerCmd()
    {
      if (viewerCheckBox.isSelected()) return viewerTextField.getText();
      return null;
    }

    private void createGUI()
    {
      dialog = new JDialog();
      dialog.setTitle("PDF--Single");
      dialog.addWindowListener(myWindowListener);
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      dialog.add(panel);

      Box optionBox = Box.createVerticalBox();
      Box vbox = Box.createVerticalBox();
      vbox.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createRaisedBevelBorder(),
        "Document creation settings"));
      optionBox.add(vbox);
      optionBox.add(Box.createVerticalStrut(10));
      Box hbox;

      hbox = Box.createHorizontalBox();
      duplexCheckBox = new JCheckBox("Add blank pages for duplex printing");
      hbox.add(duplexCheckBox);
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);

      hbox = Box.createHorizontalBox();
      hbox.add(Box.createHorizontalStrut(22));
      hbox.add(new JLabel(
        "<html>Add blank pages so all printouts begin with odd numbers.<br/>This will be required for later duplex printing.</html>"));
      hbox.add(Box.createHorizontalStrut(10));
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);
      vbox.add(Box.createVerticalStrut(10));

      hbox = Box.createHorizontalBox();
      outputFileCheckBox = new JCheckBox("Save document as");
      outputFileCheckBox.setSelected(false);
      hbox.add(outputFileCheckBox);
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);

      hbox = Box.createHorizontalBox();
      hbox.add(Box.createHorizontalStrut(22));
      outputFileTextField = new JTextField("");
      outputFileTextField.setEnabled(false);
      hbox.add(outputFileTextField);
      hbox.add(Box.createHorizontalStrut(10));
      outputFileChooserButton = new JButton("...");
      outputFileChooserButton.setEnabled(false);
      outputFileChooserButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          showOutputFilePicker();
        }
      });
      hbox.add(outputFileChooserButton);
      outputFileCheckBox.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent arg0)
        {
          outputFileTextField.setEnabled(outputFileCheckBox.isSelected());
          outputFileChooserButton.setEnabled(outputFileCheckBox.isSelected());
        }
      });
      hbox.add(Box.createHorizontalStrut(10));
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);
      vbox.add(Box.createVerticalStrut(10));

      hbox = Box.createHorizontalBox();
      viewerCheckBox = new JCheckBox("Open document for viewing with");
      viewerCheckBox.setSelected(true);
      hbox.add(viewerCheckBox);
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);

      hbox = Box.createHorizontalBox();
      hbox.add(Box.createHorizontalStrut(22));
      viewerTextField = new JTextField(getDefaultPDFViewer());
      hbox.add(viewerTextField);
      hbox.add(Box.createHorizontalStrut(10));
      viewerChooserButton = new JButton("...");
      viewerChooserButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          showViewerFileChooser();
        }
      });
      viewerCheckBox.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent arg0)
        {
          viewerTextField.setEnabled(viewerCheckBox.isSelected());
          viewerChooserButton.setEnabled(viewerCheckBox.isSelected());
        }
      });
      hbox.add(viewerChooserButton);
      hbox.add(Box.createHorizontalStrut(10));
      hbox.add(Box.createHorizontalGlue());
      vbox.add(hbox);
      vbox.add(Box.createVerticalStrut(10));

      panel.add(optionBox, BorderLayout.LINE_START);

      JButton button;
      hbox = Box.createHorizontalBox();
      button = new JButton(("Cancel"));
      button.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          abort(CMD_CANCEL);
        }
      });
      hbox.add(button);
      hbox.add(Box.createHorizontalGlue());
      button = new JButton(("Print"));
      button.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent e)
        {
          if (outputFileCheckBox.isSelected()
            && (outputFileTextField.getText() == null || outputFileTextField.getText().trim().equals(
              "")))
          {
            showInfo(FEHLER_TITLE, "Geben Sie einen Dateinamen an.", null, 0);
            return;
          }

          if (viewerCheckBox.isSelected()
            && (viewerTextField.getText() == null || viewerTextField.getText().trim().equals(
              "")))
          {
            showInfo(FEHLER_TITLE, "Wählen Sie ein Programm für die Anzeige aus.",
              null, 0);
            return;
          }
          printButtonPressed();
        }
      });
      hbox.add(button);
      panel.add(hbox, BorderLayout.SOUTH);

      dialog.setVisible(false);
      dialog.setAlwaysOnTop(true);
      dialog.pack();
      int frameWidth = dialog.getWidth();
      int frameHeight = dialog.getHeight();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      int x = screenSize.width / 2 - frameWidth / 2;
      int y = screenSize.height / 2 - frameHeight / 2;
      dialog.setLocation(x, y);
      dialog.setResizable(false);
      dialog.setVisible(true);
    }

    /**
     * Liefert den (systemabhängigen) PDF-Viewer, der als Vorbelegung im Dialog
     * angezeigt werden soll; Dabei wird unter Windows in der Registry nach dem
     * Default-Open-Kommando für .pdf Dateien gesucht; wird hier nichts gefunden, so
     * liefert die Methode den konstanten Wert PDF_VIEWER_DEFAULT_COMMAND zurück
     * (insbesondere für Linux geeignet).
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private static String getDefaultPDFViewer()
    {
      String progId = null;
      progId =
        WinRegistryAccess.getStringValueFromRegistry("HKEY_CLASSES_ROOT", ".pdf", "");
      String cmd = null;
      if (progId != null)
        cmd =
          WinRegistryAccess.getStringValueFromRegistry("HKEY_CLASSES_ROOT", ""
            + progId + "\\shell\\open\\command", "");
      if (cmd != null)
      {
        // cmd kann sein z.B. '"C:\Programme...\acrobat.exe" "%1"' oder 'acrobat %1'
        Matcher m = Pattern.compile("(?:\"([^\"]*)\"|([^\\s]*))\\s*").matcher(cmd);
        if (m.find())
        {
          if (m.group(1) != null) return m.group(1);
          if (m.group(2) != null) return m.group(2);
        }
      }
      return PDF_VIEWER_DEFAULT_COMMAND;
    }

    protected void printButtonPressed()
    {
      abort(CMD_SUBMIT);
    }

    protected void abort(String commandStr)
    {
      if (CMD_CANCEL.equals(commandStr)) isCanceled = true;

      /*
       * Wegen folgendem Java Bug (WONTFIX)
       * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4259304 sind die
       * folgenden 3 Zeilen nötig, damit der Dialog gc'ed werden kann. Die Befehle
       * sorgen dafür, dass kein globales Objekt (wie z.B. der
       * Keyboard-Fokus-Manager) indirekt über den JFrame den Dialog kennt.
       */
      if (dialog != null)
      {
        dialog.removeWindowListener(myWindowListener);
        dialog.getContentPane().remove(0);
        dialog.setJMenuBar(null);

        dialog.dispose();
        dialog = null;
      }

      if (closeActionListener != null)
        closeActionListener.actionPerformed(new ActionEvent(this, 0, commandStr));
    }

    /**
     * Startet den FileChooser für die Auswahl des zu speichernden Dokuments und
     * merkt sich dabei das zuletzt ausgewählte Verzeichnis, um beim nächsten Öffnen
     * des Dialogs erneut in diesem Verzeichnis starten zu können.
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private void showOutputFilePicker()
    {
      JFileChooser fc;
      if (currentPath != null)
        fc = new JFileChooser(currentPath);
      else
        fc = new JFileChooser();
      FileFilter filter = new FileFilter()
      {
        public String getDescription()
        {
          return "PDF-Dokumente";
        }

        public boolean accept(File f)
        {
          if (f != null)
          {
            if (f.getName().toLowerCase().endsWith(".pdf")) return true;
            if (f.isDirectory()) return true;
          }
          return false;
        }
      };
      fc.setFileFilter(filter);
      fc.setDialogTitle("Save document as...");
      fc.setMultiSelectionEnabled(false);

      int ret = fc.showSaveDialog(dialog);

      if (ret == JFileChooser.APPROVE_OPTION)
      {
        currentPath = fc.getSelectedFile().getParent();
        String fname = fc.getSelectedFile().getAbsolutePath();
        if (!fname.toLowerCase().endsWith(".pdf")) fname = fname + ".pdf";
        outputFileTextField.setText(fname);
      }
    }

    /**
     * Startet den FileChooser für die Auswahl des PDF-Viewers
     * 
     * @author Christoph Lutz (D-III-ITD-D101)
     */
    private void showViewerFileChooser()
    {
      JFileChooser fc = new JFileChooser("/");
      fc.setDialogTitle("Choose PDF viewer");
      fc.setMultiSelectionEnabled(false);
      int ret = fc.showOpenDialog(dialog);
      if (ret == JFileChooser.APPROVE_OPTION)
        viewerTextField.setText(fc.getSelectedFile().getAbsolutePath());
    }
  }

  /**
   * Testmethode für Whiteboxtests
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static void main(String[] args) throws Exception
  {
    XComponentContext ctx = Bootstrap.bootstrap();
    XMultiComponentFactory xRemoteServiceManager = ctx.getServiceManager();
    XDesktop desktop =
      (XDesktop) UnoRuntime.queryInterface(XDesktop.class,
        xRemoteServiceManager.createInstanceWithContext(
          "com.sun.star.frame.Desktop", ctx));
    XTextDocument doc =
      (XTextDocument) UnoRuntime.queryInterface(XTextDocument.class,
        desktop.getCurrentComponent());

    XPrintModel pmod = new TestPrintModel(doc, 10);
    mailMerge(pmod);

    Thread.sleep(25000);
    System.exit(0);
  }

  /**
   * Für Test- und Debugging-Zwecke benötigt
   * 
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  private static class TestPrintModel implements XPrintModel
  {
    HashMap<String, Object> p = new HashMap<String, Object>();

    boolean c = false;

    XTextDocument doc;

    int count;

    /**
     * @param doc
     *          das TextDocumentModel
     * @param count
     *          gibt an, wie häufig printWithProps() die Methode
     *          mailMergeOutput(this) aufrufen soll.
     */
    private TestPrintModel(XTextDocument doc, int count)
    {
      this.doc = doc;
      this.count = count;
    }

    public void setPropertyValue(String arg0, Object arg1)
        throws UnknownPropertyException, PropertyVetoException,
        IllegalArgumentException, WrappedTargetException
    {
      p.put(arg0, arg1);
    }

    public void removeVetoableChangeListener(String arg0,
        XVetoableChangeListener arg1) throws UnknownPropertyException,
        WrappedTargetException
    {}

    public void removePropertyChangeListener(String arg0,
        XPropertyChangeListener arg1) throws UnknownPropertyException,
        WrappedTargetException
    {}

    public Object getPropertyValue(String arg0) throws UnknownPropertyException,
        WrappedTargetException
    {
      return p.get(arg0);
    }

    public XPropertySetInfo getPropertySetInfo()
    {
      return null;
    }

    public void addVetoableChangeListener(String arg0, XVetoableChangeListener arg1)
        throws UnknownPropertyException, WrappedTargetException
    {}

    public void addPropertyChangeListener(String arg0, XPropertyChangeListener arg1)
        throws UnknownPropertyException, WrappedTargetException
    {}

    public void usePrintFunction(String arg0) throws NoSuchMethodException
    {
    // throw new NoSuchMethodException();
    }

    public void setPrintProgressValue(short arg0)
    {}

    public void setPrintProgressMaxValue(short arg0)
    {}

    public void setPrintBlocksProps(String arg0, boolean arg1, boolean arg2)
    {}

    public void setGroupVisible(String arg0, boolean arg1)
    {}

    public void setFormValue(String arg0, String arg1)
    {}

    public void setDocumentModified(boolean arg0)
    {}

    public void printWithProps()
    {
      for (int i = 0; i < count; ++i)
      {
        System.out.println("-----" + i);
        // doc.getText().setString("Datensatz " + i);
        mailMergeOutput(this);
      }
    }

    public void print(short arg0)
    {}

    public boolean isCanceled()
    {
      return c;
    }

    public XTextDocument getTextDocument()
    {
      return doc;
    }

    public boolean getDocumentModified()
    {
      return true;
    }

    public void collectNonWollMuxFormFields()
    {}

    public void cancel()
    {
      c = true;
    }

	public Object getProp(String arg0, Object arg1) {
		try {
			return getPropertyValue(arg0);
		} catch (Exception e) {
			return arg1;
		}
	}
  }
}
