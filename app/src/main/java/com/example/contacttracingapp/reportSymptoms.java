package com.example.contacttracingapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import static com.example.contacttracingapp.serverComm.postData;

/**
 * <h1>reportSymptoms.java</h1>
 * This class receives and validates user input symptoms. Once the data has been validates the data
 * is posted to a node server and is compiled into usable statistical data for anonymised research.
 * <p>
 * The user should only really visit this page once. This is when they are showing symptoms and
 * believe they have the virus.
 *
 * @author  Connor Pearson
 * @since 2020-05-18
 */

public class reportSymptoms extends AppCompatActivity {
    private int checkedElements = 0;
    private UUID uuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_symptoms);

        Bundle extras = getIntent().getExtras();
        assert extras != null;
        uuid = UUID.fromString(extras.getString("uuid"));
    }

    /**
     * Allows the submit button to change enabled status depending on if the user has selected a
     * symptom checkbox.
     */
    public void logCheckedElement(View view) {
        if (((CheckBox) view).isChecked())
            checkedElements++;
        else
            checkedElements--;

        if (checkedElements > 0)
            (findViewById(R.id.submitSymptomsBtn)).setEnabled(true);
        else
            findViewById(R.id.submitSymptomsBtn).setEnabled(false);
    }


    /**
     * Posts the users symptom data and UUID to the node server for logging. Method then closes
     * current intent and returns to mainActivity with intent data telling the mainActivity class
     * to change status to 'RED'.
     */
    public void submitSymptoms(View view) throws InterruptedException {
        JSONObject proximityUUIDs = new JSONObject();

        try {
            proximityUUIDs = new JSONObject(fileReadWrite.loadFromFile(this, "proximityUuids.json"));
            proximityUUIDs.put(uuid.toString(), "");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Send symptoms data
        postData("http://192.168.0.90:3000/receiveSymptomaticData", createSymptomsJson().toString());

        //Send uuids of previously close proximity devices
        postData("http://192.168.0.90:3000/receiveProximityUuids", proximityUUIDs.toString());

        Intent returnIntent = new Intent();
        returnIntent.putExtra("newStatus", "RED");
        setResult(2,returnIntent);
        finish();
    }

    /**
     * Create JSON of checked symptom data provided upon submission. This also includes the
     * additional data provided in the text box element. Parsing the JSON can produce a
     * JSONException if the formatting is malformed.
     */
    private JSONObject createSymptomsJson() {
        JSONObject symptoms = new JSONObject();

        try {
            symptoms.put("fever", ((CheckBox) findViewById(R.id.feverCheckBox)).isChecked());
            symptoms.put("fatigue", ((CheckBox) findViewById(R.id.fatigueCheckBox)).isChecked());
            symptoms.put("cough", ((CheckBox) findViewById(R.id.dryCoughCheckBox)).isChecked());
            symptoms.put("lossOfAppetite", ((CheckBox) findViewById(R.id.lossOfAppetiteCheckBox)).isChecked());
            symptoms.put("bodyAche", ((CheckBox) findViewById(R.id.bodyAchesCheckBox)).isChecked());
            symptoms.put("breathShortness", ((CheckBox) findViewById(R.id.breathShortnessCheckBox)).isChecked());
            symptoms.put("mucusOrPhlegm", ((CheckBox) findViewById(R.id.mucusPhlegmCheckBox)).isChecked());
            symptoms.put("soreThroat", ((CheckBox) findViewById(R.id.soreThroatCheckBox)).isChecked());
            symptoms.put("headaches", ((CheckBox) findViewById(R.id.headachesCheckBox)).isChecked());
            symptoms.put("chillsAndShaking", ((CheckBox) findViewById(R.id.chillsCheckBox)).isChecked());
            symptoms.put("additional", ((TextView) findViewById(R.id.additionalTextBox)).getText());
        } catch (JSONException e) {
            Log.e("JSON Symptom Parse", e.toString());
        }

        return symptoms;
    }
}
