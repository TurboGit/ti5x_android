/*
    ti5x calculator emulator -- the TI-59 ROM used as a math coprocessor

    A function is computed by the original ROM running a small program
    such as "Deg RCL 00 sin STO 01 R/S": the argument is put in R00, the
    program is run from a snapshot of the CPU taken just before it reads
    R00, and the result is read from R01. The results are then exactly
    the ones of the TI-59, with all the ROM algorithms.

    The ROM is not part of ti5x, it is loaded from files (see RomCpu).

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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class RomMath {

  /* keys as wired in the keyboard matrix (row in the low nibble) */
  private static final int KEY_RST = 0x18;
  private static final int KEY_RS = 0x19;

  /* the keyboard scan loop: the program has stopped */
  private static final int IDLE_LOOP = 0x063C;

  private static final int KEY_HOLD = 3000;
  private static final int MAX_STEPS = 1_000_000;

  /* data registers in RAM: Rnn is RAM[59 - nn] */
  private static final int R00 = 59;
  private static final int R01 = 58;

  private final int[] Rom;
  private final int[][] Constant;
  private final RomCpu Booted;

  private static class Start {
    RomCpu Cpu;     // just before the program reads R00
    int KeyHold;    // instructions left before the R/S key is released
  }

  private final Map<String, Start> Starts = new HashMap<>();

  /* number of ROM instructions run by the last call */
  public long LastSteps;

  public RomMath(InputStream RomDump, InputStream Constants) throws IOException {
    Rom = RomCpu.LoadRom(RomDump);
    Constant = RomCpu.LoadConstants(Constants);
    Booted = new RomCpu(Rom, Constant);
    Settle(Booted);
  }

  private static void Run(RomCpu Cpu, int N) {
    while (N > 0) {
      if (Cpu.Execute() != 0)
        N--;
    }
  }

  /* runs until the calculator has been idle for a while (as the
     reference emulator harness) */

  private static void Settle(RomCpu Cpu) {
    int Idle = 0;
    while (Idle < 60000) {
      if (Cpu.Execute() != 0) {
        if ((Cpu.Flags & RomCpu.FLG_IDLE) != 0)
          Idle++;
        else
          Idle = 0;
      }
    }
  }

  private static void Press(RomCpu Cpu, int Key) {
    Cpu.Key[Key & 15] |= 1 << ((Key >> 4) & 7);
  }

  private static void Release(RomCpu Cpu, int Key) {
    Cpu.Key[Key & 15] &= ~(1 << ((Key >> 4) & 7));
  }

  private static void PressKey(RomCpu Cpu, int Key) {
    Press(Cpu, Key);
    Run(Cpu, KEY_HOLD);
    Release(Cpu, Key);
    Settle(Cpu);
  }

  /* the snapshot of the CPU running Program (opcodes) just before it
     reads R00 */

  private synchronized Start StartOf(int[] Program) {
    final String Name = java.util.Arrays.toString(Program);
    Start S = Starts.get(Name);
    if (S != null)
      return S;

    final RomCpu Cpu = new RomCpu(Booted);
    for (int Addr = 0; Addr < Program.length; Addr++) {
      final int Op = Program[Addr];
      Cpu.Ram[Addr >> 3][(Addr & 7) << 1] = (Op % 10);
      Cpu.Ram[Addr >> 3][((Addr & 7) << 1) + 1] = (Op / 10);
    }
    PressKey(Cpu, KEY_RST);
    Press(Cpu, KEY_RS);
    int Steps = 0;
    while (true) {
      if (Cpu.Execute() != 0) {
        Steps++;
        if (Steps == KEY_HOLD)
          Release(Cpu, KEY_RS);
      }
      if ((Cpu.Flags & RomCpu.FLG_RAM_READ) != 0 && Cpu.RamAddr == R00)
        break;
      if (Steps > MAX_STEPS)
        throw new IllegalStateException("ROM program does not read R00");
    }
    S = new Start();
    S.Cpu = Cpu;
    S.KeyHold = Math.max(0, KEY_HOLD - Steps);
    Starts.put(Name, S);
    return S;
  }

  /* runs Program with X in R00, returns R01 (null if the ROM does not stop) */

  private BigDecimal Call(int[] Program, BigDecimal X) {
    final Start S = StartOf(Program);
    final RomCpu Cpu = new RomCpu(S.Cpu);
    ToRam(X, Cpu.Ram[R00]);

    long Steps = 0;
    boolean Pressed = S.KeyHold > 0;
    while (Cpu.Addr != IDLE_LOOP) {
      if (Cpu.Execute() != 0) {
        Steps++;
        if (Pressed && Steps == S.KeyHold) {
          Release(Cpu, KEY_RS);
          Pressed = false;
        }
      }
      if (Steps > MAX_STEPS)
        return null;
    }
    LastSteps = Steps;
    return FromRam(Cpu.Ram[R01]);
  }

  /* a data register: mantissa digits 15..3, exponent digits 2..1, digit 0
     flags: 2 negative mantissa, 4 negative exponent */

  static void ToRam(BigDecimal X, int[] Reg) {
    java.util.Arrays.fill(Reg, 0);
    if (X.signum() == 0)
      return;
    BigDecimal A = X.abs();
    if (A.precision() > 13)
      A = A.round(new java.math.MathContext(13, java.math.RoundingMode.DOWN));
    final int Exp = A.precision() - 1 - A.scale();
    String M = A.unscaledValue().toString();
    while (M.length() < 13)
      M = M + "0";
    for (int i = 0; i < 13; i++)
      Reg[15 - i] = M.charAt(i) - '0';
    final int E = Math.abs(Exp);
    Reg[2] = (E / 10) % 10;
    Reg[1] = E % 10;
    Reg[0] = (X.signum() < 0 ? 2 : 0) | (Exp < 0 ? 4 : 0);
  }

  static BigDecimal FromRam(int[] Reg) {
    BigInteger M = BigInteger.ZERO;
    for (int i = 15; i >= 3; i--)
      M = M.multiply(BigInteger.TEN).add(BigInteger.valueOf(Reg[i]));
    if (M.signum() == 0)
      return BigDecimal.ZERO;
    int Exp = Reg[2] * 10 + Reg[1];
    if ((Reg[0] & 4) != 0)
      Exp = -Exp;
    if ((Reg[0] & 2) != 0)
      M = M.negate();
    return new BigDecimal(M, 12 - Exp);
  }

  private static int AngleOp(int Angle) {
    switch (Angle) {
      case Number.ANG_RAD:
        return 70;
      case Number.ANG_GRAD:
        return 80;
      default:
        return 60;
    }
  }

  /* sin(X) as the ROM computes it, X in the given angular unit */

  public BigDecimal Sin(BigDecimal X, int Angle) {
    return Call(new int[] {AngleOp(Angle), 43, 0, 38, 42, 1, 91}, X);
  }
}
