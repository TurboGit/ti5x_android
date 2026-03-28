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

package net.obry.ti5x;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

class Number {
  private BigDecimal v;
  private boolean error;
  private final static MathContext mc = new MathContext(13, RoundingMode.HALF_DOWN);

  public static final int ANG_RAD = 1;
  public static final int ANG_DEG = 2;
  public static final int ANG_GRAD = 3;

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

  public void add(Number n) {
    v = v.add(n.v, mc);
  }

  public void add(long i) {
    add(new Number((double) i));
  }

  public void sub(Number n) {
    v = v.subtract(n.v, mc);
  }

  public void sub(long i) {
    sub(new Number((double) i));
  }

  public void mult(Number n) {
    v = v.multiply(n.v, mc);
  }

  public void mult(long i) {
    mult(new Number((double) i));
  }

  public void div(Number n) {
    if (n.v.compareTo(B_ZERO) == 0) {
      v = B_ERROR;
      error = true;
    } else
      v = v.divide(n.v, mc);
  }

  public void div(long i) {
    div(new Number((double) i));
  }

  public void rem(Number n) {
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

  private void NormalizeAngle(int Angle) {
    v = v.multiply(trigScale2(Angle), mc);

    if (v.compareTo(B_ZERO) < 0) {
      while (v.compareTo(B_ZERO) < 0.0)
        v = v.add(TWO_PI, mc);
    } else if (v.compareTo(TWO_PI) >= 0) {
      while (v.compareTo(TWO_PI) >= 0)
        v = v.subtract(TWO_PI, mc);
    }
  }

  public void cos(int Angle) {
    NormalizeAngle(Angle);

    if (v.compareTo(HALF_PI) == 0 || v.compareTo(HALF3_PI) == 0)
      v = B_ZERO;
    else
      v = new BigDecimal(Math.cos(v.doubleValue()), mc);
  }

  public void acos(int Value) {
    if (v.compareTo(B_ONE.negate()) < 0 || v.compareTo(B_ONE) > 0)
      error = true;
    else
      v = new BigDecimal(Math.acos(v.doubleValue()), mc).divide(trigScale2(Value), mc);
  }

  public void sin(int Angle) {
    NormalizeAngle(Angle);

    if (v.compareTo(B_ZERO) == 0 || v.compareTo(PI.v) == 0)
      v = B_ZERO;
    else
      v = new BigDecimal(Math.sin(v.doubleValue()), mc);
  }

  public void asin(int Value) {
    if (v.compareTo(B_ONE.negate()) < 0 || v.compareTo(B_ONE) > 0)
      error = true;
    else
      v = new BigDecimal(Math.asin(v.doubleValue()), mc).divide(trigScale2(Value), mc);
  }

  public void tan(int Angle) {
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

  private static BigDecimal sqrt(BigDecimal A) {
    BigDecimal x0 = new BigDecimal("0", mc);
    BigDecimal x1 = new BigDecimal(Math.sqrt(A.doubleValue()), mc);
    BigDecimal diff = x0.subtract(x1);

    while (diff.doubleValue() > 0.0000000000001) {
      x0 = x1;
      x1 = A.divide(x0, mc);
      x1 = x1.add(x0);
      x1 = x1.divide(B_TWO, mc);
      diff = x1.subtract(x0);
    }
    return x1;
  }

  public void sqrt() {
    if (v.compareTo(B_ZERO) < 0.0) {
      v = sqrt(v.negate(mc));
      error = true;
    } else
      v = sqrt(v);
  }

  public void x2() {
    v = v.multiply(v, mc);
  }

  public void reciprocal() {
    if (v.signum() == 0) {
      v = B_ERROR;
      error = true;
    } else
      v = B_ONE.divide(v, mc);
  }

  public void pow(Number n) {
    if (v.signum() == 0 && n.v.signum() < 0) {
      v = B_ERROR;
      error = true;
    } else if (v.signum() < 0 && n.v.signum() == 0) {
      v = B_ONE;
      error = true;
    } else if (v.signum() < 0 && n.v.signum() <= 0) {
      set(Math.pow(Math.abs(v.doubleValue()), Math.abs(n.v.doubleValue())));
      error = true;
    } else
      set(Math.pow(v.doubleValue(), n.v.doubleValue()));
  }

  public void exp() {
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
        case State.FORMAT_FLOAT:
          Exp = l.v.setScale(0, RoundingMode.FLOOR).intValue();
          break;
        case State.FORMAT_ENG:
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
