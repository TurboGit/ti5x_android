/*
    ti5x calculator emulator -- BCD Number 13-digits

    Copyright 2015-2018 Pascal Obry <pascal@obry.net>.

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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class Number {
  private BigDecimal v;
  private boolean error;
  private final static MathContext mc = new MathContext(13, RoundingMode.HALF_DOWN);

  public static final int ANG_RAD = 1;
  public static final int ANG_DEG = 2;
  public static final int ANG_GRAD = 3;

  /* display formats, see scaleExp */
  public static final int FORMAT_FIXED = 0;
  public static final int FORMAT_FLOAT = 1;
  public static final int FORMAT_ENG = 2;

  public final static Number ZERO = new Number(0.0);
  public final static Number ONE = new Number(1.0);
  public final static Number mONE = new Number(-1.0);
  public final static Number TEN = new Number(10.0);
  public final static Number PI = new Number(Math.PI);
  public final static Number ERROR = new Number("9.9999999e99");

  private final static BigDecimal B_ZERO = new BigDecimal(0.0, mc);
  private final static BigDecimal B_ONE = new BigDecimal(1.0, mc);
  private final static BigDecimal B_TEN = new BigDecimal(10.0, mc);
  private final static BigDecimal B_TWO = new BigDecimal(2.0, mc);
  private final static BigDecimal B_THREE = new BigDecimal(3.0, mc);
  private final static BigDecimal B_ERROR = new BigDecimal(9.9999999e99, mc);
  private final static BigDecimal B_SMALL = new BigDecimal(1e-99, mc);
  private final static BigDecimal HALF_PI = new BigDecimal(Math.PI / 2.0, mc);
  private final static BigDecimal HALF3_PI = new BigDecimal(3.0 * Math.PI / 2.0, mc);
  private final static BigDecimal TWO_PI = new BigDecimal(Math.PI * 2.0, mc);
  private final static BigDecimal FROM_DEG = new BigDecimal(Math.PI / 180.0, mc);
  private final static BigDecimal FROM_RAD = new BigDecimal(Math.PI / 200.0, mc);

  private static final double LOG10 = Math.log(10.0);
  private static final double LOG2 = Math.log(2.0);

  // constructors

  public Number() {
    v = B_ZERO;
    error = false;
  }

  public Number(double d) {
    if (d > 9.9999999e99 || Double.isInfinite(d) || Double.isNaN(d)) {
      v = B_ERROR;
      error = true;
    } else {
      v = new BigDecimal(d, mc);
      error = false;
    }
  }

  public Number(String s) {
    v = new BigDecimal(s, mc);
    error = false;
  }

  public Number(Number n) {
    v = n.v;
    error = false;
  }

  private Number(BigDecimal d) {
    v = d;
    error = false;
  }

  // state

  public boolean isEqual(Number n) {
    return v.compareTo(n.v) == 0;
  }

  public int compareTo(Number n) {
    return v.compareTo(n.v);
  }

  public int compareTo(long i) {
    return compareTo(new Number((double) i));
  }

  public boolean isError() {
    final boolean R = error;
    error = false;
    return R;
  }

  public boolean isNaN() {
    return false;
  }

  public boolean isInfinite() {
    if (v.compareTo(B_ERROR) > 0) {
      v = B_ERROR;
      return true;
    }
    if (v.compareTo(B_ERROR.negate()) < 0) {
      v = B_ERROR.negate();
      return true;
    } else
      return false;
  }

  public boolean isSmall() {
    Number l = new Number(v);
    l.abs();
    if (l.v.compareTo(B_ZERO) > 0 && l.v.compareTo(B_SMALL) < 0) {
      v = B_SMALL;
      return true;
    } else
      return false;
  }

  // setters/getters

  public void set(double d) {
    if (d > 9.9999999e99 || Double.isInfinite(d) || Double.isNaN(d)) {
      v = B_ERROR;
      error = true;
    } else {
      v = new BigDecimal(d, mc);
      error = false;
    }
  }

  public void set(Number n) {
    v = n.v;
    error = false;
  }

  public void set(String s) {
    v = new BigDecimal(s, mc);
    error = false;
  }

  public double get() {
    return v.doubleValue();
  }

  public long getInt() {
    return v.longValue();
  }

  public int getSignum() {
    return v.signum();
  }

  // signs

  public void signum() {
    v = new BigDecimal(v.signum());
  }

  public void abs() {
    v = v.abs();
  }

  public void negate() {
    v = v.negate();
  }

  public void min(long i) {
    BigDecimal N = new BigDecimal(i);
    v = v.min(N);
  }

  public void max(long i) {
    BigDecimal N = new BigDecimal(i);
    v = v.max(N);
  }

  // maths

  /* The arithmetic is the one of the TI-59 ROM (see romAdd, romSub,
     romMult and romDiv), this number is the first operand. */

  public void add(Number n) {
    romAdd(n);
  }

  public void add(long i) {
    add(new Number((double) i));
  }

  public void sub(Number n) {
    romSub(n);
  }

  public void sub(long i) {
    sub(new Number((double) i));
  }

  /* Addition and subtraction done exactly as the TI-59 ROM does them.

     The ROM aligns the operand with the smaller exponent by shifting its
     13-digit mantissa right, the digits shifted out are lost (there is
     no guard digit), then adds or subtracts the mantissas and truncates
     the result to 13 digits. An addition then gives the truncated exact
     sum, but a subtraction can give a result one more than the truncated
     one: 33.28601 - 1.404796669725 = 31.88121333028 (exact
     31.881213330275). */

  public void romAdd(Number Other) {
    romAddSub(Other, false);
  }

  public void romSub(Number Other) {
    romAddSub(Other, true);
  }

  private void romAddSub(Number Other, boolean Subtract) {
    final int SignB = Subtract ? -Other.v.signum() : Other.v.signum();

    if (Other.v.signum() == 0) {
      return;
    }
    if (v.signum() == 0) {
      v = SignB < 0 ? Other.v.abs().negate() : Other.v.abs();
      return;
    }

    final int[] ExpA = new int[1];
    final int[] ExpB = new int[1];
    long A = mantissa(v, ExpA) * v.signum();
    long B = mantissa(Other.v, ExpB) * SignB;
    int Exp = ExpA[0];

    // align the operand with the smaller exponent, truncating it
    if (ExpA[0] < ExpB[0]) {
      A = shiftRight(A, ExpB[0] - ExpA[0]);
      Exp = ExpB[0];
    } else if (ExpB[0] < ExpA[0]) {
      B = shiftRight(B, ExpA[0] - ExpB[0]);
    }

    long Sum = A + B;

    if (Sum == 0) {
      v = B_ZERO;
      return;
    }

    // a carry gives a 14th digit, truncated
    if (Math.abs(Sum) >= MANT_TOP) {
      Sum /= 10;
      Exp++;
    }

    v = BigDecimal.valueOf(Sum, 12 - Exp);
  }

  /* shift a signed mantissa right by N digits, truncating its magnitude */

  private static long shiftRight(long M, int N) {
    if (N >= 13)
      return 0;
    long Div = 1;
    for (int i = 0; i < N; i++)
      Div *= 10;
    return M / Div; // Java division truncates toward zero
  }

  public void mult(Number n) {
    romMult(n);
  }

  public void mult(long i) {
    mult(new Number((double) i));
  }

  /* Multiplication done exactly as the TI-59 ROM does it, with this
     number as the first operand and Second as the second one (the
     display register for "a x b =" and the memory as first operand
     for Prd).

     The ROM multiplies the 13-digit mantissas by repeated addition:
     the digits of the first operand are taken from the least
     significant one, the second operand is added digit times into a
     13-digit accumulator, and the accumulator is shifted right
     (truncated, there is no guard digit) between two digits.

     When an addition overflows the accumulator, the accumulator and
     the second operand are shifted right, so the 13th digit of the
     second operand is lost. At the next digit the second operand is
     shifted back left instead of shifting the accumulator, so this
     digit stays lost for the rest of the multiplication. If the
     overflow is on the last digit the exponent is one more.

     The result is then not always the 13-digit truncation of the
     exact product, and it depends on the order of the operands:
     24298 x 107766.3599999 = 2618507015.255 but
     107766.3599999 x 24298 = 2618507015.277. Random number generators
     keeping only a fractional part (ML-15) show the difference. */

  public void romMult(Number Second) {
    if (v.signum() == 0 || Second.v.signum() == 0) {
      v = B_ZERO;
      return;
    }

    final int[] ExpA = new int[1];
    final int[] ExpB = new int[1];
    final long MantA = mantissa(v, ExpA);
    final long MantB = mantissa(Second.v, ExpB);
    final int[] Carry = new int[1];
    final long Result = romMultMantissa(MantA, MantB, Carry);

    BigDecimal R = BigDecimal.valueOf(Result, 12 - (ExpA[0] + ExpB[0] + Carry[0]));
    if (v.signum() != Second.v.signum())
      R = R.negate();
    v = R;
  }

  private final static long MANT_TOP = 10_000_000_000_000L; // 10^13

  /* The 13-digit mantissa of a non-zero value (truncated if longer),
     Exp[0] is set to its exponent: |d| = mantissa * 10^(Exp[0] - 12). */

  private static long mantissa(BigDecimal d, int[] Exp) {
    BigDecimal A = d.abs();
    if (A.precision() > 13)
      A = A.round(new MathContext(13, RoundingMode.DOWN));
    final int P = A.precision();
    Exp[0] = P - 1 - A.scale();
    long M = A.unscaledValue().longValue();
    for (int i = P; i < 13; i++)
      M *= 10;
    return M;
  }

  /* The ROM multiplication of two 13-digit mantissas (see romMult).
     Carry[0] is set to 1 if the result has one more integer digit. The
     digit times additions are done at once, an overflow is found by
     computing the addition where it happens: the additions after it
     are done with the second operand shifted, and cannot overflow
     again. */

  static long romMultMantissa(long First, long Second, int[] Carry) {
    long Acc = 0;
    long A = First;
    long B = Second;
    boolean Overflow = false;
    boolean FirstDigit = true;

    while (true) {
      if (!FirstDigit) {
        if (A == 0)
          break;
        if (Overflow) {
          // shift the second operand back, its last digit is lost
          B *= 10;
          Overflow = false;
        } else {
          Acc /= 10;
        }
      }
      FirstDigit = false;

      final long Digit = A % 10;
      A /= 10;

      if (Digit == 0)
        continue;

      final long Sum = Acc + Digit * B;

      if (Sum < MANT_TOP) {
        Acc = Sum;
      } else {
        // the K-th addition overflows
        final long K = (MANT_TOP - Acc + B - 1) / B;
        Acc = (Acc + K * B) / 10 + (Digit - K) * (B / 10);
        B /= 10;
        Overflow = true;
      }
    }

    Carry[0] = Overflow ? 1 : 0;
    return Acc;
  }

  public void div(Number n) {
    romDiv(n);
  }

  public void div(long i) {
    div(new Number((double) i));
  }

  /* Division done exactly as the TI-59 ROM does it, this number is the
     dividend.

     The ROM does a long division by repeated subtraction on 15 digits:
     the 13 digits of the mantissas and the 2 exponent digits used as
     guard digits. After each digit of the quotient the divisor is
     shifted right and its last digit is lost, so the divisor gets a
     bit smaller and the quotient is sometimes one more in its 13th
     digit than the truncated exact quotient:
     10 / 17.897541921 = .5587359450891 (exact .55873594508900382...).
     The quotient digits are made until there are 13 of them, a first
     digit 0 is not counted. */

  public void romDiv(Number Divisor) {
    if (Divisor.v.signum() == 0) {
      v = B_ERROR;
      error = true;
      return;
    }
    if (v.signum() == 0) {
      v = B_ZERO;
      return;
    }

    final int[] ExpA = new int[1];
    final int[] ExpB = new int[1];
    final long MantA = mantissa(v, ExpA);
    final long MantB = mantissa(Divisor.v, ExpB);
    final int[] Shift = new int[1];
    final long Result = romDivMantissa(MantA, MantB, Shift);

    BigDecimal R = BigDecimal.valueOf(Result, 12 - (ExpA[0] - ExpB[0] - Shift[0]));
    if (v.signum() != Divisor.v.signum())
      R = R.negate();
    v = R;
  }

  /* The ROM division of two 13-digit mantissas (see romDiv). Shift[0]
     is set to 1 if the first digit of the quotient was 0. The repeated
     subtractions for a digit are done at once by a division. */

  static long romDivMantissa(long Dividend, long Divisor, int[] Shift) {
    long R = Dividend * 100;
    long D = Divisor * 100;
    long Q = 0;
    Shift[0] = 0;

    while (true) {
      Q += R / D;
      R %= D;
      if (Q >= MANT_TOP / 10)
        break;
      D /= 10;
      Q *= 10;
      if (Q == 0)
        Shift[0]++;
    }

    return Q;
  }

  public void rem(Number n) {
    if (n.v.compareTo(B_ZERO) == 0) {
      v = B_ERROR;
      error = true;
    } else
      v = v.remainder(n.v, mc);
  }

  public void rem(long i) {
    rem(new Number((double) i));
  }

  public void trigScale(int Angle) {
    v = trigScale2(Angle);
  }

  private static BigDecimal trigScale2(int Angle) {
    switch (Angle) {
      case ANG_DEG:
        return FROM_DEG;
      case ANG_GRAD:
        return FROM_RAD;
      default:
        return B_ONE;
    }
  }

  private void NormalizeAngle(int angle) {
    v = v.multiply(trigScale2(angle), mc).remainder(TWO_PI, mc);

    if (v.signum() < 0) {
      v = v.add(TWO_PI, mc);
    }
  }

  public void cos(int Angle) {
    if (Rom() != null && FromRom(Rom().Cos(v, Angle)))
      return;

    NormalizeAngle(Angle);

    if (v.compareTo(HALF_PI) == 0 || v.compareTo(HALF3_PI) == 0)
      v = B_ZERO;
    else
      v = new BigDecimal(Math.cos(v.doubleValue()), mc);
  }

  public void acos(int Value) {
    if (Rom() != null && FromRom(Rom().Acos(v, Value)))
      return;

    if (v.compareTo(B_ONE.negate()) < 0 || v.compareTo(B_ONE) > 0)
      error = true;
    else
      v = new BigDecimal(Math.acos(v.doubleValue()), mc).divide(trigScale2(Value), mc);
  }

  /* the functions done by the original TI-59 ROM (see RomMath): the ROM
     of ti5x by default, created when first used; none after SetRom(null) */
  private static RomMath TheRom = null;
  private static boolean UseRom = true;

  public static synchronized RomMath Rom() {
    if (UseRom && TheRom == null)
      TheRom = new RomMath();
    return UseRom ? TheRom : null;
  }

  public static synchronized void SetRom(RomMath R) {
    TheRom = R;
    UseRom = R != null;
  }

  /* sets the result of a ROM function, false if the ROM is not used */

  private boolean FromRom(BigDecimal R) {
    if (R == null)
      return false;
    v = R;
    if (Rom().LastError)
      error = true;
    return true;
  }

  public void sin(int Angle) {
    if (Rom() != null && FromRom(Rom().Sin(v, Angle)))
      return;

    NormalizeAngle(Angle);

    if (v.compareTo(B_ZERO) == 0 || v.compareTo(PI.v) == 0)
      v = B_ZERO;
    else
      v = new BigDecimal(Math.sin(v.doubleValue()), mc);
  }

  public void asin(int Value) {
    if (Rom() != null && FromRom(Rom().Asin(v, Value)))
      return;

    if (v.compareTo(B_ONE.negate()) < 0 || v.compareTo(B_ONE) > 0)
      error = true;
    else
      v = new BigDecimal(Math.asin(v.doubleValue()), mc).divide(trigScale2(Value), mc);
  }

  public void tan(int Angle) {
    if (Rom() != null && FromRom(Rom().Tan(v, Angle)))
      return;

    NormalizeAngle(Angle);

    if (v.compareTo(HALF_PI) == 0 || v.compareTo(HALF3_PI) == 0) {
      v = B_ERROR;
      error = true;
    } else if (v.compareTo(B_ZERO) == 0 || v.compareTo(PI.v) == 0) {
      v = B_ZERO;
    } else
      v = new BigDecimal(Math.tan(v.doubleValue()), mc);
  }

  public void atan(int Angle) {
    if (Rom() != null && FromRom(Rom().Atan(v, Angle)))
      return;

    v = new BigDecimal(Math.atan(v.doubleValue()), mc).divide(trigScale2(Angle), mc);
  }

  public static double logBigInteger(BigInteger n) {
    final int blex = n.bitLength() - 1022; // any value in 60..1023 is ok
    if (blex > 0)
      n = n.shiftRight(blex);
    double res = Math.log(n.doubleValue());
    return blex > 0 ? res + blex * LOG2 : res;
  }

  public void log() {
    if (Rom() != null && FromRom(Rom().Log(v)))
      return;

    final int check = v.compareTo(B_ZERO);

    if (check < 0) {
      v = v.abs();
      set((logBigInteger(v.unscaledValue()) + (-v.scale() * LOG10)) / LOG10);
      error = true;
    } else if (check == 0) {
      v = B_ERROR.negate();
      error = true;
    } else
      set((logBigInteger(v.unscaledValue()) + (-v.scale() * LOG10)) / LOG10);
  }

  public void ln() {
    if (Rom() != null && FromRom(Rom().Ln(v)))
      return;

    final int check = v.compareTo(B_ZERO);

    if (check < 0) {
      v = v.abs();
      set((logBigInteger(v.unscaledValue()) + (-v.scale() * LOG10)));
      error = true;
    } else if (check == 0) {
      v = B_ERROR.negate();
      error = true;
    } else
      set((logBigInteger(v.unscaledValue()) + (-v.scale() * LOG10)));
  }

  /* The square root as computed by the TI-59 ROM: the exact square root
     truncated to 13 digits. */

  private static BigDecimal sqrt(BigDecimal A) {
    if (A.signum() == 0)
      return B_ZERO;

    final int[] Exp = new int[1];
    final long M = mantissa(A, Exp);

    // A = M * 10^(Exp - 12): scale M so that the exponent left is even
    // and the integer square root has 13 digits
    final int Shift = ((Exp[0] - 12 - 12) % 2 == 0) ? 12 : 13;
    final BigInteger N = BigInteger.valueOf(M).multiply(BigInteger.TEN.pow(Shift));
    final BigInteger R = isqrt(N);

    return new BigDecimal(R, -((Exp[0] - 12 - Shift) / 2));
  }

  /* floor of the square root (BigInteger.sqrt is not on Android API 23) */

  static BigInteger isqrt(BigInteger N) {
    BigInteger X = BigInteger.ONE.shiftLeft((N.bitLength() + 1) / 2);
    while (true) {
      final BigInteger Y = X.add(N.divide(X)).shiftRight(1);
      if (Y.compareTo(X) >= 0)
        return X;
      X = Y;
    }
  }

  public void sqrt() {
    if (v.compareTo(B_ZERO) < 0.0) {
      v = sqrt(v.negate(mc));
      error = true;
    } else
      v = sqrt(v);
  }

  /* x^2 and 1/x use the ROM multiplication and division */

  public void x2() {
    romMult(this);
  }

  public void reciprocal() {
    final Number Value = new Number(v);
    v = B_ONE;
    romDiv(Value);
  }

  public void pow(Number n) {
    if (Rom() != null && FromRom(Rom().Pow(v, n.v)))
      return;

    if (v.signum() == 0 && n.v.signum() < 0) {
      v = B_ERROR;
      error = true;
    } else if (v.signum() < 0) {
      /* as the ROM: a negative base gives |y|^x and the error, whatever
         x is: (-8)^3 = 512, (-8)^-2 = .015625 (flashing) */
      if (n.v.signum() == 0)
        v = B_ONE;
      else
        set(Math.pow(Math.abs(v.doubleValue()), n.v.doubleValue()));
      error = true;
    } else
      set(Math.pow(v.doubleValue(), n.v.doubleValue()));
  }

  /* this number with the sign of its exponent dropped: 9.347E-6 gives
     9.347E6 (see the HIR operations in State) */

  public Number positiveExponent() {
    if (v.signum() == 0)
      return new Number(v);
    final BigDecimal A = v.abs();
    final int Exp = A.precision() - 1 - A.scale();
    return new Number(Exp < 0 ? v.scaleByPowerOfTen(-2 * Exp) : v);
  }

  /* 10^x and the X root of this number: only done by the ROM, false if
     it is not used */

  public boolean romExp10() {
    return Rom() != null && FromRom(Rom().Exp10(v));
  }

  public boolean romRoot(Number X) {
    return Rom() != null && FromRom(Rom().Root(v, X.v));
  }

  /* P->R (Inv false) or R->P (Inv true) by the ROM: returns the new X
     and T, null if the ROM is not used. The new X has the error flag. */

  public static Number[] romPolar(boolean Inv, Number T, Number X, int Angle) {
    final RomMath Rom = Rom();
    if (Rom == null)
      return null;
    final BigDecimal[] R = Rom.Polar(Inv, T.v, X.v, Angle);
    if (R == null)
      return null;
    final Number NewX = new Number(R[0]);
    final Number NewT = new Number(R[1]);
    NewX.error = Rom.LastError;
    return new Number[] {NewX, NewT};
  }

  public void exp() {
    if (Rom() != null && FromRom(Rom().Exp(v)))
      return;

    set(Math.exp(v.doubleValue()));
  }

  public void Pi() {
    v = PI.v;
  }

  public void Factorial() {
    BigDecimal result = BigDecimal.ONE;
    for (int i = 2; i <= v.intValue(); i++) {
      result = result.multiply(BigDecimal.valueOf(i));
    }
    v = result;
  };

  // cut

  public void intPart() {
    v = new BigDecimal(v.longValue(), mc);
  }

  public void fracPart() {
    final BigDecimal i = new BigDecimal(v.longValue(), mc);
    v = v.subtract(i, mc);
  }

  public void nDigits(int n) {
    if (n >= 0) {
      final Number Factor = new Number(B_ONE.scaleByPowerOfTen(n));

      mult(Factor);
      intPart();
      div(Factor);
    }
  }

  public void roundUpNDigits(int n) {
    if (n >= 0) {
      final Number Factor = new Number(B_ONE.scaleByPowerOfTen(n));

      v = v.setScale(n, RoundingMode.HALF_UP);
    }
  }

  /* returns the number of figures before the decimal point in the
     formatted representation of X scaled by Exp. This has to be
     at least 1, because the decimal point is part of the display
     of the preceding digit. */

  public int figuresBeforeDecimal(int Exp) {
    int BeforeDecimal;
    Number l = new Number(this);

    if (v.compareTo(B_ZERO) != 0) {
      Number d = new Number(10);
      Number e = new Number(Exp);
      int Adjust = 0;

      d.pow(e);
      l.abs();

      if (Exp == 0 && l.v.compareTo(B_ONE) < 0)
        Adjust = 1;

      l.div(d);
      l.log();
      l.add(ONE);
      BeforeDecimal = l.v.intValue();

      if (BeforeDecimal >= 1)
        return BeforeDecimal - Adjust;
      else
        return 1 - Adjust;
    } else {
      BeforeDecimal = 1;
    }

    return BeforeDecimal;
  }

  /* returns the exponent scale to display X using the given format. */

  public int scaleExp(int UsingFormat) {
    int Exp = 0;
    boolean infone = false;
    Number l = new Number(v);

    l.abs();
    l.log();
    // Rounding to correspond to Ti59 precision, this makes test_36() to pass
    l.roundUpNDigits(9);

    if (v.compareTo(B_ZERO) != 0) {
      switch (UsingFormat) {
        case FORMAT_FLOAT:
          Exp = l.v.setScale(0, RoundingMode.FLOOR).intValue();
          break;
        case FORMAT_ENG:
          l.v = l.v.divide(B_THREE, mc);
          Exp = l.v.setScale(0, RoundingMode.FLOOR).intValue() * 3;
          break;
      }

      // we should not have a log of 100, this can happen for displaying the
      // flashing "9.9999999 99". Adjust to 99 for exponent. This is a issue
      // with log() precision.

      if (Exp >= 100.0)
        Exp = 99;
      if(Exp <= -100)
        Exp = -99;
    }

    return Exp;
  }

  public String toString() {
    return v.toString();
  }

  public String toStateString() {
    /* text saved in the calculator state and bank files, reads back as
       exactly this number with new Number(String). Fixed notation with 16
       decimals (the format of earlier versions) when that is exact,
       scientific notation otherwise (e.g. 1.5E-20). */
    if (v.stripTrailingZeros().scale() <= 16) {
      return formatString(java.util.Locale.US, 16);
    }
    return v.toString();
  }

  public String formatString(java.util.Locale locale, int nrDecimals) {
    final DecimalFormat form = new DecimalFormat();
    final DecimalFormatSymbols decForm = new DecimalFormatSymbols(locale);
    form.setMaximumFractionDigits(nrDecimals);
    form.setMinimumFractionDigits(nrDecimals);
    form.setGroupingUsed(false);
    form.setDecimalFormatSymbols(decForm);
    form.setRoundingMode(RoundingMode.HALF_UP);
    return form.format(v);
  }
}
