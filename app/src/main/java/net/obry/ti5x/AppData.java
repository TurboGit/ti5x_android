package net.obry.ti5x;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppData {

  private Context AppDataContext;
  private static final String PREFS_NAME = "Ti5xPrefs";
  private static final String KEY_SAF_URI = "saf_tree_uri";

  private DocumentFile cachedRootDir = null;
  private String cachedUriStr = null;
  private final Map<String, DocumentFile> cachedSubDirs = new HashMap<>();

  public AppData(Context ctx) {
    AppDataContext = ctx;
  }

  public synchronized void clearCache() {
    cachedRootDir = null;
    cachedUriStr = null;
    cachedSubDirs.clear();
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
    if (Global.DataStorage != null) {
      Global.DataStorage.clearCache();
    }
  }

  public static void clearUri(Context ctx) {
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().remove(KEY_SAF_URI).apply();
    if (Global.DataStorage != null) {
      Global.DataStorage.clearCache();
    }
  }

  private synchronized DocumentFile getRootDir() {
    SharedPreferences prefs = AppDataContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String uriStr = prefs.getString(KEY_SAF_URI, null);
    if (uriStr == null) {
      clearCache();
      return null;
    }
    if (cachedRootDir != null && uriStr.equals(cachedUriStr) && cachedRootDir.exists()) {
      return cachedRootDir;
    }
    try {
      Uri uri = Uri.parse(uriStr);
      DocumentFile root = DocumentFile.fromTreeUri(AppDataContext, uri);
      if (root != null && root.canRead() && root.canWrite()) {
        DocumentFile ti5x = root.findFile("Ti5x");
        if (ti5x == null) {
          ti5x = root.createDirectory("Ti5x");
        }
        cachedUriStr = uriStr;
        cachedRootDir = ti5x;
        cachedSubDirs.clear();
        return ti5x;
      }
    } catch (Exception e) {
      clearCache();
    }
    return null;
  }

  private synchronized DocumentFile getSubDir(String dir, boolean create) {
    DocumentFile root = getRootDir();
    if (root == null) {
      return null;
    }
    DocumentFile subDir = cachedSubDirs.get(dir);
    if (subDir != null && subDir.exists()) {
      return subDir;
    }
    subDir = root.findFile(dir);
    if (subDir == null && create) {
      subDir = root.createDirectory(dir);
    }
    if (subDir != null) {
      cachedSubDirs.put(dir, subDir);
    }
    return subDir;
  }

  private DocumentFile getSubDirFile(String dir, String fileName, boolean create) {
    DocumentFile subDir = getSubDir(dir, create);
    if (subDir != null) {
      DocumentFile file = subDir.findFile(fileName);
      if (file == null && create) {
        file = subDir.createFile("application/octet-stream", fileName);
      }
      return file;
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
        InputStream is = AppDataContext.getContentResolver().openInputStream(doc.getUri());
        if (is != null) {
          return new BufferedInputStream(is);
        }
      }
      File f = getFallbackFile(dir, fileName);
      if (f.exists() && f.isFile()) {
        return new BufferedInputStream(new java.io.FileInputStream(f));
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
        OutputStream os = AppDataContext.getContentResolver().openOutputStream(doc.getUri(), mode);
        return os != null ? new BufferedOutputStream(os) : null;
      } else {
        File f = getFallbackFile(dir, fileName);
        return new BufferedOutputStream(new java.io.FileOutputStream(f, append));
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

  public boolean dirExists(String dir) {
    DocumentFile root = getRootDir();
    if (root != null) {
      DocumentFile subDir = getSubDir(dir, false);
      if (subDir != null && subDir.exists()) {
        return true;
      }
    }
    File documents = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOCUMENTS);
    if (documents == null) {
      documents = AppDataContext.getFilesDir();
    }
    File appDir = new File(documents, "Ti5x/" + dir);
    return appDir.exists();
  }

  public boolean deleteFile(String dir, String fileName) {
    boolean deleted = false;
    DocumentFile doc = getSubDirFile(dir, fileName, false);
    if (doc != null && doc.exists()) {
      deleted = doc.delete();
    }
    File f = getFallbackFile(dir, fileName);
    if (f.exists()) {
      deleted = f.delete() || deleted;
    }
    return deleted;
  }

  public boolean deleteFileForPath(String path) {
    if (path == null) {
      return false;
    }
    try {
      String cleanName = path;
      if (cleanName.startsWith("/")) {
        cleanName = cleanName.substring(1);
      }
      String dir = Persistent.ProgramsDir;
      if (cleanName.regionMatches(true, 0, Persistent.ProgramsDir + "/", 0, Persistent.ProgramsDir.length() + 1)) {
        dir = Persistent.ProgramsDir;
        cleanName = cleanName.substring(Persistent.ProgramsDir.length() + 1);
      } else if (cleanName.regionMatches(true, 0, Persistent.DataDir + "/", 0, Persistent.DataDir.length() + 1)) {
        dir = Persistent.DataDir;
        cleanName = cleanName.substring(Persistent.DataDir.length() + 1);
      } else {
        int idx = cleanName.lastIndexOf('/');
        if (idx >= 0) {
          String prefix = cleanName.substring(0, idx);
          if (prefix.equalsIgnoreCase(Persistent.DataDir) || prefix.toLowerCase().endsWith("/" + Persistent.DataDir.toLowerCase())) {
            dir = Persistent.DataDir;
          } else if (prefix.equalsIgnoreCase(Persistent.ProgramsDir) || prefix.toLowerCase().endsWith("/" + Persistent.ProgramsDir.toLowerCase())) {
            dir = Persistent.ProgramsDir;
          }
          cleanName = cleanName.substring(idx + 1);
        }
      }

      boolean deleted = deleteFile(dir, cleanName);
      if (!deleted) {
        String fallbackDir = dir.equals(Persistent.ProgramsDir) ? Persistent.DataDir : Persistent.ProgramsDir;
        deleted = deleteFile(fallbackDir, cleanName);
      }

      if (!deleted) {
        File f = new File(path);
        if (f.exists()) {
          deleted = f.delete();
        }
      }

      return deleted;
    } catch (Exception e) {
      return false;
    }
  }

  public String[] listFiles(String dir, String[] extensions) {
    List<String> list = new ArrayList<>();
    try {
      DocumentFile root = getRootDir();
      if (root != null) {
        DocumentFile subDir = getSubDir(dir, false);
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
    if (cleanName.regionMatches(true, 0, Persistent.ProgramsDir + "/", 0, Persistent.ProgramsDir.length() + 1)) {
      subDir = Persistent.ProgramsDir;
      cleanName = cleanName.substring(Persistent.ProgramsDir.length() + 1);
    } else if (cleanName.regionMatches(true, 0, Persistent.DataDir + "/", 0, Persistent.DataDir.length() + 1)) {
      subDir = Persistent.DataDir;
      cleanName = cleanName.substring(Persistent.DataDir.length() + 1);
    } else {
      int idx = cleanName.lastIndexOf('/');
      if (idx >= 0) {
        String prefix = cleanName.substring(0, idx);
        if (prefix.equalsIgnoreCase(Persistent.DataDir)) {
          subDir = Persistent.DataDir;
        } else if (prefix.equalsIgnoreCase(Persistent.ProgramsDir)) {
          subDir = Persistent.ProgramsDir;
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

  public InputStream getInputStreamForPath(String path) {
    if (path == null) {
      return null;
    }
    try {
      String cleanName = path;
      if (cleanName.startsWith("/")) {
        cleanName = cleanName.substring(1);
      }
      String dir = Persistent.ProgramsDir;
      if (cleanName.regionMatches(true, 0, Persistent.ProgramsDir + "/", 0, Persistent.ProgramsDir.length() + 1)) {
        dir = Persistent.ProgramsDir;
        cleanName = cleanName.substring(Persistent.ProgramsDir.length() + 1);
      } else if (cleanName.regionMatches(true, 0, Persistent.DataDir + "/", 0, Persistent.DataDir.length() + 1)) {
        dir = Persistent.DataDir;
        cleanName = cleanName.substring(Persistent.DataDir.length() + 1);
      } else {
        int idx = cleanName.lastIndexOf('/');
        if (idx >= 0) {
          String prefix = cleanName.substring(0, idx);
          if (prefix.equalsIgnoreCase(Persistent.DataDir) || prefix.toLowerCase().endsWith("/" + Persistent.DataDir.toLowerCase())) {
            dir = Persistent.DataDir;
          } else if (prefix.equalsIgnoreCase(Persistent.ProgramsDir) || prefix.toLowerCase().endsWith("/" + Persistent.ProgramsDir.toLowerCase())) {
            dir = Persistent.ProgramsDir;
          }
          cleanName = cleanName.substring(idx + 1);
        }
      }

      InputStream in = getInputStream(dir, cleanName);
      if (in == null) {
        String fallbackDir = dir.equals(Persistent.ProgramsDir) ? Persistent.DataDir : Persistent.ProgramsDir;
        in = getInputStream(fallbackDir, cleanName);
      }

      if (in == null) {
        try {
          android.net.Uri uri = android.net.Uri.parse(path);
          InputStream uriStream = AppDataContext.getContentResolver().openInputStream(uri);
          if (uriStream != null) {
            in = new BufferedInputStream(uriStream);
          }
        } catch (Exception ignored) {}
      }

      if (in == null) {
        File f = new File(path);
        if (f.exists() && f.isFile()) {
          in = new BufferedInputStream(new java.io.FileInputStream(f));
        }
      }

      return in;
    } catch (Exception e) {
      return null;
    }
  }
}