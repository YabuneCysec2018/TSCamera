package com.example.diamond.tscamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.StateCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener, View.OnClickListener, LocationListener {

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder mCaptureRequestBuilder;//プレビュー用
    private CaptureRequest.Builder captureBuilder;//////////撮影用
    private CaptureRequest mCaptureRequest;

    private TextureView mTextureView;
    private SurfaceTexture mSurfaceTexture;

    ImageReader imageReader;

    private String mCameraId;
    private CameraCharacteristics characteristics;
    private String DirPath;

    private LocationManager locationManager;
    Location location;

    double latitude;        //緯度
    double longitude;       //経度


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = findViewById(R.id.textureview);

        mTextureView.setSurfaceTextureListener(this);

        findViewById(R.id.shutter).setOnClickListener(this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            locationStart();
        }
    }



    ///////////////////////////////////////////////////////////Camera//////////////////////////////

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurfaceTexture = surface;
        try {
            openCamera();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return false; }
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }


    @SuppressLint("MissingPermission")
    private void openCamera() throws CameraAccessException {
        CameraManager mCameraManager
                = (CameraManager) getBaseContext().getSystemService(Context.CAMERA_SERVICE);

        assert mCameraManager != null;
        String[] cameraIdList = mCameraManager.getCameraIdList();

        for (String cameraId : cameraIdList) {
            characteristics = mCameraManager.getCameraCharacteristics(cameraId);
            if (characteristics.get(CameraCharacteristics.LENS_FACING)
                    == CameraCharacteristics.LENS_FACING_BACK) {
                //アウトカメラ
                mCameraId = cameraId;
                break;
            }
        }
        assert mCameraId != null;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mCameraManager.openCamera(mCameraId, mStateCallback, null);
    }


    private final CameraDevice.StateCallback mStateCallback
            = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //接続成功時CameraDeviceのインスタンスを保持
            mCameraDevice = camera;
            createCameraCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //接続切断時CameraDeviceをクローズ.CameraDeviceのインスタンスをnull.
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };


    public void createCameraCaptureSession() {
        //CaptureRequestを生成
        try {
            StreamConfigurationMap map
                    = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size mCameraBufferSize = map.getOutputSizes(SurfaceTexture.class)[0];
            mSurfaceTexture.setDefaultBufferSize(mCameraBufferSize.getWidth(), mCameraBufferSize.getHeight());
            Surface mSurface = new Surface(mSurfaceTexture);




            //プレビュー用
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(mSurface);
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);

            //撮影用
            imageReader = ImageReader.newInstance(mCameraBufferSize.getWidth(), mCameraBufferSize.getHeight(), ImageFormat.JPEG,1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener,null);
            captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);




            //ビューを登録
            ArrayList<Surface> surfaceArrayList = new ArrayList<>();
            surfaceArrayList.add(mSurface);
            surfaceArrayList.add(imageReader.getSurface());               //imageReaderを登録したい


            //CameraCaptureSessionを生成
            mCameraDevice.createCaptureSession
                    (surfaceArrayList, CaptureSessionCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private final StateCallback CaptureSessionCallback
            = new StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCaptureSession = session;
            RepeatingRequest();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            //Session設定失敗時
            Log.e("TAG", "error");
        }
    };


    public void RepeatingRequest() {   //TextureViewへカメラの画像を連続表示
        mCaptureRequest = mCaptureRequestBuilder.build();

        try {
            mCaptureSession.setRepeatingRequest(mCaptureRequest, null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    /////////////////////////////////////////////////////////////Location//////////////////////////

    private void locationStart() {
        Log.d("debug", "locationStart()");

        // LocationManager インスタンス生成
        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        if (locationManager != null
                && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            Log.d("debug", "location manager Enabled");
        } else {
            // GPSを設定するように促す
            Intent settingsIntent =
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(settingsIntent);
            Log.d("debug", "not gpsEnable, startActivity");
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 100);

            Log.d("debug", "checkSelfPermission false");
            return;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                5000, 5, this);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1000) {
            // 使用が許可された
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("debug", "checkSelfPermission true");

                locationStart();

            } else {
                // それでも拒否された時の対応
                Toast toast = Toast.makeText(this,
                        "これ以上なにもできません", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        latitude = location.getLatitude();//緯度
        longitude= location.getLongitude();//経度

        this.location = location;


        Toast toast = Toast.makeText(this, "位置情報更新", Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

        switch (status) {
            case LocationProvider.AVAILABLE:
                Log.d("debug", "LocationProvider.AVAILABLE");
                break;
            case LocationProvider.OUT_OF_SERVICE:
                Log.d("debug", "LocationProvider.OUT_OF_SERVICE");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                Log.d("debug", "LocationProvider.TEMPORARILY_UNAVAILABLE");
                break;
        }
    }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }




    /////////////////////////////////////////////////////// Start for Crick ////////////////////////


    @Override
    public void onClick(View v) {
        try {
            mCaptureSession.stopRepeating();
            //locationManager.removeUpdates(this);

            byte[] picBin = null;

            mCaptureSession.capture(captureBuilder.build(), mCameraCallback, null);



            //Make signature
            byte[] signature = SignatureTool.SIGN(picBin);
            String signedText = IOandConversion.byteToString(signature);
            IOandConversion.saveStrings(DirPath, signedText, "/SignedData.txt");
            IOandConversion.saveBinary(DirPath, signature, "/SignedData.bin");


            //Make hash for TimeStamp
            byte[] TShash = IOandConversion.getSHA256(signature);
            String TSHashText = IOandConversion.byteToString(TShash);
            IOandConversion.saveStrings(DirPath, TSHashText, "/HashForTimeStamp.txt");
            IOandConversion.saveBinary(DirPath, TShash, "/TShash.bin");

            FreeTimeStamp freeTimeStamp = new FreeTimeStamp();

            //IOandConversion.setExif(DirPath + "/PictureData.jpg", latitude, longitude);
            //picBin = IOandConversion.fileToBytes(new File(DirPath + "/PictureData.jpg"));

            freeTimeStamp.getFromServer(DirPath, TShash, picBin);

            mCaptureSession.setRepeatingRequest(mCaptureRequest,null,null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private byte[] takePicture() throws IOException {

        File topFIle = getExternalFilesDir(null);
        assert topFIle != null;
        File[] files = topFIle.listFiles();   ///Count File
        DirPath = topFIle + "/ContentsFile" + files.length;
        File contentsFile = new File(DirPath);

        if (!contentsFile.exists()) {
            if (!contentsFile.mkdirs()) {
                Log.i("MainActivity :", "ディレクトリ作成失敗");
            }
        }

        File picFile = new File(contentsFile, "PictureData.jpg");



        FileOutputStream fos = new FileOutputStream(picFile);
        Bitmap bmp = mTextureView.getBitmap();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos); //保存
        fos.close();

        return IOandConversion.fileToBytes(picFile);
    }

    CameraCaptureSession.CaptureCallback mCameraCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession session,
                                               android.hardware.camera2.CaptureRequest request,
                                               android.hardware.camera2.TotalCaptureResult result) {

                    if (result != null){
                        int a = 1+1;
                    }

                }
    };

    ImageReader.OnImageAvailableListener onImageAvailableListener=
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = imageReader.acquireNextImage();



                    image.close();
                }
            };

}