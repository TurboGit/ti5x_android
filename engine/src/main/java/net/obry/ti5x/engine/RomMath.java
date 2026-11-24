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

  /* how long a key is held in KeyCall: long enough for the keyboard scan
     to see it */
  static int KeyTap = Integer.getInteger("ti5x.keytap", 200);
  private static final int MAX_STEPS = 1_000_000;

  /* data registers in RAM (Rnn is RAM[119 - nn] on a TI-59 with the
     default partition) used to pass the arguments (R00, R02) and the
     results (R01, R03) */
  static final int[] IN = {119, 117};
  static final int[] OUT = {118, 116};

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
    Booted = new RomCpu(Rom, Constant, true);
    Settle(Booted);
  }

  private static void Run(RomCpu Cpu, int N) {
    while (N > 0)
      N -= Cpu.Step();
  }

  /* runs until the calculator has been idle for a while (as the
     reference emulator harness) */

  private static void Settle(RomCpu Cpu) {
    int Idle = 0;
    while (Idle < 60000) {
      final int N = Cpu.Step();
      if (N != 0) {
        if ((Cpu.Flags & RomCpu.FLG_IDLE) != 0)
          Idle += N;
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
     reads its first argument (R00 or R02) */

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
    boolean Pressed = true;
    while (true) {
      Steps += Cpu.Step();
      if (Pressed && Steps >= KEY_HOLD) {
        Release(Cpu, KEY_RS);
        Pressed = false;
      }
      if ((Cpu.Flags & RomCpu.FLG_RAM_READ) != 0 && (Cpu.RamAddr == IN[0] || Cpu.RamAddr == IN[1]))
        break;
      if (Steps > MAX_STEPS)
        throw new IllegalStateException("ROM program does not read its argument");
    }
    S = new Start();
    S.Cpu = Cpu;
    S.KeyHold = Math.max(0, KEY_HOLD - Steps);
    Starts.put(Name, S);
    return S;
  }


  /* the CPU when the last call stopped */
  RomCpu LastCpu;

  /* the calculator was in error (flashing display) after the last call */
  public boolean LastError;

  /* error flag of the ROM */
  private static final int FB_ERROR = 0x0200;

  /* runs Program with Args in R00 (and R02), returns R01 (and R03), null
     if the ROM does not stop */

  BigDecimal[] Call(int[] Program, int Results, BigDecimal... Args) {
    final Start S = StartOf(Program);
    final RomCpu Cpu = new RomCpu(S.Cpu);
    for (int i = 0; i < Args.length; i++)
      ToRam(Args[i], Cpu.Ram[IN[i]]);

    long Steps = 0;
    boolean Pressed = S.KeyHold > 0;
    final int Last = OUT[Results - 1];
    Cpu.LastRamWrite = -1;
    /* done when the last result is stored, or when the program stops
       (error) */
    while (Cpu.Addr != IDLE_LOOP && Cpu.LastRamWrite != Last) {
      Steps += Cpu.Step();
      if (Pressed && Steps >= S.KeyHold) {
        Release(Cpu, KEY_RS);
        Pressed = false;
      }
      if (Steps > MAX_STEPS) {
        LastError = true;
        return null;
      }
    }
    LastSteps = Steps;
    LastCpu = Cpu;
    LastError = (Cpu.fB & FB_ERROR) != 0;
    final BigDecimal[] R = new BigDecimal[Results];
    for (int i = 0; i < Results; i++)
      R[i] = FromRam(Cpu.Ram[OUT[i]]);
    return R;
  }

  private BigDecimal Call(int[] Program, BigDecimal X) {
    final BigDecimal[] R = Call(Program, 1, X);
    return R == null ? null : R[0];
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

  /* key codes of the program steps */
  private static final int OP_INV = 22, OP_LNX = 23, OP_LOG = 28, OP_TAN = 30,
     OP_SIN = 38, OP_COS = 39, OP_STO = 42, OP_RCL = 43, OP_YX = 45,
     OP_EQ = 95, OP_RS = 91;

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

  /* Mode RCL 00 [INV] Op STO 01 R/S */

  private BigDecimal Unary(int Angle, boolean Inv, int Op, BigDecimal X) {
    final int[] P = Inv
       ? new int[] {AngleOp(Angle), OP_RCL, 0, OP_INV, Op, OP_STO, 1, OP_RS}
       : new int[] {AngleOp(Angle), OP_RCL, 0, Op, OP_STO, 1, OP_RS};
    final BigDecimal[] R = Call(P, 1, X);
    return R == null ? null : R[0];
  }

  /* the functions as the ROM computes them, null if the ROM did not stop;
     LastError tells if the calculator was in error */

  public BigDecimal Sin(BigDecimal X, int Angle) { return Unary(Angle, false, OP_SIN, X); }
  public BigDecimal Cos(BigDecimal X, int Angle) { return Unary(Angle, false, OP_COS, X); }
  public BigDecimal Tan(BigDecimal X, int Angle) { return Unary(Angle, false, OP_TAN, X); }
  public BigDecimal Asin(BigDecimal X, int Angle) { return Unary(Angle, true, OP_SIN, X); }
  public BigDecimal Acos(BigDecimal X, int Angle) { return Unary(Angle, true, OP_COS, X); }
  public BigDecimal Atan(BigDecimal X, int Angle) { return Unary(Angle, true, OP_TAN, X); }
  public BigDecimal Ln(BigDecimal X) { return Unary(Number.ANG_DEG, false, OP_LNX, X); }
  public BigDecimal Exp(BigDecimal X) { return Unary(Number.ANG_DEG, true, OP_LNX, X); }
  public BigDecimal Log(BigDecimal X) { return Unary(Number.ANG_DEG, false, OP_LOG, X); }
  public BigDecimal Exp10(BigDecimal X) { return Unary(Number.ANG_DEG, true, OP_LOG, X); }

  /* Y^X and the X root of Y: RCL 00 [INV] y^x RCL 02 = STO 01 R/S */

  private BigDecimal Binary(boolean Inv, BigDecimal Y, BigDecimal X) {
    final int[] P = Inv
       ? new int[] {60, OP_RCL, 0, OP_INV, OP_YX, OP_RCL, 2, OP_EQ, OP_STO, 1, OP_RS}
       : new int[] {60, OP_RCL, 0, OP_YX, OP_RCL, 2, OP_EQ, OP_STO, 1, OP_RS};
    final BigDecimal[] R = Call(P, 1, Y, X);
    return R == null ? null : R[0];
  }

  public BigDecimal Pow(BigDecimal Y, BigDecimal X) { return Binary(false, Y, X); }
  public BigDecimal Root(BigDecimal Y, BigDecimal X) { return Binary(true, Y, X); }

  /* key matrix codes (row in the low nibble) */
  private static final int K_2ND = 0x12, K_INV = 0x22, K_XT = 0x23, K_STO = 0x24,
     K_RCL = 0x34, K_MUL = 0x66, K_SUB = 0x67, K_ADD = 0x68;
  private static final int[] K_DIGIT = {0x29, 0x28, 0x38, 0x58, 0x27, 0x37, 0x57, 0x26, 0x36, 0x56};

  /* runs the key sequence from the idle calculator with Args in R00 (and
     R02), returns R01 (and R03): for the functions that the ROM does
     not run in a program in the reference emulator (P->R) */

  BigDecimal[] KeyCall(int[] Keys, int Results, BigDecimal... Args) {
    final RomCpu Cpu = new RomCpu(Booted);
    for (int i = 0; i < Args.length; i++)
      ToRam(Args[i], Cpu.Ram[IN[i]]);
    long Steps = 0;
    for (int Key : Keys) {
      Press(Cpu, Key);
      int Hold = 0;
      while (Hold < KeyTap) {
        final int N = Cpu.Step();
        Hold += N;
        Steps += N;
      }
      Release(Cpu, Key);
      while (Cpu.Addr != IDLE_LOOP) {
        Steps += Cpu.Step();
        if (Steps > MAX_STEPS) {
          LastError = true;
          return null;
        }
      }
    }
    LastSteps = Steps;
    LastCpu = Cpu;
    LastError = (Cpu.fB & FB_ERROR) != 0;
    final BigDecimal[] R = new BigDecimal[Results];
    for (int i = 0; i < Results; i++)
      R[i] = FromRam(Cpu.Ram[OUT[i]]);
    return R;
  }

  /* P->R (Inv false: T = radius, X = angle, gives X = y, T = x) and R->P
     (Inv true: T = x, X = y, gives X = angle, T = radius), returns {X, T}:
     keys Mode RCL 02 x<>t RCL 00 [INV] 2nd P->R STO 01 x<>t STO 03 (in a
     program the reference emulator ends P->R in error) */

  public BigDecimal[] Polar(boolean Inv, BigDecimal T, BigDecimal X, int Angle) {
    final int Mode = Angle == Number.ANG_RAD ? K_SUB : Angle == Number.ANG_GRAD ? K_ADD : K_MUL;
    final java.util.List<Integer> K = new java.util.ArrayList<>();
    for (int k : new int[] {K_2ND, Mode, K_RCL, K_DIGIT[0], K_DIGIT[2], K_XT, K_RCL, K_DIGIT[0], K_DIGIT[0]})
      K.add(k);
    if (Inv)
      K.add(K_INV);
    for (int k : new int[] {K_2ND, K_XT, K_STO, K_DIGIT[0], K_DIGIT[1], K_XT, K_STO, K_DIGIT[0], K_DIGIT[3]})
      K.add(k);
    final int[] Keys = new int[K.size()];
    for (int i = 0; i < Keys.length; i++)
      Keys[i] = K.get(i);
    return KeyCall(Keys, 2, X, T);
  }
}
