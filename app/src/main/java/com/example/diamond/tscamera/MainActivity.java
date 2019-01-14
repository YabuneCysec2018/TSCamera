package com.example.diamond.tscamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity
        implements TextureView.SurfaceTextureListener, View.OnClickListener, LocationListener {

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder previewCaptureBuilder;//プレビュー用
    private CaptureRequest.Builder shootCaptureBuilder;//////////撮影用
    private CaptureRequest mCaptureRequest;

    private SurfaceTexture mSurfaceTexture;

    ImageReader imageReader;

    private String mCameraId;
    private CameraCharacteristics characteristics;

    Location location;

    double latitude;        //緯度
    double longitude;       //経度


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextureView mTextureView = findViewById(R.id.textureview);

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
            previewCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewCaptureBuilder.addTarget(mSurface);
            previewCaptureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);

            //撮影用
            imageReader = ImageReader.newInstance(mCameraBufferSize.getWidth(), mCameraBufferSize.getHeight(), ImageFormat.JPEG,1);
            imageReader.setOnImageAvailableListener(onImageAvailableListener,null);
            shootCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            shootCaptureBuilder.addTarget(imageReader.getSurface());
            shootCaptureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, location);


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
        mCaptureRequest = previewCaptureBuilder.build();

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
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

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
            mCaptureSession.capture(shootCaptureBuilder.build(), mCameraCallback, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    CameraCaptureSession.CaptureCallback mCameraCallback =
            new CameraCaptureSession.CaptureCallback() { };


    ImageReader.OnImageAvailableListener onImageAvailableListener=
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = imageReader.acquireNextImage();

                    File topFIle = getFilesDir();
                    assert topFIle != null;
                    File[] files = topFIle.listFiles();   ///Count File
                    String dirPath = topFIle + "/ContentsFile" + files.length;
                    File contentsFile = new File(dirPath);

                    if (!contentsFile.exists()) {
                        if (!contentsFile.mkdirs()) {
                            Log.i("MainActivity :", "ディレクトリ作成失敗");
                        }
                    }

                    try {

                        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                        byte[] imageBytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(imageBytes);
                        image.close();

                        FileOutputStream fos = new FileOutputStream(dirPath +"/PictureData.jpeg");
                        fos.write(imageBytes);
                        fos.close();

                        //Make signature
                        SignatureTool signatureTool = new SignatureTool();
                        byte[] signature = signatureTool.SIGN(imageBytes);
                        IOandConversion.saveBinary(dirPath, signature, "/SignedData.bin");
                        X509Certificate x509Certificate = signatureTool.getX509Certificate();
                        byte[] x509byte = x509Certificate.getTBSCertificate();

                        //Make hash for TimeStamp
                        byte[] TShash = IOandConversion.getSHA256(signature);

                        //set Location data
                        IOandConversion.setExif(dirPath + "/PictureData.jpeg", latitude, longitude);
                        imageBytes = IOandConversion.fileToBytes(new File(dirPath + "/PictureData.jpeg"));

                        // nonceの生成
                        byte[] nonce = new byte[8];
                        new Random().nextBytes(nonce);
                        //make TimeStampRequest
                        byte[] request = FreeTimeStamp.makeRequest(TShash, nonce);

                        //send Request
                        httpConnect connect = new httpConnect(
                                request, dirPath, nonce, TShash, imageBytes, x509byte, signature);
                        connect.execute("http://eswg.jnsa.org/freetsa");

                        mCaptureSession.setRepeatingRequest(mCaptureRequest,null,null);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    image.close();
                }
            };
}