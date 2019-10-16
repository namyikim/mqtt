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

    private Button startSndVol, endSndVol;

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
    //namyi47.kim

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG,"onCreate");
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

                stop();

                mRecognizer=SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                if(mRecognizer != null) {
                    mRecognizer.setRecognitionListener(listener);
                    mRecognizer.startListening(intent_voicerecognizer);
                }
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
                    Toast.makeText(getBaseContext(), "Sound sensor initiated.", Toast.LENGTH_SHORT).show();
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
                    //send volume to the other
                    sendMQTTMessage("hi");
                    Toast.makeText(MainActivity.this, "hi", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("Subway"))
                {
                    //send volume to the other
                    sendMQTTMessage("subway");
                    Toast.makeText(MainActivity.this, "subway", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("car"))
                {
                    //send volume to the other
                    sendMQTTMessage("car");
                    Toast.makeText(MainActivity.this, "car", Toast.LENGTH_SHORT).show();
                }
                if(matches.get(i).contains("ambulance"))
                {
                    //send volume to the other
                    sendMQTTMessage("ambulance");
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
