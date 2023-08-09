package com.example.controller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket


class ControllPanelActivity : AppCompatActivity(){
    //シークバーのハンドラー宣言
    private val handlerL = Handler(Looper.getMainLooper())
    private val handlerR = Handler(Looper.getMainLooper())
    //シークバー定期送信用のインスタンス
    private lateinit var sendSeekBarValueRunnableL:Runnable
    private lateinit var sendSeekBarValueRunnableR:Runnable
    //シングルトンで定義したウェブソケット
    private val webSocketManager = WebSocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //onClickで呼び出される画面を指定
        setContentView(R.layout.controllpanel)
        //左右のシークバーをインスタンス化
        val seekBarL = findViewById<SeekBar>(R.id.seekBarLeft)
        val seekBarR = findViewById<SeekBar>(R.id.seekBarRight)
        //シークバーの最大値を設定
        seekBarL.max = 260
        seekBarR.max = 260
        //シークバーの初期位置を真ん中にする
        seekBarL.progress = 130
        seekBarR.progress = 130
        //現在のシークバーから値を取得し、それを入れて置く変数
        var progressL = 0;
        var progressR = 0;
        //PWM用の数値を保存する変数
        var pwmL = 0;
        var pwmR = 0;
        //送信する文字列
        var sendStrL = "";
        var sendStrR = "";

        //通信終了ボタンのインスタンス
        var endRadio = findViewById<Button>(R.id.endRadio)
        //終了ボタンを押した時、終了コマンドを送信し、サーバーから受理したという報告があれば初期画面へ、無ければ待機する
        endRadio.setOnClickListener{
            WebSocketManager.sendCommand("exit")
            // データを受信するまで待機せず、受信データをチェック
            GlobalScope.launch {
                delay(3000)
                if (WebSocketManager.getReceivedData() == "Roger") {
                    runOnUiThread {
                        setContentView(R.layout.activity_main)
                        WebSocketManager.webSocket?.close(1000, "通信の通常終了")
                    }
                } else {
                    //サーバーから受理メッセージが来ない時、コマンドを送って返答があるかを確認する
                    WebSocketManager.sendCommand("Live")
                    delay(1000)
                    var str = WebSocketManager.getReceivedData()
                    println("$str 受け取ったデータ")
                    //返答があれば終了メッセージが届いていないということなので、再処理を促す
                    if(WebSocketManager.getReceivedData() == "Lived"){
                        //再処理の判定に入ったので、今ある受信データはリセットする
                        WebSocketManager.deleteReceiveDate()
                        runOnUiThread {
                            val alertDialogBuilder = AlertDialog.Builder(this@ControllPanelActivity)
                            alertDialogBuilder.setTitle("終了コマンド未受理")
                            alertDialogBuilder.setMessage("サーバーからの受理メッセージが届かなかったようです、もう一度終了してください。")
                            alertDialogBuilder.setPositiveButton("OK") { dialog, _ ->
                                // OKボタンが押されたら最初の画面に戻る
                                dialog.dismiss()
                            }
                            alertDialogBuilder.create().show()
                        }
                    }else{
                        //メッセージに応答がないためサーバーが終了しているものとしてウェブソケットをクローズする
                        WebSocketManager.webSocket?.close(1000, "通信が終了済みのためクローズ")
                        val intent = Intent(this@ControllPanelActivity,MainActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        }

        //左シークバーの定期送信メソッド
        sendSeekBarValueRunnableL = object : Runnable{
            override fun run(){
                //シークバーの数値をそのまま代入
                progressL = seekBarL.progress
                //シークバーが130以上の場合、正転とし、１３０を引いた数値を送信する
                if(progressL >= 130){
                    pwmL = progressL -130
                    //シークバーから取得した値から130を引いたものと、正転用の数値と左右判定用の 1 0 Lを結合する
                    sendStrL = "$pwmL 1 0 L"
                    //センドコマンドでsenStrLを送信する
                    WebSocketManager.sendCommand(sendStrL)
                }else{
                    //後進時には130から値が減っていくので、130を引いたものに-を取り除いて格納している
                    pwmL = Math.abs((progressL -130))
                    //シークバーから取得した値から30を引いたものと、逆転用の数値と左右判定用の 0 1 Lを結合する
                    sendStrL = "$pwmL 0 1 L"
                    //センドコマンドでsenStrLを送信する
                    WebSocketManager.sendCommand(sendStrL)
                }

                //送信間隔を１００ミリ秒にする
                handlerL.postDelayed(this,100)
            }
        }
        //右シークバーの定期送信メソッド
        sendSeekBarValueRunnableR = object : Runnable{
            override fun run(){
                //シークバーの数値をそのまま代入
                progressR = seekBarR.progress
                //シークバーが130以上の場合、正転とし、１３０を引いた数値を送信する
                if(progressR >= 130){
                    pwmR = progressR -130
                    //シークバーから取得した値から130を引いたものと、正転用の数値と左右判定用の 1 0 Rを結合する
                    sendStrR = "$pwmR 1 0 R"
                    //センドコマンドでsenStrLを送信する
                    WebSocketManager.sendCommand(sendStrR)
                }else{
                    //後進時には130から値が減っていくので、130を引いたものに-を取り除いて格納している
                    pwmR = Math.abs((progressR -130))
                    //シークバーから取得した値から30を引いたものと、逆転用の数値と左右判定用の 0 1 Rを結合する
                    sendStrR = "$pwmR 0 1 R"
                    //センドコマンドでsenStrLを送信する
                    WebSocketManager.sendCommand(sendStrR)
                }
                //送信間隔を１００ミリ秒にする
                handlerR.postDelayed(this,100)
            }
        }

        //左シークバーの設定
        seekBarL.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //上下に空白を取るために30の間をとっている
                if (fromUser) {
                    if (progress < 30) {
                        seekBar?.progress = 30
                    }
                    if (progress > 230) {
                        seekBar?.progress = 230
                    }
                }
            }
            //タッチ中は現在の値を送信し続ける
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handlerL.post(sendSeekBarValueRunnableL)
            }
            //指を離した時は送信処理を止め、シークバーを真ん中に戻す
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                handlerL.removeCallbacks((sendSeekBarValueRunnableL))
                seekBarL.progress = 130
                WebSocketManager.sendCommand("0 0 0 L")
            }
        })

        //右シークバーの設定
        seekBarR.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (progress < 30) {
                        seekBar?.progress = 30
                    }
                    if (progress > 230) {
                        seekBar?.progress = 230
                    }
                }
            }

            //タッチ中は現在の値を送信し続ける
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handlerR.post(sendSeekBarValueRunnableR)
            }
            //指を離した時は送信処理を止め、シークバーを真ん中に戻す
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                handlerR.removeCallbacks((sendSeekBarValueRunnableR))
                seekBarR.progress = 130
                WebSocketManager.sendCommand("0 0 0 R")
            }
        })
    }
}

