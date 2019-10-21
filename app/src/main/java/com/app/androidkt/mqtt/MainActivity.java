package com.app.androidkt.mqtt;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private MqttAndroidClient client;
    private String TAG = "MainActivity";
    private PahoMqttClient pahoMqttClient;

    private Button startSndVol, endSndVol;
    private Button hiBtn,carBtn,subwayBtn,ambulanceBtn;

    //namyi47.kim
    private Button sttBtn;
    private TextView textView;

    Intent intent_voicerecognizer;
    SpeechRecognizer mRecognizer;

    private SoundMeter mSensor = null;
    private TextView volumeLevel, status;
    private static String volumeVisual = "";

    private Handler handler = null;
    private Runnable r = null;

    public String importantText[] = {
            "Hi",
            "Car",
            "Subway",
            "Ambulance"
    };
    public final int IDX_HI = 0;
    public final int IDX_CAR = 1;
    public final int IDX_SUBWAY = 2;
    public final int IDX_AMBULANCE = 3;
    //namyi47.kim

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG,"onCreate");
        setContentView(R.layout.activity_main);
        pahoMqttClient = new PahoMqttClient();

        //namyi47.kim
        textView = (TextView)findViewById(R.id.sttresult);
        sttBtn = (Button) findViewById(R.id.sttstart);

        intent_voicerecognizer=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent_voicerecognizer.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
        intent_voicerecognizer.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-EN");

        sttBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                stop();

                mRecognizer=SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                if(mRecognizer != null) {
                    mRecognizer.setRecognitionListener(listener);
                    mRecognizer.startListening(intent_voicerecognizer);
                }
            }
        });
        hiBtn = (Button) findViewById(R.id.button_hi);
        hiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMQTTMessage(importantText[IDX_HI]);
                Toast.makeText(MainActivity.this, importantText[IDX_HI], Toast.LENGTH_SHORT).show();
            }
        });
        carBtn = (Button) findViewById(R.id.button_car);
        carBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMQTTMessage(importantText[IDX_CAR]);
                Toast.makeText(MainActivity.this, importantText[IDX_CAR], Toast.LENGTH_SHORT).show();
            }
        });
        subwayBtn = (Button) findViewById(R.id.button_subway);
        subwayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMQTTMessage(importantText[IDX_SUBWAY]);
                Toast.makeText(MainActivity.this, importantText[IDX_SUBWAY], Toast.LENGTH_SHORT).show();
            }
        });
        ambulanceBtn = (Button) findViewById(R.id.button_ambulance);
        ambulanceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMQTTMessage(importantText[IDX_AMBULANCE]);
                Toast.makeText(MainActivity.this, importantText[IDX_AMBULANCE], Toast.LENGTH_SHORT).show();
            }
        });


        startSndVol = (Button) findViewById(R.id.startSndVol);
        startSndVol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(handler != null)
                    return;

                try {
                    if(mSensor != null)
                        mSensor.start();
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if(handler == null)
                    handler = new Handler();

                if( r == null) {
                    r = new Runnable() {
                        public void run() {
                            Toast.makeText(getBaseContext(), "Working!", Toast.LENGTH_LONG).show();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (mSensor == null)
                                        return;
                                    Log.d(TAG, "SoundVolume recognizer is running!");
                                    // Get the volume from 0 to 255 in 'int'
                                    double volume = 10 * mSensor.getTheAmplitude() / 32768;
                                    int volumeToSend = (int) volume;
                                    updateTextView(R.id.volumeLevel, "Volume: " + String.valueOf(volumeToSend));

                                    volumeVisual = "";
                                    for (int i = 0; i < volumeToSend; i++) {
                                        volumeVisual += "|";
                                    }
                                    updateTextView(R.id.volumeBars, "Volume: " + String.valueOf(volumeVisual));

                                    //send volume to the other
                                    sendMQTTMessage(String.valueOf(volumeVisual));

                                    if(handler != null)
                                        handler.postDelayed(this, 250); // amount of delay between every cycle of volume level detection + sending the data  out
                                }
                            });
                        }
                    };
                    handler.postDelayed(r, 250);
                }
            }
        });
        endSndVol = (Button) findViewById(R.id.endSndVol);
        endSndVol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(handler == null)
                    return;
                if(handler != null && r != null) {
                    handler.removeCallbacks(r);
                    handler = null;

                    r = null;
                }

            }
        });

        TextView volumeLevel = (TextView) findViewById(R.id.volumeLevel);
        TextView volumeBars = (TextView) findViewById(R.id.volumeBars);

        if(mSensor == null)
            mSensor = new SoundMeter();


        //namyi47.kim

        client = pahoMqttClient.getMqttClient(getApplicationContext(), Constants.MQTT_BROKER_URL, Constants.CLIENT_ID);

        Intent intent = new Intent(MainActivity.this, MqttMessageService.class);
        startService(intent);
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(getApplicationContext(),"Voice recognizer start.",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            String message;

            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "Audio Error";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "Client Error";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "No Permission";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "Network Error";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "Network Timeout";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "No match";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "Recognizer is busy";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "Server Error";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "Speech Timeout";
                    break;
                default:
                    message = "UNKNOWN ERROR";
                    break;
            }

            Toast.makeText(getApplicationContext(), "Error is occurred : " + message,Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            for(int i = 0; i < matches.size() ; i++){

                textView.setText(matches.get(i));
                //send volume to the other
                String recognizedWord = matches.get(i);

                for(int idx = 0; idx<4;idx++)
                {
                    if(matches.get(i).contains(importantText[idx]))
                    {
                        // Send message to the other device
                        sendMQTTMessage(importantText[idx]);
                        Toast.makeText(MainActivity.this, importantText[idx], Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");



    }
    private void start() throws IllegalStateException, IOException {
        if(mSensor != null)
            mSensor.start();
    }

    private void stop() {
        if(mSensor != null) {
            mSensor.stop();
        }
    }

    private void sleep() {
        if(mSensor != null)
            mSensor.stop();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG,"onPause");

        updateTextView(R.id.status, "Paused.");

        if(mRecognizer!= null)
            mRecognizer.stopListening();

        stop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        super.onDestroy();
        if( handler != null && r != null)
        {
            handler.removeCallbacks(r);
            handler = null;

            r = null;
        }
        mSensor = null;
        if(mRecognizer!= null) {
            mRecognizer.stopListening();
            mRecognizer.destroy();
            mRecognizer = null;
        }
    }

    public void updateTextView(int text_id, String toThis) {

        TextView val = (TextView) findViewById(text_id);
        val.setText(toThis);
    }
    public void sendMQTTMessage(String msg)
    {
        //send volume to the other
        try {
            if(pahoMqttClient!= null)
                pahoMqttClient.publishMessage(client, msg, 1, Constants.PUBLISH_TOPIC);
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
