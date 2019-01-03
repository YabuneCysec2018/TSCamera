package com.example.diamond.tscamera;



import android.location.Location;
import android.media.ExifInterface;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

class IOandConversion {

    static void saveStrings(String DirPath, String string, String path)
            throws IOException {

        String DirPathH = DirPath + path;
        FileOutputStream fosHash = new FileOutputStream(DirPathH, true);
        OutputStreamWriter OSwriterH = new OutputStreamWriter(fosHash, "UTF-8");
        BufferedWriter BwriterH = new BufferedWriter(OSwriterH);
        BwriterH.write(string);
        BwriterH.flush();
    }


    static String byteToString(byte[] bytes) {

        StringBuilder stringBuilder = new StringBuilder(2*bytes.length);

        for(byte b : bytes) {

            stringBuilder.append(String.format("%02x", b & 0xff));
        }
        return stringBuilder.toString();
    }


    static byte[] fileToBytes(File file) throws IOException {
        //Jpegから直接バイト配列へ
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] bytes = new byte[1250];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (fileInputStream.read(bytes) > 0){
            baos.write(bytes);
        }
        baos.close();
        fileInputStream.close();
        return baos.toByteArray();
    }

    static byte[] pathToBytes(String path) throws IOException {
        //Jpegから直接バイト配列へ
        FileInputStream fileInputStream = new FileInputStream(path);
        byte[] bytes = new byte[1250];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (fileInputStream.read(bytes) > 0){
            baos.write(bytes);
        }
        baos.close();
        fileInputStream.close();
        return baos.toByteArray();
    }


    static void saveBinary(String dirpath, byte[] bytes, String filename)throws Exception {

        FileOutputStream fos = new FileOutputStream(dirpath + filename);
        fos.write(bytes);
        fos.flush();
        fos.close();
    }


    static byte[] getSHA256(byte[] bytes) throws NoSuchAlgorithmException {
        String hashALGORITHM = "SHA-256";
        MessageDigest digest = MessageDigest.getInstance(hashALGORITHM);

        digest.update(bytes);

        return digest.digest();
    }


    static byte[] setDataToJPEG(byte[] original, byte[] tst, byte[] cert){
        int bytesLength = original.length;
        byte[] result = new byte[2];
        int read = 0;
        int write= 2;
        int DQTcount = 0;


        //SOIのみコピー
        if(original[read++] == JPEGTag.MARKER && original[read++] == JPEGTag.SOI){
            System.arraycopy(original,0,result,0,2);
        }

        while(true){                      //ファイルの最後まで

            if (original[read] == JPEGTag.MARKER){
                //セグメント長読み込み
                int segLen = original[read + 2];
                if (segLen < 0) {
                    segLen += 256;
                }
                segLen *= 256;

                segLen += original[read + 3];
                if (original[read + 3] < 0) {
                    segLen += 256;
                }

                if (original[read + 1] == JPEGTag.DQT && DQTcount == 0){
                    //DQTをそのままコピー
                    result = Arrays.copyOf(result, result.length + segLen + 2);
                    System.arraycopy(original, read, result, write, segLen + 2);
                    write += segLen + 2;            //次回書き込み・読み込み位置を設定
                    read  += segLen + 2;

                    //APP10(Certificate)
                    result = Arrays.copyOf(result, result.length + cert.length + 2);
                    result[write++] = JPEGTag.MARKER;                       //マーカ
                    result[write++] = JPEGTag.APP10;                        //APP10
                    System.arraycopy(cert, 0, result, write, cert.length);
                    write += cert.length;

                    //APP11(TST)セグメントを挟み込む
                    result = Arrays.copyOf(result, result.length + tst.length + 2);
                    result[write++] = JPEGTag.MARKER;                       //マーカ
                    result[write++] = JPEGTag.APP11;                       //APP11タグ
                    System.arraycopy(tst, 0, result, write, tst.length);
                    write += tst.length;

                    DQTcount++;


                } else if (original[read + 1] == JPEGTag.DQT){

                    //resultをセグメント長分伸ばし、増えたところに新セグメントをコピー
                    result = Arrays.copyOf(result, result.length + segLen + 2);
                    System.arraycopy(original, read, result, write, segLen + 2);

                    write += segLen + 2;            //次回書き込み・読み込み位置を設定
                    read  += segLen + 2;


                } else if (original[read + 1] == JPEGTag.DHT
                        || original[read + 1] == JPEGTag.SOI
                        || original[read + 1] == JPEGTag.SOF0
                        || original[read + 1] == JPEGTag.APP0
                        || original[read + 1] == JPEGTag.APP1
                        || original[read + 1] == JPEGTag.APP5
                        || original[read + 1] == JPEGTag.APP6
                        || original[read + 1] == JPEGTag.APP7){

                    //resultをセグメント長分伸ばし、増えたところに新セグメントをコピー
                    result = Arrays.copyOf(result, result.length + segLen + 2);
                    System.arraycopy(original, read, result, write, segLen + 2);

                    write += segLen + 2;            //次回書き込み・読み込み位置を設定
                    read  += segLen + 2;


                } else if (original[read + 1] == JPEGTag.SOS) {  //SOSの次は直接画像データがくる

                    //resultをセグメント長分伸ばし、増えたところに最後までコピー
                    result = Arrays.copyOf(result, result.length + bytesLength - read);
                    System.arraycopy(original, read, result, write, bytesLength - read);
                    break;

                } else { read += segLen + 2;}    //readを次のマーカーの位置に合わせる


            }
        }

        return result;

    }


    static void setExif(String path, double latitude, double longitude) throws IOException {

        ExifInterface exifInterface = new ExifInterface(path);

        String[] lonDMS = Location.convert(longitude, Location.FORMAT_SECONDS).split(":");
        StringBuilder lon = new StringBuilder();

        if (lonDMS[0].contains("-")){    //東西判定
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,"W");
        }else{
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,"E");
        }

        lon.append(lonDMS[0].replace("-", ""));
        lon.append("/1,");
        lon.append(lonDMS[1]);
        lon.append("/1,");

        int index = lonDMS[2].indexOf(".");
        if (index == -1){
            lon.append(lonDMS[2]);
            lon.append("/1");
        } else {
            int digit = lonDMS[2].substring(index + 1).length();
            int second = (int) (Double.parseDouble(lonDMS[2]) * Math.pow(10,digit));
            lon.append(String.valueOf(second));
            lon.append("/1");
            for (int i = 0; i < digit; i++){
                lon.append("0");
            }
        }
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,lon.toString());


        String[] latDMS = Location.convert(latitude, Location.FORMAT_SECONDS).split(":");
        StringBuilder lat = new StringBuilder();

        if (latDMS[0].contains("-")){    //東西判定
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"S");
        }else{
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,"N");
        }

        lat.append(latDMS[0].replace("-", ""));
        lat.append("/1,");
        lat.append(latDMS[1]);
        lat.append("/1,");

        index = latDMS[2].indexOf(".");
        if (index == -1){
            lat.append(latDMS[2]);
            lat.append("/1");
        } else {
            int digit = latDMS[2].substring(index + 1).length();
            int second = (int) (Double.parseDouble(latDMS[2]) * Math.pow(10,digit));
            lat.append(String.valueOf(second));
            lat.append("/1");
            for (int i = 0; i < digit; i++){
                lat.append("0");
            }
        }
        exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE,lat.toString());

        exifInterface.saveAttributes();
    }

    //JPEGタグまとめ
    public interface JPEGTag{
        byte MARKER = (byte) 0xff;
        byte SOI = (byte) 0xd8;
        byte DQT = (byte) 0xdb;
        byte DHT = (byte) 0xc4;
        byte SOS = (byte) 0xda;
        byte APP0= (byte) 0xe0;
        byte APP1= (byte) 0xe1;
        byte APP5= (byte) 0xe5;
        byte APP6= (byte) 0xe6;
        byte APP7= (byte) 0xe7;
        byte APP10=(byte) 0xea;
        byte APP11=(byte) 0xeb;
        byte APP15=(byte) 0xef;
        byte SOF0= (byte) 0xc0;
    }
}