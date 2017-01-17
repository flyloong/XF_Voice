package com.example.lb.xf_voice;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.json.JSONException;
import org.json.JSONObject;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.EditText;

import android.widget.Switch;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

public class MainActivity extends AppCompatActivity implements OnClickListener ,CompoundButton.OnCheckedChangeListener{
    private boolean mIsRecognizer=false;
    private Switch mSwitch_Unceasing_IAT;
    private AudioRecord audioRecord;
    private int recBufSize = 0;
    // 用HashMap存储听写结果
    // 语音听写对象
    private SpeechRecognizer mIat;
    private EditText mResultText;
    private Toast mToast;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
    private static String TAG = MainActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mResultText = ((EditText) findViewById(R.id.iat_text));
        findViewById(R.id.iat_genral).setOnClickListener(this);
        mSwitch_Unceasing_IAT=(Switch)findViewById(R.id.Switch_Unceasing_IAT);
        mSwitch_Unceasing_IAT.setOnCheckedChangeListener(this);
// 应用程序入口处调用，避免手机内存过小，杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
        // 如在Application中调用初始化，需要在Mainifest中注册该Applicaiton
        // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
        // 参数间使用半角“,”分隔。
        // 设置你申请的应用appid,请勿在'='与appid之间添加空格及空转义符
        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
        SpeechUtility.createUtility(MainActivity.this, "appid=" + "5878e808");
        //1.创建SpeechRecognizer对象，第二个参数：本地听写时传InitListener
        mIat= SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
    }
    /**
     * 听写监听器。
     */
    private com.iflytek.cloud.RecognizerListener mRecognizerListener = new com.iflytek.cloud.RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }
        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
           showTip(error.getPlainDescription(true));
        }
        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
            if (mIsRecognizer) {
                mIat.startListening(mRecognizerListener);
            }
        }
        @Override
        public void onResult(com.iflytek.cloud.RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            printResult(results);
            if (isLast) {
                // TODO 最后的结果
            }
        }
        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }
        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            		Log.d(TAG, "session id =" + sid);
            	}
        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出时释放连接
        mIat.cancel();
        mIat.destroy();
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iat_genral:
                //3.开始听写
                mResultText.setText(null);// 清空显示内容
                mIatResults.clear();
                // 设置参数
                setParam();
                mIat.startListening(mRecognizerListener);
                break;
        }
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.Switch_Unceasing_IAT:
                if (isChecked) {
                    mIsRecognizer=true;
                    creatAudioRecord();
                    setParam();
                    mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
                    mIat.startListening(mRecognizerListener);
                    new ThreadInstantPlay().start();
                }else{
                    mIsRecognizer=false;
                }
                break;
        }
    }
    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
    private void printResult(com.iflytek.cloud.RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());
        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mIatResults.put(sn, text);
        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }
        mResultText.setText(resultBuffer.toString());
        mResultText.setSelection(mResultText.length());
    }
    public void setParam() {
        mIat.setParameter(SpeechConstant.PARAMS, null);
//2.设置听写参数，详见《科大讯飞MSC API手册(Android)》SpeechConstant类
        mIat.setParameter(SpeechConstant.DOMAIN, "iat");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
    }
    private void creatAudioRecord() {
        if(recBufSize==0||audioRecord==null)
        // 获得缓冲区字节大小
        recBufSize = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        // 创建AudioRecord对象
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBufSize);
    }
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };
    class ThreadInstantPlay extends Thread
    {
        @Override
        public void run()
        {
            byte[] bsBuffer = new byte[recBufSize];
            if(!(audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)) {
                audioRecord.startRecording();
            }
            while(mIsRecognizer && !Thread.currentThread().isInterrupted())
            {
                int line = audioRecord.read(bsBuffer, 0, recBufSize);
                byte[] tmpBuf = new byte[line];
                System.arraycopy(bsBuffer, 0, tmpBuf, 0, line);
                mIat.writeAudio(tmpBuf, 0, tmpBuf.length);
            }
            mIat.stopListening();
            audioRecord.stop();
        }
    }

}
