package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class SimpleDhtActivity extends Activity {
    static private final String TAG = SimpleDhtActivity.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;

    static int seqNum = 0;//{0,0,0,0,0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dht_main);
        
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));

 //
//        try {
//            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
//            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
//        } catch (IOException e) {
//            Log.e(TAG, "Can't create a ServerSocket");
//            return;
//        }

//        final EditText editText = (EditText) findViewById(R.id.editText1);
//
        final Button myButton = (Button) findViewById(R.id.button1);

        myButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                ContextVariables contextVariables= (ContextVariables)getApplicationContext();
                Log.w(TAG,"My Node id: "+contextVariables.getMyNodeId()+"\nMy Predecessor : "+contextVariables.getMyPredecessor()+"My Successor : "+contextVariables.getMySuccessor());
                TextView tv = (TextView) findViewById(R.id.textView1);
                tv.append("My Node id: "+contextVariables.getMyNodeId()+"\nMy Predecessor : "+contextVariables.getMyPredecessor()+"\nMy Successor : "+contextVariables.getMySuccessor());
            }
        });

        /*
         * Register an OnKeyListener for the input box. OnKeyListener is an event handler that
         * processes each key event. The purpose of the following code is to detect an enter key
         * press event, and create a client thread so that the client thread can send the string
         * in the input box over the network.
         */
//        editText.setOnKeyListener(new View.OnKeyListener() {
//            @Override
//            public boolean onKey(View v, int keyCode, KeyEvent event) {
//                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
//                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
//                    /*
//                     * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
//                     * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
//                     * an AsyncTask that sends the string to the remote AVD.
//                     */
//
////                    TextView remoteTextView = (TextView) findViewById(R.id.remote_text_display);
////                    remoteTextView.append("\n");
//
//                    /*
//                     * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
//                     * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
//                     * the difference, please take a look at
//                     * http://developer.android.com/reference/android/os/AsyncTask.html
//                     */
//
//                    return true;
//                }
//                return false;
//            }
//        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_simple_dht_main, menu);
        return true;
    }

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }



}
