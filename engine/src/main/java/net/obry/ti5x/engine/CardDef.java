/*
    ti5x calculator emulator -- simple label card definition

    Copyright 2026 Pascal Obry <pascal@obry.net>.

    This program is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
    A PARTICULAR PURPOSE. See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package net.obry.ti5x.engine;

/* The text of a simple user label card: a title and a program id (e.g. "ML-12")
   on the second row, then the A' to E' labels and the A to E labels, one per cell.
   Stored as the <carddef> XML used by util/rendercard (style "user", slots
   titleleft, titleright, aprime..eprime and a..e) so that the card can be edited
   again and rendered by util/rendercard as well. */

public class CardDef {
  public static final int NrCells = 5;

  public String Title = "";
  public String Id = "";
  public final String[] Prime = new String[NrCells]; /* A' .. E' */
  public final String[] Base = new String[NrCells]; /* A .. E */

  private static final String[] PrimeSlots =
     {"aprime", "bprime", "cprime", "dprime", "eprime"};
  private static final String[] BaseSlots = {"a", "b", "c", "d", "e"};

  public CardDef() {
    for (int i = 0; i < NrCells; ++i) {
      Prime[i] = "";
      Base[i] = "";
    }
  }

  public boolean IsEmpty() {
    boolean Empty = Title.isEmpty() && Id.isEmpty();
    for (int i = 0; i < NrCells; ++i) {
      Empty = Empty && Prime[i].isEmpty() && Base[i].isEmpty();
    }
    return Empty;
  }

  public static boolean ValidId
     (
        String Id
     ) {
    /* two letters, a dash and two digits like on the TI cards, or nothing */
    return Id.isEmpty() || Id.matches("[A-Z]{2}-[0-9]{2}");
  }

  private static String Escape
     (
        String Text
     ) {
    final StringBuilder Result = new StringBuilder();
    for (int i = 0; i < Text.length(); ++i) {
      final char Ch = Text.charAt(i);
      switch (Ch) {
        case '&':
          Result.append("&amp;");
          break;
        case '<':
          Result.append("&lt;");
          break;
        case '>':
          Result.append("&gt;");
          break;
        case '"':
          Result.append("&quot;");
          break;
        default:
          Result.append(Ch);
          break;
      }
    }
    return Result.toString();
  }

  private static void AddLabel
     (
        StringBuilder Out,
        String Slot,
        String Text
     ) {
    if (!Text.isEmpty()) {
      Out.append("<label slot=\"").append(Slot).append("\">")
         .append(Escape(Text)).append("</label>\n");
    }
  }

  public byte[] ToXml() {
    final StringBuilder Out = new StringBuilder();
    Out.append("<carddef style=\"user\">\n");
    AddLabel(Out, "titleleft", Title);
    AddLabel(Out, "titleright", Id);
    for (int i = 0; i < NrCells; ++i) {
      AddLabel(Out, PrimeSlots[i], Prime[i]);
    }
    for (int i = 0; i < NrCells; ++i) {
      AddLabel(Out, BaseSlots[i], Base[i]);
    }
    Out.append("</carddef>\n");
    try {
      return Out.toString().getBytes("UTF-8");
    } catch (java.io.UnsupportedEncodingException Impossible) {
      throw new RuntimeException(Impossible);
    }
  }

  private static class Unsupported extends org.xml.sax.SAXException {
    Unsupported
       (
          String Message
       ) {
      super(Message);
    }
  }

  private static class Loader extends org.xml.sax.helpers.DefaultHandler {
    final CardDef Result = new CardDef();
    boolean SeenCardDef = false;
    String Slot = null; /* slot of the label being read, null if none */
    final StringBuilder Text = new StringBuilder();

    @Override
    public void startElement
       (
          String uri,
          String localName,
          String qName,
          org.xml.sax.Attributes attributes
       ) throws org.xml.sax.SAXException {
      if (qName.equals("carddef")) {
        SeenCardDef = true;
      } else if (qName.equals("label")) {
        Slot = attributes.getValue("slot");
        Text.setLength(0);
        if (Slot == null)
          throw new Unsupported("label without slot");
      } else if (Slot == null) {
        throw new Unsupported("unexpected element " + qName);
      }
      /* other elements inside a label (button, sub, symbols...) are flattened
         to their text */
    }

    @Override
    public void endElement
       (
          String uri,
          String localName,
          String qName
       ) throws org.xml.sax.SAXException {
      if (qName.equals("label")) {
        final String Value = Text.toString().trim();
        boolean Known = false;
        if (Slot.equals("titleleft")) {
          Result.Title = Value;
          Known = true;
        } else if (Slot.equals("titleright")) {
          Result.Id = Value;
          Known = true;
        }
        for (int i = 0; i < NrCells; ++i) {
          if (Slot.equals(PrimeSlots[i])) {
            Result.Prime[i] = Value;
            Known = true;
          } else if (Slot.equals(BaseSlots[i])) {
            Result.Base[i] = Value;
            Known = true;
          }
        }
        if (!Known)
          throw new Unsupported("unsupported slot " + Slot);
        Slot = null;
      }
    }

    @Override
    public void characters
       (
          char[] ch,
          int start,
          int length
       ) {
      if (Slot != null) {
        Text.append(ch, start, length);
      }
    }
  }

  public static CardDef Parse
     (
        byte[] Xml /* can be null */
     ) {
    /* returns the card defined by Xml, or null if Xml is null, invalid or uses
       a layout that this simple card does not support */
    if (Xml == null)
      return null;
    final Loader Handler = new Loader();
    try {
      javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().parse
         (
            new java.io.ByteArrayInputStream(Xml),
            Handler
         );
    } catch (org.xml.sax.SAXException
             | javax.xml.parsers.ParserConfigurationException
             | java.io.IOException Failed) {
      return null;
    }
    return Handler.SeenCardDef ? Handler.Result : null;
  }
}
