/*
    ti5x calculator emulator -- data exporter context

    Copyright 2021 Pascal Obry <pascal@obry.net>.

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

public class Card {
  final int BANK_MEM = 30;
  final int BANK_PROG = 240;

  private byte[] ProgData;
  private Number[] MemData;
  private int BankNr;
  private int Id;
  private boolean HasMem;
  private boolean HasProg;

  public Card (
      int BankNr
  )
  {
    ProgData = new byte[BANK_PROG];
    MemData = new Number[BANK_MEM];
    this.BankNr = BankNr;
    Id = -1;
    HasMem = false;
    HasProg = false;

    for (int k = 0; k < BANK_PROG; k++) {
      ProgData[k] = 0;
    }

    for (int k = 0; k < BANK_MEM; k++) {
      MemData[k] = new Number(0.0);
    }
  }

  public void SetMem
      (
          Number[] Data
      ) {
    for (int k=0; k<BANK_MEM; k++)
    {
      if (k < Data.length) {
        MemData[k] = Data[k];
      } else {
        MemData[k] = new Number(0.0);
      }
    }

    HasMem = true;
  }

  public void SetProg
      (
          byte[] Data
      ) {
    for (int k=0; k<BANK_PROG; k++)
    {
      if (k < Data.length) {
        ProgData[k] = Data[k];
      } else {
        ProgData[k] = 0;
      }
    }

    HasProg = true;
  }

  public void WriteCard
      (
          Number[] Memory, /* calculator data registers */
          byte[] Program, /* calculator program memory (bank 0) */
          int BankNr,
          int Id
      ) {
    this.BankNr = BankNr;
    this.Id = Id;

    if (HasMem) {
      int idx = 0;
      for (int k = (4 - BankNr) * BANK_MEM; k < (4 - BankNr + 1) * BANK_MEM; k++) {
        if (k < Memory.length) {
          MemData[idx++] = new Number(Memory[k]);
        }
      }
    }

    if(HasProg) {
      System.arraycopy
          (
              Program, (BankNr - 1) * BANK_PROG,
              ProgData, 0,
              BANK_PROG
          );
    }
  }

  public void LoadCard
      (
          Number[] Memory, /* calculator data registers */
          byte[] Program, /* calculator program memory (bank 0) */
          int Part
      )
  {
    if(HasMem && (Part == 0 || Part == 2)) {
      int idx = 0;
      for (int k = (4 - this.BankNr) * BANK_MEM; k < (4 - this.BankNr + 1) * BANK_MEM; k++) {
        if (k < Memory.length) {
          Memory[k] = new Number(MemData[idx++]);
        }
      }
    }

    if(HasProg && (Part == 0 || Part == 1)) {
      System.arraycopy
          (
              ProgData, 0,
              Program, (this.BankNr - 1) * BANK_PROG,
              BANK_PROG
          );
    }
  }
}
