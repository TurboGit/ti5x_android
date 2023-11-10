all:
	gcc -o emul emul.c Master.c Const.c

win32:
	C_INCLUDE_PATH=/home/obry/dev/OpenSource/builds/install/i686-pc-mingw32/include \
		i686-pc-mingw32-gcc -o emul32.exe emul.c Master.c Const.c
	cp emul32.exe ~/Public/ti59

win64:
	C_INCLUDE_PATH=/home/obry/dev/OpenSource/builds/install/x86_64-pc-mingw32/include \
		x86_64-pc-mingw32-gcc -o emul64.exe emul.c Master.c Const.c
	cp emul64.exe ~/Public/ti59
