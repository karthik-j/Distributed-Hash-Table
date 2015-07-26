package edu.buffalo.cse.cse486586.simpledht;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class SimpleDhtProvider extends ContentProvider {
    static private final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String[] REMOTE_PORT = {"11108","11112","11116","11120","11124"};
    static final String NodeJoin_REMOTE_PORT = "11108";
    static final int SERVER_PORT = 10000;
    static final String msgDelimiter="####";
    static final String cursorDelimiter="<==>";

    //variable to Identify the incoming msg in Server
    static final String NodeJoin="NodeJoin";
    static final String NodeAdded="NodeAdded";
    static final String UpdatePredecessor= "UpdatePredecessor";
    static final String InsertTask="InsertTask";
    static final String QueryTask="QueryTask";
    static final String ReturnQueryTask="ReturnQueryTask";
    static final String DeleteTask="DeleteTask";
    static final String StarQuery="StarQuery";
    static final String StarDelete="StarDelete";
    static final String ReturnStarQuery="ReturnStarQuery";

    static boolean isQueryOriginatingPort=true;
    static String queryOriginatingPort="";
    //uri config
    final static Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    static final String allMsgs= "\"@\"";
    static final String allDHTMsgs="\"*\"";

    static final String[] columnNames = {KEY_FIELD, VALUE_FIELD};
    static MatrixCursor allMsgCursor = new MatrixCursor(columnNames);
    static MatrixCursor starMsgCursor = new MatrixCursor(columnNames);


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(selection.equals(allMsgs)){
            String[] files=getContext().fileList();
            for(String file: files){
                getContext().deleteFile(file);
            }
        }else if(selection.equals(allDHTMsgs)){
            String[] files=getContext().fileList();
            for(String file: files){
                getContext().deleteFile(file);
            }
            new DeleteQueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {

            }
        }else {
            getContext().deleteFile(selection);
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        // TODO Auto-generated method stub

        String key;
        String value;
        ContextVariables contextVariables= (ContextVariables)getContext();
        try {
            key=values.get(KEY_FIELD).toString();
            value= values.get(VALUE_FIELD).toString();
            if(contextVariables.getMySuccessor().equals("")||contextVariables.getMySuccessor().equals(contextVariables.getMyNodeId())||keyLookUp(key)){
//                Log.w(TAG,"Inserting key @"+contextVariables.getMyNodeId()+" : "+key+" with hash : "+genHash(key));
                FileOutputStream fos = getContext().openFileOutput(key, Context.MODE_PRIVATE);
                fos.write(value.getBytes());
                fos.close();
            }else{
//                Log.w(TAG,"Insert Task to"+contextVariables.getMySuccessor()+" for key : "+key+" with hash : "+genHash(key));
                new InsertClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,getPortId(contextVariables.getMySuccessor()),key,value);
            }
        }catch(IOException ioe){
            Log.e(TAG,"Insert Exception"+ioe.getMessage());
        }catch(NullPointerException npe){
            Log.e(TAG,"Insert Exception"+npe.getMessage());
        }catch(Exception e){
            Log.e(TAG,"Insert Exception"+e.getMessage());
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub
        String msgValue="" ;
//        String[] columnNames = {KEY_FIELD, VALUE_FIELD};
        MatrixCursor msgCursor = new MatrixCursor(columnNames);
        int keyIndex = msgCursor.getColumnIndex(KEY_FIELD);
        int valueIndex = msgCursor.getColumnIndex(VALUE_FIELD);

        FileInputStream fis;
        BufferedInputStream bis;
        int temp;
        ContextVariables contextVariables= (ContextVariables)getContext();
        //code for handling @ and *  Have to add a separate block for each
        if(selection.equals(allMsgs)){
            String[] files=getContext().fileList();
            for(String file: files){
                msgValue="";
                try {
                    fis = getContext().openFileInput(file);
                    bis = new BufferedInputStream(fis);
                    while ((temp = bis.read()) != -1) {
                        msgValue += (char) temp;
                    }
                    bis.close();
                } catch (IOException ioe) {

                } catch (NullPointerException npe) {

                }
                msgCursor.addRow(new String[]{file, msgValue});
            }
            return msgCursor;
        }else if(selection.equals(allDHTMsgs)){

            String[] files=getContext().fileList();
            for(String file: files){
                msgValue="";
                try {
                    fis = getContext().openFileInput(file);
                    bis = new BufferedInputStream(fis);
                    while ((temp = bis.read()) != -1) {
                        msgValue += (char) temp;
                    }
                    bis.close();
                } catch (IOException ioe) {
//                    Log.w(TAG,"STar Cursor processing exception"+ ioe.getMessage());
                } catch (NullPointerException npe) {
//                    Log.w(TAG,"STar Cursor processing exception"+ npe.getMessage());
                }
                starMsgCursor.addRow(new String[]{file, msgValue});
            }

//            Log.w(TAG, "StarCursor before querying ==>" + starMsgCursor.getCount());
            if(!contextVariables.getMyNodeId().equals(contextVariables.getMySuccessor())) {
                new StarQueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, getPortId(contextVariables.getMySuccessor()), contextVariables.getMyPort());
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {

                }
            }
//            Log.w(TAG,"StarCursor Iterating ==>"+starMsgCursor.getCount());
//            int i=0;
//
//            if(starMsgCursor.moveToFirst()){
//                do{
////                    Log.w("StarCursor index-"+i,"Key="+starMsgCursor.getString(keyIndex)+" Value="+starMsgCursor.getString(valueIndex));
//                    i++;
//                }while(starMsgCursor.moveToNext());
//            }else{
//                Log.w(TAG,"StarCursor is Empty");
//          }
            return starMsgCursor;
        }
        else {

            try {
//                Log.w(TAG, "Query to be performed on key :"+selection);
                if(contextVariables.getMySuccessor().equals("")||contextVariables.getMySuccessor().equals(contextVariables.getMyNodeId())||keyLookUp(selection)){
                    fis = getContext().openFileInput(selection);
                    bis = new BufferedInputStream(fis);
                    while ((temp = bis.read()) != -1) {
                        msgValue += (char) temp;
                    }
                    bis.close();
//                    Log.w(TAG,"Query processed successfully for key :"+selection+" with value :"+msgValue);
                    msgCursor.addRow(new String[]{selection, msgValue});
//                    return msgCursor;
                }else{
//                    Log.w(TAG, "Query Task to be passed to "+contextVariables.getMySuccessor()+" for key :"+selection);
                    //IF ORIGINATING PORT IS NOT CLEARLY PASSED INSTEAD NEW PORT IS BEING PASSED
                    String originatingPort=contextVariables.getMyPort();
                    if(!isQueryOriginatingPort){
                        originatingPort=queryOriginatingPort;
                    }
                    new QueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, getPortId(contextVariables.getMySuccessor()), selection, originatingPort);
//                    boolean queryReturned =false;
//                    do {
                    if(isQueryOriginatingPort) {
//                        Log.w(TAG, "Thread sleep start");
                        Thread.sleep(1500);
//                        Log.w(TAG, "Thread sleep end");
                    }

                    if (allMsgCursor.moveToFirst()) {
                        do {
                            if(selection.equals(allMsgCursor.getString(keyIndex))){
                                msgCursor.addRow(new String[]{selection, allMsgCursor.getString(valueIndex)});
//                                    queryReturned =true;
//                                Log.w(TAG,"Query from other nodes ==> key :"+selection+" value :"+ allMsgCursor.getString(valueIndex));
                                return msgCursor;
                            }
                        } while (allMsgCursor.moveToNext());
                    }else{
//                        Log.w(TAG,"Cursor is empty");
                    }
//                        if(queryReturned){
//                            break;
//                        }
//                    }while(true);
                }
            } catch (IOException ioe) {
//                Log.w(TAG," Query IOexception"+ ioe.getMessage());
            } catch (NullPointerException npe) {
//                Log.w(TAG," Query NullPointerException"+ npe.getMessage());
            } catch (InterruptedException e) {
//                Log.w(TAG," Query InterruptedException"+ e.getMessage());
            } catch (Exception e){
//                Log.w(TAG," Query Exception"+ e.getMessage());
            }

        }
//        Log.v("query", selection);
        return msgCursor;

    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        ContextVariables contextVariables= (ContextVariables)getContext();
        contextVariables.setMyPort(myPort);
        contextVariables.setMyNodeId(portStr);
        contextVariables.setMyHashNode(genHashNodeWrapper(portStr));

//        Log.w(TAG,"OnCreate of "+contextVariables.getMyNodeId()+" with hashValue "+contextVariables.getMyHashNode());
        //Initiating the server task
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }
        //send client request to node 5554 for node join
        new NodeJoinClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,myPort);
        return false;
    }



    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input)  {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1Hash = sha1.digest(input.getBytes());
            Formatter formatter = new Formatter();
            for (byte b : sha1Hash) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }catch(NoSuchAlgorithmException nsae){
            return null;
        }
    }

    private String genHashNodeWrapper(String nodeId){
        if(null==nodeId || nodeId.equals("")){
            return "";
        }else{
            return genHash(nodeId);
        }

    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                while(true) {
                    Socket socket = serverSocket.accept();//socket that connects to individual clients
                    InputStream is = socket.getInputStream();
                    BufferedReader br =new BufferedReader(new InputStreamReader(is));
//                    DataInputStream bis = new DataInputStream(is);
//                    byte[] incomingMsg = new byte[1024];//msg maximum character size is expected to be 256
//                    bis.read(incomingMsg);
                    String strIncomingMsg=br.readLine();
                    socket.close(); //closing the socket once the msg is read

//                    String strIncomingMsg=new String(incomingMsg);
                    String[] msgs=strIncomingMsg.split(msgDelimiter);
                    ContextVariables contextVariables= (ContextVariables)getContext();
//                    Log.w(TAG,"Received Msg in ServerTask @"+contextVariables.getMyNodeId()+" : " + strIncomingMsg.toString());
                    switch(msgs[0]){
                        case NodeJoin:
                            //NodeJoin+####+appPortNo####+appNodeId
//                            Log.w(TAG,"ReceivedNodeJoin @ "+contextVariables.getMyPort()+" for node"+msgs[1]);
                            callClientTask(strIncomingMsg);
//                            publishProgress(new String(incomingMsg));
                            break;
                        case NodeAdded:
                            //NodeJoin+####+myIdAsSuccessor+####+predecessorId
                            contextVariables.setMySuccessor(msgs[1]);
                            contextVariables.setMyHashSuccessor(genHashNodeWrapper(msgs[1]));
                            contextVariables.setMyPredecessor(msgs[2]);
                            contextVariables.setMyHashPredecessor(genHashNodeWrapper(msgs[2]));
//                            Log.w(TAG,"NodeAdding for "+contextVariables.getMyPort()+" with successor "+contextVariables.getMySuccessor()+"and with predecessor "+contextVariables.getMyPredecessor());
                            break;
                        case UpdatePredecessor:
                            //NodeJoin+####+newNodeAsSuccessorForPredecessor
                            contextVariables.setMySuccessor(msgs[1]);
                            contextVariables.setMyHashSuccessor(genHashNodeWrapper(msgs[1]));
//                            Log.w(TAG,"UpdatingPredecessor for "+contextVariables.getMyPort()+" with successor"+contextVariables.getMySuccessor());
                            break;
                        case InsertTask:
                            //InsertTask####key####value####
//                            Log.w(TAG,"received msg to insert at "+contextVariables.getMyNodeId() +" for key"+msgs[1]);
                            ContentValues keyValueToInsert = new ContentValues();
                            keyValueToInsert.put(KEY_FIELD, msgs[1]);
                            keyValueToInsert.put(VALUE_FIELD,msgs[2]);
                            insert(uri, keyValueToInsert);
                            break;
                        case QueryTask:
                            //QueryTask####key####originatingPort####
                            isQueryOriginatingPort=false;
                            queryOriginatingPort=msgs[2];
                            String key = "";
                            String value ="";
                            Cursor resultCursor = query(uri, null, msgs[1], null, null);
                            if(resultCursor.moveToFirst()){
                                int keyIndex = resultCursor.getColumnIndex(KEY_FIELD);
                                int valueIndex = resultCursor.getColumnIndex(VALUE_FIELD);
                                key = resultCursor.getString(keyIndex);
                                value = resultCursor.getString(valueIndex);
                                String msgToSend =  ReturnQueryTask+msgDelimiter+key+msgDelimiter+value+msgDelimiter+msgs[2]+msgDelimiter;//ReturnQueryTask####key####value####originatingPort####
                                callClientTask(msgToSend);
                            }
                            isQueryOriginatingPort=true;
                            queryOriginatingPort="";
                            break;
                        case ReturnQueryTask:
                            //ReturnQueryTask####key####value####
//                            Log.w(TAG,"Return Query Task from "+ msgs[3]+ " for key " + msgs[1]);
                            allMsgCursor.addRow(new String[]{msgs[1],msgs[2]});
                            break;
                        case StarQuery:
                            //StarQuery####originatingPort####
                            String rowList="";
                            if(!msgs[1].equals(contextVariables.getMyPort())) {

                                Cursor resultStarCursor = query(uri, null, allMsgs, null, null);
                                if (resultStarCursor != null && resultStarCursor.getCount() > 0) {
                                    int keyIndex = resultStarCursor.getColumnIndex(KEY_FIELD);
                                    int valueIndex = resultStarCursor.getColumnIndex(VALUE_FIELD);
                                    if (resultStarCursor.moveToFirst()) {
                                        do {
                                            rowList += resultStarCursor.getString(keyIndex) + cursorDelimiter + resultStarCursor.getString(valueIndex) + msgDelimiter;
                                        } while (resultStarCursor.moveToNext());
                                    }
                                    new ReturnStarQueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgs[1], rowList);

                                }
                                new StarQueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, getPortId(contextVariables.getMySuccessor()), msgs[1]);
                            }
                            break;
                        case ReturnStarQuery:
                            //ReturnStarQuery####key1<==>value1####...keyn<==>valuen####

                            for(int i=1;i<msgs.length;i++){
                                if(msgs[i].contains(cursorDelimiter)){
                                    starMsgCursor.addRow(new String[]{msgs[i].split(cursorDelimiter)[0], msgs[i].split(cursorDelimiter)[1]});
                                }
                            }
//                            starMsgCursor.addRow(new String[]{file, msgValue});
                            break;
                        case DeleteTask:
//                            Log.w(TAG,"Delete Task started");
                            delete(uri,allMsgs,null);
                            break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, " Server Task IOException :" + e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Server Task Exception :" + e.getMessage());
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */


            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */

        }

        protected void callClientTask(String msg){

            if(msg!=null) {
                String[] msgs = msg.split(msgDelimiter);
                switch (msgs[0]) {
                    case NodeJoin:
                        //NodeJoin+####+newAppNodeId
                        new NodeJoinTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgs[1],msgs[2]);
                        break;
                    case InsertTask:

                        break;
                    case ReturnQueryTask:
                        //ReturnQueryTask####key####value####originatingPort####
                        new ReturnQueryClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgs[3],msgs[1],msgs[2]);
                        break;
                    case DeleteTask:


                }
            }
        }


    }
    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class InsertClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                //msgs 0 - successor portId, 1 - key, 2 -value
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = InsertTask+msgDelimiter+msgs[1]+msgDelimiter+msgs[2]+msgDelimiter;//InsertTask####key####value####
                Log.w(TAG,"Insert Client Task msg to "+msgs[0]+" :"+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[0]));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Insert ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Insert ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Insert ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class StarQueryClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                //msgs 0 - successor portId, 1 - originatingPort
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = StarQuery+msgDelimiter+msgs[1]+msgDelimiter;//StarQuery####originatingPort####
                Log.w(TAG,"Star Query Client Task msg to "+msgs[0]+" :"+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[0]));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Star Query ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Star Query ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, "Star Query ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class ReturnStarQueryClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                ContextVariables contextVariables= (ContextVariables)getContext();
                //msgs 0 - originating portId, 1 - msgsList
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = ReturnStarQuery+msgDelimiter+msgs[1]+contextVariables.getMyNodeId()+msgDelimiter;//ReturnStarQuery####key1<==>value1####...keyn<==>valuen####
                Log.w(TAG,"Return star Query Client Task msg to "+msgs[0]+" :"+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[0]));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Return star Query ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Return Star Query ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Return star Query ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class DeleteQueryClientTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... msgs) {
            try {
                ContextVariables contextVariables= (ContextVariables)getContext();

                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = DeleteTask+msgDelimiter;//StarQuery####originatingPort####
                for(int i=0; i< REMOTE_PORT.length;i++) {
                    if(!REMOTE_PORT[i].equals(contextVariables.getMyPort())) {
//                        Log.w(TAG, "Delete Client Task msg to " + REMOTE_PORT[i] + " :" + msgToSend);

                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[i]));
                        os = socket.getOutputStream();
                        pw = new PrintWriter(os, true);
                        pw.println(msgToSend);
                        pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                        socket.close();
                    }
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Star Query ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Star Query ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, "Star Query ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class QueryClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                //msgs 0 - successor portId, 1 - key 2 - originatingPort
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = QueryTask+msgDelimiter+msgs[1]+msgDelimiter+msgs[2]+msgDelimiter;//QueryTask####key####originatingPort####
//                Log.w(TAG,"Query Client Task msg to "+msgs[0]+" :"+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[0]));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Query ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Query ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Query ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class ReturnQueryClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                ContextVariables contextVariables= (ContextVariables)getContext();
                //msgs 0 -  originatingPortId, 1 - key ,2 -value
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = ReturnQueryTask+msgDelimiter+msgs[1]+msgDelimiter+msgs[2]+msgDelimiter+contextVariables.getMyNodeId()+msgDelimiter;//ReturnQueryTask####key####value####sending portid####
//                Log.w(TAG,"Return Query Client Task msg to "+msgs[0]+" :"+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(msgs[0]));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Query ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Query ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Query ClientTAsk Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private class NodeJoinClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            ContextVariables contextVariables= (ContextVariables)getContext();
//            Log.w(TAG,"Starting node join request for "+msgs[0]+" with NodeId "+contextVariables.getMyNodeId());
            try {
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend = NodeJoin+msgDelimiter+msgs[0]+msgDelimiter+contextVariables.getMyNodeId()+msgDelimiter; //NodeJoin+####+appPortNo####+appNodeId
//                Log.w(TAG,"NodeJoin Msg to send From "+contextVariables.getMyNodeId()+" : "+msgToSend);
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(NodeJoin_REMOTE_PORT));
                os= socket.getOutputStream();
                pw= new PrintWriter(os,true);
                pw.println(msgToSend);
                pw.flush();
//                dos= new DataOutputStream(os);
//                dos.write(msgToSend.getBytes());
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "Node Join ClientTask UnknownHostException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Node Join ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Node Join ClientTask Exception :" + e.getMessage());
            }
//            Log.w(TAG,"Ending node join request for "+msgs[0]+" with NodeId "+contextVariables.getMyNodeId());
            return null;
        }
    }

    private class NodeJoinTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                ContextVariables contextVariables= (ContextVariables)getContext();
//                Log.w(TAG,"Starting NodeJoin Check @" +contextVariables.getMyNodeId()+" for "+msgs[1]);
                //msgs[0] ==> AppPort;
                //msgs[1]==>appNodeId
                Socket socket;
                OutputStream os;
//                DataOutputStream dos;
                PrintWriter pw;
                String msgToSend;
                if(LookUp(msgs[1])){
                    String tempPred=contextVariables.getMyPredecessor();
                    if(tempPred.equals("")){
                        tempPred=contextVariables.getMyNodeId();
                    }
//                    Log.w(TAG,"NodeJoin@"+contextVariables.getMyNodeId()+" : My Predecessor with Hash "+contextVariables.getMyPredecessor()+" "+contextVariables.getMyHashPredecessor());
                    //updating new nodes information
                    msgToSend = NodeAdded+msgDelimiter+contextVariables.getMyNodeId()+msgDelimiter+tempPred+msgDelimiter; //NodeJoin+####+myIdAsSuccessor+####+predecessorId
//                    Log.w(TAG,"NodeAdded msg to port "+msgs[1]+":"+msgToSend);
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(msgs[0]));
                    os= socket.getOutputStream();
                    pw= new PrintWriter(os,true);
                    pw.println(msgToSend);
                    pw.flush();
//                    dos= new DataOutputStream(os);
//                    dos.write(msgToSend.getBytes());
                    socket.close();

                    //updating predecessors information.
                    if(null != contextVariables.getMyPredecessor() && !contextVariables.getMyPredecessor().equals("")) {
                        msgToSend = UpdatePredecessor + msgDelimiter + msgs[1]+msgDelimiter; //NodeJoin+####+newNodeAsSuccessorForPredecessor
//                        Log.w(TAG,"UpdatePredecessor msg to port "+contextVariables.getMyPredecessor()+" : "+msgToSend);
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(getPortId(contextVariables.getMyPredecessor())));
                        os = socket.getOutputStream();
                        pw= new PrintWriter(os,true);
                        pw.println(msgToSend);
                        pw.flush();
//                        dos = new DataOutputStream(os);
//                        dos.write(msgToSend.getBytes());
                        socket.close();
                    }
                    //updating current nodes information
                    contextVariables.setMyPredecessor(msgs[1]);
                    contextVariables.setMyHashPredecessor(genHashNodeWrapper(msgs[1]));
                }else if(contextVariables.getMyNodeId().equals(contextVariables.getMySuccessor())){
                    //updating new nodes information
                    msgToSend = NodeAdded+msgDelimiter+contextVariables.getMyNodeId()+msgDelimiter+contextVariables.getMyNodeId()+msgDelimiter; //NodeJoin+####+myIdAsSuccessor+####+predecessorId
//                    Log.w(TAG,"NodeAdded msg to port "+msgs[1]+":"+msgToSend);
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(msgs[0]));
                    os= socket.getOutputStream();
                    pw= new PrintWriter(os,true);
                    pw.println(msgToSend);
                    pw.flush();
//                    dos= new DataOutputStream(os);
//                    dos.write(msgToSend.getBytes());
                    socket.close();
                    //updating current node
                    contextVariables.setMySuccessor(msgs[1]);
                    contextVariables.setMyHashSuccessor(genHashNodeWrapper(msgs[1]));
                    contextVariables.setMyPredecessor(msgs[1]);
                    contextVariables.setMyHashPredecessor(genHashNodeWrapper(msgs[1]));
                }else{
                    msgToSend = NodeJoin+msgDelimiter+msgs[0]+msgDelimiter+msgs[1]+msgDelimiter; //NodeJoin+####+newAppPortNo+####+newAppNodeId
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(getPortId(contextVariables.getMySuccessor())));
                    os= socket.getOutputStream();
                    pw= new PrintWriter(os,true);
                    pw.println(msgToSend);
                    pw.flush();
//                    dos= new DataOutputStream(os);
//                    dos.write(msgToSend.getBytes());
                    socket.close();
                }

               // TextView tv = (TextView ) findViewById(R.id.textView1);





            } catch (UnknownHostException e) {
                Log.e(TAG, "Node Join ClientTask UnknownHostException : "+e.getMessage());
            } catch (InterruptedIOException e){
                Log.e(TAG, "Node Join ClientTask socket InterruptedIOException : "+e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Node Join ClientTask socket IOException : "+e.getMessage());
            } catch (Exception e){
                Log.e(TAG, " Node Join Exception :" + e.getMessage());
            }

            return null;
        }
    }

    private boolean keyLookUp(String newkey){
        String hashNewkey= genHash(newkey);
        ContextVariables contextVariables= (ContextVariables)getContext();
        if(hashNewkey.compareTo(contextVariables.getMyHashPredecessor()) > 0 && hashNewkey.compareTo(contextVariables.getMyHashNode())<= 0) {
            return true;
        }
        else if(hashNewkey.compareTo(contextVariables.getMyHashPredecessor()) > 0 && contextVariables.getMyHashPredecessor().compareTo(contextVariables.getMyHashNode())>0)
        {
            return true;
        }else if(hashNewkey.compareTo(contextVariables.getMyHashNode()) <= 0 && contextVariables.getMyHashPredecessor().compareTo(contextVariables.getMyHashNode())>0)
        {
            return true;
        }
        else {
            return false;
        }
    }

    private boolean LookUp(String newNode){
        String hashNewNode= genHashNodeWrapper(newNode);
        ContextVariables contextVariables= (ContextVariables)getContext();
        //for debug purpose
//        String temp=contextVariables.getMyHashNode();
//        String tempPred=contextVariables.getMyHashPredecessor();
//        int test= hashNewNode.compareTo(contextVariables.getMyHashNode());
        //end of debug code
        if(hashNewNode.compareTo(contextVariables.getMyHashPredecessor()) > 0 && hashNewNode.compareTo(contextVariables.getMyHashNode())<= 0) {
            return true;
        }
        else if(hashNewNode.compareTo(contextVariables.getMyHashPredecessor()) > 0 && contextVariables.getMyHashPredecessor().compareTo(contextVariables.getMyHashNode())>0)
//                && hashNewNode.compareTo(contextVariables.getMyHashSuccessor())> 0)
        {
            return true;
        }else if(hashNewNode.compareTo(contextVariables.getMyHashNode()) <= 0 && contextVariables.getMyHashPredecessor().compareTo(contextVariables.getMyHashNode())>0)
        {
            return true;
        }
        else {
            return false;
        }
    }

    private String getPortId(String NodeId){
        switch(NodeId){
            case "5554":
                return "11108";
            case "5556":
                return "11112";
            case "5558":
                return "11116";
            case "5560":
                return "11120";
            case "5562":
                return "11124";
        }
        return "";
    }

    /**
     * buildUri() demonstrates how to build a URI for a ContentProvider.
     *
     * @param scheme
     * @param authority
     * @return the URI
     */
    private static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
}


//Uri uri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
//
//ContentValues keyValueToInsert = new ContentValues();
//keyValueToInsert.put("key", Integer.toString(seqNum++));
//        keyValueToInsert.put("value",strReceived);
//        insert(uri,keyValueToInsert);