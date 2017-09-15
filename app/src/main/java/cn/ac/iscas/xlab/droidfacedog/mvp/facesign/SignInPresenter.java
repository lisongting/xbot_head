package cn.ac.iscas.xlab.droidfacedog.mvp.facesign;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Binder;
import android.support.annotation.NonNull;
import android.util.Log;

import cn.ac.iscas.xlab.droidfacedog.RosConnectionService;
import cn.ac.iscas.xlab.droidfacedog.entity.RobotStatus;
import cn.ac.iscas.xlab.droidfacedog.entity.SignStatus;
import cn.ac.iscas.xlab.droidfacedog.model.AiTalkModel;
import cn.ac.iscas.xlab.droidfacedog.network.YoutuConnection;
import cn.ac.iscas.xlab.droidfacedog.util.Util;
import de.greenrobot.event.EventBus;

/**
 * Created by lisongting on 2017/9/12.
 */

public class SignInPresenter implements SignInContract.Presenter {

    public static final String TAG = "SignInPresenter";
    private SignInContract.View view;
    private Context context;
    private YoutuConnection youtuConnection;
    private AiTalkModel aiTalkModel;
    private int recogFailureCount = 0;

    private RosConnectionService.ServiceBinder rosProxy;

    //表示还没有开始一轮寻路
    private boolean hasStart = false;

    public SignInPresenter(SignInContract.View view, Context context) {
        this.view = view;
        this.context = context;
        view.setPresenter(this);
    }

    @Override
    public void start() {
        youtuConnection = new YoutuConnection(context);
        aiTalkModel = AiTalkModel.getInstance(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void speak(String str) {
        aiTalkModel.speakOutResult(str);
    }

    @Override
    public void recognize(Bitmap bitmap) {

        youtuConnection.recognizeFace(bitmap, new YoutuConnection.RecognitionCallback() {
            //识别结果回调
            @Override
            public void onResponse(String personId) {
                //personId不为0表示识别成功
                if (personId.length() != 0) {
                    SignStatus signStatus = new SignStatus(true, true);
                    if (rosProxy != null) {
                        rosProxy.publishSignStatus(signStatus);
                    }
                    StringBuilder sb =new StringBuilder("你好，");
                    String chineseName = Util.hexStringToString(personId);
                    sb.append(chineseName);

                    speak(sb.toString());
                    view.displayInfo("你好，"+chineseName);
                    log("识别成功，用户id:" + personId);
                    view.closeCamera();
                    view.changeUiState(SignInContract.UI_STATE_ON_THE_WAY);
                }else{
                    recogFailureCount++;
                    if (recogFailureCount == 3) {
                        SignStatus signStatus = new SignStatus(true, false);
                        if (rosProxy != null) {
                            rosProxy.publishSignStatus(signStatus);
                        }
                        recogFailureCount =0;
                        view.displayInfo("识别失败,请检查服务器设置或降低人脸检测阈值");
                        view.closeCamera();
                        view.changeUiState(SignInContract.UI_STATE_ON_THE_WAY);
                        speak("识别失败,请检查人脸服务器设置或降低人脸检测阈值");
                        log("识别失败");
                    }
                }
            }

            @Override
            public void onFailure(String errorInfo) {
                recogFailureCount ++;
                if(recogFailureCount<3){
                    //onFailure表示优图服务器连接失败
                    log("人脸识别服务器连接超时或网络错误");
                    speak("人脸识别服务器连接超时或网络错误");
                    view.displayInfo("人脸识别服务器连接超时或网络错误");
                    SignStatus signStatus = new SignStatus(false, false);
                    if (rosProxy != null) {
                        rosProxy.publishSignStatus(signStatus);
                    }
                    view.closeCamera();
                    view.changeUiState(SignInContract.UI_STATE_ON_THE_WAY);
                }

            }
        });
    }

    @Override
    public void releaseMemory() {
        aiTalkModel.releaseMemory();

        EventBus.getDefault().unregister(this);

    }

    @Override
    public void setServiceProxy(@NonNull Binder binder) {
        rosProxy = (RosConnectionService.ServiceBinder) binder;
    }

    //EventBus的回调，用来接收从Service中发回来的机器人状态
    public void onEvent(RobotStatus status) {
        int locationId = status.getLocationId();
        boolean isMoving = status.isMoving();

        log(status.toString());

        //id为0有两情况，一是到达了起始点，此时把UIState切换为On the way ，表示前往下一个点
        //二是一轮走完回到起始点，此时把UIState 切换为Ready
        if (locationId == 0 && isMoving == false ) {
            if(!hasStart){//如果第一次到起始点
                view.changeUiState(SignInContract.UI_STATE_ON_THE_WAY);
                hasStart = true;
            }else{
                view.changeUiState(SignInContract.UI_STATE_READY);
            }

        }else if (locationId > 0 && isMoving == false) {
            //如果到达了新的位置(工位)，则开启摄像头进行人脸识别
            speak("请人脸打卡");

            //开启摄像头进行人脸识别
            view.startCamera();
        }

    }

    public static void log(String str) {
        Log.i(TAG, str);
    }
}
