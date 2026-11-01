package net.obry.ti5x;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AppData {

  private Context AppDataContext;
  private static final String PREFS_NAME = "Ti5xPrefs";
  private static final String KEY_SAF_URI = "saf_tree_uri";

  public AppData(Context ctx) {
    AppDataContext = ctx;
  }

  public static boolean hasUri(Context ctx) {
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String uriStr = prefs.getString(KEY_SAF_URI, null);
    if (uriStr != null) {
      try {
        Uri uri = Uri.parse(uriStr);
        for (UriPermission perm : ctx.getContentResolver().getPersistedUriPermissions()) {
          if (perm.getUri().equals(uri) && perm.isReadPermission() && perm.isWritePermission()) {
            return true;
          }
        }
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  public static void setUri(Context ctx, Uri uri) {
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().putString(KEY_SAF_URI, uri.toString()).apply();
  }

  public static void clearUri(Context ctx) {
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().remove(KEY_SAF_URI).apply();
  }

  private DocumentFile getRootDir() {
    SharedPreferences prefs = AppDataContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String uriStr = prefs.getString(KEY_SAF_URI, null);
    if (uriStr != null) {
      try {
        Uri uri = Uri.parse(uriStr);
        DocumentFile root = DocumentFile.fromTreeUri(AppDataContext, uri);
        if (root != null && root.canRead() && root.canWrite()) {
          DocumentFile ti5x = root.findFile("Ti5x");
          if (ti5x == null) {
            ti5x = root.createDirectory("Ti5x");
          }
          return ti5x;
        }
      } catch (Exception e) {
        // fallback
      }
    }
    return null;
  }

  private DocumentFile getSubDirFile(String dir, String fileName, boolean create) {
    DocumentFile root = getRootDir();
    if (root != null) {
      DocumentFile subDir = root.findFile(dir);
      if (subDir == null && create) {
        subDir = root.createDirectory(dir);
      }
      if (subDir != null) {
        DocumentFile file = subDir.findFile(fileName);
        if (file == null && create) {
          file = subDir.createFile("application/octet-stream", fileName);
        }
        return file;
      }
    }
    return null;
  }

  private File getFallbackFile(String dir, String fileName) {
    File documents = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS);
    if (documents == null) {
      documents = AppDataContext.getFilesDir();
    }
    File appDir = new File(documents, "Ti5x/" + dir);
    if (!appDir.exists()) {
      appDir.mkdirs();
    }
    return new File(appDir, fileName);
  }

  public InputStream getInputStream(String dir, String fileName) {
    try {
      DocumentFile doc = getSubDirFile(dir, fileName, false);
      if (doc != null && doc.exists()) {
        return AppDataContext.getContentResolver().openInputStream(doc.getUri());
      } else {
        File f = getFallbackFile(dir, fileName);
        if (f.exists()) {
          return new java.io.FileInputStream(f);
        }
      }
    } catch (Exception e) {
      // ignore
    }
    return null;
  }

  public OutputStream getOutputStream(String dir, String fileName, boolean append) {
    try {
      DocumentFile doc = getSubDirFile(dir, fileName, true);
      if (doc != null) {
        String mode = append ? "wa" : "w";
        return AppDataContext.getContentResolver().openOutputStream(doc.getUri(), mode);
      } else {
        File f = getFallbackFile(dir, fileName);
        return new java.io.FileOutputStream(f, append);
      }
    } catch (Exception e) {
      // ignore
    }
    return null;
  }

  public boolean fileExists(String dir, String fileName) {
    DocumentFile doc = getSubDirFile(dir, fileName, false);
    if (doc != null && doc.exists()) {
      return true;
    }
    File f = getFallbackFile(dir, fileName);
    return f.exists();
  }

  public boolean deleteFile(String dir, String fileName) {
    DocumentFile doc = getSubDirFile(dir, fileName, false);
    if (doc != null && doc.exists()) {
      return doc.delete();
    }
    File f = getFallbackFile(dir, fileName);
    return f.delete();
  }

  public String[] listFiles(String dir, String[] extensions) {
    List<String> list = new ArrayList<>();
    try {
      DocumentFile root = getRootDir();
      if (root != null) {
        DocumentFile subDir = root.findFile(dir);
        if (subDir != null) {
          for (DocumentFile file : subDir.listFiles()) {
            if (file.isFile() && file.getName() != null) {
              String name = file.getName();
              if (extensions == null) {
                list.add(name);
              } else {
                for (String ext : extensions) {
                  if (name.endsWith(ext)) {
                    list.add(name);
                    break;
                  }
                }
              }
            }
          }
        }
      } else {
        File documents = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS);
        if (documents == null) {
          documents = AppDataContext.getFilesDir();
        }
        File appDir = new File(documents, "Ti5x/" + dir);
        if (appDir.exists() && appDir.isDirectory()) {
          File[] files = appDir.listFiles();
          if (files != null) {
            for (File file : files) {
              if (file.isFile()) {
                String name = file.getName();
                if (extensions == null) {
                  list.add(name);
                } else {
                  for (String ext : extensions) {
                    if (name.endsWith(ext)) {
                      list.add(name);
                      break;
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      // ignore
    }
    String[] res = new String[list.size()];
    list.toArray(res);
    return res;
  }

  String GetAppDataDir(String dir) {
    File documents = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS);
    if (documents == null) {
      documents = AppDataContext.getFilesDir();
    }
    File appDir = new File(documents, "Ti5x/" + dir);
    if (!appDir.exists()) {
      appDir.mkdirs();
    }
    return appDir.getAbsolutePath();
  }
  String GetAppDataFile(String filename) {
    String cleanName = filename != null ? filename : "";
    if (cleanName.startsWith("/")) {
      cleanName = cleanName.substring(1);
    }
    String subDir = Persistent.ProgramsDir;
    if (cleanName.startsWith(Persistent.ProgramsDir + "/")) {
      cleanName = cleanName.substring(Persistent.ProgramsDir.length() + 1);
    } else if (cleanName.startsWith(Persistent.DataDir + "/")) {
      subDir = Persistent.DataDir;
      cleanName = cleanName.substring(Persistent.DataDir.length() + 1);
    } else {
      int idx = cleanName.lastIndexOf('/');
      if (idx >= 0) {
        String prefix = cleanName.substring(0, idx);
        if (prefix.equals(Persistent.DataDir)) {
          subDir = Persistent.DataDir;
        }
        cleanName = cleanName.substring(idx + 1);
      }
    }
    File documents = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS);
    if (documents == null) {
      documents = AppDataContext.getFilesDir();
    }
    File appDir = new File(documents, "Ti5x/" + subDir + "/" + cleanName);
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

  public Context getContext() {
    return AppDataContext;
  }

  public java.io.InputStream getInputStreamForPath(String path) {
    if (path == null) {
      return null;
    }
    try {
      String cleanName = path;
      if (cleanName.startsWith("/")) {
        cleanName = cleanName.substring(1);
      }
      String dir = Persistent.ProgramsDir;
      if (cleanName.startsWith(Persistent.ProgramsDir + "/")) {
        cleanName = cleanName.substring(Persistent.ProgramsDir.length() + 1);
      } else if (cleanName.startsWith(Persistent.DataDir + "/")) {
        dir = Persistent.DataDir;
        cleanName = cleanName.substring(Persistent.DataDir.length() + 1);
      } else {
        int idx = cleanName.lastIndexOf('/');
        if (idx >= 0) {
          String prefix = cleanName.substring(0, idx);
          if (prefix.equals(Persistent.DataDir) || prefix.endsWith("/" + Persistent.DataDir)) {
            dir = Persistent.DataDir;
          }
          cleanName = cleanName.substring(idx + 1);
        }
      }

      java.io.InputStream in = getInputStream(dir, cleanName);
      if (in == null) {
        String fallbackDir = dir.equals(Persistent.ProgramsDir) ? Persistent.DataDir : Persistent.ProgramsDir;
        in = getInputStream(fallbackDir, cleanName);
      }

      if (in == null) {
        try {
          android.net.Uri uri = android.net.Uri.parse(path);
          in = AppDataContext.getContentResolver().openInputStream(uri);
        } catch (Exception ignored) {}
      }

      if (in == null) {
        java.io.File f = new java.io.File(path);
        if (f.exists() && f.isFile()) {
          in = new java.io.FileInputStream(f);
        }
      }

      return in;
    } catch (Exception e) {
      return null;
    }
  }
}
