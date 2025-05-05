package com.project1.speechtotext;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SpeechToText";
    private TextView resultTextView;
    private Button recordButton, playButton, saveButton, transcribeButton;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private boolean isRecording = false;

    // Deepgram API Configurations
    private static final String DEEPGRAM_API_KEY = "bca7c19847394764dd2bb6c808f484662922e8a9";
    private static final String DEEPGRAM_API_URL = "https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        resultTextView = findViewById(R.id.textView_result);
        recordButton = findViewById(R.id.button_record);
        playButton = findViewById(R.id.button_play);
        saveButton = findViewById(R.id.button_save);
        transcribeButton = findViewById(R.id.button_transcribe);

        // Set initial states
        playButton.setEnabled(false);
        transcribeButton.setEnabled(false);
        resultTextView.setText("Press record to start...");
        resultTextView.setMovementMethod(new ScrollingMovementMethod());

        // Request permissions
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        // Set up button click listeners
        recordButton.setOnClickListener(v -> {
            if (permissionToRecordAccepted) {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        });

        playButton.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                releaseMediaPlayer();
                playButton.setText("Play");
            } else {
                playRecording();
            }
        });

        saveButton.setOnClickListener(v -> saveTranscribedText());

        transcribeButton.setOnClickListener(v -> {
            transcribeButton.setEnabled(false);
            transcribeAudioFile();
        });
    }

    //this method will start recording the audio and save it to a file in documents directory First I was saving in music directory but professor told me save on same path.
    private void startRecording() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (!dir.exists()) dir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            audioFilePath = new File(dir, "recording_" + timeStamp + ".wav").getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordButton.setText("Stop");
            playButton.setEnabled(false);
            transcribeButton.setEnabled(false);
            resultTextView.setText("Recording...");

            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Recording failed", e);
            Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();

                isRecording = false;
                recordButton.setText("Record");
                playButton.setEnabled(true);
                transcribeButton.setEnabled(true);

                Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
                resultTextView.setText("Recording complete. Press Play or Transcribe.");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording", e);
                Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show();
            } finally {
                mediaRecorder = null;
            }
        }
    }

    private void playRecording() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            playButton.setText("Stop");

            mediaPlayer.setOnCompletionListener(mp -> {
                playButton.setText("Play");
                releaseMediaPlayer();
            });
        } catch (IOException e) {
            Toast.makeText(this, "Playback failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Playback error", e);
        }
    }

    //This method will transcribe the audio file using the Deepgram API
    private void transcribeAudioFile() {
        if (audioFilePath == null || audioFilePath.isEmpty()) {
            Toast.makeText(this, "No recording available to transcribe", Toast.LENGTH_SHORT).show();
            transcribeButton.setEnabled(true);
            return;
        }

        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show();
            transcribeButton.setEnabled(true);
            return;
        }

        resultTextView.setText("Transcribing... Please wait");

        try {
            // Read the audio file into a byte array
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] audioBytes = new byte[(int) audioFile.length()];
            fis.read(audioBytes);
            fis.close();

            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("audio/wav"),
                    audioBytes
            );

            Request request = new Request.Builder()
                    .url(DEEPGRAM_API_URL)
                    .addHeader("Authorization", "Token " + DEEPGRAM_API_KEY)
                    .addHeader("Content-Type", "audio/wav")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Transcription failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        resultTextView.setText("Transcription error: " + e.getMessage());
                        transcribeButton.setEnabled(true);
                    });
                    Log.e(TAG, "API call failed", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected code " + response);
                        }

                        String responseData = response.body().string();
                        Log.d(TAG, "API Response: " + responseData);

                        JSONObject jsonResponse = new JSONObject(responseData);
                        JSONObject results = jsonResponse.getJSONObject("results");
                        JSONObject channels = results.getJSONArray("channels").getJSONObject(0);
                        JSONObject alternatives = channels.getJSONArray("alternatives").getJSONObject(0);
                        final String transcript = alternatives.getString("transcript");

                        runOnUiThread(() -> {
                            resultTextView.setText(transcript);
                            Toast.makeText(MainActivity.this, "Transcription complete", Toast.LENGTH_SHORT).show();
                            transcribeButton.setEnabled(true);
                        });

                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Error processing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            resultTextView.setText("Error: " + e.getMessage());
                            transcribeButton.setEnabled(true);
                        });
                        Log.e(TAG, "Response processing error", e);
                    }
                }
            });
        } catch (IOException e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Error reading audio file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                transcribeButton.setEnabled(true);
            });
            Log.e(TAG, "File read error", e);
        }
    }

    //This method will save the transcribed text to a file in the documents directory
    private void saveTranscribedText() {
        String text = resultTextView.getText().toString();
        if (text.isEmpty() || text.startsWith("Press") || text.startsWith("Recording") || text.startsWith("Transcribing")) {
            Toast.makeText(this, "No valid text to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "transcript_" + timeStamp + ".txt");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(text.getBytes());
            fos.close();

            Toast.makeText(this, "Transcript saved to:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving transcript: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Save error", e);
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!permissionToRecordAccepted) {
                Toast.makeText(this, "Permission denied - app cannot record audio", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
        }
        releaseMediaPlayer();
    }
}