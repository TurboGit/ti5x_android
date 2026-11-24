/*
    ti5x calculator emulator -- TI-58/59 CPU (TMC0501) emulation

    A Java port of the CPU part of the TI-5x emulator. It runs the original ROM,
    which is not part of ti5x: the ROM dump and the SCOM constants are loaded from files.
    Only what is needed to run the ROM arithmetic is emulated (no
    display, printer, card reader or library module).

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RomCpu {

  // flags
  static final int FLG_IDLE = 0x0001;
  static final int FLG_HOLD = 0x0002;
  static final int FLG_JUMP = 0x0004;
  static final int FLG_RECALL = 0x0010;
  static final int FLG_STORE = 0x0020;
  static final int FLG_RAM_OP = 0x0040;
  static final int FLG_RAM_READ = 0x0080;
  static final int FLG_RAM_WRITE = 0x0100;
  static final int FLG_EXT_VALID = 0x0200;
  static final int FLG_IO_VALID = 0x0400;
  static final int FLG_COND = 0x0800;
  static final int FLG_DISP = 0x1000;
  static final int FLG_BUSY = 0x8000;

  static final int KR_BIT = 4;

  private static final int ALU_ADD = 0;
  private static final int ALU_SHL = 1;
  private static final int ALU_SUB = 2;
  private static final int ALU_SHR = 3;

  /* mask: start, end, cpos, cval (start -1: no digit) */
  private static final int[][] MASK = {
     {-1, 0, 0, 0},
     {0, 15, 0, 0},     // ALL
     {0, 0, 0, 0},      // DPT
     {0, 0, 0, 1},      // DPT 1
     {0, 0, 0, 0xC},    // DPT C
     {3, 3, 3, 1},      // LLSD 1
     {1, 2, 1, 0},      // EXP
     {1, 2, 1, 1},      // EXP 1
     {-1, 0, 0, 0},
     {3, 15, 3, 0},     // MANT
     {-1, 0, 0, 0},
     {3, 15, 3, 5},     // MLSD 5
     {1, 15, 1, 0},     // MAEX
     {1, 15, 3, 1},     // MLSD 1
     {1, 15, 15, 1},    // MMSD 1
     {1, 15, 1, 1}      // MAEX 1
  };

  // ROM and constants (shared, read only)
  final int[] Rom;
  final int[][] Constant;

  // registers
  int[] A = new int[16], B = new int[16], C = new int[16], D = new int[16], E = new int[16];
  int[] Key = new int[16];
  int KR, SR, fA, fB;
  int Ext;
  int Preg;
  int[] Sout = new int[16];
  int Flags;
  int R5;
  int Digit;
  int Addr;
  int RegAddr;
  int RamAddr, RamOp;

  /* the last RAM register written (-1: none) */
  int LastRamWrite = -1;

  int[][] Scom = new int[16][16];

  /* TI-59 (true) or TI-58 */
  final boolean Ti59;
  int[][] Ram = new int[120][16];

  RomCpu(int[] Rom, int[][] Constant, boolean Ti59) {
    this.Rom = Rom;
    this.Constant = Constant;
    this.Ti59 = Ti59;
    Reset();
  }

  /* the CPU state, to restart from a snapshot */

  RomCpu(RomCpu From) {
    this.Rom = From.Rom;
    this.Constant = From.Constant;
    this.Ti59 = From.Ti59;
    CopyFrom(From);
  }

  void CopyFrom(RomCpu From) {
    A = From.A.clone(); B = From.B.clone(); C = From.C.clone();
    D = From.D.clone(); E = From.E.clone();
    Key = From.Key.clone();
    KR = From.KR; SR = From.SR; fA = From.fA; fB = From.fB;
    Ext = From.Ext; Preg = From.Preg;
    Sout = From.Sout.clone();
    SoutDirty = true;
    Flags = From.Flags; R5 = From.R5; Digit = From.Digit; Addr = From.Addr;
    RegAddr = From.RegAddr; RamAddr = From.RamAddr; RamOp = From.RamOp;
    Scom = new int[16][];
    for (int i = 0; i < 16; i++)
      Scom[i] = From.Scom[i].clone();
    Ram = new int[120][];
    for (int i = 0; i < 120; i++)
      Ram[i] = From.Ram[i].clone();
  }

  void Reset() {
    A = new int[16]; B = new int[16]; C = new int[16]; D = new int[16]; E = new int[16];
    Key = new int[16];
    KR = SR = fA = fB = Ext = Preg = 0;
    Sout = new int[16];
    R5 = Digit = Addr = RegAddr = RamAddr = RamOp = 0;
    Flags = FLG_COND | FLG_DISP;
    if (Ti59)
      // TI-59: D10-KR card switch normally closed
      Key[10] |= 1 << KR_BIT;
    else
      // TI-58: D7-KR diode
      Key[7] |= 1 << KR_BIT;
  }

  /* ROM dump: lines "AAAA: XXXX XXXX ..." (hexadecimal) */

  static int[] LoadRom(InputStream In) throws IOException {
    final int[] Rom = new int[0x2000];
    try (BufferedReader R = new BufferedReader(new InputStreamReader(In))) {
      String Line;
      while ((Line = R.readLine()) != null) {
        final int Colon = Line.indexOf(':');
        if (Colon <= 0)
          continue;
        int Address;
        try {
          Address = Integer.parseInt(Line.substring(0, Colon).trim(), 16);
        } catch (NumberFormatException Bad) {
          continue;
        }
        for (String Word : Line.substring(Colon + 1).trim().split("\\s+")) {
          if (!Word.isEmpty())
            Rom[Address++] = Integer.parseInt(Word, 16);
        }
      }
    }
    return Rom;
  }

  /* SCOM constants: the 64 x 16 hexadecimal values of Const.c */

  static int[][] LoadConstants(InputStream In) throws IOException {
    final List<Integer> Values = new ArrayList<>();
    final Pattern Hex = Pattern.compile("0x([0-9a-fA-F]+)");
    try (BufferedReader R = new BufferedReader(new InputStreamReader(In))) {
      String Line;
      while ((Line = R.readLine()) != null) {
        final int Comment = Line.indexOf("//");
        final Matcher M = Hex.matcher(Comment >= 0 ? Line.substring(0, Comment) : Line);
        while (M.find())
          Values.add(Integer.parseInt(M.group(1), 16));
      }
    }
    final int[][] Constant = new int[64][16];
    for (int i = 0; i < 64 * 16 && i < Values.size(); i++)
      Constant[i / 16][i % 16] = Values.get(i);
    return Constant;
  }

  /* Sout has nonzero digits */
  private boolean SoutDirty = false;

  private void ClearSout() {
    if (SoutDirty) {
      java.util.Arrays.fill(Sout, 0);
      SoutDirty = false;
    }
  }

  /* the ALU on 16 BCD digits, as emul.c (C unsigned char arithmetic).

     All the digits go to Sout (the IO bus), but Sout is only used when
     the result goes to IO or is stored (SCOM or RAM): else only the
     digits of the mask are computed, the carry starting at the first one. */

  private void Alu(int[] Dst, int[] SrcX, int[] SrcY, int[] Mask, int Op) {
    final int Start = Mask[0], End = Mask[1], CPos = Mask[2], CVal = Mask[3];
    final boolean FullOut = (Flags & (FLG_IO_VALID | FLG_STORE | FLG_RAM_OP | FLG_RAM_WRITE)) != 0;
    final int From, To;
    if (FullOut) {
      From = 0;
      To = 15;
      SoutDirty = true;
    } else {
      if (Start < 0)
        return;
      MaskAlu(Dst, SrcX, SrcY, Start, End, CPos, CVal, Op);
      return;
    }
    final boolean Subtract = Op >= ALU_SUB;
    int Carry = 0;
    int Shl = 0;
    for (int i = From; i <= To; i++) {
      int Sum = 0, Shr;
      if (i == Start)
        Shl = Carry = 0;
      if (SrcY != null)
        Sum = SrcY[i];
      if (i == CPos)
        Sum |= CVal;
      Shr = Sum;
      Sum = (Sum + Carry) & 0xFF;
      if (Subtract)
        Sum = (-Sum) & 0xFF;
      if (SrcX != null) {
        Sum = (Sum + SrcX[i]) & 0xFF;
        Shr |= SrcX[i];
      }
      if (FullOut)
        Sout[i] = Sum & 0x0F;
      if (i == 0) {
        Carry = (Sum >= 0x10) ? 1 : 0;
        if (Carry != 0)
          Sum &= 0x0F;
      } else {
        Carry = (Sum >= 10) ? 1 : 0;
        if (Carry != 0) {
          if (Subtract)
            Sum = (Sum + 10) & 0xFF;
          else
            Sum = (Sum - 10) & 0xFF;
        }
      }
      if (Start >= 0 && i >= Start && i <= End) {
        if (i == Start)
          R5 = Sum;
        if (Dst != null) {
          if (Op == ALU_SHL)
            Dst[i] = Shl;
          else if (Op == ALU_SHR) {
            if (i > Start)
              Dst[i - 1] = Shr;
            if (i == End)
              Dst[i] = 0;
          } else
            Dst[i] = Sum;
          Shl = Sum;
        }
        if (i == End && (Op & ALU_SHL) == 0 && Carry != 0)
          Flags &= ~FLG_COND;
      }
    }
  }

  /* the ALU on the digits of the mask only (Sout not needed) */

  private void MaskAlu(int[] Dst, int[] SrcX, int[] SrcY, int Start, int End, int CPos, int CVal, int Op) {
    final boolean Subtract = Op >= ALU_SUB;
    int Carry = 0;
    int Shl = 0;
    for (int i = Start; i <= End; i++) {
      int Y = SrcY != null ? SrcY[i] : 0;
      if (i == CPos)
        Y |= CVal;
      final int X = SrcX != null ? SrcX[i] : 0;
      int Sum = (Y + Carry) & 0xFF;
      if (Subtract)
        Sum = (-Sum) & 0xFF;
      Sum = (Sum + X) & 0xFF;
      if (i == 0) {
        Carry = (Sum >= 0x10) ? 1 : 0;
        if (Carry != 0)
          Sum &= 0x0F;
      } else if (Sum >= 10) {
        Carry = 1;
        Sum = (Subtract ? Sum + 10 : Sum - 10) & 0xFF;
      } else {
        Carry = 0;
      }
      if (i == Start)
        R5 = Sum;
      if (Dst != null) {
        if (Op == ALU_SHL) {
          Dst[i] = Shl;
        } else if (Op == ALU_SHR) {
          if (i > Start)
            Dst[i - 1] = Y | X;
          if (i == End)
            Dst[i] = 0;
        } else {
          Dst[i] = Sum;
        }
        Shl = Sum;
      }
    }
    if ((Op & ALU_SHL) == 0 && Carry != 0)
      Flags &= ~FLG_COND;
  }

  private static void Xch(int[] S1, int[] S2, int[] Mask) {
    if (Mask[0] < 0)
      return;
    for (int i = Mask[0]; i <= Mask[1]; i++) {
      final int T = S1[i];
      S1[i] = S2[i];
      S2[i] = T;
    }
  }

  private int[] Const() {
    return Constant[((KR >> 5) & 0x78) | ((KR >> 4) & 0x07)];
  }

  /* instruction cycles skipped by the last Execute (WAIT) */
  private int Skipped = 0;

  /* executes the next instruction, returns the number of instruction
     cycles it took as counted by the reference emulator (0 for a PREG
     jump) */

  int Step() {
    Skipped = 0;
    final int Counted = Execute() != 0 ? 1 : 0;
    return Counted + Skipped;
  }

  /* executes the instruction at Addr: 0 for a PREG jump, else nonzero */

  int Execute() {
    final int Opcode = Rom[Addr];

    if ((Preg & 0x1) != 0) {
      Addr = (Preg >> 3) & 0x1FFF;
      Preg = 0;
      return 0;
    }
    if (Digit != 0)
      Digit--;
    else
      Digit = 15;
    Flags &= ~FLG_HOLD;
    if ((KR & 0x2) != 0) {
      Preg = ((KR >> 1) | (KR << 15)) & 0xFFFF;
      KR &= ~0x2;
    }
    if ((Flags & FLG_EXT_VALID) != 0)
      Flags &= ~FLG_EXT_VALID;
    else
      Ext = 0;
    if ((Flags & FLG_IO_VALID) != 0)
      Flags &= ~FLG_IO_VALID;
    else
      ClearSout();

    if ((Opcode & 0x1000) != 0) {
      // jump
      Flags |= FLG_JUMP;
      if (((Flags ^ Opcode) & FLG_COND) == 0) {
        final int Offs = (Opcode >> 1) & 0x3FF;
        if ((Opcode & 0x0001) != 0)
          Addr = (Addr - Offs) & 0xFFFF;
        else
          Addr = (Addr + Offs) & 0xFFFF;
      } else
        Addr = (Addr + 1) & 0xFFFF;
      return 2;
    }
    if ((Flags & FLG_JUMP) != 0) {
      Flags &= ~FLG_JUMP;
      Flags |= FLG_COND;
    }

    switch (Opcode & 0x0F00) {
      case 0x0000: {
        // flag operations
        final int Bit = (Opcode >> 4) & 0x000F;
        final int Mask = 1 << Bit;
        switch (Opcode & 0x000F) {
          case 0x0: if ((fA & Mask) != 0) Flags &= ~FLG_COND; break;
          case 0x1: fA |= Mask; break;
          case 0x2: fA &= ~Mask; break;
          case 0x3: fA ^= Mask; break;
          case 0x4:
            if (((fA ^ fB) & Mask) != 0) {
              fA ^= Mask;
              fB ^= Mask;
            }
            break;
          case 0x5: KR |= Mask; break;
          case 0x6: if (((fA ^ fB) & Mask) != 0) fA ^= Mask; break;
          case 0x7: fA = (fA & ~0x001E) | ((R5 & 0x000F) << 1); break;
          case 0x8: if ((fB & Mask) != 0) Flags &= ~FLG_COND; break;
          case 0x9: fB |= Mask; break;
          case 0xA: fB &= ~Mask; break;
          case 0xB: fB ^= Mask; break;
          case 0xC: if (((fA ^ fB) & Mask) == 0) Flags &= ~FLG_COND; break;
          case 0xD: KR &= ~Mask; break;
          case 0xE: if (((fA ^ fB) & Mask) != 0) fB ^= Mask; break;
          case 0xF: fB = (fB & ~0x001E) | ((R5 & 0x000F) << 1); break;
        }
        break;
      }
      case 0x0800: {
        // keyboard
        int Mask = (((Opcode & 0x07) | ((Opcode >> 1) & 0x78)) ^ 0x7F) & Key[Digit];
        if ((Mask & (Mask - 1)) != 0)
          Mask = 0;
        if ((Opcode & 0x0008) == 0) {
          if ((Key[Digit] & Mask) != 0) {
            int Bit = 0;
            while ((Mask & 1) == 0) {
              Bit++;
              Mask >>= 1;
            }
            Flags &= ~FLG_COND;
            KR = ((Digit << 4) | ((Bit << 8) & 0x0700)) & 0xFFFF;
          } else if (Digit != 0) {
            Flags |= FLG_HOLD;
            return 11;
          }
        } else {
          if ((Key[Digit] & Mask) != 0)
            Flags &= ~FLG_COND;
        }
        break;
      }
      case 0x0A00:
        switch (Opcode & 0x000F) {
          case 0x0: {
            final int Wanted = (Opcode >> 4) & 0x000F;
            if (Digit != Wanted) {
              if (Preg != 0 || (KR & 0x2) != 0) {
                Flags |= FLG_HOLD;
                return 12;
              }
              /* the instruction would be run again until the digit
                 counter is Wanted, nothing else changes meanwhile: skip
                 these cycles (they are counted, see Step) */
              Skipped += (Digit - Wanted) & 15;
              Digit = Wanted;
            }
            break;
          }
          case 0x1: Flags &= ~FLG_IDLE; break;
          case 0x2: fA = 0; break;
          case 0x3: break;
          case 0x4:
            KR = (KR + 0x0010) & 0xFFFF;
            if ((KR & 0xFFF0) == 0)
              KR ^= 0x0001;
            break;
          case 0x5:
            if ((KR & (1 << ((Opcode >> 4) & 0x000F))) != 0)
              Flags &= ~FLG_COND;
            break;
          case 0x6:
            if ((Opcode & 0x0010) != 0)
              R5 = (fB >> 1) & 0x000F;
            else
              R5 = (fA >> 1) & 0x000F;
            break;
          case 0x7: R5 = (Opcode >> 4) & 0x000F; break;
          case 0x8:
            switch (Opcode & 0x00F0) {
              case 0x0000: R5 = (KR >> 4) & 0x000F; break;
              case 0x0010: KR = (KR & ~0x00F0) | (R5 << 4); break;
              case 0x00F0: Flags |= FLG_RAM_OP; break;
              default: break; // card and printer: not emulated
            }
            break;
          case 0x9: Flags |= FLG_IDLE; break;
          case 0xA: fB = 0; break;
          case 0xB:
            if ((Key[Digit] & (1 << KR_BIT)) != 0 || (Flags & FLG_BUSY) != 0)
              Flags &= ~(FLG_COND | FLG_BUSY);
            break;
          case 0xC: KR = (KR & 0x000F) | Ext; break;
          case 0xD: {
            final int T = KR;
            KR = SR;
            SR = T;
            break;
          }
          case 0xE: break; // library module: not emulated
          case 0xF:
            switch (Opcode & 0x00F0) {
              case 0x0000:
                Flags |= FLG_STORE;
                RegAddr = Sout[0];
                break;
              case 0x0010:
                Flags |= FLG_RECALL;
                RegAddr = Sout[0];
                break;
            }
            break;
        }
        break;
      default: {
        // ALU operations
        final int[] Mask = MASK[(Opcode >> 8) & 0x0F];
        int[] Dst;
        switch (Opcode & 0x07) {
          case 0: Dst = A; break;
          case 3: Dst = B; break;
          case 4: Dst = C; break;
          case 6: Dst = D; break;
          default: Dst = null; break;
        }
        if ((Opcode & 0x07) == 0x01)
          Flags |= FLG_IO_VALID;
        final int Sel = (Opcode >> 3) & 0x1F;
        switch (Opcode & 0x00F8) {
          case 0x00C0: Alu(Dst, A, Const(), Mask, ALU_ADD); break;
          case 0x00C8: Alu(Dst, A, Const(), Mask, ALU_SUB); break;
          case 0x00D0:
            if ((Flags & FLG_RECALL) != 0) {
              Flags &= ~FLG_RECALL;
              Alu(Dst, null, Scom[RegAddr], Mask, ALU_ADD);
            } else if ((Flags & FLG_RAM_READ) != 0 && (Ti59 || RamAddr < 60)) {
              Flags &= ~FLG_RAM_READ;
              Alu(Dst, null, Ram[RamAddr], Mask, ALU_ADD);
            } else {
              Alu(Dst, null, null, Mask, ALU_ADD);
            }
            break;
          case 0x00D8: Alu(Dst, null, null, Mask, ALU_SUB); break;
          case 0x00E0: Alu(Dst, C, Const(), Mask, ALU_ADD); break;
          case 0x00E8: Alu(Dst, C, Const(), Mask, ALU_SUB); break;
          case 0x00F0:
          case 0x00F8:
            if (Dst != null && Mask[0] >= 0) {
              for (int i = Mask[0] + 1; i <= Mask[1]; i++)
                Dst[i] = 0;
              Dst[Mask[2]] = Mask[3];
              Dst[Mask[0]] = R5;
              if ((Opcode & 0x0008) == 0)
                Alu(Dst, null, Dst, Mask, ALU_ADD);
              else
                Alu(Dst, null, Dst, Mask, ALU_SUB);
            }
            break;
          default: {
            int[] X = null, Y = null;
            int Op;
            switch (Sel) {
              case 0:  X = A; Op = ALU_ADD; break;
              case 1:  X = A; Op = ALU_SUB; break;
              case 2:  Y = B; Op = ALU_ADD; break;
              case 3:  Y = B; Op = ALU_SUB; break;
              case 4:  X = C; Op = ALU_ADD; break;
              case 5:  X = C; Op = ALU_SUB; break;
              case 6:  Y = D; Op = ALU_ADD; break;
              case 7:  Y = D; Op = ALU_SUB; break;
              case 8:  X = A; Op = ALU_SHL; break;
              case 9:  X = A; Op = ALU_SHR; break;
              case 10: Y = B; Op = ALU_SHL; break;
              case 11: Y = B; Op = ALU_SHR; break;
              case 12: X = C; Op = ALU_SHL; break;
              case 13: X = C; Op = ALU_SHR; break;
              case 14: Y = D; Op = ALU_SHL; break;
              case 15: Y = D; Op = ALU_SHR; break;
              case 16: X = A; Y = B; Op = ALU_ADD; break;
              case 17: X = A; Y = B; Op = ALU_SUB; break;
              case 18: X = C; Y = B; Op = ALU_ADD; break;
              case 19: X = C; Y = B; Op = ALU_SUB; break;
              case 20: X = C; Y = D; Op = ALU_ADD; break;
              case 21: X = C; Y = D; Op = ALU_SUB; break;
              case 22: X = A; Y = D; Op = ALU_ADD; break;
              case 23: X = A; Y = D; Op = ALU_SUB; break;
              default: Op = ALU_ADD; break; // not reached (special cases above)
            }
            Alu(Dst, X, Y, Mask, Op);
            break;
          }
        }
        switch (Opcode & 0x0007) {
          case 2: Xch(A, B, Mask); break;
          case 5: Xch(C, D, Mask); break;
          case 7: Xch(A, E, Mask); break;
        }
        if ((Flags & FLG_STORE) != 0) {
          Flags &= ~FLG_STORE;
          System.arraycopy(Sout, 0, Scom[RegAddr], 0, 16);
        }
        if ((Flags & FLG_RAM_OP) != 0) {
          Flags &= ~FLG_RAM_OP;
          RamOp = Sout[0];
          RamAddr = Sout[3] * 10 + Sout[2];
          if (RamOp == 2) {
            java.util.Arrays.fill(Ram[RamAddr], 0);
          } else if (RamOp == 4) {
            for (int i = 0; i < 10 && RamAddr + i < 120; i++)
              java.util.Arrays.fill(Ram[RamAddr + i], 0);
          } else if (RamOp == 1) {
            Flags |= FLG_RAM_WRITE;
          } else if (RamOp == 0) {
            Flags |= FLG_RAM_READ;
          }
        } else if ((Flags & FLG_RAM_WRITE) != 0) {
          Flags &= ~FLG_RAM_WRITE;
          System.arraycopy(Sout, 0, Ram[RamAddr], 0, 16);
          LastRamWrite = RamAddr;
        }
        if ((Flags & FLG_IO_VALID) == 0)
          ClearSout();
        break;
      }
    }
    Addr = (Addr + 1) & 0xFFFF;
    return 1;
  }
}
