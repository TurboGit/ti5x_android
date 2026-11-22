/*
    Saving/loading of programs, program libraries and calculator state

    Copyright 2011 Lawrence D'Oliveiro <ldo@geek-central.gen.nz>.
    Copyright 2015 Pascal Obry <pascal@obry.net>.

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

import net.obry.ti5x.engine.State;
import net.obry.ti5x.engine.Constants;
import net.obry.ti5x.engine.DataFormatException;
import net.obry.ti5x.engine.Disassembler;
import net.obry.ti5x.engine.ProgramFiles;

import java.io.File;

public class Persistent {
  /* saving/loading of libraries and calculator state. */
  static final String CalcMimeType = ProgramFiles.CalcMimeType;
  private static final String StateExt = ".ti5s"; /* for saved calculator state */
  static final String ProgExt = ".ti5p"; /* for saved user program */
  static final String LibExt = ".ti5l"; /* for library */
  static final String BankExt = Constants.BankExt;
  static final String DataExt = Constants.DataExt;
  static final String TextExt = ".txt"; /* for exported program text */
  static final String ProgramsDir = Constants.ProgramsDir;
  static final String DataDir = Constants.DataDir;
  static final String PrinterDir = "Paper"; /* where to save exported paper */
  static final String BankDir = Constants.BankDir;
  static final String StateDir = "State"; /* where to save state files */
  static final String[] ExternalCalcDirectories =
      /* where to load programs/libraries from */
     {
        ProgramsDir,
        DataDir,
     };
  static final String[] ExternalDataDirectories =
      /* where to load data files from */
     {
        DataDir,
     };
  static final String[] LikelyDataExts =
      /* most likely extensions for import data files */
     {
        ".dat",
        ".txt",
     };
  static final String SavedStateName = "state" + StateExt;
  /* for saved state other than loaded library */
  static final String SavedLibName = "module" + LibExt;
  /* loaded library saved separately so it doesn't have to be re-saved
     every time state changes */

  public static String EnsureDirExists
     (
        android.content.Context ctx,
        String DirName,
        String FileName
     )
  {
    final String Dir =
        new java.io.File(ctx.getExternalFilesDir(null), DirName)
            .getAbsolutePath();
    new java.io.File(Dir).mkdirs();

    return Dir + (FileName == "" ? "" : "/" + FileName);
  }

  public static void LoadBankFile
      (
          android.content.Context ctx,
          int N,
          int CardId,
          String FileName,
          State Calc
      ) {

    if (!Global.DataStorage.fileExists(BankDir, FileName)) {
      final String ToFile = Global.DataStorage.GetAppDataBankFile(FileName);
      if(!new File(ToFile).isFile()) {
        Calc.SetErrorState(true);
        return;
      }
    }

    // open file
    java.io.InputStream In;
    try {
      In = Global.DataStorage.getInputStream(BankDir, FileName);
      if (In == null) {
        final String ToFile = Global.DataStorage.GetAppDataBankFile(FileName);
        In = new java.io.FileInputStream(ToFile);
      }
    } catch (java.io.FileNotFoundException Failed) {
      throw new RuntimeException
        (
            "ti5x.Persistent.LoadBankFile open error " + Failed.toString()
        );
    }
    ProgramFiles.LoadBank(Calc, In, N, CardId);
  }

  public static void SaveBankFile
      (
          android.content.Context ctx,
          int N,
          int Id,
          String FileName,
          State Calc
      ) {

    // create BankDir
    final String ToFile = Global.DataStorage.GetAppDataBankFile(FileName);

    java.io.OutputStream Out;
    try {
      Out = Global.DataStorage.getOutputStream(BankDir, FileName, false);
      if (Out == null) {
        Out = new java.io.FileOutputStream(ToFile);
      }
    } catch (Exception Failed) {
      throw new RuntimeException
          (
              "ti5x.Persistent.SaveBankFile create error " + Failed.toString()
          );
    }

    ProgramFiles.SaveBank(Calc, Out, N, Id);
  }

  static void Save
     (
        ButtonGrid Buttons, /* ignored unless CalcState */
        State Calc,
        boolean Libs, /* save currently-loaded library */
        boolean CalcState,
          /* true to save entire calculator state (apart from library),
             false to only save program */
        String ToFile
     ) {
    java.io.OutputStream Out = null;
    try {
      File f = new File(ToFile);
      String parentName = f.getParentFile() != null ? f.getParentFile().getName() : ProgramsDir;
      String fileName = f.getName();
      Out = Global.DataStorage.getOutputStream(parentName, fileName, false);
      if (Out == null) {
        Out = new java.io.FileOutputStream(ToFile);
      }
    } catch (Exception Failed) {
      throw new RuntimeException
         (
            "ti5x.Persistent.Save create error " + Failed.toString()
         );
    }
    ProgramFiles.Save(Calc, Buttons, Libs, CalcState, Out);
    try {
      Out.flush();
      Out.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException("ti5x.Persistent.Save error " + Failed.toString());
    }
  }

  static void ExportProgramText
     (
        State Calc,
        String ToFile
     ) {
    /* saves the currently selected program bank as source text. */
    java.io.OutputStream Out = null;
    try {
      final File f = new File(ToFile);
      final String ParentName = f.getParentFile() != null ? f.getParentFile().getName() : ProgramsDir;
      Out = Global.DataStorage.getOutputStream(ParentName, f.getName(), false);
      if (Out == null) {
        Out = new java.io.FileOutputStream(ToFile);
      }
    } catch (Exception Failed) {
      throw new RuntimeException("ti5x export program create error " + Failed.toString());
    }

    final byte[] Program = Calc.Bank[Calc.CurBank].Program != null
        ? Calc.Bank[Calc.CurBank].Program
        : Calc.Program;
    final String Text = Disassembler.disassemble(Program);

    try (java.io.PrintStream PS = new java.io.PrintStream(Out)) {
      PS.print(Text);
      PS.flush();
    } catch (Exception Failed) {
      throw new RuntimeException("ti5x export program error " + Failed.toString());
    }
  }

  public static class Load extends Global.Task {
    private final String FromFile;
    private final boolean Libs;
    private final boolean CalcState;
    private final ButtonGrid Buttons;
    private final State Calc;

    public Load
       (
          String FromFile,
          boolean Libs, /* true to load nonzero program banks, false to load bank 0 */
          boolean CalcState, /* true to load all state (including all available program banks) */
          ButtonGrid Buttons,
          State Calc
       ) {
      this.FromFile = FromFile;
      this.Libs = Libs;
      this.CalcState = CalcState;
      this.Buttons = Buttons;
      this.Calc = Calc;
    }

    @Override
    public boolean PreRun() {
      if (CalcState) {
        Buttons.Reset();
        Calc.Engine.Keys.Reset();
        Calc.Reset(Libs);
      } else if (Libs) {
        Calc.ResetLibs();
      } else {
        Calc.ResetLabels();
      }
      return true;
    }

    @Override
    public void BGRun() {
      try {
        final java.io.InputStream In = Global.DataStorage.getInputStreamForPath(FromFile);
        if (In == null) {
          throw new DataFormatException("I/O error: File not found: " + FromFile);
        }
        ProgramFiles.Load(Calc, Buttons, In, Libs, CalcState);
      } catch (DataFormatException Failure) {
        SetStatus(-1, Failure);
      }
    }

    @Override
    public void PostRun() {
      if (TaskFailure == null) {
        if (Calc != null) {
          if (Calc.Bank[Calc.CurBank] == null)
            Calc.CurBank = 1;
          Calc.SelectProgram(Calc.CurBank, false);
          switch (Calc.CurState) {
            case State.ResultState:
              Calc.SetX(Calc.X, false);
              break;
          }
          if (State.inError) {
            Calc.SetX(Calc.X, false);
            Calc.SetErrorState(false);
          }
          Calc.SetProgMode(Calc.ProgMode);
        }
        if (Buttons != null) {
          if (CalcState) {
            Buttons.SetFeedbackType(Buttons.FeedbackType);
          }
          Buttons.invalidate();
        }
        // And reset status to current one
        Global.setStatusDisplay();
      }
    }
  }

  public static class LoadBuiltin extends Global.Task {
    /* loads the included Master Library module into the calculator state. */
    private final Load DoLoad;
    private final android.content.Context ctx;
    private final int SelId;
    private final boolean IsLib;
    /* Unfortunately java.util.zip.ZipFile can't read from an arbitrary InputStream,
      so I need to make a temporary copy of the master library out of my raw resources. */
    private final String TempLibName = "temp.ti5x"; /* name for temporary copy */

    LoadBuiltin
       (
          android.content.Context ctx,
          boolean IsLib,
          int Id // Id number for the built-in program to load
       ) {
      this.ctx = ctx;
      this.IsLib = IsLib;
      this.SelId = Id;
      final String TempLibFile = ctx.getFilesDir().getAbsolutePath() + "/" + TempLibName;
      DoLoad = new Load
         (
            TempLibFile,
            IsLib,
            false,
            Global.Buttons,
            Global.Calc
         );
    }

    @Override
    public boolean PreRun() {
      return DoLoad.PreRun();
    }

    @Override
    public void BGRun() {
      BuiltinLibrary Prog = IsLib ? Main.BuiltinLibraries[SelId] : Main.BuiltinPrograms[SelId];
      try {
        final java.io.InputStream LibFile = Prog.getInputStream(ctx);
        final java.io.OutputStream TempLib =
           ctx.openFileOutput(TempLibName, android.content.Context.MODE_PRIVATE);
        {
          byte[] Buffer = new byte[2048]; /* some convenient size */
          for (; ; ) {
            final int NrBytes = LibFile.read(Buffer);
            if (NrBytes <= 0)
              break;
            TempLib.write(Buffer, 0, NrBytes);
          }
        }
        TempLib.flush();
        TempLib.close();
      } catch (java.io.FileNotFoundException Failed) {
        throw new RuntimeException("ti5x " + Prog.getName(ctx)
           + " load failed: " + Failed.toString());
      } catch (java.io.IOException Failed) {
        throw new RuntimeException("ti5x " + Prog.getName(ctx)
           + " load failed: " + Failed.toString());
      }
      DoLoad.BGRun();
      ctx.deleteFile(TempLibName);
    }

    @Override
    public void PostRun() {
      DoLoad.PostRun();
      // we have loaded a new built-in library, select its first program
      Global.Calc.SelectProgram(1, false);
    }
  }

  static void SaveState
     (
        android.content.Context ctx,
        boolean SaveLib /* true to save loaded library, false to save rest of state */
     ) {
    /* saves the current calculator state for later restoration. */
    java.io.FileOutputStream CurSave;
    try {
      final String FileName = SaveLib ? SavedLibName : SavedStateName;
      final String StateName = EnsureDirExists(ctx, StateDir, FileName);

      CurSave = new java.io.FileOutputStream(StateName);
    } catch (java.io.FileNotFoundException Eh) {
      throw new RuntimeException("ti5x save-state create error " + Eh.toString());
    }
    ProgramFiles.Save
       (
          Global.Calc,
          Global.Buttons,
          SaveLib,
          !SaveLib,
          CurSave
       );
    try {
      CurSave.flush();
      CurSave.close();
    } catch (java.io.IOException Failed) {
      throw new RuntimeException
         (
            "ti5x state save error " + Failed.toString()
         );
    }
  }

  public static class RestoreState extends Global.Task {
    /* restores the entire calculator state, using the previously-saved
       state if available, otherwise (re)initializes to default state. */
    private final android.content.Context ctx;

    private static final int LOAD_LIB = 0;
    private static final int LOAD_STATE = 1;
    private static final int LOAD_MASTER = 2;
    private static final int SAVE_STATE = 3;
    private static final int RESTORE_DONE = 4;
    private int Restoring;

    private boolean RestoredLib;
    String StateFile = null;
    private Global.Task Subtask;

    private RestoreState
       (
          android.content.Context ctx,
          int Restoring,
          boolean RestoredLib
       ) {
      this.ctx = ctx;
      this.Restoring = Restoring;
      this.RestoredLib = RestoredLib;
      Subtask = null;
    }

    RestoreState
       (
          android.content.Context ctx
       ) {
      this(ctx, LOAD_LIB, false);
    }

    @Override
    public boolean PreRun() {
      for (; ; ) {
        switch (Restoring) {
          case LOAD_LIB:
          case LOAD_STATE:
            /* load library before rest of state, otherwise Calc.SelectProgram(Calc.CurBank)
               call (above) will trigger error on nonexistent bank */

            final boolean LoadingLib = Restoring == LOAD_LIB;
            final String FileName = LoadingLib ? SavedLibName : SavedStateName;
            final String StateFile = EnsureDirExists(ctx, StateDir, FileName);

            if (new java.io.File(StateFile).exists()) {
              Subtask = new Load
                 (
                    StateFile,
                    LoadingLib,
                    !LoadingLib,
                    Global.Buttons,
                    Global.Calc
                 );
            }
            break;
          case LOAD_MASTER:
            if (!RestoredLib) {
              /* initialize state to include Master Library */
              Subtask = new LoadBuiltin(ctx, true, Main.BUILTIN_MASTER_LIBRARY_INDEX);
            }
            break;
          case SAVE_STATE:
            /* save newly-initialized state */
            Subtask = new Global.Task() {
              @Override
              public void BGRun() {
                SaveState(ctx, true);
              } /*BGRun*/
            } /*Global.Task*/;
            break;
        }
        if (Subtask != null || Restoring >= LOAD_MASTER)
          break;
        ++Restoring;
      }
      return Subtask != null && Subtask.PreRun();
    }

    @Override
    public void BGRun() {
      if (Subtask != null) {
        Subtask.BGRun();
        if (Subtask.TaskFailure == null && Restoring == LOAD_LIB) {
          RestoredLib = true;
        }
      }
    }

    @Override
    public void PostRun() {
      if (Subtask != null) {
        if (Subtask.TaskFailure != null && StateFile != null) {
          System.err.printf
             (
                "ti5x failure to reload state from file \"%s\": %s\n",
                StateFile,
                Subtask.TaskFailure.toString()
             );
        }
        Subtask.PostRun();
        if (Restoring != RESTORE_DONE) {
          /* run the next stage */
          Global.StartBGTask(new RestoreState(ctx, Restoring + 1, RestoredLib), null);
        }
      }
    }
  }

  static void ResetState
     (
        android.content.Context ctx
     ) {
    /* wipes saved state. */
    ctx.deleteFile(SavedStateName);
    ctx.deleteFile(SavedLibName);
  }
}
