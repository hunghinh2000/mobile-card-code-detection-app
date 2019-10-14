package com.hungjoker.cardrecognization;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

public class MainActivity extends AppCompatActivity {
    private com.hungjoker.cardrecognization.CameraSource cameraSource;
    private  com.hungjoker.cardrecognization.CameraSource.Builder builder;
    private SurfaceView cameraView;
    private ImageView imageView;
    private TextView txtResult;
    private RelativeLayout scanner_view;
    private RadioGroup radioGroup;
    private Button btnSettings, btnFlash;

    private final int REQUEST_CAMERA_ID = 1001;
    private static final int REQUEST_PERMISSION_APP = 77;

    private Animation movingAnimation;
    private boolean isChose = false, isScanned = false, isFlash = false;

    public enum Card {
        Viettel, Vina, Mobi, Vietnam
    }

    private Card currCard = Card.Viettel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE}, REQUEST_PERMISSION_APP);
            return;
        }

        showDialog();
        createCameraSource();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_APP) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0) {
                this.recreate();
            } else {
                // TODO: 5/8/17 show permission denied
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
            }
        }
    }

    public void initView() {
        cameraView = (SurfaceView) findViewById(R.id.cameraView);
        txtResult = (TextView) findViewById(R.id.txtResult);
        imageView = (ImageView) findViewById(R.id.imageView);
        scanner_view = (RelativeLayout) findViewById(R.id.scanner_view);

        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });

        btnFlash = (Button) findViewById(R.id.btnFlash);
        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isFlash)
                    flashLightOn();
                else
                    flashLightOff();
            }
        });
    }

    public void showDialog() {
        isChose = false;

        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.activity_chooser);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        Button btnOk = dialog.findViewById(R.id.btnOk);
        radioGroup = dialog.findViewById(R.id.radioGroup);

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = radioGroup.getCheckedRadioButtonId();
                switch (id){
                    case R.id.rdbViettel:
                        currCard = Card.Viettel;
                        break;
                    case R.id.rdbMobifone:
                        currCard = Card.Mobi;
                        break;
                    case R.id.rdbVietnamobile:
                        currCard = Card.Vietnam;
                        break;
                    case R.id.rdbVinaphone:
                        currCard = Card.Vina;
                        break;
                }
                isChose = true;
                startAnimation();
                dialog.dismiss();
            }
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
    public void showResult(final String result) {
        isScanned = true;
        isChose = false;
        stopAnimation();

        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.activity_result);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        Button btnNap = dialog.findViewById(R.id.btnNap);
        Button btnSua = dialog.findViewById(R.id.btnSua);
        Button btnThoat = dialog.findViewById(R.id.btnThoat);
        final EditText edtResult = dialog.findViewById(R.id.edtResult);

        edtResult.setText(result);
        btnNap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CALL);
                String numberCard = "*100*"
                        + edtResult.getText().toString().trim()
                        + Uri.encode("#");

                intent.setData(Uri.parse("tel:" + numberCard));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                startActivity(intent);
            }
        });
        btnSua.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edtResult.setEnabled(true);
            }
        });
        btnThoat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isScanned =false;
                showDialog();
                dialog.dismiss();
            }
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
    private void createCameraSource() {

        TextRecognizer textRecognizer = new TextRecognizer.Builder(MainActivity.this).build();

        if (!textRecognizer.isOperational()) {
            Log.w("detectorError", "Detector dependencies not loaded yet");

            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, "low storage error", Toast.LENGTH_LONG).show();
                Log.w("lowstorage", "low storage error");
            }
            return;
        }

        builder = new com.hungjoker.cardrecognization.CameraSource.Builder(getApplicationContext(), textRecognizer)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1280, 1024)
                .setRequestedFps(15.0f)
                .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);

        cameraSource = builder.build();

        /*builder = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1280, 1024)
                .setRequestedFps(15.0f)
                .setAutoFocusEnabled(true);

        cameraSource = builder.build();*/

        //region cameraView
        cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CAMERA},
                                REQUEST_CAMERA_ID);
                        return;
                    }
                    cameraSource.start(cameraView.getHolder());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Camera error", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });
        //endregion

        //region Set text recognizer processor
        textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
            @Override
            public void release() {

            }
            @Override
            public void receiveDetections(Detector.Detections<TextBlock> detections) {
                if(!isChose || isScanned) return;

                final SparseArray<TextBlock> items = detections.getDetectedItems();
                if (items.size() != 0) {

                    txtResult.post(new Runnable() {
                        @Override
                        public void run() {

                            for (int i = 0; i < items.size(); i++) {
                                TextBlock item = items.valueAt(i);
                                String orginal = item.getValue();
                                String spaceDeleted = orginal.replaceAll("\\s","");
                                spaceDeleted = spaceDeleted.replaceAll("\\-", "");
                                if(!isNumeric(spaceDeleted)) continue;

                                Log.d("number", spaceDeleted);

                                switch (currCard){
                                    case Viettel:
                                        if(spaceDeleted.length() != 13 && spaceDeleted.length() != 15)
                                            continue;
                                        break;
                                    case Mobi:
                                    case Vietnam:
                                        if(spaceDeleted.length() != 12)
                                            continue;
                                        break;
                                    case Vina:
                                        if(spaceDeleted.length() != 14 && spaceDeleted.length()!=12)
                                            continue;
                                        break;
                                }

                                showResult(spaceDeleted);
                                break;
                            }
                        }
                    });

                }
            }
        });
        //endregion

    }
    public void flashLightOn(){
        cameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        isFlash = true;
    }
    public void flashLightOff(){
        cameraSource.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        isFlash = false;
    }
    public void startAnimation(){
        scanner_view.bringToFront();
        movingAnimation = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 1.0f);
        movingAnimation.setDuration(2000);
        movingAnimation.setRepeatCount(-1);
        movingAnimation.setRepeatMode(Animation.REVERSE);
        movingAnimation.setInterpolator(new LinearInterpolator());
        movingAnimation.start();
        imageView.setAnimation(movingAnimation);
    }
    public void stopAnimation(){
        movingAnimation.cancel();
    }
    public static boolean isNumeric(String strNum) {
        return strNum.length() == 0 || strNum.matches("-?\\d+(\\.\\d+)?");
    }


}
