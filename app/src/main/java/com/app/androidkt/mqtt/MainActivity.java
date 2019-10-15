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

    private EditText textMessage, subscribeTopic, unSubscribeTopic;
    private Button publishMessage, subscribe, unSubscribe;

    //namyi47.kim
    private Button sttBtn;
    private TextView textView;

    Intent intent_voicerecognizer;
    SpeechRecognizer mRecognizer;

    private SoundMeter mSensor = null;
    private TextView volumeLevel, status;
    private static String volumeVisual = "";

    private Handler handler;
    //namyi47.kim

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pahoMqttClient = new PahoMqttClient();

        textMessage = (EditText) findViewById(R.id.textMessage);
        publishMessage = (Button) findViewById(R.id.publishMessage);

        subscribe = (Button) findViewById(R.id.subscribe);
        unSubscribe = (Button) findViewById(R.id.unSubscribe);

        subscribeTopic = (EditText) findViewById(R.id.subscribeTopic);
        unSubscribeTopic = (EditText) findViewById(R.id.unSubscribeTopic);

        //namyi47.kim
        textView = (TextView)findViewById(R.id.sttresult);
        sttBtn = (Button) findViewById(R.id.sttstart);

        intent_voicerecognizer=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent_voicerecognizer.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
        intent_voicerecognizer.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-EN");

        sttBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecognizer=SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                mRecognizer.setRecognitionListener(listener);
                mRecognizer.startListening(intent_voicerecognizer);
            }
        });

        TextView volumeLevel = (TextView) findViewById(R.id.volumeLevel);
        TextView volumeBars = (TextView) findViewById(R.id.volumeBars);

        if(mSensor== null)
            mSensor = new SoundMeter();

        try {
            if(mSensor != null)
                mSensor.start();
            Toast.makeText(getBaseContext(), "Sound sensor initiated.", Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        handler = new Handler();

        final Runnable r = new Runnable() {
            public void run() {
                Log.d("Amplify","HERE");
                Toast.makeText(getBaseContext(), "Working!", Toast.LENGTH_LONG).show();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Get the volume from 0 to 255 in 'int'
                        double volume = 10 * mSensor.getTheAmplitude() / 32768;
                        int volumeToSend = (int) volume;
                        updateTextView(R.id.volumeLevel, "Volume: " + String.valueOf(volumeToSend));

                        volumeVisual = "";
                        for( int i=0; i<volumeToSend; i++){
                            volumeVisual += "|";
                        }

                        updateTextView(R.id.volumeBars, "Volume: " + String.valueOf(volumeVisual));

                        //send volume to the other
                        try {
                            pahoMqttClient.publishMessage(client, String.valueOf(volumeVisual), 1, Constants.PUBLISH_TOPIC);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }

                        handler.postDelayed(this, 250); // amount of delay between every cycle of volume level detection + sending the data  out
                    }
                });
            }
        };
        handler.postDelayed(r, 250);
        //namyi47.kim

        client = pahoMqttClient.getMqttClient(getApplicationContext(), Constants.MQTT_BROKER_URL, Constants.CLIENT_ID);

        publishMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = textMessage.getText().toString().trim();
                if (!msg.isEmpty()) {
                    try {
                        pahoMqttClient.publishMessage(client, msg, 1, Constants.PUBLISH_TOPIC);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        subscribe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String topic = subscribeTopic.getText().toString().trim();
                if (!topic.isEmpty()) {
                    try {
                        pahoMqttClient.subscribe(client, topic, 1);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        unSubscribe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String topic = unSubscribeTopic.getText().toString().trim();
                if (!topic.isEmpty()) {
                    try {
                        pahoMqttClient.unSubscribe(client, topic);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Intent intent = new Intent(MainActivity.this, MqttMessageService.class);
        startService(intent);
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(getApplicationContext(),"음성인식을 시작합니다.",Toast.LENGTH_SHORT).show();
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
                    message = "오디오 에러";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "클라이언트 에러";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "퍼미션 없음";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "네트워크 에러";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "네트웍 타임아웃";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "찾을 수 없음";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER가 바쁨";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "서버가 이상함";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "말하는 시간초과";
                    break;
                default:
                    message = "알 수 없는 오류임";
                    break;
            }

            Toast.makeText(getApplicationContext(), "에러가 발생하였습니다. : " + message,Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            // 말을 하면 ArrayList에 단어를 넣고 textView에 단어를 이어줍니다.
            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            for(int i = 0; i < matches.size() ; i++){

                textView.setText(matches.get(i));

                if(matches.get(i).contains("hi"))
                {
                    try {
                        pahoMqttClient.publishMessage(client, "hi", 1, Constants.PUBLISH_TOPIC);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, "hi", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("Subway"))
                {
                    try {
                        pahoMqttClient.publishMessage(client, "subway", 1, Constants.PUBLISH_TOPIC);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, "subway", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("car"))
                {
                    try {
                        pahoMqttClient.publishMessage(client, "car", 1, Constants.PUBLISH_TOPIC);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, "car", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("ambulance"))
                {
                    try {
                        pahoMqttClient.publishMessage(client, "ambulance", 1, Constants.PUBLISH_TOPIC);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, "ambulance", Toast.LENGTH_SHORT).show();
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
        updateTextView(R.id.status, "On resume, need to initiate sound sensor.");
        // Sound based code
        try {
            if(mSensor != null) {
                mSensor.start();
                Toast.makeText(getBaseContext(), "Sound sensor resumed.", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            Toast.makeText(getBaseContext(), "On resume, sound sensor messed up...", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
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
        updateTextView(R.id.status, "Paused.");
        super.onPause();
        stop();
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

    }

    public void updateTextView(int text_id, String toThis) {

        TextView val = (TextView) findViewById(text_id);
        val.setText(toThis);
    }
}
