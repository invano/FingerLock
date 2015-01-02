package com.invano.fingerlock.util;

import android.content.Context;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;


public class LogFile {

    private static final String LOG_FILE = "log.txt";

    public static void i(Context c, String msg) {

        String out = "[" + DateFormat.getDateTimeInstance().format(new Date()) + "] " + msg;
        try {
            FileOutputStream fOut = c.openFileOutput(LOG_FILE, Context.MODE_APPEND);
            OutputStreamWriter osw = new OutputStreamWriter(fOut);

            osw.append(out).append("\n");
            osw.flush();
            osw.close();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void delete(Context c) {
        c.deleteFile(LOG_FILE);
    }
}
