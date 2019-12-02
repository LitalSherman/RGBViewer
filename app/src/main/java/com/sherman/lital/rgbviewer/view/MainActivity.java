package com.sherman.lital.rgbviewer.view;

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.sherman.lital.rgbviewer.services.CommonColorViewModel;
import com.sherman.lital.rgbviewer.R;
import com.sherman.lital.rgbviewer.model.CommonColorObj;
import com.sherman.lital.rgbviewer.model.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Observable;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AndroidCameraApi";
    //  Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_WIDTH = 1920;
    //  Max preview height that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private TextureView textureView;
    private CommonColorViewModel viewModel;
    private CommonColorView mostCommonV;
    private CommonColorView secondCommonV;
    private CommonColorView thirdCommonV;
    private CommonColorView fourthCommonV;
    private CommonColorView fifthCommonV;
    private CommonColorObj mostCommon;
    private CommonColorObj secondCommon;
    private CommonColorObj thirdCommon;
    private CommonColorObj fourthCommon;
    private CommonColorObj fifthCommon;
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Size previewSize;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ImageReader imageReader;
    private Observer<Pair> colorsKeyValuePair;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);

        mostCommonV = findViewById(R.id.most_common_view);
        secondCommonV = findViewById(R.id.second_common_view);
        thirdCommonV = findViewById(R.id.third_common_view);
        fourthCommonV = findViewById(R.id.fourth_common_view);
        fifthCommonV = findViewById(R.id.fifth_common_view);

        // mostCommonObj for each view
        mostCommon = new CommonColorObj();
        secondCommon = new CommonColorObj();
        thirdCommon = new CommonColorObj();
        fourthCommon = new CommonColorObj();
        fifthCommon = new CommonColorObj();

        viewModel = ViewModelProviders.of(this).get(CommonColorViewModel.class);
        colorsKeyValuePair = new Observer<Pair>() {
            @Override
            public void onChanged(@Nullable Pair pair) {
                setCommonColorView(pair, viewModel.getRgbBufferLength());
            }
        };
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;

            final Image.Plane[] planes = image.getPlanes();
            final int total = planes[0].getRowStride() * previewSize.getHeight();
           getLastColorsKeyValueLiveData(total, planes);
            image.close();
        }
    };

    private void getLastColorsKeyValueLiveData(int total, Image.Plane[] planes){
        viewModel.getColorsKeyValueLiveData(total, planes, previewSize.getHeight()).observe(this, colorsKeyValuePair);
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            List surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            Surface readerSurface = imageReader.getSurface();
            surfaces.add(readerSurface);
            captureRequestBuilder.addTarget(readerSurface);

            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            //The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            cameraCaptureSessions = cameraCaptureSession;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[CameraCharacteristics.LENS_FACING_FRONT];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            imageDimension = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                    new CompareSizesByArea());

            imageReader = ImageReader.newInstance(imageDimension.getWidth(),
                    imageDimension.getHeight(),
                    ImageFormat.YUV_420_888, 1);
            imageReader.setOnImageAvailableListener(imageAvailableListener, mBackgroundHandler);


            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
                default:
                    Log.e(TAG, "Display rotation is invalid: " + displayRotation);
            }

            Point displaySize = new Point();
            getWindowManager().getDefaultDisplay().getSize(displaySize);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight, imageDimension);

            configureTransform(width, height);

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSessions != null) {
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void setCommonColorView(final Pair keyValue, final int allColors) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mostCommon.setCommonColorObj(keyValue.getKeys()[0], keyValue.getValues()[0], allColors);
                mostCommonV.setRValue(mostCommon.getR());
                mostCommonV.setGValue(mostCommon.getG());
                mostCommonV.setBValue(mostCommon.getB());
                mostCommonV.setColor(mostCommon.getHexColor());
                mostCommonV.setRgbPercentage(mostCommon.getPercentage());

                secondCommon.setCommonColorObj(keyValue.getKeys()[1], keyValue.getValues()[1], allColors);
                secondCommonV.setRValue(secondCommon.getR());
                secondCommonV.setGValue(secondCommon.getG());
                secondCommonV.setBValue(secondCommon.getB());
                secondCommonV.setColor(secondCommon.getHexColor());
                secondCommonV.setRgbPercentage(secondCommon.getPercentage());

                thirdCommon.setCommonColorObj(keyValue.getKeys()[2], keyValue.getValues()[2], allColors);
                thirdCommonV.setRValue(thirdCommon.getR());
                thirdCommonV.setGValue(thirdCommon.getG());
                thirdCommonV.setBValue(thirdCommon.getB());
                thirdCommonV.setColor(thirdCommon.getHexColor());
                thirdCommonV.setRgbPercentage(thirdCommon.getPercentage());

                fourthCommon.setCommonColorObj(keyValue.getKeys()[3], keyValue.getValues()[3], allColors);
                fourthCommonV.setRValue(fourthCommon.getR());
                fourthCommonV.setGValue(fourthCommon.getG());
                fourthCommonV.setBValue(fourthCommon.getB());
                fourthCommonV.setColor(fourthCommon.getHexColor());
                fourthCommonV.setRgbPercentage(fourthCommon.getPercentage());

                fifthCommon.setCommonColorObj(keyValue.getKeys()[4], keyValue.getValues()[4], allColors);
                fifthCommonV.setRValue(fifthCommon.getR());
                fifthCommonV.setGValue(fifthCommon.getG());
                fifthCommonV.setBValue(fifthCommon.getB());
                fifthCommonV.setColor(fifthCommon.getHexColor());
                fifthCommonV.setRgbPercentage(fifthCommon.getPercentage());
            }
        });
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


}
