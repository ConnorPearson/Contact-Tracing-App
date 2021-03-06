package com.example.contacttracingapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.util.Objects;

/**
 * <h1>MainActivity.java</h1>
 * This class controls which activity the user is greeted with upon application launch. The class
 * also creates the appropriate files needed for initial setup, updates exposure state and
 * handles button actions on activity.
 * <p>
 * The user will spend the majority of their time using the application on this activity class.
 *
 * @author  Connor Pearson
 * @since 2020-05-18
 */

public class mainActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";
    private static JSONObject userData = new JSONObject();

    @Override
    protected void onResume() {
        super.onResume();

        //Get status from file and apply
        applyStatus(getStatus());
    }

    /**
     * Check if its the user has set up their info, if not then bring up the setup page. Load the
     * user data in for setting activity attributes.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean isSetupComplete = getSharedPreferences("isSetupComplete", MODE_PRIVATE).getBoolean("isSetupComplete", true);

        if (isSetupComplete) {
            Intent intent = new Intent(this, setup.class);
            startActivityForResult(intent, 1);

            finish();
        }
        else {
            try {
                userData = new JSONObject(fileReadWrite.loadFromFile(this, "userData.json"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            setupPageAttributes();
        }
    }

    /**
     * Set the status attribute of the activity to the status read from the file.
     */
    @Override
    protected  void onStart() {
        super.onStart();

        //Get status from file and apply
        applyStatus(getStatus());
    }

    /**
     * If the intent returns from setup then use returned data to set the activities personal
     * information instead of reading from file.
     * <p>
     * If the intent returns from report symptoms activity then update status and disable the report
     * symptoms buttons.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(!(data == null)) {       //check data is not null
            switch (requestCode) {
                case 1:
                    try {
                        userData = new JSONObject(Objects.requireNonNull(data.getStringExtra("userdata")));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    setupPageAttributes();

                    break;

                case 2:
                    String status = Objects.requireNonNull(data.getStringExtra("newStatus"));
                    changeStatus(status);
                    if (status.equals("RED"))
                        findViewById(R.id.reportSymptomsBtn).setEnabled(false);
            }
        }
    }

    /**
     * Load status from file and return the current value
     */
    private String getStatus() {
        String status;
        JSONObject userDataJson;

        status = fileReadWrite.loadFromFile(this, "userData.json");

        try {
            userDataJson = new JSONObject(status);
            status = userDataJson.getString("status");
        } catch (Exception e) {
            Log.e(TAG, "checkStatus: ", e);
        }

        return status;
    }


    /**
     * Start new intent for status description activity
     */
    public void openStatusDescription(View view) {
        Intent intent = new Intent(this, statusDescription.class);
        startActivity(intent);
    }

    /**
     * Open government guideline page for covid in new intent. This method will use the users
     * default browser.
     */
    public void openCovidWebPage(View view) {
        Uri govUrl = Uri.parse("https://www.gov.uk/coronavirus");
        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, govUrl);
        startActivity(launchBrowser);
    }

    /**
     * Start new intent for report symptoms activity, pass UUID into activity for sending in the
     * POST request
     */
    public void openReportSymptoms(View view) {
        Intent intent = new Intent(this, reportSymptoms.class);
        try {
            //Pass uuid into symptoms report for posting
            intent.putExtra("uuid", userData.get("uuid").toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        startActivityForResult(intent, 2);
    }

    /**
     * Set activity attributes to userData JSON properties and generate UUID QR code bitmap.
     */
    @SuppressLint("SetTextI18n")
    private void setupPageAttributes() {
        ImageView QRCodeImg  = findViewById(R.id.QRCodeImg);

        try {
                ((TextView) findViewById(R.id.nameTextVal)).setText(" " + userData.get("firstName") + "\n " + userData.get("surname"));
                ((TextView) findViewById(R.id.addressTextVal)).setText(userData.get("address").toString());
                ((TextView) findViewById(R.id.passportTextVal)).setText(userData.get("passportID").toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Bitmap bitmap = textToImageEncode(userData.toString());
            QRCodeImg.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "setupPageAttributes: ", e);
        }
    }

    /**
     * Encodes given String value to QR code
     */
    private Bitmap textToImageEncode(String value) throws WriterException {
        BitMatrix bitMatrix;
        try {
            bitMatrix = new MultiFormatWriter().encode(
                    value,
                    BarcodeFormat.QR_CODE,
                    130, 130, null
            );
        } catch (IllegalArgumentException Illegalargumentexception) {
            return null;
        }

        int bitMatrixWidth = bitMatrix.getWidth();
        int bitMatrixHeight = bitMatrix.getHeight();
        int[] pixels = new int[bitMatrixWidth * bitMatrixHeight];

        for (int y = 0; y < bitMatrixHeight; y++) {
            int offset = y * bitMatrixWidth;

            for (int x = 0; x < bitMatrixWidth; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ?
                        ContextCompat.getColor(this, R.color.black):ContextCompat.getColor(this, R.color.white);
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(bitMatrixWidth, bitMatrixHeight, Bitmap.Config.ARGB_4444);
        bitmap.setPixels(pixels, 0, 130, 0, 0, bitMatrixWidth, bitMatrixHeight);

        return bitmap;
    }

    /**
     * Change userdata JSON status to given String color variable.
     */
    public void changeStatus(String color) {
        OutputStreamWriter writer;

        try {
            userData.put("status", color.toUpperCase());

            writer = new OutputStreamWriter(this.openFileOutput("userData.json", Context.MODE_PRIVATE));
            writer.write(userData.toString());
            writer.close();

        } catch (Exception e) {
            Log.e("User data write", "Change status method", e);
        }

        applyStatus(color.toUpperCase());
    }

    /**
     * change activity elements to given status value
     */
    private void applyStatus(String status){
        int statusColor;

        //Default to red as precaution if an error occurs
        if ("GREEN".equals(status.toUpperCase())) {
            statusColor = Color.parseColor("#59ff00");
            findViewById(R.id.reportSymptomsBtn).setEnabled(true);
        } else {
            statusColor = Color.parseColor("#fc0505");
            status = "RED";
            findViewById(R.id.reportSymptomsBtn).setEnabled(false);
        }

        ((TextView)findViewById(R.id.statusValueTxt)).setText(status);
        ((TextView)findViewById(R.id.statusValueTxt)).setTextColor(statusColor);
        findViewById(R.id.statusColourContainer).setBackgroundColor(statusColor);
    }
}