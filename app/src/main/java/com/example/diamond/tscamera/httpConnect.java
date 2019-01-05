package com.example.diamond.tscamera;

import android.os.AsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class httpConnect extends AsyncTask<String, Void, byte[]> {

    private byte[] request;
    private String DirPath;
    private byte[] nonce;
    private byte[] hash;
    private byte[] jpgData;
    private byte[] x509Certificate;
    private byte[] mysign;


    httpConnect(byte[] request, String DirPath, byte[] nonce,
                byte[] hash, byte[] jpgData, byte[] x509Certificate, byte[] signature){
        this.request = request;
        this.DirPath = DirPath;
        this.nonce = nonce;
        this.hash = hash;
        this.jpgData = jpgData;
        this.x509Certificate = x509Certificate;
        this.mysign = signature;
    }



    @Override
    protected byte[] doInBackground(String... strings) {

        byte[] response = null;

        HttpURLConnection connection = null;

        try {
            URL url = new URL(strings[0]);

            connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(3000); // タイムアウト 3 秒
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/timestamp-query");
            connection.setUseCaches(false);
            connection.setFixedLengthStreamingMode(request.length);

            // タイムスタンプリクエストの書き込み
            BufferedOutputStream os = new BufferedOutputStream(connection.getOutputStream());
            os.write(request);
            os.flush();
            os.close();

            InputStream is = new BufferedInputStream(connection.getInputStream());
            int nBufSize = 1024 * 10;		// とりあえずタイムスタンプ応答は10KB未満とする
            byte[] buf = new byte[nBufSize];
            int len = 0;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            while (true) {
                int len2 = is.read(buf);
                if (len2 < 0) {
                    break;
                }
                len += len2;
                byteArrayOutputStream.write(buf, 0, len2);
            }
            buf = byteArrayOutputStream.toByteArray();

            // 成功したのでタイムスタンプレスポンスが返っているはず
            response = Arrays.copyOf(buf, len);
            is.close();


        } catch (IOException e) {
            e.printStackTrace();

        }finally {

            assert connection != null;
            connection.disconnect();
        }

        return response;
    }




    @Override
    protected void onPostExecute(byte[] response) {
        super.onPostExecute(response);

        try {
            IOandConversion.saveBinary(DirPath,response,"/response.res");

            byte[] tst = FreeTimeStamp.parseResponse(response, nonce);

            IOandConversion.saveBinary(DirPath, tst,"/tst.tst");

            FreeTimeStamp freeTimeStamp = new FreeTimeStamp(
                    tst, nonce, hash, jpgData, x509Certificate, DirPath, mysign);

            // タイムスタンプトークンの解析

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}