package com.nuyun.emoar;

import static android.content.ContentValues.TAG;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CompletableFuture;

public class GraphicLabelingActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private Interpreter tflite;
    private FaceDetector faceDetector;
    private TextView emotionTextView;
    private static final String[] EMOTION_LABELS = {"Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphiclabeling);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        emotionTextView = findViewById(R.id.emotionTextView);

        try {
            tflite = new Interpreter(loadModelFile("model.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        faceDetector = FaceDetection.getClient(highAccuracyOpts);

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    }

    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        try (Image image = frame.acquireCameraImage()) {
            if (image == null) {
                return;
            }
            Bitmap bitmap = imageToBitmap(image);
            detectFaces(bitmap, frame);
        } catch (Exception e) {
            Log.e("GraphicLabelingActivity", "Error acquiring camera image.", e);
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

    private void detectFaces(Bitmap bitmap, Frame frame) {
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        for (Face face : faces) {
                            RectF faceRect = new RectF(face.getBoundingBox());
                            String emotion = predictEmotion(bitmap, faceRect);
                            displayEmotion(emotion, frame, faceRect);
                        }
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace);
    }

    private String predictEmotion(Bitmap bitmap, RectF faceRect) {
        Bitmap faceBitmap = Bitmap.createBitmap(bitmap, (int) faceRect.left, (int) faceRect.top, (int) faceRect.width(), (int) faceRect.height());
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, 48, 48, false);

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 7}, DataType.FLOAT32);
        tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer());

        float[] outputArray = outputBuffer.getFloatArray();
        int maxIndex = -1;
        float maxConfidence = -1.0f;
        for (int i = 0; i < outputArray.length; i++) {
            if (outputArray[i] > maxConfidence) {
                maxConfidence = outputArray[i];
                maxIndex = i;
            }
        }
        return EMOTION_LABELS[maxIndex];
    }

    private void displayEmotion(String emotion, Frame frame, RectF faceRect) {
        // Display the emotion text on the TextView
        runOnUiThread(() -> emotionTextView.setText(emotion));

        // Create an Anchor at the specified location
        Pose pose = frame.getCamera().getPose().compose(Pose.makeTranslation(faceRect.centerX() / 1000, faceRect.centerY() / 1000, -1f));
        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
        AnchorNode anchorNode = new AnchorNode(anchor);

        // Create the ViewRenderable with the emotion text
        ViewRenderable.builder()
                .setView(this, R.layout.emotion_text_view) // Ensure you have a layout with a TextView
                .build()
                .thenAccept(viewRenderable -> {
                    // Find the TextView in the layout and set the text
                    TextView textView = (TextView) viewRenderable.getView();
                    textView.setText(emotion);
                    // Set the renderable to the anchor node
                    anchorNode.setRenderable(viewRenderable);

                    // Add the anchor node to the scene
                    arFragment.getArSceneView().getScene().addChild(anchorNode);
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error creating view renderable", throwable);
                    return null;
                });
    }

    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
