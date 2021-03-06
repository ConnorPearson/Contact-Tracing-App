package com.example.contacttracingapp;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * <h1>fileReadWrite.java</h1>
 * This class is used to write to and read from the android devices internal storage.
 * <p>
 * The class is used in multiple other classes in the application and therefore was given its own
 * class to prevent repeated code.
 *
 * @author  Connor Pearson
 * @since 2020-05-18
 */

public class fileReadWrite {
    private static final String TAG = "fileReadWrite  class";

    /**
     * Writes given variable data to file at filename in androids storage.
     */
    static void writeToFile(String data, String filename, Context context) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(context.getFilesDir() + "/" + filename));
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    /**
     * Loads data from given filename.
     */
    static String loadFromFile(Context context, String filename) {
        String userData = null;

        try {
            InputStream inputStream = context.openFileInput(filename);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString =  bufferedReader.readLine();
                inputStream.close();

                userData = receiveString;
            }
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "Cannot read file: " + e.toString());
        }

        return userData;
    }
}
