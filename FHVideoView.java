package com.fhvideo.phoneui.view;
import static android.content.ContentValues.TAG;
import static com.fhvideo.FHLiveClientParams.INTERACT_EVENT_DEFAULT;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.fhvideo.FHLiveClient;
import com.fhvideo.FHLiveClientParams;
import com.fhvideo.FHLiveMacro;
import com.fhvideo.FHResource;
import com.fhvideo.bank.FHBankParams;
import com.fhvideo.bank.FHPermission;
import com.fhvideo.bank.FHSurfaceView;
import com.fhvideo.bank.bean.PushFile;
import com.fhvideo.bean.AnsEvent;
import com.fhvideo.bean.GsonUtil;
import com.fhvideo.bean.UiEvent;
import com.fhvideo.fhcommon.FHBusiCallBack;
import com.fhvideo.fhcommon.bean.FHLog;
import com.fhvideo.fhcommon.bean.URLCons;
import com.fhvideo.fhcommon.utils.StringUtil;
import com.fhvideo.fhcommon.utils.SystemUtil;
import com.fhvideo.phoneui.FHUIConstants;
import com.fhvideo.phoneui.FHVideoManager;
import com.fhvideo.phoneui.bean.FHFunctionSelectBean;
import com.fhvideo.phoneui.busi.FHVideoListener;
import com.fhvideo.phoneui.floatt.FloatManager;
import com.fhvideo.phoneui.utils.GestureTouchHandler;
import com.fhvideo.phoneui.utils.LogUtils;
import com.fhvideo.phoneui.utils.ToastUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.regex.Matcher;

/**
 * 视频会话操作界面
 */

public class FHVideoView implements View.OnClickListener {

    private static FHVideoView instance;
    private FHVideoListener videoListener;

    public static FHVideoView getInstance() {
        if (instance == null)
            instance = new FHVideoView();
        return instance;
    }

    private RelativeLayout rl;
    private Activity mContext;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public View getView(Activity activity, FHVideoListener listener) {
        this.videoListener = listener;
        mContext = activity;
        if (rl == null) {
            rl = (RelativeLayout) LayoutInflater.from(activity).inflate(FHResource.getInstance().getId(activity,"layout","layout_fh_video"), null, false);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rl.setLayoutParams(lp);
            initView();
        } else {//还原

        }
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);
        return rl;
    }

    private boolean isFirst = true;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initView() {
        isFirst = true;
        getSize();
        initFl();

        initSurface();
        initTool();
        FHWebView.getInstance().init(mContext, rl);
        FHHintView.getInstance().init(mContext, rl);
        FHChatView.getInstance().init(mContext, rl);
        FHChatView.getInstance().setVideoListener(videoListener);
        FHPushView.getInstance().init(mContext, rl);
        sv_local.callOnClick();
        hideTool();
        FHWebView.getInstance().loadUrl(URLCons.getServer()+"/digitalH5/#/");

    }

    public static final String
            PUSH_TIME = "pushtime"//推送时间
            , SHOW_PUSH = "show_push"//显示推送
            , SHOW_TOOL = "showTool"//显示功能界面
            ;
    boolean isStartH5 = false;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void mainEvent(UiEvent ros) {
        if (ros == null) return;

        String type = ros.getType();
        String msg = ros.getMsg();

        // 过滤网络质量相关的事件
        if ("INTERACT_EVENT_NETWORK_QUALITY_ARRAY".equals(type) ||
                "fh_params_remoteQuality".equals(type) ||
                "fh_params_localQuality".equals(type) ||
                "weakNet".equals(type)) {
            return;
        }

        Log.d(TAG, "111 " + getClass().getSimpleName() + "  mainEvent : Type：" + type + " Msg：" + msg);

        switch (type) {
            case INTERACT_EVENT_DEFAULT:
                FHWebView.getInstance().appToJs(INTERACT_EVENT_DEFAULT, msg);
                break;

            case "INTERACT_EVENT_SUBTITLES":
                FHWebView.getInstance().appToJs(extractSubtitle(type), msg);
                try {
                    JSONObject jsonObject = new JSONObject(msg);
                    if ("AI".equals(jsonObject.getString("type")) && !isStartH5) {
                        isStartH5 = true;
                        hideTool();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;

            case "onReceivedError":
                if (!isPush) {
                    // 处理页面加载出错逻辑
                    // ToastUtil.getInatance(mContext).show(getResString("fh_web_error"));
                }
                break;

            case PUSH_TIME:
                FHPushView.getInstance().hiddenShow();
                break;

            case SHOW_PUSH:
                showPush(ros);
                break;

            case SHOW_TOOL:
                hideTool();
                break;

            case FHUIConstants.ONCLICK_FLOAT:
                if (isPush) {
                    // 处理点击悬浮窗逻辑
                    EventBus.getDefault().post(new UiEvent("", true, FHBankParams.FH_BACK_METTING));
                    isPush = false;
                    FHWebView.getInstance().hidden();
                }
                break;

            case FHUIConstants.ONPICTURE_INPICTURE_MODE_CHANGED:
                onPictureInPictureModeChanged((Boolean) ros.getObj());
                break;

            default:
                if (msg.contains("face_fount") ||
                        msg.contains("idcard_nationalEmblem") ||
                        msg.contains("idcard_face") ||
                        msg.contains("close_card") ||
                        msg.contains("front_idcard_face") ||
                        msg.contains("back_idcard_face")) {

                    try {
                        JSONObject jsonObject1 = new JSONObject();
                        jsonObject1.put("content", "");  // 设置 content 为空字符串
                        jsonObject1.put("type", msg);
                        FHWebView.getInstance().appToJs(INTERACT_EVENT_DEFAULT, jsonObject1.toString());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                break;
        }
    }

    /**
     * 更新UI
     *
     * @param type 更新类型
     * @param data 更新数据
     */
    private boolean isSubStream = false;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onVideoEvent(final String type, String data) {
        FHLog.addLog( " FHVideoView onVideoEvent: " + type + "  " + data);
        switch (type) {
            case FHBankParams.FH_ONCLICK_BACK://物理返回键
                hideTool();
                onClickClose();
                break;
            case FHLiveClientParams.INTERACT_EVENT_SUBSTREAM_AVAILABLE://辅流
                rotation = false;
                if (data.equals("true")) {
                    iv_rotate.setVisibility(View.VISIBLE);
                    isSubStream = true;
                } else {
                    iv_rotate.setVisibility(View.GONE);
                    isSubStream = false;
                }
                break;
            case FHBankParams.FH_ON_ERROR://显示错误信息
                FHHintView.getInstance().warn(data);
                break;
            case FHLiveClientParams.INTERACT_EVENT_FIRST_FRAME://主画布第一帧画面绘制
                if(isFirst && !StringUtil.isEmpty(sv_main.getUid()) && data.equals(sv_main.getUid())){
                    isFirst= false;
                    iv_net_main.setVisibility(View.VISIBLE);
                    rl_main.setVisibility(View.GONE);
                    onClickSurface(rl_video_main);
                }

                break;
            case FHLiveClientParams.INTERACT_EVENT_USER_LEAVE://用户离开房间
                onUserLeave(data);
                break;
            case FHBankParams.FH_HANDLE_TRANS:
                onTransEvent(data);
                break;
            case FHLiveClientParams.INTERACT_EVENT_USER_JOIN:
                if (othersShow && (!StringUtil.isEmpty(sv_third.getUid()) && data.equals(sv_third.getUid()))) {
                    FHLog.addLog("FHVideoView onVideoEvent rl_video_third VISIBLE: othersShow" + othersShow);
                    rl_video_third.setVisibility(View.VISIBLE);
                }
                if (!othersShow && (!StringUtil.isEmpty(sv_third.getUid()) && data.equals(sv_third.getUid()))) {
                    FHLog.addLog("FHVideoView onVideoEvent rl_video_third GONE: othersShow" + othersShow);
                    rl_video_third.setVisibility(View.GONE);
                }
                break;
            case FHLiveClientParams.INTERACT_EVENT_TEXT://新消息
                onNewMsg(data);
                break;
            case FHBankParams.FH_BACK_METTING:
                isPush = false;
                FHWebView.getInstance().hidden();
                break;
            case FHLiveClientParams.INTERACT_EVENT_SCREEN_START:
                ll_screen_btn.callOnClick();
                break;
            case FHLiveClientParams.INTERACT_EVENT_SCREEN_STOP:
                videoListener.uptVideoType(FHBankParams.FH_VIDEO_TYPE_VIDEO);

                break;
            case FHLiveClientParams.INTERACT_EVENT_IDCARD_FACE://身份证正面
            case FHLiveClientParams.INTERACT_EVENT_IDCARD_NATIONAL_EMBLEM://国徽
            case FHLiveClientParams.INTERACT_EVENT_BANK_CARD://银行卡
            case FHLiveClientParams.INTERACT_EVENT_FACE_RECOGNITION://人脸识别
            case FHLiveClientParams.INTERACT_EVENT_CLOSE_CARD://
                if(isVideo){
                    showCardHint(type);
                } else {
                    rl_open_webcam.setVisibility(View.VISIBLE);
                    sure_open.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            uptVideoType();
                            rl_open_webcam.setVisibility(View.GONE);
                            showCardHint(type);
                        }
                    });
                }
                break;

        }
    }

    public void onTransEvent(String url) {
        FHWebView.getInstance().loadUrl(url);
    }


    private String tellerId = "";


    public static String extractSubtitle(String event) {
        int lastUnderscoreIndex = event.lastIndexOf('_');
        if (lastUnderscoreIndex!= -1) {
            return event.substring(lastUnderscoreIndex + 1);
        }
        return "";
    }

    public static String removeSlashes(String jsonString) {
        // 使用正则表达式替换斜杠
        String pattern = "\\\\";
        String replacement = "";
        return jsonString.replaceAll(pattern, replacement);
    }

    private void showPush(UiEvent ros) {
        iv_push_point.setVisibility(View.INVISIBLE);
        iv_more_point.setVisibility(View.GONE);
        FHPushView.getInstance().hidden();
        if (Build.VERSION.SDK_INT >= 23) {
            if (!FHPermission.getInstance().checkFloat(mContext)) {
                FHPermission.getInstance().applyFloat(mContext);
                return;
            }
        }
        FloatManager.getInstance().createFloatView(mContext, FHUIConstants.FH_FLOAT_TYPE_PUSH);
        isPush = true;
        FHWebView.getInstance().loadUrl(ros.getMsg());
    }

    private void showCardHint(String type){
        LogUtils.e("showCardHint::"+type);
        if(type.equals(FHLiveClientParams.INTERACT_EVENT_CLOSE_CARD)){
            LogUtils.e("showCardHint::close_card");
            showTool();
            showOthers(View.VISIBLE);
            onClickSurface(rl_video_main);
            mask_idcard_face.setVisibility(View.GONE);
            iv_close_mask.setVisibility(View.GONE);
            return;
        }
        if(type.equals(FHLiveClientParams.INTERACT_EVENT_IDCARD_FACE)
                || type.equals(FHLiveClientParams.INTERACT_EVENT_IDCARD_NATIONAL_EMBLEM)){//国徽
            mask_idcard_face.setImageResource(getMipmap("idcard_national_emblem"));
            FHVideoManager.getInstance().changeCameraType(0);
        } else if(type.equals(FHLiveClientParams.INTERACT_EVENT_BANK_CARD)){//银行卡
            mask_idcard_face.setImageResource(getMipmap("bank_card_hint"));
        } else if(type.equals(FHLiveClientParams.INTERACT_EVENT_FACE_RECOGNITION)){//人脸识别
            mask_idcard_face.setImageResource(getMipmap("face_fount_hint"));
            FHVideoManager.getInstance().changeCameraType(1);
        }

        onClickSurface(rl_video_local);
        showOthers(View.GONE);
        mask_idcard_face.setVisibility(View.GONE);
        iv_close_mask.setVisibility(View.GONE);
        hideTool();
    }

    /**
     * 获取图片id
     * @param name
     * @return
     */
    public int getMipmap(String name){
        return FHResource.getInstance().getId(mContext,"mipmap",name);
    }

    private boolean isPush = false;

    private boolean isStart = false;
    public void onStart(boolean isVideo) { //会话开启
        rl_main.setVisibility(View.GONE);
        isStart = true;
        if(!FHVideoManager.getInstance().getCallType().equals(FHLiveMacro.FH_LIVE_CALL_TYPE_NORMAL)){
            iv_more.setVisibility(View.GONE);
            iv_more_point.setVisibility(View.GONE);

        }
        showTool();
        if (runnable == null) {
            runnable = new VideoRunnable();
            videoHandler.postDelayed(runnable, 0);
        }
    }

    private VideoRunnable runnable;
    Handler videoHandler = new Handler();

    private class VideoRunnable implements Runnable {
        @Override
        public void run() {
            gettime();
            videoHandler.postDelayed(this, 1000);
        }
    }

    private int videotime = 0;

    //顶部提示信息
    private void gettime() {
        videotime++;

        String timer = getResString("fh_uid") + " " + FHUIConstants.getTellerId() + " " + getResString("fh_time");
        int mm = videotime / 60;
        int ss = videotime % 60;
        if (mm < 10) {
            timer = timer + " " + "0" + mm + getResString("fh_minute");
        } else {
            timer = timer + " " + mm + getResString("fh_minute");
        }
        if (ss < 10) {
            timer = timer + " 0" + ss + " " + getResString("fh_second");
        } else {
            timer = timer + " " + ss + " " + getResString("fh_second");
        }
        tv_time.setText(timer);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void ansEvent(AnsEvent ros) {
        if (ros != null && (
                ros.getType().equals(PUSH_TIME)
                        || ros.getType().equals(SHOW_TOOL)
        )) {
            if (ros.getType().equals(PUSH_TIME)) {
                SystemClock.sleep(5 * 1000);
                if (!StringUtil.isEmpty(FHPushView.getInstance().pushtime) && ros.getMsg().equals(FHPushView.getInstance().pushtime)) {
                    EventBus.getDefault().post(new UiEvent(ros.getMsg(), true, ros.getType()));
                }
            } else if (ros.getType().equals(SHOW_TOOL)) {
                SystemClock.sleep(5 * 1000);
                if (!StringUtil.isEmpty(show_tool_time) && ros.getMsg().equals(show_tool_time)) {
                    EventBus.getDefault().post(new UiEvent(ros.getMsg(), true, ros.getType()));
                }
            }
        }
    }

    //释放资源
    public void release() {
        isStart = false;
        isPush = false;
        show_tool_time = "";
        videotime = 0;
        isVideo = true;
        videoHandler.removeCallbacks(runnable);
        runnable = null;
        rl = null;
        EventBus.getDefault().unregister(this);
        instance = null;
        FHChatView.getInstance().release();
        FHHintView.getInstance().release();
        FHPushView.getInstance().release();
        FHWebView.getInstance().release();
    }

    private PushFile push;

    public void onNewPush(String data) {//新推送
        if (StringUtil.isEmpty(data))
            return;
        if(!FHVideoManager.getInstance().getCallType().equals(FHLiveMacro.FH_LIVE_CALL_TYPE_NORMAL))
            return;
            push = GsonUtil.fromJson(data, PushFile.class);
        if (push != null) {
            iv_more_point.setVisibility(View.VISIBLE);
            iv_push_point.setVisibility(View.VISIBLE);
            FHPushView.getInstance().onNewPush(push);
        }
    }

    private void onNewMsg(String data) {//新消息
        if (StringUtil.isEmpty(data))
            return;
        if(!FHVideoManager.getInstance().getCallType().equals(FHLiveMacro.FH_LIVE_CALL_TYPE_NORMAL))
            return;
        if (FHChatView.getInstance().rl_chat.getVisibility() == View.GONE) {
            iv_more_point.setVisibility(View.VISIBLE);
            iv_chat_point.setVisibility(View.VISIBLE);
            showTool();
        }
        FHChatView.getInstance().onNewMsg(data);
    }

    private boolean tellerScreen = false;
    private String tellerType = "";

    private String showScreenUserType = "";;
    public void onUptTellerType(String type, String data) {//柜员视频类型
        FHLog.addLog(  "1111 onUptTellerType: type："+type+" data："+data);
        if (StringUtil.isEmpty(type))
            return;
        /*if (type.equals(FHLiveClientParams.INTERACT_EVENT_PPT_ON)) {//柜员ppt
            rl_teller_type.setVisibility(View.VISIBLE);
            tv_teller_type.setText(getResString("fh_show_ppt);
            iv_teller_type.setImageResource(getMipmap("ic_fh_ppt_ing);
            refreshView(data);
        } else*/
        rotation = false;
        if (type.equals(FHLiveClientParams.INTERACT_EVENT_SCREEN_ON)
                    ||type.equals(FHLiveClientParams.INTERACT_EVENT_PPT_ON)
            ) {//柜员投屏
            if(data.equals("1"))
                return;
            showScreenUserType = data;
            rl_teller_type.setVisibility(View.VISIBLE);
            tv_teller_type.setText(getResString("fh_show_screen"));
            iv_teller_type.setImageResource(getMipmap("ic_fh_screen_ing"));
            refreshView(data);

            tmpSurfaceView.setOnClickListener(null);
            tmpSurfaceView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mGestureTouchHandler.onTouchEvent(event);
                    return true;
                }
            });
            if(data.equals("3")){
                tellerType = "third";
            } else if(data.equals("2")){
                tellerType = "main";
            }
            iv_rotate.setVisibility(View.VISIBLE);
        } else if (type.equals(FHLiveClientParams.INTERACT_EVENT_SCREEN_PPT_OFF)) {//视频
            if(!StringUtil.isEmpty(showScreenUserType) && !data.equals(showScreenUserType))
                return;
            showScreenUserType = "";
            rl_teller_type.setVisibility(View.GONE);
            tellerScreen = false;
            showOthers(View.VISIBLE);

            mGestureTouchHandler.reset();
            tmpSurfaceView.setOnTouchListener(null);
            tmpSurfaceView.setOnClickListener(this);
            iv_rotate.setVisibility(View.GONE);
            tellerType = "";
            isSubStream = false;
            if(data.equals("3")){
                FHVideoManager.getInstance().rotationSubStream(sv_third.getUid(), false);
            } else if(data.equals("2")){
                FHVideoManager.getInstance().rotationSubStream(sv_main.getUid(), false);
            }
        }

    }

    private FHSurfaceView tmpSurfaceView;
    private void refreshView(String flag) {
        if (FHUIConstants.iShowTellerShareScreenFloatView())
            return;
        if (flag.equals("3")) {
            tmpSurfaceView = sv_third;
            onClickSurface(rl_video_third);

        } else if (flag.equals("2")) {
            tmpSurfaceView = sv_main;
            onClickSurface(rl_video_main);
        }
        showOthers(View.GONE);
        tellerScreen = true;
    }

    boolean iss = false;

    @Override
    public void onClick(View v) {
        if (v.getId() == ll_min.getId()) {//最小化视频窗口
            onClickmin();
        } else if (v.getId() == iv_more.getId()) {//更多
            if(rl_more.getVisibility() == View.VISIBLE){
                rl_more.setVisibility(View.GONE);
            }else {
                iv_more_point.setVisibility(View.GONE);
                rl_more.setVisibility(View.VISIBLE);
                showTool();
            }

        } else if (v.getId() == ll_switch.getId()) {//前后摄像头切换
            if (isVideo) {
                hideTool();
                //切换摄像头
                FHVideoManager.getInstance().switchCamera();
            }
            //验证分支
        } else if (v.getId() == ll_push_btn.getId()) {//推送
            iv_push_point.setVisibility(View.INVISIBLE);
            iv_more_point.setVisibility(View.GONE);
            hideTool();
            FHPushView.getInstance().onClickPush();
        } else if (v.getId() == ll_chat_btn.getId()) {//聊天
            iv_chat_point.setVisibility(View.INVISIBLE);
            iv_more_point.setVisibility(View.GONE);
            hideTool();
            FHChatView.getInstance().onClickChat();
        } else if (v.getId() == ll_video_audio.getId()) {//音视频切换
            hideTool();
            uptVideoType();
        } else if (v.getId() == ll_screen_btn.getId()) {//投屏
            if (!FHBankParams.isConnected()) { //判断网络状态提示
                ToastUtil.getInatance(mContext).show(getResString("fh_net_weak_hint"));
                return;
            }
            hideTool();
            onClickScreen();
        } else if (v.getId() == ll_close_btn.getId()) {//结束会话
            hideTool();
            onClickClose();
        } else if (v.getId() == sv_local.getId()) {//
            onClickSurface(rl_video_local);

        } else if (v.getId() == sv_main.getId()) {//
            onClickSurface(rl_video_main);

        } else if (v.getId() == sv_third.getId()) {//
            onClickSurface(rl_video_third);
        } else if (v.getId() == iv_rotate.getId()) {//
            rotation = !rotation;
            if (tellerType.equals("subStream")) {
                videoListener.rotationRemote(rotation);
            } else if (tellerType.equals("main")) {
                if(isSubStream){
                    videoListener.rotationRemote(rotation);
                } else {
                    FHVideoManager.getInstance().rotationSubStream(sv_main.getUid(), rotation);
                }
            } else if(tellerType.equals("third")){
                if(isSubStream){
                    videoListener.rotationRemote(rotation);
                } else {
                    FHVideoManager.getInstance().rotationSubStream(sv_third.getUid(), rotation);
                }
                //FHVideoManager.getInstance().rotationSubStream(sv_third.getUid(), rotation);
            }

        } else if (v.getId() == iv_close_mask.getId()) {
            showCardHint("close_card");
        } else if(v.getId() == cancel_open.getId()){
            rl_open_webcam.setVisibility(View.GONE);
        }

    }

    //用户离开
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onUserLeave(String data) {

        if (data.equals(sv_third.getUid())) {
            rl_video_local.setLayoutParams(local_sv_params);
            rl_video_main.setLayoutParams(main_sv_params);
            rl_video_third.setLayoutParams(third_sv_params);
            sv_fourth.setLayoutParams(forth_sv_params);
            rl_video_main.setZ(10);
            rl_video_local.setZ(15);
            rl_video_third.setZ(15);
            sv_fourth.setZ(15);

            tmpSurface = rl_video_main;
            showOthers(View.VISIBLE);
            rl_video_third.setVisibility(View.GONE);
            rl_video_main.setVisibility(View.VISIBLE);
            if(FHVideoManager.getInstance().getCallType().equals(FHLiveMacro.FH_LIVE_CALL_TYPE_NORMAL))
                ToastUtil.getInatance(mContext).show(getResString("fh_thrid_close"));
        }
    }
    /**
     * 获取String.xml中字符串
     * @param name 字符串id名
     * @return
     */
    protected String getResString(String name){
        return FHResource.getInstance().getString(mContext,name);
    }

    /**
     * 设置单个view
     * @param name 控件id名
     */
    protected <T extends View> T getView(String name){
        T view = rl.findViewById(FHResource.getInstance().getId(mContext,"id",name));
        if(view == null){
            Log.e("FH_ERROR","findViewById 出错："+name);
        }
        return view;
    }

    /**
     * 设置单个view
     * @param name 控件id名
     * @param listener 点击事件监听
     */
    protected <T extends View> T getView(String name, View.OnClickListener listener){
        T view = rl.findViewById(FHResource.getInstance().getId(mContext,"id",name));
        if(view == null ) {
            Log.e("FH_ERROR","findViewById 出错："+name);
            return view;
        }
        if(listener != null)
            view.setOnClickListener(listener);
        return view;
    }
    private boolean rotation = false;

    public void switchSurface(View surfaceA, View surfaceB) {//切换画布
        if (Build.VERSION.SDK_INT >= 21) {
            float tmpz = surfaceA.getZ();
            ViewGroup.LayoutParams tmp = (ViewGroup.LayoutParams) surfaceA.getLayoutParams();
            surfaceA.setLayoutParams(surfaceB.getLayoutParams());
            surfaceA.setZ(surfaceB.getZ());
            surfaceB.setLayoutParams(tmp);
            surfaceB.setZ(tmpz);
        }
    }

    private View tmpSurface;//临时主画布

    public void changeMainSurfaceLocation(View surface) {
        if (tmpSurface == null)
            tmpSurface = rl_video_main;
        if (tmpSurface.equals(surface))
            return;
        switchSurface(surface, tmpSurface);
        tmpSurface = surface;
    }

    private void onClickSurface(View view) {
        if (view.getLayoutParams().width == RelativeLayout.LayoutParams.MATCH_PARENT || view.getLayoutParams().width > 900) {
            if(!isStart)
                return;
            FHChatView.getInstance().hidden();
            FHPushView.getInstance().hidden();
            if (rl_tool.getVisibility() == View.VISIBLE)
                hideTool();
            else
                showTool();
        } else {
            changeMainSurfaceLocation(view);
        }
    }

    private void showBottom(boolean show) {
        if (show) {
            showTool();
        } else {
            hideTool();
        }

    }

    private boolean othersShow = true;//小窗口是否显示

    //右侧小窗口控制
    private void showOthers(int gone) {
        FHLog.addLog("11111 showOthers");
        gone= View.VISIBLE;
        if (tellerScreen)
            return;
        if (gone == View.GONE)
            othersShow = false;
        else
            othersShow = true;
        if (rl_video_local.getLayoutParams().width > 0 && rl_video_local.getLayoutParams().width < 900 && !StringUtil.isEmpty(sv_local.getUid())) {
            rl_video_local.setVisibility(gone);
        }
        if (rl_video_main.getLayoutParams().width > 0 && rl_video_main.getLayoutParams().width < 900 && !StringUtil.isEmpty(sv_main.getUid())) {
            rl_video_main.setVisibility(gone);
        }
        if (rl_video_third.getLayoutParams().width > 0 && rl_video_third.getLayoutParams().width < 900
                && !StringUtil.isEmpty(sv_third.getUid())) {
            FHLog.addLog("11111 rl_video_third gone");
            rl_video_third.setVisibility(gone);
        }
    }

    private void onClickmin() {
        hideTool();
        FHHintView.getInstance().show(getResString("fh_min_video_sure"), getResString("fh_cancel"), getResString("fh_confirm"), new FHHintView.OnClickHintListener() {
            @Override
            public void onCancel() {

            }

            @Override
            public void onConfirm() {
                //最小化视频窗
                videoListener.minVideo(FHUIConstants.FH_FLOAT_TYPE_MIN);
            }
        });
    }

    private void onClickScreen() {
        FHHintView.getInstance().show(getResString("fh_share_screen"),
                getResString("fh_cancel"),
                getResString("fh_confirm"),
                new FHHintView.OnClickHintListener() {
                    @Override
                    public void onCancel() {
                    }

                    @Override
                    public void onConfirm() {
                        //切换投屏
                        videoListener.uptVideoType(FHBankParams.FH_VIDEO_TYPE_SREEN);
                    }
                });
    }

    private void onClickClose() {
        if (!FHBankParams.isConnected()) {
            noNet();
            return;
        }
        FHHintView.getInstance().show(getResString("fh_close_video"),
                getResString("fh_cancel"),
                getResString("fh_confirm"),
                new FHHintView.OnClickHintListener() {
                    @Override
                    public void onCancel() {
                    }

                    @Override
                    public void onConfirm() {
                        //结束会话
                        videoListener.leave();
                    }
                });
    }

    private int showHintNum = 0; //统计弹框显示次数

    private void noNet() {
        if (showHintNum != 0) {
            showHintNum = 0;
            videoListener.closeVideo(false);
            return;
        }
        showHintNum++;
        FHHintView.getInstance().warn(getResString("leave_net_error"));
    }

    public void setVideoType(boolean video) {
        isVideo = !video;
        uptVideoType();
    }

    private boolean isVideo = true;

    private void uptVideoType() {
        isVideo = !isVideo;
        if (isVideo) {
            iv_video_audio.setImageResource(getMipmap("ic_fh_switch_audio"));
            tv_video_audio.setText(getResString("fh_change_audio"));
            iv_switch.setImageResource(getMipmap("ic_fh_switch_camera"));
            tv_switch.setTextColor(FHResource.getInstance().getColor(mContext,"white"));
            videoListener.uptVideoType(FHBankParams.FH_VIDEO_TYPE_VIDEO);

        } else {
            iv_video_audio.setImageResource(getMipmap("ic_fh_switch_camera"));
            tv_video_audio.setText(getResString("fh_change_video"));
            iv_switch.setImageResource(getMipmap("ic_fh_switch_nocamera"));
            tv_switch.setTextColor(FHResource.getInstance().getColor(mContext,"fh_color_9c"));
            videoListener.uptVideoType(FHBankParams.FH_VIDEO_TYPE_AUDIO);

        }
    }


    private String show_tool_time = "";
    private RelativeLayout.LayoutParams params;

    private void showTool() {
        if (rl_tool.getVisibility() != View.VISIBLE)
            rl_tool.setVisibility(View.VISIBLE);
        show_tool_time = new Date().getTime() + "";
    }

    private void hideTool() {
        rl_tool.setVisibility(View.GONE);
        rl_more.setVisibility(View.GONE);
        show_tool_time = new Date().getTime() + "";
    }

    private void uptParams(View view, int top) {
        params = (RelativeLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = SystemUtil.dp2px(mContext, top);
        view.setLayoutParams(params);
    }

    private ViewGroup.LayoutParams local_sv_params, main_sv_params, third_sv_params, forth_sv_params;
    private FHSurfaceView sv_main, sv_local, sv_third, sv_fourth,sv_pip;
    private RelativeLayout rl_main, rl_video_main, rl_video_local, rl_video_third;
    private ImageView  iv_main_screen;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initSurface() {
        //主画面
        sv_main = getView("sv_main");
        rl_video_main = getView("rl_video_main");
        rl_video_main.setZ(10);
        sv_main.setOnClickListener(this);

        sv_local = getView("sv_local");
        rl_video_local = getView("rl_video_local");
        rl_video_local.setZ(15);
        sv_local.setOnClickListener(this);

        //第三方
        sv_third = getView("sv_third");
        rl_video_third = getView("rl_video_third");
        rl_video_third.setZ(15);
        sv_third.setOnClickListener(this);

        sv_pip =getView("sv_pip");
        sv_pip.setZ(200);

                //第四方
        sv_fourth = getView("sv_fourth");
        sv_fourth.setZ(15);

        rl_main = getView("rl_main");
        rl_main.setZ(9);

        iv_main_screen = getView("iv_main_screen");
        iv_main_screen.setZ(12);
        //initScroll();        //本地画面

        //rl_fourth = getView("rl_fourth);
        tmpSurfaceView = sv_main;
        local_sv_params = rl_video_local.getLayoutParams();
        main_sv_params = rl_video_main.getLayoutParams();
        third_sv_params = rl_video_third.getLayoutParams();
        forth_sv_params = sv_fourth.getLayoutParams();
        rl_video_third.setVisibility(View.VISIBLE);
        rl_video_third.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        sv_third.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        sv_third.setVisibility(View.VISIBLE);

        if (sv_third.getParent() == null) {
            rl_video_third.addView(sv_third); // 确保 sv_third 被添加到父布局
            FHLog.addLog("sv_third 已添加到 rl_video_third");
        }

// 检查宽高
        sv_third.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int width = sv_third.getWidth();
                int height = sv_third.getHeight();
                FHLog.addLog("sv_third 宽高（布局完成后）：宽=" + width + " 高=" + height);

                if (width <= 0 || height <= 0) {
                    FHLog.addLog("sv_third 画布大小异常，当前宽：" + width + " 高：" + height);
                }

                // 移除监听器，避免重复回调
                sv_third.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

// 检查可见性
        if (sv_third.getVisibility() != View.VISIBLE) {
            FHLog.addLog("sv_third 画布不可见，当前状态：" + sv_third.getVisibility());
        }

// 检查父布局
        if (rl_video_third.getVisibility() != View.VISIBLE) {
            FHLog.addLog("父布局 rl_video_third 不可见，当前状态：" + rl_video_third.getVisibility());
        }

// 确保 sv_third 在最顶层
        sv_third.bringToFront();
        sv_third.invalidate(); // 刷新视图

        // 确保父布局宽高有效
        rl_video_third.post(new Runnable() {
            @Override
            public void run() {
                int parentWidth = rl_video_third.getWidth();
                int parentHeight = rl_video_third.getHeight();
                FHLog.addLog("rl_video_third 父布局宽高：宽=" + parentWidth + " 高=" + parentHeight);

                if (parentWidth <= 0 || parentHeight <= 0) {
                    FHLog.addLog("rl_video_third 父布局大小异常，请检查父布局参数");
                    return;
                }

                // 确保 sv_third 被正确添加到父布局
                if (sv_third.getParent() == null) {
                    FHLog.addLog("sv_third 未被添加到父布局，尝试添加");
                    rl_video_third.addView(sv_third);
                }

                // 设置正确的 LayoutParams
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT
                );
                sv_third.setLayoutParams(params);

                // 强制测量和布局
                sv_third.measure(
                        View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.EXACTLY)
                );
                sv_third.layout(0, 0, parentWidth, parentHeight);

                int width = sv_third.getMeasuredWidth();
                int height = sv_third.getMeasuredHeight();
                FHLog.addLog("sv_third 测量后宽高：宽=" + width + " 高=" + height);
            }
        });
        sv_third.setBackgroundColor(Color.RED);
        sv_third.bringToFront();
        sv_third.invalidate();
        ViewGroup parent = (ViewGroup) rl_video_third;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            FHLog.addLog("子视图：" + child.getClass().getSimpleName() + " 可见性：" + child.getVisibility());
        }
        int[] location = new int[2];
        sv_third.getLocationOnScreen(location);
        FHLog.addLog("sv_third 屏幕位置：X=" + location[0] + " Y=" + location[1]);


        sv_third.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                FHLog.addLog("surfaceCreated: SurfaceView 准备渲染");
                Canvas canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.BLUE); // 在 SurfaceView 上画一个蓝色背景
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                FHLog.addLog("surfaceChanged: 宽=" + width + " 高=" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                FHLog.addLog("surfaceDestroyed: SurfaceView 已销毁");
            }
        });

        //设置画布
        videoListener.setSurface(sv_main, sv_local, sv_third, sv_fourth);
        initTouch();
    }

    private GestureTouchHandler mGestureTouchHandler;
    private void initTouch(){
        mGestureTouchHandler = new GestureTouchHandler(mContext.getApplicationContext());
        mGestureTouchHandler.setOnTouchResultListener(new GestureTouchHandler.OnTouchResultListener() {
            @Override
            public void onTransform(float x1, float y1, float x2, float y2) {

            }

            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onScalForm(Matrix matrix) {
                tmpSurfaceView.setAnimationMatrix(matrix);

            }

            @Override
            public void onClick() {
                FHChatView.getInstance().hidden();
                FHPushView.getInstance().hidden();
                if (rl_tool.getVisibility() == View.VISIBLE)
                    hideTool();
                else
                    showTool();            }
        });
        mGestureTouchHandler.setViewSize(winWidth,rl_video.getLayoutParams().height);
    }


    private int winWidth = 0, winHeight = 0;

    private void getSize() {
        DisplayMetrics outMetrics = new DisplayMetrics();
        mContext.getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        winWidth = outMetrics.widthPixels;
        winHeight = outMetrics.heightPixels;
    }

    private RelativeLayout.LayoutParams rlParams;

    private void initFl() {
        rl_video = getView("rl_video");
        rl_video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        rlParams = (RelativeLayout.LayoutParams) rl_video.getLayoutParams();
        if (winHeight == 0 || winHeight == 0)
            return;
        if (winHeight * 9 - winWidth * 16 > 0) {
            rlParams.height = winWidth * 16 / 9;
            rl_video.setLayoutParams(rlParams);
        }
    }

    private RelativeLayout rl_tool, rl_more, rl_teller_type, rl_video, rl_open_webcam;
    private LinearLayout ll_close_btn, ll_min, ll_push_btn, ll_chat_btn, ll_video_audio, ll_screen_btn, ll_switch;
    private TextView tv_time, tv_video_audio, tv_switch, tv_teller_type, cancel_open, sure_open;
    private ImageView iv_more, iv_more_point, iv_push_point, iv_chat_point, iv_video_audio, iv_switch, iv_teller_type, iv_rotate,iv_above_sv
            //, iv_hint_slide
            ;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initTool() {

        rl_tool = getView("rl_tool");
        rl_tool.setZ(110);
        /*rl_tool.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideTool();
            }
        });*/
        tv_time = getView("tv_time");
        //柜员状态
        rl_teller_type = getView("rl_teller_type");
        rl_teller_type.setZ(110);
        iv_teller_type = getView("iv_teller_type");
        tv_teller_type = getView("tv_teller_type");

        //更多
        iv_more = getView("iv_more");
        iv_more_point = getView("iv_more_point");
        iv_more.setOnClickListener(this);

        //旋转
        iv_rotate = getView("iv_rotate");
        iv_rotate.setOnClickListener(this);

        rl_more = getView("rl_more");
        //推送
        ll_push_btn = getView("ll_push_btn");
        iv_push_point = getView("iv_push_point");
        ll_push_btn.setOnClickListener(this);

        //聊天
        ll_chat_btn = getView("ll_chat_btn");
        iv_chat_point = getView("iv_chat_point");
        ll_chat_btn.setOnClickListener(this);

        //最小化
        ll_min = getView("ll_min");
        ll_min.setOnClickListener(this);
        //挂断
        ll_close_btn = getView("ll_close_btn");
        ll_close_btn.setOnClickListener(this);
        //投屏
        ll_screen_btn = getView("ll_screen_btn");
        ll_screen_btn.setOnClickListener(this);
        //切换摄像头
        ll_switch = getView("ll_switch");
        tv_switch = getView("tv_switch");
        iv_switch = getView("iv_switch");
        ll_switch.setOnClickListener(this);
        //音视频
        ll_video_audio = getView("ll_video_audio");
        tv_video_audio = getView("tv_video_audio");
        iv_video_audio = getView("iv_video_audio");
        ll_video_audio.setOnClickListener(this);

        /*iv_hint_slide = getView("iv_hint_slide);
        iv_hint_slide.setZ(110);*/

        iv_net_main = getView("iv_net_main");
        iv_net_local = getView("iv_net_local");
        iv_net_thrid = getView("iv_net_thrid");
        iv_net_main.setZ(16);
        iv_net_local.setZ(16);
        iv_net_thrid.setZ(16);

        mask_idcard_face = getView("mask_idcard_face");
        iv_close_mask = getView("iv_close_mask");
        mask_idcard_face.setOnClickListener(this);
        iv_close_mask.setOnClickListener(this);

        rl_open_webcam = getView("rl_open_webcam");
        cancel_open = getView("cancel_open");
        cancel_open.setOnClickListener(this);
        sure_open = getView("sure_open");

        iv_above_sv = getView("iv_above_sv");
    }

    private ImageView iv_net_main, iv_net_local, iv_net_thrid, mask_idcard_face, iv_close_mask;

    /**
     * @param user    main主画面 loacl自己画面 thrid第三方画面
     * @param quality
     */
    public void showNetStatus(String user, int quality) {
        Log.d(TAG, "1111 showNetStatus: user"+user+" "+quality);
        ImageView imageView = null;
        if (user.equals("main")) {
            imageView = iv_net_main;
        } else if (user.equals("local")) {
            imageView = iv_net_local;
        } else if (user.equals("third")) {
            imageView = iv_net_thrid;
        }
        if (imageView == null)
            return;
        int imgName = 0;
        if (quality == FHBankParams.QUALITY_Poor) {
            imgName = getMipmap("ic_fh_video_network_poor");
        } else if (quality == FHBankParams.QUALITY_Bad) {
            imgName = getMipmap("ic_fh_video_network_bad");
        } else if (quality == FHBankParams.QUALITY_Vbad) {
            imgName = getMipmap("ic_fh_video_network_vbad");
        } else {
            imgName = getMipmap("ic_fh_video_network_good");
        }
        Glide.with(mContext)
                .load(imgName)
                .thumbnail(0.1f)
                .into(imageView);
    }

    /**
     * 音频呼叫展示主画布上的默认图，遮挡主主画布，防止图片重叠，会话开启后隐藏
     * @param isShow  true：展示
     */
    public void showIvAboveMainSv(boolean isShow) {
        iv_above_sv.setVisibility(isShow ? View.VISIBLE : View.GONE);
    }
    private void onPictureInPictureModeChanged(boolean isInPictureInPictureMode){
        hideTool();

        if(isInPictureInPictureMode){
            showOthers(View.GONE);
            sv_pip.setVisibility(View.VISIBLE);
            FHVideoManager.getInstance().changeSurface(sv_pip);

        }else {
            showOthers(View.VISIBLE);
            sv_pip.setVisibility(View.GONE);
            FHVideoManager.getInstance().changeSurface(sv_main);

        }
    }
}
