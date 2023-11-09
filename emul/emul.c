#include <stdio.h>
#include <string.h>

// ====================================
// Mode control
// ====================================
#define	MODE_TI_59	0x0001
#define	MODE_PRINTER	0x0002
#define	MODE_CARD	0x0004
static unsigned mode_flags = 0;

// ====================================
// card file names
// ====================================
static char card_input[1024] = "";
static char card_output[1024] = "card.bin";

// ====================================
// disassembler source
// ====================================
// #include "disasm.inc"

// ====================================
// CPU state variables
// ====================================
static struct {
  // registers
  unsigned char A[16], B[16], C[16], D[16], E[16];
  // keyboard map
#define	KN_BIT	0
#define	KO_BIT	1
#define	KP_BIT	2
#define	KQ_BIT	3
#define	KR_BIT	4
#define	KS_BIT	5
#define	KT_BIT	6
  unsigned char key[16];
  // bit registers
  unsigned short KR, SR, fA, fB;
  // EXT signal (used for data exchange)
  unsigned short EXT;
  // PREG temporary register (used for direct PC control)
  unsigned short PREG;
  // ALU output signal (used for data exchange)
  unsigned char Sout[16];
  // various CPU flags
#define	FLG_IDLE	0x0001
#define	FLG_HOLD	0x0002
#define	FLG_JUMP	0x0004
#define	FLG_RCL_SHIFT	4
#define	FLG_RECALL	0x0010
#define	FLG_STORE	0x0020
#define	FLG_RAM_OP	0x0040
#define	FLG_RAM_READ	0x0080
#define	FLG_RAM_WRITE	0x0100
#define	FLG_EXT_VALID	0x0200
#define	FLG_IO_VALID	0x0400
#define	FLG_COND	0x0800 // must be here for easy jump processing...
#define	FLG_DISP	0x1000
#define	FLG_DISP_C	0x4000 // must be here for easy BUSY processing...
#define	FLG_BUSY	0x8000
  unsigned short flags;
  // R5 ALU register
  unsigned char R5;
  // cycle digit counter
  unsigned char digit;
  // CPU cycle counter (used to simulate real CPU frequency)
  unsigned cycle;
  // SCOM's & firstROM's address counter = program counter
  unsigned short addr;
  // SCOM register addressing
  unsigned char REG_ADDR;
  // RAM register addressing
  unsigned char RAM_ADDR, RAM_OP;
  // Library module addressing
  unsigned short LIB;
  // printer support
  char PRN_BUF[20];
  unsigned char PRN_PTR;
  unsigned PRN_BUSY;
  // card support
#define	CRD_LEN	(4+30*8+2)
  unsigned char CRD_PTR;
  unsigned char CRD_BUF[CRD_LEN]; // 246 bytes
  unsigned char CRD_FLAGS;
#define	CRD_READ	0x01
#define	CRD_WRITE	0x02
} cpu;
// 455kHz / 2 / 16 = 14219
// 20ms ~ 284.375 instructions
// 50ms ~ 710.9375 instructions
#define	CPU_FREQ	455000	//[Hz]
#define	EMUL_TICK	20	//[ms]
#define	EMUL_CYCLE	((EMUL_TICK * CPU_FREQ) / 2 / 16 / 1000)
// printer real speed emulation
//#define	PRN_BUSY_CYCLE	0
// printer advance can't be fast when using buttons ADVANCE/Adv; calc sends ADV instuction all the time these buttons are held
#define	PRN_BUSY_CYCLE	((150 * CPU_FREQ) / 2 / 16 / 1000)

#define	CARD_INSERTED	(cpu.key[10] & (1 << KR_BIT))
#define	PRN_CONNECTED	(cpu.key[0] & (1 << KP_BIT))
#define	PRN_TRACE	(cpu.key[15] & (1 << KP_BIT))
#define	PRN_ADVANCE	(cpu.key[12] & (1 << KN_BIT))
#define	PRN_PRINT	(cpu.key[12] & (1 << KP_BIT))

// SCOM registers
unsigned char SCOM[16][16];

// RAM registers
unsigned char RAM[120][16];

// mask definitions
typedef struct {
  unsigned char start, end, cpos, cval;
} mask_type;
static const mask_type mask_info[16] = {
  {-1, 0,  0,   0},
  {0, 15,  0,   0}, // ALL
  {0,  0,  0,   0}, // DPT
  {0,  0,  0,   1}, // DPT 1
  {0,  0,  0, 0xC}, // DPT C
  {3,  3,  3,   1}, // LLSD 1
  {1,  2,  1,   0}, // EXP
  {1,  2,  1,   1}, // EXP 1
  {-1, 0,  0,   0},
  {3, 15,  3,   0}, // MANT
  {-1, 0,  0,   0},
  {3, 15,  3,   5}, // MLSD 5
  {1, 15,  1,   0}, // MAEX
  {1, 15,  3,   1}, // MLSD 1
  {1, 15, 15,   1}, // MMSD 1
  {1, 15,  1,   1}  // MAEX 1
};

// Library module data (linked to Master.c)
extern unsigned char LIB[5000];
extern const char libtoken[100][8];

// SCOM constant data (linked to Const.c)
extern unsigned char CONSTANT[64][16];

static const char PRN_CODE[64] = {
  ' ','0','1','2','3','4','5','6',
  '7','8','9','A','B','C','D','E',
  '-','F','G','H','I','J','K','L',
  'M','N','O','P','Q','R','S','T',
  '.','U','V','W','X','Y','Z','+',
  'x','*','s','p','e','(',')',',',
  '^','%','_','/','=','\'',0x9E,'&',
  'z','?',':','!',']',0x7F,'[','S'
};

static const struct {
  unsigned char code;
  char str[3];
} PRN_STR[] = {
  {0x00, "   "},
  {0x11, " = "},
  {0x12, " - "},
  {0x13, " + "},
  {0x16, " / "},
  {0x17, " x "},
  {0x1A, "xsY"}, //x_sqrt_Y
  {0x1B, "Y^x"}, //Yx_
  {0x21, "CLR"},
  {0x22, "INV"},
  {0x23, "DPT"},
  {0x26, "CE "},
  {0x27, "+/-"},
  {0x2D, "EE "},
  {0x31, "e^x"}, // ex_
  {0x33, "x^2"}, // x2_
  {0x36, "1/x"},
  {0x3C, "sX "}, // sqrt_X_
  {0x3D, "X_Y"}, // X exchange Y ??
  {0x51, "LNX"},
  {0x53, "PRM"},
  {0x54, " % "},
  {0x56, "COS"},
  {0x57, "SIN"},
  {0x5D, "TAN"},
  {0x61, "SUM"},
  {0x66, "STO"},
  {0x67, "pi "}, //_pi_
  {0x68, "RCL"},
  {0x69, "S+ "},
  {0x70, "ERR"},
  {0x71, " { "},
  {0x72, " ) "},
  {0x73, "LRN"},
  {0x74, "RUN"},
  {0x76, "HLT"},
  {0x78, "STP"},
  {0x7A, "GTO"},
  {0x7C, "IF "},
  {0, {0}}
};

// ====================================
// ALU functions
// ====================================
// ALU block
// ------------------------------------
enum {ALU_ADD, ALU_SHL, ALU_SUB, ALU_SHR};
#define	ALU_SHIFT	ALU_SHL
// ------------------------------------
static void Alu (unsigned char *dst, unsigned char *srcX, unsigned char *srcY, const mask_type *mask, unsigned char flags) {
unsigned char carry = 0;
unsigned char shl = 0;
int i;
  for (i = 0; i <= 15; i++) {
    unsigned char sum = 0, shr = 0;
    if (i == mask->start)
      shl = carry = 0;
    if (srcY)
      sum = srcY[i];
    if (i == mask->cpos)
      sum |= mask->cval;
    shr = sum;
    sum += carry;
    if (flags >= ALU_SUB)
      sum = -sum;
    if (srcX) {
      sum += srcX[i];
      shr |= srcX[i];
    }
    cpu.Sout[i] = (sum & 0x0F);
    if (!i) {
      if ((carry = (sum >= 0x10)))
        sum &= 0x0F;
    } else {
      if ((carry = (sum >= 10))) {
	if (flags < ALU_SUB)
	  sum -= 10;
	else
	  sum += 10;
      }
    }
    // write result to destination
    if (i >= mask->start && i <= mask->end) {
      if (i == mask->start)
	cpu.R5 = sum;
      if (dst) {
	if (flags == ALU_SHL)
	  dst[i] = shl;
	else
	if (flags == ALU_SHR) {
	  if (i > mask->start)
	    dst[i-1] = shr;
	  if (i == mask->end)
	    dst[i] = 0;
	} else
	  dst[i] = sum;
	shl = sum;
      }
      if (i == mask->end && !(flags & ALU_SHIFT) && carry)
	cpu.flags &= ~FLG_COND;
    }
  }
}

// ====================================
// Exchange value
// ------------------------------------
static void Xch (unsigned char *src1, unsigned char *src2, const mask_type *mask) {
int i;
  for (i = mask->start; i <= mask->end; i++) {
    unsigned char tmp;
    tmp = src1[i];
    src1[i] = src2[i];
    src2[i] = tmp;
  }
}

// ====================================
// main CPU function
// executes instructions
// ------------------------------------
int execute (unsigned short opcode) {
  // process PREG address change
  if (cpu.PREG & 0x1) {
    // PREG
    cpu.addr = cpu.PREG >> 3;
    cpu.PREG = 0;
    return 0;
  }
  // update digit counter
  if (cpu.digit)
    cpu.digit--;
  else
    cpu.digit = 15;

  //  printf("cpu.digit = %d\n", cpu.digit);
  // update instruction cycle counter
  if (cpu.flags & FLG_IDLE)
    cpu.cycle += 4;
  else
    cpu.cycle++;
  // clear HOLD bit
  cpu.flags &= ~FLG_HOLD;
  // process PREG bit
  if (cpu.KR & 0x2) {
    cpu.PREG = (cpu.KR >> 1) | (cpu.KR << 15);
    cpu.KR &= ~0x2;
  }
  // init EXT
  if (cpu.flags & FLG_EXT_VALID)
    cpu.flags &= ~FLG_EXT_VALID;
  else
    cpu.EXT = 0;

  // init IO (ALU out)
  if (cpu.flags & FLG_IO_VALID)
    cpu.flags &= ~FLG_IO_VALID;
  else
    memset (cpu.Sout, 0, sizeof (cpu.Sout));

  // process opcode
  if (opcode & 0x1000) {
    // ================================
    // jump
    // ================================
    cpu.flags |= FLG_JUMP;
    if (!((cpu.flags ^ opcode) & FLG_COND)) {
      // COND is same as bit in opcode
      unsigned short offs = (opcode >> 1) & 0x3FF;
      if (opcode & 0x0001)
	cpu.addr -= offs;
      else
        cpu.addr += offs;
    } else
      cpu.addr++;
    return 2;
  }
  if (cpu.flags & FLG_JUMP) {
    // COND is set again after last jump in series
    cpu.flags &= ~FLG_JUMP;
    cpu.flags |= FLG_COND;
  }
  switch (opcode & 0x0F00) {
    // ================================
    // flag operations
    // ================================
    case 0x0000:
      {
	unsigned bit = (opcode >> 4) & 0x000F;
	unsigned mask = 1 << bit;
	switch (opcode & 0x000F) {
	  case 0x0000:
	    // TEST FLAG A
	    if (cpu.fA & mask)
	      cpu.flags &= ~FLG_COND;
	    break;
	  case 0x0001:
	    // SET FLAG A
	    cpu.fA |= mask;
	    break;
	  case 0x0002:
	    // ZERO FLAG A
	    cpu.fA &= ~mask;
	    break;
	  case 0x0003:
	    // INVERT FLAG A
	    cpu.fA ^= mask;
	    break;
	  case 0x0004:
	    // EXCH. FLAG A B
	    if ((cpu.fA ^ cpu.fB) & mask) {
	      cpu.fA ^= mask;
	      cpu.fB ^= mask;
	    }
	    break;
	  case 0x0005:
	    // SET FLAG KR
	    cpu.KR |= mask;
	    break;
	  case 0x0006:
	    // COPY FLAG B->A
	    if ((cpu.fA ^ cpu.fB) & mask)
	      cpu.fA ^= mask;
	    break;
	  case 0x0007:
	    // REG 5->FLAG A S0 S3
	    cpu.fA = (cpu.fA & ~0x001E) | ((cpu.R5 & 0x000F) << 1);
	    break;
	  case 0x0008:
	    // TEST FLAG B
	    if (cpu.fB & mask)
	      cpu.flags &= ~FLG_COND;
	    break;
	  case 0x0009:
	    // SET FLAG B
	    cpu.fB |= mask;
	    break;
	  case 0x000A:
	    // ZERO FLAG B
	    cpu.fB &= ~mask;
	    break;
	  case 0x000B:
	    // INVERT FLAG B
	    cpu.fB ^= mask;
	    break;
	  case 0x000C:
	    // COMPARE FLAG A B
	    if (!((cpu.fA ^ cpu.fB) & mask))
	      cpu.flags &= ~FLG_COND;
	    break;
	  case 0x000D:
	    // ZERO FLAG KR
	    cpu.KR &= ~mask;
	    break;
	  case 0x000E:
	    // COPY FLAG A->B
	    if ((cpu.fA ^ cpu.fB) & mask)
	      cpu.fB ^= mask;
	    break;
	  case 0x000F:
	    // REG 5->FLAG B S0 S3
	    cpu.fB = (cpu.fB & ~0x001E) | ((cpu.R5 & 0x000F) << 1);
	    break;
	}
      }
      break;
    // ================================
    // keyboard operations
    // ================================
    case 0x0800:
      {
	unsigned char mask;
	// get pressed key(s) mask
	mask = (((opcode & 0x07) | ((opcode >> 1) & 0x78)) ^ 0x7F) & cpu.key[cpu.digit];
	// check if more than 1 key is pressed
	if (mask & (mask - 1))
	  mask = 0;
	if (!(opcode & 0x0008)) {
	  // scan all keyboard
	  // scan current row
	  if (cpu.key[cpu.digit] & mask) {
	    unsigned char bit = 0;
	    // get bit position
	    while (!(mask & 1)) {
	      bit++;
	      mask >>= 1;
	    }
	    // clear COND
	    cpu.flags &= ~FLG_COND;
	    // set result to KR
	    cpu.KR = /*(cpu.KR & ~0x07F0) |*/ (cpu.digit << 4) | ((bit << 8) & 0x0700);
	  } else
	  if (cpu.digit) {
	    // wait for digit 0 counter - end of scan
	    cpu.flags |= FLG_HOLD;
	    return 11;
	  }
	} else {
	  // scan current row and update COND
	  if (cpu.key[cpu.digit] & mask)
	    cpu.flags &= ~FLG_COND;
	}
      }
      break;
    // ================================
    // wait operations
    // ================================
    case 0x0A00:
      switch (opcode & 0x000F) {
	case 0x0000:
	  // wait for digit
	  if (cpu.digit != ((opcode >> 4) & 0x000F)) {
	    cpu.flags |= FLG_HOLD;
	    return 12;
	  }
	  break;
	case 0x0001:
	  // Zero Idle
	  cpu.flags &= ~FLG_IDLE;
	  break;
	case 0x0002:
	  // CLFA
	  cpu.fA = 0;
	  break;
	case 0x0003:
	  // Wait Busy
#warning "Unknown behaviour..."
	  break;
	case 0x0004:
	  // INCKR
	  cpu.KR += 0x0010;
	  if (!(cpu.KR & 0xFFF0))
	    cpu.KR ^= 0x0001;
	  break;
	case 0x0005:
	  // TKR
	  if (cpu.KR & (1 << ((opcode >> 4) & 0x000F)))
	    cpu.flags &= ~FLG_COND;
	  break;
	case 0x0006:
	  // FLGR5
	  if (opcode & 0x0010) {
	    cpu.R5 = (cpu.fB >> 1) & 0x000F;
	  } else {
	    cpu.R5 = (cpu.fA >> 1) & 0x000F;
	  }
	  break;
	case 0x0007:
	  // Number
	  cpu.R5 = (opcode >> 4) & 0x000F;
	  break;
	case 0x0008:
	  // KRR5/R5KR + peripherals
	  switch (opcode & 0x00F0) {
	    case 0x0000:
	      // KRR5
	      cpu.R5 = (cpu.KR >> 4) & 0x000F;
	      break;
	    case 0x0010:
	      // R5KR
	      cpu.KR = (cpu.KR & ~0x00F0) | (cpu.R5 << 4);
	      break;
	    case 0x0020:
	      // READ
//	      cpu.EXT = (card_read () << 4);
	      cpu.EXT = cpu.CRD_BUF[cpu.CRD_PTR++] << 4;
	      cpu.flags |= FLG_EXT_VALID;
	      break;
	    case 0x0030:
	      // WRITE
//	      card_write (cpu.KR >> 4)
	      cpu.CRD_BUF[cpu.CRD_PTR++] = (cpu.KR >> 4);
	      break;
	    case 0x0040:
	      // CRDOFF
	      if (cpu.CRD_FLAGS & CRD_WRITE) {
		FILE *f;
		if ((f = fopen (card_output, "r+b")) == NULL)
		  f = fopen (card_output, "wb");
		if (f == NULL) {
		  fprintf (stderr, "Can't access file %s!\n", card_output);
		} else {
		  fseek (f, CRD_LEN * ((cpu.CRD_BUF[2] & 0x0F) / 3), SEEK_SET);
		  fwrite (cpu.CRD_BUF, CRD_LEN, 1, f);
		  fclose (f);
		}
	      } else
	      if (cpu.CRD_FLAGS & CRD_READ) {
		cpu.CRD_BUF[2] += 3;
	      }
	      cpu.CRD_FLAGS = 0;
	      cpu.CRD_PTR = 0;
	      // clear card switch
	      if ((mode_flags & MODE_TI_59))
		// TI-59: D10-KR card switch normally closed
		cpu.key[10] |= (1 << KR_BIT);
	      break;
	    case 0x0050:
	      // CRDREAD
	      if (!cpu.CRD_FLAGS) {
		FILE *f;
		if ((f = fopen (card_input, "rb")) == NULL) {
		  fprintf (stderr, "Can't open file %s!\n", card_input);
		  break;
		}
		fseek (f, CRD_LEN * ((cpu.CRD_BUF[2] & 0x0F) / 3), SEEK_SET);
		fread (cpu.CRD_BUF, CRD_LEN, 1, f);
		fclose (f);
		cpu.CRD_FLAGS = CRD_READ;
		//cpu.CRD_PTR = 0;
	      }
	      break;
	    case 0x0060:
	      // LOAD
//	      print_char ((cpu.KR >> 4) & 0x3F);
	      if (mode_flags & MODE_PRINTER) {
		cpu.PRN_BUF[cpu.PRN_PTR++] = PRN_CODE[(cpu.KR >> 4) & 0x3F];
		if (cpu.PRN_PTR >= sizeof (cpu.PRN_BUF)) cpu.PRN_PTR = 0;
	      }
	      break;
	    case 0x0070:
	      // FUNCTION
//	      print_func ((cpu.KR >> 4) & 0x7F);
	      if (mode_flags & MODE_PRINTER) {
		int i;
		unsigned char code = (cpu.KR >> 4) & 0x7F;
		for (i = 0; *PRN_STR[i].str; i++) {
		  if (code == PRN_STR[i].code) {
		    for (code = 3; code; ) {
		      cpu.PRN_BUF[cpu.PRN_PTR++] = PRN_STR[i].str[--code];
		      if (cpu.PRN_PTR >= sizeof (cpu.PRN_BUF)) cpu.PRN_PTR = 0;
		    }
		  }
		}
	      }
	      break;
	    case 0x0080:
	      // CLEAR
	      // clear printer buffer
//	      print_clear ();
	      if (mode_flags & MODE_PRINTER) {
		memset (cpu.PRN_BUF, ' ', sizeof (cpu.PRN_BUF));
		cpu.PRN_PTR = 0;
	      }
	      break;
	    case 0x0090:
	      // STEP
	      // decrement printer position
	      if (mode_flags & MODE_PRINTER) {
		cpu.PRN_BUF[cpu.PRN_PTR++] = ' ';
		if (cpu.PRN_PTR >= sizeof (cpu.PRN_BUF)) cpu.PRN_PTR = 0;
		if (cpu.PRN_BUSY) {
		  if ((cpu.cycle - cpu.PRN_BUSY) < PRN_BUSY_CYCLE) {
		    cpu.flags |= FLG_BUSY;
		  } else {
		    cpu.PRN_BUSY = 0;
		  }
		}
	      }
	      break;
	    case 0x00A0:
	      // PRINT
	      // print data from buffer
	      if (mode_flags & MODE_PRINTER) {
		int i;
		fprintf (stderr, "\r\t\t");
		for (i = 20; i; )
		  fputc (cpu.PRN_BUF[--i], stderr);
		// force display re-print
		cpu.flags ^= FLG_DISP;
	      }
	      //break;
	    case 0x00B0:
	      // PAPER ADVANCE
	      // scroll paper in printer
	      if (mode_flags & MODE_PRINTER) {
		fputc ('\n', stderr);
		// check if advance buttons (. or @) are pressed
		if ((cpu.key[9] & (1 << 3)) || (cpu.key[0xC] & (1 << 0))) {
		  // use real delay for button-driven paper feed
		  cpu.PRN_BUSY = cpu.cycle;
		  if (!cpu.PRN_BUSY) cpu.PRN_BUSY--;
		}
	      }
	      break;
	    case 0x00C0:
	      // CRDWRITE
	      if (!cpu.CRD_FLAGS) {
		cpu.CRD_FLAGS = CRD_WRITE;
		//cpu.CRD_PTR = 0;
	      }
	      break;
	    case 0x00F0:
	      // RAMOP
	      // next IO contains specification for RAM operation
	      cpu.flags |= FLG_RAM_OP;
	      break;
	  }
	  break;
	case 0x0009:
	  // Set Idle
	  cpu.flags |= FLG_IDLE;
	  break;
	case 0x000A:
	  // CLFB
	  cpu.fB = 0;
	  break;
	case 0x000B:
	  // Test Busy
	  if ((cpu.key[cpu.digit] & (1 << KR_BIT)) || (cpu.flags & FLG_BUSY))
	    cpu.flags &= ~(FLG_COND | FLG_BUSY);
	  break;
	case 0x000C:
	  // EXTKR
	  cpu.KR = (cpu.KR & 0x000F) | cpu.EXT;
	  break;
	case 0x000D:
	  // XKRSR
	  {
	    unsigned short tmp;
	    tmp = cpu.KR;
	    cpu.KR = cpu.SR;
	    cpu.SR = tmp;
	  }
	  break;
	case 0x000E:
	  // NO-OP + peripherals
	  switch (opcode & 0x00F0) {
	    case 0x0000:
	      // FETCH
	      cpu.EXT = LIB[cpu.LIB++] << 4;
	      cpu.flags |= FLG_EXT_VALID;
	      cpu.LIB %= 5000;
	      break;
	    case 0x0010:
	      // LOAD PC
	      cpu.LIB /= 10;
	      cpu.LIB += ((cpu.KR >> 4) & 0xF) * 1000;
	      break;
	    case 0x0020:
	      // UNLOAD PC
	      cpu.EXT = (cpu.LIB % 10) << 4;
	      cpu.flags |= FLG_EXT_VALID;
	      //cpu.LIB = (cpu.LIB / 10) + ((cpu.LIB % 10) * 1000); // address is not wrapped around
	      cpu.LIB /= 10;
	      break;
	    case 0x0030:
	      // FETCH HIGH
	      cpu.EXT = LIB[cpu.LIB] & 0xF0;
	      cpu.flags |= FLG_EXT_VALID;
	      break;
	  }
	  break;
	case 0x000F:
	  // Register
	  switch (opcode & 0x00F0) {
	    case 0x0000:
	      // Store F
	      cpu.flags |= FLG_STORE;
	      // address is taken from last IO result - digit 0
	      cpu.REG_ADDR = cpu.Sout[0];
	      break;
	    case 0x0010:
	      // Recall F
	      cpu.flags |= FLG_RECALL;
	      // address is taken from last IO result - digit 0
	      cpu.REG_ADDR = cpu.Sout[0];
	      break;
	  }
	  break;
      }
      break;
    // ================================
    // ALU operations
    // ================================
    default: {
	const mask_type *mask = &mask_info[(opcode >> 8) & 0x0F];
	static const struct {
	  unsigned char *srcX, *srcY;
	  unsigned char flags;
	} *alu_inp, ALU_OP[32] = {
	  {cpu.A, 0, ALU_ADD},
	  {cpu.A, 0, ALU_SUB},
	  {0, cpu.B, ALU_ADD},
	  {0, cpu.B, ALU_SUB},
	  {cpu.C, 0, ALU_ADD},
	  {cpu.C, 0, ALU_SUB},
	  {0, cpu.D, ALU_ADD},
	  {0, cpu.D, ALU_SUB},
	  {cpu.A, 0, ALU_SHL},
	  {cpu.A, 0, ALU_SHR},
	  {0, cpu.B, ALU_SHL},
	  {0, cpu.B, ALU_SHR},
	  {cpu.C, 0, ALU_SHL},
	  {cpu.C, 0, ALU_SHR},
	  {0, cpu.D, ALU_SHL},
	  {0, cpu.D, ALU_SHR},
	  {cpu.A, cpu.B, ALU_ADD},
	  {cpu.A, cpu.B, ALU_SUB},
	  {cpu.C, cpu.B, ALU_ADD},
	  {cpu.C, cpu.B, ALU_SUB},
	  {cpu.C, cpu.D, ALU_ADD},
	  {cpu.C, cpu.D, ALU_SUB},
	  {cpu.A, cpu.D, ALU_ADD},
	  {cpu.A, cpu.D, ALU_SUB},
	// following needs special approach...
	// -> variable pointers, RAM/SCOM access, R5 access
	  {cpu.A, 0 /*CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)]*/, ALU_ADD}, // IO read
	  {cpu.A, 0 /*CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)]*/, ALU_SUB}, // IO read
	  {0, 0, ALU_ADD}, // IO read: 0 -> SCOM[cpu.REG_ADDR] | RAM[cpu.RAM_ADDR]
	  {0, 0, ALU_SUB},
	  {cpu.C, 0 /*CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)]*/, ALU_ADD}, // IO read
	  {cpu.C, 0 /*CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)]*/, ALU_SUB}, // IO read
	  {0, 0 /*cpu.R5*/, ALU_ADD}, // IO read ??
	  {0, 0 /*cpu.R5*/, ALU_SUB} // IO read ??
	};
	static const struct {
	  unsigned char *dst;
	  char log[4];
	} *alu_out, ALU_DST[8] = {
	  {cpu.A, "A"},
	  {0, "IO"},
	  {0, ""}, // Xch A,B
	  {cpu.B, "B"},
	  {cpu.C, "C"},
	  {0, ""}, // Xch C,D
	  {cpu.D, "D"},
	  {0, ""}  // Xch A,E
	};
	alu_out = &ALU_DST[opcode & 0x07];
	if ((opcode & 0x07) == 0x01)
	  cpu.flags |= FLG_IO_VALID;
	alu_inp = &ALU_OP[(opcode >> 3) & 0x1F];
	switch (opcode & 0x00F8) {
	  default:
	    // generic ALU operation
	    Alu (alu_out->dst, alu_inp->srcX, alu_inp->srcY, mask, alu_inp->flags);
	    break;
	  // process special cases
	  case 0x00C0: // A+-<io>
	    Alu (alu_out->dst, cpu.A, CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)], mask, ALU_ADD);
	    break;
	  case 0x00C8: // A+-<io>
	    Alu (alu_out->dst, cpu.A, CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)], mask, ALU_SUB);
	    break;
	  case 0x00D0: // NO-OP
	    // LOAD from IO / mask
	    if (cpu.flags & FLG_RECALL) {
	      // load from register
	      cpu.flags &= ~FLG_RECALL;
	      Alu (alu_out->dst, 0, SCOM[cpu.REG_ADDR], mask, ALU_ADD);
	    } else
	    if ((cpu.flags & FLG_RAM_READ) && ((mode_flags & MODE_TI_59) || cpu.RAM_ADDR < 60)) {
	      cpu.flags &= ~FLG_RAM_READ;
	      Alu (alu_out->dst, 0, RAM[cpu.RAM_ADDR], mask, ALU_ADD);
	    } else {
	      Alu (alu_out->dst, 0, 0, mask, ALU_ADD);
	    }
	    break;
	  case 0x00D8:
	    // LOAD (negative) from mask
	    // never used for IO load but IMHO it will work the same way
	    Alu (alu_out->dst, 0, 0, mask, ALU_SUB);
	    break;
	  case 0x00E0: // C+-<io>
	    Alu (alu_out->dst, cpu.C, CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)], mask, ALU_ADD);
	    break;
	  case 0x00E8: // C+-<io>
	    Alu (alu_out->dst, cpu.C, CONSTANT[((cpu.KR >> 5) & 0x78) | ((cpu.KR >> 4) & 0x07)], mask, ALU_SUB);
	    break;
	  case 0x00F0: // R5->Adder
	  case 0x00F8: // not used in TI-58, probably different behavior...
	    if (alu_out->dst) {
	      int i;
	      for (i = mask->start+1; i <= mask->end; i++)
		alu_out->dst[i] = 0;
	      alu_out->dst[mask->cpos] = mask->cval;
	      alu_out->dst[mask->start] = cpu.R5;
	      // make BCD correction
	      if (!(opcode & 0x0008))
		Alu (alu_out->dst, 0, alu_out->dst, mask, ALU_ADD);
	      else
		Alu (alu_out->dst, 0, alu_out->dst, mask, ALU_SUB); // not sure with this...
	    }
	    break;
	}
	// EXCHANGE instructions
	switch (opcode & 0x0007) {
	  case 0x0002: // A<->B
	    Xch (cpu.A, cpu.B, mask);
	    break;
	  case 0x0005: // C<->D
	    Xch (cpu.C, cpu.D, mask);
	    break;
	  case 0x0007: // A<->E
	    Xch (cpu.A, cpu.E, mask);
	    break;
	}
	// IO write control
	if (cpu.flags & FLG_STORE) {
	  cpu.flags &= ~FLG_STORE;
	  memcpy (SCOM[cpu.REG_ADDR], cpu.Sout, sizeof (cpu.Sout));
	}
	if (cpu.flags & FLG_RAM_OP) {
	  cpu.flags &= ~FLG_RAM_OP;
	  cpu.RAM_OP = cpu.Sout[0];
	  cpu.RAM_ADDR = /*cpu.IO[5]*100 +*/ cpu.Sout[3]*10 + cpu.Sout[2];
	  if (cpu.RAM_OP == 2) {
	    // clear 1 memory cell
	    memset (RAM[cpu.RAM_ADDR], 0, 16*1);
	  } else
	  if (cpu.RAM_OP == 4) {
	    // clear 10 memory cells
	    memset (RAM[cpu.RAM_ADDR], 0, 16*10);
	  } else
	  if (cpu.RAM_OP == 1) {
	    cpu.flags |= FLG_RAM_WRITE;
	  } else
	  if (cpu.RAM_OP == 0) {
	    cpu.flags |= FLG_RAM_READ;
	  }
	} else
	if (cpu.flags & FLG_RAM_WRITE) {
	  // store to RAM (ALL_MASK)
	  cpu.flags &= ~FLG_RAM_WRITE;
	  memcpy (RAM[cpu.RAM_ADDR], cpu.Sout, sizeof (cpu.Sout));
	}
      }

  if (!(cpu.flags & FLG_IO_VALID))
    memset (cpu.Sout, 0, sizeof (cpu.Sout));

      break;
  }
  cpu.addr++;
  return 1;
}

// ====================================
// SCOM's ROM data
// ------------------------------------
static unsigned short ROM[0x2000];
// ------------------------------------
int load_dump (unsigned short *buf, const char *name) {
FILE *f;
#define	LINELEN	1024
char line[LINELEN];
  if ((f = fopen (name, "rt")) == NULL)
    return 1;
  while (!feof (f)) {
    unsigned addr, data, idx = 0;
    if (!fgets (line, LINELEN, f))
      break;
    if (!sscanf (line, "%X: ", &addr))
      continue;
    while (line[idx] > ' ') idx++;
    while (line[idx] && line[idx] <= ' ') idx++;
    while (sscanf (line+idx, "%X", &data) > 0) {
      buf[addr++] = data;
      while (line[idx] > ' ') idx++;
      while (line[idx] && line[idx] <= ' ') idx++;
    }
  }
  fclose (f);
  return 0;
}

static void cpu_reset (void) {
  // reset cpu variables
  memset (&cpu, 0, sizeof (cpu));
  cpu.flags |= FLG_COND | FLG_DISP;
  // TI-58/59 mode
  if ((mode_flags & MODE_TI_59))
    // TI-59: D10-KR card switch normally closed
    cpu.key[10] |= (1 << KR_BIT);
  else
    // TI-58: D7-KR diode
    cpu.key[7] |= (1 << KR_BIT);
  // printer mode
  if (mode_flags & MODE_PRINTER)
    // printer: D0-KP diode
    cpu.key[0] |= (1 << KP_BIT);
}

///////////////////////////////////////////////////////
// unblocking get c

#include <unistd.h>
#include <sys/select.h>
#include <termios.h>

char prev_key = -1, prev_vkey = -1;
int count = 0;

struct termios orig_termios;

void reset_terminal_mode()
{
    tcsetattr(0, TCSANOW, &orig_termios);
}

void set_conio_terminal_mode()
{
    struct termios new_termios;

    /* take two copies - one for now, one for later */
    tcgetattr(0, &orig_termios);
    memcpy(&new_termios, &orig_termios, sizeof(new_termios));

    /* register cleanup handler, and set the new terminal mode */
    atexit(reset_terminal_mode);
    cfmakeraw(&new_termios);
    tcsetattr(0, TCSANOW, &new_termios);
}

int kbhit()
{
    struct timeval tv = { 0L, 1L };
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(0, &fds);

    if (++count < 100)
      return 0;

    count=0;

    if (prev_key == -1)
      {
        return select(1, &fds, NULL, NULL, &tv);
      }
    else
      {
        return 1;
      }
}

int getch(int *key_down, int *vkey)
{
    int r;
    int res;
    unsigned char c;

    if (prev_key == -1)
      {
        if ((r = read(0, &c, sizeof(c))) < 0) {
          res = r;
        } else {
          res = c;
        }

        // vkey
        if (res == 27)
          {
            r = read(0, &c, sizeof(c));

            if (c == 91)
              {
                unsigned char prev;
                while  (c != 126)
                  {
                    prev = c;
                    r = read(0, &c, sizeof(c));
                  }
                c = prev;
              }
            else
              r = read(0, &c, sizeof(c));

            *vkey = c;
            prev_vkey = c;
            res = 0;
          }
        else
          *vkey = 0;

        prev_key = res;
        *key_down = 1;
      }
    else
      {
        res = prev_key;
        *vkey = prev_vkey;
        *key_down = 0;
        prev_key = -1;
        prev_vkey = -1;
      }

    return res;
}

unsigned ex_cnt;

// ====================================
// Application's main
// ------------------------------------
int main (int argc, char *argv[]) {

  printf ("TI-5x emulator\n(c) 2014 Hynek Sladky\n");
  // parse command line
  ROM[0] = 0;
  for (ex_cnt = 1; ex_cnt < argc; ex_cnt++) {
    if (!strcmp (argv[ex_cnt], "-mode")) {
      // set debug flags
      ex_cnt++;
      if (ex_cnt >= argc) {
	printf ("Parameter for -mode missing!\n");
	return 1;
      }
      sscanf (argv[ex_cnt], "%u", &mode_flags);
      continue;
    } else
    if (!strcmp (argv[ex_cnt], "-help")) {
      // print help
      ROM[0] = 0;
      break;
    } else
    if (!strcmp (argv[ex_cnt], "-lib")) {
      // disassemble library
      ex_cnt = (LIB[0]>>4)*10 + (LIB[0]&0x0F);
      unsigned i;
      for (i = 1; i <= ex_cnt; i++) {
	unsigned addr, end;
	end = i * 2;
	addr = ((LIB[end] >> 4) * 1000) + ((LIB[end] & 0x0F) * 100) + ((LIB[end+1] >> 4) * 10) + (LIB[end+1] & 0x0F);
	end = ((LIB[end+2] >> 4) * 1000) + ((LIB[end+2] & 0x0F) * 100) + ((LIB[end+3] >> 4) * 10) + (LIB[end+3] & 0x0F);
	fprintf (stderr, "========================\nProgram %u\n------------------------\n", i);
	fprintf (stderr, "(%04u=%04u)\n", addr, end);
	for (; addr < end; addr++) {
	  fprintf (stderr, "%04u\t%02X\t%s\n", addr, LIB[addr], libtoken[(LIB[addr]>>4)*10+(LIB[addr]&0x0F)]);
	}
      }
      return 0;
    } else
    if (!strcmp (argv[ex_cnt], "-clib")) {
      // disassemble library
      unsigned i;
      fprintf (stderr, "========================\ninternal programs\n------------------------\n");
      for (i = 0; i < 48*16; i += 2) {
	unsigned char code = CONSTANT[16][i] + 10*CONSTANT[16][i+1];
	fprintf (stderr, "%04u\t%02d\t%s\n", i >> 1, code, libtoken[code]);
      }
      return 0;
    } else
    if (!strcmp (argv[ex_cnt], "-in")) {
      // card input file name
      ex_cnt++;
      if (ex_cnt >= argc) {
	printf ("Parameter for -in missing!\n");
	return 1;
      }
      strcpy (card_input, argv[ex_cnt]);
    } else
    if (!strcmp (argv[ex_cnt], "-out")) {
      // card output file name
      ex_cnt++;
      if (ex_cnt >= argc) {
	printf ("Parameter for -out missing!\n");
	return 1;
      }
      strcpy (card_output, argv[ex_cnt]);
    } else
    if (!strcmp (argv[ex_cnt], "-lst")) {
      // list file name
      FILE *f;
      ex_cnt++;
      if (ex_cnt >= argc) {
	printf ("Parameter for -lst missing!\n");
	return 1;
      }
      if ((f = fopen (argv[ex_cnt], "rt")) == NULL) {
	printf ("Can't open file %s!\n", argv[ex_cnt]);
	return 1;
      }
      printf ("Loading listing file %s...\n", argv[ex_cnt]);
      while (!feof (f)) {
	char line[64];
	unsigned addr, opcode;
	if (!fgets (line, 64, f))
	  break;
	if (sscanf (line, "%u %02X", &addr, &opcode) == 2) {
	  //printf ("%03u %02X\n", addr, opcode);
	  RAM[addr>>3][((addr&0x7)<<1)+0] = opcode & 0x0F;
	  RAM[addr>>3][((addr&0x7)<<1)+1] = (opcode >> 4) & 0x0F;
	}
      }
      fclose (f);
    } else
    if (*argv[ex_cnt] == '-') {
      // unknown option
      printf ("Unknown option \"%s\"!\n", argv[ex_cnt]);
      return 1;
    } else {
      // load dump file
      if (load_dump (ROM, argv[ex_cnt])) {
	printf ("Error reading file %s!\n", argv[ex_cnt]);
	return 1;
      }
    }
  }
  if (!ROM[0]) {
    printf ("Usage: %s ROM_dump.txt [options]\n", argv[0]);
    printf ("options:\n-dbg <value> set debug output level\n-help print help\n-mode <value>\n-in <file> set card input file name\n-out <file> set card output file name\n-log <file> set log file name\n-lst <file> load a program\n");
    return 0;
  }
  // check if card input file is entered for TI-58
  if ((mode_flags & MODE_TI_59)) {
    printf ("TI-59 mode selected.\n");
    if (!*card_input)
      strcpy (card_input, card_output);
  } else {
    printf ("TI-59 mode selected.\n");
    if (*card_input) {
      // load RAM from card file
      FILE *f = fopen (card_input, "rb");
      if (f == NULL) {
	fprintf (stderr, "Can't read file %s!\n", card_input);
      } else {
	fprintf (stderr, "Reading file %s...\n", card_input);
	while (!feof (f)) {
	  unsigned char block[30*8];
	  unsigned offs, i;
	  if (!fread (block, 4, 1, f))
	    break;
	  offs = (block[2] & 0x0F) * 10;
	  if (offs > 30) {
	    fprintf (stderr, "Card size too big!\n");
	    break;
	  }
	  if (!fread (block, 30*8, 1, f))
	    break;
	  for (i = 0; i < 30*8; i++) {
	    RAM[offs+(i>>3)][15-((i&7)<<1)] = block[i] & 0x0F;
	    RAM[offs+(i>>3)][14-((i&7)<<1)] = block[i] >> 4;
	  }
	  if (!fread (block, 2, 1, f))
	    break;
	}
	fclose (f);
      }
    }
  }
  if (mode_flags & MODE_PRINTER)
    printf ("Printer mode selected.\n");

  // print keyboard help
  printf ("[A]=F1        [B]=F2        [C]=F3      [D]=F4       [E]=F5\n"
	  "[2nd]=F8      [INV]=F9      [ln\\log]=L  [CE\\CP]=Back [CLR]=Space\n"
	  "[LRN\\Pgm]=P   [x<>t\\P->R]=X [x^2\\sin]=S [sqrt\\cos]=C [1/x\\tan]=T\n"
	  "[SST\\Ins]=Ins [STO\\CMs]=>   [RCL\\Exc]=< [SUM\\Prd]=&  [Y^x\\Ind]=Y\n"
	  "[BST\\Del]=Del [EE\\Eng]=E    [(\\Fix]=(   [)\\Int]=)    [/\\|x|]=/\n"
	  "[GTO\\Pause]=G [7\\x=t]=7     [8\\Nop]=8   [9\\Op]=9     [x\\Deg]=*\n"
	  "[SBR\\Lbl]=B   [4\\x>=t]=4    [5\\S+]=5    [6\\avg]=6    [-\\Rad]=-\n"
	  "[RST\\StFlg]=R [1\\IfFlg]=1   [2\\D.MS]=2  [3\\pi]=3     [+\\Grad]=+\n"
	  "[R/S]=$       [0\\Dsz]=0     [.\\Adv]=.   [+/-\\Prt]=N  [=\\List]=Enter\n\n");
  if (mode_flags & MODE_PRINTER)
    printf ("----------\n"
	    "PRINT=#        TRACE=!        ADVANCE=@\n");
  // reset CPU
  cpu_reset ();

  ex_cnt = 0;
  loop_run();
  printf ("\nFinished.\n");
  return 0;
}

int display(void)
{
  int res = 0;

    if (!cpu.digit) {
      static char disp_filter = 0;
      if (cpu.flags & FLG_IDLE) {
	// display enabled
	static unsigned char dA[16], dB[16];
	int i;
	if (cpu.flags & FLG_DISP) {
	  // check difference between current and saved registers
	  for (i = 13; i >= 2; i--) {
	    if (dA[i] != cpu.A[i] || dB[i] != cpu.B[i]) {
	      cpu.flags &= ~FLG_DISP;
	      break;
	    }
	  }
	  if ((cpu.flags ^ cpu.fA) & FLG_DISP_C)
	    cpu.flags &= ~FLG_DISP;
          /// ????? cpu.flags &= ~FLG_DISP; (display each time, but 0)
	}
	if (!(cpu.flags & FLG_DISP)) {
	  int zero = 1;
	  cpu.flags |= FLG_DISP;
	  putchar ('\r');
	  if (cpu.fA & 0x4000) {
	    putchar ('C');
	    cpu.flags |= FLG_DISP_C;
	  } else {
	    cpu.flags &= ~FLG_DISP_C;
	  }

          /* display of TI is there */
         for (i = 13; i >= 2; i--) {
           dA[i] = cpu.A[i];
           dB[i] = cpu.B[i];
           if (i == 3 || cpu.R5 == i || cpu.B[i] >= 8)
             zero = 0;
           if (i == 2)
             zero = 1;
           if (cpu.B[i] == 7 || cpu.B[i] == 3 || (cpu.B[i] <= 4 && zero && !cpu.A[i]))
             putchar (' ');
           else
           if (cpu.B[i] == 6 || (cpu.B[i] == 5 && !cpu.A[i]))
             putchar ('-');
           else
           if (cpu.B[i] == 5)
             putchar ('o');
           else
           if (cpu.B[i] == 4)
             putchar ('\'');
           else
           if (cpu.B[3] == 2)
             putchar ('"');
           else {
             putchar ('0' + cpu.A[i]);
             if (cpu.A[i])
               zero = 0;
           }
           if (cpu.R5 == i)
             putchar ('.');
         }

	  putchar ('|'); putchar (' ');
          res = 1;
	}
	disp_filter = 0;
      } else
      if (disp_filter < 3)
	disp_filter++;
      else {
	// display disabled
	if ((cpu.flags & FLG_DISP) /*|| (!cpu.fA && (cpu.flags & FLG_DISP_C))*/ || (cpu.fA && !(cpu.flags & FLG_DISP_C))) {
	  cpu.flags &= ~FLG_DISP;
	  if (cpu.fA) {
	    printf ("\rC            |");
	    cpu.flags |= FLG_DISP_C;
	  } else {
	    printf ("\r             |");
	    cpu.flags &= ~FLG_DISP_C;
	  }
	}
      }
    }

    return res;
}

void loop_run(void)
{
  int key_down = 0, count=0;
  char AsciiChar = 0, keep_char=0, vkey=0;

  while (1)
    {
      if (!execute (ROM[cpu.addr]))
        continue;

      display();

      {
        fflush(0);
        set_conio_terminal_mode();

        if (kbhit())
          {
            AsciiChar = getch(&key_down, &vkey);
            reset_terminal_mode();
            if (AsciiChar == 3)
              {
                printf ("ctrl-c exit!!!!");
                break;
              }

            handle_key (AsciiChar, vkey, &key_down);
          }
      }
      reset_terminal_mode();
    }
}

void handle_key(char AsciiChar, char vkey, char *key_down)
{
  int size;

#define VK_F1 80
#define VK_F2 81
#define VK_F3 82
#define VK_F4 83
#define VK_F5 53
#define VK_F6 55
#define VK_F7 56
#define VK_F8 57
#define VK_F9 48
#define VK_INSERT 50
#define VK_DELETE 51

          //          printf("\ngetchar: '%c' %d (vk=%d) isdown:%d\n", AsciiChar, AsciiChar, vkey, key_down);
#define	KEY_INVERT	0x01
#define	KEY_ONOFF	0x02

  static const struct {
    unsigned char key_code;
    unsigned char flags;
    unsigned char ascii;
    unsigned vkey;
  } key_table[] = {
    {0x11, 0, 0, VK_F1},     {0x21, 0, 0, VK_F2}, {0x31, 0, 0, VK_F3}, {0x51, 0, 0, VK_F4}, {0x61, 0, 0, VK_F5},
    {0x12, 0, 0, VK_F8},     {0x22, 0, 0, VK_F9}, {0x32, 0, 'l', 0},   {0x52, 0, '\b', 0},  {0x62, 0, ' ', 0},
    {0x13, 0, 'p', 0}, 	     {0x23, 0, 'x', 0},   {0x33, 0, 's', 0},   {0x53, 0, 'c', 0},   {0x63, 0, 't', 0},
    {0x14, 0, 0, VK_INSERT}, {0x24, 0, '>', 0},   {0x34, 0, '<', 0},   {0x54, 0, '&', 0},   {0x64, 0, 'y', 0},
    {0x15, 0, 0, VK_DELETE}, {0x25, 0, 'e', 0},   {0x35, 0, '(', 0},   {0x55, 0, ')', 0},   {0x65, 0, '/', 0},
    {0x16, 0, 'g', 0},       {0x26, 0, '7', 0},   {0x36, 0, '8', 0},   {0x56, 0, '9', 0},   {0x66, 0, '*', 0},
    {0x17, 0, 'b', 0},       {0x27, 0, '4', 0},   {0x37, 0, '5', 0},   {0x57, 0, '6', 0},   {0x67, 0, '-', 0},
    {0x18, 0, 'r', 0},       {0x28, 0, '1', 0},   {0x38, 0, '2', 0},   {0x58, 0, '3', 0},   {0x68, 0, '+', 0},
    {0x19, 0, '$', 0},       {0x29, 0, '0', 0},   {0x39, 0, '.', 0},   {0x59, 0, 'n', 0},   {0x69, 0, '=' /*'\r'*/, 0},
    // printer buttons
    {0x2C, 0, '#', 0}, // PRINT
    {0x2F, KEY_ONOFF, '!', 0}, // TRACE
    {0x0C, 0, '@', 0}, // ADVANCE
    // card buttons
    {0x4A, KEY_INVERT, '~', 0}, // card inserted
    {0}
  };

  for (size = 0; key_table[size].ascii || key_table[size].vkey; size++) {
    if ((key_table[size].ascii && key_table[size].ascii == AsciiChar) ||
        (key_table[size].vkey && key_table[size].vkey == vkey)) {
      if (key_table[size].flags & KEY_INVERT)
        *key_down = *key_down==1?0:1;
      if (*key_down == 1) {
        if (key_table[size].flags & KEY_ONOFF)
          cpu.key[key_table[size].key_code & 0x0F] ^= 1 << ((key_table[size].key_code >> 4) & 0x07);
        else
          cpu.key[key_table[size].key_code & 0x0F] |= 1 << ((key_table[size].key_code >> 4) & 0x07);
      } else {
        if (!(key_table[size].flags & KEY_ONOFF))
          cpu.key[key_table[size].key_code & 0x0F] &= ~(1 << ((key_table[size].key_code >> 4) & 0x07));
      }
      break;
    }
  }
}
