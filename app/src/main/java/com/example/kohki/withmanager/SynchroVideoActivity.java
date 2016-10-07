package com.example.kohki.withmanager;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class SynchroVideoActivity extends Activity {
    private final static String TAG = "SynchroVideoActivity";
    private static Context context;

    private EventDbHelper cDbHelper;
    public static SQLiteDatabase mDB;
    private VideoRecorder mRecorder = null;
    private EventLogger mEventLogger;
    private Camera mCamera;

    public static SurfaceView main_surface;

    public static SurfaceView   sv_sPlayBackView;
    public static SurfaceHolder sh_sPlayBackHolder;
    public static PreviewSurfaceViewCallback mPreviewCallback;

    private ListView lv_mOurTeamList;
    private ListView lv_mOppTeamList;

    private Button btn_start;
    private Button btn_stop;

    private static TextView tv_ourScore;
    private static TextView tv_oppScore;
    protected static ListView lv_EventLog;

    private SimpleDateFormat sdf;

    protected static int sPoint;
    protected static int sSuccess;
    protected static String sEventName;
    protected static String sMovieName;

    protected static String sGameStartDateTime;
    protected static int sCurrentQuarterNum = 1;

    public static int sMovieTime = 5000;
    public static int sOurMemberNum = 15;
    public static int sOppMemberNum = 15;

    private boolean isPlaying;
    private int flg_menu = 0;  //0:eventlog, 1:score, 2:foul

    private String sava_dir  = "/storage/emulated/legacy/WithManager/";
    //    private String sava_dir = "sdcard/WithManager/";

    /* Synchro only */
    private static final Handler handler = new Handler();
    private Button btnBluetoothSettiong;
    private BluetoothUtil bu;
    private BluetoothDevice targetDevice = null;
    private BluetoothStatus bluetoothStatus;
    private BluetoothAdapter ba;
    private BluetoothConnection bc;
    protected byte buf[];
    private enum BluetoothStatus{
        ERROR("Bluetooth接続に失敗しました"),
        CONNECTING("Bluetooth接続 : 接続中"),
        CONNECTED("Bluetooth接続 : OK");

        private String message;

        private BluetoothStatus(String message){
            this.message = message;
        }

        public String toString(){
            return this.message;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_synchro_video);

        context = this;

         /* Synchro only */
        buf = new byte[4]; buf[3] = 111;
        sdf = new SimpleDateFormat();
         /* --- */

        //main surfaceview
        main_surface = (SurfaceView) findViewById(R.id.main_surface);
        mRecorder = new VideoRecorder(context, sava_dir, main_surface, getResources());

        //sub surfaceview
        sv_sPlayBackView = (SurfaceView) findViewById(R.id.sub_surface);
        sh_sPlayBackHolder = sv_sPlayBackView.getHolder();
        sh_sPlayBackHolder.setFormat(PixelFormat.TRANSLUCENT);//ここで半透明にする
        mPreviewCallback = new PreviewSurfaceViewCallback(context);
        sh_sPlayBackHolder.addCallback(mPreviewCallback);
        sv_sPlayBackView.setVisibility(SurfaceView.INVISIBLE);

        cDbHelper = new EventDbHelper(context);
        mDB       = cDbHelper.getWritableDatabase();

        try {
            File dir_save = new File(sava_dir);
            if(!dir_save.exists())
                dir_save.mkdir();
        } catch (Exception e) {
            Toast.makeText(context, "e:" + e, Toast.LENGTH_SHORT).show();
        }
        mEventLogger = new EventLogger(context);

        sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        //Start button
        btn_start = (Button)findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecorder != null) {
                    Date date = new Date();
                    sGameStartDateTime = sdf.format(date);
                    //    System.out.println("Game start at "+str_gameStartDateTime);
                    mEventLogger.addGameTime(sGameStartDateTime);
                    isPlaying = true;
                    btn_start.setVisibility(View.INVISIBLE);
                    btn_stop.setVisibility(View.VISIBLE);
                    mRecorder.start();
                }
            }
        });

        //Recording stop
        btn_stop = (Button)findViewById(R.id.btn_stop);
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isPlaying = false;
                btn_start.setVisibility(View.VISIBLE);
                btn_stop.setVisibility(View.INVISIBLE);
                mRecorder.stop();
            }
        });

        /* only */
        findViewById(R.id.steal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buf[2] = 1;
                sEventName = "steal";
                Toast.makeText(context, "スティール", Toast.LENGTH_SHORT).show();
                //recordEvent(0,1,"steal"); //1:point,2:is success?,3:event name
            }
        });
        findViewById(R.id.rebound).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buf[2] = 2;
                sEventName = "rebound";
                Toast.makeText(context, "リバウンド", Toast.LENGTH_SHORT).show();
                //recordEvent(0,1,"rebound"); //1:point,2:is success?,3:event name
            }
        });
        findViewById(R.id.foul).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buf[2] = 3;
                sEventName = "foul";
                Toast.makeText(context, "ファウル", Toast.LENGTH_SHORT).show();
                //recordEvent(0,1,"foul");
            }
        });

        findViewById(R.id.btn_chenge_scoresheet_and_eventlog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LinearLayout menu = (LinearLayout) findViewById(R.id.menu);
                flg_menu++;
                if(flg_menu >= 3)
                    flg_menu = 0;

                switch (flg_menu){
                    case 0://eventlog
                        LinearLayout foulsheet = (LinearLayout) findViewById(R.id.foulsheet);
                        menu.removeView(foulsheet);
                        getLayoutInflater().inflate(R.layout.event_log, menu);
                        mEventLogger = new EventLogger(context);
                        mEventLogger.updateEventLog(context, (ListView)findViewById(R.id.event_log));

                        break;
                    case 1://scoresheet
                        LinearLayout eventlog = (LinearLayout) findViewById(R.id.menu_log);
                        menu.removeView(eventlog);
                        getLayoutInflater().inflate(R.layout.score_sheet, menu);
                        setScoresheet();

                        break;
                    case 2:
                        LinearLayout scoresheet = (LinearLayout) findViewById(R.id.scoresheet);
                        menu.removeView(scoresheet);
                        getLayoutInflater().inflate(R.layout.foul_sheet, menu);
                        setFoulsheet();

                        break;
                    default:
                        break;
                }
            }
        });

        findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent itt_setting = new Intent(context, SettingOfGameActivity.class);
                    startActivity(itt_setting);
                }catch (Exception e) {
                    Log.v(TAG, e.getMessage() + "," + e);
                }
            }
        });

        findViewById(R.id.btn_save_or_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.app.AlertDialog.Builder ADB_quarter_save = new android.app.AlertDialog.Builder(context);
                ADB_quarter_save.setTitle("第" + sCurrentQuarterNum + "Q の記録を完了しますか？");
                //    alertDialogBuilder.setMessage("メッセージ");
                if (sCurrentQuarterNum == 4) {
                    sCurrentQuarterNum = 1;
                } else if (sCurrentQuarterNum <= 3) {
                    ADB_quarter_save.setNeutralButton("第" + (sCurrentQuarterNum + 1) + "Qの記録をする", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sCurrentQuarterNum++;
                            Toast.makeText(context, "第" + sCurrentQuarterNum + "Q 記録開始", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                ADB_quarter_save.setNegativeButton("保存して終了する", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        Intent itt = new Intent(context, GameResultActivity.class);
                        itt.putExtra("record_mode", "single");
                        itt.putExtra("game_start_date_time", sGameStartDateTime);
                        startActivity(itt);
                    }
                });
                ADB_quarter_save.setPositiveButton("いいえ", new DialogInterface.OnClickListener() {
                    // 何もしなくていい
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                // アラートダイアログのキャンセルが可能かどうかを設定します
                ADB_quarter_save.setCancelable(true);
                android.app.AlertDialog alertDialog = ADB_quarter_save.create();
                // アラートダイアログを表示します
                alertDialog.show();
            }
        });
        showBluetoothSelectDialog();
        //startConnect();
    }
    private void setScoresheet(){
        ListView listView_our, listView_opt;
        ItemArrayAdapter adpt_our, adpt_opt;

        //スコアシートのリストビュー
        listView_our = (ListView) findViewById(R.id.listView_our);
        listView_opt = (ListView) findViewById(R.id.listView_opt);
        //リストに追加するためのアダプタ
        adpt_our = new ItemArrayAdapter(getApplicationContext(), R.layout.item_rusult);
        adpt_opt = new ItemArrayAdapter(getApplicationContext(), R.layout.item_rusult);

        Parcelable state_our = listView_our.onSaveInstanceState();
        Parcelable state_opt = listView_opt.onSaveInstanceState();

        listView_our.setAdapter(adpt_our);
        listView_our.onRestoreInstanceState(state_our);
        listView_opt.setAdapter(adpt_opt);
        listView_opt.onRestoreInstanceState(state_opt);

        ScoreDataGenerater cScoreData = new ScoreDataGenerater(context, sGameStartDateTime);
        List<String[]> scoreList = cScoreData.getScoreData();
        for (String[] scoreData : scoreList) {
            if (scoreData[0].equals("0")) {
                adpt_our.add(scoreData);

            } else if (scoreData[0].equals("1")) {
                String tmp   = scoreData[1];
                scoreData[1] = scoreData[2];
                scoreData[2] = tmp;
                adpt_opt.add(scoreData);
            }
        }
        //(TextView)findViewById(R.id.name).setBackgroundColor();
    }

    private void setFoulsheet(){

        ListView lv_ourfoul = (ListView) findViewById(R.id.our_foul_list);
        ListView lv_oppfoul = (ListView) findViewById(R.id.opp_foul_list);
        //リストに追加するためのアダプタ
        FoulsheetArrayAdapter adpt_our_foulsheet = new FoulsheetArrayAdapter(getApplicationContext(), R.layout.foul_sheet_row);
        FoulsheetArrayAdapter adpt_opp_foulsheet = new FoulsheetArrayAdapter(getApplicationContext(), R.layout.foul_sheet_row);

        //REVIEW: Instance state is needed ?
        Parcelable state_our = lv_ourfoul.onSaveInstanceState();
        lv_ourfoul.setAdapter(adpt_our_foulsheet);
        lv_ourfoul.onRestoreInstanceState(state_our);

        Parcelable state_opt = lv_oppfoul.onSaveInstanceState();
        lv_oppfoul.setAdapter(adpt_opp_foulsheet);
        lv_oppfoul.onRestoreInstanceState(state_opt);

        List<Integer[]> foulList = FoulCounter.getFoulData(mDB,sGameStartDateTime);

        Integer[] ourmember_foul = foulList.get(0);//[0]is?,[1]is4,[2]is5...
        Integer[] ourteam_foul   = foulList.get(1);
        Integer[] oppmember_foul = foulList.get(2);
        Integer[] oppteam_foul   = foulList.get(3);

        //ourteam
        adpt_our_foulsheet.add(new String[]{"team_kind","ourteam"});
        adpt_our_foulsheet.add(new String[]{"T", String.valueOf(ourteam_foul[sCurrentQuarterNum-1])});
        for(int i=0; i<ourmember_foul.length; i++){
            adpt_our_foulsheet.add(new String[]{String.valueOf(i+4), String.valueOf(ourmember_foul[i])});
        }

        //oppteam
        adpt_opp_foulsheet.add(new String[]{"team_kind","oppteam"});
        adpt_opp_foulsheet.add(new String[]{"T", String.valueOf(oppteam_foul[sCurrentQuarterNum-1]) });
        for(int i=0;i<oppmember_foul.length;i++){
            adpt_opp_foulsheet.add(new String[]{String.valueOf(i+4), String.valueOf(oppmember_foul[i])});
        }
    }

    public void recordEvent(int point, int is_success,String event_name) {
        if (!isPlaying) return;

        sPoint       = point;
        sSuccess     = is_success;
        sEventName   = event_name;

        mRecorder.stop();
        sEventName = mRecorder.save();
        mRecorder.start();

    }
    public static void updateScoreView(int team, int num){ //is accessed from Team.java
        //---点数更新fromDB
        ArrayList column = EventDbHelper.getRowFromSuccessShoot(mDB, sGameStartDateTime);

        int our_score = 0;
        int opp_score = 0;
        for(int i=0;i<column.size()-1;i++){
            try {
                Integer[] row = (Integer[]) column.get(i);
                if(row[0] == 0){
                    our_score = our_score + row[1];
                }else if(row[0] == 1){
                    opp_score = opp_score + row[1];
                }
            }catch (NumberFormatException e){
                Log.w(TAG,e+"");
            }
        }
        tv_ourScore.setText(our_score+"");
        tv_oppScore.setText(opp_score+"");
        //---
    }

    @Override
    public void onResume(){
        super.onResume();
        mRecorder.resume();

        lv_mOurTeamList = (ListView) findViewById(R.id.our_team_list);
        lv_mOppTeamList = (ListView) findViewById(R.id.opposing_team_list);

        Team cOurTeam = new Team(context, lv_mOurTeamList,  sOurMemberNum);
        Team cOppTeam = new Team(context, lv_mOppTeamList , sOppMemberNum);

        Team.TeamSelectListener our_team_lisener = cOurTeam.new TeamSelectListener();
        Team.TeamSelectListener opp_team_lisener = cOppTeam.new TeamSelectListener();

        lv_mOppTeamList.setOnItemClickListener(our_team_lisener);
        lv_mOppTeamList.setOnItemClickListener(opp_team_lisener);

    }
    @Override
    protected void onPause() { //別アクティビティ起動時
        mRecorder.pause();
        super.onPause();
    }

    private void showBluetoothSelectDialog(){
        this.bu = new BluetoothUtil();

        if (!this.bu.isSpported()) // 非対応デバイス
            DialogBuilder.showErrorDialog(this, "Bluetooth非対応デバイスです。");
        else if (!this.bu.isEnabled()) // 設定無効
            DialogBuilder.showErrorDialog(this, "Bluetooth有効にしてください。");
        else if (this.bu.getPairingCount() == 0) // ペアリング済みデバイスなし
            DialogBuilder.showErrorDialog(this, "ペアリング済みのBluetooth設定がありません。");
        else{
            new DialogBuilder(this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle("Bluetoothデバイス選択")
                    .setItems(bu.getDeviceNames(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            decideBluetoothDevice(bu.getDevices()[which]);
                        }
                    })
                    .setNegativeButton("キャンセル", null)
                    .show("Bluetoothデバイス選択");

        }
    }
    private void decideBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.targetDevice = bluetoothDevice;
        Toast.makeText(context, targetDevice.getName() + "が選択されました", Toast.LENGTH_SHORT).show();

        new AlertDialog.Builder(this)
                .setTitle(targetDevice.getName() + "が選択されました")
                .setMessage("同期を開始します")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startConnect();
                    }
                }).show();
    }
    private void startConnect() {
        bluetoothStatus = BluetoothStatus.CONNECTING;

        bc = new BluetoothConnection();

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("接続中");
        progressDialog.setCancelable(true);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "キャンセル", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (bc != null) {
                    bc.close();
                    bc = null;
                }
            }
        });
        progressDialog.show();
        //Bluetooth接続スレッド
        new Thread(new Runnable(){
            @Override
            public void run(){

            }
        }).start();

        //接続待機
        new Thread(new Runnable(){
            @Override
            public void run(){
                ba = BluetoothAdapter.getDefaultAdapter();
                bluetoothStatus = bc.makeServer(ba) ? BluetoothStatus.CONNECTED : BluetoothStatus.ERROR;

                System.out.println("refresh");
                refreshProgressMessage(progressDialog);
            }
        }).start();
    }
    private void startSendConnect(){
        bluetoothStatus = BluetoothStatus.CONNECTING;

        bc = new BluetoothConnection();
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("接続中");
        progressDialog.setCancelable(true);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "キャンセル", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if(bc != null){
                    bc.close();
                    bc = null;
                }
            }
        });
        progressDialog.show();

        //Bluetooth接続スレッド
        new Thread(new Runnable() {
            @Override
            public void run() {
                //接続
                while(progressDialog.isShowing() && bluetoothStatus != BluetoothStatus.CONNECTED){

                    bluetoothStatus = bc.connectToServer(targetDevice) ? BluetoothStatus.CONNECTED : BluetoothStatus.CONNECTING;

                    if(bluetoothStatus == BluetoothStatus.CONNECTED)
                        refreshProgressMessage(progressDialog);
                    else
                        Util.sleep(2000);
                }
            }
        }).start();
    }
    private void refreshProgressMessage(final ProgressDialog dialog) {
        System.out.println("refreshきたよ");
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (dialog) {
                    if(!dialog.isShowing()){

                    }
                    else if(bluetoothStatus == BluetoothStatus.CONNECTED){ //両方接続完了
                        dialog.dismiss();
                        new Thread(bluetoothReceiveRunnable).start();
                        //startInside();
                    }
                    else if(bluetoothStatus == BluetoothStatus.ERROR){ //Bluetooth接続エラー
                        dialog.cancel();
                        showErrorDialog(bluetoothStatus.toString());
                    }
                    else{ //接続中
                        dialog.setMessage(bluetoothStatus.toString());
                    }
                }
            }
        });
    }
    private final Runnable bluetoothReceiveRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                Log.d("MaA","El03");
                while(true){
                    bluetoothReceive();
                }
            }
            catch(Exception e){
                Log.d("MaA","El01");
            }
            //endCheck();
        }
    };
    public void bluetoothReceive(){
        int i;
        int[] j = new int[5];
        int x = 0;
        while((i = bc.readObject()) != -1){
            System.out.println(i);
            j[x] = i;
            if(i == 111){
                final int[] t = j;
                handler.post(new Runnable(){
                    @Override
                    public void run(){
                        bluetoothRecordScore(t);
                    }
                });

                break;
            }
            x++;
        }
    }
    private void showErrorDialog(String message){
        DialogBuilder.showErrorDialog(this, message);
    }

    public void bluetoothRecordScore(int[] buf){
        //buf[0] = team: 0 or 1, buf[1] = actor: 4 ~ 18 , buf[2] = point: 1 or 2 or 3, buf[3] = is_success: 0,1
        if(!isPlaying) return ;
        //TODO:録画中でないとエラー
        String file_name = "no file";
        //   if(mRecorder.mCamera != null) {
        mRecorder.stop();
        file_name = mRecorder.save();
        mRecorder.start();

        int team  = buf[0];
        int actor = buf[1];
        int point = buf[2];
        int is_success = buf[3];

        if(is_success == 1) {
            final TextView tv_our_score = (TextView) findViewById(R.id.our_score);
            int our_score = Integer.parseInt(tv_our_score.getText().toString());

            final TextView tv_opp_score = (TextView) findViewById(R.id.opposing_score);
            int opp_score = Integer.parseInt(tv_opp_score.getText().toString());

            switch (team) {
                case 0:
                    int our_point = our_score + point;
                    tv_our_score.setText(Integer.toString(our_point));//intをsetText()すると落ちる
                    //    Toast.makeText(context,"味方チーム"+ who_is_acter[1]+"番 得点！",Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    int opp_point = opp_score + point;
                    tv_opp_score.setText(Integer.toString(opp_point));
                    //    Toast.makeText(context,"敵チーム"+who_is_acter[1]+"番 得点！",Toast.LENGTH_SHORT).show();
                    break;
            /*case -1:
                Toast.makeText(context, "(score)team isnt be selected", Toast.LENGTH_SHORT).show();
                return ;
            default:
                Toast.makeText(context, "(score)team cant be specified", Toast.LENGTH_SHORT).show();
                return ;  */
            }
        }
        mEventLogger.addEvent(team, actor, point, is_success, "shoot",
                file_name, sGameStartDateTime, sCurrentQuarterNum);
    }
}
