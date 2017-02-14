package com.example.lb.xf_voice;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CompoundButton;
import android.widget.EditText;

import android.widget.Switch;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

public class MainActivity extends AppCompatActivity implements OnClickListener ,CompoundButton.OnCheckedChangeListener{
    private boolean mIsRecognizer=false;
    private boolean mIsAsr=false;
    private Switch mSwitch_Unceasing_IAT;
    private Switch mSwitch_Iat_Asr;
    private AudioRecord audioRecord;
    private int recBufSize = 0;
    // 用HashMap存储听写结果
    // 语音听写对象
    // 缓存
    private SharedPreferences mSharedPreferences;
    private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    // 云端语法文件
    private String mCloudGrammar = null;
    /*
    String mCloudGrammar = "#ABNF 1.0 UTF-8;" +
            "languagezh-CN;" +
            "mode voice;" +
            "root $main;$main = $place1 到$place2 ;" +
            "$place1 = 北京 | 武汉 | 南京 | 天津 | 天京 | 东京;" +
            "$place2 = 上海 | 合肥; ";
    */
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
        mSwitch_Iat_Asr=(Switch)findViewById(R.id.Switch_Iat_Asr);
        mSwitch_Iat_Asr.setOnCheckedChangeListener(this);

// 应用程序入口处调用，避免手机内存过小，杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
        // 如在Application中调用初始化，需要在Mainifest中注册该Applicaiton
        // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
        // 参数间使用半角“,”分隔。
        // 设置你申请的应用appid,请勿在'='与appid之间添加空格及空转义符
        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
        SpeechUtility.createUtility(MainActivity.this, "appid=" + "5878e808");
        //1.创建SpeechRecognizer对象，第二个参数：本地听写时传InitListener
        mSharedPreferences = getSharedPreferences(getPackageName(),	MODE_PRIVATE);
        mCloudGrammar = readFile(this,"grammar_sample.abnf","utf-8");
        mIat= SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);

    }
    public static String readFile(Context mContext, String file, String code)
    {
        int len = 0;
        byte []buf = null;
        String result = "";
        try {
            InputStream in = mContext.getAssets().open(file);
            len  = in.available();
            buf = new byte[len];
            in.read(buf, 0, len);

            result = new String(buf,code);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
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
            if (mIsRecognizer) {
                mIat.startListening(mRecognizerListener);
            }
        }
        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
            Log.d(TAG,"结束说话");

        }
        @Override
        public void onResult(com.iflytek.cloud.RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            if(mIsAsr){
                String text ;
                text = JsonParser.parseGrammarResult(results.getResultString());
                mResultText.append(text);
                Log.d(TAG,"ok");
            }else {
                String text ;
                text = JsonParser.parseIatResult(results.getResultString());
                mResultText.append(text);
            }
            if (isLast) {
                if (mIsRecognizer) {
                    mIat.startListening(mRecognizerListener);
                }
                mResultText.append("\n");
                mResultText.setSelection(mResultText.length());
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
           // 	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
           // 		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
           // 		Log.d(TAG, "session id =" + sid);
           // 	}
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
                //mResultText.setText(null);// 清空显示内容

                mIatResults.clear();
                // 设置参数
                if(mIsAsr){
                    setParamAsr();
                }else {
                    setParamIat();
                }
                mIat.startListening(mRecognizerListener);
                break;
        }
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.Switch_Unceasing_IAT:
                if (isChecked) {
                    mIatResults.clear();
                    mIsRecognizer=true;
                    creatAudioRecord();
                    if(mIsAsr) {
                        setParamAsr();
                    }else {
                        setParamIat();
                    }
                    mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
                    mIat.startListening(mRecognizerListener);
                    new ThreadInstantPlay().start();
                }else{
                    mIsRecognizer=false;
                }
                break;
            case R.id.Switch_Iat_Asr:
                if(isChecked){
                    mIsAsr=true;
                    mResultText.setText("请说“开灯”或“关灯”"+"\n");

                }else {
                    mResultText.setText("连续语音听写"+"\n");
                    mIsAsr=false;
                }
                break;
        }
    }
    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
    public void setParamIat() {
       mIat.setParameter(SpeechConstant.PARAMS, null);
//2.设置听写参数，详见《科大讯飞MSC API手册(Android)》SpeechConstant类
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "10000"));
        mIat.setParameter(SpeechConstant.DOMAIN, "iat");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
    }

    public void setParamAsr(){
        int ret;
        mIat.setParameter(SpeechConstant.PARAMS, null);
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "10000"));
        mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mIat.buildGrammar("abnf", mCloudGrammar , mCloudGrammarListener);
        if (ret != ErrorCode.SUCCESS){
            Log.d(TAG,"语法构建失败,错误码：" + ret);
        }else{
            Log.d(TAG,"语法构建成功");
        }
//3.开始识别,设置引擎类型为云端
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "cloud");
//设置grammarId
        String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);

    }
    private GrammarListener mCloudGrammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                String grammarID = new String(grammarId);
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                if(!TextUtils.isEmpty(grammarId))
                    editor.putString(KEY_GRAMMAR_ABNF_ID, grammarID);
                editor.commit();
                showTip("语法构建成功：" + grammarId);
            }else{
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };

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
