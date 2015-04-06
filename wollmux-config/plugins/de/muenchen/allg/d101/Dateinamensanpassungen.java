package de.muenchen.allg.d101;

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.StringBuffer;

public class Dateinamensanpassungen {

	private static final Pattern PROP = Pattern.compile("\\$\\{([^\\}]+)\\}");

	/**
	 * Dieser Funktion kann eine durch Pipe ('|') getrennte Liste mit
	 * Pfaden/Dateinamen übergeben werden, wovon der erste Eintrag dieser Liste
	 * zurückgegeben wird, dessen Pfad-Anteil tatsächlich verfügbar ist.
	 * Innerhalb eines Pfades/Dateinamens kann vor der Verfügbarkeitsprüfung mit
	 * ${<name>} der Wert einer Java-Systemproperty in den Dateinamen eingefügt
	 * werden.
	 */
	public static String verfuegbarenPfadVerwenden(String Filename) {
		String[] paths = Filename.split("\\s*\\|\\s*");
		String first = null;
		for (String p : paths) {

			// alle ${<prop>} durch evaluierten Inhalt ersetzen
			Matcher m = PROP.matcher(p);
			StringBuffer buf = new StringBuffer();
			while (m.find()) {
				String propVal = System.getProperty(m.group(1).trim());
				if (propVal == null)
					propVal = "";
				m.appendReplacement(buf, propVal);
			}
			m.appendTail(buf);

			if (first == null)
				first = buf.toString();

			File f = new File(buf.toString());
			File parent = f.getParentFile();
			if (parent != null && parent.isDirectory())
				return f.toString();
		}
		if (first == null)
			first = paths[0];
		return new File(first).getName();
	}

	/**
	 * Arbeitet wie
	 * {@link Dateinamensanpassungen#verfuegbarenPfadVerwenden(String)} und
	 * nimmt zusätzlich die folgenden LHM-spezifischen Dateinamensanpassungen
	 * vor:
	 * 
	 * a. Substituiert werden ß in ss ä in ae ö in oe ü in ue, Ä in Ae, Ü in Üe,
	 * Ö in Oe
	 * 
	 * b. Alle Sonderzeichen, Satzzeichen etc. sollen durch _ ersetzt werden,
	 * außer dem Punkt vor der Dateiendung (.odt)
	 * 
	 * c. Damit sind im Dateinamen nur noch die Zahlen von 0-9, die Buchstaben
	 * von a-z und A-Z und der Unterstrich _ vorhanden
	 * 
	 * d. Die Länge des Dateinamens wird auf maximal 240 Zeichen (inkl. Pfad)
	 * begrenzt; ist der ermittelte Dateiname länger, so wird er nach 240
	 * Zeichen abgeschnitten (genau genommen wird nach 236 Zeichen abgeschnitten
	 * und dann wird die Endung .odt angehängt).
	 */
	public static String lhmDateinamensanpassung(String Filename) {
		String pfad = verfuegbarenPfadVerwenden(Filename);
		File file = new File(pfad);
		int parentLength = 0;
		if (file.getParent() != null)
			parentLength = file.getParent().length() + 1;

		String name = file.getName();
		String suffix = "";
		int idx = name.lastIndexOf(".");
		if (idx >= 0) {
			suffix = name.substring(idx);
			if (suffix.matches("\\.\\w{3,4}"))
				name = name.substring(0, idx);
			else
				suffix = "";
		}

		name = name.replaceAll("ß", "ss");
		name = name.replaceAll("ä", "ae");
		name = name.replaceAll("ö", "oe");
		name = name.replaceAll("ü", "ue");
		name = name.replaceAll("Ä", "Ae");
		name = name.replaceAll("Ö", "Oe");
		name = name.replaceAll("Ü", "Ue");
		name = name.replaceAll("[^a-zA-Z_0-9]", "_");

		int maxlength = 240 - suffix.length() - parentLength;
		if (name.length() > maxlength)
			name = name.substring(0, maxlength);

		name = name + suffix;

		file = new File(file.getParentFile(), name);
		return file.toString();
	}

	public static void main(String[] args) {
		String fname = lhmDateinamensanpassung(args[0]);
		System.out.println(fname.length() + " : " + fname);
	}
}
