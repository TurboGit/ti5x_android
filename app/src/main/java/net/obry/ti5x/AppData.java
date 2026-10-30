package net.obry.ti5x;

import android.app.AlertDialog;
import android.os.Environment;

import java.io.File;

public class AppData {

  private android.content.Context AppDataContext;

  public AppData
      (
          android.content.Context ctx
      ) {
    //  This context is needed so we can call SavePaper (and other functions) properly
    AppDataContext = ctx;
  }

  String GetAppDataDir(String dir) {
    File documents = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS);

    File appDir = new File(documents, "Ti5x");

    if (!appDir.exists() && !appDir.mkdirs()) {
      new AlertDialog.Builder(AppDataContext)
          .setMessage("Cannot access " + appDir.getAbsolutePath())
          .setPositiveButton("OK", null)
          .show();
    }

    appDir = new File(documents, "Ti5x/" + dir);

    if (!appDir.exists() && !appDir.mkdirs()) {
      new AlertDialog.Builder(AppDataContext)
          .setMessage("Cannot access " + appDir.getAbsolutePath())
          .setPositiveButton("OK", null)
          .show();
    }

    return appDir.getAbsolutePath();
  }

  String GetAppDataPrinterDir(){
    return GetAppDataDir(Persistent.PrinterDir);
  }

  String GetAppDataExportDir(){
    return GetAppDataDir(Persistent.DataDir);
  }

  String GetAppDataProgramsDir(){
    return GetAppDataDir(Persistent.ProgramsDir);
  }

  String GetAppDataBankDir(){
    return GetAppDataDir(Persistent.BankDir);
  }
  String GetAppDataPrinterFile(String Filename) {
    return GetAppDataPrinterDir() + "/" + Filename;
  }

  String GetAppDataExportFile(String Filename){
    return GetAppDataExportDir() + "/" + Filename;
  }

  String GetAppDataProgramsFile(String Filename){
    return GetAppDataProgramsDir() + "/" + Filename;
  }

  String GetAppDataBankFile(String Filename){
    return GetAppDataBankDir() + "/" + Filename;
  }
}
