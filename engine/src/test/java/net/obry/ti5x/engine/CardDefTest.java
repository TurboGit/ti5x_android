/*
    ti5x calculator emulator -- label card definition tests

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class CardDefTest {

  @Test
  void roundTrip() {
    final CardDef Def = new CardDef();
    Def.Title = "R&D <TEST> \"1\"";
    Def.Id = "AB-12";
    Def.Prime[1] = "x'";
    Def.Base[0] = "n";
    Def.Base[4] = "-> GO";
    final CardDef Copy = CardDef.Parse(Def.ToXml());
    assertNotNull(Copy);
    assertEquals(Def.Title, Copy.Title);
    assertEquals(Def.Id, Copy.Id);
    for (int i = 0; i < CardDef.NrCells; ++i) {
      assertEquals(Def.Prime[i], Copy.Prime[i]);
      assertEquals(Def.Base[i], Copy.Base[i]);
    }
    assertFalse(Copy.IsEmpty());
  }

  @Test
  void emptyCard() {
    final CardDef Copy = CardDef.Parse(new CardDef().ToXml());
    assertNotNull(Copy);
    assertTrue(Copy.IsEmpty());
  }

  @Test
  void readsRendercardSource() throws IOException {
    /* a hand-written card of a built-in program, rich text is flattened */
    final CardDef Def = CardDef.Parse
       (
          Files.readAllBytes(Paths.get("../programs/ee19_input_code/card"))
       );
    assertNotNull(Def);
    assertEquals("EE-19", Def.Id);
    assertEquals("n", Def.Base[0]);
    assertEquals("gm", Def.Prime[0]);
  }

  @Test
  void rejectsUnsupported() {
    assertNull(CardDef.Parse(null));
    assertNull(CardDef.Parse("not xml".getBytes()));
    assertNull(CardDef.Parse("<other/>".getBytes()));
    assertNull
       (
          CardDef.Parse
             (
                "<carddef style=\"system\"><label slot=\"primecenter\">X</label></carddef>"
                   .getBytes()
             )
       );
  }

  @Test
  void validId() {
    assertTrue(CardDef.ValidId(""));
    assertTrue(CardDef.ValidId("ML-12"));
    assertFalse(CardDef.ValidId("ml-12"));
    assertFalse(CardDef.ValidId("ML12"));
    assertFalse(CardDef.ValidId("M-123"));
  }
}
