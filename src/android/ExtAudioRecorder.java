package org.apache.cordova.media;

import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class ExtAudioRecorder {
    public static final boolean RECORDING_COMPRESSED = false;
    public static final boolean RECORDING_UNCOMPRESSED = true;
    private static final int TIMER_INTERVAL = 120;
    public static final int[] sampleRates = new int[]{44100, 22050, 16000, 11025, 8000};
    private int aFormat;
    private int aSource;
    private AudioRecord audioRecorder = null;
    private short bSamples;
    private ByteArrayOutputStream baos;
    private byte[] buffer;
    private int bufferSize;
    private int cAmplitude = 0;
    private DataOutputStream dos;
    private String filePath = null;
    private int framePeriod;
    private MediaRecorder mediaRecorder = null;
    private short nChannels;
    private int payloadSize;
    private boolean rUncompressed;
    private RandomAccessFile randomAccessWriter;
    private int sRate;
    private State state;
    private OnRecordPositionUpdateListener updateListener = new OnRecordPositionUpdateListener() {
        public void onPeriodicNotification(AudioRecord recorder) {
            ExtAudioRecorder.this.audioRecorder.read(ExtAudioRecorder.this.buffer, 0, ExtAudioRecorder.this.buffer.length);
            try {
                ExtAudioRecorder.this.dos.write(ExtAudioRecorder.this.buffer);
                ExtAudioRecorder.access$312(ExtAudioRecorder.this, ExtAudioRecorder.this.buffer.length);
                int i;
                if (ExtAudioRecorder.this.bSamples == (short) 16) {
                    for (i = 0; i < ExtAudioRecorder.this.buffer.length / 2; i++) {
                        short curSample = ExtAudioRecorder.this.getShort(ExtAudioRecorder.this.buffer[i * 2], ExtAudioRecorder.this.buffer[(i * 2) + 1]);
                        if (curSample > ExtAudioRecorder.this.cAmplitude) {
                            ExtAudioRecorder.this.cAmplitude = curSample;
                        }
                    }
                    return;
                }
                for (i = 0; i < ExtAudioRecorder.this.buffer.length; i++) {
                    if (ExtAudioRecorder.this.buffer[i] > ExtAudioRecorder.this.cAmplitude) {
                        ExtAudioRecorder.this.cAmplitude = ExtAudioRecorder.this.buffer[i];
                    }
                }
            } catch (IOException e) {
                Log.e(ExtAudioRecorder.class.getName(), "Error occured in updateListener, recording is aborted");
            }
        }

        public void onMarkerReached(AudioRecord recorder) {
        }
    };

    public enum State {
        INITIALIZING,
        READY,
        RECORDING,
        ERROR,
        STOPPED
    }

    static /* synthetic */ int access$312(ExtAudioRecorder x0, int x1) {
        int i = x0.payloadSize + x1;
        x0.payloadSize = i;
        return i;
    }

    public static ExtAudioRecorder getInstanse(Boolean recordingCompressed) {
        if (recordingCompressed.booleanValue()) {
            return new ExtAudioRecorder(false, 1, sampleRates[3], 16, 2);
        }
        return new ExtAudioRecorder(true, 1, sampleRates[3], 16, 2);
    }

    public State getState() {
        return this.state;
    }

    public ExtAudioRecorder(boolean uncompressed, int audioSource, int sampleRate, int channelConfig, int audioFormat) {
        try {
            this.rUncompressed = uncompressed;
            if (this.rUncompressed) {
                if (audioFormat == 2) {
                    this.bSamples = (short) 16;
                } else {
                    this.bSamples = (short) 8;
                }
                if (channelConfig == 16) {
                    this.nChannels = (short) 1;
                } else {
                    this.nChannels = (short) 2;
                }
                this.aSource = audioSource;
                this.sRate = sampleRate;
                this.aFormat = audioFormat;
                this.framePeriod = (sampleRate * TIMER_INTERVAL) / 1000;
                this.bufferSize = (((this.framePeriod * 2) * this.bSamples) * this.nChannels) / 8;
                if (this.bufferSize < AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)) {
                    this.bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                    this.framePeriod = this.bufferSize / (((this.bSamples * 2) * this.nChannels) / 8);
                    Log.w(ExtAudioRecorder.class.getName(), "Increasing buffer size to " + Integer.toString(this.bufferSize));
                }
                this.audioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, this.bufferSize);
                if (this.audioRecorder.getState() != 1) {
                    throw new Exception("AudioRecord initialization failed");
                }
                this.audioRecorder.setRecordPositionUpdateListener(this.updateListener);
                this.audioRecorder.setPositionNotificationPeriod(this.framePeriod);
            } else {
                this.mediaRecorder = new MediaRecorder();
                this.mediaRecorder.setAudioSource(1);
                this.mediaRecorder.setOutputFormat(1);
                this.mediaRecorder.setAudioEncoder(1);
            }
            this.cAmplitude = 0;
            this.filePath = null;
            this.state = State.INITIALIZING;
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured while initializing recording");
            }
            this.state = State.ERROR;
        }
    }

    public void setOutputFile(String argPath) {
        try {
            if (this.state == State.INITIALIZING) {
                this.filePath = argPath;
                if (!this.rUncompressed) {
                    this.mediaRecorder.setOutputFile(this.filePath);
                }
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured while setting output path");
            }
            this.state = State.ERROR;
        }
    }

    public int getMaxAmplitude() {
        if (this.state != State.RECORDING) {
            return 0;
        }
        if (this.rUncompressed) {
            int result = this.cAmplitude;
            this.cAmplitude = 0;
            return result;
        }
        try {
            return this.mediaRecorder.getMaxAmplitude();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public void prepare() {
        int i = 1;
        try {
            if (this.state != State.INITIALIZING) {
                Log.e(ExtAudioRecorder.class.getName(), "prepare() method called on illegal state");
                release();
                this.state = State.ERROR;
            } else if (this.rUncompressed) {
                int i2;
                if (this.audioRecorder.getState() == 1) {
                    i2 = 1;
                } else {
                    i2 = 0;
                }
                if (this.filePath == null) {
                    i = 0;
                }
                if ((i & i2) != 0) {
                    this.baos = new ByteArrayOutputStream();
                    this.dos = new DataOutputStream(this.baos);
                    this.dos.writeBytes("RIFF");
                    this.dos.writeInt(0);
                    this.dos.writeBytes("WAVE");
                    this.dos.writeBytes("fmt ");
                    this.dos.writeInt(Integer.reverseBytes(16));
                    this.dos.writeShort(Short.reverseBytes((short) 1));
                    this.dos.writeShort(Short.reverseBytes(this.nChannels));
                    this.dos.writeInt(Integer.reverseBytes(this.sRate));
                    this.dos.writeInt(Integer.reverseBytes(((this.sRate * this.bSamples) * this.nChannels) / 8));
                    this.dos.writeShort(Short.reverseBytes((short) ((this.nChannels * this.bSamples) / 8)));
                    this.dos.writeShort(Short.reverseBytes(this.bSamples));
                    this.dos.writeBytes("data");
                    this.dos.writeInt(0);
                    this.buffer = new byte[(((this.framePeriod * this.bSamples) / 8) * this.nChannels)];
                    this.state = State.READY;
                    return;
                }
                Log.e(ExtAudioRecorder.class.getName(), "prepare() method called on uninitialized recorder");
                this.state = State.ERROR;
            } else {
                this.mediaRecorder.prepare();
                this.state = State.READY;
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
            } else {
                Log.e(ExtAudioRecorder.class.getName(), "Unknown error occured in prepare()");
            }
            this.state = State.ERROR;
        }
    }

    public void release() {
        if (this.state == State.RECORDING) {
            stop();
        } else {
            if (((this.state == State.READY ? true : false) & this.rUncompressed)) {
                try {
                    this.baos.close();
                } catch (IOException e) {
                    Log.e(ExtAudioRecorder.class.getName(), "I/O exception occured while closing output stream");
                }
                new File(this.filePath).delete();
            }
        }
        if (this.rUncompressed) {
            if (this.audioRecorder != null) {
                this.audioRecorder.release();
            }
        } else if (this.mediaRecorder != null) {
            this.mediaRecorder.release();
        }
    }

    public void reset() {
        try {
            if (this.state != State.ERROR) {
                release();
                this.filePath = null;
                this.cAmplitude = 0;
                if (this.rUncompressed) {
                    this.audioRecorder = new AudioRecord(this.aSource, this.sRate, this.nChannels + 1, this.aFormat, this.bufferSize);
                } else {
                    this.mediaRecorder = new MediaRecorder();
                    this.mediaRecorder.setAudioSource(1);
                    this.mediaRecorder.setOutputFormat(1);
                    this.mediaRecorder.setAudioEncoder(1);
                }
                this.state = State.INITIALIZING;
            }
        } catch (Exception e) {
            Log.e(ExtAudioRecorder.class.getName(), e.getMessage());
            this.state = State.ERROR;
        }
    }

    public void start() {
        if (this.state == State.READY) {
            if (this.rUncompressed) {
                this.payloadSize = 0;
                this.audioRecorder.startRecording();
                this.audioRecorder.read(this.buffer, 0, this.buffer.length);
            } else {
                this.mediaRecorder.start();
            }
            this.state = State.RECORDING;
            return;
        }
        Log.e(ExtAudioRecorder.class.getName(), "start() called on illegal state");
        this.state = State.ERROR;
    }

    public String stop() {
        String audio = null;
        if (this.state == State.RECORDING) {
            if (this.rUncompressed) {
                this.audioRecorder.stop();
                try {
                    byte[] header = this.baos.toByteArray();
                    int totalDataLength = this.payloadSize + 36;
                    header[4] = (byte) (totalDataLength & 255);
                    header[5] = (byte) ((totalDataLength >> 8) & 255);
                    header[6] = (byte) ((totalDataLength >> 16) & 255);
                    header[7] = (byte) ((totalDataLength >> 24) & 255);
                    header[40] = (byte) (this.payloadSize & 255);
                    header[41] = (byte) ((this.payloadSize >> 8) & 255);
                    header[42] = (byte) ((this.payloadSize >> 16) & 255);
                    header[43] = (byte) ((this.payloadSize >> 24) & 255);
                    String audio2 = new String(Base64.encode(header, 2));
                    try {
                        this.baos.close();
                        audio = audio2;
                    } catch (Exception e) {
                        audio = audio2;
                        Log.e(ExtAudioRecorder.class.getName(), "I/O exception occured while closing outputstream ");
                        this.state = State.ERROR;
                        this.state = State.STOPPED;
                        return audio;
                    }
                } catch (Exception e2) {
                    Log.e(ExtAudioRecorder.class.getName(), "I/O exception occured while closing outputstream ");
                    this.state = State.ERROR;
                    this.state = State.STOPPED;
                    return audio;
                }
            }
            this.mediaRecorder.stop();
            this.state = State.STOPPED;
        } else {
            Log.e(ExtAudioRecorder.class.getName(), "stop() called on illegal state");
            this.state = State.ERROR;
        }
        return audio;
    }

    private short getShort(byte argB1, byte argB2) {
        return (short) ((argB2 << 8) | argB1);
    }
}
