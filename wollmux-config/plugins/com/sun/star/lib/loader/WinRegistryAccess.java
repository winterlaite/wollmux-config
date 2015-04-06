/*
 * Dateiname: WinRegistryAccess.java
 * Projekt  : wollmux-standard-config
 * Funktion : Liest Werte aus der Windows Registry
 * 
 * Copyright (c) 2009 Landeshauptstadt München
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
 * 06.08.2009 | BNK | Erstellung
 * 18.08.2009 | BED | C_PROGRAMME_WOLLMUX_WOLLMUX_CONF wieder in WollMuxFiles;
 *            |     | Dokumentation; zwei getrennte Methoden für HKCU und HKLM
 * 20.07.2010 | LUT | Klasse WollMuxRegistryAccess aus WollMux-Projekt
 *            |     | übernommen und umbenannt in WinRegistryAccess
 * -------------------------------------------------------------------
 *
 * @author Matthias Benkmann (D-III-ITD-D101)
 * 
 */

// Leider ist WinRegKey package protected, also müssen wir uns in diese Package einklinken.
package com.sun.star.lib.loader;

public class WinRegistryAccess
{
  /**
   * Liefert die String-Daten des Werts namens valueName des Registrierungsschlüssels
   * keyName unter der Wurzel root in der Windows-Registry zurück, oder null, falls
   * beim Auslesen ein Fehler auftritt.
   * 
   * @author Matthias Benkmann (D-III-ITD-D101)
   * @author Daniel Benkmann (D-III-ITD-D101)
   * @author Christoph Lutz (D-III-ITD-D101)
   */
  public static String getStringValueFromRegistry(String root, String keyName,
      String valueName)
  {
    String path = null;
    try
    {
      WinRegKey key = new WinRegKey(root, keyName);
      path = key.getStringValue(valueName);
    }
    // Wir fangen Throwable statt Exception, da unter Linux von WinRegKey ein
    // UnsatisfiedLinkError geworfen wird
    catch (Throwable e)
    {}

    return path;
  }
}
