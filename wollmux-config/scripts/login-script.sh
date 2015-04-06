#!/bin/sh
# Dieses Login-Skript legt die $HOME/.wollmux/wollmux.conf an, falls
# sie noch nicht existiert. Es lässt sich über die folgenden Parameter
# konfigurieren:
#   WollMuxURL:    Der Wert dieses Parameters gibt die URL einer zu includenden Datei
#                  an (main.conf in der Standardkonfig). Typischerweise wird diese URL für alle
#                  Benutzergruppen, die vom selben Vorlagenserver
#                  bedient werden (also meist alle Benutzer des selben
#                  Referats) gleich sein.
#   WollMuxBarURL: Der Wert dieses Parameters gibt die URL einer zweiten
#                  zu includenden Datei an. Typischerweise ist dies die
#                  Datei, die die Spezifikation der WollMux-Toolbar und aller über sie
#                  zur Verfügung gestellten Vorlagen enthält 
#                  (wollmuxbar_<gruppe>.conf in der Standardkonfig). Diese URL wird
#                  sich oft auch innerhalb des selben Referats zwischen
#                  verschiedenen Benutzergruppen unterscheiden, damit
#                  diese verschiedene Vorlagen in ihrer WollMux-Toolbar
#                  angeboten bekommen.
#  DEFAULT_CONTEXT: Die URL eines Verzeichnisses, das an verschiedenen
#                   Stellen (siehe Doku) verwendet wird, um relative
#                   URLs zu vervollständigen. In der Standardkonfig wird hier
#                   das Elternverzeichnis der Verzeichnisse conf und vorlagen
#                   eingetragen.
#  AutoUpdate:      "yes"/"ja"/"1"/"true"/"active"/"aktiv"/"on"/"an" oder 
#                   "no"/"nein"/"0"/"false"/"inactive"/"inaktiv"/""
#                   Falls aktiviert, wird die wollmux.conf des Benutzers immer
#                   überschrieben.
#                   Falls deaktiviert, wird die wollmux.conf des Benutzers nur
#                   überschrieben, wenn die erste Zeile das Wort AUTOUPDATE
#                   enthält.
#                   Außer in dem Fall, dass die wollmux.conf "AUTOUPDATE" enthält
#                   wird vor dem Überschreiben grundsätzlich eine Sicherheitskopie
#                   angelegt.

if [ ! -d ~/.wollmux ]
then
  rm -f ~/.wollmux
  mkdir ~/.wollmux
fi

autoupdate=0
test ! -e ~/.wollmux/wollmux.conf && autoupdate=1
case "z$AutoUpdate" in
  zy*|zj*|z1|ztrue|zactiv*|zaktiv*|zY*|zJ*|zTRUE*|zACTIV*|zActiv*|zAktiv*|zAKTIV*|zon|zON|zOn|zAN*|zan*|zAn*) autoupdate=1 ;;
esac
if head -n 1 ~/.wollmux/wollmux.conf 2>/dev/null | grep -q AUTOUPDATE ; then autoupdate=2 ; fi

if [ $autoupdate -ge 1 ]; then
  if [ $autoupdate != 2 -a -e ~/.wollmux/wollmux.conf ]; then 
    mkdir -p ~/.wollmux/old
    mv ~/.wollmux/wollmux.conf ~/.wollmux/old/"wollmux.conf.$(date --iso-8601)"
  fi
  rm -f ~/.wollmux/wollmux.conf
  
  echo "####AUTOUPDATE##### Diese Datei bei jedem Login mit aktuellen Werten neu anlegen" >> ~/.wollmux/wollmux.conf
  test "x$DEFAULT_CONTEXT"  != "x" && echo "DEFAULT_CONTEXT \"$DEFAULT_CONTEXT\""    >> ~/.wollmux/wollmux.conf
  test "x$WollMuxURL"    != "x" && echo "%include \"$WollMuxURL\""    >> ~/.wollmux/wollmux.conf
  test "x$WollMuxBarURL" != "x" && echo "%include \"$WollMuxBarURL\"" >> ~/.wollmux/wollmux.conf
fi
