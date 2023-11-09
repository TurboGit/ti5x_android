# Compile emulator
------------------

$ make

# Run interactively
-------------------

./emul dump.txt

# Load & Run a program
----------------------

./emul dump.txt -lst code.lst

And do a run R/S: enter $

# Format of .lst file
---------------------

  aaa cc (where aaa the address and cc the opcode).

Example: 1 + 4 = R/S

  000 01
  001 85
  002 04
  003 95
  004 91

# Create a .lst from Ti-95 src program
--------------------------------------

$ cat progxx.src | ../util/assemble | tr ' ' '\n' | while read c; do printf "%03d %s\n" $n $c; (( n++ )) ; done > progxx.lst
