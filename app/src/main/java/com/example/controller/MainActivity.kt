package com.example.controller

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import java.lang.NumberFormatException
import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.Toast
import okio.ByteString


//シングルトン宣言として、ウェブソケットマネージャーを作成
object WebSocketManager{
    //シングルトンとして共有するOkHttpClientのインスタンス
    var webSocketClient : OkHttpClient? = OkHttpClient()
    // 受信したデータを保持する変数、終了コマンドを送信後にサーバーがちゃんと受け付けれたかを格納する変数
    private var receiveData: String? = null
    //ウェブソケットを作成、このウェブソケットを他のメソッドでも使う
    var webSocket : WebSocket? = null
    //リスナー、ビデオデータの管理変数
    var videoDataReceivedListener: ((ByteArray) -> Unit)? = null
    //アドレス、ポート番号。パス、リスナーを引数にウェブソケット接続を開始するメソッド
    fun connectWebSocket(address: String ,port: Int ,path: String, listener: WebSocketListener){
        // パスが空かどうかでURLを設定する
        val url = if (path.isNullOrEmpty()) {
            "ws://$address:$port"
        } else {
            "ws://$address:$port/$path"
        }
        val request = Request.Builder().url(url).build()
        //接続情報の入っているrequestと引数で受け取ったlistenerを引数に、ウェブソケット通信を確立する
        webSocket = webSocketClient?.newWebSocket(request,listener)
    }

    //引数を送信する関数
    fun sendCommand(command : String){
        webSocket?.send(command)
    }
    // 受信したデータを格納するメソッド
    fun setReceivedData(data: String) {
        receiveData = data
    }
    // 受信したデータを返す関数
    fun getReceivedData(): String? {
        return receiveData
    }
    //受信したデータをリセットする関数
    fun deleteReceiveDate(){
        receiveData = null
    }
    //通信終了後にリソースを解放する
    fun resourceRelease(){
        webSocket?.close(1000, "Goodbye")
        webSocket = null

    }
    // リスナーをセットするメソッド、カメラ映像用
    fun setOnVideoDataReceivedListener(listener: (ByteArray) -> Unit) {
        videoDataReceivedListener = listener
    }

    fun decodeVideoData(text: ByteArray): ByteArray {
        return android.util.Base64.decode(text, android.util.Base64.DEFAULT)
    }

}

class MainActivity : AppCompatActivity() {


    //ウェブソケットマネージャーをonCreateメソッドの外で初期化
    private  val webSocketManager = WebSocketManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //初期画面にアドレス、ポート番号、パスを入力して、ボタンを押した時の処理用インスタンス
        val buttonStart = findViewById<Button>(R.id.button)
        //buttonStartにボタンのインスタンスを生成したので、それが押された時の処理を記述する
        buttonStart.setOnClickListener{
            //各テキストフィールドから入力値を取得
            val addressEditText = findViewById<EditText>(R.id.addressEditText)
            val portEditText = findViewById<EditText>(R.id.portEditText)
            val pathEditText = findViewById<EditText>(R.id.pathEditText)
            //取得した入力値をパース
            val address = addressEditText.text.toString()
            val portString = portEditText.text.toString()
            val path = pathEditText.text.toString()
            //ポート番号に番号以外が入っていた場合、80を入力する
            val port = try{
                portString.toInt()
            }catch (e: NumberFormatException){
                80
            }
            print(address+port)
            //startWebSocketConnectionメソッドを呼び出す
            startWebSocketConnection(address , port , path)
        }
    }

    //ウェブソケット通信に必要な情報が揃ったら、このメソッドに引数として渡し、ウェブソケットマネージャーで接続を開始する
    private fun startWebSocketConnection(address : String , port: Int , path: String){
        try {
            WebSocketManager.connectWebSocket(address, port, path, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    //　接続が確立されたらコントロールパネルへ、接続が確立されない場合こちらの処理は行われない
                    runOnUiThread {
                        val intent = Intent(this@MainActivity, ControllPanelActivity::class.java)
                        startActivity(intent)
                    }

                }

                //onMessageは受け取るデータによって、分岐してくれるため、テキストデータ化バイナリデータかで
                //プログラマーが明示しなくても、適した引数のメソッドで処理してくれる

                //テキストデータで、受信する、終了の確認や現在のサーバー状態などが今回では返ってくる
                override fun onMessage(webSocket: WebSocket, text: String) {
                        //受信メッセージをウェブソケットマネージャーのセットレシーブデータに渡す
                        WebSocketManager.setReceivedData(text)
                }

                //バイナリデータで、受信する、今回のバイナリデータは映像であるので、ウェブソケットマネージャーに
                //バイナリデータを渡す、引数の型がByteStringしかないので、ByteArrayに変換してからvideoDataに
                //代入してから、渡すようにしている。
                //表示処理についてはContrillPanelActivityで行うためここでは不要。
                override fun onMessage(webSocket: WebSocket, data: ByteString) {
                    val videoData = webSocketManager.decodeVideoData(data.toByteArray())
                    webSocketManager.videoDataReceivedListener?.invoke(videoData)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("control","通信失敗時のログ",t)
                    // エラーが発生したらメッセージとともにOKを押すと元の画面に戻るように設定
                    runOnUiThread {
                        println("通信の問題点："+Log.getStackTraceString(t).toString())
                        val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
                        alertDialogBuilder.setTitle("通信エラー")
                        alertDialogBuilder.setMessage("ウェブソケット通信の確立に失敗しました、入力値をもう一度確認してください")
                        alertDialogBuilder.setPositiveButton("OK") { dialog, _ ->
                            //OKボタンが押されたら最初の画面に戻る
                            dialog.dismiss()
                        }
                        alertDialogBuilder.create().show()
                    }
                }
            })
        }catch(e : java.net.ConnectException){
            //接続エラーが発生した場合、ユーザーへ通知する
            runOnUiThread{
                val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
                alertDialogBuilder.setTitle("例外的通信エラーの発生")
                alertDialogBuilder.setMessage("ウェブソケット通信の接続に失敗しました、サーバー側に何か問題があるかもしれません、サーバー側の再起動などの処置を行ってから再接続を試してみてください。")
                alertDialogBuilder.setPositiveButton("OK"){dialog,_->
                    //ボタンを押すとメッセージを閉じる
                    dialog.dismiss()
                }
            }
        }catch (e : Exception){

        }
    }
}

